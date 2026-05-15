const test = require('node:test')
const assert = require('node:assert/strict')

const {
  applyExecutionRouteContext,
  didExecutionRouteDataChange,
  normalizeExecutionRouteContext
} = require('../src/views/quant/execution/execution-route')

test('normalizeExecutionRouteContext parses typed execution route query', () => {
  const payload = normalizeExecutionRouteContext({
    focus: 'unmatched',
    stockCode: '000001',
    strategyId: '2',
    signalId: '3001'
  })

  assert.deepEqual(payload, {
    focusPreset: 'unmatched',
    stockCode: '000001',
    strategyId: 2,
    signalId: 3001,
    hasTradeContext: true
  })
})

test('applyExecutionRouteContext hydrates filters and form from route context', () => {
  const payload = applyExecutionRouteContext(
    {
      focusPreset: 'all',
      stockCode: '000001',
      strategyId: 2,
      signalId: 3001,
      hasTradeContext: true
    },
    {
      signalFilter: { stockCode: '', side: '', strategyId: undefined },
      recordQuery: { stockCode: '', limit: 50 },
      recordForm: { stockCode: '', strategyId: undefined, signalId: undefined, side: 'BUY' }
    }
  )

  assert.deepEqual(payload.signalFilter, {
    stockCode: '000001',
    side: '',
    strategyId: 2
  })
  assert.deepEqual(payload.recordQuery, {
    stockCode: '000001',
    limit: 50
  })
  assert.deepEqual(payload.recordForm, {
    stockCode: '000001',
    strategyId: 2,
    signalId: 3001,
    side: 'BUY'
  })
})

test('applyExecutionRouteContext leaves current state unchanged without trade context', () => {
  const currentState = {
    signalFilter: { stockCode: '600519', side: 'BUY', strategyId: 9 },
    recordQuery: { stockCode: '600519', limit: 100 },
    recordForm: { stockCode: '600519', strategyId: 9, signalId: 88, side: 'SELL' }
  }

  const payload = applyExecutionRouteContext(
    {
      focusPreset: 'all',
      stockCode: '',
      strategyId: undefined,
      signalId: undefined,
      hasTradeContext: false
    },
    currentState
  )

  assert.deepEqual(payload, currentState)
})

test('didExecutionRouteDataChange detects route-driven execution context changes', () => {
  assert.equal(
    didExecutionRouteDataChange(
      { focusPreset: 'all', stockCode: '000001', strategyId: 2, signalId: 3001 },
      { focusPreset: 'unmatched', stockCode: '000001', strategyId: 2, signalId: 3001 }
    ),
    true
  )
  assert.equal(
    didExecutionRouteDataChange(
      { focusPreset: 'all', stockCode: '000001', strategyId: 2, signalId: 3001 },
      { focusPreset: 'all', stockCode: '000001', strategyId: 2, signalId: 3001 }
    ),
    false
  )
})
