package com.ruoyi.web.service.quant;

import com.ruoyi.web.core.config.QuantRoadPythonProperties;
import com.ruoyi.web.domain.quant.QuantAsyncJobRequest;
import com.ruoyi.web.domain.quant.QuantAsyncJobResponse;
import com.ruoyi.web.domain.quant.QuantExecutionResponse;
import com.ruoyi.web.domain.quant.QuantJobRequest;
import org.springframework.stereotype.Service;

@Service
public class QuantAsyncExecutionFacade
{
    private final QuantJobPlannerService planner;
    private final QuantRoadPythonService pythonService;
    private final QuantRoadPythonProperties properties;
    private final QuantExecutionFacade executionFacade;

    public QuantAsyncExecutionFacade(
            QuantJobPlannerService planner,
            QuantRoadPythonService pythonService,
            QuantRoadPythonProperties properties,
            QuantExecutionFacade executionFacade)
    {
        this.planner = planner;
        this.pythonService = pythonService;
        this.properties = properties;
        this.executionFacade = executionFacade;
    }

    public QuantAsyncJobRequest normalizeRunPortfolio(QuantJobRequest request)
    {
        QuantJobRequest source = request == null ? new QuantJobRequest() : request;
        QuantAsyncJobRequest target = new QuantAsyncJobRequest();
        target.setRequestedMode(source.getRequestedMode() == null || source.getRequestedMode().isBlank()
                ? properties.getRequestedModeDefault()
                : source.getRequestedMode());
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
        target.setUsePortfolio(Boolean.TRUE);
        target.setStrategyBacktestStartDate(
                source.getStrategyBacktestStartDate() == null || source.getStrategyBacktestStartDate().isBlank()
                        ? properties.getStrategyBacktestStartDate()
                        : source.getStrategyBacktestStartDate());
        return target;
    }

    public QuantAsyncJobRequest normalizeRunStrategy(QuantJobRequest request)
    {
        QuantJobRequest source = request == null ? new QuantJobRequest() : request;
        QuantAsyncJobRequest target = normalizeRunPortfolio(source);
        target.setUsePortfolio(Boolean.FALSE);
        return target;
    }

    public QuantAsyncJobResponse submitLegacyRunPortfolio(QuantJobRequest request)
    {
        QuantExecutionResponse response = executionFacade.execute(request == null ? new QuantJobRequest() : request);
        return adaptExecutionResponse(response, request == null ? new QuantJobRequest() : request, "run-portfolio");
    }

    public QuantAsyncJobResponse submitLegacyRunStrategy(QuantJobRequest request)
    {
        QuantExecutionResponse response = executionFacade.execute(request == null ? new QuantJobRequest() : request);
        return adaptExecutionResponse(response, request == null ? new QuantJobRequest() : request, "run-strategy");
    }

    public QuantAsyncJobResponse submitLegacyRunStrategy(QuantAsyncJobRequest request)
    {
        QuantAsyncJobRequest source = request == null ? new QuantAsyncJobRequest() : request;
        QuantJobRequest target = new QuantJobRequest();
        target.setRequestedMode(source.getRequestedMode());
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
        target.setUsePortfolio(Boolean.FALSE);
        target.setStrategyBacktestStartDate(source.getStrategyBacktestStartDate());
        return submitLegacyRunStrategy(target);
    }

    public QuantAsyncJobResponse submitScheduledRunPortfolio(String strategyBacktestStartDate, Double totalCapital, String actor)
    {
        QuantJobRequest request = new QuantJobRequest();
        request.setRequestedMode("async");
        request.setStrategyBacktestStartDate(
                strategyBacktestStartDate == null || strategyBacktestStartDate.isBlank()
                        ? properties.getStrategyBacktestStartDate()
                        : strategyBacktestStartDate);
        request.setPortfolioTotalCapital(totalCapital);
        request.setActor(actor);
        request.setUsePortfolio(Boolean.TRUE);
        return submitLegacyRunPortfolio(request);
    }

    public QuantAsyncJobResponse submitScheduledFullDaily(String strategyBacktestStartDate, Double totalCapital, String actor)
    {
        return submitScheduledRunPortfolio(strategyBacktestStartDate, totalCapital, actor);
    }

    public String executeSyncStrategy(QuantAsyncJobRequest request)
    {
        return pythonService.runStrategy(
                request.getStrategyId(),
                request.getStrategyBacktestStartDate(),
                request.getPortfolioTotalCapital(),
                request.getActor(),
                request.getSymbols());
    }

    public String executeSyncPortfolio(QuantAsyncJobRequest request)
    {
        return pythonService.runPortfolio(
                request.getStrategyBacktestStartDate(),
                request.getPortfolioTotalCapital(),
                request.getActor(),
                request.getSymbols());
    }

    private QuantAsyncJobResponse adaptExecutionResponse(QuantExecutionResponse response, QuantJobRequest source, String jobType)
    {
        QuantExecutionResponse payload = response == null ? new QuantExecutionResponse() : response;
        QuantAsyncJobResponse target = new QuantAsyncJobResponse();
        target.setJobId(payload.getExecutionId());
        target.setJobType(jobType);
        target.setRequestedMode(source.getRequestedMode() == null || source.getRequestedMode().isBlank()
                ? properties.getRequestedModeDefault()
                : source.getRequestedMode());
        target.setResolvedMode(payload.getResolvedExecutionMode());
        target.setStatus(payload.getStatus());
        target.setReason(payload.getExecutionId() == null ? "executed" : "submitted");
        target.setOutput(payload.getPlanSummary());
        return target;
    }
}
