const test = require('node:test');
const assert = require('node:assert/strict');

const {
  buildReviewRouteQuery,
  normalizeReviewContext,
  filterExecutionFeedbackRows,
  filterStrategyLogs
} = require('../src/views/quant/review/review-context');

test('normalizeReviewContext extracts typed route params', () => {
  const payload = normalizeReviewContext({
    caseId: '77',
    stockCode: '000001',
    strategyId: '2',
    signalId: '5001',
    baselineStrategyId: '1',
    candidateStrategyId: '3',
    months: '6',
    dateRangeStart: '2026-05-01',
    dateRangeEnd: '2026-05-31',
    reviewLevel: 'trade',
    sourcePage: 'execution',
    sourceAction: 'feedback'
  });

  assert.deepEqual(payload, {
    caseId: 77,
    stockCode: '000001',
    strategyId: 2,
    signalId: 5001,
    baselineStrategyId: 1,
    candidateStrategyId: 3,
    months: 6,
    dateRangeStart: '2026-05-01',
    dateRangeEnd: '2026-05-31',
    scopeType: '',
    scopePoolCode: '',
    symbols: [],
    whitelist: [],
    blacklist: [],
    adHocSymbols: [],
    reviewLevel: 'trade',
    sourcePage: 'execution',
    sourceAction: 'feedback',
    hasContext: true
  });
});

test('normalizeReviewContext parses scope fields from route query', () => {
  const payload = normalizeReviewContext({
    caseId: '88',
    scopeType: 'stock_pool',
    scopePoolCode: 'core_pool',
    symbols: '600519, 000001',
    whitelist: '600519,300750',
    blacklist: '000001',
    adHocSymbols: '159915'
  });

  assert.deepEqual(payload, {
    caseId: 88,
    stockCode: '',
    strategyId: undefined,
    signalId: undefined,
    baselineStrategyId: undefined,
    candidateStrategyId: undefined,
    months: undefined,
    dateRangeStart: '',
    dateRangeEnd: '',
    scopeType: 'stock_pool',
    scopePoolCode: 'core_pool',
    symbols: ['600519', '000001'],
    whitelist: ['600519', '300750'],
    blacklist: ['000001'],
    adHocSymbols: ['159915'],
    reviewLevel: 'trade',
    sourcePage: '',
    sourceAction: '',
    hasContext: true
  });
});

test('filterExecutionFeedbackRows keeps only matching context rows', () => {
  const rows = [
    { signal_id: 5001, stock_code: '000001', strategy_id: 1 },
    { signal_id: 5002, stock_code: '000002', strategy_id: 1 }
  ];

  const filtered = filterExecutionFeedbackRows(rows, {
    stockCode: '000001',
    strategyId: 1,
    signalId: 5001
  });

  assert.equal(filtered.length, 1);
  assert.equal(filtered[0].signal_id, 5001);
});

test('filterStrategyLogs honors strategy filter only when provided', () => {
  const rows = [
    { strategy_id: 1, run_time: 'a' },
    { strategy_id: 2, run_time: 'b' }
  ];

  assert.equal(filterStrategyLogs(rows, { strategyId: 2 }).length, 1);
  assert.equal(filterStrategyLogs(rows, {}).length, 2);
});

test('buildReviewRouteQuery keeps only supported context fields', () => {
  const query = buildReviewRouteQuery({
    caseId: 91,
    stockCode: '000001',
    strategyId: 3,
    signalId: 6001,
    baselineStrategyId: 1,
    candidateStrategyId: 2,
    months: 6,
    dateRangeStart: '2026-05-01',
    dateRangeEnd: '2026-05-31',
    scopeType: 'stock_pool',
    scopePoolCode: 'core_pool',
    symbols: ['600519', '000001'],
    whitelist: ['600519'],
    blacklist: ['000001'],
    adHocSymbols: ['159915'],
    reviewLevel: 'trade',
    sourcePage: 'execution',
    sourceAction: 'signal'
  });

  assert.deepEqual(query, {
    reviewLevel: 'trade',
    caseId: '91',
    stockCode: '000001',
    strategyId: '3',
    signalId: '6001',
    baselineStrategyId: '1',
    candidateStrategyId: '2',
    months: '6',
    dateRangeStart: '2026-05-01',
    dateRangeEnd: '2026-05-31',
    scopeType: 'stock_pool',
    scopePoolCode: 'core_pool',
    symbols: '600519,000001',
    whitelist: '600519',
    blacklist: '000001',
    adHocSymbols: '159915',
    sourcePage: 'execution',
    sourceAction: 'signal'
  });
});

test('buildReviewRouteQuery omits empty values', () => {
  const query = buildReviewRouteQuery({
    caseId: undefined,
    stockCode: '',
    strategyId: undefined,
    signalId: null,
    sourcePage: 'execution',
    sourceAction: 'feedback'
  });

  assert.deepEqual(query, {
    reviewLevel: 'trade',
    sourcePage: 'execution',
    sourceAction: 'feedback'
  });
});
