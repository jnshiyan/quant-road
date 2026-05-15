import request from '@/utils/request'

const QUANT_JOB_TIMEOUT = 4 * 60 * 60 * 1000

function quantJobRequest(config) {
  return request({
    timeout: QUANT_JOB_TIMEOUT,
    ...config
  })
}

export function getDashboardSummary() {
  return request({
    url: '/quant/dashboard/summary',
    method: 'get'
  })
}

export function listSignals(params) {
  return request({
    url: '/quant/data/signals',
    method: 'get',
    params
  })
}

export function listPositions() {
  return request({
    url: '/quant/data/positions',
    method: 'get'
  })
}

export function listStrategyLogs(params) {
  return request({
    url: '/quant/data/strategyLogs',
    method: 'get',
    params
  })
}

export function getMarketStatus() {
  return request({
    url: '/quant/data/marketStatus',
    method: 'get'
  })
}

export function listIndexValuations(params) {
  return request({
    url: '/quant/data/indexValuations',
    method: 'get',
    params
  })
}

export function listStrategySwitchAudits(params) {
  return request({
    url: '/quant/data/strategySwitchAudits',
    method: 'get',
    params
  })
}

export function getExecutionFeedbackSummary() {
  return request({
    url: '/quant/data/executionFeedbackSummary',
    method: 'get'
  })
}

export function getExecutionReconciliationSummary() {
  return request({
    url: '/quant/data/executionReconciliationSummary',
    method: 'get'
  })
}

export function getDashboardActionItems(params) {
  return request({
    url: '/quant/data/dashboardActionItems',
    method: 'get',
    params
  })
}

export function getPositionRiskSummary() {
  return request({
    url: '/quant/data/positionRiskSummary',
    method: 'get'
  })
}

export function getDashboardDeepLinks() {
  return request({
    url: '/quant/data/dashboardDeepLinks',
    method: 'get'
  })
}

export function getTaskCenterSummary() {
  return request({
    url: '/quant/data/taskCenterSummary',
    method: 'get'
  })
}

export function getDispatchDefinitions() {
  return request({
    url: '/quant/data/dispatchDefinitions',
    method: 'get'
  })
}

export function getDispatchHistory(params) {
  return request({
    url: '/quant/data/dispatchHistory',
    method: 'get',
    params
  })
}

export function getDispatchDetail(jobId) {
  return request({
    url: `/quant/data/dispatchDetail/${jobId}`,
    method: 'get'
  })
}

export function getOperationsCenterSummary() {
  return request({
    url: '/quant/data/operationsCenterSummary',
    method: 'get'
  })
}

export function getEtfOverview() {
  return request({
    url: '/quant/data/etfOverview',
    method: 'get'
  })
}

export function getEtfGovernanceSummary() {
  return request({
    url: '/quant/data/etfGovernanceSummary',
    method: 'get'
  })
}

export function getDashboardReviewCandidates(params) {
  return request({
    url: '/quant/data/reviewCandidates',
    method: 'get',
    params
  })
}

export function getSignalExplain(signalId) {
  return request({
    url: `/quant/data/signalExplain/${signalId}`,
    method: 'get'
  })
}

export function listExecutionFeedbackDetails(params) {
  return request({
    url: '/quant/data/executionFeedbackDetails',
    method: 'get',
    params
  })
}

export function listExecutionMatchCandidates(params) {
  return request({
    url: '/quant/data/executionMatchCandidates',
    method: 'get',
    params
  })
}

export function getPositionSyncResult(params) {
  return request({
    url: '/quant/data/positionSyncResult',
    method: 'get',
    params
  })
}

export function getCanaryLatest() {
  return request({
    url: '/quant/data/canaryLatest',
    method: 'get'
  })
}

export function listStrategies() {
  return request({
    url: '/quant/data/strategies',
    method: 'get'
  })
}

export function getStrategyCapabilities() {
  return request({
    url: '/quant/data/strategyCapabilities',
    method: 'get'
  })
}

export function getShadowCompare(params) {
  return request({
    url: '/quant/data/shadowCompare',
    method: 'get',
    params
  })
}

export function getShadowCompareSummary(params) {
  return request({
    url: '/quant/data/shadowCompareSummary',
    method: 'get',
    params
  })
}

export function getShadowCompareCharts(params) {
  return request({
    url: '/quant/data/shadowCompareCharts',
    method: 'get',
    params
  })
}

export function getShadowCompareApplicability(params) {
  return request({
    url: '/quant/data/shadowCompareApplicability',
    method: 'get',
    params
  })
}

