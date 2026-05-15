# Quant Road

按 [docs/req.md](/d:/hundsun-workspaces/itellij-space/git-workspace/quant-road/docs/req.md) 落地，当前默认走 `req.md` 指定框架：

- Java 后台脚手架：RuoYi-Vue（`ruoyi-admin` + `ruoyi-ui` + `ruoyi-quartz`）
- Python 数据层：AKShare
- Python 回测层：Backtrader
- Python 依赖：Pandas + psycopg2-binary

## 核心开源地址（req 2.3）

- RuoYi-Vue: `https://gitee.com/y_project/RuoYi-Vue`
- RuoYi-Vue: `https://github.com/yangzongzhuan/RuoYi-Vue`
- AKShare: `https://github.com/akfamily/akshare`
- AKShare 官网: `https://akshare.xyz`
- Backtrader: `https://github.com/mementum/backtrader`
- Pandas: `https://github.com/pandas-dev/pandas`

## 目录

```text
ruoyi-admin/            Java 后端入口（推荐）
ruoyi-ui/               RuoYi 前端（推荐）
ruoyi-quartz/           RuoYi 定时任务模块（推荐）
ruoyi-framework/        RuoYi 框架核心（主链依赖）
ruoyi-system/           RuoYi 系统能力（主链依赖）
ruoyi-common/           RuoYi 通用基础（主链依赖）
python/src/quant_road/  Python 量化层
sql/                    PostgreSQL 初始化脚本
admin/                  旧版独立后台（保留，非推荐）
ruoyi-generator/        旧代码生成模块（保留源码，已移出主链构建）
docs/
```

## 当前主链

当前推荐且持续维护的工程主链只有一条：

1. `ruoyi-admin`
2. `ruoyi-ui`
3. `ruoyi-quartz`
4. `python/src/quant_road`

补充说明：

- 默认启动后端请使用 `mvn -pl ruoyi-admin -am spring-boot:run`
- `admin/` 为历史兼容后端，默认端口已调整为 `18080`
- `ruoyi-generator/` 仅保留源码参考，已从根 `pom.xml` 和 `ruoyi-admin` 依赖中移出
- 当前 UI 默认隐藏 `monitor/*` 与 `tool/*` 平台菜单，聚焦量化运营主链

## Python 安装（req 2.4，一键复制）

```bash
pip install --upgrade pip
pip install akshare backtrader pandas psycopg2-binary
```

也可使用仓库依赖文件：

```bash
cd python
pip install -r requirements.txt
pip install -e .
```

## Python 启动

1. 复制环境变量模板

```bash
cd python
copy .env.example .env
```

2. 初始化数据库

```bash
python -m quant_road init-db
```

升级已有环境到 Plan-03 时，也执行一次该命令以补齐新增表与列（幂等）。

3. 执行盘后全流程

```bash
python -m quant_road full-daily --start-date 20230101 --strategy-id 1 --notify
```

默认行为：

- `sync-daily` / `run-strategy` 未传 `--symbols` 时，优先使用 `stock_basic` 中全量非 ST 股票。
- 可通过 `TARGET_STOCKS` 或 `--symbols` 限定股票池。
- `full-daily` 支持批次化执行与失败恢复：`--resume-batch-id <id>`。
- `full-daily` 默认新增 T+1 执行反馈步骤；可用 `--use-portfolio` 启用多策略资金分配执行。

执行回写（成交闭环）：

```bash
python -m quant_road record-execution --stock-code 000001 --side BUY --quantity 100 --price 10.23 --trade-date 2026-05-04 --strategy-id 1 --commission 1 --slippage 0.2
python -m quant_road import-executions --file executions.csv --strategy-id 1
python -m quant_road sync-valuation --index-codes 000300,000905
python -m quant_road evaluate-market --hold-days 2
python -m quant_road run-portfolio --start-date 2023-01-01 --total-capital 100000
python -m quant_road evaluate-execution-feedback --grace-days 1
python -m quant_road canary-evaluate --baseline-strategy-id 1 --candidate-strategy-id 2 --months 6
python -m quant_road monthly-report --months 6
python -m quant_road strategy-capabilities --format json
python -m quant_road shadow-compare --baseline-strategy-id 1 --candidate-strategy-id 2 --months 6 --format json
```

