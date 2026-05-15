# Quant Dispatch And Dashboard Product Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the quant dashboard and dispatch flow so users can immediately understand today's action, the difference between manual and auto dispatch, and the live state of any running job.

**Architecture:** Keep the existing route split (`dashboard`, `jobs`, `dispatch-manual`, `dispatch-auto`, `dispatch-detail`) but reorganize each page around a single primary responsibility. Centralize user-facing status wording in page-state builders and shared view-model helpers so the same job summary, waiting reason, time-range summary, and action text appear consistently across pages.

**Tech Stack:** Vue 2, Element UI, existing `ruoyi-ui/src/api/quant.js` APIs, Playwright smoke tests in `ruoyi-ui/playwright-smoke.cjs`

---

## File Structure

### Existing files to modify

- `ruoyi-ui/src/views/quant/dashboard/index.vue`
- `ruoyi-ui/src/views/quant/dashboard/dashboard-page-state.js`
- `ruoyi-ui/src/views/quant/jobs/index.vue`
- `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
- `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
- `ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue`
- `ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue`
- `ruoyi-ui/src/views/quant/jobs/components/JobStatusCard.vue`
- `ruoyi-ui/src/views/quant/dispatch-manual/index.vue`
- `ruoyi-ui/src/views/quant/dispatch-manual/manual-dispatch-submit-state.js`
- `ruoyi-ui/src/views/quant/dispatch-auto/index.vue`
- `ruoyi-ui/src/views/quant/dispatch-detail/index.vue`
- `ruoyi-ui/src/views/quant/dispatch-detail/dispatch-detail-state.js`
- `ruoyi-ui/src/views/quant/dispatch-shared/ManualDispatchSummaryCard.vue`
- `ruoyi-ui/playwright-smoke.cjs`

### New files to create

- `ruoyi-ui/src/views/quant/jobs/job-observability.js`
- `ruoyi-ui/src/views/quant/dashboard/dashboard-object-layer.js`
- `ruoyi-ui/tests/unit/quant/job-observability.spec.js`
- `ruoyi-ui/tests/unit/quant/jobs-task-center-state.spec.js`
- `ruoyi-ui/tests/unit/quant/dispatch-detail-state.spec.js`

### Responsibility split

- `job-observability.js` owns shared user-facing formatting for running-job summaries.
- `dashboard-object-layer.js` owns the three-object summary model for `指数 / ETF / 个股`.
- `jobs-task-center-state.js` owns dispatch-center view model decisions and action priority.
- `dispatch-detail-state.js` owns running/completed/error detail wording and next-step text.
- `manual-dispatch-submit-state.js` owns submit feedback wording before and after `jobId` creation.
- Page `.vue` files stay focused on layout and event wiring, not business wording assembly.

### Test strategy

- Add unit tests for the new shared observability formatter and state builders.
- Extend Playwright smoke coverage so the redesigned pages are validated by visible marker text and basic dispatch flow assertions.

## Task 1: Shared Observability Contract

**Files:**
- Create: `ruoyi-ui/src/views/quant/jobs/job-observability.js`
- Create: `ruoyi-ui/tests/unit/quant/job-observability.spec.js`
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
- Modify: `ruoyi-ui/src/views/quant/dispatch-detail/dispatch-detail-state.js`

- [ ] **Step 1: Write the failing unit test for shared job summary formatting**

```javascript
const { buildJobObservabilitySummary } = require('@/views/quant/jobs/job-observability')

describe('buildJobObservabilitySummary', () => {
  test('summarizes running job with human-readable time range and next step', () => {
    const summary = buildJobObservabilitySummary({
      historyRecord: {
        jobId: 238,
        scopeSummary: 'ETF 池',
        timeRangeSummary: '最近 60 个交易日'
      },
      jobStatus: {
        status: 'RUNNING',
        completedShardCount: 1,
        plannedShardCount: 2
      },
      detailState: {
        currentStageLabel: 'ETF 池信号计算',
        currentObjectLabel: '159915 创业板 ETF',
        nextStepLabel: '完成后进入风险过滤'
      }
    })

    expect(summary.headline).toContain('ETF 池')
    expect(summary.progressLabel).toBe('1/2')
    expect(summary.nextStepLabel).toContain('风险过滤')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ruoyi-ui; npm run test:unit -- job-observability.spec.js`

