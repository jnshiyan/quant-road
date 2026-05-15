function toNumber(value) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

function governanceActionLabel(action) {
  if (action === 'REVIEW') return '优先复盘'
  if (action === 'BUILD_POSITION') return '待建仓'
  if (action === 'KEEP_PRIMARY') return '保持主ETF'
  if (action === 'OBSERVE_MAPPING') return '观察映射'
  return action || '待确认'
}

function governanceActionType(action) {
  if (action === 'REVIEW') return 'danger'
  if (action === 'BUILD_POSITION') return 'warning'
  if (action === 'KEEP_PRIMARY') return 'success'
  return 'info'
}

function governanceActionPriority(action) {
  if (action === 'REVIEW') return 0
  if (action === 'BUILD_POSITION') return 1
  if (action === 'OBSERVE_MAPPING') return 2
  if (action === 'KEEP_PRIMARY') return 3
  return 4
}

function buildHeadline(actionCounts, summary) {
  if (actionCounts.REVIEW > 0) {
    return `当前有 ${actionCounts.REVIEW} 个主ETF需要优先复盘，应先处理风险预警和执行缺口。`
  }
  if (actionCounts.BUILD_POSITION > 0) {
    return `当前有 ${actionCounts.BUILD_POSITION} 个主ETF进入待建仓阶段，建议先确认信号是否真正落地。`
  }
  if (actionCounts.OBSERVE_MAPPING > 0) {
    return `当前主ETF暂无明显风险，但仍有 ${actionCounts.OBSERVE_MAPPING} 个映射对象需要继续观察承接稳定性。`
  }
  if (toNumber(summary.indexMappingCount) > 0) {
    return '当前 ETF 承接主链整体稳定，可按映射巡检和抽样复盘节奏继续维护。'
  }
  return '当前暂无 ETF 治理对象，请先检查 ETF 池和指数映射是否完成初始化。'
}

function buildSummaryLines(summary) {
  return [
    `指数映射 ${toNumber(summary.indexMappingCount)} 条，备选 ETF ${toNumber(summary.candidateEtfCount)} 个。`,
    `当前主 ETF 持仓 ${toNumber(summary.holdingCount)} 个，活跃信号 ${toNumber(summary.activeSignalCount)} 个。`,
    `风险预警 ${toNumber(summary.riskWarningCount)} 个，待补执行闭环 ${toNumber(summary.pendingExecutionCount)} 条。`
  ]
}

function buildActionStats(rows) {
  const counts = rows.reduce((result, row) => {
    const action = row && row.governanceAction ? row.governanceAction : 'UNKNOWN'
    result[action] = (result[action] || 0) + 1
    return result
  }, {})

  return ['REVIEW', 'BUILD_POSITION', 'OBSERVE_MAPPING', 'KEEP_PRIMARY'].map(action => ({
    action,
    label: governanceActionLabel(action),
    type: governanceActionType(action),
    count: counts[action] || 0
  }))
}

function buildPriorityQueue(rows) {
  return rows
    .slice()
    .sort((left, right) => {
      const actionCompare = governanceActionPriority(left.governanceAction) - governanceActionPriority(right.governanceAction)
      if (actionCompare !== 0) {
        return actionCompare
      }
      const pendingCompare = toNumber(right.pendingExecutionCount) - toNumber(left.pendingExecutionCount)
      if (pendingCompare !== 0) {
        return pendingCompare
      }
      const riskCompare = toNumber(right.riskWarning) - toNumber(left.riskWarning)
      if (riskCompare !== 0) {
        return riskCompare
      }
      return toNumber(right.holdingQuantity) - toNumber(left.holdingQuantity)
    })
    .slice(0, 4)
    .map(row => ({
      title: `${row.primaryEtfCode || '-'} ${row.primaryEtfName || ''}`.trim(),
      action: row.governanceAction || 'UNKNOWN',
      actionLabel: governanceActionLabel(row.governanceAction),
      actionType: governanceActionType(row.governanceAction),
      summary: `${row.indexCode || '-'} ${row.indexName || ''}`.trim(),
      reason: row.governanceReason || '当前暂无治理说明。',
      source: row
    }))
}

function buildChartOption(actionStats) {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: {
      left: 36,
      right: 20,
      top: 36,
      bottom: 28
    },
    xAxis: {
      type: 'category',
      data: actionStats.map(item => item.label),
      axisTick: { alignWithLabel: true }
    },
    yAxis: {
      type: 'value',
      minInterval: 1
    },
    series: [
      {
        name: '治理对象数',
        type: 'bar',
        barWidth: 28,
        data: actionStats.map(item => ({
          value: item.count,
          itemStyle: {
            color: item.action === 'REVIEW'
              ? '#f56c6c'
              : item.action === 'BUILD_POSITION'
                ? '#e6a23c'
                : item.action === 'KEEP_PRIMARY'
                  ? '#67c23a'
                  : '#909399'
          }
        }))
      }
    ]
  }
}

