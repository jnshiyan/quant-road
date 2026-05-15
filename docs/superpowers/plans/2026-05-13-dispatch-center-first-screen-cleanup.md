# Dispatch Center First-Screen Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the `调度中心` first screen into a compact operations console that leads with the current dispatch, falls back to manual dispatch when idle, and keeps only the latest three results above the fold.

**Architecture:** Keep the existing `/quant/jobs` route, API calls, and most state contracts, but reshape the page around one compact header, one consolidated primary card, one short recent-results section, and lower secondary collapses. Move the action hierarchy and state-specific status line into `jobs-task-center-state.js`, merge first-screen actions into `PrimaryTaskCard.vue`, and retire the old hero / duplicated first-screen blocks from `jobs/index.vue`.

**Tech Stack:** Vue 2, Element UI, Node test runner (`node --test`), PowerShell smoke scripts, existing quant dispatch state helpers.

---

## File Structure

### Existing files to read and preserve

- `docs/superpowers/specs/2026-05-13-dispatch-center-first-screen-cleanup-design.md`
  - Approved design and acceptance source of truth.
- `docs/quant/page-design-regulations.md`
  - Stable first-screen layout rules.
- `docs/quant/page-delivery-regulations.md`
  - Requires first-screen questions plus real logged-in before/after screenshots.
- `docs/quant/page-review-checklist.md`
  - Quick acceptance checklist for the page.
- `ruoyi-ui/src/views/quant/jobs/index.vue`
  - Current dispatch-center page that still contains `hero-shell`, duplicate blocks, and high first-screen density.
- `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
  - View-model helper that should own the new compact status line and primary-action priority.
- `ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue`
  - Will become the only first-screen action/fact card.
- `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
  - Must no longer be a large first-screen card and must stop using `min-height`.
- `ruoyi-ui/src/views/quant/jobs/components/TaskActionPanel.vue`
  - Action logic to fold into `PrimaryTaskCard.vue`.
- `ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue`
  - Must move below the fold and stop using `el-empty`.
- `ruoyi-ui/tests/jobs-route.test.cjs`
  - Route/page wording checks for dispatch-center structure.
- `ruoyi-ui/tests/jobs-task-center-state.test.cjs`
  - State-builder contract tests.
- `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`
  - Needs to stop expecting a hero on `jobs/index.vue`.
- `ruoyi-ui/tests/quant-page-header-copy.test.cjs`
  - Existing compact-copy assertions for quant pages.
- `ruoyi-ui/tests/quant-page-regulations.test.cjs`
  - Source-inspection checks for `hero-shell`, `el-empty`, and `min-height`.

### Files to modify

- `ruoyi-ui/src/views/quant/jobs/index.vue`
  - Remove hero-first-screen layout and rebuild around compact header, unified primary card, recent results, and secondary collapses.
- `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
  - Add compact status-line/view-model fields for `RUNNING`, `IDLE`, and `BLOCKED` first-screen states.
- `ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue`
  - Absorb the first-screen primary action, compact status strip, and small action feedback area.
- `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
  - Remove first-screen `min-height` and reduce it to a compact reusable status row if still needed.
- `ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue`
  - Replace `el-empty` with compact text placeholders and prepare for below-the-fold usage.
- `ruoyi-ui/tests/jobs-route.test.cjs`
  - Update wording and structure assertions to the new first-screen contract.
- `ruoyi-ui/tests/jobs-task-center-state.test.cjs`
  - Add state-builder assertions for compact status line and first-button priority.
- `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`
  - Remove the old hero expectation for the dispatch center and replace it with compact-header expectations.
- `ruoyi-ui/tests/quant-page-header-copy.test.cjs`
  - Align copy assertions to the new first-screen labels if needed.

### Evidence to capture during execution

- Real logged-in before screenshot of `/quant/jobs` first screen.
- Real logged-in after screenshot of `/quant/jobs` first screen.
- Command output for focused Node tests and `scripts/smoke-quant.ps1`.

---

### Task 1: Capture baseline evidence and lock the first-screen acceptance target

**Files:**
- Read: `docs/superpowers/specs/2026-05-13-dispatch-center-first-screen-cleanup-design.md`
- Read: `docs/quant/page-design-regulations.md`
- Read: `docs/quant/page-delivery-regulations.md`
- Read: `docs/quant/page-review-checklist.md`

- [ ] **Step 1: Record the five first-screen questions in the work log**

Use this checklist as the acceptance target for every later step:

```text
1. 现在有没有运行中的调度？
2. 这次调度正在做什么？
3. 我现在最应该点哪个按钮？
4. 如果不能继续，阻断点是什么？
5. 最近 3 次调度结果里有没有需要继续追查的记录？
```

- [ ] **Step 2: Capture the real logged-in baseline screenshot before editing**

Open the running app in a real logged-in browser session and capture the current `/quant/jobs` first screen. Name it with a stable timestamp, for example:

```text
dispatch-center-before-2026-05-13.png
```

The screenshot must visibly include:

```text
- hero 区
- 今日结论 / 当前运行事实 / 主操作并列
- 最近提交任务或下一次自动调度
- 现有折叠区起始位置
```

- [ ] **Step 3: Confirm the page currently violates the spec in source**

Run:

```powershell
node --test ruoyi-ui/tests/quant-page-regulations.test.cjs
```

Expected: `PASS`, but the later focused route tests should still describe the old structure; this step proves the repository rule test is active before changing any page-specific assertions.

---

### Task 2: Rewrite the page-structure tests so they describe the new dispatch-center contract

**Files:**
- Modify: `ruoyi-ui/tests/jobs-route.test.cjs`
- Modify: `ruoyi-ui/tests/jobs-task-center-state.test.cjs`
- Modify: `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`
- Modify: `ruoyi-ui/tests/quant-page-header-copy.test.cjs`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`
- Test: `ruoyi-ui/tests/jobs-task-center-state.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-header-copy.test.cjs`

- [ ] **Step 1: Add failing route assertions for the new first-screen structure**

In `ruoyi-ui/tests/jobs-route.test.cjs`, replace the old hero/section wording checks with the new first-screen contract:

```javascript
assert.equal(source.includes('hero-shell'), false)
assert.equal(source.includes('今日结论'), false)
assert.equal(source.includes('主操作'), false)
assert.equal(source.includes('统一调度入口'), false)
assert.equal(source.includes('最近 3 条调度结果'), true)
assert.equal(source.includes('最近进展与技术摘要'), true)
assert.equal(source.includes('全部调度历史'), true)
assert.equal(source.includes("'/quant/dispatch-manual'"), true)
assert.equal(source.includes("'/quant/dispatch-auto'"), true)
assert.equal(source.includes('/quant/dispatch-detail/'), true)
```

- [ ] **Step 2: Add failing state-builder tests for compact header status lines**

Append focused tests to `ruoyi-ui/tests/jobs-task-center-state.test.cjs`:

```javascript
test('running dispatch builds a compact first-screen status line', () => {
  const result = buildTaskCenterState({
    todayStatus: { statusCode: 'RUNNING', statusLabel: '运行中' },
    primaryTask: {
      taskName: '盘后主流程',
      status: 'RUNNING',
      currentStage: 'run-strategy',
      waitingTarget: 'worker 正在消费分片',
      scopeSummary: 'ETF 池',
      timeRangeSummary: '近 120 个交易日'
    }
  })

  assert.equal(result.pageStatusLine, '当前有调度正在执行，先查看当前调度，不建议重复提交。')
  assert.equal(result.primaryAction.label, '查看当前调度')
  assert.equal(result.primaryTaskView.nextStep, 'worker 正在消费分片')
})

test('idle dispatch center turns the main first-screen button into 手工调度', () => {
  const result = buildTaskCenterState({
    todayStatus: { statusCode: 'WARNING', statusLabel: '警告' },
    primaryTask: { taskName: '当前无运行任务', status: 'IDLE' }
  })

  assert.equal(result.pageStatusLine, '当前没有运行中的调度，可以直接发起手工调度。')
  assert.equal(result.primaryAction.label, '发起手工调度')
})

