package com.ruoyi.web.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuantOperationsCenterServiceTest
{
    @Test
    void summaryBuildsWorkerBlockerAndCompatibilityToolbox()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantOperationsCenterService service = new QuantOperationsCenterService(queryService);

        when(queryService.jobReadiness(null)).thenReturn(Map.of(
                "status", "RUNNING",
                "batchId", 12L,
                "message", "盘后主流程仍在运行，请等待步骤完成。",
                "failedStepCount", 0,
                "warningStepCount", 0,
                "dataIntegrityStatus", "READY"));
        when(queryService.jobErrorCategories(null)).thenReturn(List.of());
        when(queryService.jobSopHints(null)).thenReturn(List.of());
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of(
                "status", "BLOCKED",
                "message", "存在待消费分片，但当前没有活跃 worker。",
                "queuedShardCount", 4,
                "runningShardCount", 0,
                "failedShardCount", 0,
                "expiredShardCount", 0,
                "pendingJobCount", 1,
                "runningJobCount", 0,
                "activeWorkerCount", 0,
                "workers", List.of()));
        when(queryService.jobBatches(5)).thenReturn(List.of(Map.of(
                "id", 12L,
                "pipeline_name", "full-daily",
                "status", "RUNNING")));

        Map<String, Object> summary = service.summary();
        @SuppressWarnings("unchecked")
        Map<String, Object> topBlocker = (Map<String, Object>) summary.get("topBlocker");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recoveryQueue = (List<Map<String, Object>>) summary.get("recoveryQueue");
        @SuppressWarnings("unchecked")
        Map<String, Object> toolbox = (Map<String, Object>) summary.get("toolbox");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> compatibilityActions = (List<Map<String, Object>>) toolbox.get("compatibilityActions");

        assertEquals("worker", topBlocker.get("layer"));
        assertFalse(recoveryQueue.isEmpty());
        assertEquals("recoverAsyncShards", recoveryQueue.get(0).get("code"));
        assertEquals("legacyFullDaily", compatibilityActions.get(0).get("code"));
    }

    @Test
    void summaryPrefersBatchBlockerWhenWorkerHealthy()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantOperationsCenterService service = new QuantOperationsCenterService(queryService);

        when(queryService.jobReadiness(null)).thenReturn(Map.of(
                "status", "BLOCKED",
                "batchId", 22L,
                "message", "步骤 sync-daily 失败，请先恢复后再进入看板。",
                "failedStepCount", 1,
                "warningStepCount", 0,
                "latestFailedStep", "sync-daily",
                "dataIntegrityStatus", "BLOCKED",
                "dataIntegrityCategory", "PARTIAL_DAILY_SYNC",
                "dataIntegrityMessage", "日线同步存在失败或空结果标的，建议先核对失败标的后再继续运营。",
                "canRecover", true));
        when(queryService.jobErrorCategories(null)).thenReturn(List.of(Map.of(
                "category", "UPSTREAM_NETWORK",
                "severity", "danger",
                "count", 1,
                "latestMessage", "Connection aborted",
                "suggestedAction", "优先点击恢复失败批次；若仍失败，再检查代理与外网访问。")));
        when(queryService.jobSopHints(null)).thenReturn(List.of(Map.of(
                "code", "recoverBatch",
                "title", "恢复失败批次",
                "targetPage", "/quant/operations")));
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of(
                "status", "ACTIVE",
                "message", "后台执行器正在消费分片。",
                "queuedShardCount", 0,
                "runningShardCount", 2,
                "failedShardCount", 0,
                "expiredShardCount", 0,
                "pendingJobCount", 0,
                "runningJobCount", 1,
                "activeWorkerCount", 1,
                "workers", List.of(Map.of("workerId", "worker-01"))));
        when(queryService.jobBatches(5)).thenReturn(List.of(Map.of(
                "id", 22L,
                "pipeline_name", "full-daily",
                "status", "FAILED")));

        Map<String, Object> summary = service.summary();
        @SuppressWarnings("unchecked")
        Map<String, Object> topBlocker = (Map<String, Object>) summary.get("topBlocker");
        @SuppressWarnings("unchecked")
        Map<String, Object> dataIntegrity = (Map<String, Object>) summary.get("dataIntegrity");

        assertEquals("batch", topBlocker.get("layer"));
        assertEquals("sync-daily", topBlocker.get("stepName"));
        assertEquals("BLOCKED", dataIntegrity.get("status"));
        assertTrue(((List<?>) summary.get("recoveryQueue")).size() >= 1);
    }

    @Test
    void summaryOnlyKeepsExecutableRecoveryActions()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantOperationsCenterService service = new QuantOperationsCenterService(queryService);

        when(queryService.jobReadiness(null)).thenReturn(Map.of(
                "status", "BLOCKED",
                "batchId", 22L,
                "message", "步骤 sync-daily 失败，请先恢复后再进入看板。",
                "failedStepCount", 1,
                "warningStepCount", 0,
                "latestFailedStep", "sync-daily",
                "dataIntegrityStatus", "BLOCKED"));
        when(queryService.jobErrorCategories(null)).thenReturn(List.of());
        when(queryService.jobSopHints(null)).thenReturn(List.of(
                Map.of(
                        "code", "genericRecover",
                        "title", "恢复失败步骤",
                        "targetPage", "/quant/operations",
                        "autoRecoverable", false),
                Map.of(
                        "code", "recoverBatch",
                        "title", "恢复失败批次",
                        "targetPage", "/quant/operations",
                        "autoRecoverable", true)));
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of(
                "status", "ACTIVE",
                "message", "后台执行器正在消费分片。"));
        when(queryService.jobBatches(5)).thenReturn(List.of(Map.of(
                "id", 22L,
                "pipeline_name", "full-daily",
                "status", "FAILED")));

        Map<String, Object> summary = service.summary();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recoveryQueue = (List<Map<String, Object>>) summary.get("recoveryQueue");

        assertEquals(1, recoveryQueue.size());
        assertEquals("recoverBatch", recoveryQueue.get(0).get("code"));
    }
}
