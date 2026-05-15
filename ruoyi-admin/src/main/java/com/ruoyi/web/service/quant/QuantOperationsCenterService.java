package com.ruoyi.web.service.quant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class QuantOperationsCenterService
{
    private static final List<String> SUPPORTED_RECOVERY_CODES = List.of("recoverAsyncShards", "recoverBatch");

    private final QuantRoadQueryService quantRoadQueryService;

    public QuantOperationsCenterService(QuantRoadQueryService quantRoadQueryService)
    {
        this.quantRoadQueryService = quantRoadQueryService;
    }

    public Map<String, Object> summary()
    {
        Map<String, Object> workerHealth = safeMap(quantRoadQueryService.asyncWorkerSummary());
        Map<String, Object> readiness = safeMap(quantRoadQueryService.jobReadiness(null));
        List<Map<String, Object>> hints = safeList(quantRoadQueryService.jobSopHints(null));
        List<Map<String, Object>> errorCategories = safeList(quantRoadQueryService.jobErrorCategories(null));
        List<Map<String, Object>> batches = safeList(quantRoadQueryService.jobBatches(5));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topBlocker", topBlocker(workerHealth, readiness, errorCategories, batches));
        result.put("recoveryQueue", recoveryQueue(hints, workerHealth));
        result.put("workerHealth", workerHealth);
        result.put("dataIntegrity", dataIntegrity(readiness));
        result.put("toolbox", toolbox(batches));
        return result;
    }

    private Map<String, Object> topBlocker(
            Map<String, Object> workerHealth,
            Map<String, Object> readiness,
            List<Map<String, Object>> errorCategories,
            List<Map<String, Object>> batches)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if ("BLOCKED".equals(workerHealth.get("status")))
        {
            result.put("layer", "worker");
            result.put("title", "无活跃 worker");
            result.put("impact", "阻断执行");
            result.put("reason", stringValue(workerHealth.get("message"), "当前没有可消费分片的 worker。"));
            return result;
        }
        if ("BLOCKED".equals(readiness.get("status")))
        {
            result.put("layer", "batch");
            result.put("title", "盘后批次阻断");
            result.put("impact", "阻断今日流程");
            result.put("reason", stringValue(readiness.get("message"), "存在需要恢复的失败步骤。"));
            result.put("stepName", stringValue(readiness.get("latestFailedStep"), null));
            if (!errorCategories.isEmpty())
            {
                result.put("category", errorCategories.get(0).get("category"));
            }
            if (!batches.isEmpty())
            {
                result.put("batchId", batches.get(0).get("id"));
            }
            return result;
        }
        if ("READY_WITH_WARNINGS".equals(readiness.get("status")))
        {
            result.put("layer", "data");
            result.put("title", "存在数据完整性警告");
            result.put("impact", "影响业务判断");
            result.put("reason", stringValue(readiness.get("dataIntegrityMessage"), "建议先确认告警项。"));
            return result;
        }
        result.put("layer", "none");
        result.put("title", "暂无阻断");
        result.put("impact", "无影响");
        result.put("reason", stringValue(readiness.get("message"), "当前运维状态稳定。"));
        return result;
    }

    private List<Map<String, Object>> recoveryQueue(List<Map<String, Object>> hints, Map<String, Object> workerHealth)
    {
        List<Map<String, Object>> queue = hints.stream()
                .filter(hint -> "/quant/operations".equals(hint.get("targetPage")))
                .filter(hint -> SUPPORTED_RECOVERY_CODES.contains(String.valueOf(hint.get("code"))))
                .map(hint -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("code", hint.get("code"));
                    item.put("label", hint.get("title"));
                    item.put("targetPage", hint.get("targetPage"));
                    item.put("autoRecoverable", hint.get("autoRecoverable"));
                    item.put("executable", true);
                    return item;
                })
                .toList();
        if (!queue.isEmpty())
        {
            return queue;
        }
        if ("BLOCKED".equals(workerHealth.get("status")))
        {
            return List.of(recoveryItem("recoverAsyncShards", "恢复异步分片", "/quant/operations", true));
        }
        return List.of();
    }

    private Map<String, Object> dataIntegrity(Map<String, Object> readiness)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", stringValue(readiness.get("dataIntegrityStatus"), "UNKNOWN"));
        result.put("category", stringValue(readiness.get("dataIntegrityCategory"), ""));
        result.put("message", stringValue(readiness.get("dataIntegrityMessage"), "暂无数据完整性说明。"));
        result.put("canEnterDashboard", readiness.getOrDefault("canEnterDashboard", false));
        return result;
    }

    private Map<String, Object> toolbox(List<Map<String, Object>> batches)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("compatibilityActions", List.of(
                Map.of(
                        "code", "legacyFullDaily",
                        "label", "兼容旧版 full-daily",
                        "targetPage", "/quant/operations"),
                Map.of(
                        "code", "openAsyncJobs",
                        "label", "查看异步作业",
                        "targetPage", "/quant/operations")));
        result.put("recentBatches", batches);
        result.put("navigation", List.of(
                Map.of("code", "taskCenter", "label", "返回任务中心", "targetPage", "/quant/jobs"),
                Map.of("code", "executionWriteback", "label", "进入执行回写", "targetPage", "/quant/execution")));
        return result;
    }

    private Map<String, Object> recoveryItem(String code, String label, String targetPage, boolean autoRecoverable)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("label", label);
        result.put("targetPage", targetPage);
        result.put("autoRecoverable", autoRecoverable);
        result.put("executable", true);
        return result;
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

}
