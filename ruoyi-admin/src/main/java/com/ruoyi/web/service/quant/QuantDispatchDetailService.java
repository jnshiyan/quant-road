package com.ruoyi.web.service.quant;

import com.alibaba.fastjson2.JSON;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.ruoyi.web.domain.quant.QuantAsyncJobStatusResponse;

@Service
public class QuantDispatchDetailService
{
    private final QuantRoadQueryService quantRoadQueryService;
    private final QuantJobPlannerService quantJobPlannerService;

    public QuantDispatchDetailService(
            QuantRoadQueryService quantRoadQueryService,
            QuantJobPlannerService quantJobPlannerService)
    {
        this.quantRoadQueryService = quantRoadQueryService;
        this.quantJobPlannerService = quantJobPlannerService;
    }

    public Map<String, Object> detailByJobId(Long jobId)
    {
        Map<String, Object> overview = new LinkedHashMap<>(safeMap(quantRoadQueryService.asyncJobDetail(jobId)));
        List<Map<String, Object>> shards = safeList(quantRoadQueryService.asyncJobShards(jobId));
        List<Map<String, Object>> results = safeList(quantRoadQueryService.asyncJobResults(jobId, 100));
        QuantAsyncJobStatusResponse status = quantJobPlannerService.getJobStatus(jobId);

        Long batchId = resolveCurrentBatchId(jobId);
        List<Map<String, Object>> events = batchId == null ? syntheticEvents(overview, shards, status) : safeList(quantRoadQueryService.jobSteps(batchId));
        List<Map<String, Object>> errorCategories = batchId == null ? syntheticErrors(status, shards) : safeList(quantRoadQueryService.jobErrorCategories(batchId));

        Map<String, Object> currentShard = findCurrentShard(shards);
        String phaseCode = resolvePhaseCode(status, currentShard);
        String waitingKind = resolveWaitingKind(status, currentShard);
        String waitingTarget = resolveWaitingTarget(status, currentShard);
        Map<String, Object> detailSummary = buildDetailSummary(overview, status, shards, results, errorCategories);
        overview.put("taskName", detailSummary.get("taskName"));
        overview.put("scopeSummary", detailSummary.get("scopeSummary"));
        overview.put("timeRangeSummary", detailSummary.get("timeRangeSummary"));
        overview.put("resultSummary", detailSummary.get("resultSummary"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", jobId);
        result.put("batchId", batchId);
        result.put("overview", overview);
        result.put("detailSummary", detailSummary);
        result.put("status", toStatusMap(status));
        result.put("phaseCode", phaseCode);
        result.put("phaseLabel", resolvePhaseLabel(phaseCode));
        result.put("phaseStatus", status == null ? "UNKNOWN" : status.getStatus());
        result.put("waitingKind", waitingKind);
        result.put("waitingTarget", waitingTarget);
        result.put("currentShard", currentShard);
        result.put("currentSymbols", currentShard.get("symbolsPreview") instanceof List<?> list ? list : List.of());
        result.put("shards", shards);
        result.put("results", results);
        result.put("events", events);
        result.put("errorCategories", errorCategories);
        return result;
    }

    private Map<String, Object> buildDetailSummary(
            Map<String, Object> overview,
            QuantAsyncJobStatusResponse status,
            List<Map<String, Object>> shards,
            List<Map<String, Object>> results,
            List<Map<String, Object>> errorCategories)
    {
        Map<String, Object> requestPayload = parseJsonObject(overview.get("requestPayload"));
        Map<String, Object> request = nestedMap(requestPayload, "request");
        Map<String, Object> executionPlan = nestedMap(requestPayload, "executionPlan");
        Map<String, Object> normalizedPayload = parseJsonObject(overview.get("normalizedPayload"));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskName", resolveTaskName(overview, executionPlan));
        summary.put("scopeSummary", resolveScopeSummary(overview, request, executionPlan));
        summary.put("timeRangeSummary", resolveTimeRangeSummary(overview, request));
        summary.put("executionSummary", resolveExecutionSummary(executionPlan, normalizedPayload));
        summary.put("resultSummary", resolveResultSummary(status, results, errorCategories, overview));
        summary.put("resultCount", results.size());
        summary.put("errorCount", errorCategories.size());
        summary.put("shardProgress", shardProgress(status));
        summary.put("triggerModeLabel", stringValue(overview.get("triggerModeLabel"), "-"));
        summary.put("startedAt", stringValue(overview.get("startedAt"), "-"));
        summary.put("finishedAt", stringValue(overview.get("finishedAt"), stringValue(overview.get("endTime"), "-")));
        return summary;
    }

    private Long resolveCurrentBatchId(Long jobId)
    {
        Map<String, Object> readiness = safeMap(quantRoadQueryService.jobReadiness(null));
        if (readiness.get("batchId") == null)
        {
            return null;
        }
        List<Map<String, Object>> asyncJobs = safeList(quantRoadQueryService.asyncJobs(1));
        if (asyncJobs.isEmpty())
        {
            return null;
        }
        Object latestJobId = asyncJobs.get(0).get("id");
        if (latestJobId == null || !String.valueOf(latestJobId).equals(String.valueOf(jobId)))
        {
            return null;
        }
        return toLong(readiness.get("batchId"));
    }

    private List<Map<String, Object>> syntheticEvents(
            Map<String, Object> overview,
            List<Map<String, Object>> shards,
            QuantAsyncJobStatusResponse status)
    {
        Map<String, Object> submitted = new LinkedHashMap<>();
        submitted.put("stepName", "dispatch-submitted");
        submitted.put("status", "INFO");
        submitted.put("startTime", overview.get("startedAt"));
        submitted.put("message", "调度已提交");

        Map<String, Object> queueing = new LinkedHashMap<>();
        queueing.put("stepName", "queue-shards");
        queueing.put("status", status == null ? "UNKNOWN" : status.getStatus());
        queueing.put("startTime", overview.get("startedAt"));
        queueing.put("message", "系统正在调度分片");

        if (shards.isEmpty())
        {
            return List.of(submitted, queueing);
        }

        Map<String, Object> shardEvent = new LinkedHashMap<>();
        Map<String, Object> currentShard = findCurrentShard(shards);
        shardEvent.put("stepName", String.valueOf(currentShard.getOrDefault("shardKey", currentShard.getOrDefault("shard_key", "run-shard"))));
        shardEvent.put("status", currentShard.get("status"));
        shardEvent.put("startTime", currentShard.get("startTime"));
        shardEvent.put("endTime", currentShard.get("endTime"));
        shardEvent.put("message", currentShard.get("symbolsText"));
        return List.of(submitted, queueing, shardEvent);
    }

    private List<Map<String, Object>> syntheticErrors(QuantAsyncJobStatusResponse status, List<Map<String, Object>> shards)
    {
        if (status == null || (!"FAILED".equals(status.getStatus()) && !"PARTIAL_FAILED".equals(status.getStatus())))
        {
            return List.of();
        }
        String message = status.getErrorMessage();
        if (message == null || message.isBlank())
        {
            message = shards.stream()
                    .map(item -> item.get("lastError"))
                    .filter(value -> value != null && !String.valueOf(value).isBlank())
                    .map(String::valueOf)
                    .findFirst()
                    .orElse("调度失败");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", "DISPATCH_FAILED");
        result.put("count", 1);
        result.put("latestMessage", message);
        return List.of(result);
    }

    private Map<String, Object> findCurrentShard(List<Map<String, Object>> shards)
    {
        for (Map<String, Object> shard : shards)
        {
            if ("RUNNING".equals(shard.get("status")))
            {
                return shard;
            }
        }
        if (!shards.isEmpty())
        {
            return shards.get(0);
        }
        return Map.of();
    }

    private String resolvePhaseCode(QuantAsyncJobStatusResponse status, Map<String, Object> currentShard)
    {
        if (status == null)
        {
            return "UNKNOWN";
        }
        if ("SUCCESS".equals(status.getStatus()))
        {
            return "FINISHED";
        }
        if ("FAILED".equals(status.getStatus()) || "PARTIAL_FAILED".equals(status.getStatus()))
        {
            return "FAILED";
        }
        if ("RUNNING".equals(status.getStatus()))
        {
            return "RUN_STRATEGY";
        }
        if ("QUEUED".equals(status.getStatus()) || "PENDING".equals(status.getStatus()))
        {
            return currentShard.isEmpty() ? "PREPARE" : "QUEUE";
        }
        return "PREPARE";
    }

    private String resolvePhaseLabel(String phaseCode)
    {
        return switch (phaseCode)
        {
            case "FINISHED" -> "完成";
            case "FAILED" -> "失败";
            case "RUN_STRATEGY" -> "执行策略";
            case "QUEUE" -> "生成分片";
            default -> "准备参数";
        };
    }

    private String resolveWaitingKind(QuantAsyncJobStatusResponse status, Map<String, Object> currentShard)
    {
        if (status == null)
        {
            return "NONE";
        }
        if ("SUCCESS".equals(status.getStatus()) || "FAILED".equals(status.getStatus()) || "PARTIAL_FAILED".equals(status.getStatus()))
        {
            return "NONE";
        }
        if ("RUNNING".equals(status.getStatus()) && !currentShard.isEmpty())
        {
            return "COMPUTING";
        }
        if ("QUEUED".equals(status.getStatus()) || "PENDING".equals(status.getStatus()))
        {
            return "QUEUE";
        }
        return "NONE";
    }

    private String resolveWaitingTarget(QuantAsyncJobStatusResponse status, Map<String, Object> currentShard)
    {
        String waitingKind = resolveWaitingKind(status, currentShard);
        if ("COMPUTING".equals(waitingKind))
        {
            Object worker = currentShard.get("leaseOwner");
            return worker == null || String.valueOf(worker).isBlank() ? "当前分片计算中" : String.valueOf(worker);
        }
        if ("QUEUE".equals(waitingKind))
        {
            return "等待 Worker 消费分片";
        }
        return null;
    }

    private Map<String, Object> toStatusMap(QuantAsyncJobStatusResponse status)
    {
        if (status == null)
        {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", status.getJobId());
        result.put("jobType", status.getJobType());
        result.put("requestedMode", status.getRequestedMode());
        result.put("resolvedMode", status.getResolvedMode());
        result.put("resolvedExecutionMode", status.getResolvedMode());
        result.put("status", status.getStatus());
        result.put("plannedShardCount", status.getPlannedShardCount());
        result.put("completedShardCount", status.getCompletedShardCount());
        result.put("failedShardCount", status.getFailedShardCount());
        result.put("cancelRequested", status.getCancelRequested());
        result.put("errorMessage", status.getErrorMessage());
        return result;
    }

    private String resolveTaskName(Map<String, Object> overview, Map<String, Object> executionPlan)
    {
        String taskName = stringValue(overview.get("taskName"), "");
        if (!taskName.isBlank() && !"execute-plan".equalsIgnoreCase(taskName))
        {
            return taskName;
        }
        if (!executionPlan.isEmpty())
        {
            return "手工调度任务";
        }
        return taskName.isBlank() ? "量化调度任务" : taskName;
    }

    private String resolveScopeSummary(
            Map<String, Object> overview,
            Map<String, Object> request,
            Map<String, Object> executionPlan)
    {
        String scopeSummary = stringValue(overview.get("scopeSummary"), "");
        if (!scopeSummary.isBlank() && !"未记录范围".equals(scopeSummary))
        {
            return scopeSummary;
        }
        String scopeType = stringValue(request.get("scopeType"), "");
        String scopePoolCode = stringValue(request.get("scopePoolCode"), "");
        int symbolCount = listSize(executionPlan.get("resolvedSymbols"));
        if (!scopeType.isBlank() && !scopePoolCode.isBlank())
        {
            return scopeType + " / " + scopePoolCode + " / " + symbolCount + " 个标的";
        }
        if (!scopeType.isBlank())
        {
            return symbolCount > 0 ? scopeType + " / " + symbolCount + " 个标的" : scopeType;
        }
        if (symbolCount > 0)
        {
            return symbolCount + " 个标的";
        }
        return "未记录范围";
    }

    private String resolveTimeRangeSummary(Map<String, Object> overview, Map<String, Object> request)
    {
        String timeRangeSummary = stringValue(overview.get("timeRangeSummary"), "");
        if (!timeRangeSummary.isBlank() && !"未记录时间范围".equals(timeRangeSummary))
        {
            return timeRangeSummary;
        }
        String startDate = stringValue(request.get("strategyBacktestStartDate"), "");
        String endDate = stringValue(request.get("endDate"), "");
        if (startDate.isBlank() && endDate.isBlank())
        {
            return "未记录时间范围";
        }
        return startDate + " ~ " + (endDate.isBlank() ? startDate : endDate);
    }

    private String resolveExecutionSummary(Map<String, Object> executionPlan, Map<String, Object> normalizedPayload)
    {
        Object stepsValue = executionPlan.get("steps");
        if (stepsValue instanceof List<?> list && !list.isEmpty())
        {
            return list.stream()
                    .filter(item -> item instanceof Map<?, ?>)
                    .map(item -> ((Map<?, ?>) item).get("stepName"))
                    .filter(stepName -> stepName != null && !String.valueOf(stepName).isBlank())
                    .map(String::valueOf)
                    .reduce((left, right) -> left + " -> " + right)
                    .orElse("未记录执行步骤");
        }
        String planSummary = stringValue(normalizedPayload.get("planSummary"), "");
        return planSummary.isBlank() ? "未记录执行步骤" : planSummary;
    }

    private String resolveResultSummary(
            QuantAsyncJobStatusResponse status,
            List<Map<String, Object>> results,
            List<Map<String, Object>> errorCategories,
            Map<String, Object> overview)
    {
        if (status != null && "FAILED".equals(status.getStatus()))
        {
            return stringValue(status.getErrorMessage(), stringValue(overview.get("resultSummary"), "调度失败"));
        }
        if (status != null && "PARTIAL_FAILED".equals(status.getStatus()))
        {
            return "产出 " + results.size() + " 条结果，异常 " + errorCategories.size() + " 类";
        }
        if (status != null && "RUNNING".equals(status.getStatus()))
        {
            return "执行中，当前已产出 " + results.size() + " 条结果";
        }
        if (status != null && ("QUEUED".equals(status.getStatus()) || "PENDING".equals(status.getStatus())))
        {
            return "等待执行";
        }
        if (!results.isEmpty() || !errorCategories.isEmpty())
        {
            return "产出 " + results.size() + " 条结果，异常 " + errorCategories.size() + " 类";
        }
        return "当前任务已完成，但没有结果明细";
    }

    private String shardProgress(QuantAsyncJobStatusResponse status)
    {
        if (status == null)
        {
            return "-";
        }
        return status.getCompletedShardCount() + " / " + status.getPlannedShardCount() + " 分片";
    }

    private Map<String, Object> safeMap(Map<String, Object> source)
    {
        return source == null ? new LinkedHashMap<>() : source;
    }

    private List<Map<String, Object>> safeList(List<Map<String, Object>> source)
    {
        return source == null ? List.of() : source;
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

    private Map<String, Object> parseJsonObject(Object source)
    {
        if (source == null)
        {
            return new LinkedHashMap<>();
        }
        if (source instanceof Map<?, ?> map)
        {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        String text = String.valueOf(source);
        if (text.isBlank())
        {
            return new LinkedHashMap<>();
        }
        try
        {
            return JSON.parseObject(text);
        }
        catch (RuntimeException ex)
        {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> nestedMap(Map<String, Object> source, String key)
    {
        return parseJsonObject(source.get(key));
    }

    private String stringValue(Object value, String fallback)
    {
        if (value == null)
        {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private int listSize(Object value)
    {
        return value instanceof List<?> list ? list.size() : 0;
    }
}
