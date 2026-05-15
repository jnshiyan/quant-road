function buildJobCenterGuidance(payload = {}) {
  const jobReadiness = payload.jobReadiness || {}
  const asyncWorkerSummary = payload.asyncWorkerSummary || {}
  const currentJobStatus = payload.currentJobStatus || {}
  const currentBatchId = payload.currentBatchId

  const dailyOps = []
  const troubleshooting = []

  if (currentJobStatus.jobId || currentJobStatus.status) {
    dailyOps.push(`当前任务状态 ${currentJobStatus.status || 'UNKNOWN'}，先确认主任务是否仍在推进。`)
  } else {
    dailyOps.push('当前没有活跃任务，可优先执行日常盘后主流程。')
  }

  if (jobReadiness.status === 'READY') {
    dailyOps.push('当前批次已业务就绪，可进入看板、执行或复盘页继续运营。')
  } else if (jobReadiness.status === 'READY_WITH_WARNINGS') {
    dailyOps.push('当前批次可继续运营，但建议先消化警告项，避免问题扩散。')
  } else if (jobReadiness.status === 'RUNNING') {
    dailyOps.push('当前批次仍在运行，先观察步骤推进，不建议重复提交同类任务。')
  } else if (jobReadiness.status === 'BLOCKED') {
    dailyOps.push('当前批次存在阻断项，应先解决阻断问题，再决定是否继续运营。')
  }

  if (jobReadiness.dataIntegrityStatus === 'BLOCKED') {
    dailyOps.push('当前数据完整性被阻断，先不要进入看板或执行页。')
  } else if (jobReadiness.dataIntegrityStatus === 'WARNING') {
    dailyOps.push('当前数据完整性存在警告，建议先核对异常标的或降级步骤。')
  }

  if (jobReadiness.dataIntegrityStatus === 'BLOCKED') {
    troubleshooting.push(jobReadiness.dataIntegrityMessage || '当前盘后结果存在数据完整性问题，应先进入调度中心核对。')
  } else if (asyncWorkerSummary.status === 'BLOCKED') {
    troubleshooting.push('存在待消费分片但没有活跃后台执行器，应先恢复消费能力。')
  } else if (asyncWorkerSummary.status === 'DEGRADED') {
    troubleshooting.push('长任务队列存在失败或过期分片，应先恢复分片再判断主任务结果。')
  } else {
    troubleshooting.push('当前后台执行器状态可用，排障重点转向失败批次和异常步骤。')
  }

  if (jobReadiness.canRecover && currentBatchId) {
    troubleshooting.push(`批次 ${currentBatchId} 支持恢复，优先使用“恢复失败批次”收敛失败步骤。`)
  } else {
    troubleshooting.push('若没有可恢复批次，可先看错误分类和 SOP 建议，再决定是否重跑。')
  }

  return {
    dailyOps,
    troubleshooting,
    workerHeadline: asyncWorkerSummary.message || '当前暂无 worker 运维摘要',
    workerPriority: asyncWorkerSummary.status === 'BLOCKED' ? 'HIGH' : (asyncWorkerSummary.status === 'DEGRADED' ? 'MEDIUM' : 'LOW')
  }
}

module.exports = {
  buildJobCenterGuidance
}
