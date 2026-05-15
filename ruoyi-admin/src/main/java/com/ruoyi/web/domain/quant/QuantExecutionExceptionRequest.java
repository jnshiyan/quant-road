package com.ruoyi.web.domain.quant;

public class QuantExecutionExceptionRequest
{
    private Long signalId;
    private String exceptionType;
    private String remark;
    private String actor;

    public Long getSignalId()
    {
        return signalId;
    }

    public void setSignalId(Long signalId)
    {
        this.signalId = signalId;
    }

    public String getExceptionType()
    {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType)
    {
        this.exceptionType = exceptionType;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getActor()
    {
        return actor;
    }

    public void setActor(String actor)
    {
        this.actor = actor;
    }
}
