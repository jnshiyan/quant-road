package com.ruoyi.web.service.quant;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.core.config.QuantRoadPythonProperties;
import com.ruoyi.web.domain.quant.QuantAsyncJobRequest;
import com.ruoyi.web.domain.quant.QuantAsyncJobResponse;
import com.ruoyi.web.domain.quant.QuantAsyncJobStatusResponse;
import com.ruoyi.web.domain.quant.QuantExecutionPlan;
import com.ruoyi.web.domain.quant.QuantExecutionResponse;
import com.ruoyi.web.domain.quant.QuantJobCostEstimate;
import com.ruoyi.web.domain.quant.QuantJobRequest;
import com.ruoyi.web.service.quant.QuantJobRepository.JobRecord;
import com.ruoyi.web.service.quant.QuantJobRepository.ShardRecord;

@Service
public class QuantJobPlannerService
{
    private final QuantJobRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final QuantRoadPythonProperties properties;
    private final QuantRoadPythonService pythonService;
    private final QuantRoadSymbolScopeService symbolScopeService;

    public QuantJobPlannerService(
            QuantJobRepository repository,
            StringRedisTemplate redisTemplate,
            QuantRoadPythonProperties properties,
            QuantRoadPythonService pythonService,
            QuantRoadSymbolScopeService symbolScopeService)
    {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.pythonService = pythonService;
        this.symbolScopeService = symbolScopeService;
    }

    public QuantAsyncJobResponse submitRunStrategy(QuantAsyncJobRequest request)
    {
        QuantAsyncJobRequest payload = normalizeRequest(request);
        List<String> symbols = resolveScopeSymbols(payload);
        List<Long> strategyIds = resolveStrategyIds(payload, false);
        QuantJobCostEstimate estimate = estimate(strategyIds.size(), symbols.size(), payload.getStrategyBacktestStartDate(), payload.getEndDate());
        String requestedMode = resolveRequestedMode(payload.getRequestedMode());
        String resolvedMode = resolveMode(requestedMode, estimate);
        if ("sync".equalsIgnoreCase(resolvedMode))
        {
            return runSyncStrategy(payload, estimate, requestedMode);
        }
        return submitAsyncJob("run-strategy", payload, strategyIds, symbols, estimate, requestedMode, resolvedMode);
    }

    public QuantAsyncJobResponse submitRunPortfolio(QuantAsyncJobRequest request)
    {
        QuantAsyncJobRequest payload = normalizeRequest(request);
        List<String> symbols = resolveScopeSymbols(payload);
        List<Long> strategyIds = resolveStrategyIds(payload, true);
        QuantJobCostEstimate estimate = estimate(strategyIds.size(), symbols.size(), payload.getStrategyBacktestStartDate(), payload.getEndDate());
        String requestedMode = resolveRequestedMode(payload.getRequestedMode());
        String resolvedMode = resolveMode(requestedMode, estimate);
        if ("sync".equalsIgnoreCase(resolvedMode))
        {
            return runSyncPortfolio(payload, estimate, requestedMode);
        }
        return submitAsyncJob("run-portfolio", payload, strategyIds, symbols, estimate, requestedMode, resolvedMode);
    }

    public QuantExecutionResponse submitExecutionPlan(QuantExecutionPlan plan, QuantJobRequest request)
    {
        QuantExecutionPlan resolvedPlan = plan == null ? new QuantExecutionPlan() : plan;
        QuantJobRequest payload = request == null ? new QuantJobRequest() : request;
        String requestedMode = resolveRequestedMode(payload.getRequestedMode());
        if ("sync".equalsIgnoreCase(requestedMode))
        {
            throw new ServiceException("sync execution budget exceeded; resubmit with requestedMode=async or auto");
        }
        QuantAsyncJobResponse asyncResponse = submitPlannedAsyncJob(resolvedPlan, payload, requestedMode);

        QuantExecutionResponse response = new QuantExecutionResponse();
        response.setExecutionId(asyncResponse.getJobId());
        response.setStatus(asyncResponse.getStatus());
        response.setResolvedExecutionMode(asyncResponse.getResolvedMode());
        response.setPlanSummary(resolvedPlan.getPlanSummary());
        response.setEstimatedCost(asyncResponse.getCostEstimate() == null ? resolvedPlan.getEstimatedCost() : asyncResponse.getCostEstimate());
        return response;
    }

