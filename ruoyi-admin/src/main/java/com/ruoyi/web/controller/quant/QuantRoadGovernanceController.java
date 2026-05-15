package com.ruoyi.web.controller.quant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.domain.quant.QuantGovernanceDecisionRequest;
import com.ruoyi.web.service.quant.QuantRoadGovernanceService;

@RestController
@RequestMapping("/quant/governance")
public class QuantRoadGovernanceController
{
    private final QuantRoadGovernanceService quantRoadGovernanceService;

    public QuantRoadGovernanceController(QuantRoadGovernanceService quantRoadGovernanceService)
    {
        this.quantRoadGovernanceService = quantRoadGovernanceService;
    }

    @PostMapping("/decision")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult decision(@RequestBody QuantGovernanceDecisionRequest request)
    {
        return AjaxResult.success(quantRoadGovernanceService.submitGovernanceDecision(request));
    }

    @GetMapping("/history")
    @PreAuthorize("@ss.hasPermi('quant:data:query')")
    public AjaxResult history(
            @RequestParam Long baselineStrategyId,
            @RequestParam Long candidateStrategyId,
            @RequestParam(defaultValue = "10") Integer limit)
    {
        return AjaxResult.success(quantRoadGovernanceService.governanceHistory(
                baselineStrategyId,
                candidateStrategyId,
                limit));
    }
}
