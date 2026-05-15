package com.ruoyi.web.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.ruoyi.web.core.config.QuantRoadPythonProperties;
import com.ruoyi.web.domain.quant.QuantAsyncJobRequest;
import com.ruoyi.web.domain.quant.QuantExecutionPlan;
import com.ruoyi.web.domain.quant.QuantExecutionResponse;
import com.ruoyi.web.domain.quant.QuantAsyncJobResponse;
import com.ruoyi.web.domain.quant.QuantJobRequest;

public class QuantJobPlannerServiceTest
{
    @Test
    void submitRunStrategyAutoReturnsAsyncWhenEstimateExceedsBudget()
    {
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        properties.setRequestedModeDefault("auto");
        properties.setSyncExecutionBudgetSeconds(1L);
        properties.setShardSymbolChunkSize(2);
        properties.setRedisQueueKey("quant:jobs:queue:test");

        QuantJobRepository repository = mock(QuantJobRepository.class);
        when(repository.insertJob(any())).thenReturn(1001L);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.resolveScopeSymbols(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("000001", "000002", "000003"));

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        QuantJobPlannerService service = new QuantJobPlannerService(
                repository,
                redisTemplate,
                properties,
                mock(QuantRoadPythonService.class),
                symbolScopeService);
        QuantAsyncJobRequest request = new QuantAsyncJobRequest();
        request.setRequestedMode("auto");
        request.setStrategyId(1L);
        request.setStrategyBacktestStartDate("2023-01-01");
        request.setActor("planner-test");
        request.setScopeType("stock_pool");
        request.setScopePoolCode("STOCK_CORE");

        QuantAsyncJobResponse response = service.submitRunStrategy(request);

        assertNotNull(response);
        assertEquals(Long.valueOf(1001L), response.getJobId());
        assertEquals("async", response.getResolvedMode());
        assertEquals(Integer.valueOf(2), response.getPlannedShardCount());
        verify(repository, times(1)).insertShards(any(), anyList());
        verify(listOperations, times(2)).rightPush(any(), any());
        verify(symbolScopeService, times(1)).resolveScopeSymbols(
                eq("stock_pool"),
                eq("STOCK_CORE"),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void submitRunStrategyAsyncReturnsFailedReceiptWhenRedisIsUnavailable()
    {
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        properties.setRequestedModeDefault("auto");
        properties.setSyncExecutionBudgetSeconds(1L);
        properties.setShardSymbolChunkSize(2);
        properties.setRedisQueueKey("quant:jobs:queue:test");

        QuantJobRepository repository = mock(QuantJobRepository.class);
        when(repository.insertJob(any())).thenReturn(1001L);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.resolveScopeSymbols(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("000001", "000002", "000003"));

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        doThrow(new RuntimeException("Unable to connect to Redis")).when(listOperations).rightPush(any(), any());

        QuantJobPlannerService service = new QuantJobPlannerService(
                repository,
                redisTemplate,
                properties,
                mock(QuantRoadPythonService.class),
                symbolScopeService);
        QuantAsyncJobRequest request = new QuantAsyncJobRequest();
        request.setRequestedMode("async");
        request.setStrategyId(1L);
        request.setStrategyBacktestStartDate("2023-01-01");
        request.setActor("planner-test");

        QuantAsyncJobResponse response = service.submitRunStrategy(request);

        assertNotNull(response);
        assertEquals(Long.valueOf(1001L), response.getJobId());
        assertEquals("FAILED", response.getStatus());
        assertEquals("queue-unavailable", response.getReason());
        verify(repository, times(1)).markJobFailed(anyLong(), anyInt(), any());
    }

    @Test
    void submitRunStrategySyncUsesResolvedScopeSymbols()
    {
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        properties.setRequestedModeDefault("auto");
        properties.setSyncExecutionBudgetSeconds(999999L);
        properties.setStrategyBacktestStartDate("2023-01-01");

        QuantJobRepository repository = mock(QuantJobRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.resolveScopeSymbols(
                eq("etf_pool"),
                eq("ETF_CORE"),
                any(),
                any(),
                any(),
                any())).thenReturn(List.of("159915", "510300"));
        when(pythonService.runStrategy(1L, "2023-01-01", null, "scope-tester", List.of("159915", "510300")))
                .thenReturn("{\"status\":\"SUCCESS\"}");

        QuantJobPlannerService service = new QuantJobPlannerService(
                repository,
                redisTemplate,
                properties,
                pythonService,
                symbolScopeService);
        QuantAsyncJobRequest request = new QuantAsyncJobRequest();
        request.setRequestedMode("sync");
        request.setStrategyId(1L);
        request.setStrategyBacktestStartDate("2023-01-01");
        request.setActor("scope-tester");
        request.setScopeType("etf_pool");
        request.setScopePoolCode("ETF_CORE");

        QuantAsyncJobResponse response = service.submitRunStrategy(request);

        assertNotNull(response);
        assertEquals("sync", response.getResolvedMode());
        assertEquals("SUCCESS", response.getStatus());
        verify(symbolScopeService, times(2)).resolveScopeSymbols(
                eq("etf_pool"),
                eq("ETF_CORE"),
                any(),
                any(),
                any(),
                any());
        verify(pythonService, times(1)).runStrategy(1L, "2023-01-01", null, "scope-tester", List.of("159915", "510300"));
        verify(repository, never()).insertJob(any());
    }

    @Test
    void submitExecutionPlanBridgesAsyncStrategyReceipt()
    {
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        properties.setRequestedModeDefault("auto");
        properties.setSyncExecutionBudgetSeconds(1L);
        properties.setShardSymbolChunkSize(2);
        properties.setRedisQueueKey("quant:jobs:queue:test");

        QuantJobRepository repository = mock(QuantJobRepository.class);
        when(repository.insertJob(any())).thenReturn(2002L);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.resolveScopeSymbols(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("000001", "000002", "000003"));

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        QuantJobPlannerService service = new QuantJobPlannerService(
                repository,
                redisTemplate,
                properties,
                mock(QuantRoadPythonService.class),
                symbolScopeService);

        QuantExecutionPlan plan = new QuantExecutionPlan();
        plan.setResolvedExecutionMode("async");
        plan.setPlanSummary("mode=async, scope=all_stocks");
        plan.setEstimatedCost(Map.of("symbolCount", 3, "stepCount", 5));
        plan.setResolvedSymbols(List.of("000001", "000002", "000003"));

        QuantJobRequest request = new QuantJobRequest();
        request.setStrategyId(1L);
        request.setStrategyBacktestStartDate("2023-01-01");
        request.setActor("planner-test");
        request.setScopeType("all_stocks");

        QuantExecutionResponse response = service.submitExecutionPlan(plan, request);

        assertNotNull(response);
        assertEquals(Long.valueOf(2002L), response.getExecutionId());
        assertEquals("QUEUED", response.getStatus());
        assertEquals("async", response.getResolvedExecutionMode());
        assertEquals("mode=async, scope=all_stocks", response.getPlanSummary());
        ArgumentCaptor<List<QuantJobRepository.ShardRecord>> shardCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(1)).insertShards(any(), shardCaptor.capture());
        assertEquals(1, shardCaptor.getValue().size());
        assertEquals(3, shardCaptor.getValue().get(0).getSymbolCount());
        String payloadJson = shardCaptor.getValue().get(0).getPayloadJson();
        org.junit.jupiter.api.Assertions.assertTrue(payloadJson.contains("\"jobType\":\"execute-plan\""));
        org.junit.jupiter.api.Assertions.assertTrue(payloadJson.contains("\"executionPlan\""));
        org.junit.jupiter.api.Assertions.assertTrue(payloadJson.contains("\"stepCount\":5"));
        verify(listOperations, times(1)).rightPush(any(), any());
    }
}
