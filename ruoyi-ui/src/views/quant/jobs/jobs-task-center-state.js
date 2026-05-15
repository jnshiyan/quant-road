function normalizeAction(action = {}, fallback = {}) {
  return {
    code: action.code || fallback.code || 'REFRESH_STATUS',
    label: action.label || fallback.label || '刷新状态',
    targetPage: action.targetPage || fallback.targetPage || '/quant/jobs'
  }
}

function isActiveTaskStatus(status = '') {
  return ['RUNNING', 'QUEUED', 'PENDING'].includes(status)
}

function shouldUseHistoricalTaskAsPrimary(primaryTask = {}, todayStatus = {}) {
  if (isActiveTaskStatus(primaryTask.status)) {
    return true
  }
  return resolveStatusCode(todayStatus) === 'BLOCKED'
}

function resolveStatusCode(todayStatus = {}) {
  return todayStatus.statusCode || todayStatus.code || 'WARNING'
}

function resolveStatusLabel(todayStatus = {}) {
  if (todayStatus.statusLabel) {
    return todayStatus.statusLabel
  }
  if (todayStatus.label) {
    return todayStatus.label
  }
  const code = resolveStatusCode(todayStatus)
  if (code === 'OPERABLE') {
    return '可用'
  }
  if (code === 'BLOCKED') {
    return '阻断'
  }
  if (code === 'RUNNING') {
    return '运行中'
  }
  return '警告'
}

function resolvePrimaryAction(todayStatus = {}, nextAction = {}, primaryTask = {}) {
  const useHistoricalTask = shouldUseHistoricalTaskAsPrimary(primaryTask, todayStatus)
  if (nextAction.code && useHistoricalTask) {
    return normalizeAction(nextAction)
  }
  if (isActiveTaskStatus(primaryTask.status)) {
    return normalizeAction({}, {
      code: 'VIEW_CURRENT_DISPATCH',
      label: '查看当前调度',
      targetPage: '/quant/dispatch-detail'
    })
  }
  const statusCode = resolveStatusCode(todayStatus)
  if (statusCode === 'OPERABLE') {
    return normalizeAction({}, {
      code: 'RUN_EXECUTION',
      label: '发起手工调度',
      targetPage: '/quant/dispatch-manual'
    })
  }
  if (statusCode === 'BLOCKED') {
    return normalizeAction({}, {
      code: 'GO_OPERATIONS',
      label: '去运维中心处理',
      targetPage: '/quant/operations'
    })
  }
  return normalizeAction({}, {
    code: 'RUN_EXECUTION',
    label: '发起手工调度',
    targetPage: '/quant/dispatch-manual'
  })
}

function resolveSecondaryActions(todayStatus = {}, primaryAction = {}, secondaryAction = null) {
  const statusCode = resolveStatusCode(todayStatus)
  const isIdleOperable = statusCode !== 'BLOCKED' && todayStatus.canContinue !== false
  if (todayStatus.canContinue === false && !secondaryAction) {
    return []
  }
  if (secondaryAction && !isIdleOperable) {
    return [normalizeAction(secondaryAction)]
  }
  const actions = []
  if (primaryAction.code !== 'REFRESH_STATUS') {
    actions.push(normalizeAction({}, {
      code: 'REFRESH_STATUS',
      label: '刷新状态',
      targetPage: '/quant/jobs'
    }))
  }
  if (statusCode === 'OPERABLE' && primaryAction.code !== 'GO_DASHBOARD' && !isIdleOperable) {
    actions.push(normalizeAction({}, {
      code: 'GO_DASHBOARD',
      label: '进入量化看板',
      targetPage: '/quant/dashboard'
    }))
  }
  return actions.slice(0, 1)
}

function buildTechnicalSummaryRows(technicalSummary = {}, primaryTaskView = {}) {
  return [
    { label: '任务 ID', value: technicalSummary.batchId || '-' },
    { label: '步骤进度', value: primaryTaskView.progressSummary || '-' },
    { label: 'Worker 状态', value: technicalSummary.workerStatus || '-' },
    { label: '异步任务数', value: technicalSummary.asyncJobCount ?? '-' }
  ]
}

