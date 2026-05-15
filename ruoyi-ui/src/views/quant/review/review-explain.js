function normalizeText(value, fallback = '') {
  const text = String(value == null ? '' : value).trim()
  return text || fallback
}

function toNumber(value) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

function buildActionKey(item = {}, index = 0) {
  return [
    normalizeText(item.actionType),
    normalizeText(item.priority),
    normalizeText(item.targetPage),
    normalizeText(item.title),
    index
  ].join('|')
}

function isSameValue(left, right) {
  const leftText = normalizeText(left)
  const rightText = normalizeText(right)
  if (!leftText && !rightText) {
    return true
  }
  if (leftText && rightText && !Number.isNaN(Number(leftText)) && !Number.isNaN(Number(rightText))) {
    return Number(leftText) === Number(rightText)
  }
  return leftText === rightText
}

function matchesReviewContext(targetQuery = {}, reviewContext = {}) {
  const comparableKeys = ['reviewLevel', 'stockCode', 'strategyId', 'signalId', 'baselineStrategyId', 'candidateStrategyId', 'caseId']
  const constrainedKeys = comparableKeys.filter(key => normalizeText(targetQuery[key]) !== '')
  if (!constrainedKeys.length) {
    return false
  }
  return constrainedKeys.every(key => isSameValue(targetQuery[key], reviewContext[key]))
}

function buildReviewActionPlan(payload = {}) {
  const actionItems = Array.isArray(payload.actionItems) ? payload.actionItems : []
  const reviewContext = payload.reviewContext || {}
  const summaryPayload = payload.summaryPayload || {}
  const reconciliationSummary = payload.reconciliationSummary || {}
  const topAction = actionItems[0] || null
  const reviewTargetName = normalizeText(summaryPayload.reviewTargetName, '待选择')

  let headline = '当前可沿着证据链完成复盘并沉淀结论。'
  if (topAction && topAction.actionType === 'DATA_INTEGRITY_REVIEW') {
    headline = `先不要沉淀复盘结论，先${topAction.title}。`
  } else if (topAction && normalizeText(topAction.targetPage || topAction.path) === '/quant/review'
    && matchesReviewContext(topAction.targetQuery || topAction.query || {}, reviewContext)) {
    headline = '当前已定位到复盘对象，可直接补充结论并沉淀动作。'
  } else if (topAction && topAction.title) {
    headline = `当前首要动作：${topAction.title}。`
  }

  const summaryLines = [
    `复盘对象 ${reviewTargetName}，当前结论 ${normalizeText(summaryPayload.reviewConclusion, '待分析')}，建议动作 ${normalizeText(summaryPayload.suggestedAction, 'KEEP')}。`,
    `执行缺口：待执行 ${toNumber(reconciliationSummary.pendingSignalCount)}，未匹配 ${toNumber(reconciliationSummary.unmatchedExecutionCount)}，漏执行 ${toNumber(reconciliationSummary.missedSignalCount)}。`
  ]

  if (topAction && normalizeText(topAction.targetPage || topAction.path) === '/quant/review'
    && matchesReviewContext(topAction.targetQuery || topAction.query || {}, reviewContext)) {
    summaryLines[1] = '当前首要动作已落在本页，可直接在当前复盘页补充证据、结论和后续动作。'
  } else if (topAction && normalizeText(topAction.targetPage || topAction.path) && normalizeText(topAction.targetPage || topAction.path) !== '/quant/review') {
    summaryLines.push('当前还有前置动作在其他页面，建议先完成前置处理，再回到复盘页沉淀正式结论。')
  }

  const nextActions = actionItems.slice(0, 3).map((item, index) => {
    const targetPage = normalizeText(item.targetPage || item.path)
    const targetQuery = item.targetQuery || item.query || {}
    const isCurrentPage = targetPage === '/quant/review' && matchesReviewContext(targetQuery, reviewContext)
    return {
      renderKey: buildActionKey(item, index),
      title: normalizeText(item.title, '待处理动作'),
      reason: normalizeText(item.reason, '-'),
      priority: normalizeText(item.priority, 'P2'),
      targetPage,
      targetQuery,
      recommendedAction: normalizeText(item.recommendedAction || item.actionType),
      sourceAction: normalizeText(item.sourceAction),
      isCrossPage: !!targetPage && targetPage !== '/quant/review',
      isCurrentPage,
      currentSectionRef: isCurrentPage ? 'conclusionSection' : undefined
    }
  })

  return {
    headline,
    summaryLines,
    nextActions
  }
}

module.exports = {
  buildReviewActionPlan
}
