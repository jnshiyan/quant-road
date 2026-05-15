function clampElapsedSeconds(startedAt, now) {
  const start = Number(startedAt || 0)
  const current = Number(now || 0)
  if (!start || current <= start) {
    return 0
  }
  return Math.max(0, Math.floor((current - start) / 1000))
}

function normalizeErrorMessage(errorMessage) {
  if (!errorMessage) {
    return '提交失败，请检查接口响应或后台日志。'
  }
  return String(errorMessage)
}

function buildManualDispatchSubmitView(state = {}, options = {}) {
  const status = state.status || 'idle'
  const elapsedSeconds = clampElapsedSeconds(state.startedAt, options.now || Date.now())
  const jobId = state.jobId || ''

  if (status === 'submitting') {
    return {
      status,
      elapsedSeconds,
      phaseLabel: '正在提交调度任务',
      detail: '请求已发送，正在等待服务端返回任务回执。',
      expectation: '拿到任务 ID 后会自动跳转到调度详情页，后续执行状态在那里持续刷新。',
      tone: 'info'
    }
  }

  if (status === 'redirecting') {
    return {
      status,
      elapsedSeconds,
      phaseLabel: '任务回执已生成',
      detail: `已拿到任务 ID ${jobId || '-'}，正在进入调度详情页。`,
      expectation: '调度详情页会继续显示当前阶段、等待对象、实时日志和分片进度。',
      tone: 'success'
    }
  }

  if (status === 'failed') {
    return {
      status,
      elapsedSeconds,
      phaseLabel: '任务提交失败',
      detail: normalizeErrorMessage(state.errorMessage),
      expectation: '请先处理失败原因，再重新提交。',
      tone: 'danger'
    }
  }

  return {
    status: 'idle',
    elapsedSeconds: 0,
    phaseLabel: '提交后先创建调度任务',
    detail: '手工调度不会在当前页面阻塞执行，会先创建调度任务并等待服务端返回任务回执。',
    expectation: '任务回执生成后自动进入调度详情页，继续查看当前阶段与执行日志。',
    tone: 'neutral'
  }
}

module.exports = {
  buildManualDispatchSubmitView
}
