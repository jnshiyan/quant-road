const test = require('node:test')
const assert = require('node:assert/strict')

const { buildJobCenterGuidance } = require('../src/views/quant/jobs/jobs-explain')

test('buildJobCenterGuidance highlights blocked worker and recoverable batch', () => {
  const payload = buildJobCenterGuidance({
    asyncWorkerSummary: { status: 'BLOCKED', message: '存在待消费分片，但当前没有活跃 worker。' },
    jobReadiness: { status: 'READY_WITH_WARNINGS', canRecover: true },
    currentJobStatus: { status: 'RUNNING', jobId: 101 },
    currentBatchId: 12
  })

  assert.equal(payload.workerPriority, 'HIGH')
  assert.match(payload.dailyOps[0], /RUNNING/)
  assert.match(payload.troubleshooting[0], /没有活跃 worker/)
  assert.match(payload.troubleshooting[1], /批次 12/)
})

test('buildJobCenterGuidance prioritizes blocked data integrity over normal dashboard entry', () => {
  const payload = buildJobCenterGuidance({
    asyncWorkerSummary: { status: 'ACTIVE', message: 'worker 正常。' },
    jobReadiness: {
      status: 'BLOCKED',
      canRecover: false,
      dataIntegrityStatus: 'BLOCKED',
      dataIntegrityCategory: 'PARTIAL_DAILY_SYNC',
      dataIntegrityMessage: '日线同步存在失败或空结果标的，建议先核对失败标的后再继续运营。'
    },
    currentJobStatus: {},
    currentBatchId: 21
  })

  assert.equal(payload.dailyOps.some(item => /先不要进入看板或执行页/.test(item)), true)
  assert.equal(payload.troubleshooting.some(item => /数据完整性|日线同步/.test(item)), true)
  assert.equal(payload.workerPriority, 'LOW')
})
