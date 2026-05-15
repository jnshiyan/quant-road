package com.ruoyi.web.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;

class QuantRoadQueryServiceTest
{
    @Test
    void executionReconciliationSummaryAggregatesCoreCounts()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'PENDING'"),
                eq(Long.class))).thenReturn(3L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'EXECUTED'"),
                eq(Long.class))).thenReturn(5L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'MISSED'"),
                eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(DISTINCT ts.id) " +
                        "FROM trade_signal ts " +
                        "JOIN execution_record er ON er.signal_id = ts.id " +
                        "LEFT JOIN signal_execution_feedback f ON f.signal_id = ts.id " +
                        "WHERE COALESCE(ts.is_execute, 0) = 0 OR COALESCE(f.status, 'PENDING') = 'PENDING'"),
                eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM execution_record WHERE signal_id IS NULL"),
                eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(anyString(), eq(LocalDate.class))).thenReturn(LocalDate.of(2026, 5, 6));

        Map<String, Object> payload = service.executionReconciliationSummary();

        assertEquals(3L, payload.get("pendingSignalCount"));
        assertEquals(5L, payload.get("executedSignalCount"));
        assertEquals(1L, payload.get("missedSignalCount"));
        assertEquals(1L, payload.get("partialExecutionCount"));
        assertEquals(1L, payload.get("unmatchedExecutionCount"));
        assertEquals(LocalDate.of(2026, 5, 6), payload.get("latestCheckDate"));
        assertFalse((Boolean) payload.get("todayWritebackComplete"));
    }

    @Test
    void dashboardActionItemsReturnsPrioritizedRows()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'PENDING'"),
                eq(Long.class))).thenReturn(2L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'EXECUTED'"),
                eq(Long.class))).thenReturn(4L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'MISSED'"),
                eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(DISTINCT ts.id) " +
                        "FROM trade_signal ts " +
                        "JOIN execution_record er ON er.signal_id = ts.id " +
                        "LEFT JOIN signal_execution_feedback f ON f.signal_id = ts.id " +
                        "WHERE COALESCE(ts.is_execute, 0) = 0 OR COALESCE(f.status, 'PENDING') = 'PENDING'"),
                eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM execution_record WHERE signal_id IS NULL"),
                eq(Long.class))).thenReturn(2L);
        when(jdbcTemplate.queryForObject(anyString(), eq(LocalDate.class))).thenReturn(LocalDate.of(2026, 5, 6));
        when(jdbcTemplate.queryForObject(
                eq("SELECT id FROM job_run_batch ORDER BY start_time DESC, id DESC LIMIT 1"),
                eq(Long.class))).thenReturn(12L);
        when(jdbcTemplate.queryForMap(
                eq("SELECT id, pipeline_name, status, start_time, end_time, error_message FROM job_run_batch WHERE id = ?"),
                eq(12L))).thenReturn(row(
                        "id", 12L,
                        "pipeline_name", "full-daily",
                        "status", "FAILED",
                        "start_time", "2026-05-06 20:00:00",
                        "end_time", "2026-05-06 20:10:00",
                        "error_message", "upstream disconnected"));
        when(jdbcTemplate.queryForList(
                eq("SELECT id, batch_id, step_name, status, start_time, end_time, retries, error_message FROM job_run_step WHERE batch_id = ? ORDER BY id"),
                eq(12L))).thenReturn(List.of(row(
                        "id", 101L,
                        "batch_id", 12L,
                        "step_name", "sync-daily",
                        "status", "FAILED",
                        "start_time", "2026-05-06 20:02:00",
                        "end_time", "2026-05-06 20:05:00",
                        "retries", 1,
                        "error_message", "Connection aborted")));
        when(jdbcTemplate.queryForList(startsWith("SELECT p.stock_code, p.stock_name, p.quantity")))
                .thenReturn(List.of(
                        row("stock_code", "000001", "stock_name", "PingAn", "quantity", 100, "current_price", 10.0, "cost_price", 9.8, "loss_warning", 1),
                        row("stock_code", "000002", "stock_name", "Vanke", "quantity", 100, "current_price", 8.0, "cost_price", 8.1, "loss_warning", 0)));
        when(jdbcTemplate.queryForList(startsWith("SELECT ts.id AS signal_id, ts.stock_code"), eq(3)))
                .thenReturn(List.of(row(
                        "signal_id", 5001L,
                        "stock_code", "000001",
                        "stock_name", "PingAn",
                        "strategy_id", 1L,
                        "status", "MISSED",
                        "remark", "未成交",
                        "check_date", LocalDate.of(2026, 5, 6))));
        when(jdbcTemplate.queryForList(startsWith("SELECT strategy_id, run_time, annual_return"), eq(3)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(startsWith("SELECT run_date, baseline_strategy_id, candidate_strategy_id, recommendation, remark"), eq(3)))
                .thenReturn(List.of(row(
                        "run_date", LocalDate.of(2026, 5, 6),
                        "baseline_strategy_id", 1L,
                        "candidate_strategy_id", 2L,
                        "recommendation", "OBSERVE",
                        "remark", "候选策略继续观察")));

        List<Map<String, Object>> payload = service.dashboardActionItems(8);

        assertFalse(payload.isEmpty());
        assertEquals("PIPELINE_RECOVERY", payload.get(0).get("actionType"));
        assertEquals("P0", payload.get(0).get("priority"));
    }

    @Test
    void jobReadinessWarnsWhenCoreDataStepFallsBack()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
                eq("SELECT id FROM job_run_batch ORDER BY start_time DESC, id DESC LIMIT 1"),
                eq(Long.class))).thenReturn(18L);
        when(jdbcTemplate.queryForMap(
                eq("SELECT id, pipeline_name, status, start_time, end_time, error_message FROM job_run_batch WHERE id = ?"),
                eq(18L))).thenReturn(row(
                        "id", 18L,
                        "pipeline_name", "full-daily",
                        "status", "SUCCESS",
                        "start_time", "2026-05-08 19:00:00",
                        "end_time", "2026-05-08 19:08:00",
                        "error_message", null));
        when(jdbcTemplate.queryForList(
                eq("SELECT id, batch_id, step_name, status, start_time, end_time, retries, error_message FROM job_run_step WHERE batch_id = ? ORDER BY id"),
                eq(18L))).thenReturn(List.of(
                        row(
                                "id", 201L,
                                "batch_id", 18L,
                                "step_name", "sync-basic",
                                "status", "SUCCESS",
                                "start_time", "2026-05-08 19:00:00",
                                "end_time", "2026-05-08 19:01:00",
                                "retries", 0,
                                "error_message", "{\"source\":\"stock_basic_fallback\",\"usedFallback\":true,\"totalCount\":3000}"),
                        row(
                                "id", 202L,
                                "batch_id", 18L,
                                "step_name", "sync-daily",
                                "status", "SUCCESS",
                                "start_time", "2026-05-08 19:01:00",
                                "end_time", "2026-05-08 19:05:00",
                                "retries", 0,
                                "error_message", "{\"requestedCount\":10,\"successCount\":10,\"failedCount\":0,\"skippedCount\":0}")));

        Map<String, Object> payload = service.jobReadiness(null);

        assertEquals("READY_WITH_WARNINGS", payload.get("status"));
        assertEquals(true, payload.get("canEnterDashboard"));
        assertEquals("WARNING", payload.get("dataIntegrityStatus"));
        assertEquals("FALLBACK_BASIC", payload.get("dataIntegrityCategory"));
        assertEquals("基础标的信息本次使用了库内回退，今日结果可继续查看，但建议后续补跑 sync-basic 确认标的信息是否最新。", payload.get("dataIntegrityMessage"));
        assertEquals(1, payload.get("warningStepCount"));
    }

    @Test
    void jobSopHintsUseUnifiedExecutionLanguageWhenNoBatchExists()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
                eq("SELECT id FROM job_run_batch ORDER BY start_time DESC, id DESC LIMIT 1"),
                eq(Long.class))).thenReturn(null);

        List<Map<String, Object>> hints = service.jobSopHints(null);

        assertEquals(1, hints.size());
        assertEquals("runExecution", hints.get(0).get("code"));
        assertEquals("先提交执行任务", hints.get(0).get("title"));
        assertFalse(String.valueOf(hints.get(0).get("summary")).contains("fullDaily"));
        assertFalse(String.valueOf(hints.get(0).get("suggestedAction")).contains("fullDaily"));
    }

    @Test
    void dashboardActionItemsExposeUnifiedActionMetadataForDataIntegrityReview()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'PENDING'"),
                eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'EXECUTED'"),
                eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'MISSED'"),
                eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(DISTINCT ts.id) " +
                        "FROM trade_signal ts " +
                        "JOIN execution_record er ON er.signal_id = ts.id " +
                        "LEFT JOIN signal_execution_feedback f ON f.signal_id = ts.id " +
                        "WHERE COALESCE(ts.is_execute, 0) = 0 OR COALESCE(f.status, 'PENDING') = 'PENDING'"),
                eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM execution_record WHERE signal_id IS NULL"),
                eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), eq(LocalDate.class))).thenReturn(LocalDate.of(2026, 5, 8));
        when(jdbcTemplate.queryForObject(
                eq("SELECT id FROM job_run_batch ORDER BY start_time DESC, id DESC LIMIT 1"),
                eq(Long.class))).thenReturn(21L);
        when(jdbcTemplate.queryForMap(
                eq("SELECT id, pipeline_name, status, start_time, end_time, error_message FROM job_run_batch WHERE id = ?"),
                eq(21L))).thenReturn(row(
                        "id", 21L,
                        "pipeline_name", "full-daily",
                        "status", "SUCCESS",
                        "start_time", "2026-05-08 19:00:00",
                        "end_time", "2026-05-08 19:10:00",
                        "error_message", null));
        when(jdbcTemplate.queryForList(
                eq("SELECT id, batch_id, step_name, status, start_time, end_time, retries, error_message FROM job_run_step WHERE batch_id = ? ORDER BY id"),
                eq(21L))).thenReturn(List.of(row(
                        "id", 301L,
                        "batch_id", 21L,
                        "step_name", "sync-daily",
                        "status", "SUCCESS",
                        "start_time", "2026-05-08 19:01:00",
                        "end_time", "2026-05-08 19:06:00",
                        "retries", 0,
                        "error_message", "{\"requestedCount\":14,\"successCount\":12,\"failedCount\":1,\"skippedCount\":1,\"failedSymbols\":[\"000001\"],\"skippedSymbols\":[\"000002\"]}")));
        when(jdbcTemplate.queryForList(startsWith("SELECT p.stock_code, p.stock_name, p.quantity")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(startsWith("SELECT id, strategy_name, params")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(startsWith("SELECT trade_date, status, raw_status")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(startsWith("SELECT ts.id AS signal_id, ts.stock_code"), eq(3)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(startsWith("SELECT strategy_id, run_time, annual_return"), eq(3)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(startsWith("SELECT run_date, baseline_strategy_id, candidate_strategy_id, recommendation, remark"), eq(3)))
                .thenReturn(List.of());

        List<Map<String, Object>> payload = service.dashboardActionItems(6);

        assertFalse(payload.isEmpty());
        assertEquals("DATA_INTEGRITY_REVIEW", payload.get(0).get("actionType"));
        assertEquals("/quant/operations", payload.get(0).get("targetPage"));
        assertEquals("OPEN", payload.get(0).get("status"));
        assertEquals("dashboard", payload.get(0).get("sourcePage"));
        assertEquals("dataIntegrityGate", payload.get(0).get("sourceAction"));
    }

    @Test
    void jobSopHintsRouteRecoveryWorkToOperationsCenter()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForMap(
                eq("SELECT id, pipeline_name, status, start_time, end_time, error_message FROM job_run_batch WHERE id = ?"),
                eq(18L))).thenReturn(row(
                        "id", 18L,
                        "pipeline_name", "full-daily",
                        "status", "FAILED",
                        "start_time", "2026-05-08 19:00:00",
                        "end_time", "2026-05-08 19:08:00",
                        "error_message", "upstream disconnected"));
        when(jdbcTemplate.queryForList(
                eq("SELECT id, batch_id, step_name, status, start_time, end_time, retries, error_message FROM job_run_step WHERE batch_id = ? ORDER BY id"),
                eq(18L))).thenReturn(List.of(
                        row(
                                "id", 201L,
                                "batch_id", 18L,
                                "step_name", "sync-daily",
                                "status", "FAILED",
                                "start_time", "2026-05-08 19:01:00",
                                "end_time", "2026-05-08 19:03:00",
                                "retries", 1,
                                "error_message", "Connection aborted")));

        List<Map<String, Object>> hints = service.jobSopHints(18L);

        assertEquals("recoverBatch", hints.get(0).get("code"));
        assertEquals("/quant/operations", hints.get(0).get("targetPage"));
    }

    @Test
    void positionRiskSummaryBuildsBudgetAndRiskSignals()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(startsWith("SELECT p.stock_code, p.stock_name, p.quantity")))
                .thenReturn(List.of(
                        row("stock_code", "000001", "stock_name", "PingAn", "quantity", 400, "current_price", 12.0, "cost_price", 10.0, "loss_warning", 1),
                        row("stock_code", "000002", "stock_name", "Vanke", "quantity", 200, "current_price", 8.0, "cost_price", 8.2, "loss_warning", 0)));
        when(jdbcTemplate.queryForList(startsWith("SELECT id, strategy_name, params")))
                .thenReturn(List.of(
                        row("id", 1L, "strategy_name", "MA20", "params", "{\"max_single_position_pct\":0.15,\"max_total_position_pct\":0.80,\"portfolio_capital\":10000,\"regime_budget_weights\":{\"volatile\":0.8,\"default\":0.8}}"),
                        row("id", 2L, "strategy_name", "RSI", "params", "{\"max_single_position_pct\":0.12,\"max_total_position_pct\":0.75,\"portfolio_capital\":10000,\"regime_budget_weights\":{\"volatile\":0.9,\"default\":0.8}}")));
        when(jdbcTemplate.queryForList(startsWith("SELECT trade_date, status, raw_status")))
                .thenReturn(List.of(row(
                        "trade_date", LocalDate.of(2026, 5, 6),
                        "status", "volatile",
                        "raw_status", "volatile",
                        "remark", "震荡市")));

        Map<String, Object> payload = service.positionRiskSummary();

        assertEquals("HIGH", payload.get("riskLevel"));
        assertEquals(2, payload.get("overBudgetCount"));
        assertEquals(1, payload.get("stopLossWarningCount"));
        assertTrue(((Number) payload.get("topHoldingPct")).doubleValue() > 15D);
    }

    @Test
    void positionRiskSummarySeparatesEtfAndEquityRisk()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(startsWith("SELECT p.stock_code, p.stock_name, p.quantity")))
                .thenReturn(List.of(
                        row("stock_code", "510300", "stock_name", "沪深300ETF", "quantity", 300, "current_price", 5.0, "cost_price", 4.8, "loss_warning", 1),
                        row("stock_code", "000001", "stock_name", "平安银行", "quantity", 400, "current_price", 10.0, "cost_price", 9.5, "loss_warning", 1)));
        when(jdbcTemplate.queryForList(startsWith("SELECT id, strategy_name, params")))
                .thenReturn(List.of(
                        row("id", 1L, "strategy_name", "MA20", "params", "{\"max_single_position_pct\":0.15,\"max_total_position_pct\":0.80,\"portfolio_capital\":10000}")));
        when(jdbcTemplate.queryForList(startsWith("SELECT trade_date, status, raw_status")))
                .thenReturn(List.of(row(
                        "trade_date", LocalDate.of(2026, 5, 6),
                        "status", "bull",
                        "raw_status", "bull",
                        "remark", "强势")));
        when(jdbcTemplate.queryForList(startsWith("SELECT stock_code FROM quant_symbol_pool_member WHERE pool_code = 'ETF_CORE'")))
                .thenReturn(List.of(
                        row("stock_code", "510300"),
                        row("stock_code", "159915")));

        Map<String, Object> payload = service.positionRiskSummary();

        assertEquals(1, payload.get("etfHoldingCount"));
        assertEquals(1, payload.get("equityHoldingCount"));
        assertEquals(1, payload.get("etfRiskWarningCount"));
        assertEquals(1, payload.get("equityRiskWarningCount"));
        assertEquals(22.5D, ((Number) payload.get("etfSingleBudgetCapPct")).doubleValue());
        assertEquals(15.0D, ((Number) payload.get("equitySingleBudgetCapPct")).doubleValue());
        assertEquals(1, payload.get("overBudgetCount"));
    }

    @Test
    void dashboardDeepLinksExposeCoreRoutes()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'PENDING'"),
                eq(Long.class))).thenReturn(2L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'EXECUTED'"),
                eq(Long.class))).thenReturn(5L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM signal_execution_feedback WHERE status = 'MISSED'"),
                eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(DISTINCT ts.id) " +
                        "FROM trade_signal ts " +
                        "JOIN execution_record er ON er.signal_id = ts.id " +
                        "LEFT JOIN signal_execution_feedback f ON f.signal_id = ts.id " +
                        "WHERE COALESCE(ts.is_execute, 0) = 0 OR COALESCE(f.status, 'PENDING') = 'PENDING'"),
                eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM execution_record WHERE signal_id IS NULL"),
                eq(Long.class))).thenReturn(2L);
        when(jdbcTemplate.queryForObject(anyString(), eq(LocalDate.class))).thenReturn(LocalDate.of(2026, 5, 6));
        when(jdbcTemplate.queryForList(startsWith("SELECT p.stock_code, p.stock_name, p.quantity")))
                .thenReturn(List.of(row("stock_code", "000001", "stock_name", "PingAn", "quantity", 100, "current_price", 10.0, "cost_price", 10.0, "loss_warning", 0)));
        when(jdbcTemplate.queryForList(startsWith("SELECT id, strategy_name, params")))
                .thenReturn(List.of(row("id", 1L, "strategy_name", "MA20", "params", "{\"max_single_position_pct\":0.15,\"max_total_position_pct\":0.80,\"portfolio_capital\":10000}")));
        when(jdbcTemplate.queryForList(startsWith("SELECT trade_date, status, raw_status")))
                .thenReturn(List.of(row("trade_date", LocalDate.of(2026, 5, 6), "status", "volatile", "raw_status", "volatile", "remark", "震荡")));
        when(jdbcTemplate.queryForList(startsWith("SELECT ts.id AS signal_id, ts.stock_code"), eq(6)))
                .thenReturn(List.of(row(
                        "signal_id", 5001L,
                        "stock_code", "000001",
                        "stock_name", "PingAn",
                        "strategy_id", 1L,
                        "status", "MISSED",
                        "remark", "未成交",
                        "check_date", LocalDate.of(2026, 5, 6))));
        when(jdbcTemplate.queryForList(startsWith("SELECT strategy_id, run_time, annual_return"), eq(6)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(startsWith("SELECT run_date, baseline_strategy_id, candidate_strategy_id, recommendation, remark"), eq(6)))
                .thenReturn(List.of(row(
                        "run_date", LocalDate.of(2026, 5, 6),
                        "baseline_strategy_id", 1L,
                        "candidate_strategy_id", 2L,
                        "recommendation", "OBSERVE",
                        "remark", "候选策略继续观察")));

        List<Map<String, Object>> payload = service.dashboardDeepLinks();

        assertFalse(payload.isEmpty());
        assertEquals("/quant/execution", payload.get(0).get("path"));
    }

    @Test
    void etfOverviewBuildsIndependentObjectSummary()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(DISTINCT stock_code) FROM quant_symbol_pool_member WHERE pool_code = 'ETF_CORE' AND inclusion_status = 'INCLUDED'"),
                eq(Long.class))).thenReturn(12L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM quant_index_etf_mapping WHERE status = 1"),
                eq(Long.class))).thenReturn(4L);
        when(jdbcTemplate.queryForList(
                startsWith("SELECT index_code, index_name, primary_etf_code, primary_etf_name, candidate_etf_codes, candidate_etf_names"),
                eq(6))).thenReturn(List.of(
                        row(
                                "index_code", "000300",
                                "index_name", "沪深300",
                                "primary_etf_code", "510300",
                                "primary_etf_name", "沪深300ETF",
                                "candidate_etf_codes", "[\"159919\",\"510310\"]",
                                "candidate_etf_names", "[\"沪深300ETF备选A\",\"沪深300ETF备选B\"]")));
        when(jdbcTemplate.queryForList(
                startsWith("SELECT p.stock_code, p.stock_name, p.quantity, p.current_price, p.float_profit, p.loss_warning"),
                eq(6))).thenReturn(List.of(
                        row(
                                "stock_code", "510300",
                                "stock_name", "沪深300ETF",
                                "quantity", 1000,
                                "current_price", 4.12,
                                "float_profit", 3.5,
                                "loss_warning", 0)));
        when(jdbcTemplate.queryForList(
                startsWith("SELECT ts.id, ts.stock_code, ts.stock_name, ts.signal_type, ts.suggest_price, ts.signal_date, ts.strategy_id"),
                eq(java.sql.Date.valueOf(LocalDate.now())),
                eq(6))).thenReturn(List.of(
                        row(
                                "id", 7001L,
                                "stock_code", "510300",
                                "stock_name", "沪深300ETF",
                                "signal_type", "BUY",
                                "suggest_price", 4.15,
                                "signal_date", LocalDate.now(),
                                "strategy_id", 2L)));

        Map<String, Object> payload = service.etfOverview();

        assertEquals(12L, payload.get("etfUniverseCount"));
        assertEquals(4L, payload.get("indexMappingCount"));
        assertEquals(1, payload.get("etfPositionCount"));
        assertEquals(1, payload.get("todayEtfSignalCount"));
        assertEquals("etf_pool", payload.get("recommendedScopeType"));
        assertEquals("ETF_CORE", payload.get("recommendedScopePoolCode"));
        assertFalse(((List<?>) payload.get("mappingHighlights")).isEmpty());
        assertFalse(((List<?>) payload.get("activeSignals")).isEmpty());
    }

    @Test
    void etfGovernanceSummaryBuildsIndependentGovernanceRows()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM quant_index_etf_mapping WHERE status = 1"),
                eq(Long.class))).thenReturn(2L);
        when(jdbcTemplate.queryForList(
                startsWith("SELECT index_code, index_name, primary_etf_code, primary_etf_name, candidate_etf_codes, candidate_etf_names")))
                .thenReturn(List.of(
                        row(
                                "index_code", "000300",
                                "index_name", "沪深300",
                                "primary_etf_code", "510300",
                                "primary_etf_name", "沪深300ETF",
                                "candidate_etf_codes", "[\"159919\"]",
                                "candidate_etf_names", "[\"沪深300ETF备选A\"]"),
                        row(
                                "index_code", "399006",
                                "index_name", "创业板指",
                                "primary_etf_code", "159915",
                                "primary_etf_name", "创业板ETF",
                                "candidate_etf_codes", "[\"159952\"]",
                                "candidate_etf_names", "[\"创业板ETF备选A\"]")));
        when(jdbcTemplate.queryForList(
                startsWith("SELECT p.stock_code, p.stock_name, p.quantity, p.current_price, p.float_profit, p.loss_warning")))
                .thenReturn(List.of(
                        row(
                                "stock_code", "510300",
                                "stock_name", "沪深300ETF",
                                "quantity", 1000,
                                "current_price", 4.12,
                                "float_profit", 3.5,
                                "loss_warning", 0),
                        row(
                                "stock_code", "159915",
                                "stock_name", "创业板ETF",
                                "quantity", 800,
                                "current_price", 2.10,
                                "float_profit", -6.2,
                                "loss_warning", 1)));
        when(jdbcTemplate.queryForList(
                startsWith("SELECT ts.id, ts.stock_code, ts.stock_name, ts.signal_type, ts.suggest_price, ts.signal_date, ts.strategy_id"),
                eq(java.sql.Date.valueOf(LocalDate.now()))))
                .thenReturn(List.of(
                        row(
                                "id", 7001L,
                                "stock_code", "510300",
                                "stock_name", "沪深300ETF",
                                "signal_type", "BUY",
                                "suggest_price", 4.15,
                                "signal_date", LocalDate.now(),
                                "strategy_id", 2L)));
        when(jdbcTemplate.queryForList(
                startsWith("SELECT ts.stock_code, COUNT(1) AS pending_count")))
                .thenReturn(List.of(
                        row("stock_code", "159915", "pending_count", 2L)));

        Map<String, Object> payload = service.etfGovernanceSummary();

        Map<?, ?> summary = (Map<?, ?>) payload.get("summary");
        assertEquals(2L, summary.get("indexMappingCount"));
        assertEquals(2, summary.get("holdingCount"));
        assertEquals(1, summary.get("riskWarningCount"));
        assertEquals(1, summary.get("activeSignalCount"));

        List<?> rows = (List<?>) payload.get("mappingGovernanceRows");
        assertEquals(2, rows.size());
        Map<?, ?> first = (Map<?, ?>) rows.get(0);
        assertEquals("510300", first.get("primaryEtfCode"));
        assertEquals("KEEP_PRIMARY", first.get("governanceAction"));
        assertEquals("510300", ((Map<?, ?>) first.get("reviewQuery")).get("stockCode"));

        Map<?, ?> second = (Map<?, ?>) rows.get(1);
        assertEquals("159915", second.get("primaryEtfCode"));
        assertEquals("REVIEW", second.get("governanceAction"));
        assertEquals("etf_pool", ((Map<?, ?>) second.get("reviewQuery")).get("scopeType"));
    }

    @Test
    void reviewCandidatesCollectsTradeStrategyAndGovernanceRows()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(startsWith("SELECT ts.id AS signal_id, ts.stock_code"), eq(6)))
                .thenReturn(List.of(row(
                        "signal_id", 5001L,
                        "stock_code", "000001",
                        "stock_name", "PingAn",
                        "strategy_id", 1L,
                        "status", "MISSED",
                        "remark", "未成交",
                        "check_date", LocalDate.of(2026, 5, 6))));
        when(jdbcTemplate.queryForList(startsWith("SELECT strategy_id, run_time, annual_return"), eq(6)))
                .thenReturn(List.of(row(
                        "strategy_id", 2L,
                        "run_time", "2026-05-05 15:00:00",
                        "annual_return", 12.5,
                        "max_drawdown", 8.0,
                        "win_rate", 55.0,
                        "total_profit", 5.0,
                        "remark", "出现失效")));
        when(jdbcTemplate.queryForList(startsWith("SELECT run_date, baseline_strategy_id, candidate_strategy_id, recommendation, remark"), eq(6)))
                .thenReturn(List.of(row(
                        "run_date", LocalDate.of(2026, 5, 4),
                        "baseline_strategy_id", 1L,
                        "candidate_strategy_id", 3L,
                        "recommendation", "OBSERVE",
                        "remark", "继续观察")));
        when(jdbcTemplate.queryForList(startsWith("SELECT stock_code FROM quant_symbol_pool_member WHERE pool_code = 'ETF_CORE'")))
                .thenReturn(List.of(row("stock_code", "510300")));
        when(jdbcTemplate.queryForList(startsWith("SELECT p.stock_code, p.stock_name, p.float_profit, p.update_time"), eq(6)))
                .thenReturn(List.of(row(
                        "stock_code", "510300",
                        "stock_name", "沪深300ETF",
                        "float_profit", -6.2,
                        "update_time", "2026-05-06 14:30:00")));
        when(jdbcTemplate.queryForObject("SELECT strategy_name FROM strategy_config WHERE id = ?", String.class, 1L))
                .thenReturn("MA20");
        when(jdbcTemplate.queryForObject("SELECT strategy_name FROM strategy_config WHERE id = ?", String.class, 2L))
                .thenReturn("RSI");
        when(jdbcTemplate.queryForObject("SELECT strategy_name FROM strategy_config WHERE id = ?", String.class, 3L))
                .thenReturn("ETF_ROTATION");

        List<Map<String, Object>> payload = service.reviewCandidates(6);

        assertEquals(4, payload.size());
        assertTrue(payload.stream().anyMatch(item -> "trade".equals(item.get("reviewLevel"))));
        assertTrue(payload.stream().anyMatch(item -> "strategy".equals(item.get("reviewLevel"))));
        assertTrue(payload.stream().anyMatch(item -> "governance".equals(item.get("reviewLevel"))));
        assertTrue(payload.stream().anyMatch(item -> "etf_pool".equals(item.get("scopeType"))));
    }

    @Test
    void reviewCandidatesIncludesEtfGovernanceCases()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(startsWith("SELECT ts.id AS signal_id, ts.stock_code"), eq(6)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(startsWith("SELECT strategy_id, run_time, annual_return"), eq(6)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(startsWith("SELECT run_date, baseline_strategy_id, candidate_strategy_id, recommendation, remark"), eq(6)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                startsWith("SELECT index_code, index_name, primary_etf_code, primary_etf_name, candidate_etf_codes, candidate_etf_names " +
                        "FROM quant_index_etf_mapping WHERE status = 1 ORDER BY index_code ASC")))
                .thenReturn(List.of(row(
                        "index_code", "399006",
                        "index_name", "创业板指",
                        "primary_etf_code", "159915",
                        "primary_etf_name", "创业板ETF",
                        "candidate_etf_codes", "[\"159952\"]",
                        "candidate_etf_names", "[\"创业板ETF备选A\"]")));
        when(jdbcTemplate.queryForList(
                startsWith("SELECT p.stock_code, p.stock_name, p.quantity, p.current_price, p.float_profit, p.loss_warning " +
                        "FROM position p")))
                .thenReturn(List.of(row(
                        "stock_code", "159915",
                        "stock_name", "创业板ETF",
                        "quantity", 800,
                        "current_price", 2.10,
                        "float_profit", -6.2,
                        "loss_warning", 1)));
        when(jdbcTemplate.queryForList(
                startsWith("SELECT ts.id, ts.stock_code, ts.stock_name, ts.signal_type, ts.suggest_price, ts.signal_date, ts.strategy_id " +
                        "FROM trade_signal ts"),
                eq(java.sql.Date.valueOf(LocalDate.now()))))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                startsWith("SELECT ts.stock_code, COUNT(1) AS pending_count " +
                        "FROM signal_execution_feedback f")))
                .thenReturn(List.of(row(
                        "stock_code", "159915",
                        "pending_count", 2L)));

        List<Map<String, Object>> payload = service.reviewCandidates(6);

        assertEquals(1, payload.size());
        assertEquals("trade", payload.get(0).get("reviewLevel"));
        assertEquals("ETF", payload.get(0).get("assetType"));
        assertEquals("etfGovernance", payload.get(0).get("sourceAction"));
        assertEquals("REVIEW", payload.get(0).get("status"));
        assertEquals("159915", payload.get(0).get("stockCode"));
    }

    @Test
    void signalExplainBuildsActionableExplanation()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(startsWith("SELECT ts.id, ts.stock_code, ts.stock_name, ts.signal_type"), eq(5001L)))
                .thenReturn(List.of(row(
                        "id", 5001L,
                        "stock_code", "000001",
                        "stock_name", "PingAn",
                        "signal_type", "BUY",
                        "suggest_price", 10.5,
                        "signal_date", LocalDate.of(2026, 5, 6),
                        "strategy_id", 1L,
                        "is_execute", 0,
                        "strategy_name", "MA20",
                        "feedback_status", "PENDING",
                        "feedback_remark", "等待成交")));
        when(jdbcTemplate.queryForList(startsWith("SELECT trade_date, status, raw_status")))
                .thenReturn(List.of(row(
                        "trade_date", LocalDate.of(2026, 5, 6),
                        "status", "volatile",
                        "raw_status", "volatile",
                        "remark", "震荡")));
        when(jdbcTemplate.queryForList(
                startsWith("SELECT stock_code, stock_name, quantity, cost_price, current_price, float_profit, loss_warning, update_time FROM position WHERE stock_code = ?"),
                eq("000001")))
                .thenReturn(List.of(row(
                        "stock_code", "000001",
                        "stock_name", "PingAn",
                        "quantity", 100,
                        "cost_price", 9.8,
                        "current_price", 10.2,
                        "float_profit", 4.0,
                        "loss_warning", 0,
                        "update_time", "2026-05-06 15:00:00")));

        Map<String, Object> payload = service.signalExplain(5001L);

        assertEquals(5001L, payload.get("signalId"));
        assertEquals("PENDING", payload.get("executionStatus"));
        assertEquals("BUY 信号待执行", payload.get("headline"));
        assertFalse(((List<?>) payload.get("summaryLines")).isEmpty());
    }

    @Test
    void signalsDeriveExecutionDueDateAndMatchHint()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(anyString(), eq(java.sql.Date.valueOf(LocalDate.of(2026, 5, 6)))))
                .thenReturn(List.of(
                        row(
                                "id", 5001L,
                                "stock_code", "000001",
                                "stock_name", "PingAn",
                                "signal_type", "BUY",
                                "suggest_price", 10.5,
                                "signal_date", LocalDate.of(2026, 5, 6),
                                "strategy_id", 1L,
                                "is_execute", 0,
                                "create_time", "2026-05-06 15:00:00"),
                        row(
                                "id", 5002L,
                                "stock_code", "000002",
                                "stock_name", "Vanke",
                                "signal_type", "SELL",
                                "suggest_price", 9.8,
                                "signal_date", LocalDate.of(2026, 5, 6),
                                "strategy_id", 1L,
                                "is_execute", 1,
                                "create_time", "2026-05-06 15:01:00")));

        List<Map<String, Object>> payload = service.signals(LocalDate.of(2026, 5, 6));

        assertEquals(2, payload.size());
        assertEquals(LocalDate.of(2026, 5, 7), payload.get(0).get("execution_due_date"));
        assertEquals("pending_record_execution", payload.get(0).get("match_hint"));
        assertEquals("already_recorded_execution", payload.get(1).get("match_hint"));
    }

    @Test
    void signalsReturnsPagedRowsWithMeta() throws Exception
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(anyString(), eq(java.sql.Date.valueOf(LocalDate.of(2026, 5, 6)))))
                .thenReturn(List.of(
                        row(
                                "id", 5001L,
                                "stock_code", "000001",
                                "stock_name", "PingAn",
                                "signal_type", "BUY",
                                "suggest_price", 10.5,
                                "signal_date", LocalDate.of(2026, 5, 6),
                                "strategy_id", 1L,
                                "is_execute", 0,
                                "create_time", "2026-05-06 15:00:00"),
                        row(
                                "id", 5002L,
                                "stock_code", "000002",
                                "stock_name", "Vanke",
                                "signal_type", "SELL",
                                "suggest_price", 9.8,
                                "signal_date", LocalDate.of(2026, 5, 6),
                                "strategy_id", 1L,
                                "is_execute", 1,
                                "create_time", "2026-05-06 15:01:00")));

        Method method = QuantRoadQueryService.class.getMethod("signals", LocalDate.class, int.class, int.class);
        Object result = method.invoke(service, LocalDate.of(2026, 5, 6), 1, 1);

        assertNotNull(result);
        Map<?, ?> payload = (Map<?, ?>) result;
        assertEquals(2, payload.get("total"));
        assertEquals(1, payload.get("pageNum"));
        assertEquals(1, payload.get("pageSize"));
        assertEquals(1, ((List<?>) payload.get("rows")).size());
        Map<?, ?> firstRow = (Map<?, ?>) ((List<?>) payload.get("rows")).get(0);
        assertEquals(LocalDate.of(2026, 5, 7), firstRow.get("execution_due_date"));
        assertEquals("pending_record_execution", firstRow.get("match_hint"));
    }

    @Test
    void signalsPagedPayloadIncludesPageCountAndCurrentSliceSize()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(anyString(), eq(java.sql.Date.valueOf(LocalDate.of(2026, 5, 6)))))
                .thenReturn(List.of(
                        row(
                                "id", 5001L,
                                "stock_code", "000001",
                                "stock_name", "PingAn",
                                "signal_type", "BUY",
                                "suggest_price", 10.5,
                                "signal_date", LocalDate.of(2026, 5, 6),
                                "strategy_id", 1L,
                                "is_execute", 0,
                                "create_time", "2026-05-06 15:00:00"),
                        row(
                                "id", 5002L,
                                "stock_code", "000002",
                                "stock_name", "Vanke",
                                "signal_type", "SELL",
                                "suggest_price", 9.8,
                                "signal_date", LocalDate.of(2026, 5, 6),
                                "strategy_id", 1L,
                                "is_execute", 1,
                                "create_time", "2026-05-06 15:01:00"),
                        row(
                                "id", 5003L,
                                "stock_code", "000003",
                                "stock_name", "Kweichow",
                                "signal_type", "BUY",
                                "suggest_price", 18.8,
                                "signal_date", LocalDate.of(2026, 5, 6),
                                "strategy_id", 2L,
                                "is_execute", 0,
                                "create_time", "2026-05-06 15:02:00")));

        Map<String, Object> payload = service.signals(LocalDate.of(2026, 5, 6), 2, 2);

        assertEquals(3, payload.get("total"));
        assertEquals(2, payload.get("pageNum"));
        assertEquals(2, payload.get("pageSize"));
        assertEquals(2, payload.get("pageCount"));
        assertEquals(1, ((List<?>) payload.get("rows")).size());
        Map<?, ?> lastRow = (Map<?, ?>) ((List<?>) payload.get("rows")).get(0);
        assertEquals(5003L, lastRow.get("id"));
        assertEquals("pending_record_execution", lastRow.get("match_hint"));
    }

    @Test
    void signalsClampOutOfRangePageToLastAvailableSlice()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(anyString(), eq(java.sql.Date.valueOf(LocalDate.of(2026, 5, 6)))))
                .thenReturn(List.of(
                        row(
                                "id", 5001L,
                                "stock_code", "000001",
                                "stock_name", "PingAn",
                                "signal_type", "BUY",
                                "suggest_price", 10.5,
                                "signal_date", LocalDate.of(2026, 5, 6),
                                "strategy_id", 1L,
                                "is_execute", 0,
                                "create_time", "2026-05-06 15:00:00"),
                        row(
                                "id", 5002L,
                                "stock_code", "000002",
                                "stock_name", "Vanke",
                                "signal_type", "SELL",
                                "suggest_price", 9.8,
                                "signal_date", LocalDate.of(2026, 5, 6),
                                "strategy_id", 1L,
                                "is_execute", 1,
                                "create_time", "2026-05-06 15:01:00"),
                        row(
                                "id", 5003L,
                                "stock_code", "000003",
                                "stock_name", "Kweichow",
                                "signal_type", "BUY",
                                "suggest_price", 18.8,
                                "signal_date", LocalDate.of(2026, 5, 6),
                                "strategy_id", 2L,
                                "is_execute", 0,
                                "create_time", "2026-05-06 15:02:00")));

        Map<String, Object> payload = service.signals(LocalDate.of(2026, 5, 6), 9, 2);

        assertEquals(2, payload.get("pageNum"));
        assertEquals(2, payload.get("pageCount"));
        assertEquals(1, ((List<?>) payload.get("rows")).size());
        Map<?, ?> row = (Map<?, ?>) ((List<?>) payload.get("rows")).get(0);
        assertEquals(5003L, row.get("id"));
    }

    @Test
    void executionMatchCandidatesReturnsRankedRows()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(anyString(), eq(9001L), eq(5))).thenReturn(List.of(
                Map.of(
                        "execution_record_id", 9001L,
                        "signal_id", 3001L,
                        "stock_code", "000001",
                        "strategy_id", 1L,
                        "signal_type", "BUY",
                        "signal_date", LocalDate.of(2026, 5, 5),
                        "match_score", 97,
                        "match_reason", "same_code_strategy_side_latest_pending",
                        "already_executed", 0)));

        List<Map<String, Object>> payload = service.executionMatchCandidates(9001L, 5);

        assertEquals(1, payload.size());
        assertEquals(9001L, payload.get(0).get("executionRecordId"));
        assertEquals(3001L, payload.get(0).get("signalId"));
        assertEquals(97, payload.get(0).get("matchScore"));
    }

    @Test
    void executionRecordsDerivesReadableStatuses()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(this::answerExecutionRecordQueries);

        List<Map<String, Object>> payload = service.executionRecords(10, null);

        assertEquals(3, payload.size());
        assertEquals("UNMATCHED", payload.get(0).get("match_status"));
        assertEquals("PENDING_MATCH", payload.get(0).get("position_sync_status"));
        assertEquals("PARTIAL", payload.get(1).get("match_status"));
        assertEquals("DIFF", payload.get(1).get("position_sync_status"));
        assertEquals("EXECUTED", payload.get(2).get("match_status"));
        assertEquals("MATCH", payload.get(2).get("position_sync_status"));
    }

    @Test
    void executionFeedbackDetailsDerivesActionAndMatchedExecutions()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(this::answerExecutionFeedbackQueries);

        List<Map<String, Object>> payload = service.executionFeedbackDetails(10);

        assertEquals(4, payload.size());
        assertEquals("RECORD_EXECUTION", payload.get(0).get("feedback_action"));
        assertEquals(List.of(), payload.get(0).get("matched_execution_ids"));
        assertEquals("COMPLETE_PARTIAL_EXECUTION", payload.get(1).get("feedback_action"));
        assertEquals(List.of(9001L, 9002L), payload.get(1).get("matched_execution_ids"));
        assertEquals("CANCELLED_CONFIRMED", payload.get(2).get("feedback_action"));
        assertEquals("NO_ACTION", payload.get(3).get("feedback_action"));
    }

    @Test
    void positionSyncResultBuildsDifferenceItems()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(anyString())).thenReturn(
                List.of(
                        Map.of("stock_code", "000001", "quantity", 100, "cost_price", 10.0),
                        Map.of("stock_code", "000002", "quantity", 200, "cost_price", 20.0)));
        when(jdbcTemplate.queryForList(anyString(), (Object[]) any())).thenReturn(
                List.of(
                        Map.of("stock_code", "000001", "quantity", 100, "cost_price", 10.0),
                        Map.of("stock_code", "000002", "quantity", 150, "cost_price", 20.0),
                        Map.of("stock_code", "000003", "quantity", 50, "cost_price", 30.0)));

        Map<String, Object> payload = service.positionSyncResult(1L, null);

        assertEquals("DIFF", payload.get("syncStatus"));
        assertEquals(2, payload.get("differenceCount"));
        List<?> differenceItems = (List<?>) payload.get("differenceItems");
        assertEquals(2, differenceItems.size());
    }

    @Test
    void positionSyncResultSupportsStrategyAndStockFilters()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForList(
                eq("SELECT p.stock_code, p.quantity, p.cost_price FROM position p WHERE p.stock_code = ? ORDER BY p.stock_code"),
                eq("600519")))
                .thenReturn(List.of(Map.of("stock_code", "600519", "quantity", 1400, "cost_price", 1688.0)));
        when(jdbcTemplate.queryForList(startsWith("SELECT er.stock_code"), eq(1L), eq("600519")))
                .thenReturn(List.of(Map.of("stock_code", "600519", "quantity", 100, "cost_price", 1688.0)));

        Map<String, Object> payload = service.positionSyncResult(1L, "600519");

        assertEquals("DIFF", payload.get("syncStatus"));
        assertEquals(1, payload.get("differenceCount"));
    }

    @Test
    void confirmExecutionMatchBindsSignal()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        Map<String, Object> executionRow = new LinkedHashMap<>();
        executionRow.put("id", 8001L);
        executionRow.put("stock_code", "000001");
        executionRow.put("side", "BUY");
        executionRow.put("strategy_id", 1L);
        executionRow.put("signal_id", null);
        when(jdbcTemplate.queryForMap(anyString(), eq(8001L))).thenReturn(executionRow);
        when(jdbcTemplate.queryForMap(anyString(), eq(5001L))).thenReturn(Map.of(
                "id", 5001L,
                "stock_code", "000001",
                "signal_type", "BUY",
                "strategy_id", 1L));
        Map<String, Object> payload = service.confirmExecutionMatch(5001L, 8001L, "tester", "manual");

        verify(jdbcTemplate).update(anyString(), eq(5001L), eq(8001L));
        verify(jdbcTemplate).update(anyString(), eq(5001L));
        assertTrue((Boolean) payload.get("matchConfirmed"));
    }

    @Test
    void asyncWorkerSummaryDerivesWorkerHealth()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM quant_async_job_shard WHERE status = 'QUEUED'"),
                eq(Long.class))).thenReturn(3L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM quant_async_job_shard WHERE status = 'RUNNING'"),
                eq(Long.class))).thenReturn(2L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM quant_async_job_shard WHERE status = 'FAILED'"),
                eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM quant_async_job_shard WHERE status = 'RUNNING' AND lease_expires_at IS NOT NULL AND lease_expires_at < NOW()"),
                eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM quant_async_job WHERE status IN ('QUEUED', 'PENDING')"),
                eq(Long.class))).thenReturn(2L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM quant_async_job WHERE status = 'RUNNING'"),
                eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(DISTINCT lease_owner) FROM quant_async_job_shard WHERE status = 'RUNNING' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL AND lease_expires_at >= NOW()"),
                eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(startsWith("SELECT lease_owner AS worker_id")))
                .thenReturn(List.of(Map.of(
                        "worker_id", "worker-01",
                        "running_shard_count", 2L,
                        "latest_heartbeat_at", "2026-05-07T10:00:00",
                        "latest_lease_expires_at", "2026-05-07T10:01:00")));

        Map<String, Object> payload = service.asyncWorkerSummary();

        assertEquals("ACTIVE", payload.get("status"));
        assertEquals(3L, payload.get("queuedShardCount"));
        assertEquals(1L, payload.get("activeWorkerCount"));
        assertEquals(2L, payload.get("pendingJobCount"));
        assertEquals(1, ((List<?>) payload.get("workers")).size());
    }

    @Test
    void markExecutionExceptionUpsertsFeedbackRemark()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadQueryService service = new QuantRoadQueryService(jdbcTemplate);

        when(jdbcTemplate.queryForMap(anyString(), eq(5001L))).thenReturn(new LinkedHashMap<>(Map.of(
                "id", 5001L,
                "stock_code", "000001",
                "signal_date", LocalDate.of(2026, 5, 6),
                "strategy_id", 1L,
                "is_execute", 0)));

        Map<String, Object> payload = service.markExecutionException(5001L, "MISSED", "broker reject", "tester");
        long overdueDays = Math.max(0, ChronoUnit.DAYS.between(LocalDate.of(2026, 5, 7), LocalDate.now()));

        verify(jdbcTemplate).update(startsWith("INSERT INTO signal_execution_feedback"),
                eq(5001L),
                eq(LocalDate.of(2026, 5, 6)),
                eq(LocalDate.of(2026, 5, 7)),
                eq(LocalDate.now()),
                eq("MISSED"),
                eq(0),
                eq(null),
                eq((int) overdueDays),
                eq("[EXCEPTION:MISSED][tester] broker reject"));
        verify(jdbcTemplate).update(startsWith("UPDATE trade_signal SET is_execute = 0"), eq(5001L));
        assertEquals(5001L, payload.get("signalId"));
        assertEquals("MISSED", payload.get("feedbackStatus"));
    }

    private List<Map<String, Object>> answerExecutionRecordQueries(InvocationOnMock invocation)
    {
        String sql = invocation.getArgument(0, String.class);
        if (sql.contains("FROM execution_record e"))
        {
            return List.of(
                    row(
                            "id", 9003L,
                            "stock_code", "000001",
                            "side", "BUY",
                            "quantity", 100,
                            "price", 10.1,
                            "trade_date", LocalDate.of(2026, 5, 7),
                            "strategy_id", 1L,
                            "commission", 0.1,
                            "tax", 0.0,
                            "slippage", 0.0,
                            "gross_amount", 1010.0,
                            "net_amount", 1010.1,
                            "external_order_id", "ord-003",
                            "create_time", "2026-05-07 10:00:00",
                            "strategy_name", "MA20_CROSS",
                            "signal_type", "BUY",
                            "feedback_status", null,
                            "feedback_executed_quantity", null,
                            "signal_executed", 0),
                    row(
                            "id", 9002L,
                            "stock_code", "000002",
                            "side", "BUY",
                            "quantity", 100,
                            "price", 11.1,
                            "trade_date", LocalDate.of(2026, 5, 7),
                            "strategy_id", 1L,
                            "signal_id", 5002L,
                            "commission", 0.1,
                            "tax", 0.0,
                            "slippage", 0.0,
                            "gross_amount", 1110.0,
                            "net_amount", 1110.1,
                            "external_order_id", "ord-002",
                            "create_time", "2026-05-07 10:02:00",
                            "strategy_name", "MA20_CROSS",
                            "signal_type", "BUY",
                            "feedback_status", "EXECUTED",
                            "feedback_executed_quantity", 100,
                            "signal_executed", 0),
                    row(
                            "id", 9001L,
                            "stock_code", "000003",
                            "side", "BUY",
                            "quantity", 200,
                            "price", 12.1,
                            "trade_date", LocalDate.of(2026, 5, 7),
                            "strategy_id", 1L,
                            "signal_id", 5003L,
                            "commission", 0.1,
                            "tax", 0.0,
                            "slippage", 0.0,
                            "gross_amount", 2420.0,
                            "net_amount", 2420.1,
                            "external_order_id", "ord-001",
                            "create_time", "2026-05-07 10:03:00",
                            "strategy_name", "MA20_CROSS",
                            "signal_type", "BUY",
                            "feedback_status", "EXECUTED",
                            "feedback_executed_quantity", 200,
                            "signal_executed", 1));
        }
        if (sql.contains("FROM position p WHERE p.stock_code IN"))
        {
            return List.of(
                    Map.of("stock_code", "000002", "quantity", 120, "cost_price", 11.1),
                    Map.of("stock_code", "000003", "quantity", 200, "cost_price", 12.1));
        }
        if (sql.contains("FROM execution_record er") && sql.contains("GROUP BY er.stock_code"))
        {
            return List.of(
                    Map.of("stock_code", "000002", "quantity", 100, "cost_price", 11.1),
                    Map.of("stock_code", "000003", "quantity", 200, "cost_price", 12.1));
        }
        return List.of();
    }

    private List<Map<String, Object>> answerExecutionFeedbackQueries(InvocationOnMock invocation)
    {
        String sql = invocation.getArgument(0, String.class);
        if (sql.contains("FROM signal_execution_feedback f"))
        {
            return List.of(
                    row(
                            "signal_id", 5001L,
                            "signal_date", LocalDate.of(2026, 5, 6),
                            "due_date", LocalDate.of(2026, 5, 7),
                            "check_date", LocalDate.of(2026, 5, 7),
                            "status", "PENDING",
                            "executed_quantity", 0,
                            "overdue_days", 0,
                            "remark", "waiting_execution_until=2026-05-07",
                            "stock_code", "000001",
                            "stock_name", "PingAn",
                            "signal_type", "BUY",
                            "strategy_id", 1L,
                            "signal_executed", 0),
                    row(
                            "signal_id", 5002L,
                            "signal_date", LocalDate.of(2026, 5, 6),
                            "due_date", LocalDate.of(2026, 5, 7),
                            "check_date", LocalDate.of(2026, 5, 7),
                            "status", "EXECUTED",
                            "executed_quantity", 100,
                            "overdue_days", 0,
                            "remark", "executed_quantity=100",
                            "stock_code", "000002",
                            "stock_name", "Vanke",
                            "signal_type", "BUY",
                            "strategy_id", 1L,
                            "signal_executed", 0),
                    row(
                            "signal_id", 5003L,
                            "signal_date", LocalDate.of(2026, 5, 6),
                            "due_date", LocalDate.of(2026, 5, 7),
                            "check_date", LocalDate.of(2026, 5, 7),
                            "status", "MISSED",
                            "executed_quantity", 0,
                            "overdue_days", 0,
                            "remark", "[EXCEPTION:CANCELLED][tester] broker cancelled",
                            "stock_code", "000003",
                            "stock_name", "Kweichow",
                            "signal_type", "SELL",
                            "strategy_id", 1L,
                            "signal_executed", 0),
                    row(
                            "signal_id", 5004L,
                            "signal_date", LocalDate.of(2026, 5, 6),
                            "due_date", LocalDate.of(2026, 5, 7),
                            "check_date", LocalDate.of(2026, 5, 7),
                            "status", "EXECUTED",
                            "executed_quantity", 200,
                            "overdue_days", 0,
                            "remark", "executed_quantity=200",
                            "stock_code", "000004",
                            "stock_name", "BYD",
                            "signal_type", "BUY",
                            "strategy_id", 1L,
                            "signal_executed", 1));
        }
        if (sql.contains("FROM execution_record") && sql.contains("signal_id IN"))
        {
            return List.of(
                    Map.of("signal_id", 5002L, "matched_execution_ids", "9001,9002"),
                    Map.of("signal_id", 5004L, "matched_execution_ids", "9004"));
        }
        return List.of();
    }

    private LinkedHashMap<String, Object> row(Object... items)
    {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < items.length; i += 2)
        {
            result.put(String.valueOf(items[i]), items[i + 1]);
        }
        return result;
    }
}
