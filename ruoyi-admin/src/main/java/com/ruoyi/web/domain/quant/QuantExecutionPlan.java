package com.ruoyi.web.domain.quant;

import java.util.List;
import java.util.Map;

public class QuantExecutionPlan
{
    private List<String> resolvedSymbols;
    private List<QuantExecutionPlanStep> steps;
    private String resolvedExecutionMode;
    private String planSummary;
    private Map<String, Object> estimatedCost;

    public List<String> getResolvedSymbols()
    {
        return resolvedSymbols;
    }

    public void setResolvedSymbols(List<String> resolvedSymbols)
    {
        this.resolvedSymbols = resolvedSymbols;
    }

    public List<QuantExecutionPlanStep> getSteps()
    {
        return steps;
    }

    public void setSteps(List<QuantExecutionPlanStep> steps)
    {
        this.steps = steps;
    }

    public String getResolvedExecutionMode()
    {
        return resolvedExecutionMode;
    }

    public void setResolvedExecutionMode(String resolvedExecutionMode)
    {
        this.resolvedExecutionMode = resolvedExecutionMode;
    }

    public String getPlanSummary()
    {
        return planSummary;
    }

    public void setPlanSummary(String planSummary)
    {
        this.planSummary = planSummary;
    }

    public Map<String, Object> getEstimatedCost()
    {
        return estimatedCost;
    }

    public void setEstimatedCost(Map<String, Object> estimatedCost)
    {
        this.estimatedCost = estimatedCost;
    }
}
