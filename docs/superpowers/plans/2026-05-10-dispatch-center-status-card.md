# Dispatch Center Status Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the dispatch-center status card so it leads with the next action, clearly states whether the user can continue, and keeps `WARNING` scenarios operable with explicit dual-button choices.

**Architecture:** Extend the existing `QuantTaskCenterService.summary()` payload with explicit status-card fields instead of relying on the UI to infer behavior from `code / reason / suggestion`. Update the task-center state adapter to consume the new contract, then redesign `TodayStatusCard.vue` into the approved “B: action + can-continue dual-layer” card while keeping the rest of `/quant/jobs` intact.

**Tech Stack:** Java 17, Spring service aggregation, Vue 2, Element UI, Node built-in test runner, JUnit 5, AssertJ, Mockito.

---

## File Map

- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java`
  - Own the status-card contract and button mapping.
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java`
  - Lock the new contract and warning/blocked behavior.
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
  - Normalize the new backend payload for the page-level view model.
- Modify: `ruoyi-ui/tests/jobs-task-center-state.test.cjs`
  - Lock state-adapter behavior for `WARNING`, `RUNNING`, and `BLOCKED`.
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
  - Render the approved dual-layer card with fixed action slots.
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
  - Pass the normalized actions into the card and keep the existing page wiring coherent.

## Task 1: Define Explicit Status Card Contract In Backend

**Files:**
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java`

- [ ] **Step 1: Write the failing backend contract test**

Add a test that locks the new status-card shape for a warning scenario.

```java
@Test
void summaryShouldExposeExplicitStatusCardContractForWarning()
{
    QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
    when(queryService.jobReadiness(null)).thenReturn(Map.of(
            "status", "READY_WITH_WARNINGS",
            "batchId", 18L,
            "message", "基础标的信息本次使用了库内回退，今日结果可继续查看，但建议后续补跑 sync-basic 确认标的信息是否最新。",
            "canEnterDashboard", true,
            "dataIntegrityStatus", "WARNING",
            "dataIntegrityCategory", "FALLBACK_BASIC"));
    when(queryService.jobSteps(18L)).thenReturn(List.of());
    when(queryService.jobSopHints(null)).thenReturn(List.of(
            Map.of(
                    "code", "goDashboard",
                    "level", "success",
                    "title", "进入量化看板",
                    "suggestedAction", "先去看板确认市场状态、信号、持仓和风险预警。",
                    "targetPage", "/quant/dashboard"),
            Map.of(
                    "code", "checkBasicFallback",
                    "level", "warning",
                    "title", "确认基础标的回退",
                    "suggestedAction", "今日结果可以继续查看；若你需要最新基础标信息，请在运维中心补跑 sync-basic。",
                    "targetPage", "/quant/operations")));
    when(queryService.asyncWorkerSummary()).thenReturn(Map.of("status", "ACTIVE"));
    when(queryService.asyncJobs(5)).thenReturn(List.of());
    when(queryService.dispatchDefinitions()).thenReturn(List.of());
    when(queryService.dispatchHistory(1, 10, null, null)).thenReturn(Map.of("rows", List.of(), "total", 0));
    when(queryService.nextScheduledDispatch()).thenReturn(Map.of());

    QuantTaskCenterService service = new QuantTaskCenterService(queryService);

    Map<String, Object> payload = service.summary();

    @SuppressWarnings("unchecked")
    Map<String, Object> todayStatus = (Map<String, Object>) payload.get("todayStatus");

    assertThat(todayStatus).containsEntry("headlineAction", "确认基础标的回退");
    assertThat(todayStatus).containsEntry("statusCode", "WARNING");
    assertThat(todayStatus).containsEntry("statusLabel", "警告");
    assertThat(todayStatus).containsEntry("canContinue", true);
    assertThat(todayStatus).containsEntry("continueLabel", "可以继续查看");
    assertThat(todayStatus).containsEntry("urgency", "before_decision");
    assertThat(todayStatus).containsKey("primaryAction");
    assertThat(todayStatus).containsKey("secondaryAction");
}
```

- [ ] **Step 2: Run the backend test and verify it fails for missing fields**

Run: `mvn -pl ruoyi-admin "-Dtest=QuantTaskCenterServiceTest#summaryShouldExposeExplicitStatusCardContractForWarning" test`

Expected: FAIL because `todayStatus` does not yet contain `headlineAction / statusCode / canContinue / continueLabel / urgency / primaryAction / secondaryAction`.

- [ ] **Step 3: Implement the minimal backend contract**

Update `todayStatus(...)` in `QuantTaskCenterService` to build a dedicated status-card map.

```java
private Map<String, Object> todayStatus(
        Map<String, Object> readiness,
        List<Map<String, Object>> steps,
        List<Map<String, Object>> hints,
        Map<String, Object> workerHealth)
{
    String statusCode = normalizeTodayStatus(readiness, workerHealth);
    Map<String, Object> preferredHint = preferredHint(
            hints,
            "BLOCKED".equals(readiness.get("status")) || "READY_WITH_WARNINGS".equals(readiness.get("status")));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("code", statusCode);
    result.put("label", todayStatusLabel(statusCode));
    result.put("reason", stringValue(readiness.get("message"), "暂无今日任务状态。"));
    result.put("suggestion", resolveSuggestion(steps, readiness, hints));

    result.put("headlineAction", stringValue(preferredHint.get("title"), defaultHeadlineAction(statusCode)));
    result.put("statusCode", statusCode);
    result.put("statusLabel", todayStatusLabel(statusCode));
    result.put("canContinue", canContinue(statusCode));
    result.put("continueLabel", continueLabel(statusCode));
    result.put("urgency", urgency(statusCode, readiness));
    result.put("primaryAction", statusPrimaryAction(statusCode, preferredHint));
    result.put("secondaryAction", statusSecondaryAction(statusCode));
    return result;
}
```

Add focused helpers only:

```java
private boolean canContinue(String statusCode)
{
    return !"BLOCKED".equals(statusCode);
}

