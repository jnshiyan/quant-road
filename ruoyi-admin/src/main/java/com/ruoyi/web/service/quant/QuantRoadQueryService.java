package com.ruoyi.web.service.quant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Comparator;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;

/**
 * Quant Road 看板与数据查询
 */
@Service
public class QuantRoadQueryService
{
    private static final Logger log = LoggerFactory.getLogger(QuantRoadQueryService.class);
    private static final List<String> FULL_DAILY_CORE_STEPS = Arrays.asList(
            "sync-basic",
            "sync-daily",
            "sync-valuation",
            "evaluate-market",
            "run-strategy",
            "evaluate-risk",
            "evaluate-execution-feedback");

    private final JdbcTemplate jdbcTemplate;

    public QuantRoadQueryService(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> dashboardSummary()
    {
        long strategyCount = getCount("SELECT COUNT(1) FROM strategy_config");
        long todaySignalCount = getCount("SELECT COUNT(1) FROM trade_signal WHERE signal_date = ?", Date.valueOf(LocalDate.now()));
        long positionCount = getCount("SELECT COUNT(1) FROM position");
        long riskWarningCount = getCount("SELECT COUNT(1) FROM position WHERE loss_warning = 1");

        List<Map<String, Object>> latestLogsRaw = jdbcTemplate.queryForList(
                "SELECT strategy_id, run_time, annual_return, max_drawdown, win_rate, total_profit, is_invalid, remark " +
                "FROM strategy_run_log ORDER BY run_time DESC LIMIT 20");
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (Map<String, Object> row : latestLogsRaw)
        {
            BigDecimal annualReturn = toDecimal(row.get("annual_return"));
            if (annualReturn != null)
            {
                sum = sum.add(annualReturn);
                count++;
            }
        }
        BigDecimal avgAnnualReturn = count == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategyCount", strategyCount);
        result.put("todaySignalCount", todaySignalCount);
        result.put("positionCount", positionCount);
        result.put("riskWarningCount", riskWarningCount);
        result.put("averageAnnualReturn", avgAnnualReturn);
        result.put("latestSignals", latestSignals(10));
        result.put("lossWarnings", lossWarnings(10));
        result.put("latestLogs", latestLogs(10));
        return result;
    }

    public List<Map<String, Object>> dashboardActionItems(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> readiness = jobReadiness(null);
        String readinessStatus = readiness.get("status") == null ? "" : String.valueOf(readiness.get("status"));
        if (!"READY".equals(String.valueOf(readiness.get("dataIntegrityStatus")))
                && readiness.get("dataIntegrityCategory") != null)
        {
            Map<String, Object> batchQuery = new LinkedHashMap<>();
            if (readiness.get("batchId") != null)
            {
                batchQuery.put("batchId", toLong(readiness.get("batchId")));
            }
            items.add(actionItem(
                    "DATA_INTEGRITY_REVIEW",
                    "P0",
                    "batch",
                    toLong(readiness.get("batchId")),
                    "核对盘后数据完整性",
                    valueOrDefault(readiness.get("dataIntegrityMessage"), "盘后结果存在数据完整性风险，建议先进入任务中心核对。"),
                    "/quant/operations",
                    batchQuery,
                    Math.max(1, toInt(readiness.get("warningStepCount")))));
        }
        if ("BLOCKED".equals(readinessStatus) && !"READY".equals(String.valueOf(readiness.get("dataIntegrityStatus"))))
        {
            // Data integrity gate already produced the primary blocking action above.
        }
        else if ("BLOCKED".equals(readinessStatus))
        {
            Map<String, Object> batchQuery = new LinkedHashMap<>();
            if (readiness.get("batchId") != null)
            {
                batchQuery.put("batchId", toLong(readiness.get("batchId")));
            }
            items.add(actionItem(
                    "PIPELINE_RECOVERY",
                    "P0",
                    "batch",
                    toLong(readiness.get("batchId")),
                    "恢复盘后失败批次",
                    valueOrDefault(readiness.get("message"), "盘后主流程存在阻断项，需要先恢复。"),
                    "/quant/operations",
                    batchQuery,
                    toInt(readiness.get("failedStepCount"))));
        }
        else if ("RUNNING".equals(readinessStatus))
        {
            Map<String, Object> batchQuery = new LinkedHashMap<>();
            if (readiness.get("batchId") != null)
            {
                batchQuery.put("batchId", toLong(readiness.get("batchId")));
            }
            items.add(actionItem(
                    "PIPELINE_WAIT",
                    "P1",
                    "batch",
                    toLong(readiness.get("batchId")),
                    "等待盘后批次完成",
                    valueOrDefault(readiness.get("message"), "盘后主流程仍在运行，暂时不要重复触发。"),
                    "/quant/jobs",
                    batchQuery,
                    toInt(readiness.get("completedSteps"))));
        }

        Map<String, Object> reconciliation = executionReconciliationSummary();
        long unmatchedExecutionCount = toLong(reconciliation.get("unmatchedExecutionCount")) == null
                ? 0L : toLong(reconciliation.get("unmatchedExecutionCount"));
        long partialExecutionCount = toLong(reconciliation.get("partialExecutionCount")) == null
                ? 0L : toLong(reconciliation.get("partialExecutionCount"));
        long pendingSignalCount = toLong(reconciliation.get("pendingSignalCount")) == null
                ? 0L : toLong(reconciliation.get("pendingSignalCount"));
        if (unmatchedExecutionCount > 0)
        {
            items.add(actionItem(
                    "EXECUTION_RECONCILIATION",
                    "P0",
                    "execution",
                    null,
                    "处理未匹配成交",
                    String.format("当前还有 %d 条成交未绑定信号，主链可信度不完整。", unmatchedExecutionCount),
                    "/quant/execution",
                    Map.of("focus", "unmatched"),
                    unmatchedExecutionCount));
        }
        if (partialExecutionCount > 0)
        {
            items.add(actionItem(
                    "PARTIAL_EXECUTION",
                    "P1",
                    "execution",
                    null,
                    "补齐部分成交",
                    String.format("当前还有 %d 条部分成交待补录或人工确认。", partialExecutionCount),
                    "/quant/execution",
                    Map.of("focus", "partial"),
                    partialExecutionCount));
        }
        if (pendingSignalCount > 0)
        {
            items.add(actionItem(
                    "PENDING_SIGNAL_EXECUTION",
                    "P1",
                    "execution",
                    null,
                    "核对待执行信号",
                    String.format("当前还有 %d 条信号待闭环，建议优先核对真实成交。", pendingSignalCount),
                    "/quant/execution",
                    Map.of("focus", "abnormal"),
                    pendingSignalCount));
        }

        Map<String, Object> riskSummary = positionRiskSummary();
        int stopLossWarningCount = toInt(riskSummary.get("stopLossWarningCount"));
        int differenceCount = toInt(riskSummary.get("positionDiffCount"));
        if (stopLossWarningCount > 0)
        {
            items.add(actionItem(
                    "POSITION_RISK",
                    "P1",
                    "position",
                    null,
                    "处理持仓风险预警",
                    String.format("当前有 %d 个持仓触发止损或风险预警，建议进入复盘分析。", stopLossWarningCount),
                    "/quant/review",
                    Map.of("reviewLevel", "trade", "sourcePage", "dashboard", "sourceAction", "riskWarning"),
                    stopLossWarningCount));
        }
        if (differenceCount > 0)
        {
            items.add(actionItem(
                    "POSITION_SYNC_DIFF",
                    "P1",
                    "position",
                    null,
                    "核对持仓同步差异",
                    String.format("当前有 %d 个标的的持仓与成交推导结果不一致。", differenceCount),
                    "/quant/execution",
                    Map.of("focus", "positionDiff"),
                    differenceCount));
        }

        List<Map<String, Object>> reviewCandidates = reviewCandidates(Math.min(3, safeLimit));
        for (Map<String, Object> candidate : reviewCandidates)
        {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("reviewLevel", candidate.get("reviewLevel"));
            if (candidate.get("strategyId") != null)
            {
                query.put("strategyId", candidate.get("strategyId"));
            }
            if (candidate.get("stockCode") != null)
            {
                query.put("stockCode", candidate.get("stockCode"));
            }
            if (candidate.get("signalId") != null)
            {
                query.put("signalId", candidate.get("signalId"));
            }
            if (candidate.get("baselineStrategyId") != null)
            {
                query.put("baselineStrategyId", candidate.get("baselineStrategyId"));
            }
            if (candidate.get("candidateStrategyId") != null)
            {
                query.put("candidateStrategyId", candidate.get("candidateStrategyId"));
            }
            query.put("sourcePage", "dashboard");
            query.put("sourceAction", "reviewCandidate");
            items.add(actionItem(
                    "REVIEW_CANDIDATE",
                    "P2",
                    String.valueOf(candidate.get("reviewLevel")),
                    candidate.get("signalId") != null ? candidate.get("signalId") : candidate.get("strategyId"),
                    "进入复盘分析",
                    String.format("%s：%s", valueOrDefault(candidate.get("reviewTargetName"), "复盘对象"), valueOrDefault(candidate.get("reason"), "存在待解释异常")),
                    "/quant/review",
                    query,
                    1));
        }

        return items.stream()
                .sorted((left, right) -> {
                    int scoreCompare = Integer.compare(priorityScore(String.valueOf(left.get("priority"))), priorityScore(String.valueOf(right.get("priority"))));
                    if (scoreCompare != 0)
                    {
                        return scoreCompare;
                    }
                    int typeCompare = Integer.compare(actionTypePriority(String.valueOf(left.get("actionType"))), actionTypePriority(String.valueOf(right.get("actionType"))));
                    if (typeCompare != 0)
                    {
                        return typeCompare;
                    }
                    return Integer.compare(toInt(right.get("badge")), toInt(left.get("badge")));
                })
                .limit(safeLimit)
                .toList();
    }

    public Map<String, Object> positionRiskSummary()
    {
        try
        {
            List<Map<String, Object>> positions = jdbcTemplate.queryForList(
                    "SELECT p.stock_code, p.stock_name, p.quantity, p.current_price, p.cost_price, p.loss_warning " +
                            "FROM position p ORDER BY p.loss_warning DESC, p.stock_code");
            List<Map<String, Object>> activeStrategies = jdbcTemplate.queryForList(
                    "SELECT id, strategy_name, params FROM strategy_config WHERE status = 1 ORDER BY id");
            Map<String, Object> market = marketStatus();
            String marketStatus = valueOrDefault(market.get("status"), "default");
            Set<String> etfStockCodes = loadEtfCoreStockCodes();

            BigDecimal capitalAnchor = new BigDecimal("100000");
            BigDecimal effectiveTotalBudgetPct = new BigDecimal("0.80");
            BigDecimal maxSingleBudgetPct = new BigDecimal("0.15");
            if (!activeStrategies.isEmpty())
            {
                BigDecimal maxCapital = BigDecimal.ZERO;
                BigDecimal totalBudgetSum = BigDecimal.ZERO;
                BigDecimal minSingleBudget = null;
                int budgetCount = 0;
                for (Map<String, Object> strategy : activeStrategies)
                {
                    Map<String, Object> params = parseJsonObject(strategy.get("params"));
                    BigDecimal portfolioCapital = toDecimalOrDefault(params.get("portfolio_capital"), new BigDecimal("100000"));
                    if (portfolioCapital.compareTo(maxCapital) > 0)
                    {
                        maxCapital = portfolioCapital;
                    }
                    BigDecimal totalBudgetPct = toDecimalOrDefault(params.get("max_total_position_pct"), new BigDecimal("0.80"));
                    BigDecimal regimeWeight = resolveRegimeWeight(params.get("regime_budget_weights"), marketStatus);
                    totalBudgetSum = totalBudgetSum.add(totalBudgetPct.multiply(regimeWeight));
                    budgetCount++;
                    BigDecimal singleBudgetPct = toDecimalOrDefault(params.get("max_single_position_pct"), new BigDecimal("0.15"));
                    minSingleBudget = minSingleBudget == null ? singleBudgetPct : minSingleBudget.min(singleBudgetPct);
                }
                capitalAnchor = maxCapital.compareTo(BigDecimal.ZERO) > 0 ? maxCapital : capitalAnchor;
                if (budgetCount > 0)
                {
                    effectiveTotalBudgetPct = totalBudgetSum.divide(BigDecimal.valueOf(budgetCount), 4, RoundingMode.HALF_UP);
                }
                if (minSingleBudget != null)
                {
                    maxSingleBudgetPct = minSingleBudget;
                }
            }

            BigDecimal totalHoldingValue = BigDecimal.ZERO;
            BigDecimal topHoldingValue = BigDecimal.ZERO;
            BigDecimal etfHoldingValue = BigDecimal.ZERO;
            BigDecimal equityHoldingValue = BigDecimal.ZERO;
            int stopLossWarningCount = 0;
            int overBudgetCount = 0;
            int etfHoldingCount = 0;
            int equityHoldingCount = 0;
            int etfRiskWarningCount = 0;
            int equityRiskWarningCount = 0;
            List<Map<String, Object>> topRiskPositions = new ArrayList<>();
            BigDecimal etfSingleBudgetPct = maxSingleBudgetPct.multiply(new BigDecimal("1.50"));
            BigDecimal equitySingleBudgetPct = maxSingleBudgetPct;
            for (Map<String, Object> row : positions)
            {
                BigDecimal price = toDecimal(row.get("current_price"));
                if (price == null || price.compareTo(BigDecimal.ZERO) <= 0)
                {
                    price = toDecimalOrDefault(row.get("cost_price"), BigDecimal.ZERO);
                }
                BigDecimal quantity = BigDecimal.valueOf(toInt(row.get("quantity")));
                BigDecimal marketValue = quantity.multiply(price);
                totalHoldingValue = totalHoldingValue.add(marketValue);
                if (marketValue.compareTo(topHoldingValue) > 0)
                {
                    topHoldingValue = marketValue;
                }
                String stockCode = valueOrDefault(row.get("stock_code"), "");
                String stockName = valueOrDefault(row.get("stock_name"), "");
                boolean etfAsset = isEtfAsset(stockCode, stockName, etfStockCodes);
                if (etfAsset)
                {
                    etfHoldingCount++;
                    etfHoldingValue = etfHoldingValue.add(marketValue);
                }
                else
                {
                    equityHoldingCount++;
                    equityHoldingValue = equityHoldingValue.add(marketValue);
                }
                if (toInt(row.get("loss_warning")) == 1)
                {
                    stopLossWarningCount++;
                    if (etfAsset)
                    {
                        etfRiskWarningCount++;
                    }
                    else
                    {
                        equityRiskWarningCount++;
                    }
                }
                BigDecimal singleBudgetValue = capitalAnchor.multiply(etfAsset ? etfSingleBudgetPct : equitySingleBudgetPct);
                if (marketValue.compareTo(singleBudgetValue) > 0)
                {
                    overBudgetCount++;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("stockCode", stockCode);
                item.put("stockName", stockName);
                item.put("marketValue", marketValue.setScale(2, RoundingMode.HALF_UP));
                item.put("positionPct", percentageOfCapital(marketValue, capitalAnchor));
                item.put("lossWarning", toInt(row.get("loss_warning")) == 1);
                item.put("assetType", etfAsset ? "ETF" : "EQUITY");
                topRiskPositions.add(item);
            }
            topRiskPositions.sort((left, right) -> Double.compare(
                    toDouble(right.get("positionPct")),
                    toDouble(left.get("positionPct"))));

            double totalPositionPct = percentageOfCapital(totalHoldingValue, capitalAnchor);
            double topHoldingPct = percentageOfCapital(topHoldingValue, capitalAnchor);
            double budgetCapPct = effectiveTotalBudgetPct.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).doubleValue();
            double singleBudgetCapPct = maxSingleBudgetPct.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).doubleValue();
            double etfSingleBudgetCapPct = etfSingleBudgetPct.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).doubleValue();
            double equitySingleBudgetCapPct = equitySingleBudgetPct.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).doubleValue();
            int positionDiffCount = 0;
            try
            {
                positionDiffCount = toInt(positionSyncResult(null, null).get("differenceCount"));
            }
            catch (Exception ex)
            {
                log.warn("Query position sync result inside risk summary failed, fallback zero diff: {}", ex.getMessage());
            }

