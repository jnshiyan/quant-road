const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const { buildManualDispatchSubmitView } = require('../src/views/quant/dispatch-manual/manual-dispatch-submit-state')

test('submit view explains receipt wait during manual dispatch submission', () => {
  const result = buildManualDispatchSubmitView({
    status: 'submitting',
    startedAt: 1_000
  }, {
    now: 6_200
  })

  assert.equal(result.status, 'submitting')
  assert.equal(result.phaseLabel, '正在提交调度任务')
  assert.match(result.detail, /等待服务端返回任务回执/)
  assert.match(result.expectation, /jobId|任务 ID|调度详情/)
  assert.equal(result.elapsedSeconds, 5)
})

test('submit view confirms redirect after receipt is created', () => {
  const result = buildManualDispatchSubmitView({
    status: 'redirecting',
    startedAt: 10_000,
    jobId: 321
  }, {
    now: 13_100
  })

  assert.equal(result.status, 'redirecting')
  assert.equal(result.phaseLabel, '任务回执已生成')
  assert.match(result.detail, /321/)
  assert.match(result.expectation, /调度详情/)
  assert.equal(result.elapsedSeconds, 3)
})

test('manual dispatch page submits async receipt-first request and explains dispatch-first contract', () => {
  const manualSource = fs.readFileSync(
    path.join(__dirname, '../src/views/quant/dispatch-manual/index.vue'),
    'utf8'
  )
  const summarySource = fs.readFileSync(
    path.join(__dirname, '../src/views/quant/dispatch-shared/ManualDispatchSummaryCard.vue'),
    'utf8'
  )

  assert.equal(manualSource.includes("requestedMode: 'async'"), true)
  assert.equal(manualSource.includes('正在进入调度详情页'), true)
  assert.equal(summarySource.includes('先创建调度任务'), true)
  assert.equal(summarySource.includes('等待服务端返回任务回执'), true)
})
