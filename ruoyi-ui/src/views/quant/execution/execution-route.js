function toNumberOrUndefined(value) {
  if (value === null || value === undefined || String(value).trim() === '') {
    return undefined
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

function normalizeExecutionRouteContext(query) {
  const source = query || {}
  const stockCode = String(source.stockCode || '').trim()
  const strategyId = toNumberOrUndefined(source.strategyId)
  const signalId = toNumberOrUndefined(source.signalId)
  const focusPreset = String(source.focus || 'all').trim() || 'all'

  return {
    focusPreset,
    stockCode,
    strategyId,
    signalId,
    hasTradeContext: !!(stockCode || strategyId !== undefined || signalId !== undefined)
  }
}

function applyExecutionRouteContext(context = {}, currentState = {}) {
  if (!context.hasTradeContext) {
    return currentState
  }

  const signalFilter = {
    ...(currentState.signalFilter || {}),
    stockCode: context.stockCode || '',
    strategyId: context.strategyId
  }
  const recordQuery = {
    ...(currentState.recordQuery || {}),
    stockCode: context.stockCode || ''
  }
  const recordForm = {
    ...(currentState.recordForm || {}),
    stockCode: context.stockCode || '',
    strategyId: context.strategyId !== undefined ? context.strategyId : currentState.recordForm.strategyId,
    signalId: context.signalId
  }

  return {
    signalFilter,
    recordQuery,
    recordForm
  }
}

function didExecutionRouteDataChange(previous = {}, next = {}) {
  return String(previous.focusPreset || 'all') !== String(next.focusPreset || 'all')
    || String(previous.stockCode || '') !== String(next.stockCode || '')
    || Number(previous.strategyId || 0) !== Number(next.strategyId || 0)
    || Number(previous.signalId || 0) !== Number(next.signalId || 0)
}

module.exports = {
  applyExecutionRouteContext,
  didExecutionRouteDataChange,
  normalizeExecutionRouteContext
}