test('blocked dispatch center explains that recovery comes before a new dispatch', () => {
  const result = buildTaskCenterState({
    todayStatus: { statusCode: 'BLOCKED', statusLabel: '阻断', reason: '无活跃 worker' },
    primaryTask: { taskName: '盘后主流程', status: 'FAILED' }
  })

  assert.equal(result.pageStatusLine, '当前存在阻断问题，应先处理后再发起新调度。')
  assert.equal(result.primaryAction.label, '去运维中心处理')
})
```

- [ ] **Step 3: Stop expecting a hero on the dispatch-center page**

In `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`, remove `jobsSource` from the loop that still expects `hero-eyebrow`, `hero-side`, and `hero-decision-card`, and add compact-header assertions instead:

```javascript
assert.equal(jobsSource.includes('hero-eyebrow'), false)
assert.equal(jobsSource.includes('hero-decision-card'), false)
assert.equal(jobsSource.includes('jobs-secondary-collapse'), true)
assert.equal(jobsSource.includes('dispatch-center-header'), true)
assert.equal(jobsSource.includes('dispatch-center-status-line'), true)
```

- [ ] **Step 4: Align compact-copy assertions with the new labels**

In `ruoyi-ui/tests/quant-page-header-copy.test.cjs`, update the dispatch-center-specific copy expectations:

```javascript
assert.equal(jobs.includes('<h1>调度中心</h1>'), true)
assert.equal(jobs.includes('最近 3 条调度结果'), true)
assert.equal(jobs.includes('最近进展与技术摘要'), true)
assert.equal(jobs.includes('今日结论'), false)
assert.equal(jobs.includes('主操作'), false)
assert.equal(jobs.includes('统一调度入口'), false)
```

- [ ] **Step 5: Run the focused tests and verify they fail before implementation**

Run:

```powershell
node --test ruoyi-ui/tests/jobs-route.test.cjs
node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs
node --test ruoyi-ui/tests/quant-page-hero-consistency.test.cjs
node --test ruoyi-ui/tests/quant-page-header-copy.test.cjs
```

Expected: `FAIL` in all four files because the current page still contains the old hero/section structure and `jobs-task-center-state.js` does not yet expose `pageStatusLine`.

- [ ] **Step 6: Commit the test-only contract changes**

```bash
git add ruoyi-ui/tests/jobs-route.test.cjs \
  ruoyi-ui/tests/jobs-task-center-state.test.cjs \
  ruoyi-ui/tests/quant-page-hero-consistency.test.cjs \
  ruoyi-ui/tests/quant-page-header-copy.test.cjs
git commit -m "test(quant): lock dispatch center first-screen contract"
```

---

### Task 3: Extend the dispatch-center view model for compact first-screen states

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js`
- Modify: `ruoyi-ui/tests/jobs-task-center-state.test.cjs`
- Test: `ruoyi-ui/tests/jobs-task-center-state.test.cjs`

- [ ] **Step 1: Add helper functions for the page status line and compact status summary**

Extend `ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js` with these helpers above `buildTaskCenterState`:

```javascript
function resolvePageStatusLine(todayStatus = {}, primaryTask = {}) {
  const statusCode = resolveStatusCode(todayStatus)
  if (statusCode === 'BLOCKED') {
    return '当前存在阻断问题，应先处理后再发起新调度。'
  }
  if (['RUNNING', 'QUEUED', 'PENDING'].includes(primaryTask.status) || statusCode === 'RUNNING') {
    return '当前有调度正在执行，先查看当前调度，不建议重复提交。'
  }
  return '当前没有运行中的调度，可以直接发起手工调度。'
}

function buildPrimaryTaskHint(primaryTaskView = {}, statusCode = 'WARNING') {
  if (statusCode === 'BLOCKED') {
    return primaryTaskView.nextStep || '先进入运维中心恢复阻断，再决定是否重新发起调度。'
  }
  if (['RUNNING', 'QUEUED', 'PENDING'].includes(primaryTaskView.status)) {
    return primaryTaskView.nextStep || '先查看当前调度详情，确认当前步骤和等待对象。'
  }
  return '当前没有运行中的任务，可直接发起新的手工调度。'
}
```

- [ ] **Step 2: Return the new compact fields from `buildTaskCenterState`**

Update the return shape to include the new fields the page will render:

```javascript
const todayStatusCode = resolveStatusCode(todayStatus)

return {
  todayStatusCode,
  todayStatusLabel: resolveStatusLabel(todayStatus),
  pageStatusLine: resolvePageStatusLine(todayStatus, primaryTask),
  primaryTaskHint: buildPrimaryTaskHint(primaryTaskView, todayStatusCode),
  todayStatusReason: todayStatus.reason || summary.todayReason || '当前缺少可执行的业务结论',
  primaryAction,
  secondaryActions,
  progressEvents: Array.isArray(summary.progressEvents) ? summary.progressEvents.slice(0, 5) : [],
  technicalSummaryRows: buildTechnicalSummaryRows(summary.technicalSummary || {}, primaryTaskView),
  primaryTaskView,
  nextScheduledDispatch: summary.nextScheduledDispatch || {},
  dispatchHistoryRows: summary.dispatchHistory && Array.isArray(summary.dispatchHistory.rows) ? summary.dispatchHistory.rows : []
}
```

