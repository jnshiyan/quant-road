package com.ruoyi.web.service.quant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import com.ruoyi.web.core.config.QuantRoadPythonProperties;

class QuantAsyncWorkerAutoServiceTest
{
    @Test
    void autoDrainTickRecoversExpiredShardsAndLaunchesWorkersUpToCapacity()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        properties.setAutoWorkerEnabled(true);
        properties.setAutoWorkerRecoverExpired(true);
        properties.setAutoWorkerRecoverLimit(50);
        properties.setAutoWorkerMaxConcurrentWorkers(2);
        properties.setAutoWorkerIdPrefix("ruoyi-auto-worker");
        Executor directExecutor = Runnable::run;

        when(queryService.asyncWorkerSummary()).thenReturn(Map.of(
                "queuedShardCount", 5L,
                "activeWorkerCount", 0L,
                "expiredShardCount", 3L));

        QuantAsyncWorkerAutoService service = new QuantAsyncWorkerAutoService(
                queryService,
                pythonService,
                properties,
                directExecutor);

        service.autoDrainTick();

        verify(pythonService, times(1)).recoverAsyncShards(50);
        verify(pythonService, times(1)).runAsyncWorkerOnce("ruoyi-auto-worker-1");
        verify(pythonService, times(1)).runAsyncWorkerOnce("ruoyi-auto-worker-2");
    }

    @Test
    void autoDrainTickSkipsLaunchWhenActiveWorkersAlreadyAtLimit()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadPythonService pythonService = mock(QuantRoadPythonService.class);
        QuantRoadPythonProperties properties = new QuantRoadPythonProperties();
        properties.setAutoWorkerEnabled(true);
        properties.setAutoWorkerMaxConcurrentWorkers(2);
        Executor directExecutor = Runnable::run;

        when(queryService.asyncWorkerSummary()).thenReturn(Map.of(
                "queuedShardCount", 8L,
                "activeWorkerCount", 2L,
                "expiredShardCount", 0L));

        QuantAsyncWorkerAutoService service = new QuantAsyncWorkerAutoService(
                queryService,
                pythonService,
                properties,
                directExecutor);

        service.autoDrainTick();

        verify(pythonService, never()).recoverAsyncShards(100);
        verify(pythonService, never()).runAsyncWorkerOnce(org.mockito.ArgumentMatchers.anyString());
    }
}
