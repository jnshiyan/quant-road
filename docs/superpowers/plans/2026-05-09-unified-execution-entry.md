# Unified Execution Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the user-facing `fullDaily` / `runStrategy` / `runPortfolio` split with one unified execution entry that always targets formal results, auto-generates an execution plan, and auto-decides sync vs async.

**Architecture:** Add a single Java-side `execute` API that normalizes scope and request fields into a unified execution request, generates an `ExecutionPlan`, and hands that plan to a unified engine. In phase 1, the engine may bridge to legacy Python commands; in phase 2, Python executes plan-driven steps with pruning instead of the fixed `full-daily` pipeline.

**Tech Stack:** Spring Boot, Maven/JUnit 5, Vue 2 + Element UI, Python 3 CLI services, pytest.

---

### Task 1: Add The Unified Execute API Contract

**Files:**
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantExecutionResponse.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantExecutionFacade.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadJobController.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantJobRequest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadJobControllerTest.java`

- [ ] **Step 1: Write the failing controller test for `POST /quant/jobs/execute`**

```java
@Test
void executeShouldDelegateUnifiedRequest() throws Exception {
    QuantExecutionResponse response = new QuantExecutionResponse();
    response.setStatus("QUEUED");
    response.setResolvedExecutionMode("async");
    response.setPlanSummary("sync-daily -> run-strategy -> evaluate-risk");
    when(quantExecutionFacade.execute(any(QuantJobRequest.class))).thenReturn(response);

    mockMvc.perform(post("/quant/jobs/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "strategyId": 1,
                  "scopeType": "etf_pool",
                  "scopePoolCode": "ETF_CORE",
                  "strategyBacktestStartDate": "2023-01-01",
                  "actor": "plan-test"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("QUEUED"))
        .andExpect(jsonPath("$.data.resolvedExecutionMode").value("async"));
}
```

- [ ] **Step 2: Run the controller test and verify it fails**

Run:

```bash
mvn -pl ruoyi-admin "-Dtest=QuantRoadJobControllerTest#executeShouldDelegateUnifiedRequest" test
```

Expected: FAIL because `/quant/jobs/execute` and `QuantExecutionFacade` do not exist yet.

- [ ] **Step 3: Add the minimal unified facade and endpoint**

```java
@PostMapping("/execute")
@PreAuthorize("@ss.hasPermi('quant:job:run')")
public AjaxResult execute(@RequestBody(required = false) QuantJobRequest request) {
    QuantJobRequest payload = request == null ? new QuantJobRequest() : request;
    return AjaxResult.success(quantExecutionFacade.execute(payload));
}
```

```java
public class QuantExecutionResponse {
    private Long executionId;
    private String status;
    private String resolvedExecutionMode;
    private String planSummary;
    private Object estimatedCost;
    // getters / setters
}
```

```java
@Service
public class QuantExecutionFacade {
    public QuantExecutionResponse execute(QuantJobRequest request) {
        QuantExecutionResponse response = new QuantExecutionResponse();
        response.setStatus("PENDING");
        response.setResolvedExecutionMode("sync");
        response.setPlanSummary("planning");
        return response;
    }
}
```

- [ ] **Step 4: Re-run the controller test**

Run:

```bash
mvn -pl ruoyi-admin "-Dtest=QuantRoadJobControllerTest#executeShouldDelegateUnifiedRequest" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantExecutionResponse.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantExecutionFacade.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadJobController.java ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantJobRequest.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadJobControllerTest.java
git commit -m "feat: add unified execute api contract"
```

### Task 2: Introduce A First-Class Execution Plan

**Files:**
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantExecutionPlan.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantExecutionPlanStep.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantExecutionPlanService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantExecutionFacade.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadSymbolScopeService.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantExecutionPlanServiceTest.java`

- [ ] **Step 1: Write failing planner tests for small-scope ETF and full-market plans**

```java
@Test
void buildPlanShouldPruneGlobalStepsForEtfPool() {
    QuantJobRequest request = new QuantJobRequest();
    request.setStrategyId(1L);
    request.setScopeType("etf_pool");
    request.setScopePoolCode("ETF_CORE");
    request.setStrategyBacktestStartDate("2023-01-01");

    QuantExecutionPlan plan = service.buildPlan(request);

    assertThat(plan.getResolvedSymbols()).containsExactly("510300", "510500");
    assertThat(plan.getSteps()).extracting(QuantExecutionPlanStep::getStepName)
        .containsExactly("sync-daily", "run-strategy", "evaluate-risk");
}

@Test
void buildPlanShouldIncludeGlobalStepsForAllStocks() {
    QuantJobRequest request = new QuantJobRequest();
    request.setScopeType("all_stocks");
    request.setStrategyBacktestStartDate("2023-01-01");

    QuantExecutionPlan plan = service.buildPlan(request);

    assertThat(plan.getSteps()).extracting(QuantExecutionPlanStep::getStepName)
        .contains("sync-basic", "sync-daily", "evaluate-market", "run-strategy", "evaluate-risk");
}
```

- [ ] **Step 2: Run the planner tests and verify they fail**

Run:

```bash
mvn -pl ruoyi-admin "-Dtest=QuantExecutionPlanServiceTest" test
```

Expected: FAIL because the plan model and service do not exist.

- [ ] **Step 3: Implement the plan model and deterministic step selection rules**

```java
public class QuantExecutionPlan {
    private List<String> resolvedSymbols;
    private List<QuantExecutionPlanStep> steps;
    private String resolvedExecutionMode;
    private String planSummary;
    private Map<String, Object> estimatedCost;
}
```

```java
public QuantExecutionPlan buildPlan(QuantJobRequest request) {
    List<String> symbols = symbolScopeService.resolveScopeSymbols(
        request.getScopeType(),
        request.getScopePoolCode(),
        request.getSymbols(),
        request.getWhitelist(),
        request.getBlacklist(),
        request.getAdHocSymbols()
    );
    List<QuantExecutionPlanStep> steps = new ArrayList<>();
    if (requiresSyncBasic(symbols, request)) {
        steps.add(step("sync-basic", "global"));
    }
    steps.add(step("sync-daily", symbols.size() <= 200 ? "scoped" : "global"));
    if (requiresMarketEvaluation(request)) {
        steps.add(step("evaluate-market", "global"));
    }
    steps.add(step("run-strategy", "scoped"));
    steps.add(step("evaluate-risk", "scoped"));
    return finalizePlan(symbols, steps, request);
}
```

- [ ] **Step 4: Re-run the planner tests**

Run:

```bash
mvn -pl ruoyi-admin "-Dtest=QuantExecutionPlanServiceTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantExecutionPlan.java ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantExecutionPlanStep.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantExecutionPlanService.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantExecutionFacade.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantExecutionPlanServiceTest.java
git commit -m "feat: add unified execution planning"
```

### Task 3: Add A Unified Engine That Bridges Sync And Async

**Files:**
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantExecutionEngineService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantExecutionFacade.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantJobPlannerService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadPythonService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/task/QuantRoadTask.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantAsyncExecutionFacadeTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantJobPlannerServiceTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantExecutionEngineServiceTest.java`

- [ ] **Step 1: Write failing engine tests for sync and async resolution**

```java
@Test
void executeShouldRunSyncForSmallPlan() {
    QuantExecutionPlan plan = planWithMode("sync", List.of("sync-daily", "run-strategy", "evaluate-risk"));
    when(pythonService.executePlan(plan)).thenReturn("{\"status\":\"SUCCESS\"}");

    QuantExecutionResponse response = service.execute(plan, sampleRequest());

    assertThat(response.getStatus()).isEqualTo("SUCCESS");
    verify(pythonService).executePlan(plan);
    verifyNoInteractions(jobPlannerService);
}

@Test
void executeShouldSubmitAsyncForLargePlan() {
    QuantExecutionPlan plan = planWithMode("async", List.of("sync-basic", "sync-daily", "evaluate-market", "run-strategy", "evaluate-risk"));

    QuantExecutionResponse response = service.execute(plan, sampleRequest());

    assertThat(response.getStatus()).isEqualTo("QUEUED");
    verify(jobPlannerService).submitExecutionPlan(plan, sampleRequest());
}
```

- [ ] **Step 2: Run the engine tests and verify they fail**

Run:

```bash
mvn -pl ruoyi-admin "-Dtest=QuantExecutionEngineServiceTest,QuantJobPlannerServiceTest,QuantAsyncExecutionFacadeTest" test
```

Expected: FAIL because `executePlan(...)` and `submitExecutionPlan(...)` do not exist.

- [ ] **Step 3: Implement the unified engine with transitional bridges**

```java
public QuantExecutionResponse execute(QuantExecutionPlan plan, QuantJobRequest request) {
    if ("async".equalsIgnoreCase(plan.getResolvedExecutionMode())) {
        return jobPlannerService.submitExecutionPlan(plan, request);
    }
    String output = pythonService.executePlan(plan);
    QuantExecutionResponse response = new QuantExecutionResponse();
    response.setStatus("SUCCESS");
    response.setResolvedExecutionMode("sync");
    response.setPlanSummary(plan.getPlanSummary());
    response.setOutput(output);
    return response;
}
```

```java
public QuantAsyncJobResponse submitExecutionPlan(QuantExecutionPlan plan, QuantJobRequest request) {
    if (plan.isStrategyOnly()) {
        return submitRunStrategy(normalizeRunStrategy(request));
    }
    return submitLegacyFullPlan(plan, request);
}
```

```java
public String executePlan(QuantExecutionPlan plan) {
    return execute(List.of(
        properties.getExecutable(),
        "-m",
        properties.getModuleName(),
        "execute-task",
        "--plan-json",
        JSON.toJSONString(plan)
    ));
}
```

- [ ] **Step 4: Re-run the Java engine tests**

Run:

```bash
mvn -pl ruoyi-admin "-Dtest=QuantExecutionEngineServiceTest,QuantJobPlannerServiceTest,QuantAsyncExecutionFacadeTest,QuantRoadJobControllerTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantExecutionEngineService.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantExecutionFacade.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantJobPlannerService.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadPythonService.java ruoyi-admin/src/main/java/com/ruoyi/web/task/QuantRoadTask.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantExecutionEngineServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantJobPlannerServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantAsyncExecutionFacadeTest.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadJobControllerTest.java
git commit -m "feat: add unified execution engine"
```

### Task 4: Make Python Execute A Plan Instead Of A Fixed `full-daily`

**Files:**
- Create: `python/src/quant_road/services/unified_execution_service.py`
- Modify: `python/src/quant_road/cli.py`
- Modify: `python/src/quant_road/services/pipeline_service.py`
- Modify: `python/src/quant_road/services/async_worker_service.py`
- Test: `python/tests/test_cli_full_daily.py`
- Test: `python/tests/test_pipeline_batch.py`
- Test: `python/tests/test_async_worker_service.py`
- Test: `python/tests/test_unified_execution_service.py`

- [ ] **Step 1: Write failing Python tests for plan-driven step pruning**

```python
def test_execute_task_prunes_global_steps_for_small_scope(monkeypatch):
    plan = {
        "resolved_symbols": ["510300", "510500"],
        "steps": [
            {"step_name": "sync-daily"},
            {"step_name": "run-strategy"},
            {"step_name": "evaluate-risk"},
        ],
        "resolved_execution_mode": "sync",
    }

    called = []

    monkeypatch.setattr("quant_road.services.unified_execution_service.sync_stock_daily", lambda **kwargs: called.append("sync-daily"))
    monkeypatch.setattr("quant_road.services.unified_execution_service.run_strategy", lambda **kwargs: called.append("run-strategy"))
    monkeypatch.setattr("quant_road.services.unified_execution_service.update_position_risk", lambda **kwargs: called.append("evaluate-risk"))

    execute_execution_plan(plan)

    assert called == ["sync-daily", "run-strategy", "evaluate-risk"]
```

```python
def test_cli_execute_task_accepts_plan_json(capsys):
    main_args = ["execute-task", "--plan-json", "{\"resolved_symbols\": [], \"steps\": [], \"resolved_execution_mode\": \"sync\"}"]
    main(main_args)
    out = capsys.readouterr().out
    assert "SUCCESS" in out
```

- [ ] **Step 2: Run the Python tests and verify they fail**

Run:

```bash
cd python
pytest tests/test_unified_execution_service.py tests/test_cli_full_daily.py tests/test_pipeline_batch.py -q
```

Expected: FAIL because `execute-task` and `unified_execution_service` do not exist.

- [ ] **Step 3: Add the plan-driven Python executor**

```python
def execute_execution_plan(plan: dict) -> dict:
    runner = PipelineRunner("execute-task", plan)
    runner.start_batch()
    try:
        for step in plan.get("steps", []):
            step_name = step["step_name"]
            if step_name == "sync-daily":
                runner.run_step(step_name, lambda: sync_stock_daily(symbols=plan.get("resolved_symbols")))
            elif step_name == "run-strategy":
                runner.run_step(step_name, lambda: run_strategy(symbols=plan.get("resolved_symbols")))
            elif step_name == "evaluate-risk":
                runner.run_step(step_name, lambda: update_position_risk())
        runner.finalize(success=True)
        return {"status": "SUCCESS", "batch_id": runner.batch_id}
    except Exception as exc:
        runner.finalize(success=False, error_message=str(exc))
        raise
```

```python
execute_task = subparsers.add_parser("execute-task", help="Execute a unified execution plan")
execute_task.add_argument("--plan-json", required=True)
```

- [ ] **Step 4: Re-run the Python tests**

Run:

```bash
cd python
pytest tests/test_unified_execution_service.py tests/test_cli_full_daily.py tests/test_pipeline_batch.py tests/test_async_worker_service.py -q
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add python/src/quant_road/services/unified_execution_service.py python/src/quant_road/cli.py python/src/quant_road/services/pipeline_service.py python/src/quant_road/services/async_worker_service.py python/tests/test_unified_execution_service.py python/tests/test_cli_full_daily.py python/tests/test_pipeline_batch.py python/tests/test_async_worker_service.py
git commit -m "feat: execute quant tasks from unified plans"
```

### Task 5: Replace The Jobs Page With One User Action And Separate Ops Tools

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/JobStatusCard.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-explain.js`
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-full-daily-guard.js`
- Modify: `ruoyi-ui/src/api/quant.js`

- [ ] **Step 1: Write the UI behavior expectations as inline checklist comments in the task page**

```vue
<!-- expected behavior during refactor:
1. one primary button: 执行任务
2. no requestedMode selector
3. no fullDaily / runStrategy / runPortfolio action groups
4. worker operations remain, but in a dedicated 运维排障 section
5. current task card shows execution mode as system-decided, not user-selected
-->
```

- [ ] **Step 2: Build the new API client surface around `/quant/jobs/execute`**

```js
export function executeQuantTask(data) {
  return quantJobRequest({
    url: '/quant/jobs/execute',
    method: 'post',
    data
  })
}
```

```js
export function submitQuantJob(data) {
  return executeQuantTask(data)
}
```

- [ ] **Step 3: Simplify the jobs page to one primary execution action**

```vue
<el-form-item label="策略">
  <el-select v-model="jobForm.strategyId" clearable filterable style="width: 260px" placeholder="请选择策略" />
</el-form-item>

<el-button type="primary" size="small" :loading="loadingAction" @click="handleExecuteTask">
  执行任务
</el-button>
```

```js
async handleExecuteTask() {
  const payload = {
    strategyId: this.jobForm.strategyId,
    strategyBacktestStartDate: this.jobForm.strategyBacktestStartDate,
    portfolioTotalCapital: this.jobForm.portfolioTotalCapital,
    actor: this.jobForm.actor,
    notify: this.jobForm.notify,
    ...this.buildScopePayload()
  }
  const response = await executeQuantTask(payload)
  this.applyExecutionResponse(response.data)
}
```

- [ ] **Step 4: Update the status card and helper copy**

```vue
<div class="job-item">
  <label>执行方式</label>
  <span>{{ jobStatus.resolvedExecutionMode || '-' }}</span>
</div>
<div class="job-item wide">
  <label>执行计划</label>
  <span>{{ jobStatus.planSummary || '-' }}</span>
</div>
```

- [ ] **Step 5: Run the frontend build**

Run:

```bash
cd ruoyi-ui
npm run build:prod
```

Expected: PASS with production build output in `dist/`.

- [ ] **Step 6: Commit**

```bash
git add ruoyi-ui/src/views/quant/jobs/index.vue ruoyi-ui/src/views/quant/jobs/components/JobStatusCard.vue ruoyi-ui/src/views/quant/jobs/jobs-explain.js ruoyi-ui/src/views/quant/jobs/jobs-full-daily-guard.js ruoyi-ui/src/api/quant.js
git commit -m "feat: unify quant job center entry"
```

### Task 6: Retire Legacy Semantics And Backfill Compatibility Coverage

**Files:**
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantAsyncExecutionFacade.java`
- Modify: `ruoyi-admin/src/main/resources/application.yml`
- Modify: `ruoyi-ui/src/views/quant/dashboard/index.vue`
- Modify: `ruoyi-ui/src/views/quant/backtest/index.vue`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantJobPlannerIntegrationTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantRoadQueryServiceTest.java`

- [ ] **Step 1: Write failing compatibility tests for deprecated entry forwarding**

```java
@Test
void legacyRunStrategyShouldForwardToUnifiedPlanner() {
    QuantJobRequest request = new QuantJobRequest();
    request.setStrategyId(1L);
    request.setScopeType("etf_pool");
    request.setScopePoolCode("ETF_CORE");

    QuantAsyncJobResponse response = facade.submitLegacyRunStrategy(request);

    assertThat(response.getResolvedMode()).isIn("sync", "async");
    verify(executionFacade).execute(any(QuantJobRequest.class));
}
```

```java
@Test
void jobHintsShouldReferenceUnifiedExecutionLanguage() {
    List<Map<String, Object>> hints = queryService.jobSopHints(21L);
    assertThat(hints).allMatch(item -> !String.valueOf(item.get("summary")).contains("fullDaily"));
}
```

- [ ] **Step 2: Run the compatibility tests and verify they fail**

Run:

```bash
mvn -pl ruoyi-admin "-Dtest=QuantJobPlannerIntegrationTest,QuantRoadQueryServiceTest" test
```

Expected: FAIL because legacy wording and forwarding still reference `fullDaily` / `requestedMode`.

- [ ] **Step 3: Replace legacy wording and redirect compatibility paths**

```java
public QuantAsyncJobResponse submitLegacyRunStrategy(QuantJobRequest request) {
    QuantExecutionResponse execution = executionFacade.execute(request);
    return QuantAsyncJobResponse.fromExecution(execution);
}
```

```java
empty.put("message", "暂无执行批次，请先提交执行任务。");
hints.add(hint("runExecution", "info", "先提交执行任务", "当前还没有可评估的执行批次。", "先执行任务，再进入看板。", "/quant/jobs", null, false));
```

- [ ] **Step 4: Run the full targeted verification suite**

Run:

```bash
mvn -pl ruoyi-admin "-Dtest=QuantRoadJobControllerTest,QuantExecutionPlanServiceTest,QuantExecutionEngineServiceTest,QuantJobPlannerServiceTest,QuantJobPlannerIntegrationTest,QuantRoadQueryServiceTest,QuantAsyncExecutionFacadeTest" test
cd python
pytest tests/test_unified_execution_service.py tests/test_cli_full_daily.py tests/test_pipeline_batch.py tests/test_async_worker_service.py -q
cd ..\\ruoyi-ui
npm run build:prod
```

Expected:

- Java tests PASS
- Python tests PASS
- Frontend build PASS

- [ ] **Step 5: Commit**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantAsyncExecutionFacade.java ruoyi-admin/src/main/resources/application.yml ruoyi-ui/src/views/quant/dashboard/index.vue ruoyi-ui/src/views/quant/backtest/index.vue ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantJobPlannerIntegrationTest.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantRoadQueryServiceTest.java
git commit -m "refactor: retire legacy quant execution semantics"
```

## Self-Review

- **Spec coverage:** The plan covers the single-entry API, execution plan model, sync/async engine, Python plan execution, jobs-page simplification, and compatibility cleanup requested by the design spec.
- **Placeholder scan:** No `TODO`, `TBD`, or “implement later” placeholders remain in the tasks.
- **Type consistency:** The plan consistently uses `QuantJobRequest` as the incoming request, `QuantExecutionPlan` as the internal plan, and `QuantExecutionResponse` as the unified response.
