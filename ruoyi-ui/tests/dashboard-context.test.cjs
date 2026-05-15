const test = require('node:test');
const assert = require('node:assert/strict');

const {
  buildDashboardReviewCandidateQuery,
  reviewAssetTagLabel
} = require('../src/views/quant/dashboard/dashboard-context');

test('buildDashboardReviewCandidateQuery keeps ETF scope and source action', () => {
  const query = buildDashboardReviewCandidateQuery({
    reviewLevel: 'trade',
    stockCode: '510300',
    strategyId: 9,
    scopeType: 'etf_pool',
    scopePoolCode: 'ETF_CORE',
    sourceAction: 'etfRisk'
  });

  assert.deepEqual(query, {
    reviewLevel: 'trade',
    stockCode: '510300',
    strategyId: '9',
    scopeType: 'etf_pool',
    scopePoolCode: 'ETF_CORE',
    sourcePage: 'dashboard',
    sourceAction: 'etfRisk'
  });
});

test('buildDashboardReviewCandidateQuery falls back to reviewCandidate source action', () => {
  const query = buildDashboardReviewCandidateQuery({
    reviewLevel: 'governance',
    baselineStrategyId: 1,
    candidateStrategyId: 2
  });

  assert.deepEqual(query, {
    reviewLevel: 'governance',
    baselineStrategyId: '1',
    candidateStrategyId: '2',
    sourcePage: 'dashboard',
    sourceAction: 'reviewCandidate'
  });
});

test('buildDashboardReviewCandidateQuery keeps formal case id when present', () => {
  const query = buildDashboardReviewCandidateQuery({
    caseId: 301,
    reviewLevel: 'trade',
    stockCode: '510300',
    strategyId: 9
  });

  assert.deepEqual(query, {
    reviewLevel: 'trade',
    caseId: '301',
    stockCode: '510300',
    strategyId: '9',
    sourcePage: 'dashboard',
    sourceAction: 'reviewCandidate'
  });
});

test('reviewAssetTagLabel maps known asset types', () => {
  assert.equal(reviewAssetTagLabel('ETF'), 'ETF');
  assert.equal(reviewAssetTagLabel('equity'), '股票');
  assert.equal(reviewAssetTagLabel('stock'), '股票');
  assert.equal(reviewAssetTagLabel(''), '-');
});
