package com.ruoyi.web.domain.quant;

import java.util.List;
import java.util.Map;

/**
 * Quant Road 手动任务请求参数
 */
public class QuantJobRequest
{
    private Long strategyId;
    private String startDate;
    private String strategyBacktestStartDate;
    private Boolean notify;
    private Boolean usePortfolio;
    private Double portfolioTotalCapital;
    private String actor;
    private String requestedMode;
    private List<String> symbols;
    private String scopeType;
    private String scopePoolCode;
    private List<String> whitelist;
    private List<String> blacklist;
    private List<String> adHocSymbols;
    private List<Long> strategyIds;
    private String endDate;
    private Map<String, Object> paramsOverride;

    public Long getStrategyId()
    {
        return strategyId;
    }

    public void setStrategyId(Long strategyId)
    {
        this.strategyId = strategyId;
    }

    public String getStartDate()
    {
        return startDate;
    }

    public void setStartDate(String startDate)
    {
        this.startDate = startDate;
    }

    public String getStrategyBacktestStartDate()
    {
        return strategyBacktestStartDate;
    }

    public void setStrategyBacktestStartDate(String strategyBacktestStartDate)
    {
        this.strategyBacktestStartDate = strategyBacktestStartDate;
    }

    public Boolean getNotify()
    {
        return notify;
    }

    public void setNotify(Boolean notify)
    {
        this.notify = notify;
    }

    public Boolean getUsePortfolio()
    {
        return usePortfolio;
    }

    public void setUsePortfolio(Boolean usePortfolio)
    {
        this.usePortfolio = usePortfolio;
    }

    public Double getPortfolioTotalCapital()
    {
        return portfolioTotalCapital;
    }

    public void setPortfolioTotalCapital(Double portfolioTotalCapital)
    {
        this.portfolioTotalCapital = portfolioTotalCapital;
    }

    public String getActor()
    {
        return actor;
    }

    public void setActor(String actor)
    {
        this.actor = actor;
    }

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

    public String getScopeType()
    {
        return scopeType;
    }

    public void setScopeType(String scopeType)
    {
        this.scopeType = scopeType;
    }

    public String getScopePoolCode()
    {
        return scopePoolCode;
    }

    public void setScopePoolCode(String scopePoolCode)
    {
        this.scopePoolCode = scopePoolCode;
    }

    public List<String> getWhitelist()
    {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist)
    {
        this.whitelist = whitelist;
    }

    public List<String> getBlacklist()
    {
        return blacklist;
    }

    public void setBlacklist(List<String> blacklist)
    {
        this.blacklist = blacklist;
    }

    public List<String> getAdHocSymbols()
    {
        return adHocSymbols;
    }

    public void setAdHocSymbols(List<String> adHocSymbols)
    {
        this.adHocSymbols = adHocSymbols;
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
