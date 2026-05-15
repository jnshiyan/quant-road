package com.ruoyi.web.controller.quant;

import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.service.quant.QuantOperationsCenterService;
import com.ruoyi.web.service.quant.QuantDispatchDetailService;
import com.ruoyi.web.service.quant.QuantRoadGovernanceService;
import com.ruoyi.web.service.quant.QuantRoadPythonService;
import com.ruoyi.web.service.quant.QuantRoadQueryService;
import com.ruoyi.web.service.quant.QuantRoadSymbolScopeService;
import com.ruoyi.web.service.quant.QuantTaskCenterService;

/**
 * Quant Road 数据查询接口
 */
@RestController
@RequestMapping("/quant")
public class QuantRoadDataController
{
    private final QuantRoadQueryService quantRoadQueryService;
    private final QuantRoadPythonService quantRoadPythonService;
    private final QuantRoadGovernanceService quantRoadGovernanceService;
    private final QuantRoadSymbolScopeService quantRoadSymbolScopeService;
    private final QuantTaskCenterService quantTaskCenterService;
    private final QuantOperationsCenterService quantOperationsCenterService;
    private final QuantDispatchDetailService quantDispatchDetailService;

    public QuantRoadDataController(
            QuantRoadQueryService quantRoadQueryService,
            QuantRoadPythonService quantRoadPythonService,
            QuantRoadGovernanceService quantRoadGovernanceService,
            QuantRoadSymbolScopeService quantRoadSymbolScopeService,
            QuantTaskCenterService quantTaskCenterService,
            QuantOperationsCenterService quantOperationsCenterService,
            QuantDispatchDetailService quantDispatchDetailService)
    {
        this.quantRoadQueryService = quantRoadQueryService;
        this.quantRoadPythonService = quantRoadPythonService;
        this.quantRoadGovernanceService = quantRoadGovernanceService;
        this.quantRoadSymbolScopeService = quantRoadSymbolScopeService;
        this.quantTaskCenterService = quantTaskCenterService;
        this.quantOperationsCenterService = quantOperationsCenterService;
        this.quantDispatchDetailService = quantDispatchDetailService;
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult dashboardSummary()
    {
        return AjaxResult.success(quantRoadQueryService.dashboardSummary());
    }

    @GetMapping("/data/taskCenterSummary")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult taskCenterSummary()
    {
        return AjaxResult.success(quantTaskCenterService.summary());
    }

    @GetMapping("/data/dispatchDefinitions")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult dispatchDefinitions()
    {
        return AjaxResult.success(quantRoadQueryService.dispatchDefinitions());
    }

    @GetMapping("/data/dispatchHistory")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult dispatchHistory(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String taskCode,
            @RequestParam(required = false) String triggerMode)
    {
        return AjaxResult.success(quantRoadQueryService.dispatchHistory(pageNum, pageSize, taskCode, triggerMode));
    }

    @GetMapping("/data/dispatchDetail/{jobId}")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult dispatchDetail(@PathVariable Long jobId)
    {
        return AjaxResult.success(quantDispatchDetailService.detailByJobId(jobId));
    }

    @GetMapping("/data/operationsCenterSummary")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult operationsCenterSummary()
    {
        return AjaxResult.success(quantOperationsCenterService.summary());
    }

    @GetMapping("/data/signals")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult signals(
            @RequestParam(required = false) String signalDate,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize)
    {
        LocalDate date = signalDate == null || signalDate.isBlank() ? LocalDate.now() : LocalDate.parse(signalDate);
        if (pageNum == null || pageSize == null)
        {
            return AjaxResult.success(quantRoadQueryService.signals(date));
        }
        return AjaxResult.success(quantRoadQueryService.signals(
                date,
                pageNum == null ? 1 : pageNum,
                pageSize == null ? 10 : pageSize));
    }

    @GetMapping("/data/positions")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult positions()
    {
        return AjaxResult.success(quantRoadQueryService.positions());
    }

    @GetMapping("/data/strategyLogs")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult strategyLogs(@RequestParam(defaultValue = "20") int limit)
    {
        return AjaxResult.success(quantRoadQueryService.strategyLogs(limit));
    }

    @GetMapping("/data/strategies")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult strategies()
    {
        return AjaxResult.success(quantRoadQueryService.strategies());
    }

    @GetMapping("/data/marketStatus")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult marketStatus()
    {
        return AjaxResult.success(quantRoadQueryService.marketStatus());
    }

    @GetMapping("/data/indexValuations")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult indexValuations(@RequestParam(defaultValue = "20") int limit)
    {
        return AjaxResult.success(quantRoadQueryService.indexValuations(limit));
    }

    @GetMapping("/data/strategySwitchAudits")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult strategySwitchAudits(@RequestParam(defaultValue = "20") int limit)
    {
        return AjaxResult.success(quantRoadQueryService.strategySwitchAudits(limit));
    }

    @GetMapping("/data/executionFeedbackSummary")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult executionFeedbackSummary()
    {
        return AjaxResult.success(quantRoadQueryService.executionFeedbackSummary());
    }

    @GetMapping("/data/executionFeedbackDetails")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult executionFeedbackDetails(@RequestParam(defaultValue = "20") int limit)
    {
        return AjaxResult.success(quantRoadQueryService.executionFeedbackDetails(limit));
    }

    @GetMapping("/data/executionReconciliationSummary")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult executionReconciliationSummary()
    {
        return AjaxResult.success(quantRoadQueryService.executionReconciliationSummary());
    }

    @GetMapping("/data/dashboardActionItems")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult dashboardActionItems(@RequestParam(defaultValue = "8") int limit)
    {
        return AjaxResult.success(quantRoadQueryService.dashboardActionItems(limit));
    }

    @GetMapping("/data/positionRiskSummary")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult positionRiskSummary()
    {
        return AjaxResult.success(quantRoadQueryService.positionRiskSummary());
    }

    @GetMapping("/data/dashboardDeepLinks")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult dashboardDeepLinks()
    {
        return AjaxResult.success(quantRoadQueryService.dashboardDeepLinks());
    }

    @GetMapping("/data/etfOverview")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult etfOverview()
    {
        return AjaxResult.success(quantRoadQueryService.etfOverview());
    }

    @GetMapping("/data/etfGovernanceSummary")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult etfGovernanceSummary()
    {
        return AjaxResult.success(quantRoadQueryService.etfGovernanceSummary());
    }

    @GetMapping("/data/reviewCandidates")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult reviewCandidates(@RequestParam(defaultValue = "6") int limit)
    {
        return AjaxResult.success(quantRoadQueryService.reviewCandidates(limit));
    }

    @GetMapping("/data/signalExplain/{signalId}")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult signalExplain(@PathVariable Long signalId)
    {
        return AjaxResult.success(quantRoadQueryService.signalExplain(signalId));
    }

    @GetMapping("/data/executionMatchCandidates")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult executionMatchCandidates(
            @RequestParam Long executionRecordId,
            @RequestParam(defaultValue = "5") int limit)
    {
        return AjaxResult.success(quantRoadQueryService.executionMatchCandidates(executionRecordId, limit));
    }

    @GetMapping("/data/positionSyncResult")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult positionSyncResult(
            @RequestParam(required = false) Long strategyId,
            @RequestParam(required = false) String stockCode)
    {
        return AjaxResult.success(quantRoadQueryService.positionSyncResult(strategyId, stockCode));
    }

    @GetMapping("/data/canaryLatest")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult canaryLatest()
    {
        return AjaxResult.success(quantRoadQueryService.canaryLatest());
    }

    @GetMapping("/data/strategyCapabilities")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult strategyCapabilities()
    {
        String output = quantRoadPythonService.strategyCapabilities();
        try
        {
            return AjaxResult.success(JSON.parse(output));
        }
        catch (Exception ex)
        {
            throw new ServiceException("Invalid strategy capabilities payload: " + output)
                    .setDetailMessage(ex.toString());
        }
    }

    @GetMapping("/data/shadowCompare")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult shadowCompare(
            @RequestParam(defaultValue = "1") Long baselineStrategyId,
            @RequestParam Long candidateStrategyId,
            @RequestParam(defaultValue = "6") Integer months)
    {
        String output = quantRoadPythonService.shadowCompareJson(baselineStrategyId, candidateStrategyId, months);
        try
        {
            return AjaxResult.success(JSON.parse(output));
        }
        catch (Exception ex)
        {
            throw new ServiceException("Invalid shadow compare payload: " + output)
                    .setDetailMessage(ex.toString());
        }
    }

    @GetMapping("/data/shadowCompareSummary")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult shadowCompareSummary(
            @RequestParam(defaultValue = "1") Long baselineStrategyId,
            @RequestParam Long candidateStrategyId,
            @RequestParam(defaultValue = "6") Integer months)
    {
        return AjaxResult.success(quantRoadGovernanceService.shadowCompareSummary(
                baselineStrategyId,
                candidateStrategyId,
                months));
    }

    @GetMapping("/data/shadowCompareCharts")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult shadowCompareCharts(
            @RequestParam(defaultValue = "1") Long baselineStrategyId,
            @RequestParam Long candidateStrategyId,
            @RequestParam(defaultValue = "6") Integer months)
    {
        return AjaxResult.success(quantRoadGovernanceService.shadowCompareCharts(
                baselineStrategyId,
                candidateStrategyId,
                months));
    }

    @GetMapping("/data/shadowCompareApplicability")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult shadowCompareApplicability(
            @RequestParam(defaultValue = "1") Long baselineStrategyId,
            @RequestParam Long candidateStrategyId,
            @RequestParam(defaultValue = "6") Integer months)
    {
        return AjaxResult.success(quantRoadGovernanceService.shadowCompareApplicability(
                baselineStrategyId,
                candidateStrategyId,
                months));
    }

    @GetMapping("/data/shadowReviewLinks")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult shadowReviewLinks(
            @RequestParam(defaultValue = "1") Long baselineStrategyId,
            @RequestParam Long candidateStrategyId,
            @RequestParam(defaultValue = "6") Integer months)
    {
        return AjaxResult.success(quantRoadGovernanceService.shadowReviewLinks(
                baselineStrategyId,
                candidateStrategyId,
                months));
    }

    @GetMapping("/data/executionRecords")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult executionRecords(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String stockCode)
    {
        return AjaxResult.success(quantRoadQueryService.executionRecords(limit, stockCode));
    }

    @GetMapping("/data/jobBatches")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult jobBatches(@RequestParam(defaultValue = "30") int limit)
    {
        return AjaxResult.success(quantRoadQueryService.jobBatches(limit));
    }

    @GetMapping("/data/jobSteps")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult jobSteps(@RequestParam Long batchId)
    {
        return AjaxResult.success(quantRoadQueryService.jobSteps(batchId));
    }

    @GetMapping("/data/asyncJobs")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult asyncJobs(@RequestParam(defaultValue = "30") int limit)
    {
        return AjaxResult.success(quantRoadQueryService.asyncJobs(limit));
    }

    @GetMapping("/data/asyncWorkerSummary")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult asyncWorkerSummary()
    {
        return AjaxResult.success(quantRoadQueryService.asyncWorkerSummary());
    }

    @GetMapping("/data/asyncJobShards")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult asyncJobShards(@RequestParam Long jobId)
    {
        return AjaxResult.success(quantRoadQueryService.asyncJobShards(jobId));
    }

    @GetMapping("/data/asyncJobResults")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult asyncJobResults(@RequestParam Long jobId, @RequestParam(defaultValue = "100") int limit)
    {
        return AjaxResult.success(quantRoadQueryService.asyncJobResults(jobId, limit));
    }

    @GetMapping("/data/jobReadiness")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult jobReadiness(@RequestParam(required = false) Long batchId)
    {
        return AjaxResult.success(quantRoadQueryService.jobReadiness(batchId));
    }

    @GetMapping("/data/jobErrorCategories")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult jobErrorCategories(@RequestParam(required = false) Long batchId)
    {
        return AjaxResult.success(quantRoadQueryService.jobErrorCategories(batchId));
    }

    @GetMapping("/data/jobSopHints")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult jobSopHints(@RequestParam(required = false) Long batchId)
    {
        return AjaxResult.success(quantRoadQueryService.jobSopHints(batchId));
    }

    @GetMapping("/data/symbolScopeOptions")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult symbolScopeOptions()
    {
        return AjaxResult.success(quantRoadSymbolScopeService.symbolScopeOptions());
    }

    @GetMapping("/data/symbolPools")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult symbolPools()
    {
        return AjaxResult.success(quantRoadSymbolScopeService.symbolPools());
    }

    @GetMapping("/data/symbolPoolDetail")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult symbolPoolDetail(@RequestParam String poolCode)
    {
        return AjaxResult.success(quantRoadSymbolScopeService.symbolPoolDetail(poolCode));
    }

    @GetMapping("/data/indexEtfMappings")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult indexEtfMappings()
    {
        return AjaxResult.success(quantRoadSymbolScopeService.indexEtfMappings());
    }

    @GetMapping("/data/symbolScopePreview")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult symbolScopePreview(
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopePoolCode,
            @RequestParam(required = false) java.util.List<String> symbols,
            @RequestParam(required = false) java.util.List<String> whitelist,
            @RequestParam(required = false) java.util.List<String> blacklist,
            @RequestParam(required = false) java.util.List<String> adHocSymbols)
    {
        return AjaxResult.success(quantRoadSymbolScopeService.symbolScopePreview(
                scopeType,
                scopePoolCode,
                symbols,
                whitelist,
                blacklist,
                adHocSymbols));
    }
}
