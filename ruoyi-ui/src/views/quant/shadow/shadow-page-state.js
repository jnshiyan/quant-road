const SHADOW_BOOTSTRAP_CRITICAL_KEYS = [
  'strategies'
]
const SHADOW_BOOTSTRAP_DEFERRED_KEYS = [
  'capabilities'
]
const SHADOW_PRIMARY_REFRESH_KEYS = [
  'compare',
  'summary',
  'actionItems'
]
const SHADOW_DEFERRED_REFRESH_KEYS = [
  'charts',
  'applicability',
  'links',
  'history'
]

function toNumberOrUndefined(value) {
  if (value === null || value === undefined || String(value).trim() === '') {
    return undefined
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

function normalizeShadowRouteQuery(query, current = {}) {
  const source = query || {}
  const fallbackMonths = Number(current.months)
  return {
    baselineStrategyId: toNumberOrUndefined(source.baselineStrategyId),
    candidateStrategyId: toNumberOrUndefined(source.candidateStrategyId),
    months: toNumberOrUndefined(source.months) || (Number.isFinite(fallbackMonths) ? fallbackMonths : 6)
  }
}

function applyShadowRouteQuery(query, strategyList, current = {}) {
  const next = normalizeShadowRouteQuery(query, current)
  const items = Array.isArray(strategyList) ? strategyList : []
  if (!items.length) {
    return next
  }

  const activeItems = items.filter(item => Number(item.status) === 1)
  const pool = activeItems.length >= 2 ? activeItems : items
  const hasBaseline = pool.some(item => Number(item.id) === Number(next.baselineStrategyId))
  const hasCandidate = pool.some(item => Number(item.id) === Number(next.candidateStrategyId))

  if (!hasBaseline) {
    next.baselineStrategyId = pool[0].id
  }
  if (!hasCandidate || next.candidateStrategyId === next.baselineStrategyId) {
    const candidate = pool.find(item => Number(item.id) !== Number(next.baselineStrategyId))
    next.candidateStrategyId = candidate ? candidate.id : undefined
  }
  return next
}

function didShadowQueryChange(previous, next) {
  const prev = previous || {}
  const current = next || {}
  return Number(prev.baselineStrategyId) !== Number(current.baselineStrategyId)
    || Number(prev.candidateStrategyId) !== Number(current.candidateStrategyId)
    || Number(prev.months) !== Number(current.months)
}

function getShadowBootstrapPrefetchKeys() {
  return [
    ...SHADOW_BOOTSTRAP_CRITICAL_KEYS,
    ...SHADOW_BOOTSTRAP_DEFERRED_KEYS
  ]
}

function getShadowBootstrapCriticalKeys() {
  return [...SHADOW_BOOTSTRAP_CRITICAL_KEYS]
}

function getShadowBootstrapDeferredKeys() {
  return [...SHADOW_BOOTSTRAP_DEFERRED_KEYS]
}

function getShadowPrimaryRefreshKeys() {
  return [...SHADOW_PRIMARY_REFRESH_KEYS]
}

function getShadowDeferredRefreshKeys() {
  return [...SHADOW_DEFERRED_REFRESH_KEYS]
}

function createShadowRefreshKey(current) {
  return JSON.stringify(normalizeShadowRouteQuery(current || {}))
}

function didShadowRouteContextChange(nextQuery, previousQuery) {
  return createShadowRefreshKey(nextQuery) !== createShadowRefreshKey(previousQuery)
}

module.exports = {
  applyShadowRouteQuery,
  createShadowRefreshKey,
  getShadowBootstrapCriticalKeys,
  getShadowBootstrapDeferredKeys,
  getShadowBootstrapPrefetchKeys,
  getShadowDeferredRefreshKeys,
  getShadowPrimaryRefreshKeys,
  didShadowQueryChange,
  didShadowRouteContextChange
}
