package com.quantroad.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DashboardSummaryResponse {

    long strategyCount;
    long todaySignalCount;
    long positionCount;
    long riskWarningCount;
    BigDecimal averageAnnualReturn;
    List<SignalItem> latestSignals;
    List<RiskItem> lossWarnings;
    List<RunLogItem> latestLogs;

    @Value
    @Builder
    public static class SignalItem {
        String stockCode;
        String stockName;
        String signalType;
        String signalDate;
        BigDecimal suggestPrice;
    }

    @Value
    @Builder
    public static class RiskItem {
        String stockCode;
        String stockName;
        BigDecimal currentPrice;
        BigDecimal floatProfit;
    }

    @Value
    @Builder
    public static class RunLogItem {
        Long strategyId;
        String runTime;
        BigDecimal annualReturn;
        BigDecimal maxDrawdown;
        BigDecimal winRate;
        BigDecimal totalProfit;
        Integer isInvalid;
        String remark;
    }
}

