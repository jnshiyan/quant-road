# Quant Task Center Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the quant task center into a two-page product flow where the task center drives daily business decisions and the operations center handles recovery and compatibility tooling.

**Architecture:** Keep backend execution engines unchanged, add focused read-model aggregation for the new task center and operations center, then refactor the frontend into smaller page-level components with a fixed command surface. Reuse existing execute/job APIs, but add new summary/timeline/operations aggregates so the UI can explain what is happening, what the user is waiting for, and what happens after each click.

**Tech Stack:** Vue 2 + Element UI, Node `node:test` frontend tests, Spring Boot controllers/services, JUnit/MockMvc tests, Playwright smoke, PowerShell API smoke.

---

## File Structure

### Backend

- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java`
  - Focused task-center aggregate service for today status, primary task, next action, and recent progress.
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantOperationsCenterService.java`
  - Focused operations aggregate service for blockers, failed batches, worker health, and compatibility toolbox.
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java`
  - Add new read endpoints for task center and operations center.
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`
  - Repoint existing SOP/recovery deep-links from `/quant/jobs` to `/quant/operations` where appropriate.
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantRoadQueryServiceTest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantOperationsCenterServiceTest.java`

### Frontend

- Create: `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
- Create: `ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue`
- Create: `ruoyi-ui/src/views/quant/jobs/components/TaskActionPanel.vue`
- Create: `ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue`
- Create: `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
  - Pure state helpers for today status, primary task selection, next action, wait messaging, and action feedback.
- Create: `ruoyi-ui/src/views/quant/operations/index.vue`
- Create: `ruoyi-ui/src/views/quant/operations/operations-center-state.js`
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
  - Replace mixed “hint cards + button wall + ops sections” with the new command-center layout.
- Modify: `ruoyi-ui/src/api/quant.js`
  - Add new task-center and operations-center aggregate API calls.
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-explain.js`
  - Remove old ambiguous hint generation once the new cards own the experience.
- Modify: `ruoyi-ui/playwright-smoke.cjs`
  - Verify task center first-screen structure and operations center route.
- Modify: `scripts/api-smoke.ps1`
  - Exercise new task-center/operations-center read endpoints and unified execute response.
- Modify: `sql/ruoyi_quant_menu.sql`
  - Add a new `/quant/operations` menu item and keep compatibility actions off the task-center page.
- Create: `ruoyi-ui/tests/jobs-task-center-state.test.cjs`
- Create: `ruoyi-ui/tests/jobs-task-center-layout.test.cjs`
- Create: `ruoyi-ui/tests/operations-center-state.test.cjs`
- Modify: `ruoyi-ui/tests/bundle-structure.test.cjs`
- Modify: `ruoyi-ui/tests/playwright-smoke.test.cjs`

### Docs

- Modify: `docs/superpowers/specs/2026-05-10-quant-task-center-redesign-design.md`
  - Add implementation references after work lands, if needed.

## Task 0: Create a Safe Implementation Workspace

**Files:**
- No product files changed

- [ ] **Step 1: Create a fresh worktree for implementation**

```bash
git worktree add ..\\quant-road-task-center-redesign -b feat/quant-task-center-redesign
```

- [ ] **Step 2: Verify the new worktree is clean before editing**

Run: `git -C ..\\quant-road-task-center-redesign status --short`

Expected: no output

- [ ] **Step 3: Confirm the spec and plan exist in the new worktree**

Run:

```bash
dir ..\\quant-road-task-center-redesign\\docs\\superpowers\\specs\\2026-05-10-quant-task-center-redesign-design.md
dir ..\\quant-road-task-center-redesign\\docs\\superpowers\\plans\\2026-05-10-quant-task-center-redesign.md
```

Expected: both files are present

- [ ] **Step 4: Commit the branch bootstrap only if extra workspace setup files were added**

```bash
git -C ..\\quant-road-task-center-redesign status --short
```

Expected: either clean or only deliberate setup changes

### Task 1: Add Backend Task-Center and Operations-Center Aggregates

