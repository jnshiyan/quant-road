const test = require('node:test');
const assert = require('node:assert/strict');

const {
  createReviewRefreshKey,
  clearCaseBoundQueryParams,
  createDefaultReviewQueryParams,
  didReviewRouteContextChange,
  getReviewBootstrapCriticalKeys,
  getReviewBootstrapDeferredKeys,
  getReviewBootstrapPrefetchKeys,
  getReviewDeferredRefreshKeys,
  getReviewPrimaryRefreshKeys,
  getReviewTimelineLimit,
  getReviewTimelineSectionMeta,
  shouldLoadGovernanceEvidence
} = require('../src/views/quant/review/review-page-state');

test('createDefaultReviewQueryParams starts without bound case context', () => {
  assert.deepEqual(createDefaultReviewQueryParams(), {
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
  });
});

test('clearCaseBoundQueryParams drops case binding but preserves current filters', () => {
  const next = clearCaseBoundQueryParams({
    caseId: 401,
    reviewLevel: 'trade',
    strategyId: 7,
    stockCode: '510300',
    scopeType: 'etf_pool',
    scopePoolCode: 'ETF_CORE',
    symbols: ['510300']
  }, {
    reviewLevel: 'strategy'
  });

  assert.deepEqual(next, {
    caseId: undefined,
    reviewLevel: 'strategy',
    strategyId: 7,
    stockCode: '510300',
    signalId: undefined,
    baselineStrategyId: undefined,
    candidateStrategyId: undefined,
    months: 6,
    dateRangeStart: '',
    dateRangeEnd: '',
    scopeType: 'etf_pool',
    scopePoolCode: 'ETF_CORE',
    symbols: ['510300'],
    whitelist: [],
    blacklist: [],
    adHocSymbols: []
  });
});

test('shouldLoadGovernanceEvidence only enables governance calls for complete governance context', () => {
  assert.equal(
    shouldLoadGovernanceEvidence({
      reviewLevel: 'governance',
      baselineStrategyId: 1,
      candidateStrategyId: 2
    }),
    true
  );
  assert.equal(
    shouldLoadGovernanceEvidence({
      reviewLevel: 'trade',
      baselineStrategyId: 1,
      candidateStrategyId: 2
    }),
    false
  );
  assert.equal(
    shouldLoadGovernanceEvidence({
      reviewLevel: 'governance',
      baselineStrategyId: 1,
      candidateStrategyId: undefined
    }),
    false
  );
});

test('review timeline helpers keep the page on a bounded recent event window', () => {
  assert.equal(getReviewTimelineLimit(), 120);
  assert.equal(
    getReviewTimelineSectionMeta(),
    '把图表和事实串成可追责链路，仅展示最近 120 条关键事件'
  );
});

test('review bootstrap prefetch excludes data that refreshReview already reloads', () => {
  assert.deepEqual(getReviewBootstrapPrefetchKeys(), [
    'strategies',
    'dashboardSummary',
    'executionFeedback',
    'canaryLatest'
  ]);
});

test('review bootstrap separates first-screen data from follow-up context data', () => {
  assert.deepEqual(getReviewBootstrapCriticalKeys(), [
    'strategies',
    'dashboardSummary'
  ]);
  assert.deepEqual(getReviewBootstrapDeferredKeys(), [
    'executionFeedback',
    'canaryLatest'
  ]);
});

test('review refresh separates decision-critical payloads from secondary evidence', () => {
  assert.deepEqual(getReviewPrimaryRefreshKeys(), [
    'summary',
    'kline',
    'holding',
    'nav',
    'overlay',
    'governance',
    'ruleExplain'
  ]);
  assert.deepEqual(getReviewDeferredRefreshKeys(), [
    'timeline',
    'cases',
    'actionItems'
  ]);
});

test('didReviewRouteContextChange ignores semantically identical route churn', () => {
  assert.equal(
    didReviewRouteContextChange(
      {
        reviewLevel: 'trade',
        strategyId: '7',
        stockCode: '000001',
        symbols: '600519,000001'
      },
      {
        reviewLevel: 'trade',
        strategyId: 7,
        stockCode: '000001',
        symbols: ['600519', '000001']
      }
    ),
    false
  );

  assert.equal(
    didReviewRouteContextChange(
      {
        reviewLevel: 'strategy',
        strategyId: '8',
        stockCode: '000001'
      },
      {
        reviewLevel: 'trade',
        strategyId: 7,
        stockCode: '000001'
      }
    ),
    true
  );
});

test('createReviewRefreshKey collapses equivalent review params to the same refresh signature', () => {
  const a = createReviewRefreshKey({
    reviewLevel: 'trade',
    strategyId: 7,
    stockCode: '000001',
    signalId: 501,
    symbols: ['600519', '000001'],
    whitelist: [],
    blacklist: [],
    adHocSymbols: []
  });

  const b = createReviewRefreshKey({
    reviewLevel: 'trade',
    strategyId: '7',
    stockCode: '000001',
    signalId: '501',
    symbols: '600519,000001',
    whitelist: undefined,
    blacklist: undefined,
    adHocSymbols: undefined
  });

  const c = createReviewRefreshKey({
    reviewLevel: 'trade',
    strategyId: 9,
    stockCode: '000001',
    signalId: 501
  });

  assert.equal(a, b);
  assert.notEqual(a, c);
});
