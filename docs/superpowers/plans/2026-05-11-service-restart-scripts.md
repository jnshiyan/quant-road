# Service Restart Scripts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add safe restart/check scripts for `ruoyi-admin`, `ruoyi-ui`, and the Python async worker, plus one all-services orchestrator.

**Architecture:** Create one shared PowerShell helper file for process matching, path normalization, and health checks. Keep per-service restart scripts thin, then orchestrate them from one top-level restart/check entrypoint.

**Tech Stack:** PowerShell, Pester, existing Maven/npm/Python service commands

---

### File Map

**Create**
- `scripts/lib/service-runtime.ps1`
- `scripts/restart-ruoyi-ui.ps1`
- `scripts/restart-quant-worker.ps1`
- `scripts/restart-all-services.ps1`
- `scripts/check-all-services-runtime.ps1`
- `scripts/run-quant-worker-loop.ps1`
- `scripts/tests/service-runtime.Tests.ps1`

**Modify**
- `scripts/restart-ruoyi-admin.ps1`
- `scripts/check-ruoyi-admin-runtime.ps1`

### Task 1: Add failing PowerShell tests for service command-line matching

**Files:**
- Create: `scripts/tests/service-runtime.Tests.ps1`
- Test: `scripts/tests/service-runtime.Tests.ps1`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run the test to confirm helper functions do not exist yet**
- [ ] **Step 3: Add shared helper functions for admin/ui/worker matching**
- [ ] **Step 4: Re-run the test and confirm it passes**

### Task 2: Refactor admin runtime/restart scripts onto the shared helper

**Files:**
- Create: `scripts/lib/service-runtime.ps1`
- Modify: `scripts/restart-ruoyi-admin.ps1`
- Modify: `scripts/check-ruoyi-admin-runtime.ps1`

- [ ] **Step 1: Replace ad-hoc matching logic with shared helper calls**
- [ ] **Step 2: Keep existing admin restart behavior intact**
- [ ] **Step 3: Verify admin runtime script still reports process + endpoint health**

### Task 3: Add UI restart script

**Files:**
- Modify: `scripts/lib/service-runtime.ps1`
- Create: `scripts/restart-ruoyi-ui.ps1`

- [ ] **Step 1: Add ui process discovery and parent npm cleanup logic**
- [ ] **Step 2: Implement stop/start flow for `npm run dev`**
- [ ] **Step 3: Verify `http://127.0.0.1` returns HTML after restart**

### Task 4: Add Python worker restart script

**Files:**
- Modify: `scripts/lib/service-runtime.ps1`
- Create: `scripts/restart-quant-worker.ps1`

- [ ] **Step 1: Add worker process discovery logic**
- [ ] **Step 2: Implement stop/start flow for `python -m quant_road run-async-worker`**
- [ ] **Step 3: Verify the worker process stays alive after launch**
- [ ] **Step 4: Add a supervisor loop so idle worker exit does not kill the service**

### Task 5: Add all-services restart orchestrator and runtime check script

**Files:**
- Create: `scripts/restart-all-services.ps1`
- Create: `scripts/check-all-services-runtime.ps1`

- [ ] **Step 1: Implement stop-order orchestration**
- [ ] **Step 2: Implement start-order orchestration**
- [ ] **Step 3: Add final health verification and summary output**
- [ ] **Step 4: Add one-command runtime status summary for all services**

### Task 6: Verification

**Files:**
- Test: `scripts/tests/service-runtime.Tests.ps1`
- Test: runtime execution of restart/check scripts

- [ ] **Step 1: Run `Invoke-Pester scripts/tests/service-runtime.Tests.ps1`**
- [ ] **Step 2: Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-all-services-runtime.ps1`**
- [ ] **Step 3: Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/restart-all-services.ps1`**
- [ ] **Step 4: Re-run `check-all-services-runtime.ps1` and confirm healthy summary**
- [ ] **Step 5: Commit**
