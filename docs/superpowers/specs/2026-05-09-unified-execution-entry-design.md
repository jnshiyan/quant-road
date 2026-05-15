# Unified Execution Entry Design

**Date:** 2026-05-09

## Goal

消除当前量化任务执行入口中的概念歧义，把 `fullDaily`、`runStrategy`、`runPortfolio`、同步、异步、轻量、全量等实现细节从用户心智中移除，收敛为一个统一的“执行任务”入口。

该入口的固定业务目标只有一个：

- 为用户指定的执行范围产出正式可用结果

系统负责自动决定：

- 本次需要执行哪些步骤
- 哪些步骤可以跳过
- 哪些步骤只针对当前范围执行
- 本次应同步执行还是异步执行

## Current Context

- 当前产品与接口层同时暴露了 `fullDaily`、`runStrategy`、`runPortfolio` 等多个入口。
- `fullDaily` 在 Python CLI 中是固定流水线，包含 `sync-basic`、`sync-daily`、`sync-valuation`、`evaluate-market`、`run-strategy`、`evaluate-risk`、`evaluate-execution-feedback`、`notify-signals` 等步骤。
- `runStrategy` / `runPortfolio` 已接入 Java 侧 async planner，可根据 `requestedMode` 决定同步或异步，并支持分片、排队、worker 消费。
- 当前 `fullDaily` 未接入统一 async planner，因此“业务流程”和“执行方式”在用户感知上被混成了多个概念。
- 之前 ETF 小范围任务变慢，已确认有两类原因：
  - 作用范围曾存在透传不完整问题，导致小范围任务可能退化成大范围任务
  - `fullDaily` 固定串行执行过多非必要步骤，小范围任务也被全量流程绑住
- 自动 async worker 已经补齐，异步消费不再依赖手工触发，但这仍只是执行机制层能力，不解决入口语义混乱问题。

## Problem Statement

当前设计存在四类问题：

1. **概念歧义**
   用户难以理解 `fullDaily` 与异步是什么关系，也难以判断何时该选哪一个入口。

2. **性能不可预测**
   同样是“执行 ETF 池”，可能因为入口不同，走到完全不同的执行链路。

3. **实现分裂**
   固定盘后流水线与 async planner 分别演进，范围解析、成本估算、任务状态、恢复能力没有统一抽象。

4. **产品目标与技术细节混用**
   用户真正关心的是“对这个范围执行任务并得到正式结果”，而不是自己挑选同步、异步或特定 CLI 名称。

## Product Decision

对外只保留一个用户入口：

- `执行任务`

用户在该入口只表达业务信息：

- 执行范围
- 策略或策略组
- 可选日期
- 可选资金参数

系统固定以“产出正式可用结果”为目标，内部自动规划执行计划并选择执行方式。

以下概念不再作为用户需要理解或选择的产品语义：

- `fullDaily`
- `runStrategy`
- `runPortfolio`
- `requestedMode`
- 同步执行
- 异步执行
- 轻量执行
- 全量执行

这些术语仅允许作为内部代码、日志、运维视图中的实现细节存在。

## Proposed Architecture

### 1. Unified Request

新增统一请求模型 `ExecutionRequest`，仅表达用户想执行什么，不表达如何执行。

建议字段：

- `scopeType`
- `scopePoolCode`
- `symbols`
- `whitelist`
- `blacklist`
- `adHocSymbols`
- `strategyId`
- `strategyIds`
- `strategyBacktestStartDate`
- `portfolioTotalCapital`
- `actor`

不再接受：

- `requestedMode`
- `useFullDaily`
- `useAsync`
- 其他带有技术实现倾向的切换字段

### 2. Unified Plan

系统接到 `ExecutionRequest` 后，必须先生成一个 `ExecutionPlan`，然后才能执行。

`ExecutionPlan` 至少应包含：

- `requestId` 或等价唯一标识
- 解析后的 `symbols`
- 规范化后的 scope 元数据
- 步骤列表 `steps`
- 每一步的作用范围
- 每一步是否允许跳过
- 每一步的前置依赖
- 成本估算
- 决策后的执行模式 `resolvedExecutionMode`
- 可观测性字段，如计划摘要、计划原因、预计耗时

### 3. Unified Engine

新增统一执行引擎 `ExecutionEngine`：

