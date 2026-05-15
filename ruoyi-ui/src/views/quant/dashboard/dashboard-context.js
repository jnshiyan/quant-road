const { buildReviewRouteQuery } = require('../review/review-context');

function buildDashboardReviewCandidateQuery(row) {
  const source = row || {};
  return buildReviewRouteQuery({
    caseId: source.caseId,
    reviewLevel: source.reviewLevel || 'trade',
    signalId: source.signalId,
    stockCode: source.stockCode,
    strategyId: source.strategyId,
    baselineStrategyId: source.baselineStrategyId,
    candidateStrategyId: source.candidateStrategyId,
    scopeType: source.scopeType,
    scopePoolCode: source.scopePoolCode,
    sourcePage: 'dashboard',
    sourceAction: source.sourceAction || 'reviewCandidate'
  });
}

function reviewAssetTagLabel(assetType) {
  const normalized = String(assetType || '').trim().toUpperCase();
  if (normalized === 'ETF') {
    return 'ETF';
  }
  if (normalized === 'EQUITY' || normalized === 'STOCK') {
    return '股票';
  }
  return normalized || '-';
}

module.exports = {
  buildDashboardReviewCandidateQuery,
  reviewAssetTagLabel
};
