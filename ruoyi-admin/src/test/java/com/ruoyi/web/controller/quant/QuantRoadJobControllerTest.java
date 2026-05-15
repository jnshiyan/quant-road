package com.ruoyi.web.controller.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.InOrder;
import com.ruoyi.web.domain.quant.QuantAsyncJobResponse;
import com.ruoyi.web.domain.quant.QuantAsyncJobStatusResponse;
import com.ruoyi.web.domain.quant.QuantExecutionResponse;
import com.ruoyi.web.service.quant.QuantRoadQueryService;
import com.ruoyi.web.service.quant.QuantAsyncExecutionFacade;
import com.ruoyi.web.service.quant.QuantExecutionFacade;
import com.ruoyi.web.service.quant.QuantJobPlannerService;
import com.ruoyi.web.service.quant.QuantRoadPythonService;
import com.ruoyi.web.service.quant.QuantRoadSymbolScopeService;

class QuantRoadJobControllerTest
{
    @Test
    void fullDailyResolvesEtfScopeSymbolsBeforeInvokingPython() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.resolveScopeSymbols(
                eq("etf_pool"),
                eq("ETF_CORE"),
                eq(List.of()),
                eq(List.of()),
                eq(List.of()),
                eq(List.of())))
                .thenReturn(List.of("510300", "510500"));
        when(pythonService.fullDaily(
                eq(1L),
                eq("20210509"),
                eq("2021-05-09"),
                eq(true),
                eq(true),
                eq(100000.0),
                eq("ruoyi-ui"),
                any(),
                eq("etf_pool"),
                eq("ETF_CORE")))
                .thenReturn("ok");

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        pythonService,
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        String body = """
                {
                  "strategyId": 1,
                  "startDate": "20210509",
                  "strategyBacktestStartDate": "2021-05-09",
                  "notify": true,
                  "usePortfolio": true,
                  "portfolioTotalCapital": 100000.0,
                  "actor": "ruoyi-ui",
                  "scopeType": "etf_pool",
                  "scopePoolCode": "ETF_CORE",
                  "symbols": [],
                  "whitelist": [],
                  "blacklist": [],
                  "adHocSymbols": []
                }
                """;

        mockMvc.perform(post("/quant/jobs/fullDaily").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("ok"))
                .andExpect(header().string("X-Quant-Legacy-Endpoint", "fullDaily"))
                .andExpect(header().string("X-Quant-Replacement-Endpoint", "/quant/jobs/execute"))
                .andExpect(header().string("Warning", Matchers.containsString("legacy compatibility endpoint")));

        verify(symbolScopeService).resolveScopeSymbols(
                eq("etf_pool"),
                eq("ETF_CORE"),
                eq(List.of()),
                eq(List.of()),
                eq(List.of()),
                eq(List.of()));
        verify(pythonService).fullDaily(
                1L,
                "20210509",
                "2021-05-09",
                true,
                true,
                100000.0,
                "ruoyi-ui",
                List.of("510300", "510500"),
                "etf_pool",
                "ETF_CORE");
    }

    @Test
    void fullDailyKeepsLegacyBehaviorWhenAllStocksScopeIsSelected() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(pythonService.fullDaily(
                eq(1L),
                eq("20210509"),
                eq("2021-05-09"),
                eq(true),
                eq(true),
                eq(100000.0),
                eq("ruoyi-ui"),
                any(),
                any(),
                any()))
                .thenReturn("legacy");

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        pythonService,
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        String body = """
                {
                  "strategyId": 1,
                  "startDate": "20210509",
                  "strategyBacktestStartDate": "2021-05-09",
                  "notify": true,
                  "usePortfolio": true,
                  "portfolioTotalCapital": 100000.0,
                  "actor": "ruoyi-ui",
                  "scopeType": "all_stocks",
                  "symbols": [],
                  "whitelist": [],
                  "blacklist": [],
                  "adHocSymbols": []
                }
                """;

        mockMvc.perform(post("/quant/jobs/fullDaily").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("legacy"))
                .andExpect(header().string("X-Quant-Legacy-Endpoint", "fullDaily"));

        verify(symbolScopeService, never()).resolveScopeSymbols(any(), any(), any(), any(), any(), any());
        verify(pythonService).fullDaily(
                1L,
                "20210509",
                "2021-05-09",
                true,
                true,
                100000.0,
                "ruoyi-ui",
                null,
                null,
                null);
    }

    @Test
    void submitRunStrategyAutoReturnsAsyncWhenEstimateExceedsBudget() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        QuantAsyncJobResponse response = new QuantAsyncJobResponse();
        response.setJobId(1001L);
        response.setResolvedMode("async");
        response.setStatus("QUEUED");
        when(facade.submitLegacyRunStrategy(any())).thenReturn(response);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        mock(QuantRoadPythonService.class),
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        String body = """
                {
                  "requestedMode": "auto",
                  "strategyId": 1,
                  "strategyBacktestStartDate": "2023-01-01",
                  "actor": "planner-test"
                }
                """;

        mockMvc.perform(post("/quant/jobs/runStrategy").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Quant-Legacy-Endpoint", "runStrategy"))
                .andExpect(jsonPath("$.data.resolvedMode").value("async"))
                .andExpect(jsonPath("$.data.jobId").value(1001));
    }

    @Test
    void getJobStatusReturnsPersistedStatus() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        QuantAsyncJobStatusResponse response = new QuantAsyncJobStatusResponse();
        response.setJobId(1001L);
        response.setStatus("RUNNING");
        response.setPlannedShardCount(2);
        response.setCompletedShardCount(1);
        when(planner.getJobStatus(1001L)).thenReturn(response);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        mock(QuantRoadPythonService.class),
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        mockMvc.perform(get("/quant/jobs/status/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.completedShardCount").value(1));
    }

    @Test
    void confirmExecutionMatchReturnsConfirmationPayload() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(queryService.confirmExecutionMatch(any(), any(), any(), any())).thenReturn(new java.util.LinkedHashMap<>(java.util.Map.of(
                "matchConfirmed", true,
                "signal", java.util.Map.of("strategyId", 1, "stockCode", "000001"))));
        when(queryService.executionReconciliationSummary()).thenReturn(java.util.Map.of("pendingSignalCount", 0));
        when(queryService.positionSyncResult(1L, "000001")).thenReturn(java.util.Map.of("syncStatus", "DIFF"));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        pythonService,
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        String body = """
                {
                  "signalId": 5001,
                  "executionRecordId": 8001,
                  "actor": "tester",
                  "remark": "manual bind"
                }
                """;

        mockMvc.perform(post("/quant/jobs/confirmExecutionMatch").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchConfirmed").value(true))
                .andExpect(jsonPath("$.data.executionReconciliationSummary.pendingSignalCount").value(0))
                .andExpect(jsonPath("$.data.positionSyncResult.syncStatus").value("DIFF"));

        InOrder inOrder = inOrder(queryService, pythonService);
        inOrder.verify(queryService).confirmExecutionMatch(any(), any(), any(), any());
        inOrder.verify(pythonService).evaluateExecutionFeedback(null, 1);
        inOrder.verify(queryService).executionReconciliationSummary();
        inOrder.verify(queryService).positionSyncResult(eq(1L), eq("000001"));
    }

    @Test
    void recoverBatchReturnsRecoveryPayload() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(pythonService.recoverBatch(12L, "tester")).thenReturn("recovered");
        when(queryService.jobReadiness(12L)).thenReturn(Map.of("status", "READY"));
        when(queryService.jobErrorCategories(12L)).thenReturn(List.of());
        when(queryService.jobSopHints(12L)).thenReturn(List.of());

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        pythonService,
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        mockMvc.perform(post("/quant/jobs/recoverBatch/12").param("actor", "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value(12))
                .andExpect(jsonPath("$.data.output").value("recovered"))
                .andExpect(jsonPath("$.data.jobReadiness.status").value("READY"));
    }

    @Test
    void runAsyncWorkerOnceReturnsWorkerSummary() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(pythonService.runAsyncWorkerOnce("worker-01")).thenReturn("{\"status\":\"SUCCESS\"}");
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of("status", "ACTIVE"));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        pythonService,
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        mockMvc.perform(post("/quant/jobs/runAsyncWorkerOnce").param("workerId", "worker-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workerId").value("worker-01"))
                .andExpect(jsonPath("$.data.output").value("{\"status\":\"SUCCESS\"}"))
                .andExpect(jsonPath("$.data.asyncWorkerSummary.status").value("ACTIVE"));
    }

    @Test
    void recoverAsyncShardsReturnsWorkerSummary() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(pythonService.recoverAsyncShards(20)).thenReturn("{\"count\":2}");
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of("status", "DEGRADED"));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        pythonService,
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        mockMvc.perform(post("/quant/jobs/recoverAsyncShards").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limit").value(20))
                .andExpect(jsonPath("$.data.output").value("{\"count\":2}"))
                .andExpect(jsonPath("$.data.asyncWorkerSummary.status").value("DEGRADED"));
    }

    @Test
    void validateExecutionImportReturnsPreviewPayload() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(pythonService.validateExecutionImport("D:\\data\\executions.csv", 1L)).thenReturn("""
                {"totalRows":3,"validRows":2,"invalidRows":1,"duplicateRows":0,"unmatchedSignalRows":1,"canImport":false}
                """);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        pythonService,
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        mockMvc.perform(post("/quant/jobs/validateExecutionImport")
                .param("file", "D:\\data\\executions.csv")
                .param("strategyId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.validRows").value(2))
                .andExpect(jsonPath("$.data.invalidRows").value(1))
                .andExpect(jsonPath("$.data.unmatchedSignalRows").value(1))
                .andExpect(jsonPath("$.data.canImport").value(false));
    }

    @Test
    void importExecutionsReturnsStructuredPayload() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(pythonService.importExecutions("D:\\data\\executions.csv", 1L)).thenReturn("""
                {"appliedRows":2,"importedUnmatchedRows":1,"needsManualMatch":true,"unmatchedPreviewRows":[{"stockCode":"000001","side":"BUY"}]}
                """);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        pythonService,
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        mockMvc.perform(post("/quant/jobs/importExecutions")
                .param("file", "D:\\data\\executions.csv")
                .param("strategyId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedRows").value(2))
                .andExpect(jsonPath("$.data.importedUnmatchedRows").value(1))
                .andExpect(jsonPath("$.data.needsManualMatch").value(true))
                .andExpect(jsonPath("$.data.unmatchedPreviewRows[0].stockCode").value("000001"));
    }

    @Test
    void markExecutionExceptionReturnsUpdatedSummary() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(queryService.markExecutionException(5001L, "MISSED", "broker reject", "tester"))
                .thenReturn(new java.util.LinkedHashMap<>(java.util.Map.of(
                        "signalId", 5001L,
                        "feedbackStatus", "MISSED",
                        "remark", "[EXCEPTION:MISSED] broker reject")));
        when(queryService.executionReconciliationSummary()).thenReturn(java.util.Map.of("missedSignalCount", 2));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        pythonService,
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        String body = """
                {
                  "signalId": 5001,
                  "exceptionType": "MISSED",
                  "remark": "broker reject",
                  "actor": "tester"
                }
                """;

        mockMvc.perform(post("/quant/jobs/markExecutionException").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signalId").value(5001))
                .andExpect(jsonPath("$.data.feedbackStatus").value("MISSED"))
                .andExpect(jsonPath("$.data.executionReconciliationSummary.missedSignalCount").value(2));
    }

    @Test
    void executeShouldDelegateUnifiedRequest() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        QuantExecutionResponse response = new QuantExecutionResponse();
        response.setStatus("QUEUED");
        response.setResolvedExecutionMode("async");
        response.setPlanSummary("sync-daily -> run-strategy -> evaluate-risk");
        when(executionFacade.execute(any())).thenReturn(response);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        pythonService,
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        mockMvc.perform(post("/quant/jobs/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "strategyId": 1,
                          "scopeType": "etf_pool",
                          "scopePoolCode": "ETF_CORE",
                          "strategyBacktestStartDate": "2023-01-01",
                          "actor": "plan-test"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.resolvedExecutionMode").value("async"));

        ArgumentCaptor<com.ruoyi.web.domain.quant.QuantJobRequest> requestCaptor =
                ArgumentCaptor.forClass(com.ruoyi.web.domain.quant.QuantJobRequest.class);
        verify(executionFacade, times(1)).execute(requestCaptor.capture());
        assertEquals(1L, requestCaptor.getValue().getStrategyId());
        assertEquals("etf_pool", requestCaptor.getValue().getScopeType());
        assertEquals("ETF_CORE", requestCaptor.getValue().getScopePoolCode());
        assertEquals("2023-01-01", requestCaptor.getValue().getStrategyBacktestStartDate());
        assertEquals("plan-test", requestCaptor.getValue().getActor());
    }

    @Test
    void executeShouldReturnTriggerModeAndTimeRange() throws Exception
    {
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantAsyncExecutionFacade facade = mock(QuantAsyncExecutionFacade.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        QuantExecutionResponse response = new QuantExecutionResponse();
        response.setExecutionId(101L);
        response.setStatus("QUEUED");
        response.setResolvedExecutionMode("async");
        when(executionFacade.execute(any())).thenReturn(response);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadJobController(
                        pythonService,
                        planner,
                        facade,
                        executionFacade,
                        queryService,
                        symbolScopeService))
                .build();

        mockMvc.perform(post("/quant/jobs/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "strategyId": 1,
                          "scopeType": "etf_pool",
                          "scopePoolCode": "ETF_CORE",
                          "strategyBacktestStartDate": "2024-01-01",
                          "endDate": "2026-05-10",
                          "actor": "ruoyi-ui"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolvedExecutionMode").value("async"))
                .andExpect(jsonPath("$.data.triggerMode").value("manual"))
                .andExpect(jsonPath("$.data.timeRange.startDate").value("2024-01-01"))
                .andExpect(jsonPath("$.data.timeRange.endDate").value("2026-05-10"));
    }
}
