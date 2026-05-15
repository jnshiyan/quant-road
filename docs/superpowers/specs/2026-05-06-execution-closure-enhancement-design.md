# Execution Closure Enhancement Design

**Date:** 2026-05-06

## Goal

补齐执行闭环增强的 4 个核心能力：

1. `executionReconciliationSummary`
2. `executionMatchCandidates`
3. `confirmExecutionMatch`
4. `positionSyncResult`

本轮优先目标是把“信号 -> 成交 -> 持仓”主链变成可见、可补救、可人工确认的闭环，不新增库表，不重构现有执行服务。

## Current Context

- Java 后端已经提供执行记录、信号、执行反馈、持仓等读接口。
- Python 层已经支持 `record-execution`、`import-executions`、`evaluate-execution-feedback`。
- `execution_record.signal_id` 目前允许为空，因此系统天然存在“成交已录入但未匹配信号”的状态。
- `signal_execution_feedback` 仅有 `EXECUTED / MISSED / PENDING` 三种状态，没有单独的 `PARTIAL` 状态字段。

## Scope

### In Scope

- 新增 3 个数据查询接口与 1 个确认接口。
- 基于现有表推导未匹配成交、候选信号、部分成交和持仓同步差异。
- 执行回写页接入上述接口，支持人工确认匹配。
- 看板执行摘要复用新的 reconciliation summary。

### Out of Scope

- 不新增 `execution_match`、`reconciliation_detail` 等持久化明细表。
- 不新增执行异常标记、批量预校验、正式导入校验流程。
- 不重写 Python 执行引擎或持仓引擎。

## Proposed API Contract

### `GET /quant/data/executionReconciliationSummary`

返回字段：

- `pendingSignalCount`
- `executedSignalCount`
- `missedSignalCount`
- `partialExecutionCount`
- `unmatchedExecutionCount`
- `latestCheckDate`
- `todayWritebackComplete`

口径：

- `pending / executed / missed` 来自 `signal_execution_feedback.status`
- `partial` 通过“信号已有关联成交，但 `trade_signal.is_execute = 0` 或 feedback 仍为 `PENDING`”推导
- `unmatched` 来自 `execution_record.signal_id IS NULL`

### `GET /quant/data/executionMatchCandidates`

请求参数：

- `executionRecordId` 必填
- `limit` 可选，默认 5

返回字段：

- `executionRecordId`
- `signalId`
- `stockCode`
- `strategyId`
- `signalType`
- `signalDate`
- `matchScore`
- `matchReason`
- `alreadyExecuted`

候选规则：

1. `stock_code` 相同
2. `strategy_id` 相同
3. 方向匹配：`execution_record.side = trade_signal.signal_type`
4. 信号日期优先选择离成交日最近的未执行信号
5. 已执行信号可降权保留，便于人工兜底

### `POST /quant/jobs/confirmExecutionMatch`

请求字段：

- `signalId`
- `executionRecordId`
- `actor`
- `remark`

处理动作：

1. 校验成交与信号存在
2. 校验 `stock_code / strategy_id / side(signal_type)` 一致
3. 更新 `execution_record.signal_id`
4. 更新 `trade_signal.is_execute = 1`
5. 重跑执行反馈评估
6. 返回：
   - `matchConfirmed`
   - `executionRecord`
   - `signal`
   - `executionReconciliationSummary`
   - `positionSyncResult`

### `GET /quant/data/positionSyncResult`

请求参数：

- `strategyId` 可选
- `stockCode` 可选

返回字段：

- `syncStatus`
- `positionBefore`
- `positionAfter`
- `differenceItems`
- `differenceCount`

口径：

- `positionBefore` 取当前 `position` 表
- `positionAfter` 通过已匹配成交按股票聚合推导净仓位
- `differenceItems` 展示数量缺口、持仓存在性不一致、均价差异

## UI Design

### Execution Page

- 顶部新增执行闭环摘要卡片
- 执行记录区域新增“未匹配成交”操作列，点击后打开候选匹配弹窗
- 反馈区域对 `PENDING + executed_quantity > 0` 呈现“部分成交”视觉提示
- 新增持仓同步结果卡片，展示一致/差异和差异明细

### Dashboard

- 现有执行反馈摘要替换为 reconciliation summary 的核心口径

## Error Handling

- 候选列表为空时，不报错，返回空数组并提示补录成交或检查策略/方向。
- 人工确认时如果股票、策略、方向不一致，返回业务错误，禁止误绑。
- 持仓同步结果在无数据时返回 `EMPTY`，而不是 500。

## Testing Strategy

- Java 服务层测试：
  - reconciliation summary 聚合
  - match candidate 排序与过滤
  - confirm match 的校验与回填
  - position sync 差异计算
- Java 控制器测试：
  - 新增数据接口出参与 JSON 字段
  - confirm match 接口成功路径
- 前端手动验证：
  - 摘要加载
  - 未匹配成交查看候选
  - 人工确认后列表和摘要刷新

## Success Criteria

用户能够直接在系统里确认：

1. 今天还有多少信号待闭环
2. 哪些成交没匹配到信号
3. 哪条未匹配成交应该绑定到哪个信号
4. 成交绑定后反馈是否恢复正常
5. 当前持仓是否与成交记录一致
