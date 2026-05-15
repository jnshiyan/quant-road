package com.ruoyi.web.domain.quant;

public class QuantJobCostEstimate
{
    private Integer strategyCount;
    private Integer symbolCount;
    private Long tradingDays;
    private Long estimatedSyncSeconds;
    private Boolean syncEligible;

    public QuantJobCostEstimate()
    {
    }

    public QuantJobCostEstimate(
            Integer strategyCount,
            Integer symbolCount,
            Long tradingDays,
            Long estimatedSyncSeconds,
            Boolean syncEligible)
    {
        this.strategyCount = strategyCount;
        this.symbolCount = symbolCount;
        this.tradingDays = tradingDays;
        this.estimatedSyncSeconds = estimatedSyncSeconds;
        this.syncEligible = syncEligible;
    }

    public Integer getStrategyCount()
    {
        return strategyCount;
    }

    public void setStrategyCount(Integer strategyCount)
    {
        this.strategyCount = strategyCount;
    }

    public Integer getSymbolCount()
    {
        return symbolCount;
    }

    public void setSymbolCount(Integer symbolCount)
    {
        this.symbolCount = symbolCount;
    }

    public Long getTradingDays()
    {
        return tradingDays;
    }

    public void setTradingDays(Long tradingDays)
    {
        this.tradingDays = tradingDays;
    }

    public Long getEstimatedSyncSeconds()
    {
        return estimatedSyncSeconds;
    }

    public void setEstimatedSyncSeconds(Long estimatedSyncSeconds)
    {
        this.estimatedSyncSeconds = estimatedSyncSeconds;
    }

    public Boolean getSyncEligible()
    {
        return syncEligible;
    }

    public void setSyncEligible(Boolean syncEligible)
    {
        this.syncEligible = syncEligible;
    }
}
