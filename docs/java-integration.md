# Java 集成说明（RuoYi-Vue 优先）

按 `req.md` 约束，Java 管控层优先使用 RuoYi-Vue，目录如下：

- 后端入口：`ruoyi-admin`
- 定时任务：`ruoyi-quartz`
- 前端：`ruoyi-ui`

## 技术栈

- RuoYi-Vue 3.9.2
- Spring Boot + Quartz（RuoYi 内置）
- PostgreSQL
- Python 子进程调用（`python -m quant_road`）

## 默认配置

配置文件：

- [ruoyi-admin/src/main/resources/application.yml](/d:/hundsun-workspaces/itellij-space/git-workspace/quant-road/ruoyi-admin/src/main/resources/application.yml)
- [ruoyi-admin/src/main/resources/application-druid.yml](/d:/hundsun-workspaces/itellij-space/git-workspace/quant-road/ruoyi-admin/src/main/resources/application-druid.yml)

关键配置：

- 数据库：`localhost:5432/db-quant`
- 用户名：`postgres`
- 密码：`123456`
- Python 工作目录：`../python`
- Python 模块：`quant_road`
- `defaultStartDate` / `strategyBacktestStartDate` 留空时，系统会自动使用“滚动 5 年”默认窗口

## 启动方式

在仓库根目录执行：

```bash
mvn -pl ruoyi-admin -am spring-boot:run
```

注意：

1. `ruoyi-admin` 是当前唯一推荐的 Java 主链入口。
2. `admin/` 为历史兼容后端，默认端口为 `18080`，不建议作为日常启动方式。
3. `ruoyi-generator/` 已移出主链构建，不再作为当前产品能力暴露。

## 初始化与启动（一次性）

### 适用于全新数据库的完整初始化

执行：

```bash
python scripts/init-ruoyi-fresh.py --host localhost --port 5432 --user postgres --password 123456 --database db-quant
```

这个脚本会按顺序执行：

1. `sql/ruoyi_pg_init.sql`
2. `sql/init.sql`
3. `sql/ruoyi_quant_menu.sql`
4. `sql/ruoyi_quant_jobs.sql`

说明：

1. `sql/ruoyi_pg_init.sql` 是现在仓库内正式维护的若依 PostgreSQL 基础初始化脚本，包含用户、部门、角色、菜单、字典、参数、通知、定时任务、Quartz 表等默认数据。
2. 该脚本包含 `drop table if exists`，适用于“全新初始化 / 重建库”，不适合直接对现有业务库反复执行。
3. 基础初始化里已将 `系统监控 / 系统工具 / 若依官网` 设为默认隐藏。
4. 前端常量首页不是数据库菜单，因此通过路由层默认隐藏，并将登录后的默认落点切到 `/quant/dashboard`。

### 适用于已有数据库的增量菜单/任务初始化

执行：

```bash
python scripts/apply-quant-bootstrap.py --host localhost --port 5432 --user postgres --password 123456 --database db-quant
```

脚本内容：

-- 仅对已有库增量执行：`sql/ruoyi_quant_menu.sql` 与 `sql/ruoyi_quant_jobs.sql`
-- 新增菜单：`量化分析 -> 量化看板 / 影子对比 / 调度中心 / 运维中心 / 执行回写 / 标的跟踪 / 回测分析`
-- 新增权限：`quant:data:query`、`quant:job:run`
-- 绑定到管理员角色：`role_key=admin`

说明：

1. 当前 `sql/ruoyi_quant_menu.sql` 仍以 `量化分析` 为顶级目录。
2. `复盘分析` 尚不在当前菜单初始化脚本内。
3. 若后续落地 `docs/quant-system/13-量化模块页面改版实施方案（研发版）.md`，目标结构会调整为：
   - `量化运营 -> 量化看板 / 任务中心 / 执行回写`
   - `策略治理 -> 复盘分析 / 影子对比`

### 初始化后启动系统

在仓库根目录执行：

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/restart-all-services.ps1
```

默认访问入口：

1. 前端：`http://localhost:8081`
2. 后端：`http://localhost:8080`

## 定时任务初始化（一次性）

执行：

```bash
psql -h localhost -U postgres -d db-quant -f sql/ruoyi_quant_jobs.sql
```

脚本会按 `(job_name, job_group)` 幂等写入任务：

1. `Quant Full Daily`
2. `Quant Monthly Report`
3. `Quant Shadow Compare`
4. `Quant Sync Valuation`（默认暂停）
5. `Quant Evaluate Market`（默认暂停）
6. `Quant Execution Feedback`（默认暂停）
7. `Quant Canary Evaluate`（默认暂停）

## 已接入 Quant 能力

### 1. 若依定时任务 invokeTarget

已提供 Bean：`quantRoadTask`

