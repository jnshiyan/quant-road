function normalizeCount(value) {
  return Number(value || 0)
}

function buildDashboardObjectLayers(payload = {}) {
  const marketStatus = payload.marketStatus || {}
  const etfOverview = payload.etfOverview || {}
  const reviewCandidates = Array.isArray(payload.reviewCandidates) ? payload.reviewCandidates : []
  const positionRiskSummary = payload.positionRiskSummary || {}
  const executionFeedbackSummary = payload.executionFeedbackSummary || {}

  const equityReviewCount = reviewCandidates.filter(item => {
    const assetType = String(item.assetType || item.asset_type || '').toUpperCase()
    return !assetType || assetType === 'EQUITY' || assetType === 'STOCK'
  }).length

  return [
    {
      key: 'index',
      title: '指数',
      summary: marketStatus.status || '未判断',
      metricLabel: '环境判断',
      description: '负责判断市场环境、预算开关和风险节奏。',
      highlights: [
        `估值快照 ${normalizeCount(payload.indexValuationCount)} 项`,
        `风险等级 ${positionRiskSummary.riskLevel || 'LOW'}`
      ],
      actionLabel: '看指数环境'
    },
    {
      key: 'etf',
      title: 'ETF',
      summary: `${normalizeCount(etfOverview.todayEtfSignalCount)} 个待执行/运行中`,
      metricLabel: '执行主通道',
      description: '负责把指数判断落成真实交易对象，是当前默认执行主通道。',
      highlights: [
        `ETF 持仓 ${normalizeCount(etfOverview.etfPositionCount)} 个`,
        `风险预警 ${normalizeCount(etfOverview.etfRiskWarningCount)} 条`
      ],
      actionLabel: '去执行主线'
    },
    {
      key: 'equity',
      title: '个股',
      summary: `${equityReviewCount} 个待复盘`,
      metricLabel: '复盘与治理',
      description: '负责个体 alpha、持仓表现与治理动作，不在首屏抢主线。',
      highlights: [
        `待复盘 ${equityReviewCount} 个`,
        `待执行 ${normalizeCount(executionFeedbackSummary.pendingSignalCount)} 条`
      ],
      actionLabel: '去个股复盘'
    }
  ]
}

module.exports = {
  buildDashboardObjectLayers
}
