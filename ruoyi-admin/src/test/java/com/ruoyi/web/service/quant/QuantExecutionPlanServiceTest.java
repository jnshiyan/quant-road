package com.ruoyi.web.service.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.web.domain.quant.QuantExecutionPlan;
import com.ruoyi.web.domain.quant.QuantExecutionPlanStep;
import com.ruoyi.web.domain.quant.QuantJobRequest;

class QuantExecutionPlanServiceTest
{
    @Test
    void buildPlanShouldPruneGlobalStepsForEtfPool()
    {
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.resolveScopeType("etf_pool")).thenReturn("etf_pool");
        when(symbolScopeService.resolveScopeSymbols(
                eq("etf_pool"),
                eq("ETF_CORE"),
                any(),
                any(),
                any(),
                any())).thenReturn(List.of("510300", "510500"));

        QuantExecutionPlanService service = new QuantExecutionPlanService(symbolScopeService);
        QuantJobRequest request = new QuantJobRequest();
        request.setStrategyId(1L);
        request.setScopeType("etf_pool");
        request.setScopePoolCode("ETF_CORE");
        request.setStrategyBacktestStartDate("2023-01-01");

        QuantExecutionPlan plan = service.buildPlan(request);

        assertThat(plan.getResolvedSymbols()).containsExactly("510300", "510500");
        assertThat(plan.getResolvedExecutionMode()).isEqualTo("sync");
        assertThat(plan.getPlanSummary()).isEqualTo(
                "mode=sync, scope=etf_pool, symbols=2, steps=sync-daily -> run-strategy -> evaluate-risk");
        assertThat(plan.getEstimatedCost()).containsEntry("symbolCount", 2)
                .containsEntry("stepCount", 3)
                .containsEntry("globalStepCount", 0)
                .containsEntry("scopedStepCount", 3);
        assertThat(plan.getSteps()).extracting(QuantExecutionPlanStep::getStepName)
                .containsExactly("sync-daily", "run-strategy", "evaluate-risk");
        assertThat(plan.getSteps()).extracting(QuantExecutionPlanStep::getStepName, QuantExecutionPlanStep::getStepScope)
                .containsExactly(
                        tuple("sync-daily", "scoped"),
                        tuple("run-strategy", "scoped"),
                        tuple("evaluate-risk", "scoped"));
        verify(symbolScopeService).resolveScopeSymbols(
                eq("etf_pool"),
                eq("ETF_CORE"),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void buildPlanShouldIncludeGlobalStepsForAllStocks()
    {
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.resolveScopeType("all_stocks")).thenReturn("all_stocks");
        when(symbolScopeService.resolveScopeSymbols(
                eq("all_stocks"),
                eq(null),
                any(),
                any(),
                any(),
                any())).thenReturn(List.of("000001", "000002", "000003", "000004"));

        QuantExecutionPlanService service = new QuantExecutionPlanService(symbolScopeService);
        QuantJobRequest request = new QuantJobRequest();
        request.setScopeType("all_stocks");
        request.setStrategyBacktestStartDate("2023-01-01");

        QuantExecutionPlan plan = service.buildPlan(request);

        assertThat(plan.getResolvedExecutionMode()).isEqualTo("async");
        assertThat(plan.getPlanSummary()).isEqualTo(
                "mode=async, scope=all_stocks, symbols=4, steps=sync-basic -> sync-daily -> evaluate-market -> run-strategy -> evaluate-risk");
        assertThat(plan.getEstimatedCost()).containsEntry("symbolCount", 4)
                .containsEntry("stepCount", 5)
                .containsEntry("globalStepCount", 3)
                .containsEntry("scopedStepCount", 2);
        assertThat(plan.getSteps()).extracting(QuantExecutionPlanStep::getStepName)
                .containsExactly("sync-basic", "sync-daily", "evaluate-market", "run-strategy", "evaluate-risk");
        assertThat(plan.getSteps()).extracting(QuantExecutionPlanStep::getStepName, QuantExecutionPlanStep::getStepScope)
                .containsExactly(
                        tuple("sync-basic", "global"),
                        tuple("sync-daily", "global"),
                        tuple("evaluate-market", "global"),
                        tuple("run-strategy", "scoped"),
                        tuple("evaluate-risk", "scoped"));
        verify(symbolScopeService).resolveScopeSymbols(
                eq("all_stocks"),
                eq(null),
                any(),
                any(),
                any(),
                any());
    }
}