- `quantRoadTask.fullDaily()`
- `quantRoadTask.fullDaily(1L)`
- `quantRoadTask.fullDailyWithParams(1L,'20210101','2021-01-01',true)` 仅在需要显式覆盖默认窗口时使用
- `quantRoadTask.fullDailyWithPortfolio(1L,'20210101','2021-01-01',true,100000D)` 仅在需要显式覆盖默认窗口时使用
- `quantRoadTask.syncBasic()`
- `quantRoadTask.syncDaily('20210101')`
- `quantRoadTask.syncValuation()`
- `quantRoadTask.syncValuation('000300,000905','2026-05-04')`
- `quantRoadTask.evaluateMarket()`
- `quantRoadTask.evaluateMarket(2)`
- `quantRoadTask.runStrategy(1L,'2021-01-01')`
- `quantRoadTask.runPortfolio('2021-01-01',100000D)`
- `quantRoadTask.evaluateRisk(1L)`
- `quantRoadTask.evaluateExecutionFeedback(1)`
- `quantRoadTask.notifySignals()`
- `quantRoadTask.monthlyReport()`
- `quantRoadTask.monthlyReport(6)`
- `quantRoadTask.shadowCompare(2L)`
- `quantRoadTask.shadowCompare(1L,2L,6)`
- `quantRoadTask.canaryEvaluate(1L,2L,6)`

你可以直接在若依“系统监控 -> 定时任务”配置这些 `invokeTarget`，无需额外自研调度器。

推荐做法：

- 页面触发或定时任务触发时，优先不传开始日期，直接使用系统默认的滚动 5 年窗口。
- 只有在补历史数据、复盘特定行情或对齐专项分析时，再显式传入具体日期。

### 2. Quant REST 接口

- `POST /quant/jobs/fullDaily`
- `POST /quant/jobs/syncBasic`
- `POST /quant/jobs/syncDaily`（不传 `startDate` 时默认滚动 5 年）
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

### 3. RuoYi UI 页面

- 页面路由：
  - `/quant/dashboard`
  - `/quant/shadow`
  - `/quant/jobs`
  - `/quant/execution`
- 当前页面位置：`量化分析 -> 量化看板 / 影子对比 / 任务中心 / 执行回写`
- 若后续落地 `docs/quant-system/13-量化模块页面改版实施方案（研发版）.md`，目标位置会调整为：
  - `量化运营 -> 量化看板 / 任务中心 / 执行回写`
  - `策略治理 -> 复盘分析 / 影子对比`
- 页面数据来源：
  - `GET /quant/dashboard/summary`
  - `GET /quant/data/signals`
  - `GET /quant/data/positions`
  - `GET /quant/data/strategyLogs`
  - `GET /quant/data/marketStatus`
  - `GET /quant/data/indexValuations`
  - `GET /quant/data/strategySwitchAudits`
  - `GET /quant/data/executionFeedbackSummary`
  - `GET /quant/data/executionFeedbackDetails`
  - `GET /quant/data/canaryLatest`
  - `GET /quant/data/strategies`
  - `GET /quant/data/strategyCapabilities`
  - `GET /quant/data/shadowCompare`
  - `POST /quant/jobs/shadowCompare`

请求体示例（可选）：

```json
{
  "strategyId": 1,
  "notify": true,
  "usePortfolio": false,
  "portfolioTotalCapital": 100000,
  "actor": "manual-admin"
}
```

如需显式覆盖默认窗口，可改为：

```json
{
  "strategyId": 1,
  "startDate": "20210101",
  "strategyBacktestStartDate": "2021-01-01",
  "notify": true,
  "usePortfolio": false,
  "portfolioTotalCapital": 100000,
  "actor": "manual-admin"
}
```

执行回写请求体示例：

```json
{
  "stockCode": "000001",
  "side": "BUY",
  "quantity": 100,
  "price": 10.23,
  "tradeDate": "2026-05-04",
  "strategyId": 1,
  "commission": 1.0,
  "tax": 0.0,
  "slippage": 0.2,
  "externalOrderId": "ORDER-20260504-001"
}
```

## 说明

- Quant 接口权限：
  - `quant:data:query`：看板与数据查询
  - `quant:job:run`：任务触发与执行回写
- `admin/` 目录下的独立后台为历史兼容代码，不作为当前推荐主链路。
- `ruoyi-generator/` 目录保留源码参考，但已从当前主链构建与 UI 入口中移除。
- 当前主链路是 RuoYi-Vue + Python 量化层，符合 `req.md` 的“先框架、后定制”原则。
- Python 策略执行按 `strategy_config.strategy_type` 路由（当前支持 `MA` / `MA20_CROSS` / `MA_DUAL` / `MA_DUAL_CROSS`）。
- 对外使用路径以页面触发和若依定时任务触发为主，CLI 仅作为内部执行承载层。

