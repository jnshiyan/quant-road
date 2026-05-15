package com.ruoyi.web.domain.quant;

import java.util.List;
import java.util.Map;

public class QuantAsyncJobRequest extends QuantJobRequest
{
    private String requestedMode;
    private List<String> symbols;
    private List<Long> strategyIds;
    private String endDate;
    private Map<String, Object> paramsOverride;

    public String getRequestedMode()
    {
        return requestedMode;
    }

    public void setRequestedMode(String requestedMode)
    {
        this.requestedMode = requestedMode;
    }

    public List<String> getSymbols()
    {
        return symbols;
    }

    public void setSymbols(List<String> symbols)
    {
        this.symbols = symbols;
    }

    public List<Long> getStrategyIds()
    {
        return strategyIds;
    }

    public void setStrategyIds(List<Long> strategyIds)
    {
        this.strategyIds = strategyIds;
    }

    public String getEndDate()
    {
        return endDate;
    }

    public void setEndDate(String endDate)
    {
        this.endDate = endDate;
    }

    public Map<String, Object> getParamsOverride()
    {
        return paramsOverride;
    }

    public void setParamsOverride(Map<String, Object> paramsOverride)
    {
        this.paramsOverride = paramsOverride;
    }
}
