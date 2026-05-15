# 量化模块未完成功能总代办清单

> 日期：2026-05-07  
> 目标：基于 `docs/quant-system` 全套规格、当前前后端实现与测试覆盖，整理真实未完成功能，并给出逐项修复方案。  
> 原则：优先修复能形成业务闭环的能力，而不是先做装饰性增强。

---

## 1. 盘点方法

本清单同时对齐了 4 类事实来源：

1. 页面级规格：`12-页面级产品规格说明.md`
2. 当前差距文档：`07-当前实现效果与差距清单.md`
3. 页面流程文档：`09-页面体系与操作流程说明.md`
4. 代码现状：`ruoyi-ui / ruoyi-admin / python` 当前实现与测试

状态定义：

1. `completed`
   - 已完成并有验证
2. `in_progress`
   - 已进入实现，但前后端或验证未完全收口
3. `pending`
   - 尚未进入正式实现

---

## 2. 总体结论

当前主链完成度如下：

1. `任务中心增强`
   - `completed`
   - `jobReadiness / jobErrorCategories / jobSopHints / recoverBatch` 已落地

2. `执行闭环增强`
   - `completed`
   - `validateExecutionImport / executionMatchCandidates / confirmExecutionMatch / positionSyncResult / executionReconciliationSummary / markExecutionException` 已落地

3. `治理增强`
   - `completed`
   - 影子对比、治理决策、治理历史与复盘跳转已收口为正式治理台

4. `复盘图表化`
   - `completed`
   - `K线 / 持仓区间 / 净值回撤 / 市场叠加 / 时间线 / 规则解释 / 结论提交` 已产品化

5. `标的体系 / 股票池规则产品化`
   - `completed`
   - 后端范围模型、股票池接口、`symbols / backtest` 页面与系统级烟测已收口

6. `看板动作层`
   - `completed`
   - 看板、任务中心、执行页都已具备业务解释层与下一步动作指引

---

## 3. 代办清单

### Q-001 影子对比升级为正式治理页

- 状态：`completed`
- 优先级：`P0`
- 证据：
  - 规格要求 `shadowCompareSummary / shadowCompareCharts / shadowCompareApplicability / governance decision / history / shadowReviewLinks`
  - 当前页面此前仅有基础查询、月度表和策略能力表
- 目标：
  - 让用户先看到治理建议，再看证据，再提交正式决策
- 修复方案：
  1. 后端新增治理聚合服务
     - `GET /quant/data/shadowCompareSummary`
     - `GET /quant/data/shadowCompareCharts`
     - `GET /quant/data/shadowCompareApplicability`
     - `GET /quant/data/shadowReviewLinks`
     - `POST /quant/governance/decision`
     - `GET /quant/governance/history`
  2. 落治理审计表 `quant_governance_decision`
  3. 影子对比页改造成治理台
     - 结论卡
     - 图形证据区
     - 适用范围说明
     - 治理决策表单
     - 历史决策表
     - 跳转复盘链接
  4. 增加前后端测试和烟测
- 验收：
  - 用户能完成 `keep / observe / replace / disable`
  - 决策可留痕
  - 页面能直接跳转到复盘页

### Q-002 复盘页从“摘要页”升级为“正式复盘页”

- 状态：`completed`
- 优先级：`P0`
- 证据：
  - `08-复盘体系与图表需求说明.md`
  - `12-页面级产品规格说明.md` 第 7 章
- 当前缺口：
  - 无 `K线 + 买卖点图`
  - 无 `持仓区间复盘图`
  - 无 `净值 / 基准 / 回撤图`
  - 无 `市场状态叠加图`
  - 无 `事件时间线`
  - 无 `规则解释区`
  - 无 `复盘结论提交`
- 修复方案：
  1. 先补核心后端聚合接口
     - `GET /quant/review/summary`
     - `GET /quant/review/kline`
     - `GET /quant/review/holdingRange`
     - `GET /quant/review/navDrawdown`
     - `GET /quant/review/marketOverlay`
     - `GET /quant/review/timeline`
     - `GET /quant/review/ruleExplain`
     - `POST /quant/review/conclusion`
  2. 复盘页改造成三层结构
     - 顶部结论卡
     - 中部证据图层
     - 底部时间线 + 原始附录
  3. 统一与 `execution / dashboard / shadow` 的跨页参数
  4. 增加复盘结论写入和审计
- 验收：
  - 用户可顺着判断链回答：
    - 信号是否合理
    - 执行是否偏离
    - 持仓结果是否符合预期
    - 是否需要治理动作

