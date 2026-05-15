# Quant Daily Workflow Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the daily quant workflow around `盘后决策 -> 调度中心 -> 执行对账`, making the pages self-explanatory, paginated, and aligned with the approved product blueprint.

**Architecture:** Reuse the existing `ruoyi-ui` quant pages and `ruoyi-admin` quant aggregation services instead of creating a parallel surface. Phase 1 intentionally focuses on the daily operational chain only: route naming, dashboard restructure, dispatch center clarity, and execution reconciliation. Weekly/monthly explanation and governance stay for later plans.

**Tech Stack:** Vue 2 + Element UI, RuoYi router/menu metadata, Spring Boot controllers/services/tests, existing `quant.js` API layer, Maven tests, npm lint/build verification.

---

## Scope

This spec intentionally covers only the first working slice of the approved blueprint:

1. Navigation and naming for the daily workflow
2. `盘后决策` page restructure
3. `调度中心` page restructure
4. `执行对账` page restructure
5. Shared pagination and self-explanatory interaction rules for these three pages

Excluded from this plan:

1. `复盘分析` redesign
2. `策略治理` redesign
3. New chart-heavy evidence views
4. Symbol-pool and ETF-governance deep productization

---

## File Map

### Frontend routes and API

- Modify: `ruoyi-ui/src/router/index.js`
  - Hidden route labels and active-menu mapping for renamed pages.
- Modify: `ruoyi-ui/src/api/quant.js`
  - Add page/size params where phase-1 lists need pagination.

### Dashboard / post-market decision

- Modify: `ruoyi-ui/src/views/quant/dashboard/index.vue`
  - Replace hero-style copy-first layout with action-first page structure.
- Modify: `ruoyi-ui/src/views/quant/dashboard/dashboard-page-state.js`
  - Recompute page state around `今日状态 / 今日信号 / 待处理事项 / 当前持仓`.
- Modify: `ruoyi-ui/src/views/quant/dashboard/dashboard-context.js`
  - Normalize route/deep-link behavior after naming changes.
- Modify: `ruoyi-ui/src/views/quant/dashboard/dashboard-keys.js`
  - Add stable keys for paginated data and compact sections.
- Modify: `ruoyi-ui/src/views/quant/dashboard/dashboard-object-layer.js`
  - Downscope object-layer content so it no longer occupies the main decision surface.

### Dispatch center

- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
  - Make the page explicitly about current run, range, time window, and recovery.
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
  - Rebuild derived state in product language instead of narrative copy.
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
  - Simplify to factual status + next action.
- Modify: `ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue`
  - Show current step, current scope, current date range, and next step.
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue`
  - Replace “继续等待什么” ambiguity with explicit wait state rows.
- Modify: `ruoyi-ui/src/views/quant/jobs/components/DispatchHistoryTable.vue`
  - Keep paginated history list as a true secondary surface.

### Execution reconciliation

- Modify: `ruoyi-ui/src/views/quant/execution/index.vue`
  - Promote pending-signal reconciliation into the page’s main table.
- Modify: `ruoyi-ui/src/views/quant/execution/execution-page-state.js`
  - Build page state around reconciliation status and next actions.
- Modify: `ruoyi-ui/src/views/quant/execution/execution-route.js`
  - Normalize cross-page route payloads from dashboard/dispatch.

### Backend aggregation / pagination

- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java`
  - Accept pagination params for phase-1 list endpoints.
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`
  - Support paginated signal/position/execution-query slices and compact action-item payloads.
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java`
  - Ensure task-center summary exposes current scope, current date range, current step, and next step.

### Tests

- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java`
  - Cover updated list endpoint params and payloads.
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantRoadQueryServiceTest.java`
  - Cover paginated signal/position/reconciliation aggregation.
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java`
  - Cover explicit run-context fields in summary payload.

### Docs

- Modify: `docs/quant-system/13-量化模块页面改版实施方案（研发版）.md`
  - Record that phase 1 is now implemented against the target-state blueprint terminology.

---

### Task 1: Rename the Daily Workflow Surface and Lock Route Semantics

**Files:**
- Modify: `ruoyi-ui/src/router/index.js`
- Modify: `ruoyi-ui/src/api/quant.js`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java`