private String continueLabel(String statusCode)
{
    return switch (statusCode)
    {
        case "OPERABLE" -> "可以继续";
        case "RUNNING" -> "暂不建议重复提交";
        case "BLOCKED" -> "当前不可继续";
        default -> "可以继续查看";
    };
}
```

- [ ] **Step 4: Add blocked/running expectations in tests**

Extend `QuantTaskCenterServiceTest` with one blocked-focused assertion and one running-focused assertion.

```java
assertThat(todayStatus)
        .containsEntry("statusCode", "BLOCKED")
        .containsEntry("canContinue", false)
        .containsEntry("continueLabel", "当前不可继续");
assertThat(todayStatus.get("secondaryAction")).isNull();
```

```java
assertThat(todayStatus)
        .containsEntry("statusCode", "RUNNING")
        .containsEntry("headlineAction", "等待当前任务完成")
        .containsEntry("continueLabel", "暂不建议重复提交");
```

- [ ] **Step 5: Run the backend contract tests and verify they pass**

Run: `mvn -pl ruoyi-admin "-Dtest=QuantTaskCenterServiceTest" test`

Expected: PASS with 0 failures.

- [ ] **Step 6: Commit the backend contract**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantTaskCenterService.java ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantTaskCenterServiceTest.java
git commit -m "feat: add explicit dispatch status card contract"
```

## Task 2: Update Frontend State Adapter To Use The New Contract

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
- Test: `ruoyi-ui/tests/jobs-task-center-state.test.cjs`

- [ ] **Step 1: Write the failing frontend adapter test**

Add a test that consumes the new backend card fields.

```javascript
test('warning card state keeps action first and continueability second', () => {
  const result = buildTaskCenterState({
    todayStatus: {
      statusCode: 'WARNING',
      statusLabel: '警告',
      headlineAction: '确认基础标的回退',
      canContinue: true,
      continueLabel: '可以继续查看',
      reason: '基础标的信息本次使用了库内回退。',
      urgency: 'before_decision',
      primaryAction: { code: 'GO_OPERATIONS', label: '去运维中心处理', targetPage: '/quant/operations' },
      secondaryAction: { code: 'GO_DASHBOARD', label: '继续进入看板', targetPage: '/quant/dashboard' }
    }
  })

  assert.equal(result.todayStatusHeadlineAction, '确认基础标的回退')
  assert.equal(result.todayStatusContinueLabel, '可以继续查看')
  assert.equal(result.primaryAction.label, '去运维中心处理')
  assert.equal(result.secondaryActions[0].label, '继续进入看板')
})
```