- [ ] **Step 3: Keep the idle primary action aligned to the approved spec**

Adjust `resolvePrimaryAction()` so the fallback idle case stays on `发起手工调度` instead of `进入量化看板`:

```javascript
if (statusCode === 'OPERABLE') {
  return normalizeAction({}, {
    code: 'RUN_EXECUTION',
    label: '发起手工调度',
    targetPage: '/quant/dispatch-manual'
  })
}
```

- [ ] **Step 4: Run the state-builder tests and verify they pass**

Run:

```powershell
node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs
```

Expected: `PASS`; the file now exposes `pageStatusLine`, keeps `查看当前调度` for running tasks, switches idle to `发起手工调度`, and keeps blocked flows on `去运维中心处理`.

- [ ] **Step 5: Commit the state-builder update**

```bash
git add ruoyi-ui/src/views/quant/jobs/jobs-task-center-state.js \
  ruoyi-ui/tests/jobs-task-center-state.test.cjs
git commit -m "feat(quant): add compact dispatch center state contract"
```

---

### Task 4: Collapse the first-screen card set into one compact primary card

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue`
- Modify: `ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue`
- Modify: `ruoyi-ui/tests/quant-page-regulations.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-regulations.test.cjs`

- [ ] **Step 1: Add the first-screen action area to `PrimaryTaskCard.vue`**

Replace the old headline/progress-heavy layout with a compact status strip, fact grid, and inline actions:

```vue
<div v-if="statusLine" class="primary-task-card__status-line">
  <el-tag size="mini" :type="statusType">{{ statusLabel }}</el-tag>
  <span>{{ statusLine }}</span>
</div>

<div class="primary-task-card__actions">
  <el-button
    type="primary"
    size="small"
    :loading="pendingActionKey === (primaryAction.code || '')"
    @click="$emit('run-primary', primaryAction)"
  >
    {{ primaryAction.label || '刷新状态' }}
  </el-button>
  <el-button
    v-for="item in secondaryActions"
    :key="item.code"
    type="text"
    size="small"
    :disabled="pendingActionKey === item.code"
    @click="$emit('run-secondary', item)"
  >
    {{ item.label }}
  </el-button>
</div>
```

- [ ] **Step 2: Extend the `PrimaryTaskCard.vue` props for the consolidated first screen**

Add the props the page needs now that `TaskActionPanel.vue` is no longer a first-screen block:

```javascript
props: {
  primaryTask: { type: Object, default: () => ({}) },
  statusLine: { type: String, default: '' },
  primaryHint: { type: String, default: '' },
  primaryAction: { type: Object, default: () => ({}) },
  secondaryActions: { type: Array, default: () => [] },
  pendingActionKey: { type: String, default: '' },
  actionFeedback: { type: Object, default: null }
}
```

- [ ] **Step 3: Remove first-screen `min-height` from the reusable cards**

Delete the `min-height` declarations from both component styles:

```css
.today-status-card {
  border-radius: 18px;
  border: 1px solid #dfe7ef;
}

.primary-task-card {
  border-radius: 16px;
  border: 1px solid #e5eaf3;
}
```

- [ ] **Step 4: Replace `el-empty` in `TaskProgressTimeline.vue` with compact text placeholders**

Change the no-data branches so the file no longer contains `<el-empty`:

```vue
<div v-if="events.length" class="event-list">
  ...
</div>
<div v-else class="empty-note">暂无步骤进展，展开后再看系统更新。</div>

<div v-if="technicalItems.length" class="meta-list">
  ...
</div>
<div v-else class="empty-note">暂无技术摘要，展开后再看运行细节。</div>
```

Add a small style block:

```css
.empty-note {
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
  color: #64748b;
  line-height: 1.6;
}
```

- [ ] **Step 5: Run the regulations test and verify the component-level rule checks pass**

Run:

```powershell
node --test ruoyi-ui/tests/quant-page-regulations.test.cjs
```

Expected: `PASS`; `TodayStatusCard.vue` and `PrimaryTaskCard.vue` no longer set `min-height`, and `TaskProgressTimeline.vue` no longer contains `<el-empty`.

- [ ] **Step 6: Commit the component cleanup**

```bash
git add ruoyi-ui/src/views/quant/jobs/components/PrimaryTaskCard.vue \
  ruoyi-ui/src/views/quant/jobs/components/TodayStatusCard.vue \
  ruoyi-ui/src/views/quant/jobs/components/TaskProgressTimeline.vue
