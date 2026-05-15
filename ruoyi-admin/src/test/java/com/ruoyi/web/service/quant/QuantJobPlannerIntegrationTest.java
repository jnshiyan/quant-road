package com.ruoyi.web.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.ruoyi.web.core.config.QuantRoadPythonProperties;
import com.ruoyi.web.domain.quant.QuantAsyncJobRequest;
import com.ruoyi.web.domain.quant.QuantAsyncJobResponse;

class QuantJobPlannerIntegrationTest
{
    @Test
    void submitAsyncJobPersistsPlannedShards()
    {
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        properties.setSyncExecutionBudgetSeconds(1L);
        properties.setShardSymbolChunkSize(2);
        properties.setRedisQueueKey("quant:jobs:queue:test");

        InMemoryQuantJobRepository repository = new InMemoryQuantJobRepository();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.resolveScopeSymbols(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("000001", "000002", "000003"));
        QuantJobPlannerService planner = new QuantJobPlannerService(
                repository,
                redisTemplate,
                properties,
                mock(QuantRoadPythonService.class),
                symbolScopeService);

        QuantAsyncJobRequest request = new QuantAsyncJobRequest();
        request.setRequestedMode("async");
        request.setStrategyId(1L);
        request.setStrategyBacktestStartDate("2023-01-01");
        request.setSymbols(List.of("000001", "000002", "000003"));
        request.setActor("integration-test");

        QuantAsyncJobResponse response = planner.submitRunStrategy(request);

        assertTrue(repository.countShards(response.getJobId()) > 0);
        assertEquals(2, repository.countShards(response.getJobId()));
    }

    @Test
    void submitAsyncJobReturnsFailedStatusWhenQueuePushFails()
    {
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        properties.setSyncExecutionBudgetSeconds(1L);
        properties.setShardSymbolChunkSize(2);
        properties.setRedisQueueKey("quant:jobs:queue:test");

        InMemoryQuantJobRepository repository = new InMemoryQuantJobRepository();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        doThrow(new RuntimeException("Unable to connect to Redis")).when(listOperations).rightPush(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.resolveScopeSymbols(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("000001", "000002", "000003"));
        QuantJobPlannerService planner = new QuantJobPlannerService(
                repository,
                redisTemplate,
                properties,
                mock(QuantRoadPythonService.class),
                symbolScopeService);

        QuantAsyncJobRequest request = new QuantAsyncJobRequest();
        request.setRequestedMode("async");
        request.setStrategyId(1L);
        request.setStrategyBacktestStartDate("2023-01-01");
        request.setSymbols(List.of("000001", "000002", "000003"));
        request.setActor("integration-test");

        QuantAsyncJobResponse response = planner.submitRunStrategy(request);

        assertEquals("FAILED", response.getStatus());
        assertEquals("queue-unavailable", response.getReason());
    }

    private static class InMemoryQuantJobRepository extends QuantJobRepository
    {
        private long nextJobId = 1000L;
        private final List<ShardRecord> shards = new ArrayList<>();

        InMemoryQuantJobRepository()
        {
            super(null);
        }
        @Override
        public Long insertJob(JobRecord record)
        {
            return ++nextJobId;
        }

        @Override
        public void insertSummary(Long jobId, int totalSymbols, int totalStrategies, String payloadJson)
        {
        }

        @Override
        public void insertShards(Long jobId, List<ShardRecord> rows)
        {
            shards.addAll(rows);
        }

        @Override
        public int countShards(Long jobId)
        {
            return shards.size();
        }

        @Override
        public void markJobFailed(Long jobId, int failedShardCount, String errorMessage)
        {
        }
    }
}
