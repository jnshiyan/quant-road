# Quant Dispatch Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the quant dispatch experience around a unified dispatch model with separate home, manual submit, detail, and auto-schedule pages, plus a corrected backend execution-detail contract that explains what is running and what is being waited on.

**Architecture:** Keep the existing `/quant/jobs` route as the dispatch-center home, extract the manual-submit form into a dedicated page, add a dedicated dispatch-detail page keyed by `jobId`, and add an auto-schedule page backed by explicit summary/history/detail contracts. Fix the backend summary/detail contract first so the frontend stops inferring execution state from overloaded fields such as `currentStage` and `waitingFor`.

**Tech Stack:** Vue 2 + Element UI, RuoYi frontend routing/menu patterns, Spring Boot controllers/services, Java service tests with JUnit/Mockito, Node-based frontend smoke tests.

---

## File Structure

### Existing files to modify

- `ruoyi-ui/src/views/quant/jobs/index.vue`
  Current mixed page. Reduce it to dispatch-center home responsibilities only.
- `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
  State adapter for `todayStatus`, `primaryTask`, and action routing. Must stop manufacturing ambiguous "continue waiting" behavior.
- `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
  Keep current approved status-card structure; update actions/labels only if route targets change.
- `ruoyi-ui/src/views/quant/jobs/components/CurrentDispatchMonitor.vue`
  Convert from loose monitoring widget to a home-page current-dispatch summary block or reuse parts for the new detail page.
- `ruoyi-ui/src/api/quant.js`
  Add explicit APIs for dispatch detail, structured live logs, and auto-dispatch list/detail if missing.
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java`
  Expose new data endpoints for dispatch detail and auto-dispatch views.
- `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java`
  Refocus summary payload for home page only; stop overloading `waitingFor` with `currentStage`.
- `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`
  Add/adjust dispatch detail queries, waiting-object semantics, shard/result/log aggregation, and auto-dispatch summary/history shaping.
- `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java`
  Update tests for new home-page summary contract.
- `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java`
  Add endpoint coverage for new dispatch-detail and auto-dispatch data contracts.
- `ruoyi-ui/tests/jobs-task-center-state.test.cjs`
  Update adapter expectations to remove ambiguous waiting text.
- `ruoyi-ui/tests/jobs-route.test.cjs`
  Extend route/page smoke coverage for the new dispatch pages.

### New frontend files to create

- `ruoyi-ui/src/views/quant/dispatch-manual/index.vue`
  Dedicated manual-dispatch page.
- `ruoyi-ui/src/views/quant/dispatch-detail/index.vue`
  Dedicated dispatch-detail page.
- `ruoyi-ui/src/views/quant/dispatch-auto/index.vue`
  Dedicated auto-dispatch page.
- `ruoyi-ui/src/views/quant/dispatch-shared/ManualDispatchSummaryCard.vue`
  Right-side submit summary card reused by the manual page.
- `ruoyi-ui/src/views/quant/dispatch-shared/DispatchOverviewCard.vue`
  Detail-page top overview block.
- `ruoyi-ui/src/views/quant/dispatch-shared/DispatchPhaseCard.vue`
  Detail-page stage/waiting/current-object block.
- `ruoyi-ui/src/views/quant/dispatch-shared/DispatchLogStream.vue`
  Structured live-log block.
- `ruoyi-ui/src/views/quant/dispatch-shared/DispatchShardTable.vue`
  Detail-page shard table with symbol preview and merge reason.
- `ruoyi-ui/src/views/quant/dispatch-shared/DispatchResultSummary.vue`
  Result/exception summary block.
- `ruoyi-ui/src/views/quant/dispatch-shared/AutoDispatchDefinitionTable.vue`
  Auto-dispatch definitions.
- `ruoyi-ui/src/views/quant/dispatch-shared/AutoDispatchHistoryTable.vue`
  Auto-dispatch history table.

### New backend files to create if needed

- `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantDispatchDetailService.java`
  Dedicated service for dispatch-detail shaping if `QuantTaskCenterService` would otherwise become overloaded.
- `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantDispatchDetailServiceTest.java`
  Dispatch-detail contract tests.

## Task 1: Lock The New Page Map And Route Targets

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`