    private QuantAsyncJobResponse submitPlannedAsyncJob(QuantExecutionPlan plan, QuantJobRequest request, String requestedMode)
    {
        String jobKey = "quant-job-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String actor = blankToDefault(request.getActor(), "system");
        Map<String, Object> shardPayload = new LinkedHashMap<>();
        shardPayload.put("jobType", "execute-plan");
        shardPayload.put("executionPlan", plan);
        shardPayload.put("request", request);

        QuantJobRepository.JobRecord record = new QuantJobRepository.JobRecord();
        record.setJobKey(jobKey);
        record.setJobType("execute-plan");
        record.setRequestedMode(requestedMode);
        record.setResolvedMode("async");
        record.setStatus("QUEUED");
        record.setActor(actor);
        record.setRequestJson(JSON.toJSONString(Map.of("executionPlan", plan, "request", request)));
        record.setNormalizedJson(JSON.toJSONString(Map.of("jobType", "execute-plan", "planSummary", plan.getPlanSummary())));
        record.setEstimateJson(JSON.toJSONString(plan.getEstimatedCost() == null ? Map.of() : plan.getEstimatedCost()));
        record.setPlannedShardCount(1);
        Long jobId = repository.insertJob(record);

        List<String> resolvedSymbols = plan.getResolvedSymbols() == null ? List.of() : plan.getResolvedSymbols();
        repository.insertSummary(jobId, resolvedSymbols.size(), resolveExecutionPlanStrategyCount(request), JSON.toJSONString(Map.of("jobKey", jobKey)));

        QuantJobRepository.ShardRecord shard = new QuantJobRepository.ShardRecord();
        shard.setShardKey(jobKey + "-shard-0");
        shard.setStrategyId(request.getStrategyId());
        shard.setShardIndex(0);
        shard.setStatus("QUEUED");
        shard.setSymbolCount(resolvedSymbols.size());
        shard.setPayloadJson(JSON.toJSONString(shardPayload));
        repository.insertShards(jobId, List.of(shard));
        try
        {
            redisTemplate.opsForList().rightPush(properties.getRedisQueueKey(), shard.getShardKey());
        }
        catch (RuntimeException ex)
        {
            String errorMessage = "Redis queue unavailable: " + rootCauseMessage(ex);
            repository.markJobFailed(jobId, 1, errorMessage);

            QuantAsyncJobResponse failedResponse = new QuantAsyncJobResponse();
            failedResponse.setJobId(jobId);
            failedResponse.setJobType("execute-plan");
            failedResponse.setRequestedMode(requestedMode);
            failedResponse.setResolvedMode("async");
            failedResponse.setStatus("FAILED");
            failedResponse.setCostEstimate(null);
            failedResponse.setPlannedShardCount(1);
            failedResponse.setReason("queue-unavailable");
            failedResponse.setOutput(errorMessage);
            return failedResponse;
        }

        QuantAsyncJobResponse response = new QuantAsyncJobResponse();
        response.setJobId(jobId);
        response.setJobType("execute-plan");
        response.setRequestedMode(requestedMode);
        response.setResolvedMode("async");
        response.setStatus("QUEUED");
        response.setCostEstimate(null);
        response.setPlannedShardCount(1);
        response.setReason("submitted");
        return response;
    }

    public QuantAsyncJobStatusResponse getJobStatus(Long jobId)
    {
        return repository.loadJobStatus(jobId);
    }

    public void cancelJob(Long jobId)
    {
        repository.cancelJob(jobId);
    }

