const test = require('node:test')
const assert = require('node:assert/strict')

const {
  buildFullDailySubmissionGuard,
  isConstrainedFullDailyScope
} = require('../src/views/quant/jobs/jobs-full-daily-guard')

test('buildFullDailySubmissionGuard warns before broad market fullDaily runs', () => {
  const guard = buildFullDailySubmissionGuard({
    scopeType: 'all_stocks',
    resolvedCount: 0
  })

  assert.equal(guard.requiresConfirmation, true)
  assert.equal(guard.level, 'warning')
  assert.equal(guard.loadLevel, 'heavy')
  assert.match(guard.summary, /广范围|全市场/)
  assert.match(guard.message, /全市场/)
  assert.match(guard.message, /耗时|较长/)
})

test('buildFullDailySubmissionGuard summarizes ETF scope without broad-market warning', () => {
  const guard = buildFullDailySubmissionGuard({
    scopeType: 'etf_pool',
    scopePoolCode: 'ETF_CORE',
    resolvedCount: 2
  })

  assert.equal(guard.requiresConfirmation, false)
  assert.equal(guard.level, 'success')
  assert.equal(guard.loadLevel, 'light')
  assert.match(guard.summary, /小范围|受限范围/)
  assert.match(guard.notice, /ETF池/)
  assert.match(guard.notice, /2 个标的/)
})

test('buildFullDailySubmissionGuard marks medium constrained scope separately', () => {
  const guard = buildFullDailySubmissionGuard({
    scopeType: 'stock_pool',
    scopePoolCode: 'STOCK_CORE',
    resolvedCount: 48
  })

  assert.equal(guard.requiresConfirmation, false)
  assert.equal(guard.loadLevel, 'medium')
  assert.match(guard.summary, /48 个标的/)
})

test('isConstrainedFullDailyScope treats explicit symbols as constrained', () => {
  assert.equal(isConstrainedFullDailyScope({
    scopeType: 'all_stocks',
    symbols: ['510300', '510500']
  }), true)
})
