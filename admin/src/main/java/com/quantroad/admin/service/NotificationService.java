package com.quantroad.admin.service;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import com.quantroad.admin.config.NotificationProperties;
import com.quantroad.admin.dto.DashboardSummaryResponse;
import com.quantroad.admin.entity.StrategyRunLogEntity;
import com.quantroad.admin.entity.TradeSignalEntity;
import com.quantroad.admin.repository.PositionRepository;
import com.quantroad.admin.repository.StrategyRunLogRepository;
import com.quantroad.admin.repository.TradeSignalRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationProperties properties;
    private final TradeSignalRepository tradeSignalRepository;
    private final PositionRepository positionRepository;
    private final StrategyRunLogRepository strategyRunLogRepository;
    private final DashboardService dashboardService;

    public boolean pushDailySummary() {
        if (!properties.isEnabled() || properties.getWebhook() == null || properties.getWebhook().isBlank()) {
            log.info("Notification disabled or webhook missing, skip push.");
            return false;
        }

        DashboardSummaryResponse summary = dashboardService.getSummary();
        List<TradeSignalEntity> todaySignals = tradeSignalRepository.findBySignalDateOrderBySignalTypeAscStockCodeAsc(LocalDate.now());
        List<StrategyRunLogEntity> invalidLogs = strategyRunLogRepository.findTop5ByIsInvalidOrderByRunTimeDesc(1);

        StringBuilder content = new StringBuilder();
        content.append("Quant Road 盘后摘要").append("\n")
            .append("今日信号数: ").append(summary.getTodaySignalCount()).append("\n")
            .append("持仓数: ").append(summary.getPositionCount()).append("\n")
            .append("止损预警数: ").append(summary.getRiskWarningCount()).append("\n")
            .append("平均年化: ").append(summary.getAverageAnnualReturn()).append("\n");

        if (!todaySignals.isEmpty()) {
            content.append("今日信号:").append("\n");
            todaySignals.stream().limit(10).forEach(signal ->
                content.append(signal.getSignalType()).append(" ")
                    .append(signal.getStockCode()).append(" ")
                    .append(signal.getStockName()).append(" @ ")
                    .append(signal.getSuggestPrice()).append("\n"));
        }

        if (!positionRepository.findByLossWarningOrderByStockCodeAsc(1).isEmpty()) {
            content.append("存在止损预警持仓，请尽快检查。").append("\n");
        }

        if (!invalidLogs.isEmpty()) {
            content.append("最近策略失效:").append("\n");
            invalidLogs.stream().limit(3).forEach(logItem ->
                content.append("strategy=").append(logItem.getStrategyId()).append(" ")
                    .append(logItem.getRemark()).append("\n"));
        }

        String body = buildPayload(content.toString());
        String response = HttpRequest.post(properties.getWebhook())
            .body(body, ContentType.JSON.getValue())
            .timeout(10000)
            .execute()
            .body();
        log.info("Notification response: {}", response);
        return true;
    }

    private String buildPayload(String content) {
        String normalizedType = properties.getType() == null ? "dingding" : properties.getType().toLowerCase();
        if (!"dingding".equals(normalizedType) && !"wechat".equals(normalizedType)) {
            throw new IllegalStateException("Unsupported notification type: " + properties.getType());
        }
        String escapedContent = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");
        return "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + escapedContent + "\"}}";
    }
}
