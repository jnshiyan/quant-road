const DASHBOARD_BOOTSTRAP_CRITICAL_KEYS = [
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
]

const DASHBOARD_BOOTSTRAP_DEFERRED_KEYS = [
  'executionFeedbackDetails',
  'logs',
  'valuations',
  'switchAudits',
  'canaryLatest'
]

function getDashboardBootstrapCriticalKeys() {
  return [...DASHBOARD_BOOTSTRAP_CRITICAL_KEYS]
}

function getDashboardBootstrapDeferredKeys() {
  return [...DASHBOARD_BOOTSTRAP_DEFERRED_KEYS]
}

function getDashboardBootstrapKeys() {
  return [
    ...DASHBOARD_BOOTSTRAP_CRITICAL_KEYS,
    ...DASHBOARD_BOOTSTRAP_DEFERRED_KEYS
  ]
}

module.exports = {
  getDashboardBootstrapCriticalKeys,
  getDashboardBootstrapDeferredKeys,
  getDashboardBootstrapKeys
}
