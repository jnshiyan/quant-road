const test = require('node:test')
const assert = require('node:assert/strict')

const { summarizeBatchScope } = require('../src/views/quant/jobs/jobs-batch-summary')

test('summarizeBatchScope marks ETF batch as light constrained scope', () => {
  const summary = summarizeBatchScope({
    params: {
      scope_type: 'etf_pool',
      scope_pool_code: 'ETF_CORE',
      symbols: ['510300', '510500']
    }
  })

  assert.equal(summary.scopeLabel, 'ETF池 ETF_CORE')
  assert.equal(summary.loadLabel, '轻量')
  assert.equal(summary.tagType, 'success')
})

test('summarizeBatchScope parses JSON string params and detects broad market batch', () => {
  const summary = summarizeBatchScope({
    params: '{"scope_type":null,"scope_pool_code":null,"symbols":null}'
  })

  assert.equal(summary.scopeLabel, '全市场')
  assert.equal(summary.loadLabel, '重型')
  assert.equal(summary.tagType, 'danger')
})
