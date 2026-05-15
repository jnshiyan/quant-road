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

function buildCurrentDecisionAction(summaryPayload = {}) {
  return {
    actionType: 'GOVERNANCE_DECISION',
    title: '提交正式治理决策',
    reason: normalizeText(summaryPayload.recommendationReason, '当前治理证据已就绪，可结合系统建议沉淀正式动作。'),
    priority: 'P1',
    targetPage: '/quant/shadow',
    targetQuery: {},
    recommendedAction: normalizeText(summaryPayload.governanceAction, 'OBSERVE'),
    sourceAction: 'governanceDecision',
    isCrossPage: false,
    isCurrentPage: true,
    currentSectionRef: 'decisionSection'
  }
}

function buildShadowActionPlan(payload = {}) {
  const actionItems = Array.isArray(payload.actionItems) ? payload.actionItems : []
  const queryParams = payload.queryParams || {}
  const summaryPayload = payload.summaryPayload || {}
  const baseline = summaryPayload.baseline || {}
  const candidate = summaryPayload.candidate || {}
  const comparableMonths = toNumber((summaryPayload.summary || {}).comparableMonths)
  const targetLabel = `${normalizeText(baseline.strategy_name || baseline.strategy_id, '未选择基线')} vs ${normalizeText(candidate.strategy_name || candidate.strategy_id, '未选择候选')}`
  const topAction = actionItems[0] || null

  let headline = '当前治理证据已聚合，可据此形成正式决策。'
  if (topAction && topAction.actionType === 'DATA_INTEGRITY_REVIEW') {
    headline = `先不要提交治理决策，先${topAction.title}。`
  } else if (queryParams.baselineStrategyId && queryParams.candidateStrategyId) {
    headline = '当前已定位到治理对象，可直接沉淀正式治理决策。'
  }

  const summaryLines = [
    `治理对象 ${targetLabel}，系统建议 ${normalizeText(summaryPayload.recommendation, '暂无结论')}，治理动作 ${normalizeText(summaryPayload.governanceAction, '待确认')}，可比月份 ${comparableMonths}。`
  ]
  if (topAction && normalizeText(topAction.targetPage || topAction.path) && normalizeText(topAction.targetPage || topAction.path) !== '/quant/shadow') {
    summaryLines.push('当前还有前置动作在其他页面，建议先处理前置阻断，再回到治理页提交正式决策。')
  } else {
    summaryLines.push('当前证据已落在本页，可直接在当前治理页补充审批状态、核心证据与风险备注。')
  }

  const nextActions = actionItems.slice(0, 3).map((item, index) => {
    const targetPage = normalizeText(item.targetPage || item.path)
    return {
      renderKey: buildActionKey(item, index),
      title: normalizeText(item.title, '待处理动作'),
      reason: normalizeText(item.reason, '-'),
      priority: normalizeText(item.priority, 'P2'),
      targetPage,
      targetQuery: item.targetQuery || item.query || {},
      recommendedAction: normalizeText(item.recommendedAction || item.actionType),
      sourceAction: normalizeText(item.sourceAction),
      isCrossPage: !!targetPage && targetPage !== '/quant/shadow',
      isCurrentPage: targetPage === '/quant/shadow',
      currentSectionRef: targetPage === '/quant/shadow' ? 'decisionSection' : undefined
    }
  })

  if (!nextActions.length && queryParams.baselineStrategyId && queryParams.candidateStrategyId) {
    nextActions.push({
      renderKey: buildActionKey(buildCurrentDecisionAction(summaryPayload), 0),
      ...buildCurrentDecisionAction(summaryPayload)
    })
  }

  return {
    headline,
    summaryLines,
    nextActions
  }
}

module.exports = {
  buildShadowActionPlan
}
