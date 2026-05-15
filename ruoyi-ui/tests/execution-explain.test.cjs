const test = require('node:test')
const assert = require('node:assert/strict')

const {
  buildExecutionActionPlan,
  buildExecutionChainSummary
} = require('../src/views/quant/execution/execution-explain')

test('buildExecutionChainSummary explains closure gaps in order', () => {
  const payload = buildExecutionChainSummary({
    reconciliationSummary: {
      pendingSignalCount: 2,
      executedSignalCount: 5,
      missedSignalCount: 1,
      unmatchedExecutionCount: 3,
      partialExecutionCount: 1
    },
    positionSyncResult: {
      differenceCount: 2
    },
    abnormalFeedbackCount: 4,
    focusPreset: 'all'
  })

  assert.match(payload.headline, /执行缺口/)
  assert.equal(payload.stages[1].value, 4)
  assert.equal(payload.stages[3].value, 2)
  assert.match(payload.nextActions[0], /未匹配成交/)
})

test('buildExecutionActionPlan escalates data integrity gate before execution reconciliation', () => {
  const payload = buildExecutionActionPlan({
    actionItems: [
      {
        actionType: 'DATA_INTEGRITY_REVIEW',
        title: '核对盘后数据完整性',
        reason: '日线同步存在失败标的，先核对失败标的后再继续执行闭环。',
        priority: 'P0',
        targetPage: '/quant/jobs',
        targetQuery: { batchId: 9 },
        recommendedAction: 'DATA_INTEGRITY_REVIEW',
        sourceAction: 'dataIntegrityGate'
      },
      {
        actionType: 'EXECUTION_RECONCILIATION',
        title: '处理未匹配成交',
        reason: '当前仍有 3 条未匹配成交。',
        priority: 'P0',
        targetPage: '/quant/execution',
        targetQuery: { focus: 'unmatched' },
        recommendedAction: 'EXECUTION_RECONCILIATION',
        sourceAction: 'executionReconciliation'
      }
    ],
    reconciliationSummary: {
      unmatchedExecutionCount: 3,
      partialExecutionCount: 1,
      pendingSignalCount: 2
    },
    positionSyncResult: {
      differenceCount: 0
    },
    abnormalFeedbackCount: 0
  })

  assert.equal(payload.headline, '先不要继续处理成交，先核对盘后数据完整性。')
  assert.match(payload.summaryLines[0], /待执行 2/)
  assert.equal(payload.nextActions[0].targetPage, '/quant/jobs')
  assert.equal(payload.nextActions[0].isCrossPage, true)
})

test('buildExecutionActionPlan keeps execution actions actionable on-page', () => {
  const payload = buildExecutionActionPlan({
    actionItems: [
      {
        actionType: 'EXECUTION_RECONCILIATION',
        title: '处理未匹配成交',
        reason: '当前仍有 2 条未匹配成交。',
        priority: 'P0',
        targetPage: '/quant/execution',
        targetQuery: { focus: 'unmatched' },
        recommendedAction: 'EXECUTION_RECONCILIATION',
        sourceAction: 'executionReconciliation'
      },
      {
        actionType: 'POSITION_SYNC_DIFF',
        title: '核对持仓同步差异',
        reason: '持仓与成交推导存在差异。',
        priority: 'P1',
        targetPage: '/quant/execution',
        targetQuery: { focus: 'positionDiff' },
        recommendedAction: 'POSITION_SYNC_DIFF',
        sourceAction: 'positionRiskSummary'
      }
    ],
    reconciliationSummary: {
      unmatchedExecutionCount: 2,
      partialExecutionCount: 0,
      pendingSignalCount: 0
    },
    positionSyncResult: {
      differenceCount: 4
    },
    abnormalFeedbackCount: 1
  })

  assert.equal(payload.headline, '当前首要动作：处理未匹配成交。')
  assert.equal(payload.nextActions[0].isCrossPage, false)
  assert.deepEqual(payload.nextActions[0].targetQuery, { focus: 'unmatched' })
  assert.match(payload.summaryLines[1], /优先在当前执行页/)
})
