const test = require('node:test')
const assert = require('node:assert/strict')

const {
  buildEtfGovernanceViewModel,
  buildEtfReviewHistoryViewModel,
  buildObjectHotspotViewModel,
  buildAssetGovernanceComparisonViewModel
} = require('../src/views/quant/symbols/symbols-etf-governance')

test('buildEtfGovernanceViewModel prioritizes review objects and counts actions', () => {
  const payload = buildEtfGovernanceViewModel({
    summary: {
      indexMappingCount: 4,
      candidateEtfCount: 6,
      holdingCount: 2,
      activeSignalCount: 3,
      riskWarningCount: 1,
      pendingExecutionCount: 2
    },
    mappingGovernanceRows: [
      {
        indexCode: '000300',
        indexName: '沪深300',
        primaryEtfCode: '510300',
        primaryEtfName: '300ETF',
        governanceAction: 'KEEP_PRIMARY',
        governanceReason: '承接稳定',
        holdingQuantity: 300,
        pendingExecutionCount: 0
      },
      {
        indexCode: '399006',
        indexName: '创业板指',
        primaryEtfCode: '159915',
        primaryEtfName: '创业板ETF',
        governanceAction: 'REVIEW',
        governanceReason: '风险预警',
        holdingQuantity: 500,
        pendingExecutionCount: 1,
        riskWarning: true
      },
      {
        indexCode: '000905',
        indexName: '中证500',
        primaryEtfCode: '510500',
        primaryEtfName: '500ETF',
        governanceAction: 'BUILD_POSITION',
        governanceReason: '有信号待落地',
        holdingQuantity: 0,
        pendingExecutionCount: 2
      }
    ]
  })

  assert.match(payload.headline, /优先复盘/)
  assert.equal(payload.actionStats.find(item => item.action === 'REVIEW').count, 1)
  assert.equal(payload.actionStats.find(item => item.action === 'BUILD_POSITION').count, 1)
  assert.equal(payload.priorityQueue[0].action, 'REVIEW')
  assert.equal(payload.priorityQueue[1].action, 'BUILD_POSITION')
  assert.equal(payload.chartOption.series[0].data[0].value, 1)
})

test('buildEtfReviewHistoryViewModel summarizes formal case history', () => {
  const payload = buildEtfReviewHistoryViewModel([
    {
      caseId: 1,
      resolutionStatus: 'OPEN',
      lastReviewConclusion: 'OBSERVE',
      lastDetectedTime: '2026-05-07 10:00:00'
    },
    {
      caseId: 2,
      resolutionStatus: 'CLOSED',
      lastReviewConclusion: 'KEEP',
      lastDetectedTime: '2026-05-03 10:00:00'
    },
    {
      caseId: 3,
      resolutionStatus: 'OPEN',
      lastDetectedTime: '2026-04-22 10:00:00'
    }
  ])

  assert.match(payload.headline, /3 条 ETF formal case/)
  assert.equal(payload.summaryStats[1].value, 2)
  assert.equal(payload.summaryStats[2].value, 1)
  assert.equal(payload.conclusionStats[0].count, 1)
  assert.equal(payload.trendChartOption.xAxis.data.length, 2)
})

test('buildObjectHotspotViewModel ranks repeated ETF formal cases', () => {
  const payload = buildObjectHotspotViewModel([
    { stockCode: '510300', reviewTargetName: '300ETF', resolutionStatus: 'OPEN', lastDetectedTime: '2026-05-07 10:00:00' },
    { stockCode: '510300', reviewTargetName: '300ETF', resolutionStatus: 'CLOSED', lastDetectedTime: '2026-05-06 10:00:00' },
    { stockCode: '510500', reviewTargetName: '500ETF', resolutionStatus: 'OPEN', lastDetectedTime: '2026-05-05 10:00:00' }
  ])

  assert.match(payload.headline, /300ETF/)
  assert.equal(payload.hotspots[0].label, '300ETF')
  assert.equal(payload.hotspots[0].caseCount, 2)
  assert.equal(payload.chartOption.series[0].data[0], 2)
})

test('buildAssetGovernanceComparisonViewModel compares etf and equity governance load', () => {
  const payload = buildAssetGovernanceComparisonViewModel({
    etfCases: [
      { stockCode: '510300', resolutionStatus: 'OPEN', lastReviewConclusion: 'OBSERVE' },
      { stockCode: '510500', resolutionStatus: 'CLOSED', lastReviewConclusion: 'KEEP' }
    ],
    equityCases: [
      { stockCode: '000001', resolutionStatus: 'OPEN' }
    ],
    etfGovernanceRows: [
      { primaryEtfCode: '510300', governanceAction: 'REVIEW' },
      { primaryEtfCode: '510500', governanceAction: 'KEEP_PRIMARY' }
    ],
    symbolRows: [
      { stockCode: '510300', pendingSignalCount: 1, abnormalFeedbackCount: 0, unmatchedExecutionCount: 0 },
      { stockCode: '000001', pendingSignalCount: 1, abnormalFeedbackCount: 1, unmatchedExecutionCount: 0 },
      { stockCode: '000002', pendingSignalCount: 0, abnormalFeedbackCount: 0, unmatchedExecutionCount: 0 }
    ]
  })

  assert.equal(payload.rows[0].currentIssues, 1)
  assert.equal(payload.rows[1].currentIssues, 1)
  assert.equal(payload.rows[0].historyCases, 2)
  assert.equal(payload.chartOption.series[2].data[0], 1)
})
