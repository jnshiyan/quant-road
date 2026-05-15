const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const { buildSopRouteLocation } = require('../src/views/quant/jobs/jobs-route')

test('buildSopRouteLocation keeps target query from JSON params', () => {
  const payload = buildSopRouteLocation({
    targetPage: '/quant/execution',
    targetParams: '{"focus":"unmatched","stockCode":"000001","strategyId":2}'
  })

  assert.deepEqual(payload, {
    path: '/quant/execution',
    query: {
      focus: 'unmatched',
      stockCode: '000001',
      strategyId: 2
    }
  })
})

test('buildSopRouteLocation safely degrades when target params are invalid', () => {
  const payload = buildSopRouteLocation({
    targetPage: '/quant/jobs',
    targetParams: 'not-json'
  })

  assert.deepEqual(payload, {
    path: '/quant/jobs',
    query: {}
  })
})

test('jobs page should use dispatch center wording and show key sections', () => {
  const source = fs.readFileSync(path.join(__dirname, '../src/views/quant/jobs/index.vue'), 'utf8')
  const primaryTaskSource = fs.readFileSync(path.join(__dirname, '../src/views/quant/jobs/components/PrimaryTaskCard.vue'), 'utf8')
  const manualSource = fs.existsSync(path.join(__dirname, '../src/views/quant/dispatch-manual/index.vue'))
    ? fs.readFileSync(path.join(__dirname, '../src/views/quant/dispatch-manual/index.vue'), 'utf8')
    : ''
  const manualSummarySource = fs.existsSync(path.join(__dirname, '../src/views/quant/dispatch-shared/ManualDispatchSummaryCard.vue'))
    ? fs.readFileSync(path.join(__dirname, '../src/views/quant/dispatch-shared/ManualDispatchSummaryCard.vue'), 'utf8')
    : ''
  const detailSource = fs.existsSync(path.join(__dirname, '../src/views/quant/dispatch-detail/index.vue'))
    ? fs.readFileSync(path.join(__dirname, '../src/views/quant/dispatch-detail/index.vue'), 'utf8')
    : ''
  const autoSource = fs.existsSync(path.join(__dirname, '../src/views/quant/dispatch-auto/index.vue'))
    ? fs.readFileSync(path.join(__dirname, '../src/views/quant/dispatch-auto/index.vue'), 'utf8')
    : ''

  assert.equal(source.includes('量化调度中心'), true)
  assert.equal(source.includes('查看自动计划'), true)
  assert.equal(source.includes('hero-shell'), false)
  assert.equal(source.includes('dispatch-center-header'), true)
  assert.equal(source.includes('dispatch-center-status-line'), true)
  assert.equal(source.includes('今日结论'), false)
  assert.equal(source.includes('主操作'), false)
  assert.equal(source.includes('统一调度入口'), false)
  assert.equal(source.includes('最近 3 条调度结果'), true)
  assert.equal(source.includes('最近进展与技术摘要'), true)
  assert.equal(source.includes('全部调度历史'), true)
  assert.equal(source.includes('调度定义'), true)
  assert.equal(primaryTaskSource.includes('实时快照'), false)
  assert.equal(primaryTaskSource.includes('primary-task-card__status-line'), true)
  assert.equal(primaryTaskSource.includes('primary-task-card__actions'), true)
  assert.equal(source.includes("'/quant/dispatch-manual'"), true)
  assert.equal(source.includes("'/quant/dispatch-auto'"), true)
  assert.equal(source.includes('/quant/dispatch-detail/'), true)

  assert.equal(manualSource.includes('行情时间范围'), true)
  assert.equal(manualSource.includes('策略起算日'), true)
  assert.equal(manualSummarySource.includes('本次提交摘要'), true)
  assert.equal(manualSummarySource.includes('执行任务'), true)

  assert.equal(detailSource.includes('调度详情'), true)
  assert.equal(detailSource.includes('本次执行了什么'), true)
  assert.equal(detailSource.includes('结果与异常'), true)
  assert.equal(detailSource.includes('最近一条日志'), true)
  assert.equal(detailSource.includes('结果明细'), true)
  assert.equal(detailSource.includes('<p>状态、日志、结果。</p>'), false)

  assert.equal(autoSource.includes('调度定义'), true)
  assert.equal(autoSource.includes('最近执行历史'), true)
  assert.equal(autoSource.includes('异常与处理'), true)
})
