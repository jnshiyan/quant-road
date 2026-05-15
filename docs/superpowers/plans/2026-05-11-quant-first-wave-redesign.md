# Quant First-Wave Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refocus the quant module first-wave pages so users can understand today's next action, dispatch status, and execution anomalies at a glance.

**Architecture:** Keep existing state builders and route contracts, and reshape the page templates around decision-first blocks. Down-sink detailed tables and verbose guidance, while preserving observability on dispatch detail and actionability on execution.

**Tech Stack:** Vue 2 + Element UI, Node test runner (`node --test`), existing quant view state helpers.

---

### Task 1: Lock new page responsibilities with tests

**Files:**
- Modify: `ruoyi-ui/tests/dashboard-route.test.cjs`
- Modify: `ruoyi-ui/tests/jobs-route.test.cjs`
- Modify: `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`
- Test: `ruoyi-ui/tests/dashboard-route.test.cjs`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`

- [ ] Add assertions for the new dashboard decision-home structure.
- [ ] Add assertions for dispatch center wording that emphasize unified scheduling and current-task tracking.
- [ ] Add assertions for dispatch detail and execution wording that highlight waiting state, current object, and next step.
- [ ] Run the focused test files and verify they fail before implementation.

### Task 2: Simplify dashboard into a decision homepage

**Files:**
- Modify: `ruoyi-ui/src/views/quant/dashboard/index.vue`
- Test: `ruoyi-ui/tests/dashboard-route.test.cjs`

- [ ] Replace the oversized middle and lower sections with a compact first-screen structure: today's status, today's primary action, three next-step entry cards, and object-layer summary.
- [ ] Keep existing navigation methods and derived state, but remove or down-sink detailed tables from the dashboard template.
- [ ] Preserve links into dispatch center, execution, review, symbols, and backtest.
- [ ] Re-run the dashboard-focused test file.

### Task 3: Refocus dispatch center around one control hub

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`

- [ ] Tighten the hero and first-screen sections so the page reads as one unified scheduling hub.
- [ ] Keep current task, next scheduled dispatch, primary actions, and recent dispatches visible.
- [ ] Move heavy definition/history sections lower and make the top of page more decision-oriented.
- [ ] Re-run the jobs and hero consistency tests.

### Task 4: Reduce duplication in dispatch detail and strengthen run-state feedback

**Files:**
- Modify: `ruoyi-ui/src/views/quant/dispatch-detail/index.vue`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`

- [ ] Merge duplicate status storytelling so the page has one clear run-state banner plus supporting sections.
- [ ] Keep current stage, current object, waiting state, latest log, next step, shard progress, and result sections.
- [ ] Re-run related structure tests.

### Task 5: Make execution page anomaly-first instead of form-first

**Files:**
- Modify: `ruoyi-ui/src/views/quant/execution/index.vue`
- Modify: `ruoyi-ui/tests/quant-page-header-copy.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-header-copy.test.cjs`

- [ ] Reorder the execution page so today's closure status and anomaly priorities lead the page.
- [ ] Keep manual writeback available but visually secondary to anomaly handling.
- [ ] Re-run the focused execution/page copy tests.

### Task 6: Verify the first-wave redesign

**Files:**
- Test: `ruoyi-ui/tests/dashboard-route.test.cjs`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-header-copy.test.cjs`

- [ ] Run the focused quant page test pack.
- [ ] Run the quant smoke script.
- [ ] If verification is green, prepare a concise implementation summary and remaining follow-ups.
