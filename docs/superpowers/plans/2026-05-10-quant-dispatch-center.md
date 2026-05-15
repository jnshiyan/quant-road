# Quant Dispatch Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current quant task center with a unified dispatch center that clearly exposes manual dispatch, automatic scheduling, time range, dispatch definitions, and dispatch history.

**Architecture:** Extend the existing quant summary/query services to produce a dispatch-oriented payload, then update the current `/quant/jobs` page into `/quant/dispatch` semantics while keeping manual execution on the same page. Reuse existing async job, batch, worker, and Quartz data instead of building a parallel subsystem, and make trigger mode plus time range first-class fields in both backend payloads and frontend UI.

**Tech Stack:** Spring Boot, PostgreSQL, Quartz `sys_job`, Vue 2, Element UI, existing quant async job repository/query services, Jest-style CJS UI tests, JUnit controller/service tests.

---

## File Structure

### Backend files

- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java`
  - Rework summary payload from “task center” wording to “dispatch center” wording.
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java`
  - Add dispatch definition/history endpoints.
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`
  - Add Quartz definition query and dispatch history query methods.
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantJobPlannerService.java`
  - Normalize and persist time-range/trigger metadata for dispatch records.
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantJobRequest.java`
  - Ensure request model carries explicit time range and trigger mode semantics.
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadJobControllerTest.java`
  - Cover execute payload with time range fields.
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java`
  - Cover dispatch summary mapping.

### Frontend files

- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
  - Convert current task page into the dispatch center view.
- Modify: `ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue`
  - Show trigger mode, scope summary, time range summary.
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
  - Show dispatch wording and next auto dispatch hint.
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TaskActionPanel.vue`
  - Keep dynamic recommendation button, but not as the only execution entry.
- Create: `ruoyi-ui/src/views/quant/jobs/components/DispatchDefinitionTable.vue`
  - Render task definitions / automatic scheduling details.
- Create: `ruoyi-ui/src/views/quant/jobs/components/DispatchHistoryTable.vue`
  - Render paginated dispatch history.
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
  - Update UI state builder for dispatch semantics.
- Modify: `ruoyi-ui/src/api/quant.js`
  - Add dispatch definition/history APIs.
- Modify: `sql/ruoyi_quant_menu.sql`
  - Rename menu label from `任务中心` to `调度中心`.
- Modify: `ruoyi-ui/tests/jobs-route.test.cjs`
  - Adjust route/menu wording expectations.
- Create: `ruoyi-ui/tests/jobs-dispatch-center-state.test.cjs`
  - Cover dispatch-specific UI state mapping.

### Docs

- Modify: `docs/superpowers/specs/2026-05-10-quant-dispatch-center-design.md`
  - Link implementation status note if needed after delivery.

## Task 1: Define backend dispatch summary and history payloads