export function getShadowReviewLinks(params) {
  return request({
    url: '/quant/data/shadowReviewLinks',
    method: 'get',
    params
  })
}

export function getGovernanceHistory(params) {
  return request({
    url: '/quant/governance/history',
    method: 'get',
    params
  })
}

export function submitGovernanceDecision(data) {
  return quantJobRequest({
    url: '/quant/governance/decision',
    method: 'post',
    data
  })
}

export function getReviewCandidates(params) {
  return request({
    url: '/quant/review/candidates',
    method: 'get',
    params
  })
}

export function getReviewCases(params) {
  return request({
    url: '/quant/review/cases',
    method: 'get',
    params
  })
}

export function getReviewCaseDetail(caseId) {
  return request({
    url: '/quant/review/caseDetail',
    method: 'get',
    params: { caseId }
  })
}

export function getReviewSummary(params) {
  return request({
    url: '/quant/review/summary',
    method: 'get',
    params
  })
}

export function getReviewKline(params) {
  return request({
    url: '/quant/review/kline',
    method: 'get',
    params
  })
}

export function getReviewHoldingRange(params) {
  return request({
    url: '/quant/review/holdingRange',
    method: 'get',
    params
  })
}

export function getReviewNavDrawdown(params) {
  return request({
    url: '/quant/review/navDrawdown',
    method: 'get',
    params
  })
}

export function getReviewMarketOverlay(params) {
  return request({
    url: '/quant/review/marketOverlay',
    method: 'get',
    params
  })
}

export function getReviewGovernanceEvidence(params) {
  return request({
    url: '/quant/review/governanceEvidence',
    method: 'get',
    params
  })
}

export function getReviewTimeline(params) {
  return request({
    url: '/quant/review/timeline',
    method: 'get',
    params
  })
}

export function getReviewRuleExplain(params) {
  return request({
    url: '/quant/review/ruleExplain',
    method: 'get',
    params
  })
}

export function submitReviewConclusion(data) {
  return quantJobRequest({
    url: '/quant/review/conclusion',
    method: 'post',
    data
  })
}

export function listExecutionRecords(params) {
  return request({
    url: '/quant/data/executionRecords',
    method: 'get',
    params
  })
}

export function listJobBatches(params) {
  return request({
    url: '/quant/data/jobBatches',
    method: 'get',
    params
  })
}

export function listJobSteps(params) {
  return request({
    url: '/quant/data/jobSteps',
    method: 'get',
    params
  })
}

export function getJobReadiness(params) {
  return request({
    url: '/quant/data/jobReadiness',
    method: 'get',
    params
  })
}

export function getJobErrorCategories(params) {
  return request({
    url: '/quant/data/jobErrorCategories',
    method: 'get',
    params
  })
}

export function getJobSopHints(params) {
  return request({
    url: '/quant/data/jobSopHints',
    method: 'get',
    params
  })
}

export function getSymbolScopeOptions() {
  return request({
    url: '/quant/data/symbolScopeOptions',
    method: 'get'
  })
}

export function listSymbolPools() {
  return request({
    url: '/quant/data/symbolPools',
    method: 'get'
  })
}

export function getSymbolPoolDetail(params) {
  return request({
    url: '/quant/data/symbolPoolDetail',
    method: 'get',
    params
  })
}

export function listIndexEtfMappings() {
  return request({
    url: '/quant/data/indexEtfMappings',
    method: 'get'
  })
}

export function getSymbolScopePreview(params) {
  return request({
    url: '/quant/data/symbolScopePreview',
    method: 'get',
    params
  })
}

export function listAsyncJobs(params) {
  return request({
    url: '/quant/data/asyncJobs',
    method: 'get',
    params
  })
}

export function getAsyncWorkerSummary() {
  return request({
    url: '/quant/data/asyncWorkerSummary',
    method: 'get'
  })
}

export function listAsyncJobShards(params) {
  return request({
    url: '/quant/data/asyncJobShards',
    method: 'get',
    params
  })
}

export function listAsyncJobResults(params) {
  return request({
    url: '/quant/data/asyncJobResults',
    method: 'get',
    params
  })
}

export function runLegacyFullDaily(data) {
  // Deprecated compatibility entry. Prefer executeQuantTask.
  return quantJobRequest({
    url: '/quant/jobs/fullDaily',
    method: 'post',
    data
  })
}

