package com.ruoyi.web.service.quant;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.ruoyi.web.core.config.QuantRoadPythonProperties;

@Service
public class QuantAsyncWorkerAutoService
{
    private static final Logger log = LoggerFactory.getLogger(QuantAsyncWorkerAutoService.class);

    private final QuantRoadQueryService queryService;
    private final QuantRoadPythonService pythonService;
    private final QuantRoadPythonProperties properties;
    private final Executor executor;
    private final AtomicInteger launchInFlightCount = new AtomicInteger();
    private final AtomicInteger workerSequence = new AtomicInteger();

    @Autowired
    public QuantAsyncWorkerAutoService(
            QuantRoadQueryService queryService,
            QuantRoadPythonService pythonService,
            QuantRoadPythonProperties properties)
    {
        this(queryService, pythonService, properties, createExecutor(properties));
    }

    QuantAsyncWorkerAutoService(
            QuantRoadQueryService queryService,
            QuantRoadPythonService pythonService,
            QuantRoadPythonProperties properties,
            Executor executor)
    {
        this.queryService = queryService;
        this.pythonService = pythonService;
        this.properties = properties;
        this.executor = executor;
    }

    @Scheduled(
            initialDelayString = "${quant-road.python.autoWorkerInitialDelayMillis:15000}",
            fixedDelayString = "${quant-road.python.autoWorkerFixedDelayMillis:5000}")
    public void scheduledAutoDrainTick()
    {
        autoDrainTick();
    }

    public void autoDrainTick()
    {
        if (!properties.isAutoWorkerEnabled())
        {
            return;
        }

        Map<String, Object> summary = queryService.asyncWorkerSummary();
        long queuedShardCount = asLong(summary.get("queuedShardCount"));
        long expiredShardCount = asLong(summary.get("expiredShardCount"));

        if (properties.isAutoWorkerRecoverExpired() && expiredShardCount > 0)
        {
            int recoverLimit = Math.max(1, properties.getAutoWorkerRecoverLimit());
            log.info("Recovering expired async shards before auto-drain, expiredShardCount={}, limit={}", expiredShardCount, recoverLimit);
            pythonService.recoverAsyncShards(recoverLimit);
            queuedShardCount = Math.max(queuedShardCount, expiredShardCount);
        }

        if (queuedShardCount <= 0)
        {
            return;
        }

        int maxConcurrentWorkers = Math.max(1, properties.getAutoWorkerMaxConcurrentWorkers());
        int activeWorkerCount = (int) Math.max(0L, asLong(summary.get("activeWorkerCount")));
        int availableLaunchSlots = Math.max(0, maxConcurrentWorkers - activeWorkerCount - launchInFlightCount.get());
        int launchCount = (int) Math.min(queuedShardCount, (long) availableLaunchSlots);

        for (int index = 0; index < launchCount; index++)
        {
            submitWorkerLaunch();
        }
    }

    @PreDestroy
    void shutdownExecutor()
    {
        if (executor instanceof ExecutorService executorService)
        {
            executorService.shutdownNow();
        }
    }

    private void submitWorkerLaunch()
    {
        int workerNo = workerSequence.incrementAndGet();
        String workerId = properties.getAutoWorkerIdPrefix() + "-" + workerNo;
        launchInFlightCount.incrementAndGet();
        try
        {
            executor.execute(() -> {
                try
                {
                    log.info("Launching auto async worker {}", workerId);
                    pythonService.runAsyncWorkerOnce(workerId);
                }
                catch (RuntimeException ex)
                {
                    log.warn("Auto async worker {} failed: {}", workerId, ex.getMessage());
                }
                finally
                {
                    launchInFlightCount.decrementAndGet();
                }
            });
        }
        catch (RuntimeException ex)
        {
            launchInFlightCount.decrementAndGet();
            throw ex;
        }
    }

    private long asLong(Object value)
    {
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        if (value == null)
        {
            return 0L;
        }
        try
        {
            return Long.parseLong(String.valueOf(value));
        }
        catch (NumberFormatException ex)
        {
            return 0L;
        }
    }

    private static ExecutorService createExecutor(QuantRoadPythonProperties properties)
    {
        int maxConcurrentWorkers = Math.max(1, properties.getAutoWorkerMaxConcurrentWorkers());
        return Executors.newFixedThreadPool(maxConcurrentWorkers, runnable -> {
            Thread thread = new Thread(runnable, "quant-auto-worker-launcher");
            thread.setDaemon(true);
            return thread;
        });
    }
}