Expected: FAIL with module-not-found or function-not-defined error for `job-observability.js`.

- [ ] **Step 3: Implement the shared formatter**

```javascript
function buildJobObservabilitySummary(payload = {}) {
  const historyRecord = payload.historyRecord || {}
  const jobStatus = payload.jobStatus || {}
  const detailState = payload.detailState || {}
  const completed = Number(jobStatus.completedShardCount || 0)
  const planned = Number(jobStatus.plannedShardCount || 0)

  return {
    headline: `${historyRecord.scopeSummary || '当前任务'}正在执行`,
    statusText: jobStatus.status || 'IDLE',
    progressLabel: planned > 0 ? `${completed}/${planned}` : '-',
    timeRangeLabel: historyRecord.timeRangeSummary || '-',
    currentStageLabel: detailState.currentStageLabel || '等待系统判定',
    currentObjectLabel: detailState.currentObjectLabel || '暂无',
    nextStepLabel: detailState.nextStepLabel || '等待下一步'
  }
}

module.exports = {
  buildJobObservabilitySummary
}
```

- [ ] **Step 4: Wire the formatter into the task-center and detail state builders**

```javascript
const { buildJobObservabilitySummary } = require('./job-observability')

const observability = buildJobObservabilitySummary({
  historyRecord,
  jobStatus,
  detailState
})

return {
  ...state,
  primaryTaskView: {
    ...state.primaryTaskView,
    observability
  }
}
```

- [ ] **Step 5: Run unit tests and commit**

Run: `cd ruoyi-ui; npm run test:unit -- job-observability.spec.js`

Expected: PASS

```bash
git add ruoyi-ui/src/views/quant/jobs/job-observability.js \
  ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js \
  ruoyi-ui/src/views/quant/dispatch-detail/dispatch-detail-state.js \
  ruoyi-ui/tests/unit/quant/job-observability.spec.js
git commit -m "feat: add shared quant job observability state"
```

## Task 2: Quant Dashboard Restructure

**Files:**
- Create: `ruoyi-ui/src/views/quant/dashboard/dashboard-object-layer.js`
- Modify: `ruoyi-ui/src/views/quant/dashboard/index.vue`
- Modify: `ruoyi-ui/src/views/quant/dashboard/dashboard-page-state.js`

- [ ] **Step 1: Write the failing test for the three-object layer model**

```javascript
const { buildDashboardObjectLayers } = require('@/views/quant/dashboard/dashboard-object-layer')

describe('buildDashboardObjectLayers', () => {
  test('returns 指数 ETF 个股 cards in fixed order', () => {
    const cards = buildDashboardObjectLayers({
      etfOverview: { todayEtfSignalCount: 1 },
      reviewCandidates: [{ assetType: 'EQUITY' }],
      marketStatus: { status: 'volatile' }
    })

    expect(cards.map(item => item.key)).toEqual(['index', 'etf', 'equity'])
    expect(cards[1].title).toBe('ETF')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ruoyi-ui; npm run test:unit -- dashboard-object-layer.spec.js`

Expected: FAIL because `dashboard-object-layer.js` does not exist yet.

- [ ] **Step 3: Implement the object-layer builder and dashboard view model**

```javascript
function buildDashboardObjectLayers(payload = {}) {
  const marketStatus = payload.marketStatus || {}
  const etfOverview = payload.etfOverview || {}
  const reviewCandidates = Array.isArray(payload.reviewCandidates) ? payload.reviewCandidates : []

  return [
    {
      key: 'index',
      title: '指数',
      summary: marketStatus.status || '未判断',
      nextAction: '看市场环境'
    },
    {
      key: 'etf',
      title: 'ETF',
      summary: `${Number(etfOverview.todayEtfSignalCount || 0)} 个运行中/待执行`,
      nextAction: '去调度中心'
    },
    {
      key: 'equity',
      title: '个股',
      summary: `${reviewCandidates.length} 个待复盘`,
      nextAction: '去复盘分析'
    }
  ]
}

module.exports = {
  buildDashboardObjectLayers
}
```

