package com.ruoyi.web.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.ruoyi.web.domain.quant.QuantExecutionResponse;
import com.ruoyi.web.domain.quant.QuantJobRequest;
import com.ruoyi.web.service.quant.QuantExecutionFacade;
import com.ruoyi.web.service.quant.QuantRoadPythonService;

class QuantRoadTaskTest
{
    @Test
    void fullDailyAsyncRoutesThroughUnifiedExecutionFacade()
    {
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        when(executionFacade.execute(any())).thenReturn(new QuantExecutionResponse());
        QuantRoadTask task = new QuantRoadTask(pythonService, executionFacade);

        task.fullDailyAsync();

        ArgumentCaptor<QuantJobRequest> requestCaptor = ArgumentCaptor.forClass(QuantJobRequest.class);
        verify(executionFacade).execute(requestCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("async", requestCaptor.getValue().getRequestedMode());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, requestCaptor.getValue().getUsePortfolio());
        org.junit.jupiter.api.Assertions.assertEquals("quartz", requestCaptor.getValue().getActor());
    }

    @Test
    void runPortfolioAsyncRoutesThroughUnifiedExecutionFacade()
    {
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantExecutionFacade executionFacade = mock(QuantExecutionFacade.class);
        when(executionFacade.execute(any())).thenReturn(new QuantExecutionResponse());
        QuantRoadTask task = new QuantRoadTask(pythonService, executionFacade);

        task.runPortfolioAsync("2024-01-01", 120000D);

        ArgumentCaptor<QuantJobRequest> requestCaptor = ArgumentCaptor.forClass(QuantJobRequest.class);
        verify(executionFacade).execute(requestCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("async", requestCaptor.getValue().getRequestedMode());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, requestCaptor.getValue().getUsePortfolio());
        org.junit.jupiter.api.Assertions.assertEquals("quartz", requestCaptor.getValue().getActor());
        org.junit.jupiter.api.Assertions.assertEquals("2024-01-01", requestCaptor.getValue().getStrategyBacktestStartDate());
        org.junit.jupiter.api.Assertions.assertEquals(120000D, requestCaptor.getValue().getPortfolioTotalCapital());
    }
}
