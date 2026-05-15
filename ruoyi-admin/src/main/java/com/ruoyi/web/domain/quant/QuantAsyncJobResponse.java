package com.ruoyi.web.domain.quant;

public class QuantAsyncJobResponse
{
    private Long jobId;
    private String jobType;
    private String requestedMode;
    private String resolvedMode;
    private String status;
    private QuantJobCostEstimate costEstimate;
    private Integer plannedShardCount;
    private String reason;
    private Object output;

    public Long getJobId()
    {
        return jobId;
    }

    public void setJobId(Long jobId)
    {
        this.jobId = jobId;
    }

    public String getJobType()
    {
        return jobType;
    }

    public void setJobType(String jobType)
    {
        this.jobType = jobType;
    }

    public String getRequestedMode()
    {
        return requestedMode;
    }

    public void setRequestedMode(String requestedMode)
    {
        this.requestedMode = requestedMode;
    }

    public String getResolvedMode()
    {
        return resolvedMode;
    }

    public void setResolvedMode(String resolvedMode)
    {
        this.resolvedMode = resolvedMode;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public QuantJobCostEstimate getCostEstimate()
    {
        return costEstimate;
    }

    public void setCostEstimate(QuantJobCostEstimate costEstimate)
    {
        this.costEstimate = costEstimate;
    }

    public Integer getPlannedShardCount()
    {
        return plannedShardCount;
    }

    public void setPlannedShardCount(Integer plannedShardCount)
    {
        this.plannedShardCount = plannedShardCount;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public Object getOutput()
    {
        return output;
    }

    public void setOutput(Object output)
    {
        this.output = output;
    }
}
