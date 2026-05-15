function toNumber(value) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

function normalizeText(value, fallback = '') {
  const text = String(value == null ? '' : value).trim()
  return text || fallback
}

function buildExecutionActionKey(item = {}, index = 0) {
  return [
    normalizeText(item.actionType),
    normalizeText(item.priority),
    normalizeText(item.targetPage),
    normalizeText(item.title),
    index
  ].join('|')
}

function buildExecutionActionPlan(payload = {}) {
  const actionItems = Array.isArray(payload.actionItems) ? payload.actionItems : []
  const reconciliationSummary = payload.reconciliationSummary || {}
  const positionSyncResult = payload.positionSyncResult || {}
  const abnormalFeedbackCount = toNumber(payload.abnormalFeedbackCount)
  const topAction = actionItems[0] || null

  let headline = '当前执行主链没有明显阻断项，可按未匹配成交、反馈、持仓的顺序巡检。'
  if (topAction && topAction.actionType === 'DATA_INTEGRITY_REVIEW') {
    headline = `先不要继续处理成交，先${topAction.title}。`
  } else if (topAction && topAction.title) {
    headline = `当前首要动作：${topAction.title}。`
  }

  const summaryLines = [
    `待执行 ${toNumber(reconciliationSummary.pendingSignalCount)}，未匹配 ${toNumber(reconciliationSummary.unmatchedExecutionCount)}，部分成交 ${toNumber(reconciliationSummary.partialExecutionCount)}，异常反馈 ${abnormalFeedbackCount}，持仓差异 ${toNumber(positionSyncResult.differenceCount)}。`
  ]
  if (topAction && normalizeText(topAction.targetPage) && normalizeText(topAction.targetPage) !== '/quant/execution') {
    summaryLines.push('当前首要动作不在执行页内，应先完成前置门禁，再回到成交闭环。')
  } else if (topAction) {
    summaryLines.push('优先在当前执行页完成首要动作，再继续处理后续反馈与持仓同步。')
  } else {
    summaryLines.push('当前没有额外运营动作，可直接围绕执行记录、反馈、持仓差异做抽样确认。')
  }

  const nextActions = actionItems.slice(0, 3).map((item, index) => {
    const targetPage = normalizeText(item.targetPage || item.path)
    const targetQuery = item.targetQuery || item.query || {}
    return {
      renderKey: buildExecutionActionKey(item, index),
      title: normalizeText(item.title, '待处理动作'),
      reason: normalizeText(item.reason, '-'),
      priority: normalizeText(item.priority, 'P2'),
      targetPage,
      targetQuery,
      recommendedAction: normalizeText(item.recommendedAction || item.actionType),
      sourceAction: normalizeText(item.sourceAction),
      isCrossPage: !!targetPage && targetPage !== '/quant/execution'
    }
  })

  return {
    headline,
    summaryLines,
    nextActions
  }
}

function buildExecutionChainSummary(payload = {}) {
  const reconciliationSummary = payload.reconciliationSummary || {}
  const positionSyncResult = payload.positionSyncResult || {}
  const abnormalFeedbackCount = toNumber(payload.abnormalFeedbackCount)
  const focusPreset = String(payload.focusPreset || 'all')

  const signalCount = toNumber(reconciliationSummary.pendingSignalCount) + toNumber(reconciliationSummary.executedSignalCount) + toNumber(reconciliationSummary.missedSignalCount)
  const executionGapCount = toNumber(reconciliationSummary.unmatchedExecutionCount) + toNumber(reconciliationSummary.partialExecutionCount)
  const positionGapCount = toNumber(positionSyncResult.differenceCount)

  let headline = '当前执行闭环基本通顺，可按信号、成交、持仓顺序抽样巡检。'
  if (focusPreset !== 'all') {
    headline = `当前正在聚焦处理“${focusPreset}”相关问题。`
  } else if (executionGapCount > 0 || abnormalFeedbackCount > 0) {
    headline = '当前主链仍有执行缺口，建议先补成交匹配，再核对反馈和持仓。'
  } else if (positionGapCount > 0) {
    headline = '成交链基本完成，但持仓同步仍有差异，建议先校验 position。'
  }

  const stages = [
    {
      key: 'signal',
      title: '信号',
      value: signalCount,
      status: signalCount > 0 ? 'active' : 'idle',
      summary: `待执行 ${toNumber(reconciliationSummary.pendingSignalCount)}，漏执行 ${toNumber(reconciliationSummary.missedSignalCount)}。`
    },
    {
      key: 'execution',
      title: '成交',
      value: executionGapCount,
      status: executionGapCount > 0 ? 'warning' : 'healthy',
      summary: `未匹配 ${toNumber(reconciliationSummary.unmatchedExecutionCount)}，部分成交 ${toNumber(reconciliationSummary.partialExecutionCount)}。`
    },
    {
      key: 'feedback',
      title: '反馈',
      value: abnormalFeedbackCount,
      status: abnormalFeedbackCount > 0 ? 'warning' : 'healthy',
      summary: `当前异常反馈 ${abnormalFeedbackCount} 条。`
    },
    {
      key: 'position',
      title: '持仓',
      value: positionGapCount,
      status: positionGapCount > 0 ? 'warning' : 'healthy',
      summary: `持仓差异 ${positionGapCount} 条。`
    }
  ]

  const nextActions = []
  if (toNumber(reconciliationSummary.unmatchedExecutionCount) > 0) {
    nextActions.push('先处理未匹配成交，避免后续反馈和持仓判断建立在错误关联上。')
  }
  if (toNumber(reconciliationSummary.partialExecutionCount) > 0) {
    nextActions.push('再补齐部分成交，确认信号是否真正落地。')
  }
  if (abnormalFeedbackCount > 0) {
    nextActions.push('随后核对异常反馈，判断是取消、漏执行还是需要人工复核。')
  }
  if (positionGapCount > 0) {
    nextActions.push('最后校验持仓同步，确认 position 与成交事实一致。')
  }
  if (!nextActions.length) {
    nextActions.push('当前没有显著缺口，可抽样核对最近成交与当日信号是否一致。')
  }

  return {
    headline,
    stages,
    nextActions
  }
}

module.exports = {
  buildExecutionActionPlan,
  buildExecutionChainSummary
}
