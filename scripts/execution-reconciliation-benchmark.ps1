param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Username = 'admin',
    [string]$Password = 'admin123',
    [switch]$KeepArtifacts
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$pythonDir = Join-Path $repoRoot 'python'

function Invoke-PythonJson {
    param(
        [string]$Script
    )

    Push-Location $pythonDir
    try {
        $env:PYTHONPATH = 'src'
        $raw = $Script | python -
        if ($LASTEXITCODE -ne 0) {
            throw "Python command failed with exit code $LASTEXITCODE"
        }
        if (-not $raw) {
            return $null
        }
        return ($raw | ConvertFrom-Json)
    } finally {
        Pop-Location
    }
}

$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$loginResponse = Invoke-RestMethod -Method Post -Uri "$BaseUrl/login" -ContentType 'application/json' -Body $loginBody
if ($loginResponse.code -ne 200 -or -not $loginResponse.token) {
    throw "Login failed: $($loginResponse | ConvertTo-Json -Depth 6)"
}
$headers = @{ Authorization = "Bearer $($loginResponse.token)" }

$seedScript = @'
import json
import psycopg2
from quant_road.config import settings

conn = psycopg2.connect(
    host=settings.pg_host,
    port=settings.pg_port,
    dbname=settings.pg_database,
    user=settings.pg_user,
    password=settings.pg_password,
)
conn.autocommit = False
cur = conn.cursor()
cur.execute(
    """
    select er.id, er.stock_code, er.side, er.trade_date, er.strategy_id, er.price, sb.stock_name
    from execution_record er
    join stock_basic sb on sb.stock_code = er.stock_code
    where er.signal_id is null
      and not exists (
        select 1
        from trade_signal ts
        where ts.stock_code = er.stock_code
          and ts.strategy_id = er.strategy_id
          and ts.signal_type = er.side
          and ts.signal_date = er.trade_date
      )
    order by er.id desc
    limit 1
    """
)
row = cur.fetchone()
if row is None:
    raise RuntimeError("No unmatched execution without existing candidate signal found for benchmark")

execution_id, stock_code, side, trade_date, strategy_id, price, stock_name = row
cur.execute(
    """
    insert into trade_signal (
        stock_code, stock_name, signal_type, suggest_price, signal_date, strategy_id, is_execute, create_time
    )
    values (%s, %s, %s, %s, %s, %s, 0, now())
    returning id
    """,
    (stock_code, stock_name, side, price, trade_date, strategy_id),
)
signal_id = cur.fetchone()[0]
conn.commit()
cur.close()
conn.close()

print(json.dumps({
    "executionRecordId": execution_id,
    "signalId": signal_id,
    "stockCode": stock_code,
    "side": side,
    "tradeDate": str(trade_date),
    "strategyId": strategy_id,
}))
'@

$seed = Invoke-PythonJson -Script $seedScript
if ($null -eq $seed -or -not $seed.executionRecordId -or -not $seed.signalId) {
    throw "Failed to seed reconciliation benchmark payload"
}

try {
    $candidates = Invoke-RestMethod `
        -Method Get `
        -Uri "$BaseUrl/quant/data/executionMatchCandidates?executionRecordId=$($seed.executionRecordId)&limit=5" `
        -Headers $headers
    if ($candidates.code -ne 200) {
        throw "Candidate query failed: $($candidates | ConvertTo-Json -Depth 8)"
    }
    $candidateIds = @($candidates.data | ForEach-Object { $_.signalId })
    if ($candidateIds -notcontains $seed.signalId) {
        throw "Seeded signal $($seed.signalId) not returned in candidate list: $($candidates | ConvertTo-Json -Depth 8)"
    }

    $confirmBody = @{
        signalId = [long]$seed.signalId
        executionRecordId = [long]$seed.executionRecordId
        actor = 'execution-benchmark'
        remark = 'temporary benchmark match'
    } | ConvertTo-Json -Compress
    $confirm = Invoke-RestMethod `
        -Method Post `
        -Uri "$BaseUrl/quant/jobs/confirmExecutionMatch" `
        -Headers $headers `
        -ContentType 'application/json' `
        -Body $confirmBody
    if ($confirm.code -ne 200 -or -not $confirm.data.matchConfirmed) {
        throw "Confirm execution match failed: $($confirm | ConvertTo-Json -Depth 8)"
    }
    if (-not $confirm.data.executionReconciliationSummary) {
        throw "Confirm response missing executionReconciliationSummary: $($confirm | ConvertTo-Json -Depth 8)"
    }
    if (-not $confirm.data.positionSyncResult) {
        throw "Confirm response missing positionSyncResult: $($confirm | ConvertTo-Json -Depth 8)"
    }
    if ([int]($confirm.data.executionReconciliationSummary.executedSignalCount | ForEach-Object { $_ }) -lt 1) {
        throw "Confirm response did not refresh execution feedback to EXECUTED: $($confirm | ConvertTo-Json -Depth 8)"
    }

    $summary = Invoke-RestMethod `
        -Method Get `
        -Uri "$BaseUrl/quant/data/executionReconciliationSummary" `
        -Headers $headers
    if ($summary.code -ne 200) {
        throw "Reconciliation summary query failed: $($summary | ConvertTo-Json -Depth 8)"
    }
    if ([int]($summary.data.executedSignalCount | ForEach-Object { $_ }) -lt 1) {
        throw "Summary after confirm does not show executed signal count: $($summary | ConvertTo-Json -Depth 8)"
    }

    $positionSync = Invoke-RestMethod `
        -Method Get `
        -Uri "$BaseUrl/quant/data/positionSyncResult?strategyId=$($seed.strategyId)&stockCode=$($seed.stockCode)" `
        -Headers $headers
    if ($positionSync.code -ne 200) {
        throw "Position sync query failed: $($positionSync | ConvertTo-Json -Depth 8)"
    }
    if (-not $positionSync.data.syncStatus) {
        throw "Position sync query returned empty payload: $($positionSync | ConvertTo-Json -Depth 8)"
    }

    [pscustomobject]@{
        benchmark = $seed
        candidates = $candidates.data
        confirm = $confirm.data
        summary = $summary.data
        positionSync = $positionSync.data
    } | ConvertTo-Json -Depth 10
}
finally {
    if (-not $KeepArtifacts) {
        $cleanupScript = @"
import psycopg2
from quant_road.config import settings

signal_id = int("$($seed.signalId)")
execution_record_id = int("$($seed.executionRecordId)")
conn = psycopg2.connect(
    host=settings.pg_host,
    port=settings.pg_port,
    dbname=settings.pg_database,
    user=settings.pg_user,
    password=settings.pg_password,
)
conn.autocommit = False
cur = conn.cursor()
cur.execute("delete from signal_execution_feedback where signal_id = %s", (signal_id,))
cur.execute("update execution_record set signal_id = null where id = %s and signal_id = %s", (execution_record_id, signal_id))
cur.execute("delete from trade_signal where id = %s", (signal_id,))
conn.commit()
cur.close()
conn.close()
"@
        Invoke-PythonJson -Script $cleanupScript | Out-Null
    }
}
