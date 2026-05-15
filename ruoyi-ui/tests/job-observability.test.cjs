const test = require('node:test')
const assert = require('node:assert/strict')

const { buildJobObservabilitySummary } = require('../src/views/quant/jobs/job-observability')

test('summarizes running job with human-readable time range and next step', () => {
  const summary = buildJobObservabilitySummary({
    historyRecord: {
      jobId: 238,
      scopeSummary: 'ETF 池',
      timeRangeSummary: '最近 60 个交易日'
    },
    jobStatus: {
      status: 'RUNNING',
      completedShardCount: 1,
      plannedShardCount: 2
    },
    detailState: {
      currentStageLabel: 'ETF 池信号计算',
      currentObjectLabel: '159915 创业板 ETF',
      nextStepLabel: '完成后进入风险过滤'
    }
  })

  assert.match(summary.headline, /ETF 池/)
  assert.equal(summary.progressLabel, '1/2')
  assert.match(summary.nextStepLabel, /风险过滤/)
  assert.equal(summary.timeRangeLabel, '最近 60 个交易日')
  assert.equal(summary.currentObjectLabel, '159915 创业板 ETF')
})

test('derives progress percent and progress hint for running job cards', () => {
  const summary = buildJobObservabilitySummary({
    primaryTask: {
      scopeSummary: 'ETF 池',
      currentStageLabel: 'ETF 池信号计算',
      currentSymbols: ['510300', '159915'],
      waitingTarget: '等待风险过滤完成'
    },
    jobStatus: {
      status: 'RUNNING',
      completedShardCount: 1,
      plannedShardCount: 4
    }
  })

  assert.equal(summary.progressLabel, '1/4')
  assert.equal(summary.progressPercent, 25)
  assert.match(summary.progressHint, /已完成 1\/4 分片/)
  assert.match(summary.progressHint, /等待风险过滤完成/)
})
