# Execution Closure Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reconciliation summary, execution match candidates, manual match confirmation, and position sync result so the execution workflow can close the signal-to-position loop.

**Architecture:** Keep the current Java controller/query-service structure and derive the new execution-closure views from existing `trade_signal`, `execution_record`, `signal_execution_feedback`, and `position` data. Implement manual confirmation in Java by binding `execution_record.signal_id`, marking the signal executed, and re-running execution feedback through the existing Python command path.

**Tech Stack:** Spring Boot, JdbcTemplate, Mockito/JUnit 5, Vue 2 + Element UI, existing quant Python CLI integration

---

## File Map

- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadJobController.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadPythonService.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantExecutionMatchConfirmRequest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantRoadQueryServiceTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadJobControllerTest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java`
- Modify: `ruoyi-ui/src/api/quant.js`
- Modify: `ruoyi-ui/src/views/quant/execution/index.vue`
- Modify: `ruoyi-ui/src/views/quant/dashboard/index.vue`

### Task 1: Backend Query Contract

**Files:**
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/service/quant/QuantRoadQueryServiceTest.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`

- [ ] Write failing tests for reconciliation summary, match candidates, and position sync result.
- [ ] Run `mvn -pl ruoyi-admin -Dtest=QuantRoadQueryServiceTest test` and confirm the new tests fail for missing methods.
- [ ] Implement the minimal query-service methods and helper mapping logic.
- [ ] Re-run `mvn -pl ruoyi-admin -Dtest=QuantRoadQueryServiceTest test` and confirm they pass.

### Task 2: Match Confirmation API

**Files:**
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/domain/quant/QuantExecutionMatchConfirmRequest.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadQueryService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/service/quant/QuantRoadPythonService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadJobController.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadJobControllerTest.java`

- [ ] Write a failing controller test for `POST /quant/jobs/confirmExecutionMatch`.
- [ ] Run `mvn -pl ruoyi-admin -Dtest=QuantRoadJobControllerTest test` and confirm the test fails.
- [ ] Implement request DTO, confirmation service method, feedback refresh call, and controller endpoint.
- [ ] Re-run `mvn -pl ruoyi-admin -Dtest=QuantRoadJobControllerTest test` and confirm they pass.

### Task 3: Read APIs

**Files:**
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/quant/QuantRoadDataControllerTest.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/quant/QuantRoadDataController.java`

- [ ] Write failing controller tests for `executionReconciliationSummary`, `executionMatchCandidates`, and `positionSyncResult`.
- [ ] Run `mvn -pl ruoyi-admin -Dtest=QuantRoadDataControllerTest test` and confirm the tests fail.
- [ ] Implement the new read endpoints with existing permission patterns.
- [ ] Re-run `mvn -pl ruoyi-admin -Dtest=QuantRoadDataControllerTest test` and confirm they pass.

### Task 4: Frontend Integration

**Files:**
- Modify: `ruoyi-ui/src/api/quant.js`
- Modify: `ruoyi-ui/src/views/quant/execution/index.vue`
- Modify: `ruoyi-ui/src/views/quant/dashboard/index.vue`

- [ ] Add API wrappers for the new execution-closure endpoints.
- [ ] Extend the execution page with reconciliation summary, unmatched execution actions, match-confirm dialog, and position-sync card.
- [ ] Update the dashboard execution summary consumer to read the new reconciliation summary fields.
- [ ] Run the relevant frontend verification command and confirm the page compiles.

### Task 5: Verification

**Files:**
- No code changes

- [ ] Run `mvn -pl ruoyi-admin -Dtest=QuantRoadQueryServiceTest,QuantRoadDataControllerTest,QuantRoadJobControllerTest test`.
- [ ] Run the targeted frontend check available in this repo and confirm there are no new build errors.
- [ ] Review the changed files for contract consistency between backend field names and frontend consumers.

## Self Review

- Spec coverage: summary, candidates, confirmation, sync result, execution page, and dashboard summary are all mapped to tasks.
- Placeholder scan: no `TODO` or deferred implementation markers remain.
- Type consistency: planned field names use camelCase at the API boundary to match existing JSON serialization conventions.