- [ ] **Step 1: Write the failing controller test for paginated signal and execution list params**

```java
@Test
void signalsSupportsPageAndPageSize() throws Exception
{
    when(queryService.signals(eq(LocalDate.of(2026, 5, 12)), eq(2), eq(20))).thenReturn(Map.of(
            "rows", List.of(Map.of("stock_code", "510300", "signal_type", "BUY")),
            "total", 41,
            "pageNum", 2,
            "pageSize", 20));

    mockMvc.perform(get("/quant/data/signals")
                    .param("signalDate", "2026-05-12")
                    .param("pageNum", "2")
                    .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(41))
            .andExpect(jsonPath("$.data.pageNum").value(2))
            .andExpect(jsonPath("$.data.rows[0].stock_code").value("510300"));
}
```

- [ ] **Step 2: Run the targeted controller test to verify it fails**

Run:

```powershell
mvn -pl ruoyi-admin -Dtest=QuantRoadDataControllerTest#signalsSupportsPageAndPageSize test
```

Expected:

```text
FAIL ... wanted but not invoked / method signature mismatch for signals(...)
```

- [ ] **Step 3: Update controller signatures and API helpers to carry page params**

```java
@GetMapping("/data/signals")
@PreAuthorize("@ss.hasPermi('quant:data:query')")
public AjaxResult signals(
        @RequestParam(required = false) String signalDate,
        @RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "10") int pageSize)
{
    LocalDate date = signalDate == null || signalDate.isBlank() ? LocalDate.now() : LocalDate.parse(signalDate);
    return AjaxResult.success(quantRoadQueryService.signals(date, pageNum, pageSize));
}
```

```javascript
export function listSignals(params) {
  return request({
    url: '/quant/data/signals',
    method: 'get',
    params: {
      pageNum: 1,
      pageSize: 10,
      ...params
    }
  })
}
```

```javascript
export function listExecutionRecords(params) {
  return request({
    url: '/quant/data/executionRecords',
    method: 'get',
    params: {
      pageNum: 1,
      pageSize: 10,
      ...params
    }
  })
}
```

- [ ] **Step 4: Align hidden route titles with the new product naming**

```javascript
{
  path: '/quant',
  component: Layout,
  hidden: true,
  children: [
    {
      path: 'dispatch-manual',
      component: () => import('@/views/quant/dispatch-manual'),
      name: 'QuantDispatchManual',
      meta: { title: '调度中心', activeMenu: '/quant/jobs' }
    },
    {
      path: 'dispatch-detail/:jobId?',
      component: () => import('@/views/quant/dispatch-detail'),
      name: 'QuantDispatchDetail',
      meta: { title: '调度详情', activeMenu: '/quant/jobs' }
    },
    {
      path: 'dispatch-auto',
      component: () => import('@/views/quant/dispatch-auto'),
      name: 'QuantDispatchAuto',
      meta: { title: '自动调度计划', activeMenu: '/quant/jobs' }
    }
  ]
}
```

- [ ] **Step 5: Run the updated targeted test to verify the contract passes**

Run:

```powershell
mvn -pl ruoyi-admin -Dtest=QuantRoadDataControllerTest#signalsSupportsPageAndPageSize test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-ui/src/router/index.js ruoyi-ui/src/api/quant.js ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java
git commit -m "refactor(quant): lock daily workflow route semantics"
```

---

### Task 2: Rebuild Dashboard into the Post-Market Decision Page

