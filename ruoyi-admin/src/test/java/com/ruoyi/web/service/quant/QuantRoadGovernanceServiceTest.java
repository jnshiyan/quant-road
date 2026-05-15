package com.ruoyi.web.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.web.domain.quant.QuantGovernanceDecisionRequest;

class QuantRoadGovernanceServiceTest
{
    @Test
    void shadowCompareSummaryDerivesGovernanceRecommendation()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadGovernanceService service = new QuantRoadGovernanceService(jdbcTemplate, pythonService);

        when(pythonService.shadowCompareJson(1L, 2L, 6)).thenReturn("""
                {
                  "baseline": {"strategy_id": 1, "strategy_name": "MA20_CROSS", "strategy_type": "MA"},
                  "candidate": {"strategy_id": 2, "strategy_name": "MA_DUAL_CROSS_5_20", "strategy_type": "MA_DUAL"},
                  "months": 6,
                  "generated_at": "2026-05-07",
                  "months_data": [
                    {
                      "month": "2026-05",
                      "baseline": {"avg_annual_return": 8.0, "avg_max_drawdown": -6.0, "avg_win_rate": 55.0, "invalid_rate": 10.0},
                      "candidate": {"avg_annual_return": 12.0, "avg_max_drawdown": -4.0, "avg_win_rate": 60.0, "invalid_rate": 0.0},
                      "delta": {"avg_annual_return": 4.0, "avg_max_drawdown": 2.0, "avg_win_rate": 5.0, "invalid_rate": -10.0}
                    },
                    {
                      "month": "2026-04",
                      "baseline": {"avg_annual_return": 7.0, "avg_max_drawdown": -5.0, "avg_win_rate": 52.0, "invalid_rate": 10.0},
                      "candidate": {"avg_annual_return": 9.0, "avg_max_drawdown": -4.0, "avg_win_rate": 55.0, "invalid_rate": 0.0},
                      "delta": {"avg_annual_return": 2.0, "avg_max_drawdown": 1.0, "avg_win_rate": 3.0, "invalid_rate": -10.0}
                    }
                  ],
                  "summary": {
                    "comparable_months": 2,
                    "candidate_better_annual_months": 2,
                    "candidate_lower_drawdown_months": 2,
                    "candidate_higher_win_rate_months": 2,
                    "candidate_lower_invalid_rate_months": 2
                  }
                }
                """);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), anyInt())).thenReturn(List.of(
                Map.of(
                        "run_date", Date.valueOf(LocalDate.of(2026, 5, 7)),
                        "recommendation", "promote_candidate",
                        "remark", "candidate stable across both comparable months")));

        Map<String, Object> payload = service.shadowCompareSummary(1L, 2L, 6);

        assertEquals("PROMOTE_CANDIDATE", payload.get("recommendation"));
        assertEquals("REPLACE", payload.get("governanceAction"));
        assertEquals("HIGH", payload.get("confidenceLevel"));
        assertTrue(String.valueOf(payload.get("recommendationReason")).contains("候选策略"));
        assertTrue(((List<?>) payload.get("coreEvidences")).size() >= 3);
    }

    @Test
    void submitGovernanceDecisionPersistsAuditableHistory()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadGovernanceService service = new QuantRoadGovernanceService(jdbcTemplate, pythonService);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1001L);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(10))).thenReturn(List.of(historyRow()));

        QuantGovernanceDecisionRequest request = new QuantGovernanceDecisionRequest();
        request.setBaselineStrategyId(1L);
        request.setCandidateStrategyId(2L);
        request.setMonths(6);
        request.setSystemRecommendation("PROMOTE_CANDIDATE");
        request.setGovernanceAction("REPLACE");
        request.setConfidenceLevel("HIGH");
        request.setApprovalStatus("APPROVED");
        request.setDecisionSource("shadow_compare");
        request.setActor("tester");
        request.setEffectiveFrom("2026-05-08");
        request.setRemark("candidate stable enough");
        request.setCoreEvidences(List.of("年化更优 2/2 月", "回撤更低 2/2 月"));
        request.setRiskNotes(List.of("样本窗口仍较短"));

        Map<String, Object> payload = service.submitGovernanceDecision(request);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();
        assertEquals(1L, args[0]);
        assertEquals(2L, args[1]);
        assertEquals("REPLACE", args[4]);
        assertEquals("tester", args[9]);
        assertEquals(1001L, payload.get("decisionId"));
        assertFalse(((List<?>) payload.get("history")).isEmpty());
    }

    @Test
    void governanceHistoryRecoversWhenTableIsMissing()
    {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadGovernanceService service = new QuantRoadGovernanceService(jdbcTemplate, pythonService);

        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(10)))
                .thenThrow(new BadSqlGrammarException(
                        "governanceHistory",
                        "SELECT * FROM quant_governance_decision",
                        new PSQLException("ERROR: relation \"quant_governance_decision\" does not exist", PSQLState.UNDEFINED_TABLE)))
                .thenReturn(List.of(historyRow()));

        List<Map<String, Object>> rows = service.governanceHistory(1L, 2L, 10);

        assertEquals(1, rows.size());
        verify(jdbcTemplate, atLeastOnce()).execute(contains("CREATE TABLE IF NOT EXISTS quant_governance_decision"));
    }

    private Map<String, Object> historyRow()
    {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1001L);
        row.put("baseline_strategy_id", 1L);
        row.put("candidate_strategy_id", 2L);
        row.put("months", 6);
        row.put("system_recommendation", "PROMOTE_CANDIDATE");
        row.put("governance_action", "REPLACE");
        row.put("confidence_level", "HIGH");
        row.put("approval_status", "APPROVED");
        row.put("decision_source", "shadow_compare");
        row.put("actor", "tester");
        row.put("effective_from", Date.valueOf(LocalDate.of(2026, 5, 8)));
        row.put("remark", "candidate stable enough");
        row.put("core_evidences", "[\"年化更优 2/2 月\"]");
        row.put("risk_notes", "[\"样本窗口仍较短\"]");
        row.put("create_time", "2026-05-07 12:00:00");
        return row;
    }
}