function monthKey(value) {
  const text = value ? String(value) : ''
  if (text.length >= 7) {
    return text.slice(0, 7)
  }
  return '未知月份'
}

function reviewConclusionLabel(value) {
  if (value === 'KEEP') return '继续持有'
  if (value === 'OBSERVE') return '继续观察'
  if (value === 'REDUCE') return '降低暴露'
  if (value === 'EXIT') return '退出治理'
  if (value === 'REPLACE') return '切换对象'
  return value || '未提交结论'
}

function buildHistoryTrendChart(cases) {
  const monthly = cases.reduce((result, item) => {
    const key = monthKey(item.lastDetectedTime)
    result[key] = (result[key] || 0) + 1
    return result
  }, {})
  const categories = Object.keys(monthly).sort()
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: {
      left: 36,
      right: 16,
      top: 28,
      bottom: 28
    },
    xAxis: {
      type: 'category',
      data: categories
    },
    yAxis: {
      type: 'value',
      minInterval: 1
    },
    series: [
      {
        name: 'ETF formal case',
        type: 'line',
        smooth: true,
        data: categories.map(item => monthly[item]),
        lineStyle: {
          width: 3,
          color: '#e6a23c'
        },
        itemStyle: {
          color: '#e6a23c'
        },
        areaStyle: {
          color: 'rgba(230, 162, 60, 0.16)'
        }
      }
    ]
  }
}

function buildConclusionStats(cases) {
  const counts = cases.reduce((result, item) => {
    const key = item.lastReviewConclusion || 'UNREVIEWED'
    result[key] = (result[key] || 0) + 1
    return result
  }, {})
  return Object.keys(counts).map(key => ({
    key,
    label: key === 'UNREVIEWED' ? '未提交结论' : reviewConclusionLabel(key),
    count: counts[key]
  })).sort((left, right) => right.count - left.count)
}

function buildEtfReviewHistoryViewModel(cases = []) {
  const rows = Array.isArray(cases) ? cases : []
  const openCount = rows.filter(item => item.resolutionStatus === 'OPEN').length
  const closedCount = rows.filter(item => item.resolutionStatus && item.resolutionStatus !== 'OPEN').length
  const reviewedCount = rows.filter(item => item.lastReviewConclusion).length
  const latestDetected = rows.length ? rows[0].lastDetectedTime : ''

  return {
    headline: rows.length
      ? `最近共沉淀 ${rows.length} 条 ETF formal case，其中 OPEN ${openCount} 条、已形成结论 ${reviewedCount} 条。`
      : '当前暂无 ETF formal case 历史记录。',
    summaryStats: [
      { label: '历史 case', value: rows.length },
      { label: '待处理 OPEN', value: openCount },
      { label: '已关闭', value: closedCount },
      { label: '已出结论', value: reviewedCount }
    ],
    conclusionStats: buildConclusionStats(rows),
    trendChartOption: buildHistoryTrendChart(rows),
    latestDetected
  }
}

function buildObjectHotspotViewModel(cases = []) {
  const grouped = (Array.isArray(cases) ? cases : []).reduce((result, item) => {
    const key = item.stockCode || item.reviewTargetName || `case-${item.caseId || ''}`
    if (!result[key]) {
      result[key] = {
        key,
        label: item.reviewTargetName || item.stockCode || '未知对象',
        caseCount: 0,
        openCount: 0,
        latestDetectedTime: item.lastDetectedTime || ''
      }
    }
    result[key].caseCount += 1
    if (item.resolutionStatus === 'OPEN') {
      result[key].openCount += 1
    }
    if (String(item.lastDetectedTime || '') > String(result[key].latestDetectedTime || '')) {
      result[key].latestDetectedTime = item.lastDetectedTime
    }
    return result
  }, {})

  const hotspots = Object.values(grouped)
    .sort((left, right) => {
      if (right.caseCount !== left.caseCount) {
        return right.caseCount - left.caseCount
      }
      if (right.openCount !== left.openCount) {
        return right.openCount - left.openCount
      }
      return String(right.latestDetectedTime || '').localeCompare(String(left.latestDetectedTime || ''))
    })
    .slice(0, 5)

  return {
    headline: hotspots.length
      ? `当前最值得持续观察的 ETF 对象集中在 ${hotspots[0].label} 等 ${hotspots.length} 个对象。`
      : '当前暂无 ETF 热点对象。',
    hotspots,
    chartOption: {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' }
      },
      legend: {
        top: 0
      },
      grid: {
        left: 36,
        right: 16,
        top: 36,
        bottom: 28
      },
      xAxis: {
        type: 'category',
        data: hotspots.map(item => item.label)
      },
      yAxis: {
        type: 'value',
        minInterval: 1
      },
      series: [
        {
          name: '历史 case',
          type: 'bar',
          data: hotspots.map(item => item.caseCount),
          itemStyle: {
            color: '#409eff'
          }
        },
        {
          name: 'OPEN',
          type: 'bar',
          data: hotspots.map(item => item.openCount),
          itemStyle: {
            color: '#e6a23c'
          }
        }
      ]
    }
  }
}

