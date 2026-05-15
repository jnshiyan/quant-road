function normalizeText(value, fallback = '-') {
  if (value === undefined || value === null) {
    return fallback
  }
  const text = String(value).trim()
  return text ? text : fallback
}

function resolveStatusText(jobStatus = null, historyRecord = {}) {
  if (jobStatus && jobStatus.status) {
    return jobStatus.status
  }
  return historyRecord.status || 'UNKNOWN'
}

function resolveStatusLabel(statusText = 'UNKNOWN') {
  if (statusText === 'SUCCESS') return '已完成'
  if (statusText === 'FAILED') return '失败'
  if (statusText === 'PARTIAL_FAILED') return '部分失败'
  if (statusText === 'RUNNING') return '运行中'
  if (statusText === 'QUEUED') return '排队中'
  if (statusText === 'PENDING') return '待开始'
  return statusText
}

function resolveStatusType(statusText = 'UNKNOWN') {
  if (statusText === 'SUCCESS') return 'success'
  if (statusText === 'FAILED' || statusText === 'PARTIAL_FAILED') return 'danger'
  if (['RUNNING', 'QUEUED', 'PENDING'].includes(statusText)) return 'warning'
  return 'info'
}

function resolveRefreshHint(autoRefresh, nextRefreshInSeconds, statusText = 'UNKNOWN') {
  if (!autoRefresh) {
    return '自动刷新已关闭'
  }
  if (!['QUEUED', 'PENDING', 'RUNNING'].includes(statusText)) {
    return '当前状态稳定，无需继续自动刷新'
  }
  if (Number(nextRefreshInSeconds || 0) > 0) {
    return `${nextRefreshInSeconds} 秒后自动刷新`
  }
  return '即将自动刷新'
}

function resolveFirstScreenMode(statusText = 'UNKNOWN') {
  if (['SUCCESS', 'FAILED', 'PARTIAL_FAILED'].includes(statusText)) {
    return 'outcome-first'
  }
  return 'progress-first'
}

function formatMetric(value, suffix = '') {
  if (value === undefined || value === null || value === '') {
    return null
  }
  return `${value}${suffix}`
}

function buildResultBreakdown(results = []) {
  const counts = new Map()
  ;(Array.isArray(results) ? results : []).forEach(item => {
    const key = normalizeText(item && item.signal_type, '未标记')
    counts.set(key, (counts.get(key) || 0) + 1)
  })
  return Array.from(counts.entries()).map(([label, count]) => ({
    label,
    value: `${count} 条`
  }))
}

function buildResultPreviewRows(results = []) {
  return (Array.isArray(results) ? results : [])
    .slice(0, 3)
    .map(item => {
      const metrics = [
        formatMetric(item.strategy_id, ' 策略'),
        formatMetric(item.annual_return, '%'),
        formatMetric(item.max_drawdown, '% 回撤')
      ].filter(Boolean)
      return {
        title: [normalizeText(item.stock_code, '-'), normalizeText(item.signal_type, '未标记')].join(' / '),
        subtitle: metrics.length ? metrics.join(' · ') : normalizeText(item.remark, '当前没有附加指标')
      }
    })
}

function buildFactRows(detailSummary = {}, detailPayload = {}, statusText = 'UNKNOWN') {
  return [
    { label: '任务', value: normalizeText(detailSummary.taskName || detailPayload.taskName, '量化调度任务') },
    { label: '执行内容', value: normalizeText(detailSummary.executionSummary, '未记录执行步骤') },
    { label: '执行范围', value: normalizeText(detailSummary.scopeSummary, '未记录范围') },
    { label: '时间范围', value: normalizeText(detailSummary.timeRangeSummary, '未记录时间范围') },
    { label: '触发方式', value: normalizeText(detailSummary.triggerModeLabel, '-') },
    { label: '状态', value: resolveStatusLabel(statusText) },
    { label: '当前阶段', value: normalizeText(detailPayload.phaseLabel, '待判断') },
    { label: '分片进度', value: normalizeText(detailSummary.shardProgress, '-') },
    { label: '下一步', value: normalizeText(detailPayload.nextStepLabel || detailSummary.nextStepLabel, '等待系统更新') },
    { label: '提交时间', value: normalizeText(detailSummary.startedAt, '-') },
    { label: '结束时间', value: normalizeText(detailSummary.finishedAt, '-') }
  ]
}

function buildOutcomeStats(detailSummary = {}, errorCategories = []) {
  return [
    { label: '结果条数', value: String(detailSummary.resultCount ?? 0) },
    { label: '异常类别', value: String(detailSummary.errorCount ?? errorCategories.length ?? 0) },
    { label: '结果摘要', value: normalizeText(detailSummary.resultSummary, '当前没有结果摘要') }
  ]
}

function buildDispatchDetailState(input = {}) {
  const detailPayload = input.detailPayload || {}
  const historyRecord = input.historyRecord || {}
  const jobStatus = input.jobStatus || null
  const events = Array.isArray(input.events) ? input.events : []
  const errorCategories = Array.isArray(input.errorCategories) ? input.errorCategories : []
  const results = Array.isArray(input.results) ? input.results : []
  const detailSummary = detailPayload.detailSummary || {}
  const statusText = resolveStatusText(jobStatus, historyRecord)
  const latestEvent = events.length ? events[0] : null

  return {
    statusText,
    firstScreenMode: resolveFirstScreenMode(statusText),
    statusLabel: resolveStatusLabel(statusText),
    statusType: resolveStatusType(statusText),
    pageTitle: normalizeText(detailSummary.taskName, '调度详情'),
    resultSummary: normalizeText(detailSummary.resultSummary, '当前没有结果摘要'),
    refreshHint: resolveRefreshHint(input.autoRefresh !== false, input.nextRefreshInSeconds || 0, statusText),
    factRows: buildFactRows(detailSummary, detailPayload, statusText),
    outcomeStats: buildOutcomeStats(detailSummary, errorCategories),
    resultBreakdown: buildResultBreakdown(results),
    resultPreviewRows: buildResultPreviewRows(results),
    latestLogLabel: latestEvent && (latestEvent.stepName || latestEvent.message)
      ? (latestEvent.stepName || latestEvent.message)
      : '当前还没有可展示的结构化日志',
    latestLogTime: latestEvent ? (latestEvent.endTime || latestEvent.startTime || '-') : '-'
  }
}

module.exports = {
  buildDispatchDetailState
}
