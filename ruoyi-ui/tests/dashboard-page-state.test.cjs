const test = require('node:test');
const assert = require('node:assert/strict');

const {
  getDashboardBootstrapCriticalKeys,
  getDashboardBootstrapDeferredKeys,
  getDashboardBootstrapKeys
} = require('../src/views/quant/dashboard/dashboard-page-state');

test('dashboard bootstrap keeps main operations chain in the first-screen batch', () => {
  assert.deepEqual(getDashboardBootstrapCriticalKeys(), [
    'summary',
    'marketStatus',
    'dashboardDeepLinks',
    'dashboardActionItems',
    'positionRiskSummary',
    'reviewCandidates',
    'etfOverview',
    'signals',
    'positions',
    'executionFeedbackSummary'
  ]);
});

test('dashboard bootstrap pushes supplementary panels to deferred loading', () => {
  assert.deepEqual(getDashboardBootstrapDeferredKeys(), [
    'executionFeedbackDetails',
    'logs',
    'valuations',
    'switchAudits',
    'canaryLatest'
  ]);
});

test('dashboard bootstrap full list remains the union of critical and deferred keys', () => {
  assert.deepEqual(getDashboardBootstrapKeys(), [
    'summary',
    'marketStatus',
    'dashboardDeepLinks',
    'dashboardActionItems',
    'positionRiskSummary',
    'reviewCandidates',
    'etfOverview',
    'signals',
    'positions',
    'executionFeedbackSummary',
    'executionFeedbackDetails',
    'logs',
    'valuations',
    'switchAudits',
    'canaryLatest'
  ]);
});
