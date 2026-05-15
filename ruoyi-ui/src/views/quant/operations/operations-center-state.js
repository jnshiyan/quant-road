function buildOperationsCenterState(payload = {}) {
  const topBlocker = payload.topBlocker || {}
  const recoveryQueue = Array.isArray(payload.recoveryQueue)
    ? payload.recoveryQueue.filter(item => item && item.executable !== false)
    : []
  const toolbox = payload.toolbox || {}

  return {
    blockerTitle: topBlocker.title || '暂无阻断',
    blockerBadge: topBlocker.impact || '无影响',
    blockerReason: topBlocker.reason || '当前没有需要处理的运维阻断。',
    primaryRecovery: recoveryQueue[0] || null,
    secondaryRecoveries: recoveryQueue.slice(1, 3),
    compatibilityActions: Array.isArray(toolbox.compatibilityActions) ? toolbox.compatibilityActions : [],
    navigation: Array.isArray(toolbox.navigation) ? toolbox.navigation : []
  }
}

module.exports = {
  buildOperationsCenterState
}