- [ ] **Step 4: Replace the existing ETF-heavy middle section with a unified object-layer section**

```vue
<el-card shadow="never" class="box-card">
  <div slot="header" class="section-header">
    <span>对象层总览</span>
    <span class="section-meta">指数、ETF、个股分工明确，但只突出当前主线</span>
  </div>
  <div class="object-layer-grid">
    <div v-for="item in objectLayers" :key="item.key" class="object-layer-card">
      <div class="object-layer-card__title">{{ item.title }}</div>
      <div class="object-layer-card__summary">{{ item.summary }}</div>
      <el-button type="text" size="mini">{{ item.nextAction }}</el-button>
    </div>
  </div>
</el-card>
```

- [ ] **Step 5: Run smoke tests and commit**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-quant.ps1`

Expected: PASS and the dashboard scenario still finds visible quant markers.

```bash
git add ruoyi-ui/src/views/quant/dashboard/index.vue \
  ruoyi-ui/src/views/quant/dashboard/dashboard-page-state.js \
  ruoyi-ui/src/views/quant/dashboard/dashboard-object-layer.js
git commit -m "feat: redesign quant dashboard object layers"
```

## Task 3: Dispatch Center Decision Surface

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue`
- Create: `ruoyi-ui/tests/unit/quant/jobs-task-center-state.spec.js`

- [ ] **Step 1: Write the failing test for action prioritization**

```javascript
const { buildTaskCenterState } = require('@/views/quant/jobs/jobs-task-center-state')

describe('buildTaskCenterState', () => {
  test('prefers 查看当前调度 when a same-scope job is already running', () => {
    const state = buildTaskCenterState({
      todayStatus: { statusCode: 'RUNNING' },
      currentDispatch: { jobId: 238, scopeSummary: 'ETF 池', status: 'RUNNING' }
    })

    expect(state.primaryAction.label).toBe('查看当前调度')
    expect(state.todayStatusReason).toContain('运行中')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ruoyi-ui; npm run test:unit -- jobs-task-center-state.spec.js`

Expected: FAIL because current state builder does not yet produce the new wording.

- [ ] **Step 3: Update the state builder to emit concrete action wording**

```javascript
function buildPrimaryAction(summary = {}) {
  if (summary.currentDispatch && summary.currentDispatch.status === 'RUNNING') {
    return {
      code: 'VIEW_CURRENT_DISPATCH',
      label: '查看当前调度',
      reason: '当前有同范围任务运行中，建议先观察，不重复发起。'
    }
  }

  return {
    code: 'GO_MANUAL_DISPATCH',
    label: '发起手工调度',
    reason: '当前没有运行中任务，可以创建新的调度任务。'
  }
}
```

- [ ] **Step 4: Rebuild the center page layout around status, primary action, current dispatch summary, and short history**

```vue
<today-status-card
  :summary="taskCenterSummary"
  :primary-action="taskCenterUi.primaryAction"
  :secondary-action="taskCenterUi.secondaryActions[0] || null"
/>

<primary-task-card :primary-task="taskCenterUi.primaryTaskView" />

<task-progress-timeline
  :events="taskCenterUi.progressEvents"
  :technical-summary="taskCenterUi.technicalSummaryRows"
/>
```

- [ ] **Step 5: Run unit + smoke tests and commit**

Run:

```bash
cd ruoyi-ui
npm run test:unit -- jobs-task-center-state.spec.js
cd ..
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-quant.ps1
```

Expected: PASS

```bash
git add ruoyi-ui/src/views/quant/jobs/index.vue \
  ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js \
  ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue \
  ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue \
  ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue \
  ruoyi-ui/tests/unit/quant/jobs-task-center-state.spec.js
git commit -m "feat: redesign quant dispatch center decision flow"
```

## Task 4: Manual And Auto Dispatch Clarification

