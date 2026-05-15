package com.ruoyi.web.service.quant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.domain.quant.QuantReviewConclusionRequest;

@Service
public class QuantRoadReviewService
{
    private static final int DEFAULT_TIMELINE_LIMIT = 120;
    private static final int MAX_TIMELINE_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;
    private final QuantRoadGovernanceService quantRoadGovernanceService;
    private final QuantRoadQueryService quantRoadQueryService;

    public QuantRoadReviewService(
            JdbcTemplate jdbcTemplate,
            QuantRoadGovernanceService quantRoadGovernanceService,
            QuantRoadQueryService quantRoadQueryService)
    {
        this.jdbcTemplate = jdbcTemplate;
        this.quantRoadGovernanceService = quantRoadGovernanceService;
        this.quantRoadQueryService = quantRoadQueryService;
    }

    @PostConstruct
    void initializeReviewSchema()
    {
        ensureReviewSchema();
    }

    public List<Map<String, Object>> reviewCandidates(String reviewLevel, Integer limit)
    {
        int safeLimit = limit == null ? 12 : Math.max(1, Math.min(limit, 50));
        List<Map<String, Object>> candidates = new ArrayList<>();
        if (reviewLevel == null || reviewLevel.isBlank() || "trade".equalsIgnoreCase(reviewLevel))
        {
            candidates.addAll(jdbcTemplate.queryForList(
                    "SELECT ts.id AS signal_id, ts.stock_code, ts.stock_name, ts.strategy_id, f.status, f.remark, f.check_date " +
                            "FROM signal_execution_feedback f " +
                            "JOIN trade_signal ts ON ts.id = f.signal_id " +
                            "WHERE f.status IN ('MISSED', 'PENDING') " +
                            "ORDER BY f.check_date DESC, ts.id DESC LIMIT ?",
                    safeLimit).stream().map(row -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("reviewLevel", "trade");
                        item.put("signalId", toLong(row.get("signal_id")));
                        item.put("stockCode", row.get("stock_code"));
                        item.put("reviewTargetName", row.get("stock_name"));
                        item.put("strategyId", toLong(row.get("strategy_id")));
                        item.put("reason", row.get("remark"));
                        item.put("status", row.get("status"));
                        item.put("date", row.get("check_date"));
                        return item;
                    }).toList());
        }
        if (reviewLevel == null || reviewLevel.isBlank() || "strategy".equalsIgnoreCase(reviewLevel))
        {
            candidates.addAll(jdbcTemplate.queryForList(
                    "SELECT strategy_id, run_time, annual_return, max_drawdown, win_rate, total_profit, remark " +
                            "FROM strategy_run_log WHERE is_invalid = 1 ORDER BY run_time DESC LIMIT ?",
                    safeLimit).stream().map(row -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("reviewLevel", "strategy");
                        item.put("strategyId", toLong(row.get("strategy_id")));
                        item.put("reviewTargetName", resolveStrategyName(toLong(row.get("strategy_id"))));
                        item.put("reason", row.get("remark"));
                        item.put("status", "INVALID");
                        item.put("date", row.get("run_time"));
                        return item;
                    }).toList());
        }
        if (reviewLevel == null || reviewLevel.isBlank() || "governance".equalsIgnoreCase(reviewLevel))
        {
            candidates.addAll(jdbcTemplate.queryForList(
                    "SELECT run_date, baseline_strategy_id, candidate_strategy_id, recommendation, remark " +
                            "FROM canary_run_log ORDER BY run_date DESC, id DESC LIMIT ?",
                    safeLimit).stream().map(row -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("reviewLevel", "governance");
                        item.put("baselineStrategyId", toLong(row.get("baseline_strategy_id")));
                        item.put("candidateStrategyId", toLong(row.get("candidate_strategy_id")));
                        item.put("reviewTargetName",
                                resolveStrategyName(toLong(row.get("baseline_strategy_id"))) + " vs "
                                        + resolveStrategyName(toLong(row.get("candidate_strategy_id"))));
                        item.put("reason", row.get("remark"));
                        item.put("status", row.get("recommendation"));
                        item.put("date", row.get("run_date"));
                        return item;
                    }).toList());
        }
        return candidates.stream()
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("date")), Comparator.reverseOrder()))
                .limit(safeLimit)
                .toList();
    }

    public List<Map<String, Object>> reviewCases(String reviewLevel, String caseType, String assetType, Integer limit)
    {
        int safeLimit = limit == null ? 12 : Math.max(1, Math.min(limit, 50));
        synchronizeReviewCases(safeLimit);
        return jdbcTemplate.queryForList(
                "SELECT id, review_level, case_type, case_status, resolution_status, asset_type, severity, " +
                        " strategy_id, stock_code, signal_id, baseline_strategy_id, candidate_strategy_id, " +
                        " review_target_name, reason, scope_type, scope_pool_code, source_action, " +
                        " last_detected_time, last_review_conclusion, last_review_time " +
                "FROM quant_review_case " +
                        "WHERE (CAST(? AS VARCHAR) IS NULL OR review_level = CAST(? AS VARCHAR)) " +
                        "AND (CAST(? AS VARCHAR) IS NULL OR case_type = CAST(? AS VARCHAR)) " +
                        "AND (CAST(? AS VARCHAR) IS NULL OR asset_type = CAST(? AS VARCHAR)) " +
                        "ORDER BY CASE severity WHEN 'P0' THEN 0 WHEN 'P1' THEN 1 ELSE 2 END, last_detected_time DESC, id DESC LIMIT ?",
                trimText(reviewLevel),
                trimText(reviewLevel),
                trimText(caseType),
                trimText(caseType),
                trimText(assetType),
                trimText(assetType),
                safeLimit).stream().map(this::toCamelCaseMap).map(row -> {
                    row.put("caseId", row.get("id"));
                    return row;
                }).toList();
    }

    public Map<String, Object> reviewCaseDetail(Long caseId)
    {
        if (caseId == null)
        {
            return Map.of();
        }
        synchronizeReviewCases(50);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, case_key, review_level, case_type, case_status, resolution_status, asset_type, severity, " +
                        " strategy_id, stock_code, signal_id, baseline_strategy_id, candidate_strategy_id, date_range_start, date_range_end, " +
                        " review_target_name, reason, source_page, source_action, scope_type, scope_pool_code, evidence_snapshot, " +
                        " last_detected_time, last_review_id, last_review_conclusion, last_review_time " +
                        "FROM quant_review_case WHERE id = ? LIMIT 1",
                caseId);
        if (rows.isEmpty())
        {
            return Map.of();
        }
        Map<String, Object> detail = toCamelCaseMap(rows.get(0));
        detail.put("caseId", detail.get("id"));
        detail.put("routeQuery", buildReviewCaseRouteQuery(detail));
        detail.put("history", reviewConclusionHistory(
                String.valueOf(detail.get("reviewLevel")),
                toLong(detail.get("strategyId")),
                normalizeStockCode(String.valueOf(detail.get("stockCode"))),
                toLong(detail.get("signalId")),
                10));
        return detail;
    }

    public Map<String, Object> reviewSummary(
            String reviewLevel,
            Long strategyId,
            String stockCode,
            Long signalId,
            String dateRangeStart,
            String dateRangeEnd,
            Long baselineStrategyId,
            Long candidateStrategyId,
            Integer months,
            Long caseId)
    {
        ResolvedReviewContext context = resolveContext(
                reviewLevel,
                strategyId,
                stockCode,
                signalId,
                dateRangeStart,
                dateRangeEnd,
                baselineStrategyId,
                candidateStrategyId,
                months,
                caseId);
        Map<String, Object> latestConclusion = reviewConclusionHistory(
                context.reviewLevel(),
                context.strategyId(),
                context.stockCode(),
                context.signalId(),
                1).stream().findFirst().orElse(Map.of());

        long signalCount = queryCount(
                "SELECT COUNT(1) FROM trade_signal WHERE signal_date BETWEEN ? AND ? " +
                        "AND (CAST(? AS BIGINT) IS NULL OR strategy_id = CAST(? AS BIGINT)) " +
                        "AND (CAST(? AS VARCHAR) IS NULL OR stock_code = CAST(? AS VARCHAR))",
                Date.valueOf(context.startDate()),
                Date.valueOf(context.endDate()),
                context.strategyId(),
                context.strategyId(),
                context.stockCode(),
                context.stockCode());
        long executionIssueCount = queryCount(
                "SELECT COUNT(1) FROM signal_execution_feedback f " +
                        "JOIN trade_signal ts ON ts.id = f.signal_id " +
                        "WHERE f.check_date BETWEEN ? AND ? " +
                        "AND (CAST(? AS BIGINT) IS NULL OR ts.strategy_id = CAST(? AS BIGINT)) " +
                        "AND (CAST(? AS VARCHAR) IS NULL OR ts.stock_code = CAST(? AS VARCHAR)) " +
                        "AND f.status IN ('MISSED', 'PENDING')",
                Date.valueOf(context.startDate()),
                Date.valueOf(context.endDate()),
                context.strategyId(),
                context.strategyId(),
                context.stockCode(),
                context.stockCode());
        long invalidLogCount = queryCount(
                "SELECT COUNT(1) FROM strategy_run_log WHERE is_invalid = 1 " +
                        "AND (CAST(? AS BIGINT) IS NULL OR strategy_id = CAST(? AS BIGINT)) " +
                        "AND CAST(run_time AS DATE) BETWEEN ? AND ?",
                context.strategyId(),
                context.strategyId(),
                Date.valueOf(context.startDate()),
                Date.valueOf(context.endDate()));

        Map<String, Object> governanceSummary = context.isGovernance()
                ? quantRoadGovernanceService.shadowCompareSummary(
                        context.baselineStrategyId(),
                        context.candidateStrategyId(),
                        context.months())
                : Map.of();

        String reviewConclusion = valueOrDefault(latestConclusion.get("reviewConclusion"), "");
        String suggestedAction = valueOrDefault(latestConclusion.get("suggestedAction"), "");
        String primaryReason = valueOrDefault(latestConclusion.get("primaryReason"), "");
        String secondaryReason = valueOrDefault(latestConclusion.get("secondaryReason"), "");
        String confidenceLevel = valueOrDefault(latestConclusion.get("confidenceLevel"), "");
        if (reviewConclusion.isBlank())
        {
            reviewConclusion = deriveReviewConclusion(context, governanceSummary, executionIssueCount, invalidLogCount);
            suggestedAction = deriveSuggestedAction(context, governanceSummary, reviewConclusion);
            primaryReason = derivePrimaryReason(context, governanceSummary, executionIssueCount, invalidLogCount);
            secondaryReason = deriveSecondaryReason(context, signalCount, executionIssueCount);
            confidenceLevel = context.isGovernance()
                    ? valueOrDefault(governanceSummary.get("confidenceLevel"), "MEDIUM")
                    : (invalidLogCount > 0 ? "HIGH" : (executionIssueCount > 0 ? "MEDIUM" : "LOW"));
        }

        Map<String, Object> shadowQuery = new LinkedHashMap<>();
        if (context.baselineStrategyId() != null)
        {
            shadowQuery.put("baselineStrategyId", context.baselineStrategyId());
        }
        if (context.candidateStrategyId() != null)
        {
            shadowQuery.put("candidateStrategyId", context.candidateStrategyId());
        }
        if (context.months() != null)
        {
            shadowQuery.put("months", context.months());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseId", context.caseId());
        result.put("reviewId", latestConclusion.get("id"));
        result.put("reviewLevel", context.reviewLevel());
        result.put("reviewTargetId", context.reviewTargetId());
        result.put("reviewTargetName", context.reviewTargetName());
        result.put("startDate", context.startDate());
        result.put("endDate", context.endDate());
        result.put("reviewConclusion", reviewConclusion);
        result.put("primaryReason", primaryReason);
        result.put("secondaryReason", secondaryReason);
        result.put("suggestedAction", suggestedAction);
        result.put("confidenceLevel", confidenceLevel);
        result.put("lastUpdatedTime", latestConclusion.get("createTime"));
        result.put("signalCount", signalCount);
        result.put("executionIssueCount", executionIssueCount);
        result.put("invalidLogCount", invalidLogCount);
        result.put("governanceSummary", governanceSummary);
        result.put("relatedActions", List.of(
                actionLink("去执行回写", "/quant/execution", Map.of("focus", "abnormal")),
                actionLink("去影子对比", "/quant/shadow", shadowQuery)));
        return result;
    }

    public Map<String, Object> reviewKline(
            String reviewLevel,
            Long strategyId,
            String stockCode,
            Long signalId,
            String dateRangeStart,
            String dateRangeEnd)
    {
        ResolvedReviewContext context = resolveContext(reviewLevel, strategyId, stockCode, signalId, dateRangeStart, dateRangeEnd, null, null, 6, null);
        if (context.stockCode() == null)
        {
            return emptyKlinePayload(context);
        }
        List<Map<String, Object>> dailyRows = jdbcTemplate.queryForList(
                "SELECT trade_date, open, high, low, close, volume " +
                        "FROM stock_daily WHERE stock_code = ? AND trade_date BETWEEN ? AND ? ORDER BY trade_date",
                context.stockCode(),
                Date.valueOf(context.startDate()),
                Date.valueOf(context.endDate()));
        if (dailyRows.isEmpty())
        {
            return emptyKlinePayload(context);
        }
        List<String> categories = new ArrayList<>();
        List<List<Double>> candles = new ArrayList<>();
        List<Double> volumes = new ArrayList<>();
        List<Double> closes = new ArrayList<>();
        for (Map<String, Object> row : dailyRows)
        {
            categories.add(String.valueOf(row.get("trade_date")));
            candles.add(List.of(
                    toDouble(row.get("open")),
                    toDouble(row.get("close")),
                    toDouble(row.get("low")),
                    toDouble(row.get("high"))));
            volumes.add(toDouble(row.get("volume")));
            closes.add(toDouble(row.get("close")));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewLevel", context.reviewLevel());
        result.put("stockCode", context.stockCode());
        result.put("strategyId", context.strategyId());
        result.put("categories", categories);
        result.put("candles", candles);
        result.put("volumes", volumes);
        result.put("ma5Series", movingAverage(closes, 5));
        result.put("ma10Series", movingAverage(closes, 10));
        result.put("ma20Series", movingAverage(closes, 20));
        result.put("signalPoints", loadSignalPoints(context));
        result.put("executionPoints", loadExecutionPoints(context));
        result.put("holdingRanges", loadHoldingRanges(context));
        result.put("marketStatusSegments", loadMarketStatusSegments(context.startDate(), context.endDate()));
        return result;
    }

    public Map<String, Object> holdingRange(
            String reviewLevel,
            Long strategyId,
            String stockCode,
            Long signalId,
            String dateRangeStart,
            String dateRangeEnd)
    {
        ResolvedReviewContext context = resolveContext(reviewLevel, strategyId, stockCode, signalId, dateRangeStart, dateRangeEnd, null, null, 6, null);
        List<Map<String, Object>> ranges = loadHoldingRanges(context);
        List<Map<String, Object>> detailedRanges = new ArrayList<>();
        for (Map<String, Object> range : ranges)
        {
            LocalDate entryDate = parseDateObject(range.get("entryDate"));
            LocalDate exitDate = parseDateObject(range.get("exitDate"));
            LocalDate evaluationEnd = exitDate == null ? context.endDate() : exitDate;
            List<Map<String, Object>> dailyRows = jdbcTemplate.queryForList(
                    "SELECT trade_date, high, low, close FROM stock_daily WHERE stock_code = ? AND trade_date BETWEEN ? AND ? ORDER BY trade_date",
                    context.stockCode(),
                    Date.valueOf(entryDate),
                    Date.valueOf(evaluationEnd));
            double entryPrice = toDouble(range.get("entryPrice"));
            double maxProfit = 0D;
            double maxDrawdown = 0D;
            for (Map<String, Object> dailyRow : dailyRows)
            {
                double high = toDouble(dailyRow.get("high"));
                double low = toDouble(dailyRow.get("low"));
                maxProfit = Math.max(maxProfit, percentageChange(high, entryPrice));
                maxDrawdown = Math.min(maxDrawdown, percentageChange(low, entryPrice));
            }
            Map<String, Object> item = new LinkedHashMap<>(range);
            item.put("holdingDays", ChronoUnit.DAYS.between(entryDate, evaluationEnd) + 1);
            item.put("maxFloatingProfit", round(maxProfit));
            item.put("maxDrawdownInHolding", round(maxDrawdown));
            item.put("realizedProfit", range.get("exitPrice") == null ? null : round(percentageChange(toDouble(range.get("exitPrice")), entryPrice)));
            detailedRanges.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewLevel", context.reviewLevel());
        result.put("stockCode", context.stockCode());
        result.put("ranges", detailedRanges);
        return result;
    }

    public Map<String, Object> navDrawdown(
            Long strategyId,
            String dateRangeStart,
            String dateRangeEnd)
    {
        ResolvedReviewContext context = resolveContext("strategy", strategyId, null, null, dateRangeStart, dateRangeEnd, null, null, 6, null);
        if (context.strategyId() == null)
        {
            return Map.of("categories", List.of(), "strategyNavSeries", List.of(), "benchmarkNavSeries", List.of(), "drawdownSeries", List.of());
        }
        List<Map<String, Object>> logRows = jdbcTemplate.queryForList(
                "SELECT run_date, annual_return, total_profit, is_invalid FROM (" +
                        " SELECT CAST(run_time AS DATE) AS run_date, annual_return, total_profit, is_invalid, " +
                        " ROW_NUMBER() OVER (PARTITION BY CAST(run_time AS DATE) ORDER BY run_time DESC, id DESC) AS rn " +
                        " FROM strategy_run_log WHERE strategy_id = ? AND CAST(run_time AS DATE) BETWEEN ? AND ?" +
                        ") daily WHERE rn = 1 ORDER BY run_date",
                context.strategyId(),
                Date.valueOf(context.startDate()),
                Date.valueOf(context.endDate()));
        List<Map<String, Object>> benchmarkRows = jdbcTemplate.queryForList(
                "SELECT trade_date, hs300_close FROM market_status WHERE trade_date BETWEEN ? AND ? ORDER BY trade_date",
                Date.valueOf(context.startDate()),
                Date.valueOf(context.endDate()));
        List<String> categories = new ArrayList<>();
        List<Double> strategyNavSeries = new ArrayList<>();
        List<Double> benchmarkNavSeries = new ArrayList<>();
        List<Double> drawdownSeries = new ArrayList<>();
        List<Boolean> invalidFlags = new ArrayList<>();

        double baseProfit = logRows.isEmpty() ? 0D : toDouble(logRows.get(0).get("total_profit"));
        double runningMax = Double.MIN_VALUE;
        for (Map<String, Object> row : logRows)
        {
            categories.add(String.valueOf(row.get("run_date")));
            double nav = 1D + (toDouble(row.get("total_profit")) - baseProfit) / 100D;
            runningMax = Math.max(runningMax, nav);
            strategyNavSeries.add(round(nav));
            drawdownSeries.add(round(runningMax == 0D ? 0D : ((nav / runningMax) - 1D) * 100D));
            invalidFlags.add(toInt(row.get("is_invalid")) == 1);
        }
        benchmarkNavSeries.addAll(normalizeBenchmarkSeries(benchmarkRows, categories));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategyId", context.strategyId());
        result.put("strategyName", context.reviewTargetName());
        result.put("categories", categories);
        result.put("strategyNavSeries", strategyNavSeries);
        result.put("benchmarkNavSeries", benchmarkNavSeries);
        result.put("drawdownSeries", drawdownSeries);
        result.put("invalidTriggerFlags", invalidFlags);
        return result;
    }

    public Map<String, Object> marketOverlay(
            Long strategyId,
            String dateRangeStart,
            String dateRangeEnd)
    {
        ResolvedReviewContext context = resolveContext("strategy", strategyId, null, null, dateRangeStart, dateRangeEnd, null, null, 6, null);
        List<Map<String, Object>> marketRows = jdbcTemplate.queryForList(
                "SELECT trade_date, status, hs300_close FROM market_status WHERE trade_date BETWEEN ? AND ? ORDER BY trade_date",
                Date.valueOf(context.startDate()),
                Date.valueOf(context.endDate()));
        List<Map<String, Object>> auditRows = context.strategyId() == null ? List.of() : jdbcTemplate.queryForList(
                "SELECT CAST(create_time AS DATE) AS action_date, decision, reason FROM strategy_switch_audit " +
                        "WHERE strategy_id = ? AND CAST(create_time AS DATE) BETWEEN ? AND ? ORDER BY create_time",
                context.strategyId(),
                Date.valueOf(context.startDate()),
                Date.valueOf(context.endDate()));
        Map<String, Map<String, Object>> auditByDate = new HashMap<>();
        for (Map<String, Object> row : auditRows)
        {
            auditByDate.put(String.valueOf(row.get("action_date")), row);
        }
        BigDecimal baseWeight = resolveStrategyBaseWeight(context.strategyId());
        List<Map<String, Object>> points = new ArrayList<>();
        for (Map<String, Object> marketRow : marketRows)
        {
            String tradeDate = String.valueOf(marketRow.get("trade_date"));
            Map<String, Object> audit = auditByDate.get(tradeDate);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", tradeDate);
            item.put("marketStatus", marketRow.get("status"));
            item.put("benchmarkClose", marketRow.get("hs300_close"));
            item.put("strategyEnabled", audit == null || !"BLOCK".equals(String.valueOf(audit.get("decision"))));
            item.put("budgetPct", baseWeight);
            item.put("gateDecision", audit == null ? "ALLOW" : audit.get("decision"));
            item.put("gateReason", audit == null ? "no_gate_adjustment" : audit.get("reason"));
            points.add(item);
        }
        return Map.of(
                "strategyId", context.strategyId(),
                "strategyName", context.reviewTargetName(),
                "points", points);
    }

    public Map<String, Object> governanceEvidence(Long baselineStrategyId, Long candidateStrategyId, Integer months)
    {
        Long resolvedBaseline = baselineStrategyId;
        Long resolvedCandidate = candidateStrategyId;
        Integer resolvedMonths = months == null ? 6 : months;
        if (resolvedBaseline == null || resolvedCandidate == null)
        {
            List<Map<String, Object>> latestRows = jdbcTemplate.queryForList(
                    "SELECT baseline_strategy_id, candidate_strategy_id, months FROM canary_run_log ORDER BY run_date DESC, id DESC LIMIT 1");
            if (!latestRows.isEmpty())
            {
                Map<String, Object> latest = latestRows.get(0);
                resolvedBaseline = resolvedBaseline == null ? toLong(latest.get("baseline_strategy_id")) : resolvedBaseline;
                resolvedCandidate = resolvedCandidate == null ? toLong(latest.get("candidate_strategy_id")) : resolvedCandidate;
                resolvedMonths = months == null ? toInt(latest.get("months")) : resolvedMonths;
            }
        }
        if (resolvedBaseline == null || resolvedCandidate == null)
        {
            return Map.of(
                    "recommendation", "INSUFFICIENT_DATA",
                    "summary", Map.of(),
                    "charts", Map.of(),
                    "monthsData", List.of());
        }
        Map<String, Object> summary = quantRoadGovernanceService.shadowCompareSummary(resolvedBaseline, resolvedCandidate, resolvedMonths);
        Map<String, Object> charts = quantRoadGovernanceService.shadowCompareCharts(resolvedBaseline, resolvedCandidate, resolvedMonths);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("baselineStrategyId", resolvedBaseline);
        payload.put("candidateStrategyId", resolvedCandidate);
        payload.put("months", resolvedMonths);
        payload.put("recommendation", summary.get("recommendation"));
        payload.put("governanceAction", summary.get("governanceAction"));
        payload.put("summary", summary);
        payload.put("charts", charts);
        payload.put("monthsData", charts.get("categories"));
        return payload;
    }

    public List<Map<String, Object>> timeline(
            String reviewLevel,
            Long strategyId,
            String stockCode,
            Long signalId,
            String dateRangeStart,
            String dateRangeEnd,
            Long baselineStrategyId,
            Long candidateStrategyId,
            Integer months,
            Integer limit)
    {
        ResolvedReviewContext context = resolveContext(
                reviewLevel,
                strategyId,
                stockCode,
                signalId,
                dateRangeStart,
                dateRangeEnd,
                baselineStrategyId,
                candidateStrategyId,
                months,
                null);
        int safeLimit = sanitizeTimelineLimit(limit);
        List<Map<String, Object>> events = new ArrayList<>();
        if (context.stockCode() != null || context.signalId() != null || context.strategyId() != null)
        {
            events.addAll(jdbcTemplate.queryForList(
                    "SELECT id, signal_date, signal_type, stock_code, stock_name, strategy_id " +
                            "FROM trade_signal WHERE signal_date BETWEEN ? AND ? " +
                            "AND (CAST(? AS BIGINT) IS NULL OR strategy_id = CAST(? AS BIGINT)) " +
                            "AND (CAST(? AS VARCHAR) IS NULL OR stock_code = CAST(? AS VARCHAR)) " +
                            "AND (CAST(? AS BIGINT) IS NULL OR id = CAST(? AS BIGINT)) ORDER BY signal_date DESC, id DESC LIMIT ?",
                    Date.valueOf(context.startDate()),
                    Date.valueOf(context.endDate()),
                    context.strategyId(),
                    context.strategyId(),
                    context.stockCode(),
                    context.stockCode(),
                    context.signalId(),
                    context.signalId(),
                    safeLimit).stream().map(row -> timelineEvent(
                            row.get("signal_date"),
                            "SIGNAL",
                            "信号生成",
                            String.valueOf(row.get("stock_code")) + " " + row.get("signal_type"),
                            "signal",
                            row.get("id"),
                            "generated")).toList());

            events.addAll(jdbcTemplate.queryForList(
                    "SELECT id, trade_date, side, stock_code, quantity, price, signal_id " +
                            "FROM execution_record WHERE trade_date BETWEEN ? AND ? " +
                            "AND (CAST(? AS BIGINT) IS NULL OR strategy_id = CAST(? AS BIGINT)) " +
                            "AND (CAST(? AS VARCHAR) IS NULL OR stock_code = CAST(? AS VARCHAR)) " +
                            "AND (CAST(? AS BIGINT) IS NULL OR signal_id = CAST(? AS BIGINT)) ORDER BY trade_date DESC, id DESC LIMIT ?",
                    Date.valueOf(context.startDate()),
                    Date.valueOf(context.endDate()),
                    context.strategyId(),
                    context.strategyId(),
                    context.stockCode(),
                    context.stockCode(),
                    context.signalId(),
                    context.signalId(),
                    safeLimit).stream().map(row -> timelineEvent(
                            row.get("trade_date"),
                            "EXECUTION",
                            "成交回写",
                            String.valueOf(row.get("stock_code")) + " " + row.get("side") + " " + row.get("quantity") + " @ " + row.get("price"),
                            "execution",
                            row.get("id"),
                            "recorded")).toList());
        }

        if (context.strategyId() != null)
        {
            events.addAll(jdbcTemplate.queryForList(
                    "SELECT CAST(run_time AS DATE) AS run_date, id, is_invalid, remark FROM strategy_run_log " +
                            "WHERE strategy_id = ? AND CAST(run_time AS DATE) BETWEEN ? AND ? ORDER BY run_time DESC LIMIT ?",
                    context.strategyId(),
                    Date.valueOf(context.startDate()),
                    Date.valueOf(context.endDate()),
                    safeLimit).stream().map(row -> timelineEvent(
                            row.get("run_date"),
                            "STRATEGY_LOG",
                            "策略运行",
                            String.valueOf(row.get("remark")),
                            "strategyLog",
                            row.get("id"),
                            toInt(row.get("is_invalid")) == 1 ? "invalid" : "healthy")).toList());
        }

        if (context.isGovernance())
        {
            events.addAll(jdbcTemplate.queryForList(
                    "SELECT run_date, id, recommendation, remark FROM canary_run_log " +
                            "WHERE baseline_strategy_id = ? AND candidate_strategy_id = ? AND months = ? " +
                            "ORDER BY run_date DESC, id DESC LIMIT 12",
                    context.baselineStrategyId(),
                    context.candidateStrategyId(),
                    context.months()).stream().map(row -> timelineEvent(
                            row.get("run_date"),
                            "CANARY",
                            "Canary 评估",
                            String.valueOf(row.get("remark")),
                            "canary",
                            row.get("id"),
                            row.get("recommendation"))).toList());
            events.addAll(quantRoadGovernanceService.governanceHistory(
                    context.baselineStrategyId(),
                    context.candidateStrategyId(),
                    10).stream().map(row -> timelineEvent(
                            row.get("createTime"),
                            "GOVERNANCE",
                            "治理决策",
                            String.valueOf(row.get("remark")),
                            "governance",
                            row.get("id"),
                            row.get("governanceAction"))).toList());
        }

        events.addAll(reviewConclusionHistory(context.reviewLevel(), context.strategyId(), context.stockCode(), context.signalId(), 10).stream()
                .map(row -> timelineEvent(
                        row.get("createTime"),
                        "REVIEW",
                        "复盘结论",
                        String.valueOf(row.get("primaryReason")),
                        "review",
                        row.get("id"),
                        row.get("reviewConclusion")))
                .toList());
        return events.stream()
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("eventTime")), Comparator.reverseOrder()))
                .limit(safeLimit)
                .toList();
    }

    public Map<String, Object> ruleExplain(
            String reviewLevel,
            Long strategyId,
            String stockCode,
            Long baselineStrategyId,
            Long candidateStrategyId)
    {
        if ("governance".equalsIgnoreCase(normalizeReviewLevel(reviewLevel)))
        {
            Map<String, Object> summary = governanceEvidence(baselineStrategyId, candidateStrategyId, 6);
            return Map.of(
                    "reviewLevel", "governance",
                    "ruleTitle", "治理评审规则",
                    "explanations", List.of(
                            "先看候选策略是否在足够月份内同时改善收益、回撤和失效率。",
                            "再看优势是否稳定，而不是由单月异常收益驱动。",
                            "最后把结论落到 keep / observe / replace / disable。"),
                    "evidenceSummary", summary.get("summary"));
        }
        if (strategyId == null)
        {
            return Map.of(
                    "reviewLevel", normalizeReviewLevel(reviewLevel),
                    "ruleTitle", "复盘规则",
                    "explanations", List.of(
                            "先确认信号是否符合规则，再确认执行是否偏离，最后确认持仓结果与治理动作。"));
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT strategy_name, strategy_type, params FROM strategy_config WHERE id = ? LIMIT 1",
                strategyId);
        if (rows.isEmpty())
        {
            return Map.of("reviewLevel", normalizeReviewLevel(reviewLevel), "ruleTitle", "未找到策略配置", "explanations", List.of());
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> params = parseJsonObject(row.get("params"));
        List<String> explanations = new ArrayList<>();
        explanations.add("策略类型: " + row.get("strategy_type"));
        if (params.containsKey("ma_period"))
        {
            explanations.add("单均线规则：价格向上突破 MA" + params.get("ma_period") + " 触发买入，跌破时触发卖出。");
        }
        if (params.containsKey("short_ma_period") && params.containsKey("long_ma_period"))
        {
            explanations.add("双均线规则：短均线(" + params.get("short_ma_period") + ") 上穿长均线("
                    + params.get("long_ma_period") + ") 触发买入。");
        }
        if (params.containsKey("stop_loss_rate"))
        {
            explanations.add("止损规则：浮亏达到 " + params.get("stop_loss_rate") + " 时应进入风控退出。");
        }
        if (params.containsKey("max_total_position_pct"))
        {
            explanations.add("总仓约束：组合总仓不应超过 " + params.get("max_total_position_pct") + "。");
        }
        if (stockCode != null)
        {
            explanations.add("当前复盘标的：" + stockCode + "，重点查看买卖点、执行点和持仓退出原因。");
        }
        return Map.of(
                "reviewLevel", normalizeReviewLevel(reviewLevel),
                "ruleTitle", row.get("strategy_name") + " 规则解释",
                "strategyId", strategyId,
                "explanations", explanations,
                "params", params);
    }

    public Map<String, Object> submitConclusion(QuantReviewConclusionRequest request)
    {
        ensureReviewSchema();
        if (request == null)
        {
            throw new ServiceException("review conclusion request is required");
        }
        String reviewLevel = normalizeReviewLevel(request.getReviewLevel());
        jdbcTemplate.update(
                "INSERT INTO quant_review_conclusion (" +
                        " review_level, case_id, strategy_id, stock_code, signal_id, date_range_start, date_range_end, review_target_name, " +
                        " review_conclusion, primary_reason, secondary_reason, suggested_action, confidence_level, actor, remark, " +
                        " source_page, source_action, evidence_snapshot" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB))",
                reviewLevel,
                request.getCaseId(),
                request.getStrategyId(),
                normalizeStockCode(request.getStockCode()),
                request.getSignalId(),
                parseDate(request.getDateRangeStart()),
                parseDate(request.getDateRangeEnd()),
                normalizeText(request.getReviewTargetName()),
                normalizeText(request.getReviewConclusion()),
                request.getPrimaryReason(),
                request.getSecondaryReason(),
                normalizeText(request.getSuggestedAction()),
                normalizeText(request.getConfidenceLevel()),
                request.getActor(),
                request.getRemark(),
                normalizeText(request.getSourcePage()),
                normalizeText(request.getSourceAction()),
                JSON.toJSONString(request.getEvidenceSnapshot() == null ? Map.of() : request.getEvidenceSnapshot()));

        Long reviewId = jdbcTemplate.queryForObject(
                "SELECT id FROM quant_review_conclusion ORDER BY id DESC LIMIT 1",
                Long.class);
        if (request.getCaseId() != null && reviewId != null)
        {
            jdbcTemplate.update(
                    "UPDATE quant_review_case SET resolution_status = ?, last_review_id = ?, last_review_conclusion = ?, last_review_time = NOW(), update_time = NOW() WHERE id = ?",
                    "REVIEWED",
                    reviewId,
                    normalizeText(request.getReviewConclusion()),
                    request.getCaseId());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewId", reviewId);
        result.put("reviewLevel", reviewLevel);
        result.put("caseId", request.getCaseId());
        result.put("reviewConclusion", normalizeText(request.getReviewConclusion()));
        result.put("suggestedAction", normalizeText(request.getSuggestedAction()));
        result.put("history", reviewConclusionHistory(
                reviewLevel,
                request.getStrategyId(),
                normalizeStockCode(request.getStockCode()),
                request.getSignalId(),
                10));
        return result;
    }

    private Map<String, Object> emptyKlinePayload(ResolvedReviewContext context)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewLevel", context.reviewLevel());
        result.put("stockCode", context.stockCode());
        result.put("strategyId", context.strategyId());
        result.put("categories", List.of());
        result.put("candles", List.of());
        result.put("volumes", List.of());
        result.put("ma5Series", List.of());
        result.put("ma10Series", List.of());
        result.put("ma20Series", List.of());
        result.put("signalPoints", List.of());
        result.put("executionPoints", List.of());
        result.put("holdingRanges", List.of());
        result.put("marketStatusSegments", List.of());
        return result;
    }

    private void ensureReviewSchema()
    {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS quant_review_conclusion (" +
                        " id BIGSERIAL PRIMARY KEY," +
                        " review_level VARCHAR(20) NOT NULL," +
                        " case_id BIGINT," +
                        " strategy_id BIGINT," +
                        " stock_code VARCHAR(20)," +
                        " signal_id BIGINT," +
                        " date_range_start DATE," +
                        " date_range_end DATE," +
                        " review_target_name VARCHAR(120)," +
                        " review_conclusion VARCHAR(20) NOT NULL," +
                        " primary_reason VARCHAR(255)," +
                        " secondary_reason VARCHAR(255)," +
                        " suggested_action VARCHAR(30)," +
                        " confidence_level VARCHAR(20)," +
                        " actor VARCHAR(80)," +
                        " remark TEXT," +
                        " source_page VARCHAR(40)," +
                        " source_action VARCHAR(40)," +
                        " evidence_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb," +
                        " create_time TIMESTAMP DEFAULT NOW()" +
                        ")");
        jdbcTemplate.execute("ALTER TABLE quant_review_conclusion ADD COLUMN IF NOT EXISTS case_id BIGINT");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_quant_review_conclusion_scope_time " +
                        "ON quant_review_conclusion(review_level, strategy_id, stock_code, create_time DESC)");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_quant_review_conclusion_signal_time " +
                        "ON quant_review_conclusion(signal_id, create_time DESC)");
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS quant_review_case (" +
                        " id BIGSERIAL PRIMARY KEY," +
                        " case_key VARCHAR(160) NOT NULL UNIQUE," +
                        " review_level VARCHAR(20) NOT NULL," +
                        " case_type VARCHAR(40) NOT NULL," +
                        " case_status VARCHAR(40) NOT NULL," +
                        " resolution_status VARCHAR(20) NOT NULL DEFAULT 'OPEN'," +
                        " asset_type VARCHAR(20)," +
                        " severity VARCHAR(10) NOT NULL DEFAULT 'P2'," +
                        " strategy_id BIGINT," +
                        " stock_code VARCHAR(20)," +
                        " signal_id BIGINT," +
                        " baseline_strategy_id BIGINT," +
                        " candidate_strategy_id BIGINT," +
                        " date_range_start DATE," +
                        " date_range_end DATE," +
                        " review_target_name VARCHAR(120)," +
                        " reason TEXT," +
                        " source_page VARCHAR(40)," +
                        " source_action VARCHAR(40)," +
                        " scope_type VARCHAR(40)," +
                        " scope_pool_code VARCHAR(60)," +
                        " evidence_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb," +
                        " last_detected_time TIMESTAMP," +
                        " last_review_id BIGINT," +
                        " last_review_conclusion VARCHAR(20)," +
                        " last_review_time TIMESTAMP," +
                        " create_time TIMESTAMP DEFAULT NOW()," +
                        " update_time TIMESTAMP DEFAULT NOW()" +
                        ")");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_quant_review_case_level_time " +
                        "ON quant_review_case(review_level, last_detected_time DESC)");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_quant_review_case_signal " +
                        "ON quant_review_case(signal_id, stock_code, strategy_id)");
    }

    private List<Map<String, Object>> reviewConclusionHistory(String reviewLevel, Long strategyId, String stockCode, Long signalId, int limit)
    {
        ensureReviewSchema();
        return jdbcTemplate.queryForList(
                "SELECT id, review_level, case_id, strategy_id, stock_code, signal_id, date_range_start, date_range_end, review_target_name, " +
                        " review_conclusion, primary_reason, secondary_reason, suggested_action, confidence_level, actor, remark, " +
                        " source_page, source_action, evidence_snapshot, create_time " +
                        "FROM quant_review_conclusion " +
                        "WHERE review_level = ? " +
                        "AND COALESCE(strategy_id, 0) = COALESCE(?, 0) " +
                        "AND COALESCE(stock_code, '') = COALESCE(?, '') " +
                        "AND COALESCE(signal_id, 0) = COALESCE(?, 0) " +
                        "ORDER BY create_time DESC, id DESC LIMIT ?",
                reviewLevel,
                strategyId,
                stockCode,
                signalId,
                limit).stream().map(this::toCamelCaseMap).toList();
    }

    private ResolvedReviewContext resolveContext(
            String reviewLevel,
            Long strategyId,
            String stockCode,
            Long signalId,
            String dateRangeStart,
            String dateRangeEnd,
            Long baselineStrategyId,
            Long candidateStrategyId,
            Integer months,
            Long caseId)
    {
        String normalizedReviewLevel = normalizeReviewLevel(reviewLevel);
        String resolvedStockCode = normalizeStockCode(stockCode);
        Long resolvedStrategyId = strategyId;
        Long resolvedSignalId = signalId;
        Long resolvedBaselineStrategyId = baselineStrategyId;
        Long resolvedCandidateStrategyId = candidateStrategyId;
        if (caseId != null)
        {
            synchronizeReviewCases(50);
            List<Map<String, Object>> caseRows = jdbcTemplate.queryForList(
                    "SELECT review_level, strategy_id, stock_code, signal_id, baseline_strategy_id, candidate_strategy_id, date_range_start, date_range_end " +
                            "FROM quant_review_case WHERE id = ? LIMIT 1",
                    caseId);
            if (!caseRows.isEmpty())
            {
                Map<String, Object> caseRow = caseRows.get(0);
                normalizedReviewLevel = normalizeReviewLevel(valueOrDefault(caseRow.get("review_level"), normalizedReviewLevel));
                resolvedStrategyId = resolvedStrategyId == null ? toLong(caseRow.get("strategy_id")) : resolvedStrategyId;
                resolvedStockCode = resolvedStockCode == null
                        ? normalizeStockCode(valueOrDefault(caseRow.get("stock_code"), null))
                        : resolvedStockCode;
                resolvedSignalId = resolvedSignalId == null ? toLong(caseRow.get("signal_id")) : resolvedSignalId;
                resolvedBaselineStrategyId = resolvedBaselineStrategyId == null
                        ? toLong(caseRow.get("baseline_strategy_id"))
                        : resolvedBaselineStrategyId;
                resolvedCandidateStrategyId = resolvedCandidateStrategyId == null
                        ? toLong(caseRow.get("candidate_strategy_id"))
                        : resolvedCandidateStrategyId;
                if (dateRangeStart == null || dateRangeStart.isBlank())
                {
                    LocalDate seededStart = parseDateObject(caseRow.get("date_range_start"));
                    dateRangeStart = seededStart == null ? null : seededStart.toString();
                }
                if (dateRangeEnd == null || dateRangeEnd.isBlank())
                {
                    LocalDate seededEnd = parseDateObject(caseRow.get("date_range_end"));
                    dateRangeEnd = seededEnd == null ? null : seededEnd.toString();
                }
            }
        }
        if (resolvedSignalId != null)
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT stock_code, strategy_id, signal_date, stock_name FROM trade_signal WHERE id = ? LIMIT 1",
                    resolvedSignalId);
            if (!rows.isEmpty())
            {
                Map<String, Object> row = rows.get(0);
                resolvedStockCode = resolvedStockCode == null ? normalizeStockCode(String.valueOf(row.get("stock_code"))) : resolvedStockCode;
                resolvedStrategyId = resolvedStrategyId == null ? toLong(row.get("strategy_id")) : resolvedStrategyId;
                if (dateRangeStart == null || dateRangeEnd == null)
                {
                    LocalDate signalDate = parseDateObject(row.get("signal_date"));
                    dateRangeStart = signalDate.minusDays(20).toString();
                    dateRangeEnd = signalDate.plusDays(20).toString();
                }
            }
        }
        LocalDate startDate = parseDate(dateRangeStart);
        LocalDate endDate = parseDate(dateRangeEnd);
        if (endDate == null)
        {
            endDate = LocalDate.now();
        }
        if (startDate == null)
        {
            startDate = endDate.minusDays(60);
        }
        if (normalizedReviewLevel.equals("governance"))
        {
            String targetName = resolveStrategyName(baselineStrategyId) + " vs " + resolveStrategyName(candidateStrategyId);
            return new ResolvedReviewContext(
                    normalizedReviewLevel,
                    resolvedStrategyId,
                    resolvedStockCode,
                    resolvedSignalId,
                    startDate,
                    endDate,
                    resolvedBaselineStrategyId,
                    resolvedCandidateStrategyId,
                    months == null ? 6 : months,
                    targetName,
                    resolvedCandidateStrategyId,
                    caseId);
        }
        String targetName = resolvedStockCode != null
                ? resolvedStockCode
                : (resolvedStrategyId != null ? resolveStrategyName(resolvedStrategyId) : "复盘对象");
        Long reviewTargetId = resolvedSignalId != null ? resolvedSignalId : resolvedStrategyId;
        return new ResolvedReviewContext(
                normalizedReviewLevel,
                resolvedStrategyId,
                resolvedStockCode,
                resolvedSignalId,
                startDate,
                endDate,
                resolvedBaselineStrategyId,
                resolvedCandidateStrategyId,
                months == null ? 6 : months,
                targetName,
                reviewTargetId,
                caseId);
    }

    private void synchronizeReviewCases(int limit)
    {
        ensureReviewSchema();
        int safeLimit = Math.max(1, Math.min(Math.max(limit, 12), 50));
        List<Map<String, Object>> candidates = quantRoadQueryService.reviewCandidates(safeLimit);
        for (Map<String, Object> candidate : candidates)
        {
            Map<String, Object> caseRow = buildReviewCaseRow(candidate);
            jdbcTemplate.update(
                    "INSERT INTO quant_review_case (" +
                            " case_key, review_level, case_type, case_status, resolution_status, asset_type, severity, " +
                            " strategy_id, stock_code, signal_id, baseline_strategy_id, candidate_strategy_id, date_range_start, date_range_end, " +
                            " review_target_name, reason, source_page, source_action, scope_type, scope_pool_code, evidence_snapshot, last_detected_time, update_time" +
                            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS TIMESTAMP), NOW()) " +
                            "ON CONFLICT (case_key) DO UPDATE SET " +
                            " case_status = EXCLUDED.case_status, asset_type = EXCLUDED.asset_type, severity = EXCLUDED.severity, " +
                            " strategy_id = EXCLUDED.strategy_id, stock_code = EXCLUDED.stock_code, signal_id = EXCLUDED.signal_id, " +
                            " baseline_strategy_id = EXCLUDED.baseline_strategy_id, candidate_strategy_id = EXCLUDED.candidate_strategy_id, " +
                            " date_range_start = EXCLUDED.date_range_start, date_range_end = EXCLUDED.date_range_end, " +
                            " review_target_name = EXCLUDED.review_target_name, reason = EXCLUDED.reason, source_page = EXCLUDED.source_page, " +
                            " source_action = EXCLUDED.source_action, scope_type = EXCLUDED.scope_type, scope_pool_code = EXCLUDED.scope_pool_code, " +
                            " evidence_snapshot = EXCLUDED.evidence_snapshot, last_detected_time = EXCLUDED.last_detected_time, update_time = NOW()",
                    caseRow.values().toArray());
        }
    }

    private Map<String, Object> buildReviewCaseRow(Map<String, Object> candidate)
    {
        String reviewLevel = normalizeReviewLevel(valueOrDefault(candidate.get("reviewLevel"), "trade"));
        Long signalId = toLong(candidate.get("signalId"));
        Long strategyId = toLong(candidate.get("strategyId"));
        String stockCode = normalizeStockCode(valueOrDefault(candidate.get("stockCode"), null));
        Long baselineStrategyId = toLong(candidate.get("baselineStrategyId"));
        Long candidateStrategyId = toLong(candidate.get("candidateStrategyId"));
        String scopeType = trimText(valueOrDefault(candidate.get("scopeType"), null));
        String scopePoolCode = trimText(valueOrDefault(candidate.get("scopePoolCode"), null));
        String assetType = normalizeAssetType(valueOrDefault(candidate.get("assetType"), null), scopeType, stockCode);
        String status = normalizeText(valueOrDefault(candidate.get("status"), "OPEN"));
        String caseType = switch (reviewLevel)
        {
            case "strategy" -> "STRATEGY_REVIEW";
            case "governance" -> "GOVERNANCE_REVIEW";
            default -> "ETF".equals(assetType) ? "ETF_REVIEW" : "TRADE_REVIEW";
        };

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("case_key", buildReviewCaseKey(reviewLevel, signalId, strategyId, stockCode, baselineStrategyId, candidateStrategyId, status));
        row.put("review_level", reviewLevel);
        row.put("case_type", caseType);
        row.put("case_status", status);
        row.put("resolution_status", "OPEN");
        row.put("asset_type", assetType);
        row.put("severity", deriveCaseSeverity(reviewLevel, status));
        row.put("strategy_id", strategyId);
        row.put("stock_code", stockCode);
        row.put("signal_id", signalId);
        row.put("baseline_strategy_id", baselineStrategyId);
        row.put("candidate_strategy_id", candidateStrategyId);
        row.put("date_range_start", null);
        row.put("date_range_end", null);
        row.put("review_target_name", trimText(valueOrDefault(candidate.get("reviewTargetName"), "复盘对象")));
        row.put("reason", trimText(valueOrDefault(candidate.get("reason"), null)));
        row.put("source_page", "review");
        row.put("source_action", trimText(valueOrDefault(candidate.get("sourceAction"), "reviewCandidate")));
        row.put("scope_type", scopeType);
        row.put("scope_pool_code", scopePoolCode);
        row.put("evidence_snapshot", JSON.toJSONString(candidate));
        row.put("last_detected_time", stringifyTimestamp(candidate.get("date")));
        return row;
    }

    private Map<String, Object> buildReviewCaseRouteQuery(Map<String, Object> detail)
    {
        Map<String, Object> query = new LinkedHashMap<>();
        if (detail.get("id") != null)
        {
            query.put("caseId", String.valueOf(detail.get("id")));
        }
        if (detail.get("reviewLevel") != null)
        {
            query.put("reviewLevel", String.valueOf(detail.get("reviewLevel")));
        }
        if (detail.get("strategyId") != null)
        {
            query.put("strategyId", String.valueOf(detail.get("strategyId")));
        }
        if (detail.get("stockCode") != null)
        {
            query.put("stockCode", String.valueOf(detail.get("stockCode")));
        }
        if (detail.get("signalId") != null)
        {
            query.put("signalId", String.valueOf(detail.get("signalId")));
        }
        if (detail.get("baselineStrategyId") != null)
        {
            query.put("baselineStrategyId", String.valueOf(detail.get("baselineStrategyId")));
        }
        if (detail.get("candidateStrategyId") != null)
        {
            query.put("candidateStrategyId", String.valueOf(detail.get("candidateStrategyId")));
        }
        if (detail.get("scopeType") != null)
        {
            query.put("scopeType", String.valueOf(detail.get("scopeType")));
        }
        if (detail.get("scopePoolCode") != null)
        {
            query.put("scopePoolCode", String.valueOf(detail.get("scopePoolCode")));
        }
        return query;
    }

    private List<Map<String, Object>> loadSignalPoints(ResolvedReviewContext context)
    {
        if (context.stockCode() == null)
        {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "SELECT id, signal_date, signal_type, suggest_price, strategy_id FROM trade_signal " +
                        "WHERE stock_code = ? AND signal_date BETWEEN ? AND ? " +
                        "AND (? IS NULL OR strategy_id = ?) ORDER BY signal_date, id",
                context.stockCode(),
                Date.valueOf(context.startDate()),
                Date.valueOf(context.endDate()),
                context.strategyId(),
                context.strategyId()).stream().map(this::toCamelCaseMap).toList();
    }

    private List<Map<String, Object>> loadExecutionPoints(ResolvedReviewContext context)
    {
        if (context.stockCode() == null)
        {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "SELECT id, trade_date, side, price, quantity, strategy_id, signal_id FROM execution_record " +
                        "WHERE stock_code = ? AND trade_date BETWEEN ? AND ? " +
                        "AND (? IS NULL OR strategy_id = ?) ORDER BY trade_date, id",
                context.stockCode(),
                Date.valueOf(context.startDate()),
                Date.valueOf(context.endDate()),
                context.strategyId(),
                context.strategyId()).stream().map(this::toCamelCaseMap).toList();
    }

    private List<Map<String, Object>> loadHoldingRanges(ResolvedReviewContext context)
    {
        if (context.stockCode() == null)
        {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, trade_date, side, price, quantity, signal_id FROM execution_record " +
                        "WHERE stock_code = ? AND trade_date BETWEEN ? AND ? " +
                        "AND (? IS NULL OR strategy_id = ?) ORDER BY trade_date, id",
                context.stockCode(),
                Date.valueOf(context.startDate()),
                Date.valueOf(context.endDate()),
                context.strategyId(),
                context.strategyId());
        List<Map<String, Object>> ranges = new ArrayList<>();
        Map<String, Object> openRange = null;
        for (Map<String, Object> row : rows)
        {
            String side = String.valueOf(row.get("side"));
            if ("BUY".equalsIgnoreCase(side) && openRange == null)
            {
                openRange = new LinkedHashMap<>();
                openRange.put("entryDate", row.get("trade_date"));
                openRange.put("entryPrice", row.get("price"));
                openRange.put("entryExecutionId", row.get("id"));
                openRange.put("signalId", row.get("signal_id"));
                openRange.put("exitReason", "pending_unresolved");
                continue;
            }
            if ("SELL".equalsIgnoreCase(side) && openRange != null)
            {
                openRange.put("exitDate", row.get("trade_date"));
                openRange.put("exitPrice", row.get("price"));
                openRange.put("exitExecutionId", row.get("id"));
                openRange.put("exitReason", "strategy_exit");
                ranges.add(openRange);
                openRange = null;
            }
        }
        if (openRange != null)
        {
            ranges.add(openRange);
        }
        return ranges;
    }

    private List<Map<String, Object>> loadMarketStatusSegments(LocalDate startDate, LocalDate endDate)
    {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT trade_date, status FROM market_status WHERE trade_date BETWEEN ? AND ? ORDER BY trade_date",
                Date.valueOf(startDate),
                Date.valueOf(endDate));
        List<Map<String, Object>> segments = new ArrayList<>();
        String currentStatus = null;
        LocalDate segmentStart = null;
        for (Map<String, Object> row : rows)
        {
            LocalDate tradeDate = parseDateObject(row.get("trade_date"));
            String status = row.get("status") == null ? "unknown" : String.valueOf(row.get("status"));
            if (!Objects.equals(status, currentStatus))
            {
                if (currentStatus != null)
                {
                    segments.add(Map.of(
                            "marketStatus", currentStatus,
                            "startDate", segmentStart,
                            "endDate", tradeDate.minusDays(1)));
                }
                currentStatus = status;
                segmentStart = tradeDate;
            }
        }
        if (currentStatus != null && segmentStart != null)
        {
            segments.add(Map.of(
                    "marketStatus", currentStatus,
                    "startDate", segmentStart,
                    "endDate", endDate));
        }
        return segments;
    }

    private List<Double> movingAverage(List<Double> closes, int period)
    {
        List<Double> result = new ArrayList<>();
        double running = 0D;
        for (int i = 0; i < closes.size(); i++)
        {
            running += closes.get(i);
            if (i >= period)
            {
                running -= closes.get(i - period);
            }
            if (i + 1 < period)
            {
                result.add(null);
            }
            else
            {
                result.add(round(running / period));
            }
        }
        return result;
    }

    private List<Double> normalizeBenchmarkSeries(List<Map<String, Object>> benchmarkRows, List<String> categories)
    {
        Map<String, Double> closeByDate = new HashMap<>();
        for (Map<String, Object> row : benchmarkRows)
        {
            closeByDate.put(String.valueOf(row.get("trade_date")), toDouble(row.get("hs300_close")));
        }
        Double base = null;
        for (String category : categories)
        {
            Double close = closeByDate.get(category);
            if (close != null && base == null)
            {
                base = close;
            }
        }
        List<Double> result = new ArrayList<>();
        for (String category : categories)
        {
            Double close = closeByDate.get(category);
            if (close == null || base == null || base == 0D)
            {
                result.add(null);
            }
            else
            {
                result.add(round(close / base));
            }
        }
        return result;
    }

    private BigDecimal resolveStrategyBaseWeight(Long strategyId)
    {
        if (strategyId == null)
        {
            return BigDecimal.ZERO;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT params FROM strategy_config WHERE id = ? LIMIT 1",
                strategyId);
        if (rows.isEmpty())
        {
            return BigDecimal.ZERO;
        }
        Map<String, Object> params = parseJsonObject(rows.get(0).get("params"));
        Object rawValue = params.get("allocator_base_weight");
        if (rawValue == null)
        {
            rawValue = params.get("max_total_position_pct");
        }
        if (rawValue == null)
        {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(rawValue));
    }

    private long queryCount(String sql, Object... args)
    {
        try
        {
            Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
            return value == null ? 0L : value;
        }
        catch (DataAccessException ex)
        {
            return 0L;
        }
    }

    private int sanitizeTimelineLimit(Integer limit)
    {
        if (limit == null)
        {
            return DEFAULT_TIMELINE_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_TIMELINE_LIMIT));
    }

    private String deriveReviewConclusion(
            ResolvedReviewContext context,
            Map<String, Object> governanceSummary,
            long executionIssueCount,
            long invalidLogCount)
    {
        if (context.isGovernance())
        {
            String action = valueOrDefault(governanceSummary.get("governanceAction"), "OBSERVE");
            if ("DISABLE".equals(action))
            {
                return "INVALID";
            }
            if ("REPLACE".equals(action))
            {
                return "WARNING";
            }
            return "OBSERVE".equals(action) ? "OBSERVE" : "HEALTHY";
        }
        if (invalidLogCount > 0)
        {
            return "WARNING";
        }
        if (executionIssueCount > 0)
        {
            return "OBSERVE";
        }
        return "HEALTHY";
    }

    private String deriveSuggestedAction(ResolvedReviewContext context, Map<String, Object> governanceSummary, String reviewConclusion)
    {
        if (context.isGovernance())
        {
            return valueOrDefault(governanceSummary.get("governanceAction"), "OBSERVE");
        }
        if ("WARNING".equals(reviewConclusion))
        {
            return "REDUCE_WEIGHT";
        }
        if ("OBSERVE".equals(reviewConclusion))
        {
            return "OBSERVE";
        }
        return "KEEP";
    }

    private String derivePrimaryReason(
            ResolvedReviewContext context,
            Map<String, Object> governanceSummary,
            long executionIssueCount,
            long invalidLogCount)
    {
        if (context.isGovernance())
        {
            return valueOrDefault(governanceSummary.get("recommendationReason"), "治理证据尚不足以形成强结论");
        }
        if (invalidLogCount > 0)
        {
            return "策略运行日志出现失效记录，需要核对规则与市场适配性。";
        }
        if (executionIssueCount > 0)
        {
            return "执行闭环存在待确认或漏执行项，复盘重点应先回到信号到成交链路。";
        }
        return "当前样本未出现明显异常，复盘以确认行为一致性为主。";
    }

    private String deriveSecondaryReason(ResolvedReviewContext context, long signalCount, long executionIssueCount)
    {
        if (context.stockCode() != null)
        {
            return "当前标的在区间内共产生 " + signalCount + " 条信号，执行异常 " + executionIssueCount + " 条。";
        }
        if (context.strategyId() != null)
        {
            return "当前策略在选定区间内已形成连续复盘样本。";
        }
        return "建议结合图表证据继续确认下一步动作。";
    }

    private String resolveStrategyName(Long strategyId)
    {
        if (strategyId == null)
        {
            return "未指定策略";
        }
        try
        {
            String name = jdbcTemplate.queryForObject(
                    "SELECT strategy_name FROM strategy_config WHERE id = ?",
                    String.class,
                    strategyId);
            return name == null ? String.valueOf(strategyId) : name;
        }
        catch (DataAccessException ex)
        {
            return String.valueOf(strategyId);
        }
    }

    private Map<String, Object> actionLink(String title, String path, Map<String, Object> query)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", title);
        result.put("path", path);
        result.put("query", query);
        return result;
    }

    private Map<String, Object> timelineEvent(
            Object eventTime,
            String eventType,
            String eventTitle,
            String eventDetail,
            String relatedObjectType,
            Object relatedObjectId,
            Object eventResult)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventTime", eventTime);
        result.put("eventType", eventType);
        result.put("eventTitle", eventTitle);
        result.put("eventDetail", eventDetail);
        result.put("relatedObjectType", relatedObjectType);
        result.put("relatedObjectId", relatedObjectId);
        result.put("eventResult", eventResult);
        return result;
    }

    private Map<String, Object> parseJsonObject(Object raw)
    {
        if (raw == null)
        {
            return Map.of();
        }
        try
        {
            Object parsed = JSON.parse(String.valueOf(raw));
            if (parsed instanceof Map<?, ?> map)
            {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
        }
        catch (Exception ignored)
        {
            // ignore malformed json
        }
        return Map.of();
    }

    private String normalizeReviewLevel(String reviewLevel)
    {
        if (reviewLevel == null || reviewLevel.isBlank())
        {
            return "strategy";
        }
        return reviewLevel.trim().toLowerCase();
    }

    private String normalizeStockCode(String stockCode)
    {
        if (stockCode == null || stockCode.isBlank())
        {
            return null;
        }
        return stockCode.trim();
    }

    private String trimText(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeText(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized.toUpperCase();
    }

    private String normalizeAssetType(String assetType, String scopeType, String stockCode)
    {
        String normalized = normalizeText(assetType);
        if (normalized != null)
        {
            return normalized;
        }
        if ("etf_pool".equalsIgnoreCase(String.valueOf(scopeType)) || "index_mapped_etf_pool".equalsIgnoreCase(String.valueOf(scopeType)))
        {
            return "ETF";
        }
        if (stockCode != null && stockCode.startsWith("5"))
        {
            return "ETF";
        }
        return "EQUITY";
    }

    private String buildReviewCaseKey(
            String reviewLevel,
            Long signalId,
            Long strategyId,
            String stockCode,
            Long baselineStrategyId,
            Long candidateStrategyId,
            String status)
    {
        String normalizedStatus = normalizeText(status);
        if ("governance".equals(reviewLevel))
        {
            return String.format("governance:%s:%s:%s",
                    baselineStrategyId == null ? 0L : baselineStrategyId,
                    candidateStrategyId == null ? 0L : candidateStrategyId,
                    normalizedStatus == null ? "OPEN" : normalizedStatus);
        }
        if ("strategy".equals(reviewLevel))
        {
            return String.format("strategy:%s:%s",
                    strategyId == null ? 0L : strategyId,
                    normalizedStatus == null ? "OPEN" : normalizedStatus);
        }
        if (signalId != null)
        {
            return "trade:signal:" + signalId;
        }
        return String.format("trade:%s:%s:%s",
                strategyId == null ? 0L : strategyId,
                stockCode == null ? "-" : stockCode,
                normalizedStatus == null ? "OPEN" : normalizedStatus);
    }

    private String deriveCaseSeverity(String reviewLevel, String status)
    {
        String normalizedStatus = valueOrDefault(status, "").toUpperCase();
        if ("MISSED".equals(normalizedStatus) || "INVALID".equals(normalizedStatus))
        {
            return "P0";
        }
        if ("ETF_RISK_WARNING".equals(normalizedStatus)
                || "PENDING".equals(normalizedStatus)
                || "REVIEW".equals(normalizedStatus)
                || "BUILD_POSITION".equals(normalizedStatus)
                || "governance".equals(reviewLevel))
        {
            return "P1";
        }
        return "P2";
    }

    private String stringifyTimestamp(Object value)
    {
        if (value == null)
        {
            return LocalDateTime.now().toString();
        }
        if (value instanceof LocalDateTime time)
        {
            return time.toString();
        }
        if (value instanceof LocalDate date)
        {
            return date.atStartOfDay().toString();
        }
        return String.valueOf(value);
    }

    private String valueOrDefault(Object value, String defaultValue)
    {
        if (value == null)
        {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private LocalDate parseDate(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    private LocalDate parseDateObject(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof LocalDate localDate)
        {
            return localDate;
        }
        if (value instanceof Date date)
        {
            return date.toLocalDate();
        }
        if (value instanceof LocalDateTime dateTime)
        {
            return dateTime.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    private Long toLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private int toInt(Object value)
    {
        if (value == null)
        {
            return 0;
        }
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private double toDouble(Object value)
    {
        if (value == null)
        {
            return 0D;
        }
        if (value instanceof Number number)
        {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private double round(double value)
    {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double percentageChange(double current, double base)
    {
        if (base == 0D)
        {
            return 0D;
        }
        return ((current - base) / base) * 100D;
    }

    private Map<String, Object> toCamelCaseMap(Map<String, Object> source)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet())
        {
            result.put(toCamelCase(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private String toCamelCase(String key)
    {
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char c : key.toCharArray())
        {
            if (c == '_')
            {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return builder.toString();
    }

    private record ResolvedReviewContext(
            String reviewLevel,
            Long strategyId,
            String stockCode,
            Long signalId,
            LocalDate startDate,
            LocalDate endDate,
            Long baselineStrategyId,
            Long candidateStrategyId,
            Integer months,
            String reviewTargetName,
            Long reviewTargetId,
            Long caseId)
    {
        boolean isGovernance()
        {
            return "governance".equals(reviewLevel);
        }
    }
}
