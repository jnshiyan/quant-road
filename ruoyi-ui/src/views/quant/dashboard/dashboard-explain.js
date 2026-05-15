const { buildDashboardNarrativeActionKey } = require('./dashboard-keys')

function toNumber(value) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

function buildDashboardNarrative(payload = {}) {
  const actionItems = Array.isArray(payload.actionItems) ? payload.actionItems : []
  const riskSummary = payload.riskSummary || {}
  const marketStatus = payload.marketStatus || {}
  const reviewCandidates = Array.isArray(payload.reviewCandidates) ? payload.reviewCandidates : []
  const topAction = actionItems[0] || null
  const pending = toNumber(payload.executionPendingCount || riskSummary.positionDiffCount)
  const riskLevel = String(riskSummary.riskLevel || 'LOW')

  let headline = '今日主链整体平稳，可按既定节奏巡检。'
  if (topAction && topAction.actionType === 'DATA_INTEGRITY_REVIEW') {
    headline = `今日先不要直接执行，先${topAction.title}。`
  } else if (topAction && topAction.reason) {
    headline = `今日首要动作：${topAction.title}。`
  } else if (pending > 0) {
    headline = `今日首要动作：先处理 ${pending} 个执行闭环缺口。`
  } else if (riskLevel === 'HIGH') {
    headline = '今日首要动作：先核对高风险持仓，再决定是否继续执行新信号。'
  }

  const summaryLines = [
    `市场状态 ${marketStatus.status || '-'}，当前风险等级 ${riskLevel}。`,
    `待处理事项 ${actionItems.length} 个，待复盘对象 ${reviewCandidates.length} 个。`
  ]
  if (topAction && topAction.actionType === 'DATA_INTEGRITY_REVIEW') {
    summaryLines.push('当前首要任务是确认盘后结果是否完整可信，再进入执行或复盘。')
  }
  if (toNumber(riskSummary.etfRiskWarningCount) > 0 || toNumber(riskSummary.equityRiskWarningCount) > 0) {
    summaryLines.push(`ETF 预警 ${toNumber(riskSummary.etfRiskWarningCount)} 个，股票预警 ${toNumber(riskSummary.equityRiskWarningCount)} 个。`)
  }

  const nextActions = actionItems.slice(0, 3).map((item, index) => ({
    renderKey: buildDashboardNarrativeActionKey(item, index),
    title: item.title,
    reason: item.reason || '-',
    priority: item.priority || 'P2'
  }))

  return {
    headline,
    summaryLines,
    nextActions
  }
}

module.exports = {
  buildDashboardNarrative
}