## RuoYi 后端启动（推荐）

```bash
mvn -pl ruoyi-admin -am spring-boot:run
```

不要把 `admin/` 当作默认启动入口。它只用于历史兼容排查，不参与当前主链开发与回归。

默认配置文件：

- [ruoyi-admin/src/main/resources/application.yml](/d:/hundsun-workspaces/itellij-space/git-workspace/quant-road/ruoyi-admin/src/main/resources/application.yml)
- [ruoyi-admin/src/main/resources/application-druid.yml](/d:/hundsun-workspaces/itellij-space/git-workspace/quant-road/ruoyi-admin/src/main/resources/application-druid.yml)

## 自动化验证入口

日常只验证量化模块，直接在仓库根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-quant.ps1
```

需要全站 UI 烟测时执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-all.ps1
```

如果要看浏览器可视过程，可追加 `-Headed`。

前端开发服务默认地址：

- `http://localhost:8081`
- 如需覆盖端口，可设置环境变量 `RUOYI_UI_PORT`

## RuoYi 任务接入方式（避免自研调度）

已提供任务 Bean：`quantRoadTask`，可直接在若依“定时任务管理”里配置 `invokeTarget`：

- `quantRoadTask.fullDaily()`
- `quantRoadTask.fullDaily(1L)`
- `quantRoadTask.fullDailyWithParams(1L,'20230101','2023-01-01',true)`
- `quantRoadTask.fullDailyWithPortfolio(1L,'20230101','2023-01-01',true,100000D)`
- `quantRoadTask.syncBasic()`
- `quantRoadTask.syncDaily('20230101')`
- `quantRoadTask.syncValuation()`
- `quantRoadTask.syncValuation('000300,000905','2026-05-04')`
- `quantRoadTask.evaluateMarket()`
- `quantRoadTask.evaluateMarket(2)`
- `quantRoadTask.runStrategy(1L,'2023-01-01')`
- `quantRoadTask.runPortfolio('2023-01-01',100000D)`
- `quantRoadTask.evaluateRisk(1L)`
- `quantRoadTask.evaluateExecutionFeedback(1)`
- `quantRoadTask.notifySignals()`
- `quantRoadTask.monthlyReport()`
- `quantRoadTask.monthlyReport(6)`
- `quantRoadTask.shadowCompare(2L)`
- `quantRoadTask.shadowCompare(1L,2L,6)`
- `quantRoadTask.canaryEvaluate(1L,2L,6)`

## RuoYi Quant 接口

- `POST /quant/jobs/fullDaily`
- `POST /quant/jobs/syncBasic`
- `POST /quant/jobs/syncDaily?startDate=20230101`
- `POST /quant/jobs/syncValuation?indexCodes=000300,000905&updateDate=2026-05-04`
- `POST /quant/jobs/evaluateMarket?holdDays=2`
- `POST /quant/jobs/runStrategy`
- `POST /quant/jobs/runPortfolio`
- `POST /quant/jobs/evaluateRisk`
- `POST /quant/jobs/evaluateExecutionFeedback?asOfDate=2026-05-04&graceDays=1`
- `POST /quant/jobs/notifySignals`
- `POST /quant/jobs/recordExecution`
- `POST /quant/jobs/importExecutions?file=...`
- `POST /quant/jobs/monthlyReport?months=6`
- `POST /quant/jobs/shadowCompare?baselineStrategyId=1&candidateStrategyId=2&months=6`
- `POST /quant/jobs/canaryEvaluate?baselineStrategyId=1&candidateStrategyId=2&months=6`
- `GET /quant/dashboard/summary`
- `GET /quant/data/signals?signalDate=2026-05-03`
- `GET /quant/data/positions`
- `GET /quant/data/strategyLogs?limit=20`
- `GET /quant/data/strategies`
- `GET /quant/data/marketStatus`
- `GET /quant/data/indexValuations?limit=20`
- `GET /quant/data/strategySwitchAudits?limit=20`
- `GET /quant/data/executionFeedbackSummary`
- `GET /quant/data/executionFeedbackDetails?limit=20`
- `GET /quant/data/canaryLatest`
- `GET /quant/data/strategyCapabilities`
- `GET /quant/data/shadowCompare?baselineStrategyId=1&candidateStrategyId=2&months=6`

