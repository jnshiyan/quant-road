const test = require('node:test')
const assert = require('node:assert/strict')

const { buildReviewActionPlan } = require('../src/views/quant/review/review-explain')

test('buildReviewActionPlan escalates data integrity gate before review work', () => {
  const payload = buildReviewActionPlan({
    actionItems: [
      {
        actionType: 'DATA_INTEGRITY_REVIEW',
        title: '核对盘后数据完整性',
        reason: '日线同步存在失败标的，复盘结论暂时不可信。',
        priority: 'P0',
        targetPage: '/quant/jobs',
        targetQuery: { batchId: 12 },
        recommendedAction: 'DATA_INTEGRITY_REVIEW',
        sourceAction: 'dataIntegrityGate'
      },
      {
        actionType: 'REVIEW_CANDIDATE',
        title: '进入复盘分析',
        reason: '存在待解释异常对象。',
        priority: 'P2',
        targetPage: '/quant/review',
        targetQuery: { reviewLevel: 'trade', stockCode: '000001', strategyId: 2 },
        recommendedAction: 'REVIEW_CANDIDATE',
        sourceAction: 'reviewCandidate'
      }
    ],
    reviewContext: {
      reviewLevel: 'trade',
      stockCode: '000001',
      strategyId: 2,
      hasContext: true,
      sourceAction: 'reviewCandidate',
      sourceActionLabel: '待复盘对象'
    },
    summaryPayload: {
      reviewTargetName: '平安银行',
      reviewConclusion: 'OBSERVE',
      suggestedAction: 'OBSERVE'
    },
    reconciliationSummary: {
      pendingSignalCount: 1,
      unmatchedExecutionCount: 0,
      missedSignalCount: 0
    }
  })

  assert.equal(payload.headline, '先不要沉淀复盘结论，先核对盘后数据完整性。')
  assert.equal(payload.nextActions[0].targetPage, '/quant/jobs')
  assert.equal(payload.nextActions[0].isCrossPage, true)
  assert.match(payload.summaryLines[0], /复盘对象 平安银行/)
})

test('buildReviewActionPlan recognizes when current page already matches review candidate', () => {
  const payload = buildReviewActionPlan({
    actionItems: [
      {
        actionType: 'REVIEW_CANDIDATE',
        title: '进入复盘分析',
        reason: '000001 需要补充正式复盘结论。',
        priority: 'P2',
        targetPage: '/quant/review',
        targetQuery: { reviewLevel: 'trade', stockCode: '000001', strategyId: 2 },
        recommendedAction: 'REVIEW_CANDIDATE',
        sourceAction: 'reviewCandidate'
      }
    ],
    reviewContext: {
      reviewLevel: 'trade',
      stockCode: '000001',
      strategyId: 2,
      hasContext: true,
      sourceAction: 'reviewCandidate',
      sourceActionLabel: '待复盘对象'
    },
    summaryPayload: {
      reviewTargetName: '平安银行',
      reviewConclusion: 'WARNING',
      suggestedAction: 'REDUCE_WEIGHT'
    },
    reconciliationSummary: {
      pendingSignalCount: 0,
      unmatchedExecutionCount: 1,
      missedSignalCount: 0
    }
  })

  assert.equal(payload.headline, '当前已定位到复盘对象，可直接补充结论并沉淀动作。')
  assert.equal(payload.nextActions[0].isCurrentPage, true)
  assert.equal(payload.nextActions[0].currentSectionRef, 'conclusionSection')
  assert.match(payload.summaryLines[1], /直接在当前复盘页/)
})