- [ ] **Step 1: Write the failing frontend route smoke test**

```js
const assert = require('node:assert/strict')
const fs = require('node:fs')

const jobsSource = fs.readFileSync('ruoyi-ui/src/views/quant/jobs/index.vue', 'utf8')

assert.equal(jobsSource.includes("'/quant/dispatch-manual'"), true)
assert.equal(jobsSource.includes("'/quant/dispatch-auto'"), true)
assert.equal(jobsSource.includes("'/quant/dispatch-detail'"), true)
assert.equal(jobsSource.includes('发起手工调度'), true)
assert.equal(jobsSource.includes('进入自动调度'), true)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test ruoyi-ui/tests/jobs-route.test.cjs`
Expected: FAIL because the current page still routes everything through `/quant/jobs`.

- [ ] **Step 3: Update the jobs-page action targets and remove ambiguous "continue waiting" fallbacks**

```js
function resolvePrimaryAction(todayStatus = {}, nextAction = {}) {
  if (nextAction.code) {
    return normalizeAction(nextAction)
  }
  if (todayStatus.code === 'OPERABLE') {
    return normalizeAction({}, { code: 'GO_DASHBOARD', label: '进入量化看板', targetPage: '/quant/dashboard' })
  }
  if (todayStatus.code === 'BLOCKED') {
    return normalizeAction({}, { code: 'GO_OPERATIONS', label: '去运维中心处理', targetPage: '/quant/operations' })
  }
  return normalizeAction({}, { code: 'VIEW_CURRENT_DISPATCH', label: '查看当前调度', targetPage: '/quant/dispatch-detail' })
}
```

```vue
<el-button type="primary" @click="$router.push('/quant/dispatch-manual')">发起手工调度</el-button>
<el-button plain @click="$router.push('/quant/dispatch-auto')">进入自动调度</el-button>
```

- [ ] **Step 4: Run the route smoke test again**

Run: `node --test ruoyi-ui/tests/jobs-route.test.cjs`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ruoyi-ui/src/views/quant/jobs/index.vue ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js ruoyi-ui/tests/jobs-route.test.cjs
git commit -m "feat: split dispatch center navigation targets"
```

## Task 2: Refocus `/quant/jobs` Into A Home Page

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/CurrentDispatchMonitor.vue`
- Test: `ruoyi-ui/tests/jobs-task-center-state.test.cjs`

- [ ] **Step 1: Write the failing home-page adapter test**

```js
const { buildTaskCenterState } = require('../src/views/quant/jobs/jobs-task-center-state')
const state = buildTaskCenterState({
  primaryTask: {
    currentStage: '执行策略',
    currentSymbols: ['510300'],
    waitingTarget: ''
  }
})

assert.equal(state.waitingMessage, '当前无等待对象')
assert.equal(state.currentSymbolsSummary, '510300')
```

- [ ] **Step 2: Run the adapter test to verify it fails**

Run: `node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs`
Expected: FAIL because the current adapter only understands `waitingFor`.

- [ ] **Step 3: Trim the home page to summary-only sections**

```vue
<template>
  <div class="task-center-page">
    <today-status-card ... />
    <current-dispatch-home-card
      :primary-task="taskCenterSummary.primaryTask"
      @open-detail="openCurrentDispatch"
    />
    <next-auto-dispatch-card :summary="taskCenterSummary.nextScheduledDispatch" />
    <recent-dispatch-table :rows="dispatchHistoryRows" @view-detail="openDispatchDetail" />
  </div>
</template>
```

```js
return {
  waitingMessage: primaryTask.waitingTarget ? `正在等待：${primaryTask.waitingTarget}` : '当前无等待对象',
  currentSymbolsSummary: Array.isArray(primaryTask.currentSymbols) ? primaryTask.currentSymbols.join(', ') : '',
  ...
}
```

- [ ] **Step 4: Run the adapter test again**

