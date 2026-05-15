const EXECUTION_CLOSURE_CRITICAL_KEYS = [
  'records',
  'signals',
  'reconciliationSummary',
  'positionSyncResult',
  'actionItems'
]

const EXECUTION_CLOSURE_DEFERRED_KEYS = [
  'feedback'
]

function toNumber(value) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

function normalizeText(value, fallback = '') {
  const text = String(value == null ? '' : value).trim()
  return text || fallback
}

function getExecutionClosureCriticalKeys() {
  return [...EXECUTION_CLOSURE_CRITICAL_KEYS]
}

function getExecutionClosureDeferredKeys() {
  return [...EXECUTION_CLOSURE_DEFERRED_KEYS]
}

function getExecutionClosureKeys() {
  return [
    ...EXECUTION_CLOSURE_CRITICAL_KEYS,
    ...EXECUTION_CLOSURE_DEFERRED_KEYS
  ]
}

function resolveExecutionFirstScreenState(payload = {}) {
  const topExecutionAction = payload.topExecutionAction || {}
  const reconciliationSummary = payload.reconciliationSummary || {}
  const positionSyncResult = payload.positionSyncResult || {}
  const abnormalFeedbackCount = toNumber(payload.abnormalFeedbackCount)

  const pendingSignalCount = toNumber(reconciliationSummary.pendingSignalCount)
  const unmatchedExecutionCount = toNumber(reconciliationSummary.unmatchedExecutionCount)
  const partialExecutionCount = toNumber(reconciliationSummary.partialExecutionCount)
  const positionDifferenceCount = toNumber(positionSyncResult.differenceCount)
  const totalIssueCount = unmatchedExecutionCount + partialExecutionCount + abnormalFeedbackCount

  const targetPage = normalizeText(topExecutionAction.targetPage || topExecutionAction.path)
  const targetQuery = topExecutionAction.targetQuery || topExecutionAction.query || {}
  const isCrossPage = !!targetPage && targetPage !== '/quant/execution'

  let primaryAction = {
    title: '查看全部异常',
    description: '当前没有额外门禁阻断，可继续在执行页巡检闭环状态。',
    action: 'focus:all',
    isCrossPage: false,
    targetPage: '/quant/execution',
    targetQuery: {}
  }

  if (isCrossPage) {
    primaryAction = {
      title: normalizeText(topExecutionAction.title, '先处理前置门禁'),
      description: normalizeText(topExecutionAction.reason, '当前执行页之前还有前置门禁需要先处理。'),
      action: 'route',
      isCrossPage: true,
      targetPage,
      targetQuery
    }
  } else if (unmatchedExecutionCount > 0) {
    primaryAction = {
      title: '处理未匹配成交',
      description: '先确认成交归属，再继续处理反馈和持仓差异。',
      action: 'focus:unmatched',
      isCrossPage: false,
      targetPage: '/quant/execution',
      targetQuery: { focus: 'unmatched' }
    }
  } else if (abnormalFeedbackCount > 0) {
    primaryAction = {
      title: '处理异常反馈',
      description: '先确认漏执行、取消和人工复核，再决定是否需要补录成交。',
      action: 'focus:abnormal',
      isCrossPage: false,
      targetPage: '/quant/execution',
      targetQuery: { focus: 'abnormal' }
    }
  } else if (partialExecutionCount > 0) {
    primaryAction = {
      title: '补齐部分成交',
      description: '先补齐不完整成交，避免闭环结论偏差。',
      action: 'focus:partial',
      isCrossPage: false,
      targetPage: '/quant/execution',
      targetQuery: { focus: 'partial' }
    }
  } else if (positionDifferenceCount > 0) {
    primaryAction = {
      title: '核对持仓差异',
      description: '执行链已基本完成，优先确认 position 是否与成交事实一致。',
      action: 'focus:positionDiff',
      isCrossPage: false,
      targetPage: '/quant/execution',
      targetQuery: { focus: 'positionDiff' }
    }
  } else if (pendingSignalCount > 0) {
    primaryAction = {
      title: '核对待执行信号',
      description: '当前没有明显异常，但仍有待执行信号，建议先确认是否已完成回写。',
      action: 'section:signalSection',
      isCrossPage: false,
      targetPage: '/quant/execution',
      targetQuery: {}
    }
  }

  const manualActions = [
    {
      title: '手工成交回写',
      description: '单笔补录 1 笔成交。',
      action: 'section:importSection'
    },
    {
      title: '批量导入回写',
      description: '券商回流缺失时集中补录。',
      action: 'section:recordEntrySection'
    }
  ]

  const summaryCards = [
    {
      title: '当前异常',
      label: '异常优先级',
      emphasis: totalIssueCount > 0 ? `${totalIssueCount} 条待处理` : '当前无执行异常',
      lines: [
        `未匹配 ${unmatchedExecutionCount} 条`,
        `异常反馈 ${abnormalFeedbackCount} 条`,
        `部分成交 ${partialExecutionCount} 条`
      ]
    },
    {
      title: '手工触发',
      label: '仅在必要时使用',
      emphasis: '仅在券商回流缺失时使用',
      lines: manualActions.map(item => item.description)
    },
    {
      title: '持仓同步',
      label: '技术状态',
      emphasis: positionDifferenceCount > 0 ? `${positionDifferenceCount} 条差异待核对` : '当前已对齐',
      lines: [
        `待执行 ${pendingSignalCount} 条`,
        `持仓前 ${toNumber((positionSyncResult.positionBefore || []).length)} 条`,
        `推导后 ${toNumber((positionSyncResult.positionAfter || []).length)} 条`
      ]
    }
  ]

  return {
    primaryAction,
    manualActions,
    summaryCards
  }
}

module.exports = {
  getExecutionClosureCriticalKeys,
  getExecutionClosureDeferredKeys,
  getExecutionClosureKeys,
  resolveExecutionFirstScreenState
}
