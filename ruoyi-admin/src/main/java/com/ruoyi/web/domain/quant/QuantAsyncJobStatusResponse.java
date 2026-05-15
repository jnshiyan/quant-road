package com.ruoyi.web.domain.quant;

public class QuantAsyncJobStatusResponse
{
    private Long jobId;
    private String jobType;
    private String requestedMode;
    private String resolvedMode;
    private String status;
    private Integer plannedShardCount;
    private Integer completedShardCount;
    private Integer failedShardCount;
    private Boolean cancelRequested;
    private String errorMessage;

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

    public Integer getPlannedShardCount()
    {
        return plannedShardCount;
    }

    public void setPlannedShardCount(Integer plannedShardCount)
    {
        this.plannedShardCount = plannedShardCount;
    }

    public Integer getCompletedShardCount()
    {
        return completedShardCount;
    }

    public void setCompletedShardCount(Integer completedShardCount)
    {
        this.completedShardCount = completedShardCount;
    }

    public Integer getFailedShardCount()
    {
        return failedShardCount;
    }

    public void setFailedShardCount(Integer failedShardCount)
    {
        this.failedShardCount = failedShardCount;
    }

    public Boolean getCancelRequested()
    {
        return cancelRequested;
    }

    public void setCancelRequested(Boolean cancelRequested)
    {
        this.cancelRequested = cancelRequested;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }
}
