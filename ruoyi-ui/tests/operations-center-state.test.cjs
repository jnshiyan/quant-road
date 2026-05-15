const test = require('node:test')
const assert = require('node:assert/strict')

const { buildOperationsCenterState } = require('../src/views/quant/operations/operations-center-state')

test('operations state exposes blocker-first recovery ordering', () => {
  const result = buildOperationsCenterState({
    topBlocker: { layer: 'worker', title: '无活跃 worker', impact: '阻断执行' },
    recoveryQueue: [
      { code: 'recoverAsyncShards', label: '恢复过期分片' },
      { code: 'recoverBatch', label: '恢复失败批次' }
    ]
  })

  assert.equal(result.blockerTitle, '无活跃 worker')
  assert.equal(result.blockerBadge, '阻断执行')
  assert.equal(result.primaryRecovery.label, '恢复过期分片')
  assert.equal(result.secondaryRecoveries[0].label, '恢复失败批次')
})

test('operations state keeps compatibility actions in advanced toolbox only', () => {
  const result = buildOperationsCenterState({
    topBlocker: { layer: 'batch', title: '盘后批次阻断', impact: '阻断今日流程' },
    recoveryQueue: [],
    toolbox: {
      compatibilityActions: [{ code: 'legacyFullDaily', label: '兼容 fullDaily' }]
    }
  })

  assert.equal(result.primaryRecovery, null)
  assert.equal(result.compatibilityActions[0].code, 'legacyFullDaily')
})

test('operations state only surfaces executable recoveries and keeps navigation hints', () => {
  const result = buildOperationsCenterState({
    recoveryQueue: [
      { code: 'genericRecover', label: '恢复失败步骤', executable: false },
      { code: 'recoverBatch', label: '恢复失败批次', executable: true }
    ],
    toolbox: {
      navigation: [{ code: 'taskCenter', label: '返回任务中心', targetPage: '/quant/jobs' }]
    }
  })

  assert.equal(result.primaryRecovery.code, 'recoverBatch')
  assert.equal(result.secondaryRecoveries.length, 0)
  assert.equal(result.navigation[0].targetPage, '/quant/jobs')
})