**Files:**
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java`

- [ ] **Step 1: Write the failing service test for dispatch summary**

```java
@Test
void summary_shouldExposeDispatchSpecificFields()
{
    QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
    when(queryService.jobReadiness(null)).thenReturn(Map.of(
            "status", "RUNNING",
            "batchId", 12L,
            "message", "盘后主流程运行中",
            "completedSteps", 2,
            "totalSteps", 5));
    when(queryService.jobSteps(12L)).thenReturn(List.of(
            Map.of("stepName", "run-portfolio", "status", "RUNNING", "startTime", "2026-05-10 15:31:00")));
    when(queryService.jobSopHints(null)).thenReturn(List.of());
    when(queryService.asyncWorkerSummary()).thenReturn(Map.of("status", "RUNNING"));
    when(queryService.asyncJobs(5)).thenReturn(List.of());
    when(queryService.dispatchDefinitions()).thenReturn(List.of(
            Map.of("taskCode", "fullDailyAsync", "taskName", "盘后主流程", "triggerModes", List.of("manual", "auto"))));
    when(queryService.dispatchHistory(1, 20, null, null)).thenReturn(Map.of(
            "rows", List.of(Map.of("dispatchId", 1L, "triggerMode", "auto")),
            "total", 1));
    when(queryService.nextScheduledDispatch()).thenReturn(Map.of(
            "taskName", "盘后主流程",
            "nextFireTime", "2026-05-10 15:30:00"));

    QuantTaskCenterService service = new QuantTaskCenterService(queryService);

    Map<String, Object> payload = service.summary();

    assertThat(payload).containsKeys("todayStatus", "primaryTask", "dispatchDefinitions", "dispatchHistory", "nextScheduledDispatch");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl ruoyi-admin -Dtest=QuantTaskCenterServiceTest test`
Expected: FAIL because `dispatchDefinitions`, `dispatchHistory`, or `nextScheduledDispatch` are not present and the new query methods are missing.

- [ ] **Step 3: Implement the minimal backend query surface**

```java
// QuantRoadDataController.java
@GetMapping("/data/dispatchDefinitions")
@PreAuthorize("@ss.hasPermi('quant:data:query')")
public AjaxResult dispatchDefinitions()
{
    return AjaxResult.success(quantRoadQueryService.dispatchDefinitions());
}

@GetMapping("/data/dispatchHistory")
@PreAuthorize("@ss.hasPermi('quant:data:query')")
public AjaxResult dispatchHistory(
        @RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String taskCode,
        @RequestParam(required = false) String triggerMode)
{
    return AjaxResult.success(quantRoadQueryService.dispatchHistory(pageNum, pageSize, taskCode, triggerMode));
}
```

```java
// QuantTaskCenterService.java
payload.put("nextScheduledDispatch", safeMap(quantRoadQueryService.nextScheduledDispatch()));
payload.put("dispatchDefinitions", safeList(quantRoadQueryService.dispatchDefinitions()));
payload.put("dispatchHistory", safeMap(quantRoadQueryService.dispatchHistory(1, 10, null, null)));
```

```java
// QuantRoadQueryService.java
public List<Map<String, Object>> dispatchDefinitions()
{
    return jdbcTemplate.queryForList(
        "SELECT job_name AS taskName, invoke_target AS taskCode, cron_expression AS cronExpression, " +
        "status, remark, concurrent, next_valid_time AS nextFireTime " +
        "FROM sys_job WHERE job_name LIKE 'Quant %' ORDER BY job_name");
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl ruoyi-admin -Dtest=QuantTaskCenterServiceTest test`
Expected: PASS with the dispatch summary payload containing the new sections.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java
git commit -m "feat: expose quant dispatch summary and history"
```

## Task 2: Make time range and trigger mode explicit in dispatch execution

**Files:**
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantJobRequest.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantJobPlannerService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/task/QuantRoadTask.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadJobControllerTest.java`

- [ ] **Step 1: Write the failing controller test for manual execute time range**

```java
@Test
void execute_shouldReturnResolvedTimeRangeAndTriggerMode() throws Exception
{
    when(quantExecutionFacade.execute(any())).thenReturn(responseWith(
            101L,
            "QUEUED",
            "async"));

    mockMvc.perform(post("/quant/jobs/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "strategyId": 1,
                  "scopeType": "etf_pool",
                  "scopePoolCode": "ETF_CORE",
                  "strategyBacktestStartDate": "2024-01-01",
                  "endDate": "2026-05-10",
                  "actor": "ruoyi-ui"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resolvedExecutionMode").value("async"))
        .andExpect(jsonPath("$.data.timeRange.startDate").value("2024-01-01"))
        .andExpect(jsonPath("$.data.timeRange.endDate").value("2026-05-10"))
        .andExpect(jsonPath("$.data.triggerMode").value("manual"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl ruoyi-admin -Dtest=QuantRoadJobControllerTest test`
Expected: FAIL because the execute response does not include `timeRange` or `triggerMode`.

- [ ] **Step 3: Implement normalized dispatch metadata**

```java
// QuantJobPlannerService.java
private Map<String, Object> buildNormalizedPayload(QuantAsyncJobRequest request, String jobType)
{
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("jobType", jobType);
    payload.put("scopeType", request.getScopeType());
    payload.put("scopePoolCode", request.getScopePoolCode());
    payload.put("triggerMode", "manual");
    payload.put("startDate", request.getStrategyBacktestStartDate());
    payload.put("endDate", request.getEndDate());
    payload.put("actor", blankToDefault(request.getActor(), "system"));
    return payload;
}
```

```java
// QuantRoadTask.java
private QuantJobRequest buildScheduledPortfolioRequest(String strategyBacktestStartDate, Double totalCapital)
{
    QuantJobRequest request = new QuantJobRequest();
    request.setRequestedMode("async");
    request.setUsePortfolio(Boolean.TRUE);
    request.setStrategyBacktestStartDate(strategyBacktestStartDate);
    request.setPortfolioTotalCapital(totalCapital);
    request.setActor("quartz");
    request.setEndDate(LocalDate.now().toString());
    return request;
}
```

```java
// QuantRoadJobController.java execute response assembly
result.put("triggerMode", "manual");
result.put("timeRange", Map.of(
        "startDate", request.getStrategyBacktestStartDate(),
        "endDate", request.getEndDate() == null ? LocalDate.now().toString() : request.getEndDate()));
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl ruoyi-admin -Dtest=QuantRoadJobControllerTest test`
Expected: PASS with explicit `triggerMode` and `timeRange`.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantJobRequest.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantJobPlannerService.java ruoyi-admin/src/main/java/com/ruoyi/web/task/QuantRoadTask.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadJobController.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadJobControllerTest.java
git commit -m "feat: expose quant dispatch time range metadata"
```

## Task 3: Add frontend dispatch APIs and state mapping

**Files:**
- Modify: `ruoyi-ui/src/api/quant.js`
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
- Create: `ruoyi-ui/tests/jobs-dispatch-center-state.test.cjs`

- [ ] **Step 1: Write the failing UI state test**

```javascript
const { buildTaskCenterState } = require('../src/views/quant/jobs/jobs-task-center-state')

test('buildTaskCenterState should keep next scheduled dispatch and time range rows', () => {
  const state = buildTaskCenterState({
    nextScheduledDispatch: { taskName: '盘后主流程', nextFireTime: '2026-05-10 15:30:00' },
    dispatchHistory: { rows: [{ dispatchId: 1, triggerMode: 'auto', timeRangeSummary: '2021-05-10 ~ 2026-05-10' }] },
    primaryTask: { taskName: '盘后主流程', timeRangeSummary: '2021-05-10 ~ 2026-05-10' }
  })

  expect(state.nextScheduledDispatch.taskName).toBe('盘后主流程')
  expect(state.dispatchHistoryRows[0].triggerMode).toBe('auto')
  expect(state.primaryTaskRows.some(item => item.label === '时间范围')).toBe(true)
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ruoyi-ui; npm test -- jobs-dispatch-center-state.test.cjs`
Expected: FAIL because the state builder does not expose dispatch definition/history rows.

- [ ] **Step 3: Implement frontend API and state changes**

```javascript
// quant.js
export function getDispatchDefinitions() {
  return request({
    url: '/quant/data/dispatchDefinitions',
    method: 'get'
  })
}

export function getDispatchHistory(params) {
  return request({
    url: '/quant/data/dispatchHistory',
    method: 'get',
    params
  })
}
```

```javascript
// jobs-task-center-state.js
function buildPrimaryTaskRows(primaryTask = {}) {
  return [
    { label: '当前阶段', value: primaryTask.currentStage || '-' },
    { label: '当前状态', value: primaryTask.status || '-' },
    { label: '触发方式', value: primaryTask.triggerModeLabel || '-' },
    { label: '时间范围', value: primaryTask.timeRangeSummary || '-' }
  ]
}

function buildTaskCenterState(payload = {}) {
  return {
    primaryAction: resolvePrimaryAction(payload.todayStatus || {}, payload.nextAction || {}),
    progressEvents: payload.progressEvents || [],
    technicalSummaryRows: buildTechnicalSummaryRows(payload.technicalSummary || {}),
    primaryTaskRows: buildPrimaryTaskRows(payload.primaryTask || {}),
    nextScheduledDispatch: payload.nextScheduledDispatch || {},
    dispatchHistoryRows: (payload.dispatchHistory && payload.dispatchHistory.rows) || []
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ruoyi-ui; npm test -- jobs-dispatch-center-state.test.cjs`
Expected: PASS with dispatch fields mapped to UI state.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-ui/src/api/quant.js ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js ruoyi-ui/tests/jobs-dispatch-center-state.test.cjs
git commit -m "feat: add quant dispatch frontend state"
```

## Task 4: Rebuild the current jobs page into the dispatch center UI

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TaskActionPanel.vue`
- Create: `ruoyi-ui/src/views/quant/jobs/components/DispatchDefinitionTable.vue`
- Create: `ruoyi-ui/src/views/quant/jobs/components/DispatchHistoryTable.vue`

- [ ] **Step 1: Write the failing UI route/view expectation test**

```javascript
test('jobs page should show dispatch center labels and fixed execute button copy', async () => {
  const source = fs.readFileSync('src/views/quant/jobs/index.vue', 'utf8')
  expect(source).toContain('量化调度中心')
  expect(source).toContain('执行任务')
  expect(source).toContain('时间范围')
  expect(source).toContain('下一次自动调度')
  expect(source).toContain('调度历史')
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ruoyi-ui; npm test -- jobs-route.test.cjs`
Expected: FAIL because the existing page still uses `量化任务中心` language and lacks the new sections.

- [ ] **Step 3: Implement the dispatch center layout**

```vue
<!-- index.vue hero -->
<div class="hero-eyebrow">量化调度中心</div>
<h1>在一个页面里看清手工触发、自动调度和当前主调度。</h1>
```

```vue
<!-- index.vue manual dispatch section -->
<el-form-item label="时间范围">
  <el-date-picker
    v-model="timeRange"
    type="daterange"
    value-format="yyyy-MM-dd"
    range-separator="至"
    start-placeholder="开始日期"
    end-placeholder="结束日期" />
</el-form-item>
<el-form-item label="策略起算日">
  <el-date-picker
    v-model="jobForm.strategyBacktestStartDate"
    type="date"
    value-format="yyyy-MM-dd"
    placeholder="策略起算日" />
</el-form-item>
<el-button type="primary" :loading="pendingActionKey === 'RUN_EXECUTION'" @click="confirmAndSubmitExecution({ code: 'RUN_EXECUTION', label: '执行任务' })">
  执行任务
</el-button>
```

```vue
<!-- index.vue summary/history sections -->
<dispatch-definition-table :items="dispatchDefinitions" @filter-history="handleFilterHistory" />
<dispatch-history-table :rows="dispatchHistory.rows" :total="dispatchHistory.total" :query="historyQuery" @change="loadDispatchHistory" />
```

```vue
<!-- PrimaryTaskCard.vue -->
<div class="task-item">
  <label>触发方式</label>
  <span>{{ task.triggerModeLabel || '-' }}</span>
</div>
<div class="task-item wide">
  <label>时间范围</label>
  <span>{{ task.timeRangeSummary || '-' }}</span>
</div>
```

- [ ] **Step 4: Run the UI tests and targeted lint-like checks**

Run: `cd ruoyi-ui; npm test -- jobs-route.test.cjs jobs-dispatch-center-state.test.cjs`
Expected: PASS with the new dispatch center wording and sections present.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-ui/src/views/quant/jobs/index.vue ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue ruoyi-ui/src/views/quant/jobs/components/TaskActionPanel.vue ruoyi-ui/src/views/quant/jobs/components/DispatchDefinitionTable.vue ruoyi-ui/src/views/quant/jobs/components/DispatchHistoryTable.vue ruoyi-ui/tests/jobs-route.test.cjs
git commit -m "feat: redesign quant jobs page as dispatch center"
```

## Task 5: Rename menu and route language from task center to dispatch center

**Files:**
- Modify: `sql/ruoyi_quant_menu.sql`
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-route.js`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`

- [ ] **Step 1: Write the failing menu/route wording test**

```javascript
test('jobs route helpers should point to dispatch center wording', () => {
  const source = fs.readFileSync('src/views/quant/jobs/index.vue', 'utf8')
  expect(source).toContain('量化调度中心')
  expect(source).not.toContain('量化任务中心')
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ruoyi-ui; npm test -- jobs-route.test.cjs`
Expected: FAIL because the old wording still exists.

- [ ] **Step 3: Implement the menu rename**

```sql
-- sql/ruoyi_quant_menu.sql
('调度中心', v_quant_root_id, 3, 'jobs', 'quant/jobs/index', NULL, 'QuantDispatchCenter', '1', '0', 'C', '0', '0', '', 'job', 'admin', NOW(), 'Quant 调度发起与历史观察中心')
```

```sql
UPDATE sys_menu
   SET menu_name = '调度中心',
       route_name = 'QuantDispatchCenter',
       remark = 'Quant 调度发起与历史观察中心'
 WHERE menu_id = v_jobs_menu_id;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ruoyi-ui; npm test -- jobs-route.test.cjs`
Expected: PASS with the new wording only.

- [ ] **Step 5: Commit**

```bash
git add sql/ruoyi_quant_menu.sql ruoyi-ui/src/views/quant/jobs/index.vue ruoyi-ui/src/views/quant/jobs/jobs-route.js ruoyi-ui/tests/jobs-route.test.cjs
git commit -m "feat: rename quant task center to dispatch center"
```

## Task 6: End-to-end verification and documentation touch-up

**Files:**
- Modify: `docs/superpowers/specs/2026-05-10-quant-dispatch-center-design.md`
- Modify: `docs/superpowers/plans/2026-05-10-quant-dispatch-center.md`

- [ ] **Step 1: Run backend verification**

Run: `mvn -pl ruoyi-admin -Dtest=QuantTaskCenterServiceTest,QuantRoadJobControllerTest test`
Expected: PASS with the new dispatch summary and execute response coverage.

- [ ] **Step 2: Run frontend verification**

Run: `cd ruoyi-ui; npm test -- jobs-route.test.cjs jobs-dispatch-center-state.test.cjs`
Expected: PASS with dispatch center wording and state mapping green.

- [ ] **Step 3: Run a manual smoke flow**

Run:

```powershell
Invoke-WebRequest http://localhost:8080/quant/data/taskCenterSummary -UseBasicParsing
Invoke-WebRequest http://localhost:8080/quant/data/dispatchDefinitions -UseBasicParsing
Invoke-WebRequest "http://localhost:8080/quant/data/dispatchHistory?pageNum=1&pageSize=5" -UseBasicParsing
```

Expected:

- `taskCenterSummary` payload contains `nextScheduledDispatch`, `dispatchDefinitions`, `dispatchHistory`
- `dispatchDefinitions` returns Quant Quartz rows
- `dispatchHistory` returns paginated rows with trigger mode and time range fields

- [ ] **Step 4: Update docs note**

```markdown
## Implementation status

- `任务中心` 已按设计升级为 `调度中心`
- 手工执行、自动调度、补偿触发已统一为触发方式
- 时间范围已在发起区、当前主调度、调度历史中显式展示
```

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-05-10-quant-dispatch-center-design.md docs/superpowers/plans/2026-05-10-quant-dispatch-center.md
git commit -m "docs: record quant dispatch center implementation status"
```
