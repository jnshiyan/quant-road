package com.ruoyi.web.controller.quant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.domain.quant.QuantReviewConclusionRequest;
import com.ruoyi.web.service.quant.QuantRoadReviewService;

@RestController
@RequestMapping("/quant/review")
public class QuantRoadReviewController
{
    private final QuantRoadReviewService quantRoadReviewService;

    public QuantRoadReviewController(QuantRoadReviewService quantRoadReviewService)
    {
        this.quantRoadReviewService = quantRoadReviewService;
    }

    @GetMapping("/candidates")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult candidates(
            @RequestParam(required = false) String reviewLevel,
            @RequestParam(defaultValue = "12") Integer limit)
    {
        return AjaxResult.success(quantRoadReviewService.reviewCandidates(reviewLevel, limit));
    }

    @GetMapping("/cases")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult cases(
            @RequestParam(required = false) String reviewLevel,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String assetType,
            @RequestParam(defaultValue = "12") Integer limit)
    {
        return AjaxResult.success(quantRoadReviewService.reviewCases(reviewLevel, caseType, assetType, limit));
    }

    @GetMapping("/caseDetail")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult caseDetail(@RequestParam Long caseId)
    {
        return AjaxResult.success(quantRoadReviewService.reviewCaseDetail(caseId));
    }

    @GetMapping("/summary")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult summary(
            @RequestParam(required = false) String reviewLevel,
            @RequestParam(required = false) Long strategyId,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) Long signalId,
            @RequestParam(required = false) String dateRangeStart,
            @RequestParam(required = false) String dateRangeEnd,
            @RequestParam(required = false) Long baselineStrategyId,
            @RequestParam(required = false) Long candidateStrategyId,
            @RequestParam(defaultValue = "6") Integer months,
            @RequestParam(required = false) Long caseId)
    {
        return AjaxResult.success(quantRoadReviewService.reviewSummary(
                reviewLevel,
                strategyId,
                stockCode,
                signalId,
                dateRangeStart,
                dateRangeEnd,
                baselineStrategyId,
                candidateStrategyId,
                months,
                caseId));
    }

    @GetMapping("/kline")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult kline(
            @RequestParam(required = false) String reviewLevel,
            @RequestParam(required = false) Long strategyId,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) Long signalId,
            @RequestParam(required = false) String dateRangeStart,
            @RequestParam(required = false) String dateRangeEnd)
    {
        return AjaxResult.success(quantRoadReviewService.reviewKline(
                reviewLevel,
                strategyId,
                stockCode,
                signalId,
                dateRangeStart,
                dateRangeEnd));
    }

    @GetMapping("/holdingRange")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult holdingRange(
            @RequestParam(required = false) String reviewLevel,
            @RequestParam(required = false) Long strategyId,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) Long signalId,
            @RequestParam(required = false) String dateRangeStart,
            @RequestParam(required = false) String dateRangeEnd)
    {
        return AjaxResult.success(quantRoadReviewService.holdingRange(
                reviewLevel,
                strategyId,
                stockCode,
                signalId,
                dateRangeStart,
                dateRangeEnd));
    }

    @GetMapping("/navDrawdown")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult navDrawdown(
            @RequestParam(required = false) Long strategyId,
            @RequestParam(required = false) String dateRangeStart,
            @RequestParam(required = false) String dateRangeEnd)
    {
        return AjaxResult.success(quantRoadReviewService.navDrawdown(strategyId, dateRangeStart, dateRangeEnd));
    }

    @GetMapping("/marketOverlay")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult marketOverlay(
            @RequestParam(required = false) Long strategyId,
            @RequestParam(required = false) String dateRangeStart,
            @RequestParam(required = false) String dateRangeEnd)
    {
        return AjaxResult.success(quantRoadReviewService.marketOverlay(strategyId, dateRangeStart, dateRangeEnd));
    }

    @GetMapping("/governanceEvidence")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult governanceEvidence(
            @RequestParam(required = false) Long baselineStrategyId,
            @RequestParam(required = false) Long candidateStrategyId,
            @RequestParam(defaultValue = "6") Integer months)
    {
        return AjaxResult.success(quantRoadReviewService.governanceEvidence(
                baselineStrategyId,
                candidateStrategyId,
                months));
    }

    @GetMapping("/timeline")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult timeline(
            @RequestParam(required = false) String reviewLevel,
            @RequestParam(required = false) Long strategyId,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) Long signalId,
            @RequestParam(required = false) String dateRangeStart,
            @RequestParam(required = false) String dateRangeEnd,
            @RequestParam(required = false) Long baselineStrategyId,
            @RequestParam(required = false) Long candidateStrategyId,
            @RequestParam(defaultValue = "6") Integer months,
            @RequestParam(defaultValue = "120") Integer limit)
    {
        return AjaxResult.success(quantRoadReviewService.timeline(
                reviewLevel,
                strategyId,
                stockCode,
                signalId,
                dateRangeStart,
                dateRangeEnd,
                baselineStrategyId,
                candidateStrategyId,
                months,
                limit));
    }

    @GetMapping("/ruleExplain")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult ruleExplain(
            @RequestParam(required = false) String reviewLevel,
            @RequestParam(required = false) Long strategyId,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) Long baselineStrategyId,
            @RequestParam(required = false) Long candidateStrategyId)
    {
        return AjaxResult.success(quantRoadReviewService.ruleExplain(
                reviewLevel,
                strategyId,
                stockCode,
                baselineStrategyId,
                candidateStrategyId));
    }

    @PostMapping("/conclusion")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult conclusion(@RequestBody QuantReviewConclusionRequest request)
    {
        return AjaxResult.success(quantRoadReviewService.submitConclusion(request));
    }
}
