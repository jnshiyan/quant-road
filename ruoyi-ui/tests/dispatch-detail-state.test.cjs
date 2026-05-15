const test = require('node:test')
const assert = require('node:assert/strict')

const { buildDispatchDetailState } = require('../src/views/quant/dispatch-detail/dispatch-detail-state')

test('running detail state exposes factual rows and first-screen result previews', () => {
  const result = buildDispatchDetailState({
    detailPayload: {
      detailSummary: {
        taskName: '手工调度任务',
        resultSummary: '执行中，当前已产出 2 条结果',
        scopeSummary: 'ETF 池 / 2 个标的',
        timeRangeSummary: '2021-05-10 ~ 2026-05-10',
        executionSummary: 'sync-daily -> run-strategy',
        triggerModeLabel: '手工触发',
        shardProgress: '1 / 2 分片',
        startedAt: '2026-05-10 18:50:00'
      },
      phaseLabel: '执行策略',
      nextStepLabel: '等待当前分片完成'
    },
    historyRecord: {
      scopeSummary: 'ETF 池 / 2 个标的'
    },
    jobStatus: {
      status: 'RUNNING',
      completedShardCount: 1,
      plannedShardCount: 2
    },
    shards: [
      {
        status: 'RUNNING',
        shardKey: 'shard-1',
        symbolsText: '510300,159915',
        symbolCount: 2
      }
    ],
    results: [
      { stock_code: '510300', signal_type: 'BUY', strategy_id: 1, annual_return: 12.5, max_drawdown: 8.1 },
      { stock_code: '159915', signal_type: 'SELL', strategy_id: 1, annual_return: 6.8, max_drawdown: 4.2 }
    ],
    events: [
      {
        status: 'RUNNING',
        stepName: 'run-strategy',
        endTime: '2026-05-10 19:00:00'
      }
    ],
    autoRefresh: true,
    nextRefreshInSeconds: 8
  })

  assert.equal(result.statusLabel, '运行中')
  assert.equal(result.firstScreenMode, 'progress-first')
  assert.equal(result.pageTitle, '手工调度任务')
  assert.match(result.refreshHint, /8 秒后自动刷新/)
  assert.equal(result.latestLogLabel, 'run-strategy')
  assert.equal(result.factRows.find(item => item.label === '执行范围').value, 'ETF 池 / 2 个标的')
  assert.equal(result.factRows.find(item => item.label === '下一步').value, '等待当前分片完成')
  assert.equal(result.resultBreakdown[0].label, 'BUY')
  assert.equal(result.resultPreviewRows[0].title, '510300 / BUY')
})

test('queued detail state keeps factual phase and next step rows', () => {
  const result = buildDispatchDetailState({
    detailPayload: {
      detailSummary: {
        taskName: '盘后主流程',
        scopeSummary: 'ETF 池 / 2 个标的',
        timeRangeSummary: '2021-05-10 ~ 2026-05-10',
        executionSummary: 'sync-daily -> run-strategy',
        shardProgress: '0 / 1 分片'
      },
      phaseLabel: '生成分片',
      nextStepLabel: '等待 Worker 消费分片'
    },
    historyRecord: {
      scopeSummary: 'ETF 池 / 2 个标的'
    },
    jobStatus: {
      status: 'QUEUED',
      completedShardCount: 0,
      plannedShardCount: 1
    },
    shards: [],
    results: [],
    events: [],
    autoRefresh: true,
    nextRefreshInSeconds: 5
  })

  assert.equal(result.statusLabel, '排队中')
  assert.equal(result.firstScreenMode, 'progress-first')
  assert.equal(result.factRows.find(item => item.label === '当前阶段').value, '生成分片')
  assert.equal(result.factRows.find(item => item.label === '下一步').value, '等待 Worker 消费分片')
  assert.equal(result.resultBreakdown.length, 0)
})

test('success detail state summarizes outcomes and representative results without wait copy', () => {
  const result = buildDispatchDetailState({
    detailPayload: {
      detailSummary: {
        taskName: '手工调度任务',
        resultSummary: '产出 2 条结果，异常 0 类',
        resultCount: 2,
        errorCount: 0
      }
    },
    historyRecord: {
      resultSummary: '已生成结果'
    },
    jobStatus: {
      status: 'SUCCESS',
      completedShardCount: 2,
      plannedShardCount: 2
    },
    shards: [],
    results: [
      { stock_code: '510300', signal_type: 'BUY', strategy_id: 1, annual_return: 12.5, max_drawdown: 8.1 },
      { stock_code: '159915', signal_type: 'BUY', strategy_id: 2, annual_return: 9.2, max_drawdown: 5.4 }
    ],
    events: [],
    autoRefresh: false,
    nextRefreshInSeconds: 0
  })

  assert.equal(result.statusLabel, '已完成')
  assert.equal(result.firstScreenMode, 'outcome-first')
  assert.equal(result.outcomeStats[0].value, '2')
  assert.equal(result.resultBreakdown[0].value, '2 条')
  assert.equal(result.resultPreviewRows[1].title, '159915 / BUY')
  assert.equal(result.refreshHint, '自动刷新已关闭')
})
