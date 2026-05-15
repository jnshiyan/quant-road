const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

function read(relativePath) {
  return fs.readFileSync(path.join(__dirname, '..', relativePath), 'utf8')
}

function readRepo(relativePath) {
  return fs.readFileSync(path.join(__dirname, '..', '..', relativePath), 'utf8')
}

test('quant page copy checks are anchored to the stable regulation docs', () => {
  const designDoc = readRepo('docs/quant/page-design-regulations.md')
  assert.equal(designDoc.includes('operations consoles'), true)
})

test('quant pages avoid verbose hero copy and keep concise page labels', () => {
  const jobs = read('src/views/quant/jobs/index.vue')
  const manual = read('src/views/quant/dispatch-manual/index.vue')
  const auto = read('src/views/quant/dispatch-auto/index.vue')
  const detail = read('src/views/quant/dispatch-detail/index.vue')
  const operations = read('src/views/quant/operations/index.vue')
  const backtest = read('src/views/quant/backtest/index.vue')
  const symbols = read('src/views/quant/symbols/index.vue')
  const review = read('src/views/quant/review/index.vue')
  const shadow = read('src/views/quant/shadow/index.vue')
  const dashboard = read('src/views/quant/dashboard/index.vue')

  assert.equal(jobs.includes('<h1>调度中心</h1>'), true)
  assert.equal(jobs.includes('最近 3 条调度结果'), true)
  assert.equal(jobs.includes('最近进展与技术摘要'), true)
  assert.equal(jobs.includes('今日结论'), false)
  assert.equal(jobs.includes('主操作'), false)
  assert.equal(jobs.includes('统一调度入口'), false)
  assert.equal(manual.includes('<h1>手工调度</h1>'), true)
  assert.equal(auto.includes('<h1>自动调度</h1>'), true)
  assert.equal(detail.includes('调度详情'), true)
  assert.equal(operations.includes('<h1>运维中心</h1>'), true)
  assert.equal(backtest.includes('<div class="page-title">回测研究</div>'), true)
  assert.equal(symbols.includes('<div class="page-title">标的体系</div>'), true)
  assert.equal(review.includes('<span>复盘分析</span>'), true)
  assert.equal(shadow.includes('<span>影子对比</span>'), true)
  assert.equal(dashboard.includes('<div class="dashboard-hero__title">今日状态</div>'), true)
  assert.equal(review.includes('<span>结论动作</span>'), true)
  assert.equal(shadow.includes('<span>治理建议</span>'), true)
  assert.equal(dashboard.includes('<span class="section-meta">只保留 3 个入口</span>'), true)
  assert.equal(backtest.includes('<span class="section-meta">研究口径</span>'), true)
  assert.equal(symbols.includes('<span class="section-meta">正式范围</span>'), true)

  const bannedPhrases = [
    '先判断今天能不能继续，再决定是查看当前调度还是发起新任务。',
    '先确认这次准备跑什么、范围有多大，再把任务正式提交出去。',
    '看清系统会自动跑什么、多久跑一次，以及最近自动结果是否正常。',
    '把这次任务提交了什么、现在跑到哪一步、正在处理谁、有没有结果，直接放到一个页面看清楚。',
    '当首页提示先处理警告或阻断时，在这里判断哪里坏了、影响多大、该怎么恢复。',
    '把策略运行、范围模型、影子对比和 canary 结论收进同一条研究闭环。',
    '把指数层、ETF层、个股层，以及股票池规则、范围约束和运行观测统一到一个页面里。',
    '把信号、执行、持仓、风险与治理串成统一判断链',
    '从候选优势到治理结论的一站式评审页',
    '今天先做什么，再决定去哪里执行、复盘或治理。',
    '运行前先解释“为什么是这些标的”',
    '先解释研究范围，再进入回测或执行',
    '回答为什么买、为什么卖、执行点在哪里',
    '优先处理已经沉淀为正式 case 的对象',
    '策略在什么环境下有效',
    '结论必须可落成后续动作',
    '当前运营动作',
    '先判断环境，再判断执行，再判断风险',
    '首页统一承载指数、ETF、个股三类对象，但只突出当前主线',
    '离开看板前只保留最重要的 3 条动作',
    '先确认仓位是否仍在预算内',
    '先看明天要执行什么，再看为什么执行',
    '持仓是信号落地后的最终事实',
    '判断当前市场环境是否适合继续加仓',
    '预设范围 + 自定义约束，确保研究、回测、执行口径一致',
    '解释当前策略需要哪些输入参数',
    '规则池与人工调整统一进入正式范围模型',
    '指数层负责分析，ETF层负责真实交易承接',
    '把结果翻译成治理语言',
    '把异常月份带入复盘页',
    '提交正式结论并保留审计',
    '辅助解释适用场景',
    '同一策略对的人工决策留痕',
    '恢复动作只保留首页判断不了、但又会阻断你继续推进业务的操作。'
  ]

  ;[jobs, manual, auto, detail, operations, backtest, symbols, review, shadow, dashboard].forEach(source => {
    bannedPhrases.forEach(phrase => {
      assert.equal(source.includes(phrase), false)
    })
  })
})

