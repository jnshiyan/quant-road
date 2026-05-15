function hasText(value) {
  return typeof value === 'string' && value.trim().length > 0
}

function hasSymbols(values) {
  return Array.isArray(values) && values.some(item => hasText(item))
}

function scopeLabel(scopeType) {
  if (scopeType === 'stock_pool') return '个股池'
  if (scopeType === 'etf_pool') return 'ETF池'
  if (scopeType === 'index_mapped_etf_pool') return '指数映射ETF池'
  return '全市场'
}

function resolveLoadLevel(constrained, resolvedCount) {
  if (!constrained) {
    return 'heavy'
  }
  if (resolvedCount <= 10) {
    return 'light'
  }
  if (resolvedCount <= 100) {
    return 'medium'
  }
  return 'heavy'
}

function isConstrainedExecutionScope(payload = {}) {
  if (hasText(payload.scopePoolCode)) {
    return true
  }
  if (hasSymbols(payload.symbols) || hasSymbols(payload.whitelist) || hasSymbols(payload.blacklist) || hasSymbols(payload.adHocSymbols)) {
    return true
  }
  return hasText(payload.scopeType) && payload.scopeType.trim() !== 'all_stocks'
}

function buildExecutionScopeGuard(payload = {}) {
  const constrained = isConstrainedExecutionScope(payload)
  const resolvedCount = Number(payload.resolvedCount) || 0
  const label = scopeLabel(payload.scopeType)
  const suffix = hasText(payload.scopePoolCode) ? ` ${payload.scopePoolCode}` : ''
  const loadLevel = resolveLoadLevel(constrained, resolvedCount)

  if (!constrained) {
    return {
      level: 'warning',
      loadLevel,
      summary: '当前是广范围执行任务，系统会按全市场主链处理。',
      requiresConfirmation: true,
      message: '当前执行任务未收敛到股票池或 ETF 池，通常会先跑全量日线同步，耗时可能较长。确认继续吗？'
    }
  }

  return {
      level: 'success',
      loadLevel,
      summary: `当前是受限范围执行任务，预期只处理约 ${resolvedCount} 个标的。`,
      requiresConfirmation: false,
      notice: `本次执行任务将按${label}${suffix}处理，当前解析范围约 ${resolvedCount} 个标的。`
  }
}

module.exports = {
  buildExecutionScopeGuard,
  isConstrainedExecutionScope
}