- 如果计划成本较低，则同步执行整个 plan
- 如果计划成本较高，则异步提交 plan
- 异步时允许按 plan 中的可分片步骤进行 shard 化
- 所有执行都写统一的任务状态、步骤状态和结果状态

这样“业务计划”与“执行方式”被显式拆开：

- `ExecutionPlan` 决定做什么
- `ExecutionEngine` 决定怎么做

## Planning Rules

### Rule 1: Scope Resolution First

所有任务都先统一解析作用范围，输出：

- 标准化 `symbols`
- `scopeType`
- `scopePoolCode`
- 约束来源（池、白名单、黑名单、临时选择等）

范围解析必须先于任何步骤裁剪和成本估算，否则无法保证性能与结果一致性。

### Rule 2: Formal Result Is the Fixed Goal

系统默认目标不是“跑完整盘后链路”，而是：

- 让本次范围获得正式可用结果

因此系统应只执行实现该目标所必需的步骤，而不是无条件执行完整固定流水线。

### Rule 3: Steps Are Selected by Dependency, Not by Legacy Command Name

系统不得按“用户选了哪个旧入口”决定步骤，而应按依赖关系裁剪步骤。

建议规则如下：

- `sync-basic`
  - 只在基础证券主数据缺失、损坏或超过刷新阈值时执行
  - 不是每次执行任务的默认必跑步骤

- `sync-daily`
  - 对本次范围内标的是必需步骤
  - 必须支持只同步当前范围，而不是默认扫全市场

- `sync-valuation`
  - 仅当本次策略、市场判断或风控逻辑实际依赖估值数据，且当前估值数据未满足新鲜度要求时执行

- `evaluate-market`
  - 仅当本次策略需要市场状态，且当前市场状态未生成或已过期时执行

- `run-strategy`
  - 是最终结果生成的核心步骤
  - 必须严格限定在当前解析后的范围内

- `evaluate-risk`
  - 是正式可用结果的一部分
  - 默认执行，但范围必须限定在本次策略结果关联对象内

- `evaluate-execution-feedback`
  - 不再作为默认主执行链路的一部分
  - 应拆分为独立巡检/定时任务，或仅在明确依赖时单独触发

- `notify-signals`
  - 属于后置通知动作，不应影响主执行计划的成功定义
  - 可作为计划中的可选尾步骤

### Rule 4: Prefer Narrow Scope Over Global Sweep

只要任务显式给定执行范围，所有支持范围化的步骤都必须优先按范围执行，而不是退回全量模式。

例如 ETF 池仅 2 个标的时，计划应优先收敛为：

- `resolve-scope`
- `sync-daily(symbols=2)`
- `run-strategy(symbols=2)`
- `evaluate-risk(symbols=2)`

而不是自动升级为完整市场级流水线。

## Sync vs Async Decision Rules

用户不选择同步或异步，系统按预算自动决定。

### Inputs

预算估算至少应考虑：

- `symbolCount`
- `strategyCount`
- `backtestDays`
- 计划步骤数量
- 是否包含全局步骤
- 是否包含可分片步骤
- 当前队列压力或系统繁忙度

### Decision

- **同步执行**
  - 预估成本低
  - 步骤数少
  - 无需分片
  - 在前台可接受耗时阈值内完成

- **异步执行**
  - 预估成本高
  - 包含可分片步骤
  - 包含多个策略或较大范围
  - 超过前台可接受时长
  - 当前系统繁忙，异步排队更优

- **强制异步**
  - 全市场级范围
  - 夜间正式批量任务
  - 明确需要恢复、重试、分片容错的任务

### Default Threshold Strategy

建议先采用简单可解释的阈值策略，再逐步优化：

- 低成本计划：同步
- 超阈值计划：异步
- 全局计划：强制异步

阈值策略必须落日志，并可在任务详情中展示“本次为何转为异步”。

## Target User Experience

### UI

页面只保留一个主要动作：

- `执行任务`

用户只填写：

- 执行范围
- 策略
- 回测起点
- 资金参数

提交后页面展示：

- 任务已受理
- 本次执行计划摘要
- 当前进度
- 最终结果

不再展示让用户自行理解的模式选择器：

- 同步 / 异步
- `fullDaily`
- 轻量 / 全量

### API

建议新增：

- `POST /quant/jobs/execute`

返回至少包含：

- `executionId`
- `status`
- `resolvedExecutionMode`
- `planSummary`
- `stepSummary`
- `estimatedCost`

