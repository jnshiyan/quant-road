package com.ruoyi.web.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.web.domain.quant.QuantReviewConclusionRequest;

class QuantRoadReviewServiceTest
{
    @Test
    void reviewCasesBuildsFormalCaseRowsFromCandidates()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadGovernanceService governanceService = mock(QuantRoadGovernanceService.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadReviewService service = new QuantRoadReviewService(jdbcTemplate, governanceService, queryService);

        when(queryService.reviewCandidates(12)).thenReturn(List.of(caseCandidateRow()));
        when(jdbcTemplate.queryForList(anyString(), eq("trade"), eq("trade"), eq(null), eq(null), eq(null), eq(null), eq(12)))
                .thenReturn(List.of(caseListRow()));

        List<Map<String, Object>> payload = service.reviewCases("trade", null, null, 12);

        assertEquals(1, payload.size());
        assertEquals(301L, payload.get(0).get("caseId"));
        assertEquals("ETF", payload.get(0).get("assetType"));
        assertEquals("ETF_REVIEW", payload.get(0).get("caseType"));
        assertEquals("etf_pool", payload.get(0).get("scopeType"));
    }

    @Test
    void reviewCasesSupportsCaseTypeAndAssetTypeFilters()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadGovernanceService governanceService = mock(QuantRoadGovernanceService.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadReviewService service = new QuantRoadReviewService(jdbcTemplate, governanceService, queryService);

        when(queryService.reviewCandidates(6)).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq("trade"), eq("trade"), eq("ETF_REVIEW"), eq("ETF_REVIEW"), eq("ETF"), eq("ETF"), eq(6)))
                .thenReturn(List.of(caseListRow()));

        List<Map<String, Object>> payload = service.reviewCases("trade", "ETF_REVIEW", "ETF", 6);

        assertEquals(1, payload.size());
        assertEquals("ETF_REVIEW", payload.get(0).get("caseType"));
        assertEquals("ETF", payload.get(0).get("assetType"));
    }

    @Test
    void reviewCaseDetailReturnsStableRouteContext()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadGovernanceService governanceService = mock(QuantRoadGovernanceService.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadReviewService service = new QuantRoadReviewService(jdbcTemplate, governanceService, queryService);

        when(queryService.reviewCandidates(50)).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq(401L)))
                .thenReturn(List.of(caseDetailRow()));
        when(jdbcTemplate.queryForList(anyString(), eq("trade"), eq(9L), eq("510300"), eq(5001L), eq(10)))
                .thenReturn(List.of(historyRow()));

        Map<String, Object> payload = service.reviewCaseDetail(401L);

        assertEquals(401L, payload.get("caseId"));
        assertNotNull(payload.get("routeQuery"));
        assertEquals("401", ((Map<?, ?>) payload.get("routeQuery")).get("caseId"));
        assertEquals("etf_pool", ((Map<?, ?>) payload.get("routeQuery")).get("scopeType"));
    }

    @Test
    void governanceEvidenceCombinesSummaryAndCharts()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadGovernanceService governanceService = mock(QuantRoadGovernanceService.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadReviewService service = new QuantRoadReviewService(jdbcTemplate, governanceService, queryService);

        when(governanceService.shadowCompareSummary(1L, 2L, 6)).thenReturn(Map.of(
                "recommendation", "OBSERVE",
                "governanceAction", "OBSERVE"));
        when(governanceService.shadowCompareCharts(1L, 2L, 6)).thenReturn(Map.of(
                "categories", List.of("2026-05"),
                "annualDeltaSeries", List.of(-1.5D)));

        Map<String, Object> payload = service.governanceEvidence(1L, 2L, 6);

        assertEquals("OBSERVE", payload.get("recommendation"));
        assertEquals(List.of("2026-05"), ((Map<?, ?>) payload.get("charts")).get("categories"));
        verify(governanceService, times(1)).shadowCompareCharts(1L, 2L, 6);
    }

    @Test
    void submitConclusionPersistsReviewAudit()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadGovernanceService governanceService = mock(QuantRoadGovernanceService.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadReviewService service = new QuantRoadReviewService(jdbcTemplate, governanceService, queryService);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(2001L);
        when(jdbcTemplate.queryForList(anyString(), eq("strategy"), eq(1L), eq("000001"), eq(101L), eq(10)))
                .thenReturn(List.of(historyRow()));

        QuantReviewConclusionRequest request = new QuantReviewConclusionRequest();
        request.setReviewLevel("strategy");
        request.setCaseId(301L);
        request.setStrategyId(1L);
        request.setStockCode("000001");
        request.setSignalId(101L);
        request.setDateRangeStart("2026-05-01");
        request.setDateRangeEnd("2026-05-31");
        request.setReviewConclusion("OBSERVE");
        request.setPrimaryReason("执行偏差仍需观察");
        request.setSecondaryReason("样本窗口不足");
        request.setSuggestedAction("observe");
        request.setConfidenceLevel("MEDIUM");
        request.setActor("tester");
        request.setRemark("need another month");

        Map<String, Object> payload = service.submitConclusion(request);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).update(anyString(), argsCaptor.capture());
        Object[] args = argsCaptor.getAllValues().get(0);
        assertEquals("strategy", args[0]);
        assertEquals(301L, args[1]);
        assertEquals(1L, args[2]);
        assertEquals("000001", args[3]);
        assertEquals("OBSERVE", args[8]);
        assertEquals("tester", args[13]);
        assertEquals(2001L, payload.get("reviewId"));
        assertFalse(((List<?>) payload.get("history")).isEmpty());
    }

    @Test
    void timelineReturnsOnlyRecentKeyEventsWithinLimit()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadGovernanceService governanceService = mock(QuantRoadGovernanceService.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadReviewService service = new QuantRoadReviewService(jdbcTemplate, governanceService, queryService);

        when(jdbcTemplate.queryForList(
                contains("FROM trade_signal"),
                eq(Date.valueOf("2026-05-01")),
                eq(Date.valueOf("2026-05-31")),
                eq(1L),
                eq(1L),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(3)))
                .thenReturn(List.of(
                        signalRow(101L, "2026-05-02", "BUY", "000001"),
                        signalRow(102L, "2026-05-01", "SELL", "000001")));
        when(jdbcTemplate.queryForList(
                contains("FROM execution_record"),
                eq(Date.valueOf("2026-05-01")),
                eq(Date.valueOf("2026-05-31")),
                eq(1L),
                eq(1L),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(3)))
                .thenReturn(List.of(
                        executionRow(201L, "2026-05-03", "BUY", "000001", 100, 10.5D),
                        executionRow(202L, "2026-05-01", "SELL", "000001", 100, 10.2D)));
        when(jdbcTemplate.queryForList(
                contains("FROM strategy_run_log"),
                eq(1L),
                eq(Date.valueOf("2026-05-01")),
                eq(Date.valueOf("2026-05-31")),
                eq(3)))
                .thenReturn(List.of(
                        strategyLogRow(301L, "2026-05-04", 0, "ok"),
                        strategyLogRow(302L, "2026-05-02", 1, "invalid")));
        when(jdbcTemplate.queryForList(anyString(), eq("strategy"), eq(1L), eq(null), eq(null), eq(10)))
                .thenReturn(List.of(historyRow()));

        List<Map<String, Object>> payload = service.timeline(
                "strategy",
                1L,
                null,
                null,
                "2026-05-01",
                "2026-05-31",
                null,
                null,
                6,
                3);

        assertEquals(3, payload.size());
        assertEquals("2026-05-07 13:40:00", String.valueOf(payload.get(0).get("eventTime")));
        assertEquals("2026-05-04", String.valueOf(payload.get(1).get("eventTime")));
        assertEquals("2026-05-03", String.valueOf(payload.get(2).get("eventTime")));
    }

    @Test
    void navDrawdownQueriesLatestSnapshotPerTradingDay()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadGovernanceService governanceService = mock(QuantRoadGovernanceService.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadReviewService service = new QuantRoadReviewService(jdbcTemplate, governanceService, queryService);

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(1L))).thenReturn("日级策略");
        when(jdbcTemplate.queryForList(
                contains("ROW_NUMBER() OVER"),
                eq(1L),
                eq(Date.valueOf("2026-05-01")),
                eq(Date.valueOf("2026-05-31"))))
                .thenReturn(List.of(
                        navLogRow("2026-05-01", 10D, 0),
                        navLogRow("2026-05-02", 25D, 1)));
        when(jdbcTemplate.queryForList(
                contains("FROM market_status"),
                eq(Date.valueOf("2026-05-01")),
                eq(Date.valueOf("2026-05-31"))))
                .thenReturn(List.of(
                        benchmarkRow("2026-05-01", 3000D),
                        benchmarkRow("2026-05-02", 3030D)));

        Map<String, Object> payload = service.navDrawdown(1L, "2026-05-01", "2026-05-31");

        assertEquals(List.of("2026-05-01", "2026-05-02"), payload.get("categories"));
        assertEquals(2, ((List<?>) payload.get("strategyNavSeries")).size());
        assertEquals(2, ((List<?>) payload.get("invalidTriggerFlags")).size());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), eq(1L), eq(Date.valueOf("2026-05-01")), eq(Date.valueOf("2026-05-31")));
        assertTrue(sqlCaptor.getValue().contains("ROW_NUMBER() OVER"));
    }

    private Map<String, Object> caseCandidateRow()
    {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("reviewLevel", "trade");
        row.put("signalId", 5001L);
        row.put("stockCode", "510300");
        row.put("reviewTargetName", "沪深300ETF");
        row.put("reason", "ETF 风险预警");
        row.put("status", "ETF_RISK_WARNING");
        row.put("date", "2026-05-07");
        row.put("assetType", "ETF");
        row.put("scopeType", "etf_pool");
        row.put("scopePoolCode", "ETF_CORE");
        row.put("sourceAction", "etfRisk");
        return row;
    }

    private Map<String, Object> caseListRow()
    {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", 301L);
        row.put("review_level", "trade");
        row.put("case_type", "ETF_REVIEW");
        row.put("case_status", "ETF_RISK_WARNING");
        row.put("resolution_status", "OPEN");
        row.put("asset_type", "ETF");
        row.put("severity", "P1");
        row.put("stock_code", "510300");
        row.put("signal_id", 5001L);
        row.put("review_target_name", "沪深300ETF");
        row.put("reason", "ETF 风险预警");
        row.put("scope_type", "etf_pool");
        row.put("scope_pool_code", "ETF_CORE");
        row.put("source_action", "etfRisk");
        row.put("last_detected_time", "2026-05-07");
        row.put("last_review_conclusion", "OBSERVE");
        row.put("last_review_time", "2026-05-07 21:00:00");
        return row;
    }

    private Map<String, Object> caseDetailRow()
    {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", 401L);
        row.put("review_level", "trade");
        row.put("case_type", "ETF_REVIEW");
        row.put("case_status", "ETF_RISK_WARNING");
        row.put("resolution_status", "OPEN");
        row.put("asset_type", "ETF");
        row.put("severity", "P1");
        row.put("strategy_id", 9L);
        row.put("stock_code", "510300");
        row.put("signal_id", 5001L);
        row.put("review_target_name", "沪深300ETF");
        row.put("reason", "ETF 风险预警");
        row.put("scope_type", "etf_pool");
        row.put("scope_pool_code", "ETF_CORE");
        row.put("source_action", "etfRisk");
        row.put("last_detected_time", "2026-05-07");
        return row;
    }

    private Map<String, Object> historyRow()
    {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", 2001L);
        row.put("review_level", "strategy");
        row.put("strategy_id", 1L);
        row.put("stock_code", "000001");
        row.put("signal_id", 101L);
        row.put("date_range_start", java.sql.Date.valueOf("2026-05-01"));
        row.put("date_range_end", java.sql.Date.valueOf("2026-05-31"));
        row.put("review_conclusion", "OBSERVE");
        row.put("primary_reason", "执行偏差仍需观察");
        row.put("secondary_reason", "样本窗口不足");
        row.put("suggested_action", "observe");
        row.put("confidence_level", "MEDIUM");
        row.put("actor", "tester");
        row.put("remark", "need another month");
        row.put("create_time", "2026-05-07 13:40:00");
        return row;
    }

    private Map<String, Object> signalRow(Long id, String signalDate, String signalType, String stockCode)
    {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("signal_date", Date.valueOf(signalDate));
        row.put("signal_type", signalType);
        row.put("stock_code", stockCode);
        row.put("stock_name", stockCode);
        row.put("strategy_id", 1L);
        return row;
    }

    private Map<String, Object> executionRow(Long id, String tradeDate, String side, String stockCode, int quantity, double price)
    {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("trade_date", Date.valueOf(tradeDate));
        row.put("side", side);
        row.put("stock_code", stockCode);
        row.put("quantity", quantity);
        row.put("price", price);
        row.put("signal_id", 101L);
        return row;
    }

    private Map<String, Object> strategyLogRow(Long id, String runDate, int isInvalid, String remark)
    {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("run_date", Date.valueOf(runDate));
        row.put("is_invalid", isInvalid);
        row.put("remark", remark);
        return row;
    }

    private Map<String, Object> navLogRow(String runDate, double totalProfit, int isInvalid)
    {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("run_date", Date.valueOf(runDate));
        row.put("annual_return", 0D);
        row.put("total_profit", totalProfit);
        row.put("is_invalid", isInvalid);
        return row;
    }

    private Map<String, Object> benchmarkRow(String tradeDate, double hs300Close)
    {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("trade_date", Date.valueOf(tradeDate));
        row.put("hs300_close", hs300Close);
        return row;
    }
}
