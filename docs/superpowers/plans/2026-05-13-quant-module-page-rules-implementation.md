# Quant Module Page Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the quant module operations-console design spec into durable repository constraints: persistent rules, stable reference docs, and automated checks.

**Architecture:** Promote the approved design spec into a layered enforcement model. Keep the original design spec as source context, create always-on repository guidance in `CLAUDE.md` plus a quant-specific scoped rule file, add stable docs under `docs/quant/`, and add a first-pass automated UI rule test that checks machine-detectable anti-patterns in `ruoyi-ui/src/views/quant`.

**Tech Stack:** Markdown docs, repository instruction files, Node `node:test` checks for Vue source inspection.

---

## File Structure

### Existing files to read and preserve

- `docs/superpowers/specs/2026-05-13-quant-module-operations-console-regulation-design.md`
  - Approved design source for the rules and docs.
- `.gitignore`
  - Check whether new rule/doc paths need ignore updates.
- `ruoyi-ui/tests/quant-page-header-copy.test.cjs`
  - Existing content-oriented structural checks for quant pages.
- `ruoyi-ui/tests/bundle-structure.test.cjs`
  - Existing repo pattern for source-inspection tests.

### Files to create

- `CLAUDE.md`
  - Repo-level persistent instructions. Keeps the short, always-on quant page rule summary and points to stable docs.
- `.claude/rules/quant-module-page-rules.md`
  - Quant-module-scoped rules for page design and delivery behavior.
- `docs/quant/page-design-regulations.md`
  - Stable design rules distilled from the design spec.
- `docs/quant/page-delivery-regulations.md`
  - Stable execution and acceptance rules for future page tasks.
- `docs/quant/page-review-checklist.md`
  - Short operator checklist used before/after each quant UI task.
- `ruoyi-ui/tests/quant-page-regulations.test.cjs`
  - Automated machine-checkable constraints for anti-PPT layout patterns.

### Files to modify

- `ruoyi-ui/tests/quant-page-header-copy.test.cjs`
  - Align broad wording checks with the new stable regulation file references where useful.

---

### Task 1: Restore the Interrupted Working Tree Before Any Rule Work

**Files:**
- Restore: `ruoyi-ui/src/views/quant/operations/index.vue`

- [ ] **Step 1: Confirm the file is currently missing**

Run: `if (Test-Path ruoyi-ui/src/views/quant/operations/index.vue) { 'exists' } else { 'missing' }`
Expected: `missing`

- [ ] **Step 2: Restore the file from HEAD without touching unrelated changes**

Run:

```powershell
git checkout -- ruoyi-ui/src/views/quant/operations/index.vue
```

Expected: file restored to committed state

- [ ] **Step 3: Verify only the pre-existing unrelated untracked doc remains**

Run: `git status --short`
Expected: no deleted `operations/index.vue`; only unrelated untracked doc remains if still present

- [ ] **Step 4: Commit only if the restore produced a tracked diff**

Run:

```bash
git add ruoyi-ui/src/views/quant/operations/index.vue
git commit -m "chore(quant): restore interrupted operations page"
```

Expected: commit only if there was a staged restore diff

### Task 2: Create Persistent Repository Rules

**Files:**
- Create: `CLAUDE.md`
- Create: `.claude/rules/quant-module-page-rules.md`

- [ ] **Step 1: Write the failing expectation test for rule files existing**

Add to `ruoyi-ui/tests/quant-page-regulations.test.cjs`:

```javascript
test('quant page regulations are persisted in repository rule files', () => {
  const repoRoot = path.resolve(__dirname, '..', '..')
  assert.equal(fs.existsSync(path.join(repoRoot, 'CLAUDE.md')), true)
  assert.equal(fs.existsSync(path.join(repoRoot, '.claude/rules/quant-module-page-rules.md')), true)
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `node --test ruoyi-ui/tests/quant-page-regulations.test.cjs`
Expected: FAIL because `CLAUDE.md` and `.claude/rules/quant-module-page-rules.md` do not exist yet

- [ ] **Step 3: Create `CLAUDE.md` with short always-on repo instructions**

Content:

```markdown
# Repository Rules

