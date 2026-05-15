package com.ruoyi.web.service.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ruoyi.web.domain.quant.QuantAsyncJobStatusResponse;

class QuantDispatchDetailServiceTest
{
    @Test
    void detailByJobIdShouldExposeExplicitExecutionAndResultSummary()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantJobPlannerService plannerService = mock(QuantJobPlannerService.class);

        when(queryService.asyncJobDetail(5L)).thenReturn(Map.of(
                "id", 5L,
                "jobType", "execute-plan",
                "taskName", "execute-plan",
                "triggerModeLabel", "手工触发",
                "scopeSummary", "未记录范围",
                "timeRangeSummary", "未记录时间范围",
                "resultSummary", "调度已完成",
                "startedAt", "2026-05-13 09:38:41",
                "requestPayload", "{\"executionPlan\":{\"resolvedSymbols\":[\"510300\",\"159915\"],\"steps\":[{\"stepName\":\"sync-daily\"},{\"stepName\":\"run-strategy\"},{\"stepName\":\"evaluate-risk\"}]},\"request\":{\"scopeType\":\"etf_pool\",\"scopePoolCode\":\"ETF_POOL\",\"strategyBacktestStartDate\":\"2021-05-10\",\"endDate\":\"2026-05-10\"}}"));
        when(queryService.asyncJobShards(5L)).thenReturn(List.of(
                Map.of(
                        "shardIndex", 0,
                        "status", "SUCCESS",
                        "symbolsPreview", List.of("510300", "159915"),
                        "symbolsText", "510300,159915",
                        "symbolCount", 2)));
        when(queryService.asyncJobResults(5L, 100)).thenReturn(List.of(
                Map.of("stock_code", "510300", "signal_type", "BUY")));
        when(queryService.jobReadiness(null)).thenReturn(Map.of());
        when(queryService.asyncJobs(1)).thenReturn(List.of());

        QuantAsyncJobStatusResponse status = new QuantAsyncJobStatusResponse();
        status.setJobId(5L);
        status.setStatus("SUCCESS");
        status.setResolvedMode("async");
        status.setPlannedShardCount(1);
        status.setCompletedShardCount(1);
        status.setFailedShardCount(0);
        when(plannerService.getJobStatus(5L)).thenReturn(status);

        QuantDispatchDetailService service = new QuantDispatchDetailService(queryService, plannerService);

        Map<String, Object> result = service.detailByJobId(5L);

        @SuppressWarnings("unchecked")
        Map<String, Object> detailSummary = (Map<String, Object>) result.get("detailSummary");
        assertThat(detailSummary).containsEntry("scopeSummary", "etf_pool / ETF_POOL / 2 个标的");
        assertThat(detailSummary).containsEntry("timeRangeSummary", "2021-05-10 ~ 2026-05-10");
        assertThat(detailSummary).containsEntry("executionSummary", "sync-daily -> run-strategy -> evaluate-risk");
        assertThat(detailSummary).containsEntry("resultSummary", "产出 1 条结果，异常 0 类");
    }

    @Test
    void detailByJobIdShouldExposePhaseWaitingAndCurrentSymbols()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantJobPlannerService plannerService = mock(QuantJobPlannerService.class);

        when(queryService.asyncJobDetail(88L)).thenReturn(Map.of(
                "id", 88L,
                "taskName", "盘后主流程",
                "triggerModeLabel", "手工触发",
                "scopeSummary", "etf_pool / ETF_POOL / 2 个标的",
                "timeRangeSummary", "2021-05-10 ~ 2026-05-10",
                "startedAt", "2026-05-10 18:00:00"));
        when(queryService.asyncJobShards(88L)).thenReturn(List.of(
                Map.of(
                        "shardIndex", 0,
                        "status", "RUNNING",
                        "leaseOwner", "ruoyi-ui-worker",
                        "symbolsPreview", List.of("510300"),
                        "symbolsText", "510300",
                        "symbolCount", 1)));
        when(queryService.asyncJobResults(88L, 100)).thenReturn(List.of(
                Map.of("stock_code", "510300", "signal_type", "BUY")));
        when(queryService.jobReadiness(null)).thenReturn(Map.of("batchId", 12L));
        when(queryService.asyncJobs(1)).thenReturn(List.of(Map.of("id", 88L)));
        when(queryService.jobSteps(12L)).thenReturn(List.of(
                Map.of("stepName", "run-strategy", "status", "RUNNING")));
        when(queryService.jobErrorCategories(12L)).thenReturn(List.of());

        QuantAsyncJobStatusResponse status = new QuantAsyncJobStatusResponse();
        status.setJobId(88L);
        status.setStatus("RUNNING");
        status.setResolvedMode("async");
        status.setPlannedShardCount(1);
        status.setCompletedShardCount(0);
        status.setFailedShardCount(0);
        when(plannerService.getJobStatus(88L)).thenReturn(status);

        QuantDispatchDetailService service = new QuantDispatchDetailService(queryService, plannerService);

        Map<String, Object> result = service.detailByJobId(88L);

        assertThat(result).containsEntry("phaseCode", "RUN_STRATEGY");
        assertThat(result).containsEntry("waitingKind", "COMPUTING");
        assertThat(result).containsEntry("waitingTarget", "ruoyi-ui-worker");
        assertThat(result).containsEntry("batchId", 12L);
        assertThat(result.get("currentSymbols")).isEqualTo(List.of("510300"));
    }
}
