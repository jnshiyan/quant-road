package com.ruoyi.web.service.quant;

import java.sql.SQLException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.domain.quant.QuantGovernanceDecisionRequest;

@Service
public class QuantRoadGovernanceService
{
    private final JdbcTemplate jdbcTemplate;
    private final QuantRoadPythonService quantRoadPythonService;

    public QuantRoadGovernanceService(JdbcTemplate jdbcTemplate, QuantRoadPythonService quantRoadPythonService)
    {
        this.jdbcTemplate = jdbcTemplate;
        this.quantRoadPythonService = quantRoadPythonService;
    }

    @PostConstruct
    void initializeGovernanceSchema()
    {
        ensureGovernanceSchema();
    }

    public Map<String, Object> shadowCompareSummary(Long baselineStrategyId, Long candidateStrategyId, Integer months)
    {
        Map<String, Object> payload = shadowComparePayload(baselineStrategyId, candidateStrategyId, months);
        Map<String, Object> summary = childMap(payload, "summary");
        int comparableMonths = toInt(summary.get("comparable_months"));
        int betterAnnualMonths = toInt(summary.get("candidate_better_annual_months"));
        int lowerDrawdownMonths = toInt(summary.get("candidate_lower_drawdown_months"));
        int higherWinRateMonths = toInt(summary.get("candidate_higher_win_rate_months"));
        int lowerInvalidRateMonths = toInt(summary.get("candidate_lower_invalid_rate_months"));

        String recommendation = deriveRecommendation(
                comparableMonths,
                betterAnnualMonths,
                lowerDrawdownMonths,
                higherWinRateMonths,
                lowerInvalidRateMonths);
        String governanceAction = deriveGovernanceAction(recommendation);
        String confidenceLevel = deriveConfidenceLevel(
                comparableMonths,
                betterAnnualMonths,
                lowerDrawdownMonths,
                higherWinRateMonths,
                lowerInvalidRateMonths);

        Map<String, Object> baseline = childMap(payload, "baseline");
        Map<String, Object> candidate = childMap(payload, "candidate");
        Map<String, Object> latestCanary = latestCanary(baselineStrategyId, candidateStrategyId, months);
        List<String> coreEvidences = buildCoreEvidences(
                comparableMonths,
                betterAnnualMonths,
                lowerDrawdownMonths,
                higherWinRateMonths,
                lowerInvalidRateMonths);
        List<String> riskNotes = buildRiskNotes(comparableMonths, latestCanary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseline", baseline);
        result.put("candidate", candidate);
        result.put("months", payload.get("months"));
        result.put("generatedAt", payload.get("generated_at"));
        result.put("summary", toCamelCaseMap(summary));
        result.put("recommendation", recommendation);
        result.put("governanceAction", governanceAction);
        result.put("confidenceLevel", confidenceLevel);
        result.put("recommendationReason", buildRecommendationReason(candidate, recommendation, comparableMonths, coreEvidences));
        result.put("coreEvidences", coreEvidences);
        result.put("riskNotes", riskNotes);
        result.put("latestCanary", latestCanary);
        List<Map<String, Object>> history = governanceHistory(baselineStrategyId, candidateStrategyId, 5);
        result.put("latestDecision", history.isEmpty() ? Map.of() : history.get(0));
        return result;
    }

    public Map<String, Object> shadowCompareCharts(Long baselineStrategyId, Long candidateStrategyId, Integer months)
    {
        Map<String, Object> payload = shadowComparePayload(baselineStrategyId, candidateStrategyId, months);
        List<Map<String, Object>> monthsData = childList(payload, "months_data");
        List<String> categories = new ArrayList<>();
        List<Double> annualDelta = new ArrayList<>();
        List<Double> drawdownDelta = new ArrayList<>();
        List<Double> winRateDelta = new ArrayList<>();
        List<Double> invalidRateDelta = new ArrayList<>();
        List<Double> baselineAnnualSeries = new ArrayList<>();
        List<Double> candidateAnnualSeries = new ArrayList<>();

        for (int i = monthsData.size() - 1; i >= 0; i--)
        {
            Map<String, Object> item = monthsData.get(i);
            categories.add(String.valueOf(item.get("month")));
            Map<String, Object> delta = childMap(item, "delta");
            Map<String, Object> baseline = childMap(item, "baseline");
            Map<String, Object> candidate = childMap(item, "candidate");
            annualDelta.add(toDouble(delta.get("avg_annual_return")));
            drawdownDelta.add(toDouble(delta.get("avg_max_drawdown")));
            winRateDelta.add(toDouble(delta.get("avg_win_rate")));
            invalidRateDelta.add(toDouble(delta.get("invalid_rate")));
            baselineAnnualSeries.add(toDouble(baseline.get("avg_annual_return")));
            candidateAnnualSeries.add(toDouble(candidate.get("avg_annual_return")));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", categories);
        result.put("annualDeltaSeries", annualDelta);
        result.put("drawdownDeltaSeries", drawdownDelta);
        result.put("winRateDeltaSeries", winRateDelta);
        result.put("invalidRateDeltaSeries", invalidRateDelta);
        result.put("baselineAnnualSeries", baselineAnnualSeries);
        result.put("candidateAnnualSeries", candidateAnnualSeries);
        result.put("generatedAt", payload.get("generated_at"));
        return result;
    }

    public Map<String, Object> shadowCompareApplicability(Long baselineStrategyId, Long candidateStrategyId, Integer months)
    {
        Map<String, Object> payload = shadowComparePayload(baselineStrategyId, candidateStrategyId, months);
        Map<String, Object> baseline = childMap(payload, "baseline");
        Map<String, Object> candidate = childMap(payload, "candidate");
        Map<String, Object> summary = childMap(payload, "summary");
        int comparableMonths = toInt(summary.get("comparable_months"));
        int lowerDrawdownMonths = toInt(summary.get("candidate_lower_drawdown_months"));
        int higherWinRateMonths = toInt(summary.get("candidate_higher_win_rate_months"));

        List<String> strengths = new ArrayList<>();
        if (toInt(summary.get("candidate_better_annual_months")) > 0)
        {
            strengths.add("候选策略在可比月份内展现了更高的平均年化收益。");
        }
        if (lowerDrawdownMonths > 0)
        {
            strengths.add("候选策略在多个月份里回撤更低，更适合作为稳健替代。");
        }
        if (higherWinRateMonths > 0)
        {
            strengths.add("候选策略近期胜率更高，说明触发条件可能更贴近当前环境。");
        }

        List<String> riskNotes = buildRiskNotes(comparableMonths, latestCanary(baselineStrategyId, candidateStrategyId, months));
        if (lowerDrawdownMonths == 0)
        {
            riskNotes.add("候选策略尚未证明自己在回撤控制上稳定占优。");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baselineStrategy", baseline);
        result.put("candidateStrategy", candidate);
        result.put("applicabilityConclusion", comparableMonths >= 3
                ? "候选策略已具备进入正式治理评审的样本基础。"
                : "样本窗口偏短，建议继续观察后再做替换决策。");
        result.put("strengths", strengths);
        result.put("riskNotes", riskNotes);
        result.put("preferredReviewLevel", "governance");
        return result;
    }

    public List<Map<String, Object>> shadowReviewLinks(Long baselineStrategyId, Long candidateStrategyId, Integer months)
    {
        Map<String, Object> payload = shadowComparePayload(baselineStrategyId, candidateStrategyId, months);
        Map<String, Object> baseline = childMap(payload, "baseline");
        Map<String, Object> candidate = childMap(payload, "candidate");
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map<String, Object> item : childList(payload, "months_data"))
        {
            String month = String.valueOf(item.get("month"));
            Map<String, Object> delta = childMap(item, "delta");
            if (delta.isEmpty())
            {
                continue;
            }
            String sourceAction = toDouble(delta.get("avg_annual_return")) >= 0
                    ? "shadowCandidateStrength"
                    : "shadowCandidateWeakness";
            links.add(reviewLink(
                    month + " 候选策略复盘",
                    "查看候选策略在该月的行为与收益来源",
                    candidate.get("strategy_id"),
                    month,
                    sourceAction));
            links.add(reviewLink(
                    month + " 基线策略复盘",
                    "回看基线策略在同月是否出现结构性失效",
                    baseline.get("strategy_id"),
                    month,
                    "shadowBaselineReference"));
        }
        return links;
    }

    public Map<String, Object> submitGovernanceDecision(QuantGovernanceDecisionRequest request)
    {
        if (request == null)
        {
            throw new ServiceException("governance decision request is required");
        }
        if (request.getBaselineStrategyId() == null || request.getCandidateStrategyId() == null)
        {
            throw new ServiceException("baselineStrategyId and candidateStrategyId are required");
        }
        if (request.getBaselineStrategyId().equals(request.getCandidateStrategyId()))
        {
            throw new ServiceException("candidateStrategyId must be different from baselineStrategyId");
        }

        return withGovernanceSchemaRetry(() ->
        {
            jdbcTemplate.update(
                    "INSERT INTO quant_governance_decision (" +
                            " baseline_strategy_id, candidate_strategy_id, months, system_recommendation, governance_action, " +
                            " confidence_level, approval_status, decision_source, effective_from, actor, remark, core_evidences, risk_notes" +
                            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB))",
                    request.getBaselineStrategyId(),
                    request.getCandidateStrategyId(),
                    request.getMonths() == null ? 6 : request.getMonths(),
                    normalizeText(request.getSystemRecommendation()),
                    normalizeText(request.getGovernanceAction()),
                    normalizeText(request.getConfidenceLevel()),
                    normalizeText(request.getApprovalStatus()),
                    normalizeText(request.getDecisionSource()),
                    parseDate(request.getEffectiveFrom()),
                    request.getActor(),
                    request.getRemark(),
                    JSON.toJSONString(request.getCoreEvidences() == null ? List.of() : request.getCoreEvidences()),
                    JSON.toJSONString(request.getRiskNotes() == null ? List.of() : request.getRiskNotes()));

            Long decisionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM quant_governance_decision ORDER BY id DESC LIMIT 1",
                    Long.class);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("decisionId", decisionId);
            result.put("baselineStrategyId", request.getBaselineStrategyId());
            result.put("candidateStrategyId", request.getCandidateStrategyId());
            result.put("governanceAction", normalizeText(request.getGovernanceAction()));
            result.put("approvalStatus", normalizeText(request.getApprovalStatus()));
            result.put("history", governanceHistory(request.getBaselineStrategyId(), request.getCandidateStrategyId(), 10));
            return result;
        });
    }

    public List<Map<String, Object>> governanceHistory(Long baselineStrategyId, Long candidateStrategyId, Integer limit)
    {
        int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 100));
        return withGovernanceSchemaRetry(() -> jdbcTemplate.queryForList(
                "SELECT id, baseline_strategy_id, candidate_strategy_id, months, system_recommendation, governance_action, " +
                        " confidence_level, approval_status, decision_source, effective_from, actor, remark, core_evidences, risk_notes, create_time " +
                        "FROM quant_governance_decision " +
                        "WHERE baseline_strategy_id = ? AND candidate_strategy_id = ? " +
                        "ORDER BY create_time DESC, id DESC LIMIT ?",
                baselineStrategyId,
                candidateStrategyId,
                safeLimit)
                .stream()
                .map(this::toGovernanceHistoryRow)
                .toList());
    }

    private void ensureGovernanceSchema()
    {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS quant_governance_decision (" +
                        " id BIGSERIAL PRIMARY KEY," +
                        " baseline_strategy_id BIGINT NOT NULL REFERENCES strategy_config(id)," +
                        " candidate_strategy_id BIGINT NOT NULL REFERENCES strategy_config(id)," +
                        " months INT NOT NULL DEFAULT 6 CHECK (months > 0)," +
                        " system_recommendation VARCHAR(40) NOT NULL," +
                        " governance_action VARCHAR(30) NOT NULL," +
                        " confidence_level VARCHAR(20)," +
                        " approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'," +
                        " decision_source VARCHAR(40) NOT NULL DEFAULT 'shadow_compare'," +
                        " effective_from DATE," +
                        " actor VARCHAR(80)," +
                        " remark TEXT," +
                        " core_evidences JSONB NOT NULL DEFAULT '[]'::jsonb," +
                        " risk_notes JSONB NOT NULL DEFAULT '[]'::jsonb," +
                        " create_time TIMESTAMP DEFAULT NOW()" +
                        ")");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_quant_governance_decision_pair_time " +
                        "ON quant_governance_decision(baseline_strategy_id, candidate_strategy_id, create_time DESC)");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_quant_governance_decision_action_time " +
                        "ON quant_governance_decision(governance_action, create_time DESC)");
    }

    private <T> T withGovernanceSchemaRetry(Supplier<T> supplier)
    {
        ensureGovernanceSchema();
        try
        {
            return supplier.get();
        }
        catch (DataAccessException ex)
        {
            if (!isMissingGovernanceTable(ex))
            {
                throw ex;
            }
            ensureGovernanceSchema();
            return supplier.get();
        }
    }

    private boolean isMissingGovernanceTable(DataAccessException ex)
    {
        Throwable current = ex;
        while (current != null)
        {
            if (current instanceof SQLException sqlException && "42P01".equals(sqlException.getSQLState()))
            {
                return true;
            }
            String message = current.getMessage();
            if (message != null
                    && message.contains("quant_governance_decision")
                    && (message.contains("does not exist") || message.contains("不存在")))
            {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Map<String, Object> shadowComparePayload(Long baselineStrategyId, Long candidateStrategyId, Integer months)
    {
        try
        {
            Object parsed = JSON.parse(quantRoadPythonService.shadowCompareJson(
                    baselineStrategyId == null ? 1L : baselineStrategyId,
                    candidateStrategyId,
                    months == null ? 6 : months));
            if (parsed instanceof Map<?, ?> map)
            {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
        }
        catch (Exception ex)
        {
            throw new ServiceException("Invalid shadow compare payload").setDetailMessage(ex.toString());
        }
        throw new ServiceException("Invalid shadow compare payload");
    }

    private Map<String, Object> latestCanary(Long baselineStrategyId, Long candidateStrategyId, Integer months)
    {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT run_date, recommendation, remark " +
                        "FROM canary_run_log " +
                        "WHERE baseline_strategy_id = ? AND candidate_strategy_id = ? AND months = ? " +
                        "ORDER BY run_date DESC, id DESC LIMIT 1",
                baselineStrategyId == null ? 1L : baselineStrategyId,
                candidateStrategyId,
                months == null ? 6 : months);
        if (rows.isEmpty())
        {
            return Map.of();
        }
        return toCamelCaseMap(rows.get(0));
    }

    private String deriveRecommendation(
            int comparableMonths,
            int betterAnnualMonths,
            int lowerDrawdownMonths,
            int higherWinRateMonths,
            int lowerInvalidRateMonths)
    {
        if (comparableMonths <= 0)
        {
            return "INSUFFICIENT_DATA";
        }
        boolean strongAnnual = betterAnnualMonths >= Math.max(1, comparableMonths - 1);
        boolean strongRisk = lowerDrawdownMonths >= Math.max(1, comparableMonths - 1)
                && lowerInvalidRateMonths >= Math.max(1, comparableMonths / 2);
        boolean decentWinRate = higherWinRateMonths >= Math.max(1, comparableMonths / 2);
        if (strongAnnual && strongRisk && decentWinRate)
        {
            return "PROMOTE_CANDIDATE";
        }
        if (betterAnnualMonths > 0 || lowerDrawdownMonths > 0 || higherWinRateMonths > 0)
        {
            return "OBSERVE";
        }
        return "KEEP_BASELINE";
    }

    private String deriveGovernanceAction(String recommendation)
    {
        if ("PROMOTE_CANDIDATE".equals(recommendation))
        {
            return "REPLACE";
        }
        if ("OBSERVE".equals(recommendation) || "INSUFFICIENT_DATA".equals(recommendation))
        {
            return "OBSERVE";
        }
        return "KEEP";
    }

    private String deriveConfidenceLevel(
            int comparableMonths,
            int betterAnnualMonths,
            int lowerDrawdownMonths,
            int higherWinRateMonths,
            int lowerInvalidRateMonths)
    {
        if (comparableMonths <= 0)
        {
            return "LOW";
        }
        int wins = betterAnnualMonths + lowerDrawdownMonths + higherWinRateMonths + lowerInvalidRateMonths;
        int maxWins = comparableMonths * 4;
        double ratio = maxWins == 0 ? 0D : (double) wins / (double) maxWins;
        if (ratio >= 0.7D && comparableMonths >= 2)
        {
            return "HIGH";
        }
        if (ratio >= 0.4D)
        {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<String> buildCoreEvidences(
            int comparableMonths,
            int betterAnnualMonths,
            int lowerDrawdownMonths,
            int higherWinRateMonths,
            int lowerInvalidRateMonths)
    {
        List<String> evidences = new ArrayList<>();
        evidences.add("可比月份 " + comparableMonths + " 个");
        evidences.add("候选策略年化更优 " + betterAnnualMonths + "/" + comparableMonths + " 月");
        evidences.add("候选策略回撤更低 " + lowerDrawdownMonths + "/" + comparableMonths + " 月");
        evidences.add("候选策略胜率更高 " + higherWinRateMonths + "/" + comparableMonths + " 月");
        evidences.add("候选策略失效率更低 " + lowerInvalidRateMonths + "/" + comparableMonths + " 月");
        return evidences;
    }

    private List<String> buildRiskNotes(int comparableMonths, Map<String, Object> latestCanary)
    {
        List<String> riskNotes = new ArrayList<>();
        if (comparableMonths < 3)
        {
            riskNotes.add("当前可比月份不足 3 个，结论更适合作为观察意见而非直接替换。");
        }
        if (!latestCanary.isEmpty() && latestCanary.get("remark") != null)
        {
            riskNotes.add(String.valueOf(latestCanary.get("remark")));
        }
        return riskNotes;
    }

    private String buildRecommendationReason(
            Map<String, Object> candidate,
            String recommendation,
            int comparableMonths,
            List<String> coreEvidences)
    {
        String candidateName = candidate.get("strategy_name") == null
                ? "候选策略"
                : String.valueOf(candidate.get("strategy_name"));
        if ("PROMOTE_CANDIDATE".equals(recommendation))
        {
            return "候选策略 " + candidateName + " 在最近 " + comparableMonths + " 个可比月份中持续展示更优收益/风险比，建议进入替换决策。";
        }
        if ("OBSERVE".equals(recommendation))
        {
            return "候选策略 " + candidateName + " 已出现阶段性优势，但证据仍不足以直接替换，建议继续观察。";
        }
        if ("INSUFFICIENT_DATA".equals(recommendation))
        {
            return "当前样本不足，先积累更多月份数据再做治理决策。";
        }
        return "当前核心证据仍支持保留基线策略。";
    }

    private Map<String, Object> reviewLink(String title, String summary, Object strategyId, String month, String sourceAction)
    {
        YearMonth yearMonth = YearMonth.parse(month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", title);
        result.put("summary", summary);
        result.put("reviewLevel", "strategy");
        result.put("strategyId", strategyId);
        result.put("dateRangeStart", start);
        result.put("dateRangeEnd", end);
        result.put("sourcePage", "shadow");
        result.put("sourceAction", sourceAction);
        return result;
    }

    private Map<String, Object> toGovernanceHistoryRow(Map<String, Object> row)
    {
        Map<String, Object> result = toCamelCaseMap(row);
        result.put("coreEvidences", parseStringList(row.get("core_evidences")));
        result.put("riskNotes", parseStringList(row.get("risk_notes")));
        return result;
    }

    private List<String> parseStringList(Object raw)
    {
        if (raw == null)
        {
            return List.of();
        }
        try
        {
            Object parsed = JSON.parse(String.valueOf(raw));
            if (parsed instanceof List<?> list)
            {
                return list.stream().map(String::valueOf).toList();
            }
        }
        catch (Exception ignored)
        {
            // ignore malformed list payload
        }
        return List.of(String.valueOf(raw));
    }

    private Map<String, Object> childMap(Map<String, Object> source, String key)
    {
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map)
        {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((childKey, childValue) -> result.put(String.valueOf(childKey), childValue));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> childList(Map<String, Object> source, String key)
    {
        Object value = source.get(key);
        if (!(value instanceof List<?> list))
        {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list)
        {
            if (item instanceof Map<?, ?> map)
            {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((childKey, childValue) -> row.put(String.valueOf(childKey), childValue));
                result.add(row);
            }
        }
        return result;
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

    private String normalizeText(String value)
    {
        return value == null ? null : value.trim().toUpperCase();
    }

    private LocalDate parseDate(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        return LocalDate.parse(value.trim());
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
}