## Migration Strategy

### Phase 1: Unify Entry Without Rewriting Everything

先新增统一入口与统一 plan，前端改为只调用新入口。

内部实现暂时允许兼容复用旧能力：

- 小范围低成本任务可复用现有同步 `runStrategy` / `runPortfolio`
- 大范围任务可复用现有 async planner
- 仍需完整旧流水线时，可临时调用现有 `full-daily`

目标是先统一用户语义与调用路径，而不是一开始就彻底重写执行内核。

### Phase 2: Replace Fixed `full-daily` Pipeline With Planned Steps

把 Python `full-daily` 从固定流水线改造成读取计划执行。

核心要求：

- `sync-basic` 改为按需
- `sync-daily` 强制支持范围化
- `sync-valuation` / `evaluate-market` 按依赖触发
- `evaluate-execution-feedback` 移出默认主链

这一步是性能优化的关键，也是解决“小范围任务仍然很慢”的核心改造。

### Phase 3: Merge Sync and Async Into One Engine

所有任务统一先生成 plan，再由统一 engine 决定同步或异步。

异步框架不再只服务 `runStrategy` / `runPortfolio`，而应能服务任意可分片执行计划。

### Phase 4: Remove Legacy Product Concepts

当统一入口稳定后，逐步废弃：

- `/quant/jobs/fullDaily`
- `/quant/jobs/runStrategy`
- `/quant/jobs/runPortfolio`
- `requestedMode`
- 页面上的旧概念和提示文案

保留兼容期时，所有旧入口都必须转发到统一 planner，并在代码与日志中标记 deprecated。

## Non-Goals

本设计不包含以下目标：

- 本轮直接重写全部 Python 步骤实现
- 本轮直接更换存储模型
- 本轮直接引入复杂的规则 DSL
- 本轮直接做动态机器学习式调度

本轮重点是统一语义、统一入口、统一计划、统一执行方式判定。

## Observability and Diagnostics

统一执行模型必须保证可解释性。

至少要记录：

- 用户请求参数摘要
- 解析后的范围
- 生成的计划步骤
- 每一步被选择或跳过的原因
- 成本估算结果
- 同步/异步决策原因
- 最终执行耗时与结果

任务详情页应能回答三个问题：

1. 这次系统打算做什么
2. 为什么做这些步骤
3. 为什么这次是同步或异步

## Risks

### Risk 1: Legacy Compatibility Drift

统一入口早期会同时复用旧实现，可能导致同一计划在不同兼容路径上表现不一致。

缓解方式：

- 所有兼容路径都要生成统一 plan 并写统一日志
- 为小范围 ETF、全市场、组合策略分别补集成测试

### Risk 2: Over-Aggressive Step Pruning

如果步骤裁剪规则定义不完整，可能得到性能更好但结果不完整的任务输出。

缓解方式：

- 先把“正式可用结果”的必要条件显式编码
- 对每个步骤增加依赖说明与测试覆盖

### Risk 3: UI and Backend Semantic Mismatch

如果前端仍延续旧概念展示，用户仍会认为自己在选模式。

缓解方式：

- 前后端同步替换文案
- 新接口上线后，旧模式字段只作为兼容输入，不再作为主要展示字段

## Testing Strategy

### Planner Tests

- 小范围 ETF 任务应生成范围收敛的最小必要步骤
- 全市场任务应生成包含全局步骤的正式计划
- 不同数据新鲜度下，步骤裁剪结果应符合预期
- 同样的业务请求不应因入口不同而生成不同计划

### Engine Tests

- 低成本计划走同步
- 超阈值计划走异步
- 全局计划强制异步
- 异步任务可恢复、可重试、可观察

### Integration Tests

- ETF 核心池仅 2 个标的时，执行计划不得回退到全市场
- 正式盘后批量执行仍能得到完整结果
- 旧接口转发到新 planner 后，结果与预期一致

## Success Criteria

本设计落地后，系统应满足以下结果：

1. 用户只看到一个执行入口，不再被要求理解 `fullDaily`、同步、异步等技术概念。
2. 小范围任务不会再因为默认固定全链路而异常变慢。
3. 所有任务都先生成统一执行计划，再决定执行方式。
4. 同一业务请求无论从哪个兼容入口进入，都得到一致的计划与结果。
5. 任务详情可以清楚解释本次执行步骤和同步/异步决策原因。
