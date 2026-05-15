package com.ruoyi.web.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.ruoyi.web.core.config.QuantRoadPythonProperties;
import com.ruoyi.web.domain.quant.QuantAsyncJobRequest;
import com.ruoyi.web.domain.quant.QuantAsyncJobResponse;
import com.ruoyi.web.domain.quant.QuantExecutionResponse;
import com.ruoyi.web.domain.quant.QuantJobRequest;

class QuantAsyncExecutionFacadeTest
{
    @Test
    void legacyRunPortfolioPayloadNormalizesToAutoMode()
    {
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        QuantAsyncExecutionFacade facade = new QuantAsyncExecutionFacade(mock(QuantJobPlannerService.class), mock(QuantRoadPythonService.class), properties, mock(QuantExecutionFacade.class));
        QuantJobRequest request = new QuantJobRequest();
        request.setStrategyBacktestStartDate("2023-01-01");
        request.setPortfolioTotalCapital(100000D);

        QuantAsyncJobRequest normalized = facade.normalizeRunPortfolio(request);

        assertEquals("auto", normalized.getRequestedMode());
        assertEquals("2023-01-01", normalized.getStrategyBacktestStartDate());
    }

    @Test
    void blankRunPortfolioPayloadFallsBackToRollingFiveYearWindow()
    {
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        QuantAsyncExecutionFacade facade = new QuantAsyncExecutionFacade(mock(QuantJobPlannerService.class), mock(QuantRoadPythonService.class), properties, mock(QuantExecutionFacade.class));
        QuantJobRequest request = new QuantJobRequest();
        request.setPortfolioTotalCapital(100000D);

        QuantAsyncJobRequest normalized = facade.normalizeRunPortfolio(request);

        assertEquals("auto", normalized.getRequestedMode());
        assertEquals(
                LocalDate.now().minusYears(5).format(DateTimeFormatter.ISO_LOCAL_DATE),
                normalized.getStrategyBacktestStartDate());
    }

    @Test
    void submitScheduledFullDailyBuildsAsyncPlannerRequest()
    {
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        QuantJobPlannerService planner = mock(QuantJobPlannerService.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantAsyncExecutionFacade facade = new QuantAsyncExecutionFacade(planner, mock(QuantRoadPythonService.class), properties, executionFacade);
        QuantExecutionResponse planned = new QuantExecutionResponse();
        planned.setExecutionId(88L);
        planned.setStatus("QUEUED");
        planned.setResolvedExecutionMode("async");
        when(executionFacade.execute(any())).thenReturn(planned);

        QuantAsyncJobResponse response = facade.submitScheduledFullDaily(null, 100000D, "quartz");

        ArgumentCaptor<QuantJobRequest> requestCaptor = ArgumentCaptor.forClass(QuantJobRequest.class);
        verify(executionFacade).execute(requestCaptor.capture());
        assertEquals(88L, response.getJobId());
        assertEquals("QUEUED", response.getStatus());
        assertEquals("async", requestCaptor.getValue().getRequestedMode());
        assertEquals(Boolean.TRUE, requestCaptor.getValue().getUsePortfolio());
        assertEquals("quartz", requestCaptor.getValue().getActor());
        assertEquals(100000D, requestCaptor.getValue().getPortfolioTotalCapital());
        assertEquals(
                LocalDate.now().minusYears(5).format(DateTimeFormatter.ISO_LOCAL_DATE),
                requestCaptor.getValue().getStrategyBacktestStartDate());
    }

    @Test
    void executeSyncPortfolioForwardsSymbolsToPythonBridge()
    {
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantAsyncExecutionFacade facade = new QuantAsyncExecutionFacade(mock(QuantJobPlannerService.class), pythonService, properties, mock(QuantExecutionFacade.class));
        QuantAsyncJobRequest request = new QuantAsyncJobRequest();
        request.setStrategyBacktestStartDate("2024-01-01");
        request.setPortfolioTotalCapital(50000D);
        request.setActor("bridge-test");
        request.setSymbols(java.util.List.of("510300", "159915"));
        when(pythonService.runPortfolio("2024-01-01", 50000D, "bridge-test", java.util.List.of("510300", "159915")))
                .thenReturn("sync-output");

        String output = facade.executeSyncPortfolio(request);

        assertEquals("sync-output", output);
        verify(pythonService).runPortfolio("2024-01-01", 50000D, "bridge-test", java.util.List.of("510300", "159915"));
    }

    @Test
    void submitLegacyRunStrategyForwardsToUnifiedExecutionFacade()
    {
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        QuantAsyncExecutionFacade facade = new QuantAsyncExecutionFacade(
                mock(QuantJobPlannerService.class),
                mock(QuantRoadPythonService.class),
                properties,
                executionFacade);
        QuantJobRequest request = new QuantJobRequest();
        request.setRequestedMode("auto");
        request.setStrategyId(1L);
        request.setScopeType("etf_pool");
        request.setScopePoolCode("ETF_CORE");
        QuantExecutionResponse executionResponse = new QuantExecutionResponse();
        executionResponse.setExecutionId(55L);
        executionResponse.setStatus("QUEUED");
        executionResponse.setResolvedExecutionMode("async");
        executionResponse.setPlanSummary("sync-daily -> run-strategy -> evaluate-risk");
        when(executionFacade.execute(any())).thenReturn(executionResponse);

        QuantAsyncJobResponse response = facade.submitLegacyRunStrategy(request);

        assertEquals(55L, response.getJobId());
        assertEquals("QUEUED", response.getStatus());
        assertEquals("async", response.getResolvedMode());
        assertEquals("submitted", response.getReason());
        assertEquals("sync-daily -> run-strategy -> evaluate-risk", response.getOutput());
        verify(executionFacade).execute(request);
    }
}
