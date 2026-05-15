param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Username = 'admin',
    [string]$Password = 'admin123',
    [int]$PollSeconds = 3,
    [int]$MaxPolls = 40,
    [string]$WorkerId = 'benchmark-worker',
    [switch]$SkipWorker
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$pythonDir = Join-Path $repoRoot 'python'

function Invoke-WorkerOnce {
    Push-Location $pythonDir
    try {
        $env:PYTHONPATH = 'src'
        $raw = python -m quant_road run-async-worker --worker-id $WorkerId --once
        if ($LASTEXITCODE -ne 0) {
            throw "Worker command failed with exit code $LASTEXITCODE"
        }
        if (-not $raw) {
            return $null
        }
        return ($raw | ConvertFrom-Json)
    } finally {
        Pop-Location
    }
}

function Promote-JobShards {
    param(
        [long]$JobId
    )

    Push-Location $pythonDir
    try {
        $env:PYTHONPATH = 'src'
        $script = @"
import json
import psycopg2
import redis
from quant_road.config import settings

job_id = int("$JobId")
conn = psycopg2.connect(
    host=settings.pg_host,
    port=settings.pg_port,
    dbname=settings.pg_database,
    user=settings.pg_user,
    password=settings.pg_password,
)
cur = conn.cursor()
cur.execute(
    '''
    select shard_key
    from quant_async_job_shard
    where job_id = %s
    order by shard_index desc
    ''',
    (job_id,),
)
shard_keys = [row[0] for row in cur.fetchall()]
cur.close()
conn.close()

client = redis.Redis(
    host=settings.redis_host,
    port=settings.redis_port,
    db=settings.redis_db,
    password=settings.redis_password or None,
    decode_responses=True,
)
for shard_key in shard_keys:
    client.lrem(settings.async_redis_queue_key, 0, shard_key)
    client.lpush(settings.async_redis_queue_key, shard_key)

head_count = min(5, max(1, len(shard_keys)))
print(json.dumps({
    'jobId': job_id,
    'promotedShardCount': len(shard_keys),
    'queueHead': client.lrange(settings.async_redis_queue_key, 0, head_count - 1),
}))
"@
        $raw = $script | python -
        if ($LASTEXITCODE -ne 0) {
            throw "Promote-JobShards failed with exit code $LASTEXITCODE"
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
$body = @{
    requestedMode = 'async'
    strategyBacktestStartDate = '2023-01-01'
    strategyId = 1
    symbols = @('000001')
    actor = 'benchmark'
} | ConvertTo-Json

$job = Invoke-RestMethod -Method Post -Uri "$BaseUrl/quant/jobs/runStrategy" -Headers $headers -Body $body -ContentType 'application/json'
if ($job.code -ne 200) {
    throw "Job submission failed: $($job | ConvertTo-Json -Depth 6)"
}

$jobId = $job.data.jobId
if (-not $jobId) {
    throw "No jobId returned: $($job | ConvertTo-Json -Depth 6)"
}
$queuePromotion = Promote-JobShards -JobId $jobId

$history = @()
$workerHistory = @()
for ($i = 0; $i -lt $MaxPolls; $i++) {
    $status = Invoke-RestMethod -Method Get -Uri "$BaseUrl/quant/jobs/status/$jobId" -Headers $headers
    $history += $status.data
    if ($status.data.status -notin @('PENDING', 'QUEUED', 'RUNNING')) {
        break
    }
    if (-not $SkipWorker) {
        $workerPayload = Invoke-WorkerOnce
        if ($null -ne $workerPayload) {
            $workerHistory += $workerPayload
        }
    }
    Start-Sleep -Seconds $PollSeconds
}

$finalStatus = $history[-1]
if ($finalStatus.status -in @('PENDING', 'QUEUED', 'RUNNING')) {
    throw "Async job did not reach terminal state within polling budget. jobId=$jobId status=$($finalStatus.status)"
}

[pscustomobject]@{
    jobId = $jobId
    initial = $job.data
    queuePromotion = $queuePromotion
    history = $history
    workerHistory = $workerHistory
} | ConvertTo-Json -Depth 8
