const test = require('node:test')
const assert = require('node:assert/strict')

const { buildDashboardNarrative } = require('../src/views/quant/dashboard/dashboard-explain')

test('buildDashboardNarrative prioritizes top action item', () => {
  const payload = buildDashboardNarrative({
    actionItems: [
      { title: '处理未匹配成交', reason: '有 3 条未匹配成交', priority: 'P0' }
    ],
    marketStatus: { status: 'volatile' },
    riskSummary: { riskLevel: 'MEDIUM', etfRiskWarningCount: 1, equityRiskWarningCount: 2 },
    reviewCandidates: [{}, {}]
  })

  assert.equal(payload.headline, '今日首要动作：处理未匹配成交。')
  assert.equal(payload.nextActions.length, 1)
  assert.match(payload.summaryLines[0], /风险等级 MEDIUM/)
})

test('buildDashboardNarrative escalates data integrity gate into the headline', () => {
  const payload = buildDashboardNarrative({
    actionItems: [
      {
        actionType: 'DATA_INTEGRITY_REVIEW',
        title: '核对盘后数据完整性',
        reason: '日线同步存在失败标的，建议先核对失败标的后再继续运营。',
        priority: 'P0'
      }
    ],
    marketStatus: { status: 'volatile' },
    riskSummary: { riskLevel: 'LOW' },
    reviewCandidates: []
  })

  assert.equal(payload.headline, '今日先不要直接执行，先核对盘后数据完整性。')
  assert.match(payload.summaryLines[0], /市场状态 volatile/)
  assert.match(payload.summaryLines[1], /待处理事项 1 个/)
})
