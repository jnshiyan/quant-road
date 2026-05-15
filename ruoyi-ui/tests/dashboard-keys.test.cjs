const test = require('node:test')
const assert = require('node:assert/strict')

const {
  buildDashboardActionItemKey,
  buildDashboardDeepLinkKey,
  buildDashboardNarrativeActionKey
} = require('../src/views/quant/dashboard/dashboard-keys')

test('buildDashboardActionItemKey stays unique for duplicate review candidates', () => {
  const first = buildDashboardActionItemKey({
    actionType: 'REVIEW_CANDIDATE',
    targetId: 1,
    title: '进入复盘分析',
    reason: 'MA20_CROSS：000001'
  }, 0)
  const second = buildDashboardActionItemKey({
    actionType: 'REVIEW_CANDIDATE',
    targetId: 1,
    title: '进入复盘分析',
    reason: 'MA20_CROSS：000001'
  }, 1)

  assert.notEqual(first, second)
})

test('buildDashboardDeepLinkKey stays unique for duplicate route links', () => {
  const first = buildDashboardDeepLinkKey({
    path: '/quant/review',
    title: '进入复盘分析',
    badge: 1,
    reason: 'same'
  }, 0)
  const second = buildDashboardDeepLinkKey({
    path: '/quant/review',
    title: '进入复盘分析',
    badge: 1,
    reason: 'same'
  }, 1)

  assert.notEqual(first, second)
})

test('buildDashboardNarrativeActionKey stays unique for duplicate next actions', () => {
  const first = buildDashboardNarrativeActionKey({
    title: '进入复盘分析',
    priority: 'P2',
    reason: 'same'
  }, 0)
  const second = buildDashboardNarrativeActionKey({
    title: '进入复盘分析',
    priority: 'P2',
    reason: 'same'
  }, 1)

  assert.notEqual(first, second)
})
