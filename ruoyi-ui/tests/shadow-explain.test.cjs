const test = require('node:test')
const assert = require('node:assert/strict')

const { buildShadowActionPlan } = require('../src/views/quant/shadow/shadow-explain')

test('buildShadowActionPlan escalates data integrity gate before governance decision', () => {
  const payload = buildShadowActionPlan({
    actionItems: [
      {
        actionType: 'DATA_INTEGRITY_REVIEW',
        title: '核对盘后数据完整性',
        reason: '盘后结果仍有缺口，当前治理证据不稳定。',
        priority: 'P0',
        targetPage: '/quant/jobs',
        targetQuery: { batchId: 15 },
        recommendedAction: 'DATA_INTEGRITY_REVIEW',
        sourceAction: 'dataIntegrityGate'
      }
    ],
    queryParams: {
      baselineStrategyId: 1,
      candidateStrategyId: 2,
      months: 6
    },
    summaryPayload: {
      recommendation: 'OBSERVE',
      governanceAction: 'OBSERVE',
      recommendationReason: '候选策略还需要继续观察。',
      baseline: { strategy_id: 1, strategy_name: 'Baseline' },
      candidate: { strategy_id: 2, strategy_name: 'Candidate' },
      summary: { comparableMonths: 6 }
    }
  })

  assert.equal(payload.headline, '先不要提交治理决策，先核对盘后数据完整性。')
  assert.equal(payload.nextActions[0].targetPage, '/quant/jobs')
  assert.equal(payload.nextActions[0].isCrossPage, true)
  assert.match(payload.summaryLines[0], /Baseline vs Candidate/)
})

test('buildShadowActionPlan recognizes when governance pair is already on current page', () => {
  const payload = buildShadowActionPlan({
    actionItems: [],
    queryParams: {
      baselineStrategyId: 1,
      candidateStrategyId: 2,
      months: 6
    },
    summaryPayload: {
      recommendation: 'PROMOTE_CANDIDATE',
      governanceAction: 'REPLACE',
      confidenceLevel: 'HIGH',
      recommendationReason: '候选策略在多数月份更优，可进入替换决策。',
      baseline: { strategy_id: 1, strategy_name: 'Baseline' },
      candidate: { strategy_id: 2, strategy_name: 'Candidate' },
      summary: { comparableMonths: 6 }
    }
  })

  assert.equal(payload.headline, '当前已定位到治理对象，可直接沉淀正式治理决策。')
  assert.equal(payload.nextActions[0].isCurrentPage, true)
  assert.equal(payload.nextActions[0].currentSectionRef, 'decisionSection')
  assert.match(payload.summaryLines[1], /直接在当前治理页/)
})
