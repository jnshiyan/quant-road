package com.ruoyi.web.service.quant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class QuantTaskCenterService
{
    private final QuantRoadQueryService quantRoadQueryService;

    public QuantTaskCenterService(QuantRoadQueryService quantRoadQueryService)
    {
        this.quantRoadQueryService = quantRoadQueryService;
    }

    public Map<String, Object> summary()
    {
        Map<String, Object> readiness = safeMap(quantRoadQueryService.jobReadiness(null));
        Long batchId = toLong(readiness.get("batchId"));
        List<Map<String, Object>> steps = batchId == null ? List.of() : safeList(quantRoadQueryService.jobSteps(batchId));
        List<Map<String, Object>> hints = safeList(quantRoadQueryService.jobSopHints(null));
        Map<String, Object> workerHealth = safeMap(quantRoadQueryService.asyncWorkerSummary());
        List<Map<String, Object>> asyncJobs = safeList(quantRoadQueryService.asyncJobs(5));
        Map<String, Object> nextScheduledDispatch = safeMap(quantRoadQueryService.nextScheduledDispatch());
        List<Map<String, Object>> dispatchDefinitions = safeList(quantRoadQueryService.dispatchDefinitions());
        Map<String, Object> dispatchHistory = safeMap(quantRoadQueryService.dispatchHistory(1, 10, null, null));
        Map<String, Object> latestDispatch = asyncJobs.isEmpty() ? firstHistoryRow(dispatchHistory) : asyncJobs.get(0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("todayStatus", todayStatus(readiness, steps, hints, workerHealth));
        payload.put("primaryTask", primaryTask(readiness, steps, hints, latestDispatch));
        payload.put("progressEvents", progressEvents(steps));
        payload.put("nextAction", nextAction(readiness, steps, hints, workerHealth));
        payload.put("technicalSummary", technicalSummary(readiness, workerHealth, asyncJobs));
        payload.put("nextScheduledDispatch", nextScheduledDispatch);
        payload.put("dispatchDefinitions", dispatchDefinitions);
        payload.put("dispatchHistory", dispatchHistory);
        return payload;
    }

    private Map<String, Object> todayStatus(
            Map<String, Object> readiness,
            List<Map<String, Object>> steps,
            List<Map<String, Object>> hints,
            Map<String, Object> workerHealth)
    {
        String normalizedCode = normalizeTodayStatus(readiness, workerHealth);
        boolean preferWarningHint = "BLOCKED".equals(readiness.get("status")) || "READY_WITH_WARNINGS".equals(readiness.get("status"));
        Map<String, Object> preferredHint = preferredHint(hints, preferWarningHint);
        Map<String, Object> primaryAction = statusPrimaryAction(normalizedCode, preferredHint);
        Map<String, Object> secondaryAction = statusSecondaryAction(normalizedCode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", normalizedCode);
        result.put("label", todayStatusLabel(normalizedCode));
        result.put("reason", stringValue(readiness.get("message"), "暂无今日任务状态。"));
        result.put("suggestion", resolveSuggestion(steps, readiness, hints));
        result.put("headlineAction", stringValue(preferredHint.get("title"), defaultHeadlineAction(normalizedCode)));
        result.put("statusCode", normalizedCode);
        result.put("statusLabel", todayStatusLabel(normalizedCode));
        result.put("canContinue", canContinue(normalizedCode));
        result.put("continueLabel", continueLabel(normalizedCode));
        result.put("urgency", urgency(normalizedCode, readiness));
        result.put("primaryAction", primaryAction);
        result.put("secondaryAction", secondaryAction);
        return result;
    }

    private Map<String, Object> primaryTask(
            Map<String, Object> readiness,
            List<Map<String, Object>> steps,
            List<Map<String, Object>> hints,
            Map<String, Object> latestDispatch)
    {
        Map<String, Object> row = buildPrimaryTaskRow(readiness, steps, latestDispatch);
        String currentStage = resolveCurrentStage(steps, readiness);
        String currentStageLabel = resolveCurrentStageLabel(steps, readiness, currentStage);
        String waitingTarget = resolveWaitingTarget(steps, readiness);

        Map<String, Object> result = new LinkedHashMap<>(toPrimaryTask(row));
        result.put("currentStage", currentStage);
        result.put("currentStageLabel", currentStageLabel);
        result.put("waitingKind", waitingTarget == null ? "NONE" : "TARGET");
        result.put("waitingTarget", waitingTarget);
        result.put("waitingFor", waitingTarget);
        result.put("expectedOutcome", resolveExpectedOutcome(readiness));
        result.put("requiresManualIntervention", requiresManualIntervention(readiness, hints));
        result.put("triggerMode", value(row, "trigger_mode", "triggerMode", null));
        result.put("triggerModeLabel", value(row, "trigger_mode_label", "triggerModeLabel", null));
        result.put("triggerSource", value(row, "trigger_source", "triggerSource", null));
        return result;
    }

    private Map<String, Object> buildPrimaryTaskRow(
            Map<String, Object> readiness,
            List<Map<String, Object>> steps,
            Map<String, Object> latestDispatch)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        if (latestDispatch != null)
        {
            row.putAll(latestDispatch);
        }
        row.putIfAbsent("task_name", readiness.get("batchId") == null ? "待提交执行任务" : "盘后主流程");
        row.putIfAbsent("status", stringValue(readiness.get("status"), "pending"));
        row.putIfAbsent("current_step_name", resolveCurrentStage(steps, readiness));
        row.putIfAbsent("scope_summary", value(row, "scopeSummary", "未指定范围"));
        row.putIfAbsent("time_range_summary", value(row, "timeRangeSummary", "未指定时间范围"));
        row.putIfAbsent("next_step_name", resolveNextStep(steps, readiness));
        row.putIfAbsent("progress_summary", progressSummary(readiness));
        return row;
    }

    private Map<String, Object> toPrimaryTask(Map<String, Object> row)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskName", value(row, "task_name", "taskName", "未命名任务"));
        result.put("status", value(row, "status", "pending"));
        result.put("currentStep", value(row, "current_step_name", "currentStep", "等待开始"));
        result.put("scopeSummary", value(row, "scope_summary", "scopeSummary", "未指定范围"));
        result.put("timeRangeSummary", value(row, "time_range_summary", "timeRangeSummary", "未指定时间范围"));
        result.put("nextStep", value(row, "next_step_name", "nextStep", "等待系统更新"));
        result.put("progressSummary", value(row, "progress_summary", "progressSummary", "0 / 0"));
        return result;
    }

    private List<Map<String, Object>> progressEvents(List<Map<String, Object>> steps)
    {
        return steps.stream()
                .sorted((left, right) -> progressOrderKey(right).compareTo(progressOrderKey(left)))
                .limit(5)
                .map(step -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("stepName", step.get("stepName"));
                    result.put("status", step.get("status"));
                    result.put("startTime", step.get("startTime"));
                    result.put("endTime", step.get("endTime"));
                    result.put("message", step.get("errorMessage"));
                    return result;
                })
                .toList();
    }

    private Map<String, Object> nextAction(
            Map<String, Object> readiness,
            List<Map<String, Object>> steps,
            List<Map<String, Object>> hints,
            Map<String, Object> workerHealth)
    {
        if (runningNeedsOperations(readiness, workerHealth))
        {
            return action("GO_OPERATIONS", firstHintTitle(hints, "先恢复阻断执行的链路"), "/quant/operations");
        }
        if (hasRunningStep(steps))
        {
            return action("WAIT_CURRENT_TASK", "等待当前任务完成", "/quant/dispatch-detail");
        }

        String status = stringValue(readiness.get("status"), "EMPTY");
        if ("EMPTY".equals(status))
        {
            return action("RUN_EXECUTION", firstHintTitle(hints, "提交执行任务"), firstHintTargetPage(hints, "/quant/dispatch-manual"));
        }
        if ("BLOCKED".equals(status) || "READY_WITH_WARNINGS".equals(status))
        {
            Map<String, Object> warningHint = preferredHint(hints, true);
            return action(
                    "GO_OPERATIONS",
                    stringValue(warningHint.get("title"), "先处理警告"),
                    stringValue(warningHint.get("targetPage"), "/quant/operations"));
        }
        if ("READY".equals(status))
        {
            return action("GO_DASHBOARD", "进入量化看板", "/quant/dashboard");
        }
        if ("RUNNING".equals(status))
        {
            return action("WAIT_CURRENT_TASK", "等待当前任务完成", "/quant/dispatch-detail");
        }
        return action("REFRESH_STATUS", "刷新任务状态", "/quant/jobs");
    }

    private Map<String, Object> technicalSummary(
            Map<String, Object> readiness,
            Map<String, Object> workerHealth,
            List<Map<String, Object>> asyncJobs)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", readiness.get("batchId"));
        result.put("completedSteps", toInt(readiness.get("completedSteps")));
        result.put("totalSteps", toInt(readiness.get("totalSteps")));
        result.put("warningStepCount", toInt(readiness.get("warningStepCount")));
        result.put("failedStepCount", toInt(readiness.get("failedStepCount")));
        result.put("dataIntegrityStatus", readiness.get("dataIntegrityStatus"));
        result.put("dataIntegrityCategory", readiness.get("dataIntegrityCategory"));
        result.put("workerStatus", workerHealth.get("status"));
        result.put("asyncJobCount", asyncJobs.size());
        return result;
    }

    private String resolveSuggestion(List<Map<String, Object>> steps, Map<String, Object> readiness, List<Map<String, Object>> hints)
    {
        if (hasRunningStep(steps))
        {
            return "当前任务仍在运行，请等待步骤完成。";
        }
        Map<String, Object> preferredHint = preferredHint(
                hints,
                "BLOCKED".equals(readiness.get("status")) || "READY_WITH_WARNINGS".equals(readiness.get("status")));
        if (preferredHint.get("suggestedAction") != null)
        {
            return String.valueOf(preferredHint.get("suggestedAction"));
        }
        return stringValue(readiness.get("message"), "请刷新任务状态。");
    }

    private String resolveCurrentStage(List<Map<String, Object>> steps, Map<String, Object> readiness)
    {
        for (Map<String, Object> step : steps)
        {
            if ("RUNNING".equals(step.get("status")))
            {
                return stringValue(step.get("stepName"), null);
            }
        }
        if (readiness.get("latestFailedStep") != null)
        {
            return String.valueOf(readiness.get("latestFailedStep"));
        }
        for (Map<String, Object> step : steps)
        {
            if (!"SUCCESS".equals(step.get("status")))
            {
                return stringValue(step.get("stepName"), null);
            }
        }
        return steps.isEmpty() ? null : stringValue(steps.get(steps.size() - 1).get("stepName"), null);
    }

    private String resolveCurrentStageLabel(List<Map<String, Object>> steps, Map<String, Object> readiness, String currentStage)
    {
        String status = stringValue(readiness.get("status"), "EMPTY");
        if ("READY".equals(status) || "READY_WITH_WARNINGS".equals(status))
        {
            return "完成";
        }
        if ("BLOCKED".equals(status))
        {
            return stringValue(readiness.get("latestFailedStep"), currentStage);
        }
        return currentStage;
    }

    private String resolveWaitingTarget(List<Map<String, Object>> steps, Map<String, Object> readiness)
    {
        String status = stringValue(readiness.get("status"), "EMPTY");
        if ("READY".equals(status) || "READY_WITH_WARNINGS".equals(status) || "BLOCKED".equals(status) || "EMPTY".equals(status))
        {
            return null;
        }
        for (Map<String, Object> step : steps)
        {
            if ("PENDING".equals(step.get("status")) || "QUEUED".equals(step.get("status")))
            {
                return stringValue(step.get("stepName"), null);
            }
        }
        return null;
    }

    private String resolveExpectedOutcome(Map<String, Object> readiness)
    {
        if (Boolean.TRUE.equals(readiness.get("canEnterDashboard")))
        {
            return "进入量化看板";
        }
        return stringValue(readiness.get("message"), "请先确认任务状态。");
    }

    private boolean requiresManualIntervention(Map<String, Object> readiness, List<Map<String, Object>> hints)
    {
        String status = stringValue(readiness.get("status"), "EMPTY");
        if ("BLOCKED".equals(status))
        {
            for (Map<String, Object> hint : hints)
            {
                if (!Boolean.TRUE.equals(hint.get("autoRecoverable")))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolveNextStep(List<Map<String, Object>> steps, Map<String, Object> readiness)
    {
        String waitingTarget = resolveWaitingTarget(steps, readiness);
        if (waitingTarget != null)
        {
            return waitingTarget;
        }
        if ("BLOCKED".equals(readiness.get("status")))
        {
            return "等待人工恢复";
        }
        if (Boolean.TRUE.equals(readiness.get("canEnterDashboard")))
        {
            return "进入量化看板";
        }
        return null;
    }

    private String progressSummary(Map<String, Object> readiness)
    {
        return toInt(readiness.get("completedSteps")) + " / " + toInt(readiness.get("totalSteps"));
    }

    private String normalizeTodayStatus(Map<String, Object> readiness, Map<String, Object> workerHealth)
    {
        String readinessStatus = stringValue(readiness.get("status"), "EMPTY");
        if ("READY".equals(readinessStatus))
        {
            return "OPERABLE";
        }
        if ("READY_WITH_WARNINGS".equals(readinessStatus))
        {
            return "WARNING";
        }
        if ("RUNNING".equals(readinessStatus) && "BLOCKED".equals(workerHealth.get("status")))
        {
            return "BLOCKED";
        }
        if ("RUNNING".equals(readinessStatus) && "DEGRADED".equals(workerHealth.get("status")))
        {
            return "WARNING";
        }
        return readinessStatus;
    }

    private String todayStatusLabel(String code)
    {
        return switch (code)
        {
            case "OPERABLE" -> "可用";
            case "BLOCKED" -> "阻断";
            case "RUNNING" -> "运行中";
            case "EMPTY" -> "待执行";
            default -> "警告";
        };
    }

    private String defaultHeadlineAction(String code)
    {
        return switch (code)
        {
            case "OPERABLE" -> "进入量化看板";
            case "RUNNING" -> "等待当前任务完成";
            case "BLOCKED" -> "先处理阻断问题";
            case "EMPTY" -> "提交执行任务";
            default -> "去处理问题";
        };
    }

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

    private String urgency(String statusCode, Map<String, Object> readiness)
    {
        if ("BLOCKED".equals(statusCode))
        {
            return "now";
        }
        if ("WARNING".equals(statusCode))
        {
            return "before_decision";
        }
        if ("RUNNING".equals(statusCode))
        {
            return "later";
        }
        if (Boolean.TRUE.equals(readiness.get("canEnterDashboard")))
        {
            return "later";
        }
        return "now";
    }

    private Map<String, Object> statusPrimaryAction(String statusCode, Map<String, Object> preferredHint)
    {
        if ("OPERABLE".equals(statusCode))
        {
            return action("GO_DASHBOARD", "进入量化看板", "/quant/dashboard");
        }
        if ("RUNNING".equals(statusCode))
        {
            return action("VIEW_CURRENT_DISPATCH", "查看当前调度", "/quant/dispatch-detail");
        }
        if ("BLOCKED".equals(statusCode))
        {
            return action(
                    "GO_OPERATIONS",
                    stringValue(preferredHint.get("title"), "去运维中心处理"),
                    stringValue(preferredHint.get("targetPage"), "/quant/operations"));
        }
        if ("WARNING".equals(statusCode))
        {
            return action(
                    "GO_OPERATIONS",
                    "去运维中心处理",
                    stringValue(preferredHint.get("targetPage"), "/quant/operations"));
        }
        return action("RUN_EXECUTION", "提交执行任务", "/quant/dispatch-manual");
    }

    private Map<String, Object> statusSecondaryAction(String statusCode)
    {
        if ("OPERABLE".equals(statusCode))
        {
            return action("REFRESH_STATUS", "查看调度明细", "/quant/jobs");
        }
        if ("WARNING".equals(statusCode))
        {
            return action("GO_DASHBOARD", "继续进入看板", "/quant/dashboard");
        }
        if ("RUNNING".equals(statusCode))
        {
            return action("REFRESH_STATUS", "刷新状态", "/quant/jobs");
        }
        return null;
    }

    private boolean runningNeedsOperations(Map<String, Object> readiness, Map<String, Object> workerHealth)
    {
        String readinessStatus = stringValue(readiness.get("status"), "EMPTY");
        String workerStatus = stringValue(workerHealth.get("status"), "");
        return "RUNNING".equals(readinessStatus) && ("BLOCKED".equals(workerStatus) || "DEGRADED".equals(workerStatus));
    }

    private String progressOrderKey(Map<String, Object> step)
    {
        String endTime = stringValue(step.get("endTime"), "");
        if (!endTime.isBlank())
        {
            return endTime;
        }
        return stringValue(step.get("startTime"), "");
    }

    private boolean hasRunningStep(List<Map<String, Object>> steps)
    {
        return steps.stream().anyMatch(step -> "RUNNING".equals(step.get("status")));
    }

    private Map<String, Object> action(String code, String label, String targetPage)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("label", label);
        result.put("targetPage", targetPage);
        return result;
    }

    private String firstHintTitle(List<Map<String, Object>> hints, String fallback)
    {
        return hints.isEmpty() ? fallback : stringValue(hints.get(0).get("title"), fallback);
    }

    private String firstHintTargetPage(List<Map<String, Object>> hints, String fallback)
    {
        return hints.isEmpty() ? fallback : stringValue(hints.get(0).get("targetPage"), fallback);
    }

    private Map<String, Object> preferredHint(List<Map<String, Object>> hints, boolean preferWarning)
    {
        if (hints == null || hints.isEmpty())
        {
            return Map.of();
        }
        if (!preferWarning)
        {
            return hints.get(0);
        }
        for (Map<String, Object> hint : hints)
        {
            String level = stringValue(hint.get("level"), stringValue(hint.get("severity"), ""));
            if (!"success".equalsIgnoreCase(level))
            {
                return hint;
            }
        }
        return hints.get(0);
    }

    private Map<String, Object> safeMap(Map<String, Object> source)
    {
        return source == null ? new LinkedHashMap<>() : source;
    }

    private List<Map<String, Object>> safeList(List<Map<String, Object>> source)
    {
        return source == null ? List.of() : source;
    }

    private String stringValue(Object value, String fallback)
    {
        return value == null ? fallback : String.valueOf(value);
    }

    private Object value(Map<String, Object> row, String key, String fallback)
    {
        return value(row, key, null, fallback);
    }

    private Object value(Map<String, Object> row, String key, String alternateKey, String fallback)
    {
        if (row == null)
        {
            return fallback;
        }
        Object direct = row.get(key);
        if (direct != null && !String.valueOf(direct).isBlank())
        {
            return direct;
        }
        if (alternateKey != null)
        {
            Object alternate = row.get(alternateKey);
            if (alternate != null && !String.valueOf(alternate).isBlank())
            {
                return alternate;
            }
        }
        return fallback;
    }

    private int toInt(Object value)
    {
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        if (value == null)
        {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Long toLong(Object value)
    {
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank())
        {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Map<String, Object> firstHistoryRow(Map<String, Object> history)
    {
        Object rows = history.get("rows");
        if (rows instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map)
        {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return new LinkedHashMap<>();
    }
}