**Files:**
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantOperationsCenterService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantOperationsCenterServiceTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantRoadQueryServiceTest.java`

- [ ] **Step 1: Write failing backend tests for the new aggregates**

Add controller expectations like:

```java
mockMvc.perform(get("/quant/data/taskCenterSummary").header("Authorization", "Bearer token"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.todayStatus.code").value("READY_WITH_WARNINGS"))
    .andExpect(jsonPath("$.data.primaryTask.waitingFor").value("run-strategy"))
    .andExpect(jsonPath("$.data.nextAction.code").value("GO_OPERATIONS"));

mockMvc.perform(get("/quant/data/operationsCenterSummary").header("Authorization", "Bearer token"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.topBlocker.layer").value("worker"))
    .andExpect(jsonPath("$.data.toolbox.compatibilityActions[0].code").value("legacyFullDaily"));
```

Add service expectations like:

```java
assertEquals("WARNING", summary.get("todayStatus.code"));
assertEquals("WAIT_CURRENT_TASK", summary.get("nextAction.code"));
assertEquals("sync-daily", summary.get("primaryTask.waitingFor"));
```

- [ ] **Step 2: Run the backend tests to verify they fail for missing endpoints/services**

Run:

```bash
mvn -pl ruoyi-admin "-Dtest=QuantRoadDataControllerTest,QuantTaskCenterServiceTest,QuantOperationsCenterServiceTest,QuantRoadQueryServiceTest" test
```

Expected: FAIL with missing endpoint or missing class/method assertions

- [ ] **Step 3: Implement focused aggregation services and controller endpoints**

Create `QuantTaskCenterService.java` with a payload shape like:

```java
public Map<String, Object> summary() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("todayStatus", Map.of(
        "code", resolveTodayStatus(),
        "reason", resolveTodayStatusReason(),
        "suggestion", resolveTodayStatusSuggestion()
    ));
    payload.put("primaryTask", Map.of(
        "taskName", resolvePrimaryTaskName(),
        "status", resolvePrimaryTaskStatus(),
        "currentStage", resolvePrimaryTaskStage(),
        "waitingFor", resolveWaitingTarget(),
        "expectedOutcome", resolveExpectedOutcome(),
        "requiresManualIntervention", resolveManualInterventionFlag()
    ));
    payload.put("progressEvents", loadRecentProgressEvents());
    payload.put("nextAction", resolveNextAction());
    return payload;
}
```

Create `QuantOperationsCenterService.java` with a payload shape like:

```java
public Map<String, Object> summary() {
    return Map.of(
        "topBlocker", resolveTopBlocker(),
        "recoveryQueue", loadRecoveryQueue(),
        "workerHealth", loadWorkerHealth(),
        "dataIntegrity", loadDataIntegritySummary(),
        "toolbox", loadCompatibilityToolbox()
    );
}
```

Expose them from `QuantRoadDataController.java`:

```java
@GetMapping("/data/taskCenterSummary")
@PreAuthorize("@ss.hasPermi('quant:data:query')")
public AjaxResult taskCenterSummary() {
    return AjaxResult.success(quantTaskCenterService.summary());
}

@GetMapping("/data/operationsCenterSummary")
@PreAuthorize("@ss.hasPermi('quant:data:query')")
public AjaxResult operationsCenterSummary() {
    return AjaxResult.success(quantOperationsCenterService.summary());
}
```

Update `QuantRoadQueryService.java` deep-links so blocker/recovery hints target `/quant/operations` instead of `/quant/jobs`.

- [ ] **Step 4: Run the backend tests to verify the new aggregates pass**

Run:

```bash
mvn -pl ruoyi-admin "-Dtest=QuantRoadDataControllerTest,QuantTaskCenterServiceTest,QuantOperationsCenterServiceTest,QuantRoadQueryServiceTest" test
```

Expected: PASS

- [ ] **Step 5: Commit the backend aggregate slice**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantOperationsCenterService.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantOperationsCenterServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantRoadQueryServiceTest.java
git commit -m "feat: add quant task and operations aggregates"
```

### Task 2: Build Pure Frontend State Helpers for the New UX

**Files:**
- Create: `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
- Create: `ruoyi-ui/src/views/quant/operations/operations-center-state.js`
- Create: `ruoyi-ui/tests/jobs-task-center-state.test.cjs`
- Create: `ruoyi-ui/tests/operations-center-state.test.cjs`

- [ ] **Step 1: Write failing helper tests for task-center state**

Add tests like:

```javascript
test('warning status stays conservative and promotes operations handling', () => {
  const result = buildTaskCenterState({
    todayStatus: { code: 'WARNING', reason: '2 failed shards', suggestion: 'Process warnings first' },
    primaryTask: { taskName: '盘后主流程', status: 'RUNNING', currentStage: 'run-strategy', waitingFor: 'ETF 池 2 个标的计算完成' },
    nextAction: { code: 'GO_OPERATIONS', label: '先处理警告' },
    progressEvents: []
  })

  assert.equal(result.primaryAction.label, '先处理警告')
  assert.match(result.waitingMessage, /ETF 池 2 个标的计算完成/)
})
```

Add tests like:

```javascript
test('operations state exposes blocker-first recovery ordering', () => {
  const result = buildOperationsCenterState({
    topBlocker: { layer: 'worker', title: '无活跃 worker', impact: '阻断执行' },
    recoveryQueue: [{ code: 'recoverExpiredShards', label: '恢复过期分片' }]
  })

  assert.equal(result.primaryRecovery.label, '恢复过期分片')
  assert.equal(result.blockerBadge, '阻断执行')
})
```

- [ ] **Step 2: Run the helper tests to verify they fail**

Run:

```bash
node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs ruoyi-ui/tests/operations-center-state.test.cjs
```

Expected: FAIL because helper modules do not exist yet

- [ ] **Step 3: Implement the pure state helpers**

Create `jobs-task-center-state.js` with exports like:

```javascript
function buildTaskCenterState(payload = {}) {
  const todayStatus = payload.todayStatus || {}
  const primaryTask = payload.primaryTask || {}
  const nextAction = payload.nextAction || {}

  return {
    todayStatusLabel: todayStatus.code || 'EMPTY',
    todayStatusReason: todayStatus.reason || '暂无今日状态',
    todayStatusSuggestion: todayStatus.suggestion || '请刷新状态',
    waitingMessage: primaryTask.waitingFor ? `正在等待：${primaryTask.waitingFor}` : '当前无等待对象',
    primaryAction: resolvePrimaryAction(todayStatus.code, nextAction),
    secondaryActions: resolveSecondaryActions(todayStatus.code, nextAction),
    progressEvents: Array.isArray(payload.progressEvents) ? payload.progressEvents.slice(0, 5) : []
  }
}

module.exports = { buildTaskCenterState }
```

Create `operations-center-state.js` with exports like:

```javascript
function buildOperationsCenterState(payload = {}) {
  const topBlocker = payload.topBlocker || {}
  const queue = Array.isArray(payload.recoveryQueue) ? payload.recoveryQueue : []
  return {
    blockerTitle: topBlocker.title || '暂无阻断',
    blockerBadge: topBlocker.impact || '无影响',
    primaryRecovery: queue[0] || null,
    secondaryRecoveries: queue.slice(1, 3)
  }
}

module.exports = { buildOperationsCenterState }
```

- [ ] **Step 4: Run the helper tests to verify they pass**

Run:

```bash
node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs ruoyi-ui/tests/operations-center-state.test.cjs
```

Expected: PASS

- [ ] **Step 5: Commit the helper slice**

```bash
git add ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js ruoyi-ui/src/views/quant/operations/operations-center-state.js ruoyi-ui/tests/jobs-task-center-state.test.cjs ruoyi-ui/tests/operations-center-state.test.cjs
git commit -m "feat: add quant task and operations state helpers"
```

### Task 3: Refactor the Task Center Page Into the New Command-Center Layout

**Files:**
- Create: `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
- Create: `ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue`
- Create: `ruoyi-ui/src/views/quant/jobs/components/TaskActionPanel.vue`
- Create: `ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Modify: `ruoyi-ui/src/api/quant.js`
- Create: `ruoyi-ui/tests/jobs-task-center-layout.test.cjs`
- Modify: `ruoyi-ui/tests/bundle-structure.test.cjs`

- [ ] **Step 1: Write a failing layout contract test for the new page structure**

Add source-level assertions like:

```javascript
const source = fs.readFileSync(path.join(__dirname, '..', 'src/views/quant/jobs/index.vue'), 'utf8')
assert.match(source, /<today-status-card/)
assert.match(source, /<primary-task-card/)
assert.match(source, /<task-action-panel/)
assert.match(source, /<task-progress-timeline/)
assert.doesNotMatch(source, /日常运营动作/)
assert.doesNotMatch(source, /高级排障动作/)
```

- [ ] **Step 2: Run the layout contract test to verify it fails**

Run:

```bash
node --test ruoyi-ui/tests/jobs-task-center-layout.test.cjs
```

Expected: FAIL because the page still uses the old mixed layout

- [ ] **Step 3: Implement the task-center page split and per-action feedback**

Add API calls in `quant.js`:

```javascript
export function getTaskCenterSummary() {
  return request({ url: '/quant/data/taskCenterSummary', method: 'get' })
}

export function getOperationsCenterSummary() {
  return request({ url: '/quant/data/operationsCenterSummary', method: 'get' })
}
```

Refactor `index.vue` so the first screen becomes:

```vue
<today-status-card :summary="taskCenterSummary" />
<primary-task-card :primary-task="taskCenterSummary.primaryTask" :events="taskCenterSummary.progressEvents" />
<task-action-panel
  :primary-action="taskCenterUi.primaryAction"
  :secondary-actions="taskCenterUi.secondaryActions"
  :pending-action="pendingActionKey"
  @run-primary="handlePrimaryAction"
  @run-secondary="handleSecondaryAction"
/>
<task-progress-timeline :events="taskCenterUi.progressEvents" :technical-summary="taskCenterSummary.technicalSummary" />
```

Update action handling to track one pending action:

```javascript
data() {
  return {
    pendingActionKey: '',
    actionFeedback: null,
    taskCenterSummary: {}
  }
}

async runActionWithFeedback(actionKey, runner) {
  this.pendingActionKey = actionKey
  this.actionFeedback = {
    phase: 'submitting',
    actionKey,
    startedAt: new Date().toLocaleString(),
    message: `正在提交：${this.friendlyActionName(actionKey)}`
  }
  try {
    const response = await runner()
    this.actionFeedback = normalizeActionFeedback(response.data)
  } finally {
    this.pendingActionKey = ''
  }
}
```

Use `this.$confirm` to show a standardized pre-submit confirmation panel instead of firing immediately.

- [ ] **Step 4: Run the frontend contract tests and existing helper tests**

Run:

```bash
node --test ruoyi-ui/tests/jobs-task-center-layout.test.cjs ruoyi-ui/tests/jobs-task-center-state.test.cjs ruoyi-ui/tests/bundle-structure.test.cjs
```

Expected: PASS

- [ ] **Step 5: Commit the task-center UI slice**

```bash
git add ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue ruoyi-ui/src/views/quant/jobs/components/TaskActionPanel.vue ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue ruoyi-ui/src/views/quant/jobs/index.vue ruoyi-ui/src/api/quant.js ruoyi-ui/tests/jobs-task-center-layout.test.cjs ruoyi-ui/tests/bundle-structure.test.cjs
git commit -m "feat: rebuild quant task center as command center"
```

### Task 4: Add the Operations Center Page and Move Recovery/Compatibility There

**Files:**
- Create: `ruoyi-ui/src/views/quant/operations/index.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Modify: `ruoyi-ui/src/api/quant.js`
- Modify: `sql/ruoyi_quant_menu.sql`
- Modify: `ruoyi-ui/playwright-smoke.cjs`
- Modify: `ruoyi-ui/tests/playwright-smoke.test.cjs`
- Modify: `ruoyi-ui/tests/bundle-structure.test.cjs`

- [ ] **Step 1: Write failing source and smoke assertions for the operations center**

Add structure assertions like:

```javascript
assert.ok(fs.existsSync(path.join(__dirname, '..', 'src/views/quant/operations/index.vue')))
assert.match(smokeSource, /path: '\/quant\/operations'/)
assert.match(smokeSource, /markers: \['运维中心'/)
```

Add bundle assertions like:

```javascript
assert.ok(files.includes('src/views/quant/operations/index.vue'))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
node --test ruoyi-ui/tests/playwright-smoke.test.cjs ruoyi-ui/tests/bundle-structure.test.cjs
```

Expected: FAIL because the operations page and smoke route do not exist yet

- [ ] **Step 3: Implement the operations page and menu**

Create `quant/operations/index.vue` with sections like:

```vue
<el-card><span>阻断总览</span></el-card>
<el-card><span>任务恢复</span></el-card>
<el-card><span>异步执行器</span></el-card>
<el-card><span>数据完整性</span></el-card>
<el-card><span>高级工具箱</span></el-card>
```

Move from the task-center page into operations:

- worker summary
- failed batch recovery
- async shard recovery
- compatibility actions

Add menu SQL entries like:

```sql
-- Child menu: 运维中心 (/quant/operations)
('运维中心', v_quant_root_id, 4, 'operations', 'quant/operations/index', NULL, 'QuantOperationsCenter', '1', '0', 'C', '0', '0', '', 'tool', 'admin', NOW(), 'Quant 运维恢复中心')
```

Update smoke scenarios to include:

```javascript
{
  name: '量化-运维中心',
  path: '/quant/operations',
  markers: ['运维中心', '阻断总览', '任务恢复', '高级工具箱']
}
```

- [ ] **Step 4: Run the source, bundle, and smoke contract tests**

Run:

```bash
node --test ruoyi-ui/tests/playwright-smoke.test.cjs ruoyi-ui/tests/bundle-structure.test.cjs
node ruoyi-ui/playwright-smoke.cjs
```

Expected: PASS

- [ ] **Step 5: Commit the operations-center slice**

```bash
git add ruoyi-ui/src/views/quant/operations/index.vue ruoyi-ui/src/views/quant/jobs/index.vue ruoyi-ui/src/api/quant.js sql/ruoyi_quant_menu.sql ruoyi-ui/playwright-smoke.cjs ruoyi-ui/tests/playwright-smoke.test.cjs ruoyi-ui/tests/bundle-structure.test.cjs
git commit -m "feat: split quant operations center from task center"
```

### Task 5: Wire End-to-End Verification and Regression Coverage

**Files:**
- Modify: `scripts/api-smoke.ps1`
- Modify: `ruoyi-ui/playwright-smoke.cjs`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadJobControllerTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantJobPlannerIntegrationTest.java`

- [ ] **Step 1: Write failing verification expectations for the unified task flow**

Add API smoke expectations like:

```powershell
@{ name = 'quant.data.taskCenterSummary'; method = 'GET'; url = "$BaseUrl/quant/data/taskCenterSummary" },
@{ name = 'quant.data.operationsCenterSummary'; method = 'GET'; url = "$BaseUrl/quant/data/operationsCenterSummary" }
```

Update integration tests with assertions like:

```java
assertEquals("async", response.getResolvedExecutionMode());
assertTrue(response.getPlanSummary().contains("run-strategy"));
assertNotNull(response.getExecutionId());
```

- [ ] **Step 2: Run verification commands to confirm the new coverage fails first**

Run:

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/api-smoke.ps1
mvn -pl ruoyi-admin "-Dtest=QuantRoadJobControllerTest,QuantJobPlannerIntegrationTest" test
```

Expected: FAIL until the new task-center/operations-center assertions are implemented

- [ ] **Step 3: Implement the verification updates**

Update `api-smoke.ps1` to:

- call `taskCenterSummary`
- call `operationsCenterSummary`
- submit unified `/quant/jobs/execute`
- assert returned `executionId` and visible queue state

Ensure Playwright smoke:

- verifies task center first-screen markers
- verifies the task-center execute confirm/submit path
- verifies operations-center availability

- [ ] **Step 4: Run the full regression suite**

Run:

```bash
node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs ruoyi-ui/tests/jobs-task-center-layout.test.cjs ruoyi-ui/tests/operations-center-state.test.cjs ruoyi-ui/tests/bundle-structure.test.cjs ruoyi-ui/tests/playwright-smoke.test.cjs
node ruoyi-ui/playwright-smoke.cjs
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/api-smoke.ps1
mvn -pl ruoyi-admin "-Dtest=QuantRoadDataControllerTest,QuantTaskCenterServiceTest,QuantOperationsCenterServiceTest,QuantRoadQueryServiceTest,QuantRoadJobControllerTest,QuantJobPlannerIntegrationTest" test
```

Expected: PASS on all commands

- [ ] **Step 5: Commit the verification slice**

```bash
git add scripts/api-smoke.ps1 ruoyi-ui/playwright-smoke.cjs ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadJobControllerTest.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantJobPlannerIntegrationTest.java ruoyi-ui/tests/jobs-task-center-state.test.cjs ruoyi-ui/tests/jobs-task-center-layout.test.cjs ruoyi-ui/tests/operations-center-state.test.cjs ruoyi-ui/tests/playwright-smoke.test.cjs
git commit -m "test: cover quant task center redesign flow"
```

## Self-Review

- Spec coverage:
  - Dual-page split is covered in Tasks 3 and 4.
  - Today status, primary task, conservative warning behavior, and observable waiting are covered in Tasks 1, 2, and 3.
  - Operations-center recovery path and compatibility relocation are covered in Tasks 1 and 4.
  - Standard confirmation and submit feedback are covered in Task 3.
  - Updated smoke and regression checks are covered in Task 5.
- Placeholder scan:
  - No `TODO`/`TBD` placeholders remain.
  - Each task includes concrete files, commands, and expected outcomes.
- Type consistency:
  - New backend read endpoints are named `taskCenterSummary` and `operationsCenterSummary`.
  - New frontend helper entrypoints are `buildTaskCenterState` and `buildOperationsCenterState`.
  - The primary page split uses `quant/jobs` for task center and `quant/operations` for operations center consistently.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-10-quant-task-center-redesign.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