## RuoYi 菜单与权限初始化

执行一次（PostgreSQL）：

```bash
psql -h localhost -U postgres -d db-quant -f sql/ruoyi_quant_menu.sql
```

该脚本会初始化：

- 菜单：`量化分析`、`量化看板`、`影子对比`
- 权限：`quant:data:query`、`quant:job:run`
- 默认绑定到 `admin` 角色（`role_key=admin`）

## RuoYi 定时任务初始化

执行一次（PostgreSQL）：

```bash
psql -h localhost -U postgres -d db-quant -f sql/ruoyi_quant_jobs.sql
```

该脚本会初始化并更新以下任务：

- `Quant Full Daily`（默认启用）
- `Quant Monthly Report`（默认启用）
- `Quant Shadow Compare`（默认启用）
- `Quant Sync Valuation`（默认暂停）
- `Quant Evaluate Market`（默认暂停）
- `Quant Execution Feedback`（默认暂停）
- `Quant Canary Evaluate`（默认暂停）

## RuoYi 前端页面

- 路径：
  - `/quant/dashboard`
  - `/quant/shadow`
- 侧边栏：来自若依菜单配置（执行 `sql/ruoyi_quant_menu.sql` 后可见）
- 页面能力：
  - 量化看板：汇总指标、市场状态、估值分位、策略切换审计、T+1执行反馈、canary结论、当日信号、持仓、策略日志
  - 选择基线/候选策略并查询月度影子对比
  - 一键触发后端生成 shadow compare 报告
  - 查看策略能力清单（`strategy_type`、必填参数、可选参数）

## 说明

- Quant 接口权限按最佳实践拆分：
  - 数据查询：`quant:data:query`
  - 任务触发：`quant:job:run`
- 已按 `req.md` 补齐风控与失效判定：买入后仓位校验、月度失效规则、连续亏损规则。
- 通知支持 `NOTIFY_TYPE=dingding|wechat`。
- 已补齐“信号 -> 成交 -> 持仓”闭环：新增 `execution_record`，执行后自动更新 `position` 并回写 `trade_signal.is_execute=1`。
- 已补齐批次状态追踪：`job_run_batch` / `job_run_step`，支持 full-daily 失败步骤重试。
- 已补齐自适应市场数据链路：`market_status`（风格状态）/ `index_valuation`（估值快照），并接入 `full-daily`。
- `sync-valuation` 支持多源回退：优先 `index_value_name_funddb`，不可用时回退到 `stock_index_pe_lg/stock_index_pb_lg`（至少覆盖沪深300）。
- `evaluate-market` 在上游短时不可用时会回退到最近一次已落库的市场状态，避免主流程被单点数据波动阻断（无历史状态时会显式失败）。
- 已补齐多策略资金分配：`run-portfolio` 支持按 `allocator_base_weight` + `regime_budget_weights` 做市场状态自适应分配。
- 已补齐策略切换审计：`strategy_switch_audit` 记录策略在不同市场状态下的准入切换（谁、何时、为何）。
- 已补齐 T+1 执行反馈闭环：`signal_execution_feedback` 自动汇总已执行/漏执行/待执行。
- 已补齐 canary 并行评估：`canary_run_log` 持久化基线/候选策略对比结论与推荐动作。
- 回测已纳入交易成本参数：`COMMISSION_RATE`、`SLIPPAGE_RATE`、`STAMP_DUTY_RATE`。
- 策略执行已支持按 `strategy_config.strategy_type` 路由（`MA` / `MA20_CROSS` / `MA_DUAL` / `MA_DUAL_CROSS`）。
- 策略参数支持按市场状态准入：`enabled_regimes`（未配置时默认全市场可运行）。
- 策略失效阈值支持按策略独立配置（如 `monthly_max_drawdown_limit_pct`、`invalid_trigger_count` 等）。
- 旧 `admin/` 模块保留用于历史兼容，后续优先走 RuoYi-Vue 链路。
- `ruoyi-generator/` 模块保留用于历史参考，不再视为当前工程默认能力。