    public List<String> retryFailedShards(Long jobId)
    {
        List<String> shardKeys = repository.requeueFailedShards(jobId);
        for (String shardKey : shardKeys)
        {
            redisTemplate.opsForList().rightPush(properties.getRedisQueueKey(), shardKey);
        }
        return shardKeys;
    }

    private QuantAsyncJobResponse submitAsyncJob(
            String jobType,
            QuantAsyncJobRequest request,
            List<Long> strategyIds,
            List<String> symbols,
            QuantJobCostEstimate estimate,
            String requestedMode,
            String resolvedMode)
    {
        String jobKey = "quant-job-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        List<ShardRecord> shards = buildShards(jobKey, strategyIds, symbols, request);
        JobRecord record = new JobRecord();
        record.setJobKey(jobKey);
        record.setJobType(jobType);
        record.setRequestedMode(requestedMode);
        record.setResolvedMode(resolvedMode);
        record.setStatus("QUEUED");
        record.setActor(blankToDefault(request.getActor(), "system"));
        record.setRequestJson(JSON.toJSONString(request));
        record.setNormalizedJson(JSON.toJSONString(buildNormalizedPayload(request, jobType)));
        record.setEstimateJson(JSON.toJSONString(estimate));
        record.setPlannedShardCount(shards.size());
        Long jobId = repository.insertJob(record);
        repository.insertSummary(jobId, symbols.size(), strategyIds.size(), JSON.toJSONString(Map.of("jobKey", jobKey)));
        repository.insertShards(jobId, shards);
        try
        {
            for (ShardRecord shard : shards)
            {
                redisTemplate.opsForList().rightPush(properties.getRedisQueueKey(), shard.getShardKey());
            }
        }
        catch (RuntimeException ex)
        {
            String errorMessage = "Redis queue unavailable: " + rootCauseMessage(ex);
            repository.markJobFailed(jobId, shards.size(), errorMessage);

            QuantAsyncJobResponse failedResponse = new QuantAsyncJobResponse();
            failedResponse.setJobId(jobId);
            failedResponse.setJobType(jobType);
            failedResponse.setRequestedMode(requestedMode);
            failedResponse.setResolvedMode(resolvedMode);
            failedResponse.setStatus("FAILED");
            failedResponse.setCostEstimate(estimate);
            failedResponse.setPlannedShardCount(shards.size());
            failedResponse.setReason("queue-unavailable");
            failedResponse.setOutput(errorMessage);
            return failedResponse;
        }

        QuantAsyncJobResponse response = new QuantAsyncJobResponse();
        response.setJobId(jobId);
        response.setJobType(jobType);
        response.setRequestedMode(requestedMode);
        response.setResolvedMode(resolvedMode);
        response.setStatus("QUEUED");
        response.setCostEstimate(estimate);
        response.setPlannedShardCount(shards.size());
        response.setReason("submitted");
        return response;
    }

    private List<ShardRecord> buildShards(
            String jobKey,
            List<Long> strategyIds,
            List<String> symbols,
            QuantAsyncJobRequest request)
    {
        int chunkSize = Math.max(1, properties.getShardSymbolChunkSize());
        List<ShardRecord> shards = new ArrayList<>();
        int shardIndex = 0;
        for (Long strategyId : strategyIds)
        {
            for (int start = 0; start < symbols.size(); start += chunkSize)
            {
                List<String> chunk = symbols.subList(start, Math.min(symbols.size(), start + chunkSize));
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("strategy_id", strategyId);
                payload.put("symbols", chunk);
                payload.put("start_date", request.getStrategyBacktestStartDate());
                payload.put("end_date", request.getEndDate());
                payload.put("actor", blankToDefault(request.getActor(), "system"));
                payload.put("params_override", request.getParamsOverride() == null ? Map.of() : request.getParamsOverride());

                ShardRecord shard = new ShardRecord();
                shard.setShardKey(jobKey + "-shard-" + shardIndex);
                shard.setStrategyId(strategyId);
                shard.setShardIndex(shardIndex);
                shard.setStatus("QUEUED");
                shard.setSymbolCount(chunk.size());
                shard.setPayloadJson(JSON.toJSONString(payload));
                shards.add(shard);
                shardIndex++;
            }
        }
        return shards;
    }