git commit -m "refactor(quant): compact dispatch center primary card"
```

---

### Task 5: Rebuild `jobs/index.vue` around a compact header, one primary card, and recent results

**Files:**
- Modify: `ruoyi-ui/src/views/quant/jobs/index.vue`
- Modify: `ruoyi-ui/tests/jobs-route.test.cjs`
- Modify: `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`
- Modify: `ruoyi-ui/tests/quant-page-header-copy.test.cjs`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-header-copy.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-regulations.test.cjs`

- [ ] **Step 1: Remove the old hero shell and duplicate first-screen cards from the template**

Delete the top-of-file blocks for:

```vue
<div class="hero-shell"> ... </div>
<el-card shadow="never" class="dispatch-hub-card"> ... </el-card>
<today-status-card ... />
<task-action-panel ... />
<el-card shadow="never" class="next-dispatch-card"> ... </el-card>
<el-card v-if="recentDispatchHandoff" shadow="never" class="handoff-card mt16"> ... </el-card>
```

- [ ] **Step 2: Replace them with the new compact header and unified primary card**

Insert this structure near the top of `ruoyi-ui/src/views/quant/jobs/index.vue`:

```vue
<div class="dispatch-center-header">
  <div>
    <div class="page-eyebrow">量化调度中心</div>
    <h1>调度中心</h1>
    <div class="dispatch-center-status-line">{{ taskCenterUi.pageStatusLine }}</div>
  </div>
  <el-button size="small" plain icon="el-icon-refresh" @click="refreshHome">刷新</el-button>
</div>

<primary-task-card
  :primary-task="taskCenterUi.primaryTaskView || taskCenterSummary.primaryTask"
  :status-line="taskCenterUi.pageStatusLine"
  :primary-hint="taskCenterUi.primaryTaskHint"
  :primary-action="taskCenterUi.primaryAction"
  :secondary-actions="taskCenterUi.secondaryActions"
  :pending-action-key="pendingActionKey"
  :action-feedback="actionFeedback"
  @run-primary="handlePrimaryAction"
  @run-secondary="handleSecondaryAction"
/>
```

- [ ] **Step 3: Make recent results the only secondary first-screen block**

Replace the old recent section title and cards with the compact wording:

```vue
<div class="section-title section-title--inline">最近 3 条调度结果</div>
<div class="recent-history-grid">
  <el-card
    v-for="row in recentDispatchCards"
    :key="row.jobId || row.startedAt || row.taskName"
    shadow="never"
    class="recent-history-card"
  >
    <div class="recent-history-card__head">
      <div class="recent-history-card__title">{{ row.taskName || '未命名任务' }}</div>
      <el-tag size="mini" :type="historyStatusType(row.status)">{{ row.status || '-' }}</el-tag>
    </div>
    <div class="recent-history-card__meta">触发时间 {{ row.startedAt || '-' }}</div>
    <div class="recent-history-card__meta">范围 {{ row.scopeSummary || '-' }}</div>
    <div class="recent-history-card__result">{{ row.resultSummary || '等待系统产出结果摘要' }}</div>
    <el-button size="small" type="primary" plain @click="openDispatchDetail(row)">查看详情</el-button>
  </el-card>
  <div v-if="!recentDispatchCards.length" class="recent-history-empty">最近没有可展示的调度结果。</div>
</div>
```

- [ ] **Step 4: Move secondary facts into the collapse and rename the progress section**

In the collapse block, rename the first panel title and keep auto plan / history below the fold:

```vue
<el-collapse-item name="progress">
  <template slot="title">
    <div class="collapse-title-shell">
      <span>最近进展与技术摘要</span>
      <span class="section-meta">展开后看步骤、技术状态和辅助事实</span>
    </div>
  </template>
  <task-progress-timeline
    :events="taskCenterUi.progressEvents"
    :technical-summary="taskCenterUi.technicalSummaryRows"
    :primary-task="taskCenterUi.primaryTaskView"
  />
</el-collapse-item>
```

Keep the remaining collapses for:

```text
- 自动计划 / 调度定义
- 全部调度历史
```

- [ ] **Step 5: Remove old first-screen component imports and registrations**

Update the script block to remove unused imports:

```javascript
import PrimaryTaskCard from './components/PrimaryTaskCard'
import TaskProgressTimeline from './components/TaskProgressTimeline'
import DispatchDefinitionTable from './components/DispatchDefinitionTable'
import DispatchHistoryTable from './components/DispatchHistoryTable'
```

And remove `TodayStatusCard` / `TaskActionPanel` from `components: { ... }`.

