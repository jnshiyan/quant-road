const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

test('dashboard page exposes a compact decision-home structure', () => {
  const source = fs.readFileSync(path.join(__dirname, '../src/views/quant/dashboard/index.vue'), 'utf8')

  assert.equal(source.includes('今日状态'), true)
  assert.equal(source.includes('今日主动作'), true)
  assert.equal(source.includes('下一步去哪里'), true)
  assert.equal(source.includes('更多系统概览'), true)
  assert.equal(source.includes('调度中心'), true)
  assert.equal(source.includes('执行闭环'), true)
  assert.equal(source.includes('复盘治理'), true)
  assert.equal(source.includes('对象层摘要'), true)
  assert.equal(source.includes('指数'), true)
  assert.equal(source.includes('ETF'), true)
  assert.equal(source.includes('个股'), true)

  ;[
    '策略运行日志',
    '策略切换审计',
    'Canary 评估',
    'T+1 执行反馈',
    '指数估值快照',
    '当前持仓',
    '当日交易信号',
    '待复盘对象'
  ].forEach(label => {
    assert.equal(source.includes(label), false)
  })
})