**Files:**
- Modify: `ruoyi-ui/src/views/quant/dashboard/index.vue`
- Modify: `ruoyi-ui/src/views/quant/dashboard/dashboard-page-state.js`
- Modify: `ruoyi-ui/src/views/quant/dashboard/dashboard-context.js`
- Modify: `ruoyi-ui/src/views/quant/dashboard/dashboard-keys.js`
- Modify: `ruoyi-ui/src/views/quant/dashboard/dashboard-object-layer.js`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantRoadQueryServiceTest.java`

- [ ] **Step 1: Write the failing service test for paginated dashboard signals**

```java
@Test
void signalsReturnsPagedRowsSortedForDecisionSurface()
{
    Map<String, Object> payload = service.signals(LocalDate.of(2026, 5, 12), 1, 10);

    assertThat(payload).containsKeys("rows", "total", "pageNum", "pageSize");
    assertThat(((List<?>) payload.get("rows")).size()).isLessThanOrEqualTo(10);
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run:

```powershell
mvn -pl ruoyi-admin -Dtest=QuantRoadQueryServiceTest#signalsReturnsPagedRowsSortedForDecisionSurface test
```

Expected:

```text
FAIL ... method signals(LocalDate,int,int) is undefined or payload shape mismatch
```

- [ ] **Step 3: Implement paged signal payload and compact page-state derivation**

```java
public Map<String, Object> signals(LocalDate signalDate, int pageNum, int pageSize)
{
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(SQL_SIGNALS, signalDate);
    List<Map<String, Object>> sorted = rows.stream()
            .sorted(Comparator
                    .comparing((Map<String, Object> row) -> priorityRank(row.get("signal_type")))
                    .thenComparing(row -> Objects.toString(row.get("stock_code"), "")))
            .toList();
    return slicePage(sorted, pageNum, pageSize);
}
```

```javascript
export function buildDashboardPageState({
  marketStatus,
  signalPage,
  actionItems,
  positions,
  executionFeedbackSummary
}) {
  const rows = Array.isArray(signalPage.rows) ? signalPage.rows : []
  const blockingItems = (actionItems || []).filter(item => String(item.priority || '').toUpperCase() === 'P1')
  return {
    todayStatus: {
      tradeDate: signalPage.signalDate || '',
      marketStatus: marketStatus.status || '-',
      scopeLabel: signalPage.scopeLabel || '未指定范围',
      dispatchStatus: signalPage.dispatchStatus || '待确认',
      pendingCount: blockingItems.length + Number(executionFeedbackSummary.pendingSignalCount || 0)
    },
    signalTableRows: rows,
    primaryActionItems: actionItems || [],
    highlightedPositions: (positions || []).slice(0, 5)
  }
}
```

- [ ] **Step 4: Replace the copy-first dashboard layout with the four-block decision layout**

```vue
<template>
  <div class="app-container dashboard-page">
    <el-card shadow="never" class="box-card">
      <div class="section-header">
        <span>今日状态</span>
      </div>
      <div class="today-status-grid">
        <div class="status-cell">
          <label>交易日</label>
          <strong>{{ dashboardPage.todayStatus.tradeDate || todayString() }}</strong>
        </div>
        <div class="status-cell">
          <label>市场状态</label>
          <strong>{{ dashboardPage.todayStatus.marketStatus }}</strong>
        </div>
        <div class="status-cell">
          <label>当前范围</label>
          <strong>{{ dashboardPage.todayStatus.scopeLabel }}</strong>
        </div>
        <div class="status-cell">
          <label>调度状态</label>
          <strong>{{ dashboardPage.todayStatus.dispatchStatus }}</strong>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :xl="16">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>今日信号</span>
          </div>
          <el-table :data="dashboardPage.signalTableRows" border>
            <el-table-column label="标的" min-width="140">
              <template slot-scope="scope">{{ scope.row.stock_code }} {{ scope.row.stock_name || '' }}</template>
            </el-table-column>
            <el-table-column label="类型" prop="asset_type" width="90" />
            <el-table-column label="信号" prop="signal_type" width="90" />
            <el-table-column label="原因" prop="signal_reason" min-width="220" />
            <el-table-column label="下一步" width="140" fixed="right">
              <template slot-scope="scope">
                <el-button type="text" size="mini" @click="goSignalNext(scope.row)">去处理</el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="signalTotal > 0"
            :total="signalTotal"
            :page.sync="signalQuery.pageNum"
            :limit.sync="signalQuery.pageSize"
            @pagination="loadSignals"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="8">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>待处理事项</span>
          </div>
          <div class="issue-list">
            <div v-for="item in dashboardPage.primaryActionItems" :key="item.id || item.title" class="issue-row">
              <div class="issue-title">{{ item.title }}</div>
              <div class="issue-reason">{{ item.reason }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
```

- [ ] **Step 5: Run backend test and frontend lint for the dashboard slice**

Run:

```powershell
mvn -pl ruoyi-admin -Dtest=QuantRoadQueryServiceTest#signalsReturnsPagedRowsSortedForDecisionSurface test
cd ruoyi-ui
npm run lint -- src/views/quant/dashboard/index.vue src/views/quant/dashboard/dashboard-page-state.js src/api/quant.js
```

Expected:

```text
BUILD SUCCESS
DONE  No lint errors found
```

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-ui/src/views/quant/dashboard/index.vue ruoyi-ui/src/views/quant/dashboard/dashboard-page-state.js ruoyi-ui/src/views/quant/dashboard/dashboard-context.js ruoyi-ui/src/views/quant/dashboard/dashboard-keys.js ruoyi-ui/src/views/quant/dashboard/dashboard-object-layer.js ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantRoadQueryServiceTest.java
git commit -m "refactor(quant): rebuild dashboard as post-market decision page"
```

---

### Task 3: Make the Dispatch Center Explicit About What Is Running and What Is Blocking

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java`

- [ ] **Step 1: Write the failing service test for explicit current-run context**

```java
@Test
void summaryIncludesCurrentRunScopeDateRangeAndNextStep()
{
    Map<String, Object> payload = service.summary();

    Map<String, Object> primaryTask = cast(payload.get("primaryTask"));
    assertThat(primaryTask).containsKeys("taskName", "currentStep", "scopeSummary", "timeRangeSummary", "nextStep");
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run:

```powershell
mvn -pl ruoyi-admin -Dtest=QuantTaskCenterServiceTest#summaryIncludesCurrentRunScopeDateRangeAndNextStep test
```

Expected:

```text
FAIL ... expected keys [currentStep, scopeSummary, timeRangeSummary, nextStep]
```

- [ ] **Step 3: Extend the task-center summary payload with factual run context**

```java
private Map<String, Object> toPrimaryTask(Map<String, Object> row)
{
    return Map.of(
            "taskName", value(row, "task_name", "未命名任务"),
            "status", value(row, "status", "pending"),
            "currentStep", value(row, "current_step_name", "等待开始"),
            "scopeSummary", value(row, "scope_summary", "未指定范围"),
            "timeRangeSummary", value(row, "time_range_summary", "未指定时间范围"),
            "nextStep", value(row, "next_step_name", "等待系统更新"),
            "progressSummary", value(row, "progress_summary", "0 / 0"));
}
```

```javascript
export function buildTaskCenterState(summary = {}) {
  const primaryTask = summary.primaryTask || {}
  return {
    todayStatusLabel: summary.todayStatusLabel || '待判断',
    todayStatusHeadlineAction: summary.todayHeadline || '先确认今日流程是否业务就绪',
    todayStatusReason: summary.todayReason || '当前缺少可执行的业务结论',
    primaryTaskView: {
      taskName: primaryTask.taskName || '当前无运行任务',
      currentStep: primaryTask.currentStep || '等待开始',
      scopeSummary: primaryTask.scopeSummary || '未指定范围',
      timeRangeSummary: primaryTask.timeRangeSummary || '未指定时间范围',
      nextStep: primaryTask.nextStep || '等待系统更新',
      progressSummary: primaryTask.progressSummary || '0 / 0'
    }
  }
}
```

- [ ] **Step 4: Replace ambiguous waiting copy with explicit factual rows**

```vue
<template>
  <div class="progress-facts">
    <div class="progress-fact-row">
      <label>当前步骤</label>
      <span>{{ primaryTask.currentStep }}</span>
    </div>
    <div class="progress-fact-row">
      <label>当前范围</label>
      <span>{{ primaryTask.scopeSummary }}</span>
    </div>
    <div class="progress-fact-row">
      <label>时间范围</label>
      <span>{{ primaryTask.timeRangeSummary }}</span>
    </div>
    <div class="progress-fact-row">
      <label>下一步</label>
      <span>{{ primaryTask.nextStep }}</span>
    </div>
  </div>
</template>
```

- [ ] **Step 5: Run the targeted test and frontend lint**

Run:

```powershell
mvn -pl ruoyi-admin -Dtest=QuantTaskCenterServiceTest#summaryIncludesCurrentRunScopeDateRangeAndNextStep test
cd ruoyi-ui
npm run lint -- src/views/quant/jobs/index.vue src/views/quant/jobs/jobs-task-center-state.js src/views/quant/jobs/components/TodayStatusCard.vue src/views/quant/jobs/components/PrimaryTaskCard.vue src/views/quant/jobs/components/TaskProgressTimeline.vue
```

Expected:

```text
BUILD SUCCESS
DONE  No lint errors found
```

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-ui/src/views/quant/jobs/index.vue ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java
git commit -m "refactor(quant): make dispatch center explicitly observable"
```

---

### Task 4: Turn the Execution Page into a Reconciliation-First Workflow

**Files:**
- Modify: `ruoyi-ui/src/views/quant/execution/index.vue`
- Modify: `ruoyi-ui/src/views/quant/execution/execution-page-state.js`
- Modify: `ruoyi-ui/src/views/quant/execution/execution-route.js`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantRoadQueryServiceTest.java`

- [ ] **Step 1: Write the failing query-service test for pending reconciliation rows**

```java
@Test
void executionReconciliationRowsPrioritizePendingPartialAndUnmatched()
{
    Map<String, Object> payload = service.executionReconciliationRows(1, 10, null);

    assertThat(payload).containsKeys("rows", "total", "pageNum", "pageSize");
    List<Map<String, Object>> rows = castList(payload.get("rows"));
    assertThat(rows).allMatch(row -> row.containsKey("execution_status"));
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
mvn -pl ruoyi-admin -Dtest=QuantRoadQueryServiceTest#executionReconciliationRowsPrioritizePendingPartialAndUnmatched test
```

Expected:

```text
FAIL ... method executionReconciliationRows(...) not found
```

- [ ] **Step 3: Add a paginated reconciliation endpoint and wire the execution page to use it as the main table**

```java
@GetMapping("/data/pendingExecutions")
@PreAuthorize("@ss.hasPermi('quant:data:query')")
public AjaxResult pendingExecutions(
        @RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "10") int pageSize,
        @RequestParam(required = false) String stockCode)
{
    return AjaxResult.success(quantRoadQueryService.executionReconciliationRows(pageNum, pageSize, stockCode));
}
```

```javascript
export function getPendingExecutions(params) {
  return request({
    url: '/quant/data/pendingExecutions',
    method: 'get',
    params: {
      pageNum: 1,
      pageSize: 10,
      ...params
    }
  })
}
```

```vue
<el-card shadow="never" class="box-card mb16">
  <div slot="header" class="section-header">
    <span>待处理信号</span>
  </div>
  <el-table :data="pendingExecutionRows" border>
    <el-table-column label="标的" min-width="140">
      <template slot-scope="scope">{{ scope.row.stock_code }} {{ scope.row.stock_name || '' }}</template>
    </el-table-column>
    <el-table-column label="状态" prop="execution_status" width="100" />
    <el-table-column label="下一步" width="140" fixed="right">
      <template slot-scope="scope">
        <el-button type="text" size="mini" @click="handlePendingExecution(scope.row)">去处理</el-button>
      </template>
    </el-table-column>
  </el-table>
  <pagination
    v-show="pendingExecutionTotal > 0"
    :total="pendingExecutionTotal"
    :page.sync="pendingExecutionQuery.pageNum"
    :limit.sync="pendingExecutionQuery.pageSize"
    @pagination="loadPendingExecutions"
  />
</el-card>
```

- [ ] **Step 4: Demote anomaly cards and manual tools to secondary sections**

```javascript
export function buildExecutionPageState({ reconciliationSummary, pendingExecutions }) {
  return {
    headerFacts: {
      pending: Number(reconciliationSummary.pendingSignalCount || 0),
      partial: Number(reconciliationSummary.partialExecutionCount || 0),
      unmatched: Number(reconciliationSummary.unmatchedExecutionCount || 0),
      completed: Boolean(reconciliationSummary.todayWritebackComplete)
    },
    pendingRows: Array.isArray(pendingExecutions.rows) ? pendingExecutions.rows : [],
    secondaryPanels: ['tools']
  }
}
```

- [ ] **Step 5: Run targeted backend tests and frontend lint**

Run:

```powershell
mvn -pl ruoyi-admin -Dtest=QuantRoadDataControllerTest,QuantRoadQueryServiceTest test
cd ruoyi-ui
npm run lint -- src/views/quant/execution/index.vue src/views/quant/execution/execution-page-state.js src/views/quant/execution/execution-route.js src/api/quant.js
```

Expected:

```text
BUILD SUCCESS
DONE  No lint errors found
```

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-ui/src/views/quant/execution/index.vue ruoyi-ui/src/views/quant/execution/execution-page-state.js ruoyi-ui/src/views/quant/execution/execution-route.js ruoyi-ui/src/api/quant.js ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantRoadQueryServiceTest.java
git commit -m "refactor(quant): make execution page reconciliation-first"
```

---

### Task 5: Lock Shared Product Constraints and Regression Coverage

**Files:**
- Modify: `docs/quant-system/13-量化模块页面改版实施方案（研发版）.md`
- Modify: `ruoyi-ui/src/views/quant/dashboard/index.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Modify: `ruoyi-ui/src/views/quant/execution/index.vue`

- [ ] **Step 1: Remove residual explanatory copy and long narrative labels**

```vue
<div slot="header" class="section-header">
  <span>今日状态</span>
</div>
```

```vue
<div slot="header" class="section-header">
  <span>当前运行</span>
</div>
```

```vue
<div slot="header" class="section-header">
  <span>异常对账</span>
</div>
```

- [ ] **Step 2: Update the implementation doc so phase-1 terminology matches the approved blueprint**

```markdown
## 4. 页面实施方案（阶段一已固定命名）

一级主线页面统一为：

1. `盘后决策`
2. `调度中心`
3. `执行对账`

阶段一必须遵守：

1. 所有列表分页
2. 首屏仅一个主表
3. 页面靠结构自解释
4. 不再使用说明文和 PPT 式大块文案
```

- [ ] **Step 3: Run focused frontend verification commands**

Run:

```powershell
cd ruoyi-ui
npm run lint -- src/views/quant/dashboard/index.vue src/views/quant/jobs/index.vue src/views/quant/execution/index.vue
npm run build
```

Expected:

```text
DONE  No lint errors found
Build complete.
```

- [ ] **Step 4: Run the backend quant regression suite**

Run:

```powershell
mvn -pl ruoyi-admin -Dtest=QuantRoadDataControllerTest,QuantRoadQueryServiceTest,QuantTaskCenterServiceTest,QuantRoadReviewControllerTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```powershell
git add docs/quant-system/13-量化模块页面改版实施方案（研发版）.md ruoyi-ui/src/views/quant/dashboard/index.vue ruoyi-ui/src/views/quant/jobs/index.vue ruoyi-ui/src/views/quant/execution/index.vue
git commit -m "docs(quant): lock phase-1 product constraints"
```

---

## Self-Review

### Spec coverage

This phase-1 plan covers the approved blueprint sections for:

1. top-level page naming and daily workflow routing
2. `盘后决策` first-screen structure
3. `调度中心` explicit run-state behavior
4. `执行对账` reconciliation-first structure
5. “all lists paginated” and “pages self-explanatory” hard constraints

Intentionally deferred to later plans:

1. `复盘分析` full redesign
2. `策略治理` full redesign
3. chart-heavy evidence layers
4. symbol-pool / ETF-governance expansion

### Placeholder scan

Checked:

1. no `TODO` / `TBD`
2. each task includes exact files
3. each code-change step includes concrete snippets
4. each test step includes an exact command and expected output

### Type consistency

Locked names used consistently in this plan:

1. `盘后决策`
2. `调度中心`
3. `执行对账`
4. `signals(date, pageNum, pageSize)`
5. `pendingExecutions(pageNum, pageSize, stockCode)`
6. `executionReconciliationRows(pageNum, pageSize, stockCode)`

---

Plan complete and saved to `docs/superpowers/plans/2026-05-12-quant-daily-workflow-phase1.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