            String riskLevel = "LOW";
            if (overBudgetCount > 0
                    || totalPositionPct > budgetCapPct
                    || topHoldingPct > singleBudgetCapPct * 1.2
                    || stopLossWarningCount > 0
                    || positionDiffCount > 0)
            {
                riskLevel = "HIGH";
            }
            else if (topHoldingPct > singleBudgetCapPct || totalPositionPct > budgetCapPct * 0.85)
            {
                riskLevel = "MEDIUM";
            }

            List<String> riskNotes = new ArrayList<>();
            if (overBudgetCount > 0)
            {
                riskNotes.add(String.format("当前有 %d 个持仓超过单仓预算上限 %.2f%%。", overBudgetCount, singleBudgetCapPct));
            }
            if (totalPositionPct > budgetCapPct)
            {
                riskNotes.add(String.format("总仓位 %.2f%% 已超过当前市场环境下的预算上限 %.2f%%。", totalPositionPct, budgetCapPct));
            }
            if (stopLossWarningCount > 0)
            {
                riskNotes.add(String.format("当前有 %d 个持仓触发止损预警。", stopLossWarningCount));
            }
            if (etfHoldingCount > 0)
            {
                riskNotes.add(String.format("ETF 持仓 %d 个，单仓预算上限按 %.2f%% 口径单独评估。", etfHoldingCount, etfSingleBudgetCapPct));
            }
            if (equityHoldingCount > 0)
            {
                riskNotes.add(String.format("个股持仓 %d 个，单仓预算上限按 %.2f%% 口径评估。", equityHoldingCount, equitySingleBudgetCapPct));
            }
            if (positionDiffCount > 0)
            {
                riskNotes.add(String.format("当前有 %d 个持仓存在同步差异，需要回到执行闭环核对。", positionDiffCount));
            }
            if (riskNotes.isEmpty())
            {
                riskNotes.add("当前仓位未出现明显预算越界，优先继续跟踪信号与市场状态。");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("currentMarketStatus", marketStatus);
            result.put("holdingCount", positions.size());
            result.put("etfHoldingCount", etfHoldingCount);
            result.put("equityHoldingCount", equityHoldingCount);
            result.put("totalPositionPct", roundDouble(totalPositionPct));
            result.put("topHoldingPct", roundDouble(topHoldingPct));
            result.put("budgetCapPct", roundDouble(budgetCapPct));
            result.put("singleBudgetCapPct", roundDouble(singleBudgetCapPct));
            result.put("etfSingleBudgetCapPct", roundDouble(etfSingleBudgetCapPct));
            result.put("equitySingleBudgetCapPct", roundDouble(equitySingleBudgetCapPct));
            result.put("etfPositionPct", roundDouble(percentageOfCapital(etfHoldingValue, capitalAnchor)));
            result.put("equityPositionPct", roundDouble(percentageOfCapital(equityHoldingValue, capitalAnchor)));
            result.put("budgetUtilizationPct", budgetCapPct <= 0D ? 0D : roundDouble((totalPositionPct / budgetCapPct) * 100D));
            result.put("concentrationRisk", topHoldingPct > singleBudgetCapPct ? "HIGH" : (topHoldingPct > singleBudgetCapPct * 0.8 ? "MEDIUM" : "LOW"));
            result.put("riskLevel", riskLevel);
            result.put("stopLossWarningCount", stopLossWarningCount);
            result.put("etfRiskWarningCount", etfRiskWarningCount);
            result.put("equityRiskWarningCount", equityRiskWarningCount);
            result.put("overBudgetCount", overBudgetCount);
            result.put("positionDiffCount", positionDiffCount);
            result.put("riskNotes", riskNotes);
            result.put("topRiskPositions", topRiskPositions.stream().limit(5).toList());
            return result;
        }
        catch (DataAccessException ex)
        {
            log.warn("Build position risk summary failed, fallback empty map: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public List<Map<String, Object>> dashboardDeepLinks()
    {
        List<Map<String, Object>> links = new ArrayList<>();
        Map<String, Object> reconciliation = executionReconciliationSummary();
        long executionIssueCount = safeLong(reconciliation.get("pendingSignalCount"))
                + safeLong(reconciliation.get("partialExecutionCount"))
                + safeLong(reconciliation.get("unmatchedExecutionCount"));
        if (executionIssueCount > 0)
        {
            links.add(deepLink(
                    "进入执行回写",
                    "/quant/execution",
                    Map.of("focus", safeLong(reconciliation.get("unmatchedExecutionCount")) > 0 ? "unmatched" : "abnormal"),
                    executionIssueCount,
                    "warning",
                    "先把未匹配成交、部分成交和待执行信号处理完整。"));
        }

        Map<String, Object> riskSummary = positionRiskSummary();
        int reviewCandidateCount = reviewCandidates(6).size();
        if (reviewCandidateCount > 0 || toInt(riskSummary.get("stopLossWarningCount")) > 0)
        {
            links.add(deepLink(
                    "进入复盘分析",
                    "/quant/review",
                    Map.of("reviewLevel", "trade", "sourcePage", "dashboard", "sourceAction", "reviewCandidate"),
                    Math.max(reviewCandidateCount, toInt(riskSummary.get("stopLossWarningCount"))),
                    "danger",
                    "把异常执行、风险预警和策略失效样本串成正式复盘结论。"));
        }

        Map<String, Object> canaryLatest = canaryLatest();
        String recommendation = valueOrDefault(canaryLatest.get("recommendation"), "");
        if (!recommendation.isBlank())
        {
            Map<String, Object> shadowQuery = new LinkedHashMap<>();
            if (canaryLatest.get("baseline_strategy_id") != null)
            {
                shadowQuery.put("baselineStrategyId", canaryLatest.get("baseline_strategy_id"));
            }
            if (canaryLatest.get("candidate_strategy_id") != null)
            {
                shadowQuery.put("candidateStrategyId", canaryLatest.get("candidate_strategy_id"));
            }
            links.add(deepLink(
                    "进入影子对比",
                    "/quant/shadow",
                    shadowQuery,
                    1,
                    "info",
                    valueOrDefault(canaryLatest.get("remark"), "查看候选策略与基线策略的治理证据。")));
        }

        Map<String, Object> readiness = jobReadiness(null);
        String readinessStatus = valueOrDefault(readiness.get("status"), "");
        if ("BLOCKED".equals(readinessStatus) || "RUNNING".equals(readinessStatus) || "READY_WITH_WARNINGS".equals(readinessStatus))
        {
            links.add(deepLink(
                    "查看任务中心",
                    "RUNNING".equals(readinessStatus) ? "/quant/jobs" : "/quant/operations",
                    readiness.get("batchId") == null ? Map.of() : Map.of("batchId", readiness.get("batchId")),
                    toInt(readiness.get("warningStepCount")) + toInt(readiness.get("failedStepCount")),
                    "primary",
                    valueOrDefault(readiness.get("message"), "查看盘后任务状态。")));
        }

        links.add(deepLink(
                "查看标的跟踪",
                "/quant/symbols",
                Map.of(),
                toInt(riskSummary.get("holdingCount")),
                "success",
                "从标的维度查看当前池内对象和持仓状态。"));
        links.add(deepLink(
                "查看回测分析",
                "/quant/backtest",
                Map.of(),
                0,
                "success",
                "回到研究与回测页面核对策略效果。"));

        return links;
    }

    public Map<String, Object> etfOverview()
    {
        try
        {
            int safeLimit = 6;
            long etfUniverseCount = getCount(
                    "SELECT COUNT(DISTINCT stock_code) FROM quant_symbol_pool_member WHERE pool_code = 'ETF_CORE' AND inclusion_status = 'INCLUDED'");
            long indexMappingCount = getCount(
                    "SELECT COUNT(1) FROM quant_index_etf_mapping WHERE status = 1");
            List<Map<String, Object>> mappingRows = jdbcTemplate.queryForList(
                    "SELECT index_code, index_name, primary_etf_code, primary_etf_name, candidate_etf_codes, candidate_etf_names " +
                            "FROM quant_index_etf_mapping WHERE status = 1 ORDER BY index_code ASC LIMIT ?",
                    safeLimit);
            List<Map<String, Object>> etfPositions = jdbcTemplate.queryForList(
                    "SELECT p.stock_code, p.stock_name, p.quantity, p.current_price, p.float_profit, p.loss_warning " +
                            "FROM position p " +
                            "WHERE EXISTS ( " +
                            "  SELECT 1 FROM quant_symbol_pool_member m " +
                            "  WHERE m.pool_code = 'ETF_CORE' AND m.stock_code = p.stock_code AND m.inclusion_status = 'INCLUDED' " +
                            ") " +
                            "ORDER BY p.stock_code LIMIT ?",
                    safeLimit);
            List<Map<String, Object>> activeSignals = jdbcTemplate.queryForList(
                    "SELECT ts.id, ts.stock_code, ts.stock_name, ts.signal_type, ts.suggest_price, ts.signal_date, ts.strategy_id " +
                            "FROM trade_signal ts " +
                            "WHERE ts.signal_date = ? AND EXISTS ( " +
                            "  SELECT 1 FROM quant_symbol_pool_member m " +
                            "  WHERE m.pool_code = 'ETF_CORE' AND m.stock_code = ts.stock_code AND m.inclusion_status = 'INCLUDED' " +
                            ") " +
                            "ORDER BY ts.id DESC LIMIT ?",
                    Date.valueOf(LocalDate.now()),
                    safeLimit);

            Map<String, Map<String, Object>> positionsByCode = indexByStockCode(etfPositions);
            Map<String, List<Map<String, Object>>> signalsByCode = groupRowsByStockCode(activeSignals);
            int riskWarningCount = 0;
            for (Map<String, Object> position : etfPositions)
            {
                if (toInt(position.get("loss_warning")) == 1)
                {
                    riskWarningCount++;
                }
            }

            int candidateEtfCount = 0;
            List<Map<String, Object>> mappingHighlights = new ArrayList<>();
            for (Map<String, Object> row : mappingRows)
            {
                List<String> candidateCodes = parseJsonStringArray(row.get("candidate_etf_codes"));
                List<String> candidateNames = parseJsonStringArray(row.get("candidate_etf_names"));
                candidateEtfCount += candidateCodes.size();

                String primaryEtfCode = valueOrDefault(row.get("primary_etf_code"), "");
                Map<String, Object> primaryPosition = positionsByCode.get(primaryEtfCode);
                List<Map<String, Object>> primarySignals = signalsByCode.getOrDefault(primaryEtfCode, List.of());

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("indexCode", row.get("index_code"));
                item.put("indexName", row.get("index_name"));
                item.put("primaryEtfCode", row.get("primary_etf_code"));
                item.put("primaryEtfName", row.get("primary_etf_name"));
                item.put("candidateEtfCodes", candidateCodes);
                item.put("candidateEtfNames", candidateNames);
                item.put("holdingQuantity", primaryPosition == null ? 0 : toInt(primaryPosition.get("quantity")));
                item.put("floatProfit", primaryPosition == null ? null : primaryPosition.get("float_profit"));
                item.put("hasTodaySignal", !primarySignals.isEmpty());
                item.put("todaySignalTypes", primarySignals.stream()
                        .map(signal -> valueOrDefault(signal.get("signal_type"), ""))
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .toList());
                mappingHighlights.add(item);
            }

            List<String> narrative = new ArrayList<>();
            if (indexMappingCount > 0)
            {
                narrative.add(String.format("当前已维护 %d 条指数到主ETF映射，ETF 不再只是范围成员，而是指数分析之后的真实交易承接层。", indexMappingCount));
            }
            if (!activeSignals.isEmpty())
            {
                narrative.add(String.format("今日有 %d 条 ETF 信号进入主链，可直接进入回测或执行闭环继续核对。", activeSignals.size()));
            }
            if (!etfPositions.isEmpty())
            {
                narrative.add(String.format("当前有 %d 个 ETF 持仓，其中 %d 个触发风险预警。", etfPositions.size(), riskWarningCount));
            }
            if (narrative.isEmpty())
            {
                narrative.add("当前 ETF 线暂无活跃信号或持仓，适合先从范围页维护映射，再到回测页验证策略适用性。");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("etfUniverseCount", etfUniverseCount);
            result.put("indexMappingCount", indexMappingCount);
            result.put("candidateEtfCount", candidateEtfCount);
            result.put("etfPositionCount", etfPositions.size());
            result.put("todayEtfSignalCount", activeSignals.size());
            result.put("etfRiskWarningCount", riskWarningCount);
            result.put("recommendedScopeType", "etf_pool");
            result.put("recommendedScopePoolCode", "ETF_CORE");
            result.put("mappedScopeType", "index_mapped_etf_pool");
            result.put("mappedScopePoolCode", "INDEX_ETF_DEFAULT");
            result.put("mappingHighlights", mappingHighlights);
            result.put("activeSignals", decorateSignals(activeSignals));
            result.put("positions", etfPositions.stream().map(this::toCamelCaseMap).toList());
            result.put("narrative", narrative);
            return result;
        }
        catch (DataAccessException ex)
        {
            log.warn("Build ETF overview failed, fallback empty summary: {}", ex.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("etfUniverseCount", 0L);
            fallback.put("indexMappingCount", 0L);
            fallback.put("candidateEtfCount", 0);
            fallback.put("etfPositionCount", 0);
            fallback.put("todayEtfSignalCount", 0);
            fallback.put("etfRiskWarningCount", 0);
            fallback.put("recommendedScopeType", "etf_pool");
            fallback.put("recommendedScopePoolCode", "ETF_CORE");
            fallback.put("mappedScopeType", "index_mapped_etf_pool");
            fallback.put("mappedScopePoolCode", "INDEX_ETF_DEFAULT");
            fallback.put("mappingHighlights", List.of());
            fallback.put("activeSignals", List.of());
            fallback.put("positions", List.of());
            fallback.put("narrative", List.of("ETF 专题数据暂不可用，请先检查股票池与映射表是否完成初始化。"));
            return fallback;
        }
    }

    public Map<String, Object> etfGovernanceSummary()
    {
        try
        {
            long indexMappingCount = getCount(
                    "SELECT COUNT(1) FROM quant_index_etf_mapping WHERE status = 1");
            List<Map<String, Object>> mappingRows = jdbcTemplate.queryForList(
                    "SELECT index_code, index_name, primary_etf_code, primary_etf_name, candidate_etf_codes, candidate_etf_names " +
                            "FROM quant_index_etf_mapping WHERE status = 1 ORDER BY index_code ASC");
            List<Map<String, Object>> etfPositions = jdbcTemplate.queryForList(
                    "SELECT p.stock_code, p.stock_name, p.quantity, p.current_price, p.float_profit, p.loss_warning " +
                            "FROM position p " +
                            "WHERE EXISTS ( " +
                            "  SELECT 1 FROM quant_symbol_pool_member m " +
                            "  WHERE m.pool_code = 'ETF_CORE' AND m.stock_code = p.stock_code AND m.inclusion_status = 'INCLUDED' " +
                            ") " +
                            "ORDER BY p.stock_code");
            List<Map<String, Object>> activeSignals = jdbcTemplate.queryForList(
                    "SELECT ts.id, ts.stock_code, ts.stock_name, ts.signal_type, ts.suggest_price, ts.signal_date, ts.strategy_id " +
                            "FROM trade_signal ts " +
                            "WHERE ts.signal_date = ? AND EXISTS ( " +
                            "  SELECT 1 FROM quant_symbol_pool_member m " +
                            "  WHERE m.pool_code = 'ETF_CORE' AND m.stock_code = ts.stock_code AND m.inclusion_status = 'INCLUDED' " +
                            ") " +
                            "ORDER BY ts.id DESC",
                    Date.valueOf(LocalDate.now()));
            List<Map<String, Object>> pendingFeedbackRows = jdbcTemplate.queryForList(
                    "SELECT ts.stock_code, COUNT(1) AS pending_count " +
                            "FROM signal_execution_feedback f " +
                            "JOIN trade_signal ts ON ts.id = f.signal_id " +
                            "WHERE f.status IN ('PENDING', 'MISSED') " +
                            "AND EXISTS ( " +
                            "  SELECT 1 FROM quant_symbol_pool_member m " +
                            "  WHERE m.pool_code = 'ETF_CORE' AND m.stock_code = ts.stock_code AND m.inclusion_status = 'INCLUDED' " +
                            ") " +
                            "GROUP BY ts.stock_code");

            Map<String, Map<String, Object>> positionsByCode = indexByStockCode(etfPositions);
            Map<String, List<Map<String, Object>>> signalsByCode = groupRowsByStockCode(activeSignals);
            Map<String, Long> pendingCountsByCode = new HashMap<>();
            for (Map<String, Object> row : pendingFeedbackRows)
            {
                String stockCode = valueOrDefault(row.get("stock_code"), "");
                if (!stockCode.isBlank())
                {
                    pendingCountsByCode.put(stockCode, toLong(row.get("pending_count")));
                }
            }

            int riskWarningCount = 0;
            int activeSignalCount = 0;
            int candidateEtfCount = 0;
            List<Map<String, Object>> mappingGovernanceRows = new ArrayList<>();
            for (Map<String, Object> row : mappingRows)
            {
                List<String> candidateCodes = parseJsonStringArray(row.get("candidate_etf_codes"));
                List<String> candidateNames = parseJsonStringArray(row.get("candidate_etf_names"));
                candidateEtfCount += candidateCodes.size();

                String primaryEtfCode = valueOrDefault(row.get("primary_etf_code"), "");
                Map<String, Object> primaryPosition = positionsByCode.get(primaryEtfCode);
                List<Map<String, Object>> primarySignals = signalsByCode.getOrDefault(primaryEtfCode, List.of());
                long pendingExecutionCount = pendingCountsByCode.getOrDefault(primaryEtfCode, 0L);
                boolean riskWarning = primaryPosition != null && toInt(primaryPosition.get("loss_warning")) == 1;
                boolean hasSignal = !primarySignals.isEmpty();
                int holdingQuantity = primaryPosition == null ? 0 : toInt(primaryPosition.get("quantity"));
                if (riskWarning)
                {
                    riskWarningCount++;
                }
                if (hasSignal)
                {
                    activeSignalCount++;
                }

                String governanceAction = deriveEtfGovernanceAction(riskWarning, pendingExecutionCount, hasSignal, holdingQuantity, candidateCodes);
                String governanceReason = deriveEtfGovernanceReason(riskWarning, pendingExecutionCount, hasSignal, holdingQuantity, candidateCodes);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("indexCode", row.get("index_code"));
                item.put("indexName", row.get("index_name"));
                item.put("primaryEtfCode", row.get("primary_etf_code"));
                item.put("primaryEtfName", row.get("primary_etf_name"));
                item.put("candidateEtfCodes", candidateCodes);
                item.put("candidateEtfNames", candidateNames);
                item.put("holdingQuantity", holdingQuantity);
                item.put("floatProfit", primaryPosition == null ? null : primaryPosition.get("float_profit"));
                item.put("riskWarning", riskWarning);
                item.put("hasTodaySignal", hasSignal);
                item.put("todaySignalTypes", primarySignals.stream()
                        .map(signal -> valueOrDefault(signal.get("signal_type"), ""))
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .toList());
                item.put("pendingExecutionCount", pendingExecutionCount);
                item.put("governanceAction", governanceAction);
                item.put("governanceReason", governanceReason);
                item.put("reviewQuery", buildEtfReviewQuery(primaryEtfCode));
                item.put("backtestQuery", buildEtfBacktestQuery(primaryEtfCode));
                item.put("symbolsQuery", Map.of(
                        "scopeType", "index_mapped_etf_pool",
                        "scopePoolCode", "INDEX_ETF_DEFAULT"));
                mappingGovernanceRows.add(item);
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("indexMappingCount", indexMappingCount);
            summary.put("holdingCount", etfPositions.size());
            summary.put("activeSignalCount", activeSignalCount);
            summary.put("riskWarningCount", riskWarningCount);
            summary.put("candidateEtfCount", candidateEtfCount);
            summary.put("pendingExecutionCount", pendingCountsByCode.values().stream().mapToLong(Long::longValue).sum());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("summary", summary);
            result.put("governancePrinciples", List.of(
                    "ETF 治理先看指数映射是否稳定，再看主ETF是否仍承担默认执行角色。",
                    "ETF 可放宽单仓预算，但必须单独核对持仓风险、待执行信号和映射备选空间。",
                    "个股治理更关注主动选股失效；ETF 治理更关注承接一致性和执行纪律。"));
            result.put("governanceMatrix", List.of(
                    Map.of(
                            "assetType", "ETF",
                            "focus", "指数映射、主ETF承接、单仓预算、执行闭环",
                            "defaultAction", "优先复盘主ETF，再决定是否观察备选ETF"),
                    Map.of(
                            "assetType", "EQUITY",
                            "focus", "规则池质量、主动风险、个股失效、仓位集中度",
                            "defaultAction", "优先复盘策略失效和个股异常，再决定是否调池")));
            result.put("mappingGovernanceRows", mappingGovernanceRows);
            return result;
        }
        catch (DataAccessException ex)
        {
            log.warn("Build ETF governance summary failed, fallback empty summary: {}", ex.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("summary", Map.of(
                    "indexMappingCount", 0,
                    "holdingCount", 0,
                    "activeSignalCount", 0,
                    "riskWarningCount", 0,
                    "candidateEtfCount", 0,
                    "pendingExecutionCount", 0));
            fallback.put("governancePrinciples", List.of(
                    "ETF 治理摘要暂不可用，请先检查映射表、ETF 池与持仓数据是否完成初始化。"));
            fallback.put("governanceMatrix", List.of());
            fallback.put("mappingGovernanceRows", List.of());
            return fallback;
        }
    }

    public List<Map<String, Object>> reviewCandidates(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<Map<String, Object>> candidates = new ArrayList<>();
        try
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
            Set<String> etfStockCodes = loadEtfCoreStockCodes();
            if (!etfStockCodes.isEmpty())
            {
                candidates.addAll(jdbcTemplate.queryForList(
                        "SELECT p.stock_code, p.stock_name, p.float_profit, p.update_time " +
                                "FROM position p " +
                                "WHERE p.loss_warning = 1 AND EXISTS ( " +
                                "  SELECT 1 FROM quant_symbol_pool_member m " +
                                "  WHERE m.pool_code = 'ETF_CORE' AND m.stock_code = p.stock_code AND m.inclusion_status = 'INCLUDED' " +
                                ") " +
                                "ORDER BY p.update_time DESC, p.stock_code ASC LIMIT ?",
                        safeLimit).stream().map(row -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("reviewLevel", "trade");
                            item.put("stockCode", row.get("stock_code"));
                            item.put("reviewTargetName", row.get("stock_name"));
                            item.put("reason", String.format(
                                    "ETF 持仓浮盈亏 %s%%，已触发独立风险复核。",
                                    row.get("float_profit") == null ? "-" : String.valueOf(row.get("float_profit"))));
                            item.put("status", "ETF_RISK_WARNING");
                            item.put("date", row.get("update_time"));
                            item.put("scopeType", "etf_pool");
                            item.put("scopePoolCode", "ETF_CORE");
                            item.put("sourceAction", "etfRisk");
                            item.put("assetType", "ETF");
                            return item;
                        }).toList());
            }
            Map<String, Object> etfGovernance = etfGovernanceSummary();
            List<Map<String, Object>> governanceRows = mapListValue(etfGovernance.get("mappingGovernanceRows"));
            Set<String> existingEtfCodes = candidates.stream()
                    .filter(item -> "ETF".equals(String.valueOf(item.get("assetType"))))
                    .map(item -> valueOrDefault(item.get("stockCode"), ""))
                    .filter(value -> !value.isBlank())
                    .collect(java.util.stream.Collectors.toSet());
            if (governanceRows != null && !governanceRows.isEmpty())
            {
                candidates.addAll(governanceRows.stream()
                        .filter(row -> {
                            String governanceAction = valueOrDefault(row.get("governanceAction"), "");
                            return "REVIEW".equals(governanceAction)
                                    || "BUILD_POSITION".equals(governanceAction)
                                    || "OBSERVE_MAPPING".equals(governanceAction);
                        })
                        .filter(row -> {
                            String stockCode = valueOrDefault(row.get("primaryEtfCode"), "");
                            return !stockCode.isBlank() && !existingEtfCodes.contains(stockCode);
                        })
                        .map(row -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("reviewLevel", "trade");
                            item.put("stockCode", row.get("primaryEtfCode"));
                            item.put("reviewTargetName", valueOrDefault(row.get("primaryEtfName"), valueOrDefault(row.get("indexName"), "ETF治理对象")));
                            item.put("reason", row.get("governanceReason"));
                            item.put("status", row.get("governanceAction"));
                            item.put("date", LocalDate.now().toString());
                            item.put("scopeType", "etf_pool");
                            item.put("scopePoolCode", "ETF_CORE");
                            item.put("sourceAction", "etfGovernance");
                            item.put("assetType", "ETF");
                            return item;
                        })
                        .toList());
            }
        }
        catch (DataAccessException ex)
        {
            log.warn("Query dashboard review candidates failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
        return candidates.stream()
                .sorted((left, right) -> String.valueOf(right.get("date")).compareTo(String.valueOf(left.get("date"))))
                .limit(safeLimit)
                .toList();
    }

    public Map<String, Object> signalExplain(Long signalId)
    {
        if (signalId == null)
        {
            return new LinkedHashMap<>();
        }
        try
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT ts.id, ts.stock_code, ts.stock_name, ts.signal_type, ts.suggest_price, ts.signal_date, " +
                            "ts.strategy_id, ts.is_execute, sc.strategy_name, " +
                            "f.status AS feedback_status, f.remark AS feedback_remark, f.executed_quantity, f.check_date " +
                            "FROM trade_signal ts " +
                            "LEFT JOIN strategy_config sc ON sc.id = ts.strategy_id " +
                            "LEFT JOIN signal_execution_feedback f ON f.signal_id = ts.id " +
                            "WHERE ts.id = ? LIMIT 1",
                    signalId);
            if (rows.isEmpty())
            {
                return new LinkedHashMap<>();
            }
            Map<String, Object> signal = rows.get(0);
            Map<String, Object> market = marketStatus();
            List<Map<String, Object>> positions = jdbcTemplate.queryForList(
                    "SELECT stock_code, stock_name, quantity, cost_price, current_price, float_profit, loss_warning, update_time " +
                            "FROM position WHERE stock_code = ?",
                    signal.get("stock_code"));
            Map<String, Object> position = positions.isEmpty() ? Map.of() : positions.get(0);
            String executionStatus = deriveSignalExecutionStatus(signal);

            List<String> summaryLines = new ArrayList<>();
            summaryLines.add(String.format(
                    "%s 在 %s 由策略 %s 产出 %s 信号，建议价 %s。",
                    valueOrDefault(signal.get("stock_name"), valueOrDefault(signal.get("stock_code"), "未知标的")),
                    valueOrDefault(signal.get("signal_date"), "-"),
                    valueOrDefault(signal.get("strategy_name"), String.valueOf(signal.get("strategy_id"))),
                    valueOrDefault(signal.get("signal_type"), "-"),
                    signal.get("suggest_price") == null ? "-" : String.valueOf(signal.get("suggest_price"))));
            if (!market.isEmpty())
            {
                summaryLines.add(String.format(
                        "当前市场状态为 %s，说明：%s。",
                        valueOrDefault(market.get("status"), "unknown"),
                        valueOrDefault(market.get("remark"), "暂无补充说明")));
            }
            summaryLines.add(deriveSignalExecutionLine(signal, executionStatus));
            if (!position.isEmpty())
            {
                summaryLines.add(String.format(
                        "当前持仓快照：数量 %d，现价 %s，浮盈亏 %s%%，止损预警 %s。",
                        toInt(position.get("quantity")),
                        position.get("current_price"),
                        position.get("float_profit"),
                        toInt(position.get("loss_warning")) == 1 ? "是" : "否"));
            }

            List<Map<String, Object>> actions = new ArrayList<>();
            actions.add(actionLink("去执行回写", "/quant/execution", Map.of(
                    "signalId", signalId,
                    "stockCode", signal.get("stock_code"),
                    "strategyId", signal.get("strategy_id"))));
            actions.add(actionLink("去复盘分析", "/quant/review", Map.of(
                    "reviewLevel", "trade",
                    "signalId", signalId,
                    "stockCode", signal.get("stock_code"),
                    "strategyId", signal.get("strategy_id"),
                    "sourcePage", "dashboard",
                    "sourceAction", "signal")));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("signalId", toLong(signal.get("id")));
            result.put("stockCode", signal.get("stock_code"));
            result.put("stockName", signal.get("stock_name"));
            result.put("strategyId", toLong(signal.get("strategy_id")));
            result.put("strategyName", signal.get("strategy_name"));
            result.put("signalType", signal.get("signal_type"));
            result.put("signalDate", signal.get("signal_date"));
            result.put("suggestPrice", signal.get("suggest_price"));
            result.put("executionStatus", executionStatus);
            result.put("headline", deriveSignalHeadline(signal.get("signal_type"), executionStatus));
            result.put("marketStatus", market.get("status"));
            result.put("marketRemark", market.get("remark"));
            result.put("feedbackRemark", signal.get("feedback_remark"));
            result.put("holdingSnapshot", position.isEmpty() ? null : toCamelCaseMap(position));
            result.put("summaryLines", summaryLines);
            result.put("actions", actions);
            return result;
        }
        catch (DataAccessException ex)
        {
            log.warn("Build signal explain failed, fallback empty map: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public List<Map<String, Object>> signals(LocalDate signalDate)
    {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, stock_code, stock_name, signal_type, suggest_price, signal_date, strategy_id, is_execute, create_time " +
                "FROM trade_signal WHERE signal_date = ? ORDER BY signal_type, stock_code",
                Date.valueOf(signalDate));
        return decorateSignals(rows);
    }

    public Map<String, Object> signals(LocalDate signalDate, int pageNum, int pageSize)
    {
        List<Map<String, Object>> rows = signals(signalDate);
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int pageCount = rows.isEmpty() ? 0 : (int) Math.ceil((double) rows.size() / safePageSize);
        int currentPageNum = pageCount == 0 ? 1 : Math.min(safePageNum, pageCount);
        int fromIndex = Math.min((currentPageNum - 1) * safePageSize, rows.size());
        int toIndex = Math.min(fromIndex + safePageSize, rows.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pageNum", currentPageNum);
        result.put("pageSize", safePageSize);
        result.put("total", rows.size());
        result.put("pageCount", pageCount);
        result.put("rows", rows.subList(fromIndex, toIndex));
        return result;
    }

    public List<Map<String, Object>> positions()
    {
        return jdbcTemplate.queryForList(
                "SELECT stock_code, stock_name, quantity, cost_price, current_price, float_profit, loss_warning, update_time " +
                "FROM position ORDER BY stock_code");
    }

    public List<Map<String, Object>> strategyLogs(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.queryForList(
                "SELECT strategy_id, run_time, annual_return, max_drawdown, win_rate, total_profit, is_invalid, remark " +
                "FROM strategy_run_log ORDER BY run_time DESC LIMIT ?",
                safeLimit);
    }

    public List<Map<String, Object>> strategies()
    {
        return jdbcTemplate.queryForList(
                "SELECT id, strategy_name, strategy_type, status, cron_expr, create_time " +
                "FROM strategy_config ORDER BY id");
    }

    public Map<String, Object> marketStatus()
    {
        try
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT trade_date, status, raw_status, hs300_close, hs300_ma20, hs300_ma60, " +
                    "hs300_above_ma20, hs300_above_ma60, up_ratio, remark, update_time " +
                    "FROM market_status ORDER BY trade_date DESC, id DESC LIMIT 1");
            if (rows.isEmpty())
            {
                return new LinkedHashMap<>();
            }
            return rows.get(0);
        }
        catch (DataAccessException ex)
        {
            log.warn("Query market_status failed, fallback empty result: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public List<Map<String, Object>> indexValuations(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try
        {
            return jdbcTemplate.queryForList(
                    "SELECT * FROM ( " +
                    "  SELECT DISTINCT ON (index_code) index_code, index_name, pe, pb, pe_percentile, pb_percentile, source, update_date, update_time " +
                    "  FROM index_valuation " +
                    "  ORDER BY index_code, update_date DESC, id DESC " +
                    ") latest ORDER BY pe_percentile ASC NULLS LAST, index_code LIMIT ?",
                    safeLimit);
        }
        catch (DataAccessException ex)
        {
            log.warn("Query index_valuation failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> strategySwitchAudits(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try
        {
            return jdbcTemplate.queryForList(
                    "SELECT strategy_id, strategy_type, market_status, decision, reason, actor, trigger_source, create_time " +
                    "FROM strategy_switch_audit ORDER BY create_time DESC, id DESC LIMIT ?",
                    safeLimit);
        }
        catch (DataAccessException ex)
        {
            log.warn("Query strategy_switch_audit failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> executionFeedbackSummary()
    {
        try
        {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("executed", getCount("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'EXECUTED'"));
            result.put("missed", getCount("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'MISSED'"));
            result.put("pending", getCount("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'PENDING'"));
            result.put("latestCheckDate", jdbcTemplate.queryForObject(
                    "SELECT MAX(check_date) FROM signal_execution_feedback",
                    java.time.LocalDate.class));
            return result;
        }
        catch (DataAccessException ex)
        {
            log.warn("Query signal_execution_feedback summary failed, fallback empty map: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public List<Map<String, Object>> executionFeedbackDetails(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT " +
                    "  f.signal_id, f.signal_date, f.due_date, f.check_date, f.status, f.executed_quantity, f.last_trade_date, " +
                    "  f.overdue_days, f.remark, ts.stock_code, ts.stock_name, ts.signal_type, ts.strategy_id, COALESCE(ts.is_execute, 0) AS signal_executed " +
                    "FROM signal_execution_feedback f " +
                    "LEFT JOIN trade_signal ts ON ts.id = f.signal_id " +
                    "ORDER BY f.check_date DESC, f.signal_id DESC LIMIT ?",
                    safeLimit);
            return decorateExecutionFeedbackDetails(rows);
        }
        catch (DataAccessException ex)
        {
            log.warn("Query signal_execution_feedback details failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> canaryLatest()
    {
        try
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT " +
                    "  run_date, baseline_strategy_id, candidate_strategy_id, months, comparable_months, " +
                    "  candidate_better_annual_months, candidate_lower_drawdown_months, candidate_higher_win_rate_months, " +
                    "  candidate_lower_invalid_rate_months, market_status, recommendation, remark, create_time " +
                    "FROM canary_run_log ORDER BY run_date DESC, id DESC LIMIT 1");
            if (rows.isEmpty())
            {
                return new LinkedHashMap<>();
            }
            return rows.get(0);
        }
        catch (DataAccessException ex)
        {
            log.warn("Query canary_run_log failed, fallback empty map: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public List<Map<String, Object>> executionRecords(int limit, String stockCode)
    {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        try
        {
            StringBuilder sql = new StringBuilder(
                    "SELECT " +
                    "  e.id, e.stock_code, e.side, e.quantity, e.price, e.trade_date, e.strategy_id, e.signal_id, " +
                    "  e.commission, e.tax, e.slippage, e.gross_amount, e.net_amount, e.external_order_id, e.create_time, " +
                    "  sc.strategy_name, ts.signal_type, COALESCE(ts.is_execute, 0) AS signal_executed, " +
                    "  f.status AS feedback_status, f.executed_quantity AS feedback_executed_quantity " +
                    "FROM execution_record e " +
                    "LEFT JOIN strategy_config sc ON sc.id = e.strategy_id " +
                    "LEFT JOIN trade_signal ts ON ts.id = e.signal_id " +
                    "LEFT JOIN signal_execution_feedback f ON f.signal_id = e.signal_id ");
            List<Object> args = new ArrayList<>();
            if (stockCode != null && !stockCode.isBlank())
            {
                sql.append("WHERE e.stock_code = ? ");
                args.add(stockCode.trim());
            }
            sql.append("ORDER BY e.trade_date DESC, e.id DESC LIMIT ?");
            args.add(safeLimit);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
            return decorateExecutionRecords(rows);
        }
        catch (DataAccessException ex)
        {
            log.warn("Query execution_record failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> executionReconciliationSummary()
    {
        try
        {
            long pendingSignalCount = getCount("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'PENDING'");
            long executedSignalCount = getCount("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'EXECUTED'");
            long missedSignalCount = getCount("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'MISSED'");
            long partialExecutionCount = getCount(
                    "SELECT COUNT(DISTINCT ts.id) " +
                    "FROM trade_signal ts " +
                    "JOIN execution_record er ON er.signal_id = ts.id " +
                    "LEFT JOIN signal_execution_feedback f ON f.signal_id = ts.id " +
                    "WHERE COALESCE(ts.is_execute, 0) = 0 OR COALESCE(f.status, 'PENDING') = 'PENDING'");
            long unmatchedExecutionCount = getCount("SELECT COUNT(1) FROM execution_record WHERE signal_id IS NULL");
            LocalDate latestCheckDate = jdbcTemplate.queryForObject(
                    "SELECT MAX(check_date) FROM signal_execution_feedback",
                    LocalDate.class);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pendingSignalCount", pendingSignalCount);
            result.put("executedSignalCount", executedSignalCount);
            result.put("missedSignalCount", missedSignalCount);
            result.put("partialExecutionCount", partialExecutionCount);
            result.put("unmatchedExecutionCount", unmatchedExecutionCount);
            result.put("latestCheckDate", latestCheckDate);
            result.put("todayWritebackComplete",
                    pendingSignalCount == 0 && partialExecutionCount == 0 && unmatchedExecutionCount == 0);
            return result;
        }
        catch (DataAccessException ex)
        {
            log.warn("Query execution reconciliation summary failed, fallback empty map: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public List<Map<String, Object>> executionMatchCandidates(Long executionRecordId, int limit)
    {
        if (executionRecordId == null)
        {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 20));
        try
        {
            return jdbcTemplate.queryForList(
                    "SELECT " +
                    "  er.id AS execution_record_id, ts.id AS signal_id, ts.stock_code, ts.strategy_id, ts.signal_type, ts.signal_date, " +
                    "  CASE " +
                    "    WHEN COALESCE(ts.is_execute, 0) = 0 THEN 97 " +
                    "    ELSE 72 " +
                    "  END AS match_score, " +
                    "  CASE " +
                    "    WHEN COALESCE(ts.is_execute, 0) = 0 THEN 'same_code_strategy_side_latest_pending' " +
                    "    ELSE 'same_code_strategy_side_already_executed' " +
                    "  END AS match_reason, " +
                    "  COALESCE(ts.is_execute, 0) AS already_executed " +
                    "FROM execution_record er " +
                    "JOIN trade_signal ts ON ts.stock_code = er.stock_code " +
                    " AND ts.strategy_id = er.strategy_id " +
                    " AND ts.signal_type = er.side " +
                    "WHERE er.id = ? " +
                    "ORDER BY COALESCE(ts.is_execute, 0) ASC, ABS(er.trade_date - ts.signal_date) ASC, ts.signal_date DESC, ts.id DESC " +
                    "LIMIT ?",
                    executionRecordId,
                    safeLimit)
                    .stream()
                    .map(this::toExecutionMatchCandidate)
                    .toList();
        }
        catch (DataAccessException ex)
        {
            log.warn("Query execution match candidates failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> positionSyncResult(Long strategyId, String stockCode)
    {
        try
        {
            String normalizedStockCode = stockCode == null || stockCode.isBlank() ? null : stockCode.trim();

            List<Map<String, Object>> actualRows;
            if (normalizedStockCode == null)
            {
                actualRows = jdbcTemplate.queryForList(
                        "SELECT p.stock_code, p.quantity, p.cost_price FROM position p ORDER BY p.stock_code");
            }
            else
            {
                actualRows = jdbcTemplate.queryForList(
                        "SELECT p.stock_code, p.quantity, p.cost_price FROM position p WHERE p.stock_code = ? ORDER BY p.stock_code",
                        normalizedStockCode);
            }

            StringBuilder derivedSql = new StringBuilder(
                    "SELECT er.stock_code, " +
                    "       SUM(CASE WHEN er.side = 'BUY' THEN er.quantity ELSE -er.quantity END) AS quantity, " +
                    "       CASE WHEN SUM(CASE WHEN er.side = 'BUY' THEN er.quantity ELSE 0 END) = 0 THEN 0 " +
                    "            ELSE ROUND(SUM(CASE WHEN er.side = 'BUY' THEN er.net_amount ELSE 0 END) / " +
                    "                      SUM(CASE WHEN er.side = 'BUY' THEN er.quantity ELSE 0 END), 4) END AS cost_price " +
                    "FROM execution_record er " +
                    "WHERE er.signal_id IS NOT NULL ");
            List<Object> derivedArgs = new ArrayList<>();
            if (strategyId != null)
            {
                derivedSql.append("AND er.strategy_id = ? ");
                derivedArgs.add(strategyId);
            }
            if (normalizedStockCode != null)
            {
                derivedSql.append("AND er.stock_code = ? ");
                derivedArgs.add(normalizedStockCode);
            }
            derivedSql.append(
                    "GROUP BY er.stock_code " +
                    "HAVING SUM(CASE WHEN er.side = 'BUY' THEN er.quantity ELSE -er.quantity END) <> 0 " +
                    "ORDER BY er.stock_code");
            List<Map<String, Object>> derivedRows = derivedArgs.isEmpty()
                    ? jdbcTemplate.queryForList(derivedSql.toString())
                    : jdbcTemplate.queryForList(derivedSql.toString(), derivedArgs.toArray());

            Map<String, Map<String, Object>> actualByCode = indexByStockCode(actualRows);
            Map<String, Map<String, Object>> derivedByCode = indexByStockCode(derivedRows);
            Set<String> allCodes = new HashSet<>();
            allCodes.addAll(actualByCode.keySet());
            allCodes.addAll(derivedByCode.keySet());

            List<Map<String, Object>> differenceItems = new ArrayList<>();
            allCodes.stream().sorted().forEach(code -> {
                Map<String, Object> actual = actualByCode.get(code);
                Map<String, Object> derived = derivedByCode.get(code);
                int actualQty = actual == null ? 0 : toInt(actual.get("quantity"));
                int derivedQty = derived == null ? 0 : toInt(derived.get("quantity"));
                BigDecimal actualCost = actual == null ? BigDecimal.ZERO : toDecimal(actual.get("cost_price"));
                BigDecimal derivedCost = derived == null ? BigDecimal.ZERO : toDecimal(derived.get("cost_price"));
                boolean quantityMismatch = actualQty != derivedQty;
                boolean existenceMismatch = (actual == null) != (derived == null);
                boolean costMismatch = actual != null && derived != null
                        && actualCost != null && derivedCost != null
                        && actualCost.subtract(derivedCost).abs().compareTo(new BigDecimal("0.0001")) > 0;
                if (quantityMismatch || existenceMismatch || costMismatch)
                {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("stockCode", code);
                    item.put("actualQuantity", actualQty);
                    item.put("derivedQuantity", derivedQty);
                    item.put("actualCostPrice", actualCost);
                    item.put("derivedCostPrice", derivedCost);
                    item.put("differenceType", existenceMismatch ? "PRESENCE" : (quantityMismatch ? "QUANTITY" : "COST"));
                    differenceItems.add(item);
                }
            });

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("syncStatus", allCodes.isEmpty() ? "EMPTY" : (differenceItems.isEmpty() ? "MATCH" : "DIFF"));
            result.put("positionBefore", actualRows.stream().map(this::toPositionSnapshot).toList());
            result.put("positionAfter", derivedRows.stream().map(this::toPositionSnapshot).toList());
            result.put("differenceItems", differenceItems);
            result.put("differenceCount", differenceItems.size());
            return result;
        }
        catch (DataAccessException ex)
        {
            log.warn("Query position sync result failed, fallback empty map: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    @Transactional
    public Map<String, Object> confirmExecutionMatch(
            Long signalId,
            Long executionRecordId,
            String actor,
            String remark)
    {
        if (signalId == null || executionRecordId == null)
        {
            throw new ServiceException("signalId and executionRecordId are required");
        }
        Map<String, Object> execution = jdbcTemplate.queryForMap(
                "SELECT id, stock_code, side, strategy_id, signal_id FROM execution_record WHERE id = ?",
                executionRecordId);
        Map<String, Object> signal = jdbcTemplate.queryForMap(
                "SELECT id, stock_code, signal_type, strategy_id FROM trade_signal WHERE id = ?",
                signalId);

        if (!Objects.equals(String.valueOf(execution.get("stock_code")), String.valueOf(signal.get("stock_code"))))
        {
            throw new ServiceException("stock_code mismatch between execution and signal");
        }
        if (!Objects.equals(String.valueOf(execution.get("side")), String.valueOf(signal.get("signal_type"))))
        {
            throw new ServiceException("side mismatch between execution and signal");
        }
        if (!Objects.equals(toLong(execution.get("strategy_id")), toLong(signal.get("strategy_id"))))
        {
            throw new ServiceException("strategy_id mismatch between execution and signal");
        }

        jdbcTemplate.update("UPDATE execution_record SET signal_id = ? WHERE id = ?", signalId, executionRecordId);
        jdbcTemplate.update("UPDATE trade_signal SET is_execute = 1 WHERE id = ?", signalId);

        Map<String, Object> confirmedExecution = new LinkedHashMap<>(execution);
        confirmedExecution.put("signal_id", signalId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchConfirmed", true);
        result.put("actor", actor);
        result.put("remark", remark);
        result.put("executionRecord", toCamelCaseMap(confirmedExecution));
        result.put("signal", toCamelCaseMap(signal));
        return result;
    }

    public List<Map<String, Object>> jobBatches(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try
        {
            return jdbcTemplate.queryForList(
                    "SELECT id, pipeline_name, status, params, batch_key, start_time, end_time, error_message " +
                    "FROM job_run_batch ORDER BY start_time DESC, id DESC LIMIT ?",
                    safeLimit);
        }
        catch (DataAccessException ex)
        {
            log.warn("Query job_run_batch failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> jobSteps(Long batchId)
    {
        if (batchId == null)
        {
            return List.of();
        }
        try
        {
            return jdbcTemplate.queryForList(
                    "SELECT id, batch_id, step_name, status, start_time, end_time, retries, error_message " +
                    "FROM job_run_step WHERE batch_id = ? ORDER BY id",
                    batchId).stream().map(this::toCamelCaseMap).toList();
        }
        catch (DataAccessException ex)
        {
            log.warn("Query job_run_step failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> jobReadiness(Long batchId)
    {
        Long resolvedBatchId = resolveBatchId(batchId, false);
        if (resolvedBatchId == null)
        {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("status", "EMPTY");
            empty.put("batchId", null);
            empty.put("completedSteps", 0);
            empty.put("totalSteps", FULL_DAILY_CORE_STEPS.size());
            empty.put("message", "暂无执行批次，请先提交执行任务。");
            empty.put("canEnterDashboard", false);
            empty.put("canRecover", false);
            empty.put("latestFailedStep", null);
            empty.put("dataIntegrityStatus", "EMPTY");
            empty.put("dataIntegrityCategory", null);
            empty.put("dataIntegrityMessage", "暂无可评估的数据完整性信息。");
            return empty;
        }
        try
        {
            Map<String, Object> batch = jdbcTemplate.queryForMap(
                    "SELECT id, pipeline_name, status, start_time, end_time, error_message FROM job_run_batch WHERE id = ?",
                    resolvedBatchId);
            List<Map<String, Object>> steps = jobSteps(resolvedBatchId);
            int completedSteps = 0;
            int failedSteps = 0;
            int warningSteps = 0;
            String latestFailedStep = null;
            for (Map<String, Object> step : steps)
            {
                String status = String.valueOf(step.get("status"));
                if ("SUCCESS".equals(status))
                {
                    completedSteps++;
                }
                else if ("FAILED".equals(status))
                {
                    failedSteps++;
                    latestFailedStep = String.valueOf(step.get("stepName"));
                }
                if (isStepDegraded(step))
                {
                    warningSteps++;
                }
            }
            Map<String, Object> dataIntegrity = evaluateDataIntegrity(steps);
            String dataIntegrityStatus = valueOrDefault(dataIntegrity.get("status"), "READY");
            String dataIntegrityCategory = valueOrDefault(dataIntegrity.get("category"), null);
            String dataIntegrityMessage = valueOrDefault(dataIntegrity.get("message"), "盘后结果数据完整。");
            int totalSteps = Math.max(FULL_DAILY_CORE_STEPS.size(), steps.size());
            boolean batchSuccess = "SUCCESS".equals(String.valueOf(batch.get("status")));
            boolean canEnterDashboard = batchSuccess && failedSteps == 0;
            String readinessStatus;
            String message;
            if ("RUNNING".equals(String.valueOf(batch.get("status"))))
            {
                readinessStatus = "RUNNING";
                message = "盘后主流程仍在运行，请等待步骤完成。";
            }
            else if (failedSteps > 0 || "FAILED".equals(String.valueOf(batch.get("status"))))
            {
                readinessStatus = "BLOCKED";
                message = latestFailedStep == null
                        ? "盘后主流程存在失败步骤，请先恢复后再进入看板。"
                        : String.format("步骤 %s 失败，请先恢复后再进入看板。", latestFailedStep);
            }
            else if ("BLOCKED".equals(dataIntegrityStatus))
            {
                readinessStatus = "BLOCKED";
                message = dataIntegrityMessage;
                canEnterDashboard = false;
            }
            else if ("WARNING".equals(dataIntegrityStatus))
            {
                readinessStatus = "READY_WITH_WARNINGS";
                message = dataIntegrityMessage;
                canEnterDashboard = true;
            }
            else if (warningSteps > 0)
            {
                readinessStatus = "READY_WITH_WARNINGS";
                message = "盘后主流程已完成，但存在降级或部分成功步骤，建议先核对异常项。";
                canEnterDashboard = true;
            }
            else if (batchSuccess)
            {
                readinessStatus = "READY";
                message = "盘后主流程已完成，可以进入量化看板。";
            }
            else
            {
                readinessStatus = "UNKNOWN";
                message = "批次状态异常，请检查任务中心。";
                canEnterDashboard = false;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", readinessStatus);
            result.put("batchId", resolvedBatchId);
            result.put("pipelineName", batch.get("pipeline_name"));
            result.put("completedSteps", completedSteps);
            result.put("totalSteps", totalSteps);
            result.put("message", message);
            result.put("canEnterDashboard", canEnterDashboard);
            result.put("canRecover", failedSteps > 0 && "full-daily".equals(String.valueOf(batch.get("pipeline_name"))));
            result.put("latestFailedStep", latestFailedStep);
            result.put("warningStepCount", warningSteps);
            result.put("failedStepCount", failedSteps);
            result.put("dataIntegrityStatus", dataIntegrityStatus);
            result.put("dataIntegrityCategory", dataIntegrityCategory);
            result.put("dataIntegrityMessage", dataIntegrityMessage);
            return result;
        }
        catch (DataAccessException ex)
        {
            log.warn("Query job readiness failed, fallback empty map: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public List<Map<String, Object>> jobErrorCategories(Long batchId)
    {
        Long resolvedBatchId = resolveBatchId(batchId, false);
        if (resolvedBatchId == null)
        {
            return List.of();
        }
        List<Map<String, Object>> steps = jobSteps(resolvedBatchId);
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> step : steps)
        {
            Map<String, Object> category = classifyJobStep(step);
            if (category == null || category.isEmpty())
            {
                continue;
            }
            String key = String.valueOf(category.get("category"));
            Map<String, Object> bucket = grouped.computeIfAbsent(key, ignored -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("category", category.get("category"));
                row.put("severity", category.get("severity"));
                row.put("count", 0);
                row.put("latestMessage", category.get("latestMessage"));
                row.put("suggestedAction", category.get("suggestedAction"));
                row.put("stepNames", new ArrayList<String>());
                return row;
            });
            bucket.put("count", toInt(bucket.get("count")) + 1);
            @SuppressWarnings("unchecked")
            List<String> stepNames = (List<String>) bucket.get("stepNames");
            String stepName = String.valueOf(step.get("stepName"));
            if (!stepNames.contains(stepName))
            {
                stepNames.add(stepName);
            }
            bucket.put("latestMessage", category.get("latestMessage"));
            bucket.put("suggestedAction", category.get("suggestedAction"));
            if ("danger".equals(category.get("severity")))
            {
                bucket.put("severity", "danger");
            }
            else if ("warning".equals(category.get("severity")) && !"danger".equals(bucket.get("severity")))
            {
                bucket.put("severity", "warning");
            }
        }
        return new ArrayList<>(grouped.values());
    }

    public List<Map<String, Object>> jobSopHints(Long batchId)
    {
        Map<String, Object> readiness = jobReadiness(batchId);
        List<Map<String, Object>> hints = new ArrayList<>();
        if (readiness.isEmpty())
        {
            return hints;
        }
        String status = String.valueOf(readiness.get("status"));
        if ("EMPTY".equals(status))
        {
            hints.add(hint("runExecution", "info", "先提交执行任务", "当前还没有可评估的执行批次。", "先执行任务，再进入看板。", "/quant/jobs", null, false));
            return hints;
        }
        if ("RUNNING".equals(status))
        {
            hints.add(hint("waitExecution", "warning", "等待当前任务完成", "执行任务仍在运行。", "建议先观察当前任务和步骤状态，不要重复提交。", "/quant/jobs", null, false));
            return hints;
        }
        if (Boolean.TRUE.equals(readiness.get("canEnterDashboard")))
        {
            hints.add(hint("goDashboard", "success", "进入量化看板", "盘后主流程已经达到可查看状态。", "先去看板确认市场状态、信号、持仓和风险预警。", "/quant/dashboard", null, false));
        }
        for (Map<String, Object> category : jobErrorCategories(batchId))
        {
            String code = String.valueOf(category.get("category"));
            if ("UPSTREAM_NETWORK".equals(code))
            {
                hints.add(hint("recoverBatch", "danger", "恢复失败批次", "上游行情源或代理连接失败。", "优先点击恢复失败批次；若仍失败，再检查代理与外网访问。", "/quant/operations", null, true));
            }
            else if ("PARTIAL_DAILY_SYNC".equals(code))
            {
                hints.add(hint("openSymbols", "warning", "核对缺失标的", "日线同步部分成功。", "先看失败标的，再决定是否重跑 sync-daily 或继续执行闭环核对。", "/quant/symbols", null, false));
            }
            else if ("FALLBACK_BASIC".equals(code))
            {
                hints.add(hint("checkBasicFallback", "warning", "确认基础标的回退", "基础标的数据来自库内回退。", "今日结果可以继续查看；若你需要最新基础标信息，请在运维中心补跑 sync-basic。", "/quant/operations", null, false));
            }
            else if ("EXECUTION_FEEDBACK_GAP".equals(code))
            {
                hints.add(hint("openExecution", "warning", "核对执行闭环", "执行反馈或持仓同步存在缺口。", "先进入执行回写页处理未匹配成交、部分成交和持仓差异。", "/quant/execution", "{\"focus\":\"abnormal\"}", false));
            }
            else if ("STEP_FAILED".equals(code))
            {
                hints.add(hint("genericRecover", "danger", "恢复失败步骤", "存在失败步骤阻断盘后主流程。", "先恢复失败批次；若仍失败，再根据错误分类做人工排查。", "/quant/operations", null, true));
            }
        }
        return hints;
    }

    public List<Map<String, Object>> asyncJobs(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try
        {
            return jdbcTemplate.queryForList(
                    "SELECT id, job_key, job_type, requested_mode, resolved_mode, status, actor, " +
                            "planned_shard_count, completed_shard_count, failed_shard_count, cancel_requested, " +
                            "error_message, request_payload, normalized_payload, create_time, start_time, end_time " +
                            "FROM quant_async_job ORDER BY create_time DESC, id DESC LIMIT ?",
                    safeLimit)
                    .stream()
                    .map(this::enrichAsyncJobRow)
                    .toList();
        }
        catch (DataAccessException ex)
        {
            log.warn("Query quant_async_job failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> asyncJobDetail(Long jobId)
    {
        if (jobId == null)
        {
            return new LinkedHashMap<>();
        }
        try
        {
            return jdbcTemplate.queryForList(
                    "SELECT id, job_key, job_type, requested_mode, resolved_mode, status, actor, " +
                            "planned_shard_count, completed_shard_count, failed_shard_count, cancel_requested, " +
                            "error_message, request_payload, normalized_payload, create_time, start_time, end_time " +
                            "FROM quant_async_job WHERE id = ?",
                    jobId)
                    .stream()
                    .findFirst()
                    .map(this::enrichAsyncJobRow)
                    .map(LinkedHashMap::new)
                    .orElseGet(LinkedHashMap::new);
        }
        catch (DataAccessException ex)
        {
            log.warn("Query async job detail failed, fallback empty map: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public List<Map<String, Object>> dispatchDefinitions()
    {
        try
        {
            return jdbcTemplate.queryForList(
                    "SELECT job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, remark " +
                            "FROM sys_job WHERE invoke_target LIKE 'quantRoadTask.%' ORDER BY status ASC, job_name ASC")
                    .stream()
                    .map(this::toCamelCaseMap)
                    .map(this::enrichDispatchDefinition)
                    .toList();
        }
        catch (DataAccessException ex)
        {
            log.warn("Query sys_job for dispatch definitions failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> nextScheduledDispatch()
    {
        return dispatchDefinitions().stream()
                .filter(item -> Boolean.TRUE.equals(item.get("autoEnabled")))
                .filter(item -> item.get("nextFireTime") != null)
                .min(Comparator.comparing(item -> String.valueOf(item.get("nextFireTime"))))
                .map(LinkedHashMap::new)
                .orElseGet(LinkedHashMap::new);
    }

    public Map<String, Object> dispatchHistory(int pageNum, int pageSize, String taskCode, String triggerMode)
    {
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(asyncDispatchHistoryRows(safePageNum * safePageSize));
        rows.addAll(quartzDispatchHistoryRows(safePageNum * safePageSize));

        List<Map<String, Object>> filtered = rows.stream()
                .filter(row -> matchesOptional(row.get("taskCode"), taskCode))
                .filter(row -> matchesOptional(row.get("triggerMode"), triggerMode))
                .sorted((left, right) -> String.valueOf(right.getOrDefault("startedAt", ""))
                        .compareTo(String.valueOf(left.getOrDefault("startedAt", ""))))
                .toList();

        int fromIndex = Math.min((safePageNum - 1) * safePageSize, filtered.size());
        int toIndex = Math.min(fromIndex + safePageSize, filtered.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pageNum", safePageNum);
        result.put("pageSize", safePageSize);
        result.put("total", filtered.size());
        result.put("rows", filtered.subList(fromIndex, toIndex));
        return result;
    }

    public Map<String, Object> asyncWorkerSummary()
    {
        try
        {
            long queuedShardCount = getCount("SELECT COUNT(1) FROM quant_async_job_shard WHERE status = 'QUEUED'");
            long runningShardCount = getCount("SELECT COUNT(1) FROM quant_async_job_shard WHERE status = 'RUNNING'");
            long failedShardCount = getCount("SELECT COUNT(1) FROM quant_async_job_shard WHERE status = 'FAILED'");
            long expiredShardCount = getCount(
                    "SELECT COUNT(1) FROM quant_async_job_shard WHERE status = 'RUNNING' AND lease_expires_at IS NOT NULL AND lease_expires_at < NOW()");
            long pendingJobCount = getCount("SELECT COUNT(1) FROM quant_async_job WHERE status IN ('QUEUED', 'PENDING')");
            long runningJobCount = getCount("SELECT COUNT(1) FROM quant_async_job WHERE status = 'RUNNING'");
            long activeWorkerCount = getCount(
                    "SELECT COUNT(DISTINCT lease_owner) FROM quant_async_job_shard " +
                            "WHERE status = 'RUNNING' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL AND lease_expires_at >= NOW()");

            List<Map<String, Object>> workers = jdbcTemplate.queryForList(
                    "SELECT lease_owner AS worker_id, COUNT(1) AS running_shard_count, " +
                            "MAX(heartbeat_at) AS latest_heartbeat_at, MAX(lease_expires_at) AS latest_lease_expires_at " +
                            "FROM quant_async_job_shard " +
                            "WHERE status = 'RUNNING' AND lease_owner IS NOT NULL " +
                            "GROUP BY lease_owner " +
                            "ORDER BY MAX(heartbeat_at) DESC NULLS LAST, lease_owner ASC")
                    .stream()
                    .map(this::toCamelCaseMap)
                    .toList();

            String status;
            String message;
            if (queuedShardCount > 0 && activeWorkerCount == 0)
            {
                status = "BLOCKED";
                message = "存在待消费分片，但当前没有活跃 worker。";
            }
            else if (expiredShardCount > 0 || failedShardCount > 0)
            {
                status = "DEGRADED";
                message = "存在失败或过期分片，建议先执行恢复。";
            }
            else if (runningShardCount > 0 && activeWorkerCount > 0)
            {
                status = "ACTIVE";
                message = "后台执行器正在消费分片。";
            }
            else
            {
                status = "IDLE";
                message = "当前没有待消费的队列分片。";
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", status);
            result.put("message", message);
            result.put("queuedShardCount", queuedShardCount);
            result.put("runningShardCount", runningShardCount);
            result.put("failedShardCount", failedShardCount);
            result.put("expiredShardCount", expiredShardCount);
            result.put("pendingJobCount", pendingJobCount);
            result.put("runningJobCount", runningJobCount);
            result.put("activeWorkerCount", activeWorkerCount);
            result.put("workers", workers);
            return result;
        }
        catch (DataAccessException ex)
        {
            log.warn("Query async worker summary failed, fallback empty map: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public List<Map<String, Object>> asyncJobShards(Long jobId)
    {
        if (jobId == null)
        {
            return List.of();
        }
        try
        {
            return jdbcTemplate.queryForList(
                    "SELECT id, job_id, shard_key, strategy_id, shard_index, status, symbol_count, " +
                    "attempt_count, lease_owner, lease_expires_at, heartbeat_at, last_error, payload, create_time, start_time, end_time " +
                    "FROM quant_async_job_shard WHERE job_id = ? ORDER BY shard_index",
                    jobId)
                    .stream()
                    .map(this::toCamelCaseMap)
                    .map(this::enrichAsyncShardRow)
                    .toList();
        }
        catch (DataAccessException ex)
        {
            log.warn("Query quant_async_job_shard failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> asyncJobResults(Long jobId, int limit)
    {
        if (jobId == null)
        {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 500));
        try
        {
            return jdbcTemplate.queryForList(
                    "SELECT id, job_id, strategy_id, stock_code, signal_type, annual_return, max_drawdown, " +
                    "win_rate, total_profit, trade_count, total_cost, is_invalid, remark, create_time " +
                    "FROM quant_async_job_result WHERE job_id = ? ORDER BY strategy_id, stock_code LIMIT ?",
                    jobId,
                    safeLimit);
        }
        catch (DataAccessException ex)
        {
            log.warn("Query quant_async_job_result failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    @Transactional
    public Map<String, Object> markExecutionException(Long signalId, String exceptionType, String remark, String actor)
    {
        if (signalId == null)
        {
            throw new ServiceException("signalId is required");
        }
        String normalizedType = exceptionType == null ? "" : exceptionType.trim().toUpperCase();
        if (!List.of("MISSED", "CANCELLED", "MANUAL_REVIEW").contains(normalizedType))
        {
            throw new ServiceException("exceptionType must be MISSED, CANCELLED, or MANUAL_REVIEW");
        }

        Map<String, Object> signal = jdbcTemplate.queryForMap(
                "SELECT id, stock_code, signal_date, strategy_id, is_execute FROM trade_signal WHERE id = ?",
                signalId);
        LocalDate signalDate = signal.get("signal_date") instanceof LocalDate localDate
                ? localDate
                : LocalDate.parse(String.valueOf(signal.get("signal_date")));
        LocalDate dueDate = signalDate.plusDays(1);
        LocalDate checkDate = LocalDate.now();
        int overdueDays = (int) Math.max(0L, ChronoUnit.DAYS.between(dueDate, checkDate));
        String feedbackStatus = "MANUAL_REVIEW".equals(normalizedType) ? "PENDING" : "MISSED";
        String finalRemark = String.format(
                "[EXCEPTION:%s][%s] %s",
                normalizedType,
                actor == null || actor.isBlank() ? "system" : actor.trim(),
                remark == null || remark.isBlank() ? "manual exception mark" : remark.trim());

        jdbcTemplate.update(
                "INSERT INTO signal_execution_feedback (" +
                        "signal_id, signal_date, due_date, check_date, status, executed_quantity, last_trade_date, overdue_days, remark, update_time" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                        "ON CONFLICT (signal_id) DO UPDATE SET " +
                        "check_date = EXCLUDED.check_date, " +
                        "status = EXCLUDED.status, " +
                        "executed_quantity = EXCLUDED.executed_quantity, " +
                        "last_trade_date = EXCLUDED.last_trade_date, " +
                        "overdue_days = EXCLUDED.overdue_days, " +
                        "remark = EXCLUDED.remark, " +
                        "update_time = NOW()",
                signalId,
                signalDate,
                dueDate,
                checkDate,
                feedbackStatus,
                0,
                null,
                overdueDays,
                finalRemark);
        jdbcTemplate.update("UPDATE trade_signal SET is_execute = 0 WHERE id = ?", signalId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signalId", signalId);
        result.put("stockCode", signal.get("stock_code"));
        result.put("strategyId", toLong(signal.get("strategy_id")));
        result.put("exceptionType", normalizedType);
        result.put("feedbackStatus", feedbackStatus);
        result.put("checkDate", checkDate);
        result.put("overdueDays", overdueDays);
        result.put("remark", finalRemark);
        return result;
    }

    private List<Map<String, Object>> latestSignals(int limit)
    {
        return jdbcTemplate.queryForList(
                "SELECT stock_code, stock_name, signal_type, suggest_price, signal_date, strategy_id " +
                "FROM trade_signal ORDER BY signal_date DESC, create_time DESC LIMIT ?",
                limit);
    }

    private List<Map<String, Object>> lossWarnings(int limit)
    {
        return jdbcTemplate.queryForList(
                "SELECT stock_code, stock_name, current_price, float_profit " +
                "FROM position WHERE loss_warning = 1 ORDER BY stock_code LIMIT ?",
                limit);
    }

    private List<Map<String, Object>> latestLogs(int limit)
    {
        return jdbcTemplate.queryForList(
                "SELECT strategy_id, run_time, annual_return, max_drawdown, win_rate, total_profit, is_invalid, remark " +
                "FROM strategy_run_log ORDER BY run_time DESC LIMIT ?",
                limit);
    }

    private long getCount(String sql, Object... args)
    {
        Long value = args == null || args.length == 0
                ? jdbcTemplate.queryForObject(sql, Long.class)
                : jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private BigDecimal toDecimal(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof BigDecimal)
        {
            return (BigDecimal) value;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private int toInt(Object value)
    {
        if (value == null)
        {
            return 0;
        }
        if (value instanceof Number)
        {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private double toDouble(Object value)
    {
        if (value == null)
        {
            return 0D;
        }
        if (value instanceof Number)
        {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private Long toLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Map<String, Object> toExecutionMatchCandidate(Map<String, Object> row)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionRecordId", toLong(row.get("execution_record_id")));
        result.put("signalId", toLong(row.get("signal_id")));
        result.put("stockCode", row.get("stock_code"));
        result.put("strategyId", toLong(row.get("strategy_id")));
        result.put("signalType", row.get("signal_type"));
        result.put("signalDate", row.get("signal_date"));
        result.put("matchScore", toInt(row.get("match_score")));
        result.put("matchReason", row.get("match_reason"));
        result.put("alreadyExecuted", toInt(row.get("already_executed")) == 1);
        return result;
    }

    private Map<String, Object> toPositionSnapshot(Map<String, Object> row)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stockCode", row.get("stock_code"));
        result.put("quantity", toInt(row.get("quantity")));
        result.put("costPrice", toDecimal(row.get("cost_price")));
        return result;
    }

    private List<Map<String, Object>> decorateSignals(List<Map<String, Object>> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return List.of();
        }
        return rows.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>(row);
            LocalDate signalDate = row.get("signal_date") instanceof LocalDate localDate
                    ? localDate
                    : LocalDate.parse(String.valueOf(row.get("signal_date")));
            result.put("execution_due_date", signalDate.plusDays(1));
            result.put("match_hint", deriveSignalMatchHint(row));
            return result;
        }).toList();
    }

    private List<Map<String, Object>> decorateExecutionRecords(List<Map<String, Object>> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return List.of();
        }
        Set<String> matchedStockCodes = new HashSet<>();
        for (Map<String, Object> row : rows)
        {
            if (toLong(row.get("signal_id")) != null && row.get("stock_code") != null)
            {
                matchedStockCodes.add(String.valueOf(row.get("stock_code")));
            }
        }
        Map<String, Map<String, Object>> actualByCode = loadActualPositionsByStockCode(matchedStockCodes);
        Map<String, Map<String, Object>> derivedByCode = loadDerivedPositionsByStockCode(matchedStockCodes);

        return rows.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>(row);
            result.put("match_status", deriveExecutionMatchStatus(row));
            result.put("position_sync_status", deriveExecutionPositionSyncStatus(row, actualByCode, derivedByCode));
            return result;
        }).toList();
    }

    private List<Map<String, Object>> decorateExecutionFeedbackDetails(List<Map<String, Object>> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return List.of();
        }
        Set<Long> signalIds = new HashSet<>();
        for (Map<String, Object> row : rows)
        {
            Long signalId = toLong(row.get("signal_id"));
            if (signalId != null)
            {
                signalIds.add(signalId);
            }
        }
        Map<Long, List<Long>> matchedExecutionIds = loadMatchedExecutionIdsBySignal(signalIds);

        return rows.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>(row);
            Long signalId = toLong(row.get("signal_id"));
            List<Long> matchedIds = signalId == null
                    ? List.of()
                    : matchedExecutionIds.getOrDefault(signalId, List.of());
            result.put("matched_execution_ids", matchedIds);
            result.put("feedback_action", deriveFeedbackAction(row, matchedIds));
            return result;
        }).toList();
    }

    private Map<String, Map<String, Object>> indexByStockCode(List<Map<String, Object>> rows)
    {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : rows)
        {
            result.put(String.valueOf(row.get("stock_code")), row);
        }
        return result;
    }

    private Map<String, List<Map<String, Object>>> groupRowsByStockCode(List<Map<String, Object>> rows)
    {
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        for (Map<String, Object> row : rows)
        {
            String stockCode = valueOrDefault(row.get("stock_code"), "");
            if (stockCode.isBlank())
            {
                continue;
            }
            result.computeIfAbsent(stockCode, key -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private Set<String> loadEtfCoreStockCodes()
    {
        try
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT stock_code FROM quant_symbol_pool_member WHERE pool_code = 'ETF_CORE' AND inclusion_status = 'INCLUDED'");
            Set<String> result = new HashSet<>();
            for (Map<String, Object> row : rows)
            {
                String stockCode = valueOrDefault(row.get("stock_code"), "");
                if (!stockCode.isBlank())
                {
                    result.add(stockCode);
                }
            }
            return result;
        }
        catch (DataAccessException ex)
        {
            log.warn("Load ETF core stock codes failed, fallback empty set: {}", ex.getMessage());
            return Set.of();
        }
    }

    private boolean isEtfAsset(String stockCode, String stockName, Set<String> etfStockCodes)
    {
        if (stockCode != null && etfStockCodes != null && etfStockCodes.contains(stockCode))
        {
            return true;
        }
        return valueOrDefault(stockName, "").toUpperCase().contains("ETF");
    }

    private String deriveEtfGovernanceAction(
            boolean riskWarning,
            long pendingExecutionCount,
            boolean hasSignal,
            int holdingQuantity,
            List<String> candidateCodes)
    {
        if (riskWarning || pendingExecutionCount > 0)
        {
            return "REVIEW";
        }
        if (hasSignal && holdingQuantity <= 0)
        {
            return "BUILD_POSITION";
        }
        if (holdingQuantity > 0)
        {
            return "KEEP_PRIMARY";
        }
        if (candidateCodes != null && !candidateCodes.isEmpty())
        {
            return "OBSERVE_MAPPING";
        }
        return "KEEP_PRIMARY";
    }

    private String deriveEtfGovernanceReason(
            boolean riskWarning,
            long pendingExecutionCount,
            boolean hasSignal,
            int holdingQuantity,
            List<String> candidateCodes)
    {
        if (riskWarning)
        {
            return "主ETF持仓已触发独立风险预警，应先复盘主链风险和持仓纪律。";
        }
        if (pendingExecutionCount > 0)
        {
            return "主ETF仍有待执行或漏执行信号，需先核对 signal -> execution -> position 链路。";
        }
        if (hasSignal && holdingQuantity <= 0)
        {
            return "主ETF已出现当日信号，但尚未形成持仓，适合进入执行闭环确认承接。";
        }
        if (holdingQuantity > 0)
        {
            return "主ETF当前已承担默认执行角色，治理重点应放在保持映射稳定和持续复盘。";
        }
        if (candidateCodes != null && !candidateCodes.isEmpty())
        {
            return "当前主ETF暂无持仓和信号，可继续观察主ETF与备选ETF的替代空间。";
        }
        return "当前映射已建立，但暂无额外治理动作。";
    }

    private Map<String, Object> buildEtfReviewQuery(String primaryEtfCode)
    {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("reviewLevel", "trade");
        query.put("stockCode", primaryEtfCode);
        query.put("scopeType", "etf_pool");
        query.put("scopePoolCode", "ETF_CORE");
        query.put("sourcePage", "symbols");
        query.put("sourceAction", "etfGovernance");
        return query;
    }

    private Map<String, Object> buildEtfBacktestQuery(String primaryEtfCode)
    {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("scopeType", "etf_pool");
        query.put("scopePoolCode", "ETF_CORE");
        query.put("symbols", primaryEtfCode);
        return query;
    }

    private List<String> parseJsonStringArray(Object raw)
    {
        if (raw == null)
        {
            return List.of();
        }
        try
        {
            List<Object> parsed = JSON.parseArray(String.valueOf(raw), Object.class);
            if (parsed == null || parsed.isEmpty())
            {
                return List.of();
            }
            return parsed.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
        catch (Exception ex)
        {
            return List.of();
        }
    }

    private List<Map<String, Object>> mapListValue(Object raw)
    {
        if (!(raw instanceof List<?> items) || items.isEmpty())
        {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : items)
        {
            if (item instanceof Map<?, ?> map)
            {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                result.add(normalized);
            }
        }
        return result;
    }

    private Map<String, Map<String, Object>> loadActualPositionsByStockCode(Set<String> stockCodes)
    {
        if (stockCodes == null || stockCodes.isEmpty())
        {
            return Map.of();
        }
        List<String> orderedCodes = stockCodes.stream().sorted().toList();
        String placeholders = String.join(", ", Collections.nCopies(orderedCodes.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.stock_code, p.quantity, p.cost_price " +
                        "FROM position p WHERE p.stock_code IN (" + placeholders + ") ORDER BY p.stock_code",
                orderedCodes.toArray());
        return indexByStockCode(rows);
    }

    private Map<String, Map<String, Object>> loadDerivedPositionsByStockCode(Set<String> stockCodes)
    {
        if (stockCodes == null || stockCodes.isEmpty())
        {
            return Map.of();
        }
        List<String> orderedCodes = stockCodes.stream().sorted().toList();
        String placeholders = String.join(", ", Collections.nCopies(orderedCodes.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT er.stock_code, " +
                        "       SUM(CASE WHEN er.side = 'BUY' THEN er.quantity ELSE -er.quantity END) AS quantity, " +
                        "       CASE WHEN SUM(CASE WHEN er.side = 'BUY' THEN er.quantity ELSE 0 END) = 0 THEN 0 " +
                        "            ELSE ROUND(SUM(CASE WHEN er.side = 'BUY' THEN er.net_amount ELSE 0 END) / " +
                        "                      SUM(CASE WHEN er.side = 'BUY' THEN er.quantity ELSE 0 END), 4) END AS cost_price " +
                        "FROM execution_record er " +
                        "WHERE er.signal_id IS NOT NULL AND er.stock_code IN (" + placeholders + ") " +
                        "GROUP BY er.stock_code " +
                        "HAVING SUM(CASE WHEN er.side = 'BUY' THEN er.quantity ELSE -er.quantity END) <> 0 " +
                        "ORDER BY er.stock_code",
                orderedCodes.toArray());
        return indexByStockCode(rows);
    }

    private Map<Long, List<Long>> loadMatchedExecutionIdsBySignal(Set<Long> signalIds)
    {
        if (signalIds == null || signalIds.isEmpty())
        {
            return Map.of();
        }
        List<Long> orderedIds = signalIds.stream().sorted().toList();
        String placeholders = String.join(", ", Collections.nCopies(orderedIds.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT signal_id, STRING_AGG(CAST(id AS VARCHAR), ',' ORDER BY trade_date DESC, id DESC) AS matched_execution_ids " +
                        "FROM execution_record WHERE signal_id IN (" + placeholders + ") GROUP BY signal_id",
                orderedIds.toArray());
        Map<Long, List<Long>> result = new HashMap<>();
        for (Map<String, Object> row : rows)
        {
            Long signalId = toLong(row.get("signal_id"));
            if (signalId != null)
            {
                result.put(signalId, parseIdList(row.get("matched_execution_ids")));
            }
        }
        return result;
    }

    private String deriveExecutionMatchStatus(Map<String, Object> row)
    {
        if (toLong(row.get("signal_id")) == null)
        {
            return "UNMATCHED";
        }
        if (toInt(row.get("signal_executed")) == 1)
        {
            return "EXECUTED";
        }
        String feedbackStatus = row.get("feedback_status") == null ? "" : String.valueOf(row.get("feedback_status"));
        if ("MISSED".equalsIgnoreCase(feedbackStatus))
        {
            return "MISSED";
        }
        if ("EXECUTED".equalsIgnoreCase(feedbackStatus) || toInt(row.get("feedback_executed_quantity")) > 0)
        {
            return "PARTIAL";
        }
        return "PENDING";
    }

    private String deriveSignalMatchHint(Map<String, Object> row)
    {
        return toInt(row.get("is_execute")) == 1
                ? "already_recorded_execution"
                : "pending_record_execution";
    }

    private String deriveExecutionPositionSyncStatus(
            Map<String, Object> row,
            Map<String, Map<String, Object>> actualByCode,
            Map<String, Map<String, Object>> derivedByCode)
    {
        if (toLong(row.get("signal_id")) == null)
        {
            return "PENDING_MATCH";
        }
        String stockCode = row.get("stock_code") == null ? null : String.valueOf(row.get("stock_code"));
        if (stockCode == null)
        {
            return "UNKNOWN";
        }
        return hasPositionDifference(actualByCode.get(stockCode), derivedByCode.get(stockCode)) ? "DIFF" : "MATCH";
    }

    private String deriveFeedbackAction(Map<String, Object> row, List<Long> matchedExecutionIds)
    {
        String remark = row.get("remark") == null ? "" : String.valueOf(row.get("remark")).toUpperCase();
        String status = row.get("status") == null ? "" : String.valueOf(row.get("status")).toUpperCase();
        boolean signalExecuted = toInt(row.get("signal_executed")) == 1;
        boolean hasMatchedExecutions = matchedExecutionIds != null && !matchedExecutionIds.isEmpty();

        if (remark.contains("[EXCEPTION:CANCELLED]"))
        {
            return "CANCELLED_CONFIRMED";
        }
        if (remark.contains("[EXCEPTION:MISSED]"))
        {
            return "MISSED_CONFIRMED";
        }
        if (remark.contains("[EXCEPTION:MANUAL_REVIEW]"))
        {
            return "MANUAL_REVIEW";
        }
        if ("MISSED".equals(status))
        {
            return "CHECK_EXCEPTION";
        }
        if ("EXECUTED".equals(status) && signalExecuted)
        {
            return "NO_ACTION";
        }
        if (hasMatchedExecutions && !signalExecuted)
        {
            return "COMPLETE_PARTIAL_EXECUTION";
        }
        if ("PENDING".equals(status) && !hasMatchedExecutions)
        {
            return "RECORD_EXECUTION";
        }
        return "MANUAL_REVIEW";
    }

    private boolean hasPositionDifference(Map<String, Object> actual, Map<String, Object> derived)
    {
        int actualQty = actual == null ? 0 : toInt(actual.get("quantity"));
        int derivedQty = derived == null ? 0 : toInt(derived.get("quantity"));
        BigDecimal actualCost = actual == null ? BigDecimal.ZERO : toDecimal(actual.get("cost_price"));
        BigDecimal derivedCost = derived == null ? BigDecimal.ZERO : toDecimal(derived.get("cost_price"));
        boolean quantityMismatch = actualQty != derivedQty;
        boolean existenceMismatch = (actual == null) != (derived == null);
        boolean costMismatch = actual != null && derived != null
                && actualCost != null && derivedCost != null
                && actualCost.subtract(derivedCost).abs().compareTo(new BigDecimal("0.0001")) > 0;
        return quantityMismatch || existenceMismatch || costMismatch;
    }

    private List<Long> parseIdList(Object raw)
    {
        if (raw == null)
        {
            return List.of();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty())
        {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String item : text.split(","))
        {
            String normalized = item == null ? "" : item.trim();
            if (!normalized.isEmpty())
            {
                result.add(Long.parseLong(normalized));
            }
        }
        return result;
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

    private Long resolveBatchId(Long batchId, boolean latestFailedOnly)
    {
        if (batchId != null)
        {
            return batchId;
        }
        try
        {
            String sql = latestFailedOnly
                    ? "SELECT id FROM job_run_batch WHERE status = 'FAILED' ORDER BY start_time DESC, id DESC LIMIT 1"
                    : "SELECT id FROM job_run_batch ORDER BY start_time DESC, id DESC LIMIT 1";
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value;
        }
        catch (DataAccessException ex)
        {
            return null;
        }
    }

    private boolean isStepDegraded(Map<String, Object> step)
    {
        Map<String, Object> parsed = parseRuntimePayload(step.get("errorMessage"));
        if (parsed.isEmpty())
        {
            return false;
        }
        if (Boolean.TRUE.equals(parsed.get("usedFallback")))
        {
            return true;
        }
        return toInt(parsed.get("failedCount")) > 0 || toInt(parsed.get("skippedCount")) > 0;
    }

    private Map<String, Object> evaluateDataIntegrity(List<Map<String, Object>> steps)
    {
        for (Map<String, Object> step : steps)
        {
            String stepName = String.valueOf(step.get("stepName"));
            Map<String, Object> parsed = parseRuntimePayload(step.get("errorMessage"));
            if (parsed.isEmpty())
            {
                continue;
            }
            if (Boolean.TRUE.equals(parsed.get("usedFallback")) && "sync-basic".equals(stepName))
            {
                return Map.of(
                        "status", "WARNING",
                        "category", "FALLBACK_BASIC",
                        "message", "基础标的信息本次使用了库内回退，今日结果可继续查看，但建议后续补跑 sync-basic 确认标的信息是否最新。");
            }
            if ("sync-daily".equals(stepName) && (toInt(parsed.get("failedCount")) > 0 || toInt(parsed.get("skippedCount")) > 0))
            {
                return Map.of(
                        "status", "BLOCKED",
                        "category", "PARTIAL_DAILY_SYNC",
                        "message", "日线同步存在失败或空结果标的，建议先核对失败标的后再继续运营。");
            }
            if ("evaluate-execution-feedback".equals(stepName) && (toInt(parsed.get("failedCount")) > 0 || toInt(parsed.get("skippedCount")) > 0))
            {
                return Map.of(
                        "status", "WARNING",
                        "category", "PARTIAL_EXECUTION_FEEDBACK",
                        "message", "执行反馈评估存在部分缺口，建议先核对异常项后再确认执行闭环。");
            }
        }
        return Map.of(
                "status", "READY",
                "category", "READY",
                "message", "盘后结果数据完整。");
    }

    private Map<String, Object> classifyJobStep(Map<String, Object> step)
    {
        String status = String.valueOf(step.get("status"));
        String stepName = String.valueOf(step.get("stepName"));
        String rawMessage = step.get("errorMessage") == null ? "" : String.valueOf(step.get("errorMessage"));
        Map<String, Object> parsed = parseRuntimePayload(rawMessage);
        String latestMessage = rawMessage;

        if (Boolean.TRUE.equals(parsed.get("usedFallback")))
        {
            return category("FALLBACK_BASIC", "warning", latestMessage, "基础标的信息来自库内回退，后续建议补跑 sync-basic。", stepName);
        }
        if (toInt(parsed.get("failedCount")) > 0 || toInt(parsed.get("skippedCount")) > 0)
        {
            return category("PARTIAL_DAILY_SYNC", "warning", latestMessage, "日线同步存在失败或空结果标的，建议先核对失败标的。", stepName);
        }
        if ("FAILED".equals(status) || "RETRYING".equals(status))
        {
            String normalized = rawMessage.toLowerCase();
            if (normalized.contains("proxyerror")
                    || normalized.contains("remotedisconnected")
                    || normalized.contains("connection aborted")
                    || normalized.contains("httpsconnectionpool"))
            {
                return category("UPSTREAM_NETWORK", "danger", latestMessage, "上游行情源或代理连接失败，建议稍后恢复批次。", stepName);
            }
            if ("evaluate-execution-feedback".equals(stepName))
            {
                return category("EXECUTION_FEEDBACK_GAP", "warning", latestMessage, "执行反馈评估失败，建议先核对执行闭环数据。", stepName);
            }
            return category("STEP_FAILED", "danger", latestMessage, "该步骤执行失败，建议优先恢复失败批次。", stepName);
        }
        return Map.of();
    }

    private Map<String, Object> parseRuntimePayload(Object raw)
    {
        if (raw == null)
        {
            return Map.of();
        }
        String text = String.valueOf(raw).trim();
        if (!text.startsWith("{") && !text.startsWith("["))
        {
            return Map.of();
        }
        try
        {
            Object parsed = JSON.parse(text);
            if (parsed instanceof Map<?, ?> map)
            {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
        }
        catch (Exception ignored)
        {
            // ignore malformed runtime payload
        }
        return Map.of();
    }

    private Map<String, Object> parseJsonObject(Object raw)
    {
        return parseRuntimePayload(raw);
    }

    private BigDecimal toDecimalOrDefault(Object value, BigDecimal defaultValue)
    {
        BigDecimal resolved = toDecimal(value);
        return resolved == null ? defaultValue : resolved;
    }

    private BigDecimal resolveRegimeWeight(Object rawWeights, String marketStatus)
    {
        Map<String, Object> weights = parseJsonObject(rawWeights);
        if (weights.isEmpty())
        {
            return BigDecimal.ONE;
        }
        Object rawValue = weights.get(marketStatus);
        if (rawValue == null)
        {
            rawValue = weights.get("default");
        }
        return toDecimalOrDefault(rawValue, BigDecimal.ONE);
    }

    private double percentageOfCapital(BigDecimal marketValue, BigDecimal capitalAnchor)
    {
        if (marketValue == null || capitalAnchor == null || capitalAnchor.compareTo(BigDecimal.ZERO) <= 0)
        {
            return 0D;
        }
        return marketValue
                .multiply(BigDecimal.valueOf(100))
                .divide(capitalAnchor, 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double roundDouble(double value)
    {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private long safeLong(Object value)
    {
        Long resolved = toLong(value);
        return resolved == null ? 0L : resolved;
    }

    private int priorityScore(String priority)
    {
        if ("P0".equalsIgnoreCase(priority))
        {
            return 0;
        }
        if ("P1".equalsIgnoreCase(priority))
        {
            return 1;
        }
        return 2;
    }

    private int actionTypePriority(String actionType)
    {
        if ("PIPELINE_RECOVERY".equalsIgnoreCase(actionType))
        {
            return 0;
        }
        if ("PIPELINE_WAIT".equalsIgnoreCase(actionType))
        {
            return 1;
        }
        return 2;
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

    private Map<String, Object> actionItem(
            String actionType,
            String priority,
            String targetType,
            Object targetId,
            String title,
            String reason,
            String path,
            Map<String, Object> query,
            Object badge)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("actionType", actionType);
        result.put("priority", priority);
        result.put("targetType", targetType);
        result.put("targetId", targetId);
        result.put("title", title);
        result.put("reason", reason);
        result.put("path", path);
        result.put("query", query == null ? Map.of() : query);
        result.put("targetPage", path);
        result.put("targetQuery", query == null ? Map.of() : query);
        result.put("badge", badge == null ? 0 : badge);
        result.put("status", "OPEN");
        result.put("sourcePage", "dashboard");
        result.put("sourceAction", actionSource(actionType));
        result.put("recommendedAction", actionType);
        return result;
    }

    private String actionSource(String actionType)
    {
        if ("DATA_INTEGRITY_REVIEW".equalsIgnoreCase(actionType))
        {
            return "dataIntegrityGate";
        }
        if ("PIPELINE_RECOVERY".equalsIgnoreCase(actionType) || "PIPELINE_WAIT".equalsIgnoreCase(actionType))
        {
            return "jobReadiness";
        }
        if ("EXECUTION_RECONCILIATION".equalsIgnoreCase(actionType)
                || "PARTIAL_EXECUTION".equalsIgnoreCase(actionType)
                || "PENDING_SIGNAL_EXECUTION".equalsIgnoreCase(actionType))
        {
            return "executionReconciliation";
        }
        if ("POSITION_RISK".equalsIgnoreCase(actionType) || "POSITION_SYNC_DIFF".equalsIgnoreCase(actionType))
        {
            return "positionRiskSummary";
        }
        if ("REVIEW_CANDIDATE".equalsIgnoreCase(actionType))
        {
            return "reviewCandidate";
        }
        return "dashboard";
    }

    private Map<String, Object> deepLink(
            String title,
            String path,
            Map<String, Object> query,
            Object badge,
            String variant,
            String reason)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", title);
        result.put("path", path);
        result.put("query", query == null ? Map.of() : query);
        result.put("badge", badge == null ? 0 : badge);
        result.put("variant", variant);
        result.put("reason", reason);
        return result;
    }

    private Map<String, Object> actionLink(String title, String path, Map<String, Object> query)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", title);
        result.put("path", path);
        result.put("query", query == null ? Map.of() : query);
        return result;
    }

    private String deriveSignalExecutionStatus(Map<String, Object> signal)
    {
        String feedbackStatus = valueOrDefault(signal.get("feedback_status"), "");
        if ("MISSED".equalsIgnoreCase(feedbackStatus))
        {
            return "MISSED";
        }
        if (toInt(signal.get("is_execute")) == 1 || "EXECUTED".equalsIgnoreCase(feedbackStatus))
        {
            return "EXECUTED";
        }
        return "PENDING";
    }

    private String deriveSignalHeadline(Object signalType, String executionStatus)
    {
        String direction = valueOrDefault(signalType, "SIGNAL");
        if ("EXECUTED".equalsIgnoreCase(executionStatus))
        {
            return direction + " 信号已闭环";
        }
        if ("MISSED".equalsIgnoreCase(executionStatus))
        {
            return direction + " 信号待复盘";
        }
        return direction + " 信号待执行";
    }

    private String deriveSignalExecutionLine(Map<String, Object> signal, String executionStatus)
    {
        if ("EXECUTED".equalsIgnoreCase(executionStatus))
        {
            return "当前信号已经形成执行闭环，可以直接进入复盘确认执行质量。";
        }
        if ("MISSED".equalsIgnoreCase(executionStatus))
        {
            return "当前信号被标记为漏执行或异常，建议优先回到执行回写页核对原因。";
        }
        return "当前信号仍处于待执行/待确认状态，建议先核对真实成交再形成结论。";
    }

    private Map<String, Object> enrichAsyncJobRow(Map<String, Object> source)
    {
        Map<String, Object> row = toCamelCaseMap(source);
        Map<String, Object> normalizedPayload = parseJsonObject(row.get("normalizedPayload"));
        Map<String, Object> requestPayload = parseJsonObject(row.get("requestPayload"));
        String taskCode = valueOrDefault(normalizedPayload.get("taskCode"), valueOrDefault(row.get("jobType"), "dispatch"));
        String triggerMode = resolveTriggerMode(normalizedPayload, row.get("actor"));
        row.put("taskCode", taskCode);
        row.put("taskName", resolveDispatchTaskName(taskCode, row.get("jobType")));
        row.put("triggerMode", triggerMode);
        row.put("triggerModeLabel", triggerModeLabel(triggerMode));
        row.put("scopeSummary", resolveScopeSummary(normalizedPayload));
        row.put("timeRangeSummary", resolveTimeRangeSummary(normalizedPayload, requestPayload));
        row.put("startedAt", row.get("startTime") == null ? row.get("createTime") : row.get("startTime"));
        row.put("resultSummary", resolveAsyncResultSummary(row));
        row.put("errorSummary", valueOrDefault(row.get("errorMessage"), ""));
        return row;
    }

    private Map<String, Object> enrichDispatchDefinition(Map<String, Object> source)
    {
        Map<String, Object> row = new LinkedHashMap<>(source);
        String invokeTarget = valueOrDefault(row.get("invokeTarget"), "");
        row.put("taskCode", invokeTarget);
        row.put("taskName", resolveDefinitionTaskName(row));
        row.put("triggerModes", resolveDefinitionTriggerModes(invokeTarget));
        row.put("autoEnabled", "0".equals(String.valueOf(row.get("status"))));
        row.put("nextFireTime", resolveNextFireTime(row.get("cronExpression"), row.get("status")));
        row.put("defaultScope", resolveDefinitionScope(invokeTarget));
        row.put("defaultTimeRange", resolveDefinitionTimeRange(invokeTarget));
        row.put("defaultStrategy", resolveDefinitionStrategy(invokeTarget));
        row.put("latestRunSummary", resolveLatestDefinitionRunSummary(invokeTarget));
        return row;
    }

    private Map<String, Object> enrichAsyncShardRow(Map<String, Object> source)
    {
        Map<String, Object> row = new LinkedHashMap<>(source);
        Map<String, Object> payload = parseJsonObject(row.get("payload"));
        List<?> symbols = payload.get("symbols") instanceof List<?> list ? list : List.of();
        row.put("symbolsPreview", symbols.stream().map(String::valueOf).limit(8).toList());
        row.put("symbolsText", symbols.stream().map(String::valueOf).limit(8).reduce((left, right) -> left + ", " + right).orElse(""));
        return row;
    }

    private List<Map<String, Object>> asyncDispatchHistoryRows(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try
        {
            return jdbcTemplate.queryForList(
                    "SELECT id, job_key, job_type, requested_mode, resolved_mode, status, actor, " +
                            "planned_shard_count, completed_shard_count, failed_shard_count, cancel_requested, " +
                            "error_message, request_payload, normalized_payload, create_time, start_time, end_time " +
                            "FROM quant_async_job ORDER BY create_time DESC, id DESC LIMIT ?",
                    safeLimit)
                    .stream()
                    .map(this::enrichAsyncJobRow)
                    .map(row -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("dispatchId", row.get("id"));
                        result.put("taskCode", row.get("taskCode"));
                        result.put("taskName", row.get("taskName"));
                        result.put("triggerMode", row.get("triggerMode"));
                        result.put("triggerModeLabel", row.get("triggerModeLabel"));
                        result.put("actor", row.get("actor"));
                        result.put("triggerSource", row.get("actor"));
                        result.put("jobId", row.get("id"));
                        result.put("batchId", null);
                        result.put("status", row.get("status"));
                        result.put("startedAt", row.get("startedAt"));
                        result.put("finishedAt", row.get("endTime"));
                        result.put("scopeSummary", row.get("scopeSummary"));
                        result.put("timeRangeSummary", row.get("timeRangeSummary"));
                        result.put("resultSummary", row.get("resultSummary"));
                        result.put("errorSummary", row.get("errorSummary"));
                        return result;
                    })
                    .toList();
        }
        catch (DataAccessException ex)
        {
            log.warn("Query async dispatch history failed, fallback empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> quartzDispatchHistoryRows(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try
        {
            return jdbcTemplate.queryForList(
                    "SELECT job_log_id, job_name, invoke_target, job_message, status, exception_info, create_time " +
                            "FROM sys_job_log WHERE invoke_target LIKE 'quantRoadTask.%' ORDER BY create_time DESC, job_log_id DESC LIMIT ?",
                    safeLimit)
                    .stream()
                    .map(this::toCamelCaseMap)
                    .map(row -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        String invokeTarget = valueOrDefault(row.get("invokeTarget"), "");
                        result.put("dispatchId", row.get("jobLogId"));
                        result.put("taskCode", invokeTarget);
                        result.put("taskName", resolveDispatchTaskName(invokeTarget, row.get("jobName")));
                        result.put("triggerMode", "auto");
                        result.put("triggerModeLabel", triggerModeLabel("auto"));
                        result.put("actor", "quartz");
                        result.put("triggerSource", "quartz");
                        result.put("jobId", null);
                        result.put("batchId", null);
                        result.put("status", "0".equals(String.valueOf(row.get("status"))) ? "SUCCESS" : "FAILED");
                        result.put("startedAt", row.get("createTime"));
                        result.put("finishedAt", row.get("createTime"));
                        result.put("scopeSummary", resolveDefinitionScope(invokeTarget));
                        result.put("timeRangeSummary", resolveDefinitionTimeRange(invokeTarget));
                        result.put("resultSummary", valueOrDefault(row.get("jobMessage"), "Quartz 调度已执行"));
                        result.put("errorSummary", valueOrDefault(row.get("exceptionInfo"), ""));
                        return result;
                    })
                    .toList();
        }
        catch (DataAccessException ex)
        {
            return List.of();
        }
    }

    private boolean matchesOptional(Object value, String expected)
    {
        if (expected == null || expected.isBlank())
        {
            return true;
        }
        return expected.equalsIgnoreCase(String.valueOf(value));
    }

    private String resolveTriggerMode(Map<String, Object> normalizedPayload, Object actor)
    {
        String triggerMode = valueOrDefault(normalizedPayload.get("triggerMode"), "");
        if (!triggerMode.isBlank())
        {
            return triggerMode.toLowerCase();
        }
        String actorText = valueOrDefault(actor, "");
        if ("quartz".equalsIgnoreCase(actorText))
        {
            return "auto";
        }
        if (actorText.toLowerCase().contains("recover") || actorText.toLowerCase().contains("ops"))
        {
            return "recovery";
        }
        return "manual";
    }

    private String triggerModeLabel(String triggerMode)
    {
        if ("auto".equalsIgnoreCase(triggerMode))
        {
            return "自动触发";
        }
        if ("recovery".equalsIgnoreCase(triggerMode))
        {
            return "补偿触发";
        }
        return "手工触发";
    }

    private String resolveScopeSummary(Map<String, Object> normalizedPayload)
    {
        String scopeType = valueOrDefault(normalizedPayload.get("scopeType"), "");
        String scopePoolCode = valueOrDefault(normalizedPayload.get("scopePoolCode"), "");
        List<?> symbols = normalizedPayload.get("symbols") instanceof List<?> list ? list : List.of();
        int symbolCount = symbols.size();
        if (!scopeType.isBlank() && !scopePoolCode.isBlank())
        {
            return scopeType + " / " + scopePoolCode + " / " + symbolCount + " 个标的";
        }
        if (!scopeType.isBlank())
        {
            return scopeType + " / " + symbolCount + " 个标的";
        }
        return symbolCount > 0 ? symbolCount + " 个标的" : "未记录范围";
    }

    private String resolveTimeRangeSummary(Map<String, Object> normalizedPayload, Map<String, Object> requestPayload)
    {
        String startDate = valueOrDefault(normalizedPayload.get("startDate"), valueOrDefault(requestPayload.get("strategyBacktestStartDate"), ""));
        String endDate = valueOrDefault(normalizedPayload.get("endDate"), valueOrDefault(requestPayload.get("endDate"), ""));
        if (startDate.isBlank() && endDate.isBlank())
        {
            return "未记录时间范围";
        }
        String resolvedEnd = endDate.isBlank() ? LocalDate.now().toString() : endDate;
        return startDate + " ~ " + resolvedEnd;
    }

    private String resolveAsyncResultSummary(Map<String, Object> row)
    {
        String status = valueOrDefault(row.get("status"), "UNKNOWN");
        if ("FAILED".equalsIgnoreCase(status))
        {
            return valueOrDefault(row.get("errorMessage"), "调度失败");
        }
        if ("RUNNING".equalsIgnoreCase(status))
        {
            return "调度运行中";
        }
        if ("QUEUED".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status))
        {
            return "等待执行";
        }
        return "调度已完成";
    }

    private String resolveDispatchTaskName(String taskCode, Object fallback)
    {
        String normalized = valueOrDefault(taskCode, "");
        if (normalized.contains("fullDailyAsync") || normalized.contains("run-portfolio") || normalized.contains("runPortfolioAsync"))
        {
            return "盘后主流程";
        }
        if (normalized.contains("monthlyReport"))
        {
            return "月度策略报告";
        }
        if (normalized.contains("shadowCompare"))
        {
            return "影子策略对比";
        }
        if (normalized.contains("syncValuation"))
        {
            return "估值快照";
        }
        if (normalized.contains("evaluateMarket"))
        {
            return "市场状态评估";
        }
        if (normalized.contains("evaluateExecutionFeedback"))
        {
            return "执行反馈评估";
        }
        if (normalized.contains("canaryEvaluate"))
        {
            return "Canary 评估";
        }
        return valueOrDefault(fallback, "量化调度");
    }

    private String resolveDefinitionTaskName(Map<String, Object> row)
    {
        String jobName = valueOrDefault(row.get("jobName"), "");
        if (!jobName.isBlank())
        {
            return jobName.replace("Quant ", "").trim();
        }
        return resolveDispatchTaskName(valueOrDefault(row.get("invokeTarget"), ""), jobName);
    }

    private List<String> resolveDefinitionTriggerModes(String invokeTarget)
    {
        if (invokeTarget.contains("fullDailyAsync") || invokeTarget.contains("runPortfolioAsync"))
        {
            return List.of("manual", "auto", "recovery");
        }
        return List.of("auto");
    }

    private Object resolveNextFireTime(Object cronExpression, Object status)
    {
        if (!"0".equals(String.valueOf(status)))
        {
            return null;
        }
        String cron = valueOrDefault(cronExpression, "");
        if (cron.isBlank())
        {
            return null;
        }
        try
        {
            CronExpression expression = new CronExpression(cron);
            java.util.Date next = expression.getNextValidTimeAfter(new java.util.Date());
            if (next == null)
            {
                return null;
            }
            return LocalDateTime.ofInstant(next.toInstant(), ZoneId.systemDefault()).toString().replace('T', ' ');
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private String resolveDefinitionScope(String invokeTarget)
    {
        if (invokeTarget.contains("fullDailyAsync") || invokeTarget.contains("runPortfolioAsync"))
        {
            return "按当前统一执行范围";
        }
        if (invokeTarget.contains("syncValuation"))
        {
            return "预设指数范围";
        }
        return "系统默认范围";
    }

    private String resolveDefinitionTimeRange(String invokeTarget)
    {
        if (invokeTarget.contains("monthlyReport") || invokeTarget.contains("shadowCompare") || invokeTarget.contains("canaryEvaluate"))
        {
            return "最近 6 个月";
        }
        if (invokeTarget.contains("fullDailyAsync") || invokeTarget.contains("runPortfolioAsync"))
        {
            return "最近 5 年 ~ 今日";
        }
        return "按任务默认配置";
    }

    private String resolveDefinitionStrategy(String invokeTarget)
    {
        if (invokeTarget.contains("shadowCompare"))
        {
            return "基线策略 vs 候选策略";
        }
        if (invokeTarget.contains("canaryEvaluate"))
        {
            return "Canary 基线/候选策略";
        }
        if (invokeTarget.contains("fullDailyAsync") || invokeTarget.contains("runPortfolioAsync"))
        {
            return "当前启用策略组合";
        }
        return "任务默认策略";
    }

    private String resolveLatestDefinitionRunSummary(String invokeTarget)
    {
        List<Map<String, Object>> rows = dispatchHistory(1, 5, invokeTarget, null).get("rows") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
        if (rows.isEmpty())
        {
            return "暂无最近执行记录";
        }
        Map<String, Object> latest = rows.get(0);
        return valueOrDefault(latest.get("status"), "UNKNOWN") + " / " + valueOrDefault(latest.get("startedAt"), "-");
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

    private Map<String, Object> category(String code, String severity, String latestMessage, String suggestedAction, String stepName)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", code);
        result.put("severity", severity);
        result.put("latestMessage", latestMessage);
        result.put("suggestedAction", suggestedAction);
        result.put("stepName", stepName);
        return result;
    }

    private Map<String, Object> hint(
            String code,
            String level,
            String title,
            String summary,
            String suggestedAction,
            String targetPage,
            String targetParams,
            boolean autoRecoverable)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("level", level);
        result.put("title", title);
        result.put("summary", summary);
        result.put("suggestedAction", suggestedAction);
        result.put("targetPage", targetPage);
        result.put("targetParams", targetParams);
        result.put("autoRecoverable", autoRecoverable);
        return result;
    }
}