test('dashboard and backtest replace first-screen explanation paragraphs with compact guide components', () => {
  const dashboard = read('src/views/quant/dashboard/index.vue')
  const backtest = read('src/views/quant/backtest/index.vue')

  assert.equal(dashboard.includes('guide-step-grid'), false)
  assert.equal(dashboard.includes('guide-step__index'), false)
  assert.equal(dashboard.includes('1. 先看市场与信号'), false)
  assert.equal(dashboard.includes('2. 再看执行闭环'), false)
  assert.equal(dashboard.includes('3. 最后看风险与复盘'), false)

  assert.equal(backtest.includes('scope-usage-chips'), true)
  assert.equal(backtest.includes('scopeUsageTags'), true)
  assert.equal(backtest.includes('backtest-secondary-collapse'), true)
  assert.equal(backtest.includes('请先选择研究范围'), true)
  assert.equal(backtest.includes('hero-guide-chips'), false)
  assert.equal(backtest.includes('scope-preview__rule-body'), false)
})

test('dispatch center pages replace hero explanation sentences with compact guide cues', () => {
  const jobs = read('src/views/quant/jobs/index.vue')
  const auto = read('src/views/quant/dispatch-auto/index.vue')
  const currentDispatchMonitor = read('src/views/quant/jobs/components/CurrentDispatchMonitor.vue')
  const taskActionPanel = read('src/views/quant/jobs/components/TaskActionPanel.vue')

  assert.equal(jobs.includes('<p>先看状态，再选动作。</p>'), false)
  assert.equal(auto.includes('<p>先看计划，再看结果。</p>'), false)
  assert.equal(currentDispatchMonitor.includes('把系统现在正在等待什么、正在跑哪些分片直接展示出来'), false)
  assert.equal(taskActionPanel.includes('把最应该先做的动作放在前面，其他入口作为补充。'), false)

  assert.equal(jobs.includes('hero-guide-chips'), false)
  assert.equal(auto.includes('hero-shell'), false)
  assert.equal(auto.includes('hero-decision-card'), false)
  assert.equal(auto.includes('dispatch-auto-header'), true)
  assert.equal(auto.includes('dispatch-auto-topline'), true)
  assert.equal(auto.includes('当前自动计划'), true)
  assert.equal(auto.includes('最近自动结果'), true)
  assert.equal(auto.includes('当前异常'), true)
  assert.equal(auto.includes('dispatch-auto-secondary-collapse'), true)
  assert.equal(currentDispatchMonitor.includes('monitor-guide-chips'), false)
  assert.equal(taskActionPanel.includes('panel-guide-chips'), false)
})

