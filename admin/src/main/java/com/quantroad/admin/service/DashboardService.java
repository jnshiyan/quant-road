package com.quantroad.admin.service;

import com.quantroad.admin.dto.DashboardSummaryResponse;
import com.quantroad.admin.entity.PositionEntity;
import com.quantroad.admin.entity.StrategyRunLogEntity;
import com.quantroad.admin.entity.TradeSignalEntity;
import com.quantroad.admin.repository.PositionRepository;
import com.quantroad.admin.repository.StrategyConfigRepository;
import com.quantroad.admin.repository.StrategyRunLogRepository;
import com.quantroad.admin.repository.TradeSignalRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final StrategyConfigRepository strategyConfigRepository;
    private final TradeSignalRepository tradeSignalRepository;
    private final PositionRepository positionRepository;
    private final StrategyRunLogRepository strategyRunLogRepository;

    public DashboardSummaryResponse getSummary() {
        LocalDate today = LocalDate.now();
        List<TradeSignalEntity> latestSignals = tradeSignalRepository.findTop50ByOrderBySignalDateDescCreateTimeDesc();
        List<PositionEntity> lossWarnings = positionRepository.findByLossWarningOrderByStockCodeAsc(1);
        List<StrategyRunLogEntity> latestLogs = strategyRunLogRepository.findTop20ByOrderByRunTimeDesc();

        BigDecimal averageAnnualReturn = latestLogs.stream()
            .map(StrategyRunLogEntity::getAnnualReturn)
            .filter(item -> item != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long annualReturnCount = latestLogs.stream().map(StrategyRunLogEntity::getAnnualReturn).filter(item -> item != null).count();
        if (annualReturnCount > 0) {
            averageAnnualReturn = averageAnnualReturn.divide(BigDecimal.valueOf(annualReturnCount), 2, RoundingMode.HALF_UP);
        }

        return DashboardSummaryResponse.builder()
            .strategyCount(strategyConfigRepository.count())
            .todaySignalCount(tradeSignalRepository.countBySignalDate(today))
            .positionCount(positionRepository.count())
            .riskWarningCount(lossWarnings.size())
            .averageAnnualReturn(averageAnnualReturn)
            .latestSignals(latestSignals.stream().limit(10).map(this::toSignalItem).collect(Collectors.toList()))
            .lossWarnings(lossWarnings.stream().limit(10).map(this::toRiskItem).collect(Collectors.toList()))
            .latestLogs(latestLogs.stream().limit(10).map(this::toRunLogItem).collect(Collectors.toList()))
            .build();
    }

    private DashboardSummaryResponse.SignalItem toSignalItem(TradeSignalEntity entity) {
        return DashboardSummaryResponse.SignalItem.builder()
            .stockCode(entity.getStockCode())
            .stockName(entity.getStockName())
            .signalType(entity.getSignalType())
            .signalDate(entity.getSignalDate() == null ? null : entity.getSignalDate().toString())
            .suggestPrice(entity.getSuggestPrice())
            .build();
    }

    private DashboardSummaryResponse.RiskItem toRiskItem(PositionEntity entity) {
        return DashboardSummaryResponse.RiskItem.builder()
            .stockCode(entity.getStockCode())
            .stockName(entity.getStockName())
            .currentPrice(entity.getCurrentPrice())
            .floatProfit(entity.getFloatProfit())
            .build();
    }

    private DashboardSummaryResponse.RunLogItem toRunLogItem(StrategyRunLogEntity entity) {
        return DashboardSummaryResponse.RunLogItem.builder()
            .strategyId(entity.getStrategyId())
            .runTime(entity.getRunTime() == null ? null : entity.getRunTime().toString())
            .annualReturn(entity.getAnnualReturn())
            .maxDrawdown(entity.getMaxDrawdown())
            .winRate(entity.getWinRate())
            .totalProfit(entity.getTotalProfit())
            .isInvalid(entity.getIsInvalid())
            .remark(entity.getRemark())
            .build();
    }
}