function resolvePageStatusLine(todayStatus = {}, primaryTask = {}) {
  const statusCode = resolveStatusCode(todayStatus)
  if (statusCode === 'BLOCKED') {
    return '当前存在阻断问题，应先处理后再发起新调度。'
  }
  if (['RUNNING', 'QUEUED', 'PENDING'].includes(primaryTask.status) || statusCode === 'RUNNING') {
    return '当前有调度正在执行，先查看当前调度，不建议重复提交。'
  }
  return '当前没有运行中的调度，可以直接发起手工调度。'
}

function buildPrimaryTaskHint(primaryTaskView = {}, statusCode = 'WARNING') {
  if (statusCode === 'BLOCKED') {
    return primaryTaskView.nextStep || '先进入运维中心恢复阻断，再决定是否重新发起调度。'
  }
  if (isActiveTaskStatus(primaryTaskView.status)) {
    return primaryTaskView.nextStep || '先查看当前调度详情，确认当前步骤和等待对象。'
  }
  return '当前没有运行中的任务，可直接发起新的手工调度。'
}

function buildIdlePrimaryTaskView() {
  return {
    taskName: '当前无运行任务',
    status: 'IDLE',
    currentStep: '等待开始',
    scopeSummary: '未指定范围',
    timeRangeSummary: '未指定时间范围',
    nextStep: '发起手工调度',
    progressSummary: '0 / 0',
    triggerModeLabel: '-',
    requiresManualIntervention: false
  }
}

function buildPrimaryTaskView(primaryTask = {}, todayStatus = {}) {
  if (!shouldUseHistoricalTaskAsPrimary(primaryTask, todayStatus)) {
    return buildIdlePrimaryTaskView()
  }
  return {
    taskName: primaryTask.taskName || '当前无运行任务',
    status: primaryTask.status || 'PENDING',
    currentStep: primaryTask.currentStep || primaryTask.currentStage || '等待开始',
    scopeSummary: primaryTask.scopeSummary || '未指定范围',
    timeRangeSummary: primaryTask.timeRangeSummary || '未指定时间范围',
    nextStep: primaryTask.nextStep || primaryTask.waitingTarget || primaryTask.waitingFor || '等待系统更新',
    progressSummary: primaryTask.progressSummary || '0 / 0',
    triggerModeLabel: primaryTask.triggerModeLabel || '-',
    requiresManualIntervention: primaryTask.requiresManualIntervention === true
  }
}

function buildTaskCenterState(summary = {}) {
  const todayStatus = summary.todayStatus || {}
  const primaryTask = summary.primaryTask || {}
  const primaryTaskView = buildPrimaryTaskView(primaryTask, todayStatus)
  const primaryAction = resolvePrimaryAction(todayStatus, summary.nextAction || {}, primaryTask)
  const secondaryActions = resolveSecondaryActions(todayStatus, primaryAction, todayStatus.secondaryAction)
  const todayStatusCode = resolveStatusCode(todayStatus)

  return {
    todayStatusCode,
    todayStatusLabel: resolveStatusLabel(todayStatus),
    pageStatusLine: resolvePageStatusLine(todayStatus, primaryTask),
    primaryTaskHint: buildPrimaryTaskHint(primaryTaskView, todayStatusCode),
    todayStatusHeadlineAction: todayStatus.headlineAction || summary.todayHeadline || '先确认今日流程是否业务就绪',
    todayStatusContinueLabel: todayStatus.continueLabel || '可以继续查看',
    todayStatusCanContinue: todayStatus.canContinue !== false,
    todayStatusReason: todayStatus.reason || summary.todayReason || '当前缺少可执行的业务结论',
    todayStatusUrgency: todayStatus.urgency || 'later',
    todayStatusSuggestion: todayStatus.suggestion || '',
    heroPrimaryAction: primaryAction,
    primaryAction,
    secondaryActions,
    progressEvents: Array.isArray(summary.progressEvents) ? summary.progressEvents.slice(0, 5) : [],
    technicalSummaryRows: buildTechnicalSummaryRows(summary.technicalSummary || {}, primaryTaskView),
    primaryTaskView,
    nextScheduledDispatch: summary.nextScheduledDispatch || {},
    dispatchHistoryRows: summary.dispatchHistory && Array.isArray(summary.dispatchHistory.rows) ? summary.dispatchHistory.rows : []
  }
}

module.exports = {
  buildTaskCenterState
}
