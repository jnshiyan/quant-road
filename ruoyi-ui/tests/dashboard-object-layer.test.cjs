const test = require('node:test')
const assert = require('node:assert/strict')

const { buildDashboardObjectLayers } = require('../src/views/quant/dashboard/dashboard-object-layer')

test('returns 指数 ETF 个股 cards in fixed order', () => {
  const cards = buildDashboardObjectLayers({
    etfOverview: { todayEtfSignalCount: 1 },
    reviewCandidates: [{ assetType: 'EQUITY' }],
    marketStatus: { status: 'volatile' },
    executionFeedbackSummary: { pendingSignalCount: 2 }
  })

  assert.deepEqual(cards.map(item => item.key), ['index', 'etf', 'equity'])
  assert.equal(cards[1].title, 'ETF')
  assert.match(cards[2].summary, /1/)
  assert.equal(cards[0].actionLabel, '看指数环境')
  assert.equal(cards[1].actionLabel, '去执行主线')
  assert.equal(cards[2].actionLabel, '去个股复盘')
})