function buildAssetGovernanceComparisonViewModel(payload = {}) {
  const etfCases = Array.isArray(payload.etfCases) ? payload.etfCases : []
  const equityCases = Array.isArray(payload.equityCases) ? payload.equityCases : []
  const symbolRows = Array.isArray(payload.symbolRows) ? payload.symbolRows : []
  const governanceRows = Array.isArray(payload.etfGovernanceRows) ? payload.etfGovernanceRows : []
  const etfCodes = new Set([
    ...governanceRows.map(item => item.primaryEtfCode).filter(Boolean),
    ...etfCases.map(item => item.stockCode).filter(Boolean)
  ])

  const etfCurrentCount = governanceRows.filter(item => item.governanceAction && item.governanceAction !== 'KEEP_PRIMARY').length
  const equityCurrentCount = symbolRows.filter(item => (
    !etfCodes.has(item.stockCode) &&
    (toNumber(item.pendingSignalCount) > 0 || toNumber(item.abnormalFeedbackCount) > 0 || toNumber(item.unmatchedExecutionCount) > 0)
  )).length

  const etfOpenCount = etfCases.filter(item => item.resolutionStatus === 'OPEN').length
  const equityOpenCount = equityCases.filter(item => item.resolutionStatus === 'OPEN').length

  return {
    headline: etfOpenCount > equityOpenCount
      ? `ETF 当前未结 formal case 多于个股，治理重点更偏向承接层。`
      : (equityOpenCount > etfOpenCount
        ? '个股当前未结 formal case 更多，治理重点更偏向主动交易层。'
        : 'ETF 与个股当前未结 formal case 相近，建议并行治理承接层与主动交易层。'),
    rows: [
      {
        assetType: 'ETF',
        currentIssues: etfCurrentCount,
        historyCases: etfCases.length,
        openCases: etfOpenCount,
        reviewedCases: etfCases.filter(item => item.lastReviewConclusion).length
      },
      {
        assetType: 'EQUITY',
        currentIssues: equityCurrentCount,
        historyCases: equityCases.length,
        openCases: equityOpenCount,
        reviewedCases: equityCases.filter(item => item.lastReviewConclusion).length
      }
    ],
    chartOption: {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' }
      },
      legend: {
        top: 0
      },
      grid: {
        left: 36,
        right: 16,
        top: 36,
        bottom: 28
      },
      xAxis: {
        type: 'category',
        data: ['ETF', '个股']
      },
      yAxis: {
        type: 'value',
        minInterval: 1
      },
      series: [
        {
          name: '当前待治理',
          type: 'bar',
          data: [etfCurrentCount, equityCurrentCount],
          itemStyle: { color: '#e6a23c' }
        },
        {
          name: '历史 formal case',
          type: 'bar',
          data: [etfCases.length, equityCases.length],
          itemStyle: { color: '#409eff' }
        },
        {
          name: 'OPEN case',
          type: 'bar',
          data: [etfOpenCount, equityOpenCount],
          itemStyle: { color: '#f56c6c' }
        }
      ]
    }
  }
}

function buildEtfGovernanceViewModel(payload = {}) {
  const summary = payload.summary || {}
  const rows = Array.isArray(payload.mappingGovernanceRows) ? payload.mappingGovernanceRows : []
  const actionStats = buildActionStats(rows)
  const actionCounts = actionStats.reduce((result, item) => {
    result[item.action] = item.count
    return result
  }, {})

  return {
    headline: buildHeadline(actionCounts, summary),
    summaryLines: buildSummaryLines(summary),
    actionStats,
    priorityQueue: buildPriorityQueue(rows),
    chartOption: buildChartOption(actionStats)
  }
}

module.exports = {
  buildEtfGovernanceViewModel,
  buildEtfReviewHistoryViewModel,
  buildObjectHotspotViewModel,
  buildAssetGovernanceComparisonViewModel,
  governanceActionLabel,
  governanceActionType,
  reviewConclusionLabel
}
