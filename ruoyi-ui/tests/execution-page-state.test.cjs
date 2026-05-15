const test = require('node:test');
const assert = require('node:assert/strict');

const {
  getExecutionClosureCriticalKeys,
  getExecutionClosureDeferredKeys,
  getExecutionClosureKeys,
  resolveExecutionFirstScreenState
} = require('../src/views/quant/execution/execution-page-state');

test('execution closure keeps triage-critical datasets in the first batch', () => {
  assert.deepEqual(getExecutionClosureCriticalKeys(), [
    'records',
    'signals',
    'reconciliationSummary',
    'positionSyncResult',
    'actionItems'
  ]);
});

test('execution closure defers detailed feedback rows until after first-screen triage', () => {
  assert.deepEqual(getExecutionClosureDeferredKeys(), [
    'feedback'
  ]);
});

test('execution closure key list remains the union of critical and deferred sets', () => {
  assert.deepEqual(getExecutionClosureKeys(), [
    'records',
    'signals',
    'reconciliationSummary',
    'positionSyncResult',
    'actionItems',
    'feedback'
  ]);
});

test('execution first screen keeps on-page unmatched triage ahead of manual tools', () => {
  const result = resolveExecutionFirstScreenState({
    topExecutionAction: {
      title: '处理未匹配成交',
      targetPage: '/quant/execution'
    },
    reconciliationSummary: {
      unmatchedExecutionCount: 3,
      partialExecutionCount: 1
    },
    positionSyncResult: {
      differenceCount: 2
    },
    abnormalFeedbackCount: 4
  })

  assert.equal(result.primaryAction.title, '处理未匹配成交')
  assert.equal(result.primaryAction.action, 'focus:unmatched')
  assert.equal(result.primaryAction.isCrossPage, false)
  assert.deepEqual(result.manualActions.map(item => item.title), ['手工成交回写', '批量导入回写'])
  assert.equal(result.summaryCards[0].title, '当前异常')
  assert.equal(result.summaryCards[0].emphasis, '8 条待处理')
})

test('execution first screen yields to cross-page gate when preconditions block reconciliation', () => {
  const result = resolveExecutionFirstScreenState({
    topExecutionAction: {
      title: '先核对数据完整性',
      targetPage: '/quant/operations',
      reason: '成交前链路仍有门禁异常'
    },
    reconciliationSummary: {
      unmatchedExecutionCount: 1
    },
    abnormalFeedbackCount: 0,
    positionSyncResult: {
      differenceCount: 0
    }
  })

  assert.equal(result.primaryAction.title, '先核对数据完整性')
  assert.equal(result.primaryAction.action, 'route')
  assert.equal(result.primaryAction.isCrossPage, true)
  assert.equal(result.summaryCards[1].title, '手工触发')
  assert.equal(result.summaryCards[1].emphasis, '仅在券商回流缺失时使用')
})