### Q-003 看板动作层补齐

- 状态：`completed`
- 优先级：`P1`
- 证据：
  - `12-页面级产品规格说明.md` 第 5 章建议新增接口
  - `13-量化模块页面改版实施方案（研发版）.md` 建议新增 `todoCenter`
- 当前缺口：
  - 缺 `dashboardActionItems / todoCenter`
  - 缺 `signalExplain`
  - 缺 `positionRiskSummary`
  - 缺 `dashboardDeepLinks`
  - 缺 `reviewCandidates`
- 修复方案：
  1. 先补后端聚合接口
     - `GET /quant/data/dashboardActionItems`
     - `GET /quant/data/positionRiskSummary`
     - `GET /quant/data/dashboardDeepLinks`
     - `GET /quant/data/reviewCandidates`
     - `GET /quant/data/signalExplain/{signalId}`
  2. 看板新增“待处理事项”区和风险摘要区
  3. 支持从信号、风险、异常项直跳执行页 / 复盘页 / 任务中心
- 验收：
  - 用户离开看板时必须形成：
    - 明天要执行什么
    - 当前要关注什么风险
    - 哪些对象要进入复盘或治理

### Q-004 标的体系与股票池规则产品化

- 状态：`completed`
- 优先级：`P1`
- 证据：
  - `03-标的体系与股票池规则.md`
  - `07-当前实现效果与差距清单.md`
- 当前进展：
  1. 已落地正式 `scopeType / scopePoolCode / whitelist / blacklist / adHocSymbols` 范围模型
  2. 已新增股票池、池成员、指数到 ETF 映射表与默认种子数据
  3. 已补 `/quant/data/symbolScopeOptions / symbolPools / symbolPoolDetail / indexEtfMappings / symbolScopePreview`
  4. 已完成 `symbols` 页产品化和 `backtest` 页范围模型接入
  5. 已补后端测试、前端生产构建与 Playwright 系统级烟测验证
- 修复方案：
  1. 定义池模型与表结构
     - 池类型
     - 池内规则
     - 池成员调整记录
  2. 新增标的治理页或升级 `symbols` 页
     - 池概览
     - 规则说明
     - 成员明细
     - 人工调整记录
  3. 回测、策略执行、看板、复盘统一使用同一范围模型
- 验收：
  - 用户能清楚选择和解释：
    - 全市场
    - 个股池
    - ETF池
    - 指数映射ETF池

### Q-005 ETF 作为独立业务对象正式落地

- 状态：`completed`
- 优先级：`P2`
- 证据：
  - `04-回测体系与评估方法.md`
  - `07-当前实现效果与差距清单.md`
- 当前进展：
  1. 已新增 `etfOverview` 聚合接口
  2. 已在看板新增 ETF 专题对象层，显示 ETF 池规模、映射规模、持仓、信号、风险预警与指数 -> ETF 承接关系
  3. 已补 `backtest / symbols` 的 route scope hydration，可从看板直接跳转到 ETF 范围专题
  4. 已补持仓风险摘要中的 `ETF / 股票` 风险拆分口径与单仓预算差异展示
  5. 已补看板到复盘页的 ETF 专属入口，并把 `scopeType / scopePoolCode / sourceAction` 正式透传
  6. 已在待复盘对象中显示 `assetType`，支持 ETF 风险对象直接进入正式复盘页
  7. 已新增 `etfGovernanceSummary` 正式聚合接口，把映射、信号、持仓、风险和治理动作收口到同一对象模型
  8. 已在 `symbols` 页新增 ETF 治理摘要与治理对象清单，支持主 ETF 直接进入复盘页和回测页
  9. 已把 ETF 治理对象正式纳入 `reviewCandidates / reviewCases`，并新增 `ETF_REVIEW` case 类型
  10. 已在 `symbols` 页新增 ETF 治理证据图、治理动作分布与当前治理焦点队列，把“为什么先处理谁”直接展示给业务用户
  11. 已补 ETF 治理解释层纯函数测试，并再次通过前端生产构建与 Playwright 系统级烟测
  12. 已补 `reviewCases` 的 `caseType / assetType` 正式过滤能力，并在 `symbols` 页新增“最近 ETF 正式复盘记录”，把当前治理与历史 formal case 直接串起来
  13. 已在 `symbols` 页新增 ETF formal case 的历史趋势、OPEN/CLOSED 状态统计和复盘结论分布，开始把 ETF 治理闭环拉出时间维度
  14. 已在 `symbols` 页新增 ETF 热点对象排行和 `ETF / 个股治理对比`，把承接层和主动交易层放到统一治理刻度中比较