    private List<Long> resolveStrategyIds(QuantAsyncJobRequest request, boolean useActiveFallback)
    {
        if (request.getStrategyIds() != null && !request.getStrategyIds().isEmpty())
        {
            return request.getStrategyIds();
        }
        if (request.getStrategyId() != null)
        {
            return List.of(request.getStrategyId());
        }
        if (useActiveFallback)
        {
            return repository.resolveActiveStrategyIds();
        }
        return List.of(1L);
    }

    private QuantAsyncJobRequest normalizeRequest(QuantAsyncJobRequest request)
    {
        QuantAsyncJobRequest payload = request == null ? new QuantAsyncJobRequest() : request;
        if (payload.getStrategyBacktestStartDate() == null || payload.getStrategyBacktestStartDate().isBlank())
        {
            payload.setStrategyBacktestStartDate(properties.getStrategyBacktestStartDate());
        }
        return payload;
    }

    private QuantAsyncJobRequest normalizeExecutionPlanRequest(QuantJobRequest request)
    {
        QuantJobRequest source = request == null ? new QuantJobRequest() : request;
        QuantAsyncJobRequest target = new QuantAsyncJobRequest();
        target.setRequestedMode("async");
        target.setStrategyId(source.getStrategyId());
        target.setStrategyIds(source.getStrategyIds());
        target.setSymbols(source.getSymbols());
        target.setScopeType(source.getScopeType());
        target.setScopePoolCode(source.getScopePoolCode());
        target.setWhitelist(source.getWhitelist());
        target.setBlacklist(source.getBlacklist());
        target.setAdHocSymbols(source.getAdHocSymbols());
        target.setEndDate(source.getEndDate());
        target.setParamsOverride(source.getParamsOverride());
        target.setActor(source.getActor());
        target.setPortfolioTotalCapital(source.getPortfolioTotalCapital());
        target.setNotify(source.getNotify());
        target.setUsePortfolio(source.getUsePortfolio());
        target.setStrategyBacktestStartDate(
                source.getStrategyBacktestStartDate() == null || source.getStrategyBacktestStartDate().isBlank()
                        ? properties.getStrategyBacktestStartDate()
                        : source.getStrategyBacktestStartDate());
        return target;
    }

    private int resolveExecutionPlanStrategyCount(QuantJobRequest request)
    {
        if (request.getStrategyIds() != null && !request.getStrategyIds().isEmpty())
        {
            return request.getStrategyIds().size();
        }
        return request.getStrategyId() == null ? 1 : 1;
    }