export function runSyncBasic() {
  return quantJobRequest({
    url: '/quant/jobs/syncBasic',
    method: 'post'
  })
}

export function runSyncDaily(params) {
  return quantJobRequest({
    url: '/quant/jobs/syncDaily',
    method: 'post',
    params
  })
}

export function runSyncValuation(params) {
  return quantJobRequest({
    url: '/quant/jobs/syncValuation',
    method: 'post',
    params
  })
}

export function runEvaluateMarket(params) {
  return quantJobRequest({
    url: '/quant/jobs/evaluateMarket',
    method: 'post',
    params
  })
}

export function runLegacyStrategyTask(data) {
  // Deprecated compatibility entry. Prefer executeQuantTask.
  return quantJobRequest({
    url: '/quant/jobs/runStrategy',
    method: 'post',
    data
  })
}

export function runLegacyPortfolioTask(data) {
  // Deprecated compatibility entry. Prefer executeQuantTask.
  return quantJobRequest({
    url: '/quant/jobs/runPortfolio',
    method: 'post',
    data
  })
}

export const runFullDaily = runLegacyFullDaily
export const runStrategy = runLegacyStrategyTask
export const runPortfolio = runLegacyPortfolioTask

export function executeQuantTask(data) {
  return quantJobRequest({
    url: '/quant/jobs/execute',
    method: 'post',
    data
  })
}

export function submitQuantJob(data) {
  return executeQuantTask(data)
}

export function getQuantJobStatus(jobId) {
  return request({
    url: `/quant/jobs/status/${jobId}`,
    method: 'get'
  })
}

export function cancelQuantJob(jobId) {
  return quantJobRequest({
    url: `/quant/jobs/cancel/${jobId}`,
    method: 'post'
  })
}

export function retryQuantJobFailedShards(jobId) {
  return quantJobRequest({
    url: `/quant/jobs/retryFailedShards/${jobId}`,
    method: 'post'
  })
}

export function runAsyncWorkerOnce(params) {
  return quantJobRequest({
    url: '/quant/jobs/runAsyncWorkerOnce',
    method: 'post',
    params
  })
}

export function recoverAsyncShards(params) {
  return quantJobRequest({
    url: '/quant/jobs/recoverAsyncShards',
    method: 'post',
    params
  })
}

export function recoverBatch(batchId, params) {
  return quantJobRequest({
    url: `/quant/jobs/recoverBatch/${batchId}`,
    method: 'post',
    params
  })
}

export function runEvaluateRisk(data) {
  return quantJobRequest({
    url: '/quant/jobs/evaluateRisk',
    method: 'post',
    data
  })
}

export function runNotifySignals() {
  return quantJobRequest({
    url: '/quant/jobs/notifySignals',
    method: 'post'
  })
}

export function runMonthlyReport(params) {
  return quantJobRequest({
    url: '/quant/jobs/monthlyReport',
    method: 'post',
    params
  })
}

export function runRecordExecution(data) {
  return quantJobRequest({
    url: '/quant/jobs/recordExecution',
    method: 'post',
    data
  })
}

export function runImportExecutions(params) {
  return quantJobRequest({
    url: '/quant/jobs/importExecutions',
    method: 'post',
    params
  })
}

export function validateExecutionImport(params) {
  return quantJobRequest({
    url: '/quant/jobs/validateExecutionImport',
    method: 'post',
    params
  })
}

export function runImportExecutionsUpload(data) {
  return quantJobRequest({
    url: '/quant/jobs/importExecutionsUpload',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function validateExecutionImportUpload(data) {
  return quantJobRequest({
    url: '/quant/jobs/validateExecutionImportUpload',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function confirmExecutionMatch(data) {
  return quantJobRequest({
    url: '/quant/jobs/confirmExecutionMatch',
    method: 'post',
    data
  })
}

export function markExecutionException(data) {
  return quantJobRequest({
    url: '/quant/jobs/markExecutionException',
    method: 'post',
    data
  })
}

export function runShadowCompare(params) {
  return quantJobRequest({
    url: '/quant/jobs/shadowCompare',
    method: 'post',
    params
  })
}

export function runExecutionFeedback(params) {
  return quantJobRequest({
    url: '/quant/jobs/evaluateExecutionFeedback',
    method: 'post',
    params
  })
}

export function runEvaluateExecutionFeedback(params) {
  return runExecutionFeedback(params)
}

export function runCanaryEvaluate(params) {
  return quantJobRequest({
    url: '/quant/jobs/canaryEvaluate',
    method: 'post',
    params
  })
}
