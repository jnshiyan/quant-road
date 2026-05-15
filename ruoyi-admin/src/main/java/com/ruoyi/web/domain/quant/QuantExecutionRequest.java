package com.ruoyi.web.domain.quant;

public class QuantExecutionRequest
{
    private String stockCode;
    private String side;
    private Integer quantity;
    private Double price;
    private String tradeDate;
    private Long strategyId;
    private Long signalId;
    private Double commission;
    private Double tax;
    private Double slippage;
    private String externalOrderId;

    public String getStockCode()
    {
        return stockCode;
    }

    public void setStockCode(String stockCode)
    {
        this.stockCode = stockCode;
    }

    public String getSide()
    {
        return side;
    }

    public void setSide(String side)
    {
        this.side = side;
    }

    public Integer getQuantity()
    {
        return quantity;
    }

    public void setQuantity(Integer quantity)
    {
        this.quantity = quantity;
    }

    public Double getPrice()
    {
        return price;
    }

    public void setPrice(Double price)
    {
        this.price = price;
    }

    public String getTradeDate()
    {
        return tradeDate;
    }

    public void setTradeDate(String tradeDate)
    {
        this.tradeDate = tradeDate;
    }

    public Long getStrategyId()
    {
        return strategyId;
    }

    public void setStrategyId(Long strategyId)
    {
        this.strategyId = strategyId;
    }

    public Long getSignalId()
    {
        return signalId;
    }

    public void setSignalId(Long signalId)
    {
        this.signalId = signalId;
    }

    public Double getCommission()
    {
        return commission;
    }

    public void setCommission(Double commission)
    {
        this.commission = commission;
    }

    public Double getTax()
    {
        return tax;
    }

    public void setTax(Double tax)
    {
        this.tax = tax;
    }

    public Double getSlippage()
    {
        return slippage;
    }

    public void setSlippage(Double slippage)
    {
        this.slippage = slippage;
    }

    public String getExternalOrderId()
    {
        return externalOrderId;
    }

    public void setExternalOrderId(String externalOrderId)
    {
        this.externalOrderId = externalOrderId;
    }
}