## Quant Module Page Rules

For all work under `ruoyi-ui/src/views/quant`:

1. Follow `docs/quant/page-design-regulations.md`.
2. Follow `docs/quant/page-delivery-regulations.md`.
3. Use `docs/quant/page-review-checklist.md` before and after each quant page task.
4. Treat quant pages as operations consoles, not presentation pages.
5. First screens must not use hero sections, tall empty cards, illustrative empty states, or repeated summary blocks.
6. Do not claim a quant page issue is fixed without real logged-in screenshots from the target page state.
```

- [ ] **Step 4: Create quant-scoped rule file with enforceable detail**

Content:

```markdown
# Quant Module Page Rules

Applies to: `ruoyi-ui/src/views/quant/**`

## Design

1. First screens must use an operations-console skeleton:
   - compact page header
   - one primary fact/action area
   - secondary folded or paginated detail areas
2. Forbid hero sections on first screen.
3. Forbid single-card `min-height`.
4. Forbid illustrative `el-empty` states on first screen.
5. Forbid large cards that contain only one or two short lines.
6. Forbid repeating the same business fact in multiple first-screen blocks.

## Delivery

1. Before editing a quant page, define the 3-5 questions the first screen must answer.
2. Before editing, capture a real logged-in baseline screenshot.
3. After editing, verify the real logged-in page again with screenshots.
4. Build/test success is necessary but never sufficient for quant page acceptance.
5. If two rounds of edits fail to remove the first-screen problem, stop and return to root-cause review before more code changes.
```

- [ ] **Step 5: Run the rule existence test to verify it passes**

Run: `node --test ruoyi-ui/tests/quant-page-regulations.test.cjs`
Expected: PASS

- [ ] **Step 6: Commit the rule files**

```bash
git add CLAUDE.md .claude/rules/quant-module-page-rules.md ruoyi-ui/tests/quant-page-regulations.test.cjs
git commit -m "docs(quant): persist page regulations as repository rules"
```

### Task 3: Promote the Spec into Stable Reference Documents

**Files:**
- Create: `docs/quant/page-design-regulations.md`
- Create: `docs/quant/page-delivery-regulations.md`
- Create: `docs/quant/page-review-checklist.md`

- [ ] **Step 1: Write the failing expectation test for stable docs existing**

Append to `ruoyi-ui/tests/quant-page-regulations.test.cjs`:

```javascript
test('quant page regulations are promoted into stable docs', () => {
  const repoRoot = path.resolve(__dirname, '..', '..')
  assert.equal(fs.existsSync(path.join(repoRoot, 'docs/quant/page-design-regulations.md')), true)
  assert.equal(fs.existsSync(path.join(repoRoot, 'docs/quant/page-delivery-regulations.md')), true)
  assert.equal(fs.existsSync(path.join(repoRoot, 'docs/quant/page-review-checklist.md')), true)
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `node --test ruoyi-ui/tests/quant-page-regulations.test.cjs`
Expected: FAIL because the stable docs do not exist yet

- [ ] **Step 3: Create the stable design regulations doc**

`docs/quant/page-design-regulations.md` should include concise permanent sections:

```markdown
# Quant Page Design Regulations

## P0 Principle

Quant pages must behave like operations consoles, not presentation pages.

## First-Screen Skeleton

1. Compact page header
2. Primary fact/action area
3. Secondary folded or paginated details

## First-Screen Prohibitions

1. No hero sections
2. No single-card min-height
3. No illustrative empty states
4. No repeated summaries
5. No large low-information cards

## Page-Type Rules

- Dispatch pages: show current task, scope, time range, step, next step, result
- Operations pages: show blocker, impact, recovery action, technical state
- Execution pages: show current exceptions and next closure action
- Review/governance pages: show current conclusion, action, and core evidence
- Research pages: show configuration and result summary before supporting charts
- Dashboard pages: show the 1-3 most important current actions
```

- [ ] **Step 4: Create the stable delivery regulations doc**

`docs/quant/page-delivery-regulations.md` should include:

```markdown
# Quant Page Delivery Regulations

## Before Editing

1. Define the 3-5 first-screen questions
2. Capture a real logged-in baseline screenshot

## During Editing

1. Remove structural waste before polishing content
2. Fix one page at a time

## Verification

1. Real logged-in after screenshot required
2. Build/test success is necessary but insufficient
3. Verify no large empty first-screen area remains
4. Verify no repeated first-screen fact remains

## Failure Handling

1. After two failed rounds, stop
2. Revisit root cause before more edits
```

- [ ] **Step 5: Create the checklist doc**

`docs/quant/page-review-checklist.md` should include:

```markdown
# Quant Page Review Checklist

- What 3-5 questions must this first screen answer?
- Does the first screen use a hero section?
- Does any first-screen card use min-height?
- Is there any illustrative empty state on the first screen?
- Is the same fact repeated in multiple blocks?
- Does any large block contain only one or two short lines?
- If no data exists, does the first screen shrink to a small status line or compact summary?
- Are history, logs, and advanced tools pushed down?
- Is there only one primary action?
- Do we have real logged-in before/after screenshots?
```

- [ ] **Step 6: Run the stable doc existence test to verify it passes**

Run: `node --test ruoyi-ui/tests/quant-page-regulations.test.cjs`
Expected: PASS

- [ ] **Step 7: Commit the stable docs**

```bash
git add docs/quant/page-design-regulations.md docs/quant/page-delivery-regulations.md docs/quant/page-review-checklist.md ruoyi-ui/tests/quant-page-regulations.test.cjs
git commit -m "docs(quant): promote page regulations into stable references"
```

### Task 4: Add Automated Anti-PPT Source Checks

**Files:**
- Create: `ruoyi-ui/tests/quant-page-regulations.test.cjs`

- [ ] **Step 1: Write the first failing anti-pattern test**

Initial content:

```javascript
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

function read(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, '..', relativePath), 'utf8')
}

test('quant pages do not use first-screen hero shells', () => {
  const files = [
    'src/views/quant/jobs/index.vue',
    'src/views/quant/operations/index.vue',
    'src/views/quant/dispatch-detail/index.vue'
  ]
  files.forEach(file => {
    const source = read(file)
    assert.equal(source.includes('hero-shell'), false, `${file} still includes hero-shell`)
    assert.equal(source.includes('ops-hero'), false, `${file} still includes ops-hero`)
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `node --test ruoyi-ui/tests/quant-page-regulations.test.cjs`
Expected: FAIL because current quant pages still contain prohibited first-screen hero structures

- [ ] **Step 3: Expand the test to machine-check other durable anti-patterns**

Add checks for:

```javascript
test('quant pages do not use illustrative el-empty first-screen placeholders', () => {
  const files = [
    'src/views/quant/jobs/components/TaskProgressTimeline.vue',
    'src/views/quant/dispatch-detail/index.vue',
    'src/views/quant/jobs/components/JobResultTable.vue',
    'src/views/quant/jobs/components/JobShardTable.vue'
  ]
  files.forEach(file => {
    const source = read(file)
    assert.equal(source.includes('<el-empty'), false, `${file} still uses el-empty`)
  })
})

test('quant pages avoid single-card min-height on first-screen components', () => {
  const files = [
    'src/views/quant/jobs/components/TodayStatusCard.vue',
    'src/views/quant/jobs/components/PrimaryTaskCard.vue',
    'src/views/quant/jobs/index.vue',
    'src/views/quant/operations/index.vue'
  ]
  files.forEach(file => {
    const source = read(file)
    assert.equal(/min-height\s*:\s*\d+px/.test(source), false, `${file} still sets min-height`)
  })
})
```

- [ ] **Step 4: Run the regulation test suite and record the failing pages as the next remediation backlog**

Run: `node --test ruoyi-ui/tests/quant-page-regulations.test.cjs`
Expected: FAIL with specific files called out; treat this as intentional backlog evidence, not a release-ready state

- [ ] **Step 5: Commit the regulation test scaffold even though it fails**

```bash
git add ruoyi-ui/tests/quant-page-regulations.test.cjs
git commit -m "test(quant): scaffold durable page regulation checks"
```

### Task 5: Link Existing Broad Tests to the New Stable Regulation Layer

**Files:**
- Modify: `ruoyi-ui/tests/quant-page-header-copy.test.cjs`

- [ ] **Step 1: Write a failing expectation that the stable design doc is referenced by the broad quant page checks**

Add:

```javascript
test('quant page copy checks are anchored to the stable regulation docs', () => {
  const designDoc = read('../docs/quant/page-design-regulations.md')
  assert.equal(designDoc.includes('operations consoles'), true)
})
```

- [ ] **Step 2: Run the targeted test to verify it fails if the helper path or doc is wrong**

Run: `node --test ruoyi-ui/tests/quant-page-header-copy.test.cjs`
Expected: FAIL until the helper and path usage are corrected

- [ ] **Step 3: Update the test helper to support cross-root reads cleanly**

Code:

```javascript
function readRepo(relativePath) {
  return fs.readFileSync(path.join(__dirname, '..', '..', relativePath), 'utf8')
}
```

Then use:

```javascript
const designDoc = readRepo('docs/quant/page-design-regulations.md')
```

- [ ] **Step 4: Run the targeted test to verify it passes**

Run: `node --test ruoyi-ui/tests/quant-page-header-copy.test.cjs`
Expected: PASS

- [ ] **Step 5: Commit the anchor update**

```bash
git add ruoyi-ui/tests/quant-page-header-copy.test.cjs
git commit -m "test(quant): anchor page checks to stable regulations"
```

### Task 6: Final Verification and Handoff

**Files:**
- Verify only:
  - `CLAUDE.md`
  - `.claude/rules/quant-module-page-rules.md`
  - `docs/quant/page-design-regulations.md`
  - `docs/quant/page-delivery-regulations.md`
  - `docs/quant/page-review-checklist.md`
  - `ruoyi-ui/tests/quant-page-regulations.test.cjs`
  - `ruoyi-ui/tests/quant-page-header-copy.test.cjs`

- [ ] **Step 1: Run the stable passing doc/tests**

Run:

```bash
node --test ruoyi-ui/tests/quant-page-header-copy.test.cjs
```

Expected: PASS

- [ ] **Step 2: Run the regulation suite and confirm whether it is intentionally red or green**

Run:

```bash
node --test ruoyi-ui/tests/quant-page-regulations.test.cjs
```

Expected:
- PASS for existence/reference checks
- FAIL for anti-PPT page checks until page remediation work is completed

- [ ] **Step 3: Capture the current backlog implied by the new failing regulation tests**

Summarize which files still violate:

1. hero prohibition
2. `el-empty` prohibition
3. `min-height` prohibition

- [ ] **Step 4: Commit any final verification-only adjustments**

```bash
git add CLAUDE.md .claude/rules/quant-module-page-rules.md docs/quant ruoyi-ui/tests/quant-page-regulations.test.cjs ruoyi-ui/tests/quant-page-header-copy.test.cjs
git commit -m "chore(quant): finalize durable page regulation layer"
```

## Self-Review

Spec coverage:

1. Design spec promotion is covered by Tasks 2 and 3.
2. Durable repository rules are covered by Task 2.
3. Machine-checkable enforcement is covered by Task 4.
4. Existing test anchoring is covered by Task 5.
5. Interrupted deletion recovery is covered by Task 1.

Placeholder scan:

1. No TODO/TBD markers remain.
2. Each task contains exact file paths and exact commands.
3. Code-bearing steps include concrete code blocks.

Type consistency:

1. Rule/doc file names are reused consistently across tasks.
2. Test file names and helper function names are consistent.
3. The anti-pattern checks intentionally produce red tests as backlog evidence; this is explicit in Task 4 and Task 6.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-13-quant-module-page-rules-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