Run: `node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ruoyi-ui/src/views/quant/jobs/index.vue ruoyi-ui/src/views/quant/jobs/components/CurrentDispatchMonitor.vue ruoyi-ui/tests/jobs-task-center-state.test.cjs
git commit -m "feat: refocus quant jobs page as dispatch home"
```

## Task 3: Add The Dedicated Manual Dispatch Page

**Files:**
- Create: `ruoyi-ui/src/views/quant/dispatch-manual/index.vue`
- Create: `ruoyi-ui/src/views/quant/dispatch-shared/ManualDispatchSummaryCard.vue`
- Modify: `ruoyi-ui/src/api/quant.js`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`

- [ ] **Step 1: Write the failing page smoke test**

```js
const source = fs.readFileSync('ruoyi-ui/src/views/quant/dispatch-manual/index.vue', 'utf8')

assert.equal(source.includes('行情时间范围'), true)
assert.equal(source.includes('策略起算日'), true)
assert.equal(source.includes('本次提交摘要'), true)
assert.equal(source.includes('执行任务'), true)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test ruoyi-ui/tests/jobs-route.test.cjs`
Expected: FAIL because the new page does not exist yet.

- [ ] **Step 3: Create the manual-dispatch page and extract the existing form into it**

```vue
<manual-dispatch-summary-card
  :strategy-name="selectedStrategyName"
  :scope-summary="scopeSummary"
  :symbols="previewSymbols"
  :market-range="timeRangeSummary"
  :backtest-start-date="jobForm.strategyBacktestStartDate"
  :resolved-mode="resolvedExecutionMode"
  :planned-shards="plannedShardCount"
  @submit="confirmAndSubmitExecution"
/>
```

```js
async confirmAndSubmitExecution() {
  const response = await executeQuantTask(buildExecutionPayload())
  const jobId = response?.data?.jobId
  this.$router.push({ path: `/quant/dispatch-detail/${jobId}` })
}
```

- [ ] **Step 4: Run the page smoke test again**

Run: `node --test ruoyi-ui/tests/jobs-route.test.cjs`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ruoyi-ui/src/views/quant/dispatch-manual/index.vue ruoyi-ui/src/views/quant/dispatch-shared/ManualDispatchSummaryCard.vue ruoyi-ui/src/api/quant.js ruoyi-ui/tests/jobs-route.test.cjs
git commit -m "feat: add dedicated manual dispatch page"
```

## Task 4: Fix The Backend Home Summary Contract

