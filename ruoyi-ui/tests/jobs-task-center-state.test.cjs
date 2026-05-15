const test = require('node:test')
const assert = require('node:assert/strict')

const { buildTaskCenterState } = require('../src/views/quant/jobs/jobs-task-center-state')

test('running dispatch builds a compact first-screen status line', () => {
  const result = buildTaskCenterState({
    todayStatus: { statusCode: 'RUNNING', statusLabel: '运行中' },
    primaryTask: {
      taskName: '盘后主流程',
      status: 'RUNNING',
      currentStage: 'run-strategy',
      waitingTarget: 'worker 正在消费分片',
      scopeSummary: 'ETF 池',
      timeRangeSummary: '近 120 个交易日'
    }
  })

  assert.equal(result.pageStatusLine, '当前有调度正在执行，先查看当前调度，不建议重复提交。')
  assert.equal(result.primaryAction.label, '查看当前调度')
  assert.equal(result.primaryTaskView.nextStep, 'worker 正在消费分片')
})

test('idle dispatch center turns the main first-screen button into 手工调度', () => {
  const result = buildTaskCenterState({
    todayStatus: { statusCode: 'WARNING', statusLabel: '警告' },
    primaryTask: {
      taskName: '当前无运行任务',
      status: 'IDLE'
    }
  })

  assert.equal(result.pageStatusLine, '当前没有运行中的调度，可以直接发起手工调度。')
  assert.equal(result.primaryAction.label, '发起手工调度')
})

test('blocked dispatch center explains that recovery comes before a new dispatch', () => {
  const result = buildTaskCenterState({
    todayStatus: { statusCode: 'BLOCKED', statusLabel: '阻断', reason: '无活跃 worker' },
    primaryTask: {
      taskName: '盘后主流程',
      status: 'FAILED'
    }
  })

  assert.equal(result.pageStatusLine, '当前存在阻断问题，应先处理后再发起新调度。')
  assert.equal(result.primaryAction.label, '去运维中心处理')
})

test('running task still exposes technical summary rows for the lower panel', () => {
  const result = buildTaskCenterState({
    primaryTask: {
      taskName: '盘后主流程',
      status: 'RUNNING',
      progressSummary: '2 / 6'
    },
    technicalSummary: {
      batchId: 88,
      workerStatus: 'ACTIVE',
      asyncJobCount: 3
    }
  })

  assert.deepEqual(result.technicalSummaryRows, [
    { label: '任务 ID', value: 88 },
    { label: '步骤进度', value: '2 / 6' },
    { label: 'Worker 状态', value: 'ACTIVE' },
    { label: '异步任务数', value: 3 }
  ])
})

test('blocked center suppresses continue action from the compact first screen', () => {
  const result = buildTaskCenterState({
    todayStatus: {
      statusCode: 'BLOCKED',
      statusLabel: '阻断',
      canContinue: false,
      secondaryAction: null
    }
  })

  assert.equal(result.todayStatusCanContinue, false)
  assert.equal(result.secondaryActions.length, 0)
})

test('completed historical task does not occupy the first screen action slot', () => {
  const result = buildTaskCenterState({
    todayStatus: {
      statusCode: 'OPERABLE',
      statusLabel: '可用'
    },
    nextAction: {
      code: 'ROLLBACK_BASE_SYMBOLS',
      label: '确认基础标的回退',
      targetPage: '/quant/symbols'
    },
    primaryTask: {
      taskName: '盘后主流程',
      status: 'SUCCESS',
      currentStep: 'notify-signals',
      nextStep: '进入量化看板',
      triggerModeLabel: '手工触发'
    }
  })

  assert.equal(result.primaryAction.label, '发起手工调度')
  assert.equal(result.primaryTaskView.taskName, '当前无运行任务')
  assert.equal(result.primaryTaskView.status, 'IDLE')
  assert.equal(result.primaryTaskView.currentStep, '等待开始')
})