test('manual dispatch, shadow, and symbols pages compress explanation blocks into guide cues', () => {
  const manual = read('src/views/quant/dispatch-manual/index.vue')
  const shadow = read('src/views/quant/shadow/index.vue')
  const symbols = read('src/views/quant/symbols/index.vue')

  assert.equal(manual.includes('hero-shell'), false)
  assert.equal(manual.includes('hero-decision-card'), false)
  assert.equal(manual.includes('manual-dispatch-header'), true)
  assert.equal(manual.includes('manual-dispatch-topline'), true)
  assert.equal(manual.includes('查看本次提交摘要'), false)
  assert.equal(manual.includes('提交前先看提交契约'), true)
  assert.equal(manual.includes(':lg="15"'), true)
  assert.equal(manual.includes(':lg="9"'), true)
  assert.equal(manual.includes('<p>确认范围后提交。</p>'), false)
  assert.equal(manual.includes('选择本次要运行的策略模板，总资金会影响仓位与建议数量。'), false)
  assert.equal(manual.includes('行情时间范围决定读取哪段历史行情；策略起算日决定从哪天开始允许产生交易与评估结果。'), false)
  assert.equal(manual.includes('左侧是范围输入方式，右侧是最终解析结果。真正提交的是右侧解析结果。'), false)
  assert.equal(manual.includes('hero-guide-chips'), false)
  assert.equal(manual.includes('step-guide-chips'), false)

  assert.equal(shadow.includes('applicability-guide-chips'), false)
  assert.equal(shadow.includes('<span>适用性说明</span>'), false)

  assert.equal(symbols.includes('layer-card__chips'), true)
  assert.equal(symbols.includes('layer-card__footer'), false)
})

test('execution, operations, and review pages replace explanation labels with compact guide cues', () => {
  const execution = read('src/views/quant/execution/index.vue')
  const operations = read('src/views/quant/operations/index.vue')
  const review = read('src/views/quant/review/index.vue')

  assert.equal(execution.includes('今日闭环状态'), true)
  assert.equal(execution.includes('异常优先级'), true)
  assert.equal(execution.includes('当前异常列表'), true)
  assert.equal(execution.includes('execution-page-header'), true)
  assert.equal(execution.includes('execution-topline'), true)
  assert.equal(execution.includes('execution-first-screen-grid'), true)
  assert.equal(execution.includes('首要动作'), true)
  assert.equal(execution.includes('手工触发'), true)
  assert.equal(execution.includes('批量导入回写'), true)
  assert.equal(execution.includes('手工成交回写（仅在券商回流缺失时使用）'), true)
  assert.equal(execution.includes('手工成交回写</span>'), false)
  assert.equal(execution.includes('执行闭环解释'), false)
  assert.equal(execution.includes('CSV 列约定：stock_code, side, quantity, price, trade_date；可选列：signal_id, commission, tax, slippage, external_order_id。'), false)
  assert.equal(execution.includes('execution-guide-chips'), false)
  assert.equal(execution.includes('csv-guide-chips'), false)
  assert.equal(execution.includes('execution-hero__title'), false)
  assert.equal(execution.includes('secondary-section-collapse'), true)
  assert.equal(execution.includes('手工处理工具'), true)
  assert.equal(execution.includes('辅助核对与持仓同步'), true)

  assert.equal(operations.includes('只保留阻断恢复动作。'), false)
  assert.equal(operations.includes('标准路径失效时再用。'), false)
  assert.equal(operations.includes('当前阻断'), true)
  assert.equal(operations.includes('建议恢复'), true)
  assert.equal(operations.includes('技术状态'), true)
  assert.equal(operations.includes('高级兜底工具'), true)
  assert.equal(operations.includes('operations-secondary-collapse'), true)
  assert.equal(operations.includes('ops-guide-chips'), true)
  assert.equal(operations.includes('toolbox-guide-chips'), false)

  assert.equal(review.includes('当前结论'), true)
  assert.equal(review.includes('先看结论，再看证据'), true)
  assert.equal(review.includes('核心证据'), true)
  assert.equal(review.includes('review-secondary-collapse'), true)
  assert.equal(review.includes('更多图表与案例'), true)
  assert.equal(review.includes('事件链路 / 调试证据'), true)
  assert.equal(review.includes('规则解释区'), false)
  assert.equal(review.includes('规则说明'), false)
  assert.equal(review.includes('rule-guide-chips'), false)
})