- 当前结论：
  - ETF 已具备独立研究、范围选择、回测跳转、执行承接、复盘 case、治理证据和历史趋势能力，主链已形成闭环
- 修复方案：
  1. 明确指数 -> 主ETF -> 备选ETF 映射
  2. ETF 独立风控参数
  3. ETF 独立回测和复盘口径
  4. ETF 在标的页、看板、复盘页单独展示
- 验收：
  - ETF 可独立研究、执行、复盘、治理

### Q-006 回测范围模型正式化

- 状态：`completed`
- 优先级：`P2`
- 证据：
  - `03-标的体系与股票池规则.md`
  - `04-回测体系与评估方法.md`
- 当前进展：
  1. 已落地正式范围模型
     - `scopeType / scopePoolCode / symbols / whitelist / blacklist / adHocSymbols`
  2. 已完成 `backtest / jobs / review` 的统一透传与页面级解释
  3. 已补 `review-context` 测试，确保跨页 query 上下文不丢范围字段
  4. 已通过前端生产构建与 Playwright 系统级烟测
- 修复方案：
  1. 新增范围模型
     - 全市场
     - 股票池
     - ETF池
     - 指数映射ETF池
     - 白名单/黑名单
  2. 回测页、任务中心、策略页共用范围参数
  3. 输出按范围分层的回测解释
- 验收：
  - 研究范围、回测范围、执行范围口径统一

### Q-007 复盘对象与 case 模型正式化

- 状态：`completed`
- 优先级：`P2`
- 证据：
  - `13-量化模块页面改版实施方案（研发版）.md`
- 当前进展：
  1. 已新增正式接口
     - `GET /quant/review/cases`
     - `GET /quant/review/caseDetail?caseId=...`
  2. 已落地 `quant_review_case` 正式 case 表与同步逻辑
  3. 已支持 `caseId` 写入 `/quant/review/summary` 与 `/quant/review/conclusion`
  4. 已完成复盘页从 `reviewCandidates` 到 `reviewCases` 的主流程迁移
  5. 已补看板到复盘页的正式 `caseId` 透传，避免入口层回退到临时 query 拼装
  6. 已补前后端测试、前端构建与系统级烟测验证
- 修复方案：
  1. 新增 `reviewCases`
  2. 新增 `reviewCaseDetail/{id}`
  3. 把风险预警、异常执行、策略异常统一成正式 case
- 验收：
  - 复盘入口不依赖前端临时拼装

### Q-008 看板 / 任务中心 / 执行页的业务解释层继续加强

- 状态：`completed`
- 优先级：`P2`
- 当前进展：
  1. 已在看板新增“今日建议动作”和“看板使用顺序”，把市场状态、待处理事项、复盘对象和风险摘要整理成可执行叙事
  2. 已在任务中心新增“日常运营动作”和“高级排障动作”，把 worker 状态、失败批次恢复和业务就绪信息解释成运维动作
  3. 已在执行页新增“执行闭环解释”和“下一步建议”，把 `信号 -> 成交 -> 反馈 -> 持仓` 缺口按优先顺序说明清楚
  4. 已补纯函数解释层测试，并通过前端构建与 Playwright 系统级烟测验证
- 修复方案：
  1. 任务中心继续分离“日常运营动作”和“高级排障动作”
  2. 看板补“今日建议动作”
  3. 执行页补“信号 -> 成交 -> 持仓”可视化链说明
- 验收：
  - 非研发用户能在页面里完成下一步动作判断

---

## 4. 执行顺序

按主链收益和依赖关系，执行顺序定为：

1. `Q-001 影子对比升级为正式治理页`
2. `Q-002 复盘页图表化与结论化`
3. `Q-003 看板动作层补齐`
4. `Q-004 标的体系与股票池规则产品化`
5. `Q-006 回测范围模型正式化`
6. `Q-005 ETF 独立对象化`
7. `Q-007 复盘 case 模型正式化`
8. `Q-008 业务解释层继续增强`

---

## 5. 当前施工状态

### 已完成

1. 任务中心增强
2. 执行闭环增强
3. `execution -> review` 上下文跳转

### 正在进行

1. 无主链未收口事项

### 下一个施工点

1. 转入剩余非主链优化项梳理
2. 如继续增强，优先做复盘页的更深图表表达和治理横向分析