    private Map<String, Object> buildNormalizedPayload(QuantAsyncJobRequest request, String jobType)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobType", jobType);
        payload.put("strategyId", request.getStrategyId());
        payload.put("strategyIds", request.getStrategyIds());
        payload.put("symbols", request.getSymbols());
        payload.put("scopeType", request.getScopeType());
        payload.put("scopePoolCode", request.getScopePoolCode());
        payload.put("whitelist", request.getWhitelist());
        payload.put("blacklist", request.getBlacklist());
        payload.put("adHocSymbols", request.getAdHocSymbols());
        payload.put("startDate", request.getStrategyBacktestStartDate());
        payload.put("endDate", request.getEndDate());
        payload.put("actor", blankToDefault(request.getActor(), "system"));
        payload.put("requestedMode", resolveRequestedMode(request.getRequestedMode()));
        payload.put("paramsOverride", request.getParamsOverride() == null ? Map.of() : request.getParamsOverride());
        return payload;
    }

    private QuantJobCostEstimate estimate(int strategyCount, int symbolCount, String startDate, String endDate)
    {
        LocalDate start = parseDate(startDate, LocalDate.parse(properties.getStrategyBacktestStartDate()));
        LocalDate end = parseDate(endDate, LocalDate.now());
        long tradingDays = Math.max(1L, ChronoUnit.DAYS.between(start, end) + 1L);
        long workUnits = Math.max(1L, (long) Math.max(1, strategyCount) * Math.max(1, symbolCount) * tradingDays);
        long estimatedSyncSeconds = Math.max(1L, workUnits / 1000L);
        boolean syncEligible = estimatedSyncSeconds <= Math.max(1L, properties.getSyncExecutionBudgetSeconds());
        return new QuantJobCostEstimate(strategyCount, symbolCount, tradingDays, estimatedSyncSeconds, syncEligible);
    }

    private String resolveRequestedMode(String requestedMode)
    {
        String candidate = blankToDefault(requestedMode, properties.getRequestedModeDefault());
        String normalized = candidate.toLowerCase(Locale.ROOT);
        if (!List.of("auto", "sync", "async").contains(normalized))
        {
            throw new ServiceException("requestedMode must be one of auto/sync/async");
        }
        return normalized;
    }

    private String resolveMode(String requestedMode, QuantJobCostEstimate estimate)
    {
        if ("async".equals(requestedMode))
        {
            return "async";
        }
        if ("sync".equals(requestedMode))
        {
            if (Boolean.TRUE.equals(estimate.getSyncEligible()))
            {
                return "sync";
            }
            throw new ServiceException("sync execution budget exceeded; resubmit with requestedMode=async or auto");
        }
        return Boolean.TRUE.equals(estimate.getSyncEligible()) ? "sync" : "async";
    }

    private QuantAsyncJobResponse runSyncStrategy(QuantAsyncJobRequest request, QuantJobCostEstimate estimate, String requestedMode)
    {
        List<String> symbols = resolveScopeSymbols(request);
        String output = pythonService.runStrategy(
                request.getStrategyId(),
                request.getStrategyBacktestStartDate(),
                request.getPortfolioTotalCapital(),
                request.getActor(),
                symbols);
        QuantAsyncJobResponse response = new QuantAsyncJobResponse();
        response.setJobId(null);
        response.setJobType("run-strategy");
        response.setRequestedMode(requestedMode);
        response.setResolvedMode("sync");
        response.setStatus("SUCCESS");
        response.setCostEstimate(estimate);
        response.setPlannedShardCount(0);
        response.setReason("sync-fast-path");
        response.setOutput(output);
        return response;
    }

    private QuantAsyncJobResponse runSyncPortfolio(QuantAsyncJobRequest request, QuantJobCostEstimate estimate, String requestedMode)
    {
        List<String> symbols = resolveScopeSymbols(request);
        String output = pythonService.runPortfolio(
                request.getStrategyBacktestStartDate(),
                request.getPortfolioTotalCapital(),
                request.getActor(),
                symbols);
        QuantAsyncJobResponse response = new QuantAsyncJobResponse();
        response.setJobId(null);
        response.setJobType("run-portfolio");
        response.setRequestedMode(requestedMode);
        response.setResolvedMode("sync");
        response.setStatus("SUCCESS");
        response.setCostEstimate(estimate);
        response.setPlannedShardCount(0);
        response.setReason("sync-fast-path");
        response.setOutput(output);
        return response;
    }

    private String blankToDefault(String value, String fallback)
    {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private LocalDate parseDate(String raw, LocalDate fallback)
    {
        if (raw == null || raw.isBlank())
        {
            return fallback;
        }
        return LocalDate.parse(raw.trim());
    }

    private String rootCauseMessage(RuntimeException ex)
    {
        Throwable current = ex;
        while (current.getCause() != null)
        {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank() ? current.toString() : current.getMessage();
    }

    private List<String> resolveScopeSymbols(QuantAsyncJobRequest request)
    {
        return symbolScopeService.resolveScopeSymbols(
                request.getScopeType(),
                request.getScopePoolCode(),
                request.getSymbols(),
                request.getWhitelist(),
                request.getBlacklist(),
                request.getAdHocSymbols());
    }
}