**Files:**
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java`

- [ ] **Step 1: Write the failing backend contract test**

```java
@Test
void summary_should_not_copy_currentStage_into_waitingTarget_when_task_finished() {
    when(queryService.jobReadiness(null)).thenReturn(Map.of(
            "batchId", 12L,
            "status", "READY"));
    when(queryService.jobSteps(12L)).thenReturn(List.of(
            Map.of("stepName", "notify-signals", "status", "SUCCESS", "endTime", "2026-05-10 10:00:00")));

    Map<String, Object> result = service.summary();
    Map<String, Object> primaryTask = cast(result.get("primaryTask"));

    assertEquals("完成", primaryTask.get("currentStageLabel"));
    assertEquals("NONE", primaryTask.get("waitingKind"));
    assertNull(primaryTask.get("waitingTarget"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl ruoyi-admin "-Dtest=QuantTaskCenterServiceTest#summary_should_not_copy_currentStage_into_waitingTarget_when_task_finished" test`
Expected: FAIL because the service currently sets `waitingFor = currentStage`.

- [ ] **Step 3: Replace overloaded home-page fields with explicit fields**

```java
result.put("currentStageCode", stage.code());
result.put("currentStageLabel", stage.label());
result.put("phaseStatus", stage.status());
result.put("waitingKind", waiting.kind());
result.put("waitingTarget", waiting.target());
result.put("currentSymbols", latestDispatch.get("scopeSymbolsPreview"));
```

```java
private WaitingState resolveWaitingState(StageState stage, Map<String, Object> readiness) {
    if ("FINISHED".equals(stage.code())) {
        return WaitingState.none();
    }
    if ("RUNNING".equals(stage.status()) && readiness.get("activeWorkerId") != null) {
        return WaitingState.computing();
    }
    return WaitingState.none();
}
```

- [ ] **Step 4: Run the targeted backend test again**

Run: `mvn -pl ruoyi-admin "-Dtest=QuantTaskCenterServiceTest#summary_should_not_copy_currentStage_into_waitingTarget_when_task_finished" test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java
git commit -m "fix: separate dispatch stage and waiting semantics"
```

## Task 5: Add A Dedicated Dispatch Detail Backend Contract

**Files:**
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantDispatchDetailService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantDispatchDetailServiceTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java`

- [ ] **Step 1: Write the failing service test for detail semantics**

```java
@Test
void detail_should_return_phase_waiting_current_object_and_logs() {
    Map<String, Object> detail = service.detailByJobId(14L);

    assertEquals("RUN_STRATEGY", detail.get("phaseCode"));
    assertEquals("RUNNING", detail.get("phaseStatus"));
    assertEquals("COMPUTING", detail.get("waitingKind"));
    assertEquals(List.of("510300"), detail.get("currentSymbols"));
    assertNotNull(detail.get("logs"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl ruoyi-admin "-Dtest=QuantDispatchDetailServiceTest#detail_should_return_phase_waiting_current_object_and_logs" test`
Expected: FAIL because the service does not exist yet.

- [ ] **Step 3: Implement the dedicated detail service and data endpoint**

```java
@GetMapping("/data/dispatchDetail/{jobId}")
public AjaxResult dispatchDetail(@PathVariable Long jobId) {
    return AjaxResult.success(quantDispatchDetailService.detailByJobId(jobId));
}
```

```java
public Map<String, Object> detailByJobId(Long jobId) {
    Map<String, Object> job = safeMap(quantRoadQueryService.asyncJobDetail(jobId));
    List<Map<String, Object>> shards = safeList(quantRoadQueryService.asyncJobShards(jobId));
    List<Map<String, Object>> results = safeList(quantRoadQueryService.asyncJobResults(jobId));
    List<Map<String, Object>> logs = safeList(quantRoadQueryService.dispatchLiveLogs(jobId, 50));
    return buildDetail(job, shards, results, logs);
}
```

- [ ] **Step 4: Run the new service and controller tests**

Run: `mvn -pl ruoyi-admin "-Dtest=QuantDispatchDetailServiceTest,QuantRoadDataControllerTest" test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantDispatchDetailService.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantDispatchDetailServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java
git commit -m "feat: add quant dispatch detail contract"
```

## Task 6: Build The Dispatch Detail Page

**Files:**
- Create: `ruoyi-ui/src/views/quant/dispatch-detail/index.vue`
- Create: `ruoyi-ui/src/views/quant/dispatch-shared/DispatchOverviewCard.vue`
- Create: `ruoyi-ui/src/views/quant/dispatch-shared/DispatchPhaseCard.vue`
- Create: `ruoyi-ui/src/views/quant/dispatch-shared/DispatchLogStream.vue`
- Create: `ruoyi-ui/src/views/quant/dispatch-shared/DispatchShardTable.vue`
- Create: `ruoyi-ui/src/views/quant/dispatch-shared/DispatchResultSummary.vue`
- Modify: `ruoyi-ui/src/api/quant.js`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`

- [ ] **Step 1: Write the failing detail-page smoke test**

```js
const source = fs.readFileSync('ruoyi-ui/src/views/quant/dispatch-detail/index.vue', 'utf8')

assert.equal(source.includes('执行总览'), true)
assert.equal(source.includes('当前阶段'), true)
assert.equal(source.includes('实时日志'), true)
assert.equal(source.includes('分片进度'), true)
assert.equal(source.includes('结果与异常'), true)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test ruoyi-ui/tests/jobs-route.test.cjs`
Expected: FAIL because the page does not exist yet.

- [ ] **Step 3: Implement the detail page using the new backend contract**

```vue
<dispatch-overview-card :detail="detail" />
<dispatch-phase-card :detail="detail" />
<dispatch-log-stream :rows="detail.logs || []" />
<dispatch-shard-table :rows="detail.shards || []" />
<dispatch-result-summary :detail="detail" />
```

```js
async created() {
  await this.loadDetail()
  this.startAutoRefresh()
}
```

- [ ] **Step 4: Run the smoke test again**

Run: `node --test ruoyi-ui/tests/jobs-route.test.cjs`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ruoyi-ui/src/views/quant/dispatch-detail/index.vue ruoyi-ui/src/views/quant/dispatch-shared/DispatchOverviewCard.vue ruoyi-ui/src/views/quant/dispatch-shared/DispatchPhaseCard.vue ruoyi-ui/src/views/quant/dispatch-shared/DispatchLogStream.vue ruoyi-ui/src/views/quant/dispatch-shared/DispatchShardTable.vue ruoyi-ui/src/views/quant/dispatch-shared/DispatchResultSummary.vue ruoyi-ui/src/api/quant.js ruoyi-ui/tests/jobs-route.test.cjs
git commit -m "feat: add quant dispatch detail page"
```

## Task 7: Add The Auto-Dispatch Page

**Files:**
- Create: `ruoyi-ui/src/views/quant/dispatch-auto/index.vue`
- Create: `ruoyi-ui/src/views/quant/dispatch-shared/AutoDispatchDefinitionTable.vue`
- Create: `ruoyi-ui/src/views/quant/dispatch-shared/AutoDispatchHistoryTable.vue`
- Modify: `ruoyi-ui/src/api/quant.js`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java`

- [ ] **Step 1: Write the failing page and endpoint tests**

```js
const source = fs.readFileSync('ruoyi-ui/src/views/quant/dispatch-auto/index.vue', 'utf8')
assert.equal(source.includes('调度定义'), true)
assert.equal(source.includes('最近执行历史'), true)
assert.equal(source.includes('异常与处理'), true)
```

```java
@Test
void autoDispatchSummary_should_return_definition_and_history() throws Exception {
    mockMvc.perform(get("/quant/data/autoDispatchSummary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.definitions").isArray())
            .andExpect(jsonPath("$.data.history.rows").isArray());
}
```

- [ ] **Step 2: Run the failing tests**

Run: `node --test ruoyi-ui/tests/jobs-route.test.cjs`
Expected: FAIL

Run: `mvn -pl ruoyi-admin "-Dtest=QuantRoadDataControllerTest#autoDispatchSummary_should_return_definition_and_history" test`
Expected: FAIL

- [ ] **Step 3: Implement the auto-dispatch backend summary and frontend page**

```java
@GetMapping("/data/autoDispatchSummary")
public AjaxResult autoDispatchSummary() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("definitions", quantRoadQueryService.dispatchDefinitions());
    result.put("history", quantRoadQueryService.dispatchHistory(1, 20, null, "AUTO"));
    result.put("exceptions", quantRoadQueryService.dispatchExceptions());
    return AjaxResult.success(result);
}
```

```vue
<auto-dispatch-definition-table :rows="definitions" />
<auto-dispatch-history-table :rows="historyRows" @view-detail="openDispatchDetail" />
```

- [ ] **Step 4: Run the new tests again**

Run: `node --test ruoyi-ui/tests/jobs-route.test.cjs`
Expected: PASS

Run: `mvn -pl ruoyi-admin "-Dtest=QuantRoadDataControllerTest#autoDispatchSummary_should_return_definition_and_history" test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ruoyi-ui/src/views/quant/dispatch-auto/index.vue ruoyi-ui/src/views/quant/dispatch-shared/AutoDispatchDefinitionTable.vue ruoyi-ui/src/views/quant/dispatch-shared/AutoDispatchHistoryTable.vue ruoyi-ui/src/api/quant.js ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java
git commit -m "feat: add quant auto dispatch page"
```

## Task 8: Wire End-To-End Navigation And Submission Flow

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Modify: `ruoyi-ui/src/views/quant/dispatch-manual/index.vue`
- Modify: `ruoyi-ui/src/views/quant/dispatch-auto/index.vue`
- Modify: `ruoyi-ui/src/views/quant/dispatch-detail/index.vue`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`

- [ ] **Step 1: Write the failing navigation-flow test**

```js
assert.equal(manualSource.includes("/quant/dispatch-detail/${jobId}"), true)
assert.equal(homeSource.includes("'/quant/dispatch-manual'"), true)
assert.equal(autoSource.includes("openDispatchDetail"), true)
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `node --test ruoyi-ui/tests/jobs-route.test.cjs`
Expected: FAIL if any new page still routes back to `/quant/jobs`.

- [ ] **Step 3: Make all submit/history actions land on dispatch detail**

```js
openDispatchDetail(row) {
  const jobId = row.jobId || row.dispatchId
  this.$router.push({ path: `/quant/dispatch-detail/${jobId}` }).catch(() => {})
}
```

- [ ] **Step 4: Run the test again**

Run: `node --test ruoyi-ui/tests/jobs-route.test.cjs`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ruoyi-ui/src/views/quant/jobs/index.vue ruoyi-ui/src/views/quant/dispatch-manual/index.vue ruoyi-ui/src/views/quant/dispatch-auto/index.vue ruoyi-ui/src/views/quant/dispatch-detail/index.vue ruoyi-ui/tests/jobs-route.test.cjs
git commit -m "feat: unify quant dispatch navigation flow"
```

## Task 9: Verification And Regression Sweep

**Files:**
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantDispatchDetailServiceTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java`
- Test: `ruoyi-ui/tests/jobs-task-center-state.test.cjs`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`

- [ ] **Step 1: Run backend targeted tests**

Run: `mvn -pl ruoyi-admin "-Dtest=QuantTaskCenterServiceTest,QuantDispatchDetailServiceTest,QuantRoadDataControllerTest" test`
Expected: PASS

- [ ] **Step 2: Run frontend targeted tests**

Run: `node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs ruoyi-ui/tests/jobs-route.test.cjs`
Expected: PASS

- [ ] **Step 3: Run frontend production build**

Run: `npm run build:stage`
Workdir: `d:\hundsun-workspaces\itellij-space\git-workspace\quant-road\ruoyi-ui`
Expected: build completes successfully

- [ ] **Step 4: Manually verify the three key paths**

```text
1. Open /quant/jobs and confirm only summary + entry actions remain.
2. Submit an ETF-pool manual dispatch and confirm the app jumps to /quant/dispatch-detail/<jobId>.
3. Open /quant/dispatch-auto and confirm definition/history/detail navigation works.
```

- [ ] **Step 5: Commit**

```bash
git add ruoyi-ui ruoyi-admin
git commit -m "feat: complete quant dispatch center redesign"
```

## Spec Coverage Self-Review

- Home-page simplification and new page map: covered by Tasks 1-2.
- Manual-dispatch dedicated page and submit-summary behavior: covered by Task 3.
- Explicit home summary semantics for stage vs waiting: covered by Task 4.
- Dedicated dispatch-detail contract and page, including logs/shards/results: covered by Tasks 5-6.
- Auto-dispatch dedicated page with definitions/history/exceptions: covered by Task 7.
- Unified navigation from home/manual/auto to detail: covered by Task 8.
- Regression and build verification: covered by Task 9.

## Placeholder Scan Self-Review

- No `TODO`, `TBD`, or “implement later” placeholders remain.
- Every task lists concrete files.
- Every test and implementation step includes concrete commands or code snippets.

## Type Consistency Self-Review

- Home summary uses `currentStageCode/currentStageLabel`, `waitingKind`, and `waitingTarget`.
- Detail page uses `phaseCode/phaseStatus`, `waitingKind`, `currentSymbols`, `logs`, `shards`, and `resultSummary`.
- Frontend navigation consistently targets `/quant/dispatch-manual`, `/quant/dispatch-auto`, and `/quant/dispatch-detail/<jobId>`.
