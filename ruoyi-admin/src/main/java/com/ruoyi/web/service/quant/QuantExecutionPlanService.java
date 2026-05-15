package com.ruoyi.web.service.quant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.ruoyi.web.domain.quant.QuantExecutionPlan;
import com.ruoyi.web.domain.quant.QuantExecutionPlanStep;
import com.ruoyi.web.domain.quant.QuantJobRequest;

@Service
public class QuantExecutionPlanService
{
    private final QuantRoadSymbolScopeService symbolScopeService;

    public QuantExecutionPlanService(QuantRoadSymbolScopeService symbolScopeService)
    {
        this.symbolScopeService = symbolScopeService;
    }

    public QuantExecutionPlan buildPlan(QuantJobRequest request)
    {
        QuantJobRequest resolvedRequest = request == null ? new QuantJobRequest() : request;
        String scopeType = symbolScopeService.resolveScopeType(resolvedRequest.getScopeType());
        List<String> symbols = symbolScopeService.resolveScopeSymbols(
                scopeType,
                resolvedRequest.getScopePoolCode(),
                resolvedRequest.getSymbols(),
                resolvedRequest.getWhitelist(),
                resolvedRequest.getBlacklist(),
                resolvedRequest.getAdHocSymbols());
        List<QuantExecutionPlanStep> steps = new ArrayList<>();
        if (requiresSyncBasic(scopeType))
        {
            steps.add(step("sync-basic", "global"));
        }
        steps.add(step("sync-daily", resolveSyncDailyScope(scopeType)));
        if (requiresMarketEvaluation(scopeType))
        {
            steps.add(step("evaluate-market", "global"));
        }
        steps.add(step("run-strategy", "scoped"));
        steps.add(step("evaluate-risk", "scoped"));
        return finalizePlan(symbols, steps, scopeType);
    }

    private boolean requiresSyncBasic(String scopeType)
    {
        return "all_stocks".equals(scopeType);
    }

    private boolean requiresMarketEvaluation(String scopeType)
    {
        return "all_stocks".equals(scopeType);
    }

    private String resolveSyncDailyScope(String scopeType)
    {
        if ("all_stocks".equals(scopeType))
        {
            return "global";
        }
        return "scoped";
    }

    private QuantExecutionPlan finalizePlan(List<String> symbols, List<QuantExecutionPlanStep> steps, String scopeType)
    {
        QuantExecutionPlan plan = new QuantExecutionPlan();
        plan.setResolvedSymbols(List.copyOf(symbols));
        plan.setSteps(List.copyOf(steps));
        plan.setResolvedExecutionMode(resolveExecutionMode(scopeType));
        plan.setPlanSummary(buildPlanSummary(scopeType, plan.getResolvedExecutionMode(), symbols, steps));
        plan.setEstimatedCost(estimatedCost(symbols, steps));
        return plan;
    }

    private String resolveExecutionMode(String scopeType)
    {
        if ("all_stocks".equals(scopeType))
        {
            return "async";
        }
        return "sync";
    }

    private String buildPlanSummary(String scopeType, String executionMode, List<String> symbols, List<QuantExecutionPlanStep> steps)
    {
        String orderedStepNames = steps.stream()
                .map(QuantExecutionPlanStep::getStepName)
                .reduce((left, right) -> left + " -> " + right)
                .orElse("none");
        return String.format(
                "mode=%s, scope=%s, symbols=%d, steps=%s",
                executionMode,
                scopeType,
                symbols.size(),
                orderedStepNames);
    }

    private Map<String, Object> estimatedCost(List<String> symbols, List<QuantExecutionPlanStep> steps)
    {
        int globalStepCount = 0;
        int scopedStepCount = 0;
        for (QuantExecutionPlanStep step : steps)
        {
            if ("global".equals(step.getStepScope()))
            {
                globalStepCount++;
            }
            else
            {
                scopedStepCount++;
            }
        }
        Map<String, Object> estimatedCost = new LinkedHashMap<>();
        estimatedCost.put("symbolCount", symbols.size());
        estimatedCost.put("stepCount", steps.size());
        estimatedCost.put("globalStepCount", globalStepCount);
        estimatedCost.put("scopedStepCount", scopedStepCount);
        return estimatedCost;
    }

    private QuantExecutionPlanStep step(String stepName, String stepScope)
    {
        QuantExecutionPlanStep step = new QuantExecutionPlanStep();
        step.setStepName(stepName);
        step.setStepScope(stepScope);
        return step;
    }
}
