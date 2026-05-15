package com.ruoyi.web.domain.quant;

public class QuantExecutionMatchConfirmRequest
{
    private Long signalId;
    private Long executionRecordId;
    private String actor;
    private String remark;

    public Long getSignalId()
    {
        return signalId;
    }

    public void setSignalId(Long signalId)
    {
        this.signalId = signalId;
    }

    public Long getExecutionRecordId()
    {
        return executionRecordId;
    }

    public void setExecutionRecordId(Long executionRecordId)
    {
        this.executionRecordId = executionRecordId;
    }

    public String getActor()
    {
        return actor;
    }

    public void setActor(String actor)
    {
        this.actor = actor;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