- [ ] **Step 2: Run the adapter test and verify it fails**

Run: `node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs`

Expected: FAIL because `buildTaskCenterState` does not expose `todayStatusHeadlineAction` or consume backend-provided button actions.

- [ ] **Step 3: Implement minimal adapter changes**

Update `buildTaskCenterState(...)` to prefer the new contract first and keep old fields as fallback.

```javascript
function buildTaskCenterState(payload = {}) {
  const todayStatus = payload.todayStatus || {}
  const primaryTask = payload.primaryTask || {}
  const nextAction = payload.nextAction || {}

  const normalizedPrimary = todayStatus.primaryAction || resolvePrimaryAction(todayStatus, nextAction)
  const normalizedSecondary = todayStatus.secondaryAction
    ? [todayStatus.secondaryAction].filter(Boolean)
    : resolveSecondaryActions(todayStatus, normalizedPrimary)

  return {
    todayStatusCode: todayStatus.statusCode || todayStatus.code || 'WARNING',
    todayStatusLabel: todayStatus.statusLabel || statusLabel(todayStatus),
    todayStatusHeadlineAction: todayStatus.headlineAction || normalizedPrimary.label,
    todayStatusContinueLabel: todayStatus.continueLabel || '可以继续查看',
    todayStatusCanContinue: todayStatus.canContinue !== false,
    todayStatusReason: todayStatus.reason || '暂无今日状态',
    todayStatusUrgency: todayStatus.urgency || 'later',
    primaryAction: normalizeAction(normalizedPrimary),
    secondaryActions: normalizedSecondary.map(action => normalizeAction(action))
  }
}
```

- [ ] **Step 4: Add blocked-path adapter assertions**

Extend `jobs-task-center-state.test.cjs` with a blocked example.

```javascript
test('blocked card hides continue action', () => {
  const result = buildTaskCenterState({
    todayStatus: {
      statusCode: 'BLOCKED',
      statusLabel: '阻断',
      headlineAction: '先处理阻断问题',
      canContinue: false,
      continueLabel: '当前不可继续',
      primaryAction: { code: 'GO_OPERATIONS', label: '去运维中心处理', targetPage: '/quant/operations' },
      secondaryAction: null
    }
  })

  assert.equal(result.todayStatusCanContinue, false)
  assert.equal(result.secondaryActions.length, 0)
})
```

- [ ] **Step 5: Run adapter tests and verify they pass**

Run: `node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs`

Expected: PASS with all tests green.

- [ ] **Step 6: Commit the adapter changes**

```bash
git add ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js ruoyi-ui/tests/jobs-task-center-state.test.cjs
git commit -m "feat: normalize dispatch status card state"
```

## Task 3: Render The Approved Dual-Layer Status Card

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Test: `ruoyi-ui/tests/jobs-task-center-state.test.cjs`

- [ ] **Step 1: Add a failing UI-state test for card copy wiring**

Append a test that verifies the state adapter exposes everything the card needs.

```javascript
test('status card view model exposes headline, continue label, and dual actions', () => {
  const result = buildTaskCenterState({
    todayStatus: {
      statusCode: 'WARNING',
      statusLabel: '警告',
      headlineAction: '确认基础标的回退',
      canContinue: true,
      continueLabel: '可以继续查看',
      reason: '本次基础标信息来自库内回退。',
      primaryAction: { code: 'GO_OPERATIONS', label: '去运维中心处理', targetPage: '/quant/operations' },
      secondaryAction: { code: 'GO_DASHBOARD', label: '继续进入看板', targetPage: '/quant/dashboard' }
    }
  })

  assert.equal(result.todayStatusHeadlineAction, '确认基础标的回退')
  assert.equal(result.todayStatusContinueLabel, '可以继续查看')
  assert.equal(result.secondaryActions[0].targetPage, '/quant/dashboard')
})
```

- [ ] **Step 2: Run the UI-state test and verify it fails before the card update**

Run: `node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs`

Expected: FAIL if the adapter still does not expose the exact card-view properties.

- [ ] **Step 3: Rewrite `TodayStatusCard.vue` to the approved layout**

Replace the current single-title + two-text-block card with an action-first card.