**Files:**
- Modify: `ruoyi-ui/src/views/quant/dispatch-manual/index.vue`
- Modify: `ruoyi-ui/src/views/quant/dispatch-manual/manual-dispatch-submit-state.js`
- Modify: `ruoyi-ui/src/views/quant/dispatch-shared/ManualDispatchSummaryCard.vue`
- Modify: `ruoyi-ui/src/views/quant/dispatch-auto/index.vue`

- [ ] **Step 1: Write the failing test for manual submit feedback wording**

```javascript
const { buildManualDispatchSubmitView } = require('@/views/quant/dispatch-manual/manual-dispatch-submit-state')

describe('buildManualDispatchSubmitView', () => {
  test('redirecting state explains that detail page is the next stop', () => {
    const view = buildManualDispatchSubmitView(
      { status: 'redirecting', jobId: 238, startedAt: Date.now() - 2000 },
      { now: Date.now() }
    )

    expect(view.detail).toContain('238')
    expect(view.expectation).toContain('调度详情页')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ruoyi-ui; npm run test:unit -- manual-dispatch-submit-state.spec.js`

Expected: FAIL if the test file is new or current wording diverges from the desired contract.

- [ ] **Step 3: Tighten manual-dispatch page copy, pre-submit expectation, and success handoff**

```javascript
this.submitState = {
  status: 'redirecting',
  startedAt: submitStartedAt,
  jobId,
  errorMessage: ''
}

this.$modal.msgSuccess(`已创建任务 #${jobId}，正在进入调度详情页`)
this.$router.push({ path: `/quant/dispatch-detail/${jobId}` }).catch(() => {})
```

- [ ] **Step 4: Reframe auto-dispatch page as plan/history only**

```vue
<div class="panel-item">
  <div class="panel-main">这里看系统计划，不在这里手工发起主流程</div>
  <div class="panel-copy">如果你要立刻跑一次，请进入手工调度页。</div>
</div>
```

- [ ] **Step 5: Run smoke tests and commit**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-quant.ps1`

Expected: PASS and the manual/auto pages still expose the expected route markers.

```bash
git add ruoyi-ui/src/views/quant/dispatch-manual/index.vue \
  ruoyi-ui/src/views/quant/dispatch-manual/manual-dispatch-submit-state.js \
  ruoyi-ui/src/views/quant/dispatch-shared/ManualDispatchSummaryCard.vue \
  ruoyi-ui/src/views/quant/dispatch-auto/index.vue
git commit -m "feat: clarify manual and auto quant dispatch pages"
```

## Task 5: Dispatch Detail Running-State Experience

**Files:**
- Modify: `ruoyi-ui/src/views/quant/dispatch-detail/index.vue`
- Modify: `ruoyi-ui/src/views/quant/dispatch-detail/dispatch-detail-state.js`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/JobStatusCard.vue`
- Create: `ruoyi-ui/tests/unit/quant/dispatch-detail-state.spec.js`

- [ ] **Step 1: Write the failing test for running-state headline and next-step text**

```javascript
const { buildDispatchDetailState } = require('@/views/quant/dispatch-detail/dispatch-detail-state')

