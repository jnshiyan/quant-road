const { normalizeReviewContext } = require('./review-context')

const REVIEW_TIMELINE_LIMIT = 120
const REVIEW_BOOTSTRAP_CRITICAL_KEYS = [
  'strategies',
  'dashboardSummary'
]
const REVIEW_BOOTSTRAP_DEFERRED_KEYS = [
  'executionFeedback',
  'canaryLatest'
]
const REVIEW_PRIMARY_REFRESH_KEYS = [
  'summary',
  'kline',
  'holding',
  'nav',
  'overlay',
  'governance',
  'ruleExplain'
]
const REVIEW_DEFERRED_REFRESH_KEYS = [
  'timeline',
  'cases',
  'actionItems'
]

function createDefaultReviewQueryParams() {
  return {
    caseId: undefined,
    reviewLevel: 'trade',
    strategyId: undefined,
    stockCode: '',
    signalId: undefined,
    baselineStrategyId: undefined,
    candidateStrategyId: undefined,
    months: 6,
    dateRangeStart: '',
    dateRangeEnd: '',
    scopeType: '',
    scopePoolCode: '',
    symbols: [],
    whitelist: [],
    blacklist: [],
    adHocSymbols: []
  }
}

function cloneArray(value) {
  return Array.isArray(value) ? [...value] : []
}

function clearCaseBoundQueryParams(current, overrides = {}) {
  const source = current || {}
  const next = {
    ...createDefaultReviewQueryParams(),
    ...source,
    symbols: cloneArray(source.symbols),
    whitelist: cloneArray(source.whitelist),
    blacklist: cloneArray(source.blacklist),
    adHocSymbols: cloneArray(source.adHocSymbols),
    ...overrides
  }
  next.caseId = undefined
  return next
}

function shouldLoadGovernanceEvidence(current) {
  const source = current || {}
  return source.reviewLevel === 'governance'
    && source.baselineStrategyId !== undefined
    && source.candidateStrategyId !== undefined
}

function getReviewTimelineLimit() {
  return REVIEW_TIMELINE_LIMIT
}

function getReviewTimelineSectionMeta() {
  return `把图表和事实串成可追责链路，仅展示最近 ${REVIEW_TIMELINE_LIMIT} 条关键事件`
}

function getReviewBootstrapPrefetchKeys() {
  return [
    ...REVIEW_BOOTSTRAP_CRITICAL_KEYS,
    ...REVIEW_BOOTSTRAP_DEFERRED_KEYS
  ]
}

function getReviewBootstrapCriticalKeys() {
  return [...REVIEW_BOOTSTRAP_CRITICAL_KEYS]
}

function getReviewBootstrapDeferredKeys() {
  return [...REVIEW_BOOTSTRAP_DEFERRED_KEYS]
}

function getReviewPrimaryRefreshKeys() {
  return [...REVIEW_PRIMARY_REFRESH_KEYS]
}

function getReviewDeferredRefreshKeys() {
  return [...REVIEW_DEFERRED_REFRESH_KEYS]
}

function normalizeReviewRefreshPayload(current) {
  const context = normalizeReviewContext(current || {})
  return {
    caseId: context.caseId ?? null,
    reviewLevel: context.reviewLevel || 'trade',
    strategyId: context.strategyId ?? null,
    stockCode: context.stockCode || '',
    signalId: context.signalId ?? null,
    baselineStrategyId: context.baselineStrategyId ?? null,
    candidateStrategyId: context.candidateStrategyId ?? null,
    months: context.months ?? null,
    dateRangeStart: context.dateRangeStart || '',
    dateRangeEnd: context.dateRangeEnd || '',
    scopeType: context.scopeType || '',
    scopePoolCode: context.scopePoolCode || '',
    symbols: cloneArray(context.symbols),
    whitelist: cloneArray(context.whitelist),
    blacklist: cloneArray(context.blacklist),
    adHocSymbols: cloneArray(context.adHocSymbols)
  }
}

function createReviewRefreshKey(current) {
  return JSON.stringify(normalizeReviewRefreshPayload(current))
}

function didReviewRouteContextChange(nextQuery, previousQuery) {
  return createReviewRefreshKey(nextQuery) !== createReviewRefreshKey(previousQuery)
}

module.exports = {
  clearCaseBoundQueryParams,
  createDefaultReviewQueryParams,
  createReviewRefreshKey,
  didReviewRouteContextChange,
  getReviewBootstrapCriticalKeys,
  getReviewBootstrapDeferredKeys,
  getReviewBootstrapPrefetchKeys,
  getReviewDeferredRefreshKeys,
  getReviewPrimaryRefreshKeys,
  getReviewTimelineLimit,
  getReviewTimelineSectionMeta,
  shouldLoadGovernanceEvidence
}
