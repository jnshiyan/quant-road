function normalizeText(value, fallback = '-') {
  if (value === null || value === undefined || value === '') {
    return fallback
  }
  return String(value)
}

function normalizeProgressLabel(jobStatus = {}, options = {}) {
  const completed = Number(jobStatus.completedShardCount || 0)
  const planned = Number(jobStatus.plannedShardCount || 0)
  if (planned <= 0) {
    return options.withUnit ? '- / - 分片' : '-'
  }
  return options.withUnit ? `${completed} / ${planned} 分片` : `${completed}/${planned}`
}

function normalizeCurrentObject(primary = {}) {
  const currentObject = primary.currentObjectLabel || primary.currentObject || primary.currentSymbolsLabel
  if (currentObject) {
    return String(currentObject)
  }
  if (Array.isArray(primary.currentSymbols) && primary.currentSymbols.length) {
    return primary.currentSymbols.join(', ')
  }
  if (primary.currentSymbols) {
    return String(primary.currentSymbols)
  }
  return '暂无'
}

function normalizeProgressPercent(jobStatus = {}) {
  const completed = Number(jobStatus.completedShardCount || 0)
  const planned = Number(jobStatus.plannedShardCount || 0)
  if (planned <= 0) {
    return 0
  }
  return Math.max(0, Math.min(100, Math.round((completed / planned) * 100)))
}

function normalizeProgressHint(merged = {}, jobStatus = {}, progressLabel = '-') {
  const waiting = normalizeText(merged.waitingLabel || merged.waitingTarget || merged.waitingFor, '')
  const planned = Number(jobStatus.plannedShardCount || 0)
  if (planned > 0) {
    return waiting
      ? `已完成 ${progressLabel} 分片，当前等待：${waiting}`
      : `已完成 ${progressLabel} 分片`
  }
  return waiting || '等待系统返回更多进度'
}

function buildJobObservabilitySummary(payload = {}) {
  const historyRecord = payload.historyRecord || {}
  const jobStatus = payload.jobStatus || {}
  const detailState = payload.detailState || {}
  const primaryTask = payload.primaryTask || {}
  const merged = {
    ...historyRecord,
    ...primaryTask,
    ...detailState
  }

  const scopeLabel = normalizeText(
    merged.scopeSummary || merged.scopeLabel,
    '当前任务'
  )
  const progressLabel = normalizeProgressLabel(jobStatus, payload.progressOptions || {})

  return {
    headline: `${scopeLabel}正在执行`,
    statusText: normalizeText(jobStatus.status || merged.status, 'IDLE'),
    progressLabel,
    progressPercent: normalizeProgressPercent(jobStatus),
    progressHint: normalizeProgressHint(merged, jobStatus, progressLabel),
    timeRangeLabel: normalizeText(merged.timeRangeSummary),
    scopeLabel,
    currentStageLabel: normalizeText(merged.currentStageLabel || merged.currentStage, '等待系统判定'),
    currentObjectLabel: normalizeCurrentObject(merged),
    waitingLabel: normalizeText(merged.waitingLabel || merged.waitingTarget || merged.waitingFor, '等待下一步'),
    nextStepLabel: normalizeText(merged.nextStepLabel, '等待下一步')
  }
}

module.exports = {
  buildJobObservabilitySummary
}