describe('buildDispatchDetailState', () => {
  test('describes current stage, current object, and next step for a running job', () => {
    const state = buildDispatchDetailState({
      historyRecord: { scopeSummary: 'ETF 池', timeRangeSummary: '最近 60 个交易日' },
      jobStatus: { status: 'RUNNING', completedShardCount: 1, plannedShardCount: 2 },
      events: [{ message: '正在处理 159915', endTime: '13:42:15' }]
    })

    expect(state.bannerTitle).toContain('ETF 池')
    expect(state.latestLogLabel).toContain('159915')
    expect(state.nextStepLabel).not.toBe('-')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ruoyi-ui; npm run test:unit -- dispatch-detail-state.spec.js`

Expected: FAIL because current detail state does not yet satisfy the stronger wording contract.

- [ ] **Step 3: Update the detail state builder and status card**

```javascript
return {
  bannerTitle: `${scopeSummary}正在执行`,
  bannerDescription: `${currentStageLabel}，当前处理 ${currentObjectLabel}。`,
  latestLogLabel: latestEvent.message || '暂无结构化日志',
  nextStepLabel: inferNextStep({ jobStatus, shards, results }),
  waitingLabel: inferWaitingLabel({ jobStatus, latestEvent })
}
```

```vue
<div class="job-item">
  <label>当前对象</label>
  <span>{{ jobStatus.currentObjectLabel || '-' }}</span>
</div>
<div class="job-item wide">
  <label>下一步</label>
  <span>{{ jobStatus.nextStepLabel || '-' }}</span>
</div>
```

- [ ] **Step 4: Rebuild the page around live banner, execution overview, timeline, logs, and next actions**

```vue
<el-card shadow="never" class="live-banner">
  <div class="live-banner__title">{{ detailState.bannerTitle }}</div>
  <div class="live-banner__description">{{ detailState.bannerDescription }}</div>
</el-card>
<job-status-card :job-id="jobId" :job-status="jobStatus" />
```

- [ ] **Step 5: Run unit + smoke tests and commit**

Run:

```bash
cd ruoyi-ui
npm run test:unit -- dispatch-detail-state.spec.js
cd ..
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-quant.ps1
```

Expected: PASS

```bash
git add ruoyi-ui/src/views/quant/dispatch-detail/index.vue \
  ruoyi-ui/src/views/quant/dispatch-detail/dispatch-detail-state.js \
  ruoyi-ui/src/views/quant/jobs/components/JobStatusCard.vue \
  ruoyi-ui/tests/unit/quant/dispatch-detail-state.spec.js
git commit -m "feat: improve quant dispatch detail observability"
```

## Task 6: End-To-End Validation And Marker Refresh

**Files:**
- Modify: `ruoyi-ui/playwright-smoke.cjs`
- Modify: `scripts/smoke-quant.ps1`
- Modify: `scripts/smoke-all.ps1`

- [ ] **Step 1: Update Playwright markers for the redesigned pages**

```javascript
{
  name: '量化-调度中心',
  path: '/quant/jobs',
  markers: ['量化调度中心', '查看当前调度', '当前任务摘要', '调度历史'],
  suites: ['all', 'quant']
}
```

- [ ] **Step 2: Add a positive assertion for manual-dispatch handoff**

```javascript
async function runQuantManualDispatchPreview(page, report) {
  await waitForText(page, '手工调度')
  await waitForText(page, '本次提交摘要')
  report.steps.push('手工调度页显示提交摘要与调度详情回执说明')
}
```

- [ ] **Step 3: Run the quant smoke suite**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-quant.ps1`

Expected: `PASS 11/11` or the current quant suite total with all quant scenarios green.

- [ ] **Step 4: Run the full smoke suite**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-all.ps1`

Expected: `PASS 22/22` or the current all-suite total with all scenarios green.

- [ ] **Step 5: Commit the verification updates**

```bash
git add ruoyi-ui/playwright-smoke.cjs scripts/smoke-quant.ps1 scripts/smoke-all.ps1
git commit -m "test: refresh quant smoke markers for redesigned flows"
```

## Self-Review

### Spec coverage

- Dashboard main action, object-layer balance, and running-side summary are covered by Task 2.
- Dispatch center page responsibility, action priority, and current-job visibility are covered by Task 3.
- Manual vs auto dispatch boundary and post-submit handoff are covered by Task 4.
- Running-state observability, blocking explanations, and result handoff are covered by Task 5.
- Verification and regression protection are covered by Task 6.

### Placeholder scan

- No `TODO`, `TBD`, or deferred “implement later” wording remains in the plan.
- Every task includes exact file paths, concrete commands, and code snippets.

### Type consistency

- Shared running-job wording flows through `job-observability.js`, then into both task-center and detail state.
- Human-readable time range and next-step labels use the same naming family: `timeRangeLabel`, `currentStageLabel`, `currentObjectLabel`, `nextStepLabel`.
- Page names remain aligned with the approved product structure: dashboard, dispatch center, manual dispatch, auto dispatch, dispatch detail.