```vue
<template>
  <el-card shadow="never" class="today-status-card">
    <div class="card-head">
      <div>
        <div class="eyebrow">建议动作</div>
        <div class="headline">{{ headlineAction }}</div>
      </div>
      <el-tag size="mini" :type="statusType">{{ statusLabel }}</el-tag>
    </div>

    <div class="continue-panel" :class="{ blocked: !canContinue }">
      <div class="continue-label">现在能否继续</div>
      <div class="continue-value">{{ continueLabel }}</div>
    </div>

    <div class="detail">{{ statusReason }}</div>

    <div class="action-row">
      <el-button type="primary" @click="$emit('run-primary')">{{ primaryActionLabel }}</el-button>
      <el-button v-if="secondaryActionLabel" plain :disabled="!canContinue" @click="$emit('run-secondary')">
        {{ secondaryActionLabel }}
      </el-button>
    </div>
  </el-card>
</template>
```

Use computed props sourced from the normalized `taskCenterUi` fields:

```javascript
computed: {
  headlineAction() {
    return this.summary.todayStatus.headlineAction || this.summary.todayStatus.label || '请先确认状态'
  },
  continueLabel() {
    return this.summary.todayStatus.continueLabel || '可以继续查看'
  }
}
```

- [ ] **Step 4: Pass normalized actions from `index.vue` into the card**

Replace:

```vue
<today-status-card :summary="taskCenterSummary" />
```

With:

```vue
<today-status-card
  :summary="taskCenterSummary"
  :primary-action="taskCenterUi.primaryAction"
  :secondary-action="taskCenterUi.secondaryActions[0]"
  @run-primary="handlePrimaryAction"
  @run-secondary="handleSecondaryAction(taskCenterUi.secondaryActions[0])"
/>
```

And update the component props accordingly:

```javascript
props: {
  summary: { type: Object, default: () => ({}) },
  primaryAction: { type: Object, default: () => ({}) },
  secondaryAction: { type: Object, default: null }
}
```

- [ ] **Step 5: Run the frontend state tests and a staging build**

Run:

```bash
node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs
```

Expected: PASS

Run:

```bash
npm run build:stage
```

Workdir: `ruoyi-ui`

Expected: build completes successfully.

- [ ] **Step 6: Commit the UI update**

```bash
git add ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue ruoyi-ui/src/views/quant/jobs/index.vue ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js ruoyi-ui/tests/jobs-task-center-state.test.cjs
git commit -m "feat: redesign dispatch center status card"
```

## Task 4: Final Verification And Manual API Check

**Files:**
- Modify: none
- Test: existing backend and frontend tests

- [ ] **Step 1: Run backend verification suite**

Run: `mvn -pl ruoyi-admin "-Dtest=QuantTaskCenterServiceTest,QuantRoadQueryServiceTest,QuantOperationsCenterServiceTest" test`

Expected: PASS with 0 failures.

- [ ] **Step 2: Run frontend verification suite**

Run: `node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs`

Expected: PASS.

- [ ] **Step 3: Repackage and restart the backend**

Run:

```bash
mvn -pl ruoyi-admin -am -DskipTests package
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Verify the live API contract**

Run:

```powershell
$loginBody = @{ username = 'admin'; password = 'admin123' } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/login' -ContentType 'application/json' -Body $loginBody
$headers = @{ Authorization = "Bearer $($login.token)" }
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/quant/data/taskCenterSummary' -Headers $headers | ConvertTo-Json -Depth 8
```

Expected: `todayStatus` contains `headlineAction`, `statusCode`, `statusLabel`, `canContinue`, `continueLabel`, plus the correct primary/secondary actions for the current live scenario.

- [ ] **Step 5: Commit the verification checkpoint**

```bash
git add .
git commit -m "test: verify dispatch center status card flow"
```

## Self-Review

- Spec coverage check:
  - Action-first card: Task 3
  - Continueability layer: Tasks 1, 2, 3
  - `WARNING` fixed dual buttons: Tasks 1, 2, 3
  - `BLOCKED` no continue path: Tasks 1, 2
  - Stable backend contract: Task 1
  - Verification against live API: Task 4
- Placeholder scan:
  - No `TODO`, `TBD`, or “add appropriate handling” placeholders remain.
- Type consistency:
  - Contract names stay aligned as `headlineAction / statusCode / statusLabel / canContinue / continueLabel / urgency / primaryAction / secondaryAction`.
