package com.ruoyi.web.service.quant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.core.config.QuantRoadPythonProperties;
import com.ruoyi.web.domain.quant.QuantExecutionPlan;
import com.ruoyi.web.domain.quant.QuantExecutionPlanStep;
import com.ruoyi.web.domain.quant.QuantExecutionResponse;
import com.ruoyi.web.domain.quant.QuantJobRequest;

class QuantExecutionEngineServiceTest
{
    @Test
    void executeSyncPlanUsesPythonBridge()
    {
        QuantJobPlannerService plannerService = mock(QuantJobPlannerService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantExecutionEngineService engine = new QuantExecutionEngineService(plannerService, pythonService);

        QuantExecutionPlan plan = new QuantExecutionPlan();
        plan.setResolvedExecutionMode("sync");
        plan.setPlanSummary("mode=sync, scope=etf_pool");
        plan.setEstimatedCost(Map.of("symbolCount", 2, "stepCount", 3));

        QuantJobRequest request = new QuantJobRequest();
        request.setStrategyId(1L);
        request.setStrategyBacktestStartDate("2023-01-01");
        request.setActor("engine-test");

        when(pythonService.executePlan(plan, request)).thenReturn("sync-output");

        QuantExecutionResponse response = engine.execute(plan, request);

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("sync", response.getResolvedExecutionMode());
        assertEquals("mode=sync, scope=etf_pool", response.getPlanSummary());
        assertEquals(Map.of("symbolCount", 2, "stepCount", 3), response.getEstimatedCost());
        verify(pythonService).executePlan(plan, request);
        verify(plannerService, never()).submitExecutionPlan(plan, request);
    }

    @Test
    void executeAsyncPlanSubmitsViaPlanner()
    {
        QuantJobPlannerService plannerService = mock(QuantJobPlannerService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantExecutionEngineService engine = new QuantExecutionEngineService(plannerService, pythonService);

        QuantExecutionPlan plan = new QuantExecutionPlan();
        plan.setResolvedExecutionMode("async");
        plan.setPlanSummary("mode=async, scope=all_stocks");

        QuantJobRequest request = new QuantJobRequest();
        request.setRequestedMode("auto");
        request.setActor("engine-test");

        QuantExecutionResponse plannerResponse = new QuantExecutionResponse();
        plannerResponse.setExecutionId(1001L);
        plannerResponse.setStatus("QUEUED");
        plannerResponse.setResolvedExecutionMode("async");
        plannerResponse.setPlanSummary("mode=async, scope=all_stocks");
        when(plannerService.submitExecutionPlan(plan, request)).thenReturn(plannerResponse);

        QuantExecutionResponse response = engine.execute(plan, request);

        assertSame(plannerResponse, response);
        verify(plannerService).submitExecutionPlan(plan, request);
        verify(pythonService, never()).executePlan(plan, request);
    }

    @Test
    void executeExplicitAsyncRequestUsesPlannerEvenWhenPlanDefaultsToSync()
    {
        QuantJobPlannerService plannerService = mock(QuantJobPlannerService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantExecutionEngineService engine = new QuantExecutionEngineService(plannerService, pythonService);

        QuantExecutionPlan plan = new QuantExecutionPlan();
        plan.setResolvedExecutionMode("sync");
        plan.setPlanSummary("mode=sync, scope=etf_pool");

        QuantJobRequest request = new QuantJobRequest();
        request.setRequestedMode("async");
        request.setActor("engine-test");

        QuantExecutionResponse plannerResponse = new QuantExecutionResponse();
        plannerResponse.setStatus("QUEUED");
        plannerResponse.setResolvedExecutionMode("async");
        when(plannerService.submitExecutionPlan(plan, request)).thenReturn(plannerResponse);

        QuantExecutionResponse response = engine.execute(plan, request);

        assertSame(plannerResponse, response);
        verify(plannerService).submitExecutionPlan(plan, request);
        verify(pythonService, never()).executePlan(plan, request);
    }

    @Test
    void executeExplicitSyncRequestRejectsAsyncResolvedPlan()
    {
        QuantJobPlannerService plannerService = mock(QuantJobPlannerService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantExecutionEngineService engine = new QuantExecutionEngineService(plannerService, pythonService);

        QuantExecutionPlan plan = new QuantExecutionPlan();
        plan.setResolvedExecutionMode("async");
        plan.setPlanSummary("mode=async, scope=all_stocks");

        QuantJobRequest request = new QuantJobRequest();
        request.setRequestedMode("sync");
        request.setActor("engine-test");

        ServiceException ex = assertThrows(ServiceException.class, () -> engine.execute(plan, request));

        assertEquals("sync execution budget exceeded; resubmit with requestedMode=async or auto", ex.getMessage());
        verify(plannerService, never()).submitExecutionPlan(plan, request);
        verify(pythonService, never()).executePlan(plan, request);
    }

    @Test
    void executeSyncPlanBridgesSupportedStepsSequentially()
    {
        RecordingQuantRoadPythonService pythonService = new RecordingQuantRoadPythonService();
        QuantExecutionEngineService engine = new QuantExecutionEngineService(mock(QuantJobPlannerService.class), pythonService);

        QuantExecutionPlan plan = new QuantExecutionPlan();
        plan.setResolvedExecutionMode("sync");
        plan.setPlanSummary("mode=sync, scope=all_stocks");
        plan.setEstimatedCost(Map.of("symbolCount", 3, "stepCount", 5));
        plan.setSteps(List.of(
                step("sync-basic"),
                step("sync-daily"),
                step("evaluate-market"),
                step("run-strategy"),
                step("evaluate-risk")));
        plan.setResolvedSymbols(List.of("510300", "159915"));

        QuantJobRequest request = new QuantJobRequest();
        request.setStrategyId(7L);
        request.setStartDate("20240101");
        request.setStrategyBacktestStartDate("2024-01-01");
        request.setActor("engine-test");

        QuantExecutionResponse response = engine.execute(plan, request);

        assertEquals("SUCCESS", response.getStatus());
        assertEquals(
                List.of(
                        "syncBasic",
                        "syncDaily:20240101",
                        "evaluateMarket:null",
                        "runStrategy:7:2024-01-01:[510300, 159915]",
                        "evaluateRisk:7"),
                pythonService.calls);
        assertEquals(0, pythonService.fullDailyCalls);
    }

    private static QuantExecutionPlanStep step(String stepName)
    {
        QuantExecutionPlanStep step = new QuantExecutionPlanStep();
        step.setStepName(stepName);
        step.setStepScope("scoped");
        return step;
    }

    private static class RecordingQuantRoadPythonService extends QuantRoadPythonService
    {
        private final List<String> calls = new ArrayList<>();
        private int fullDailyCalls;

        RecordingQuantRoadPythonService()
        {
            super(new QuantRoadPythonProperties());
        }

        @Override
        public String syncBasic()
        {
            calls.add("syncBasic");
            return "sync-basic";
        }

        @Override
        public String syncDaily(String startDate)
        {
            calls.add("syncDaily:" + startDate);
            return "sync-daily";
        }

        @Override
        public String evaluateMarket(Integer holdDays)
        {
            calls.add("evaluateMarket:" + holdDays);
            return "evaluate-market";
        }

        @Override
        public String runStrategy(Long strategyId, String strategyBacktestStartDate, Double portfolioCapital, String actor, List<String> symbols)
        {
            calls.add("runStrategy:" + strategyId + ":" + strategyBacktestStartDate + ":" + symbols);
            return "run-strategy";
        }

        @Override
        public String runPortfolio(String strategyBacktestStartDate, Double totalCapital, String actor, List<String> symbols)
        {
            calls.add("runPortfolio:" + strategyBacktestStartDate + ":" + symbols);
            return "run-portfolio";
        }

        @Override
        public String evaluateRisk(Long strategyId)
        {
            calls.add("evaluateRisk:" + strategyId);
            return "evaluate-risk";
        }

        @Override
        public String fullDaily(
                Long strategyId,
                String startDate,
                String strategyBacktestStartDate,
                Boolean notify,
                Boolean usePortfolio,
                Double portfolioTotalCapital,
                String actor,
                List<String> symbols,
                String scopeType,
                String scopePoolCode)
        {
            fullDailyCalls++;
            calls.add("fullDaily");
            return "full-daily";
        }
    }
}