test('shadow and symbols pages emphasize governance-first and clear three-layer structure', () => {
  const shadow = read('src/views/quant/shadow/index.vue')
  const symbols = read('src/views/quant/symbols/index.vue')

  assert.equal(shadow.includes('治理结论'), true)
  assert.equal(shadow.includes('先看建议，再看证据'), true)
  assert.equal(shadow.includes('治理建议'), true)
  assert.equal(shadow.includes('提交治理动作'), true)
  assert.equal(shadow.includes('shadow-secondary-collapse'), true)
  assert.equal(shadow.includes('当前动作'), false)

  assert.equal(symbols.includes('<div class="page-subtitle">定义 / 治理 / 运行观测</div>'), true)
  assert.equal(symbols.includes('当前正式范围'), true)
  assert.equal(symbols.includes('体系说明'), true)
  assert.equal(symbols.includes('symbols-secondary-collapse'), true)
  assert.equal(symbols.includes('<el-tab-pane label="治理层" name="governance">'), true)
  assert.equal(symbols.includes('<el-tab-pane label="运行观测" name="runtime">'), true)
  assert.equal(symbols.includes('<el-tab-pane label="观测层" name="runtime">'), false)
})

test('quant pages keep cross-page action labels and section meta concise', () => {
  const dashboard = read('src/views/quant/dashboard/index.vue')
  const backtest = read('src/views/quant/backtest/index.vue')
  const execution = read('src/views/quant/execution/index.vue')
  const symbols = read('src/views/quant/symbols/index.vue')
  const review = read('src/views/quant/review/index.vue')
  const shadow = read('src/views/quant/shadow/index.vue')
  const operations = read('src/views/quant/operations/index.vue')
  const auto = read('src/views/quant/dispatch-auto/index.vue')

  assert.equal(backtest.includes('去股票池治理页维护'), false)
  assert.equal(backtest.includes('当前研究配置'), true)
  assert.equal(backtest.includes('当前研究任务'), true)
  assert.equal(backtest.includes('研究结果摘要'), true)
  assert.equal(backtest.includes('更多研究证据'), true)
  assert.equal(execution.includes('进入复盘'), false)
  assert.equal(review.includes('>进入<'), false)
  assert.equal(shadow.includes('进入复盘'), false)
  assert.equal(shadow.includes('暂无可跳转复盘对象'), false)
  assert.equal(operations.includes('手工消费分片'), true)
  assert.equal(auto.includes('调度中心 / 自动触发视图'), true)
  assert.equal(auto.includes('发起手工调度'), false)

  const bannedMetaPhrases = [
    '把异常执行、策略失效和治理样本收口到正式复盘页',
    '市场状态变化后系统做了什么决策',
    '治理线索不应该埋在日志里',
    '下沉到首屏底部，避免日志盖过动作判断',
    '主 ETF 已经是独立治理对象，可直接去复盘或回测',
    '把 formal case 的时间维度和结论沉淀挂回标的治理页',
    '最近已经进入 formal case 的 ETF 对象，便于从治理页回看历史处理结果',
    '哪些 ETF 在 formal case 里反复出现，说明需要长期观察',
    '同一治理口径下比较承接层与主动交易层的治理负载',
    '把信号、持仓、成交、反馈按标的聚合，验证当前池和真实交易是否一致'
  ]

  ;[dashboard, symbols].forEach(source => {
    bannedMetaPhrases.forEach(phrase => {
      assert.equal(source.includes(phrase), false)
    })
  })
})
