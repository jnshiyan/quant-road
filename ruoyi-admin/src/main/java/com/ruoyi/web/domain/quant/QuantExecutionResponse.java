package com.ruoyi.web.domain.quant;

public class QuantExecutionResponse
{
    private Long executionId;
    private String status;
    private String resolvedExecutionMode;
    private String planSummary;
    private Object estimatedCost;
    private String triggerMode;
    private Object timeRange;

    public Long getExecutionId()
    {
        return executionId;
    }

    public void setExecutionId(Long executionId)
    {
        this.executionId = executionId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
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

    public Object getEstimatedCost()
    {
        return estimatedCost;
    }

    public void setEstimatedCost(Object estimatedCost)
    {
        this.estimatedCost = estimatedCost;
    }

    public String getTriggerMode()
    {
        return triggerMode;
    }

    public void setTriggerMode(String triggerMode)
    {
        this.triggerMode = triggerMode;
    }

    public Object getTimeRange()
    {
        return timeRange;
    }

    public void setTimeRange(Object timeRange)
    {
        this.timeRange = timeRange;
    }
}