- [ ] **Step 6: Replace the old hero/summary CSS with compact header styles**

Delete the `.hero-*`, `.dispatch-hub-card`, `.next-dispatch-card`, and `.handoff-card` sections, then add compact replacements:

```css
.dispatch-center-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.dispatch-center-status-line {
  margin-top: 8px;
  color: #475569;
  line-height: 1.7;
}

.recent-history-empty {
  padding: 12px 14px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px dashed #d6dde8;
  color: #64748b;
}
```

- [ ] **Step 7: Run the focused page tests and verify they pass**

Run:

```powershell
node --test ruoyi-ui/tests/jobs-route.test.cjs
node --test ruoyi-ui/tests/quant-page-hero-consistency.test.cjs
node --test ruoyi-ui/tests/quant-page-header-copy.test.cjs
node --test ruoyi-ui/tests/quant-page-regulations.test.cjs
```

Expected: `PASS`; `jobs/index.vue` no longer contains `hero-shell`, no longer advertises `今日结论`/`主操作` as first-screen headings, and now exposes `dispatch-center-header`, `dispatch-center-status-line`, `最近 3 条调度结果`, and the secondary collapse structure.

- [ ] **Step 8: Commit the page rewrite**

```bash
git add ruoyi-ui/src/views/quant/jobs/index.vue \
  ruoyi-ui/tests/jobs-route.test.cjs \
  ruoyi-ui/tests/quant-page-hero-consistency.test.cjs \
  ruoyi-ui/tests/quant-page-header-copy.test.cjs
git commit -m "refactor(quant): rebuild dispatch center first screen"
```

---

### Task 6: Verify with smoke coverage and capture the after screenshot

**Files:**
- Test: `ruoyi-ui/tests/quant-page-regulations.test.cjs`
- Test: `ruoyi-ui/tests/jobs-route.test.cjs`
- Test: `ruoyi-ui/tests/jobs-task-center-state.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-hero-consistency.test.cjs`
- Test: `ruoyi-ui/tests/quant-page-header-copy.test.cjs`

- [ ] **Step 1: Run the focused dispatch-center test pack**

Run:

```powershell
node --test ruoyi-ui/tests/quant-page-regulations.test.cjs
node --test ruoyi-ui/tests/jobs-route.test.cjs
node --test ruoyi-ui/tests/jobs-task-center-state.test.cjs
node --test ruoyi-ui/tests/quant-page-hero-consistency.test.cjs
node --test ruoyi-ui/tests/quant-page-header-copy.test.cjs
```

Expected: `PASS` for all five files.

- [ ] **Step 2: Run the quant smoke script before claiming the page is fixed**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-quant.ps1
```

Expected: `PASS`; the quant smoke suite completes without a dispatch-center regression.

- [ ] **Step 3: Capture the real logged-in after screenshot**

Open the updated `/quant/jobs` page in a real logged-in browser session and capture:

```text
dispatch-center-after-2026-05-13.png
```

The screenshot must clearly show:

```text
- 紧凑页头
- 单一主卡
- 最近 3 条调度结果
- 次级信息已折叠到首屏下方
```

- [ ] **Step 4: Review the screenshots against the checklist**

Confirm the after screenshot answers all five first-screen questions and does **not** show:

```text
- hero 区
- 首屏重复摘要
- 大面积低信息空卡
- illustrative empty state
```

- [ ] **Step 5: Prepare the implementation summary**

Summarize:

```text
- What changed in jobs/index.vue
- Which first-screen blocks were removed or merged
- Which tests passed
- Where the before/after screenshots are stored or attached
- Any follow-up work for the next page in the sequence
```

---

## Self-Review

### Spec coverage

- Compact first-screen skeleton: covered by Task 5.
- Current-dispatch-first / manual-dispatch fallback / blocked recovery priority: covered by Task 3 and Task 5.
- Recent three results as second priority: covered by Task 5.
- Remove hero / duplicate summary / first-screen empty states / min-height: covered by Task 2, Task 4, and Task 5.
- Real logged-in before/after screenshots: covered by Task 1 and Task 6.
- Focused tests plus smoke verification: covered by Task 2 and Task 6.

### Placeholder scan

- No placeholder markers remain in the plan body.
- Every code-changing task contains the target snippet, concrete file path, and exact commands.

### Type consistency

- The plan consistently uses `pageStatusLine`, `primaryTaskHint`, `primaryAction`, and `secondaryActions`.
- The page rewrite task passes those same prop names into `PrimaryTaskCard.vue`.
