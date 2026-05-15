package com.ruoyi.web.core.config;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Quant Road Python 执行配置
 */
@Component
@ConfigurationProperties(prefix = "quant-road.python")
public class QuantRoadPythonProperties
{
    private static final String LEGACY_DEFAULT_START_DATE = "20230101";
    private static final String LEGACY_STRATEGY_BACKTEST_START_DATE = "2023-01-01";

    private String executable = "python";
    private String workdir = "../python";
    private String moduleName = "quant_road";
    private String defaultStartDate = "";
    private String strategyBacktestStartDate = "";
    private boolean notifyByDefault = true;
    private long commandTimeoutSeconds = 1800L;
    private String logDir = "runtime-logs/quant-road";
    private String requestedModeDefault = "auto";
    private long syncExecutionBudgetSeconds = 15L;
    private int shardSymbolChunkSize = 200;
    private long workerLeaseSeconds = 60L;
    private long heartbeatIntervalSeconds = 10L;
    private int maxShardRetries = 3;
    private String redisQueueKey = "quant:jobs:queue";
    private String redisLeasePrefix = "quant:jobs:lease:";
    private boolean autoWorkerEnabled = true;
    private long autoWorkerInitialDelayMillis = 15000L;
    private long autoWorkerFixedDelayMillis = 5000L;
    private int autoWorkerMaxConcurrentWorkers = 2;
    private boolean autoWorkerRecoverExpired = true;
    private int autoWorkerRecoverLimit = 100;
    private String autoWorkerIdPrefix = "ruoyi-auto-worker";

    public String getExecutable()
    {
        return executable;
    }

    public void setExecutable(String executable)
    {
        this.executable = executable;
    }

    public String getWorkdir()
    {
        return workdir;
    }

    public void setWorkdir(String workdir)
    {
        this.workdir = workdir;
    }

    public String getModuleName()
    {
        return moduleName;
    }

    public void setModuleName(String moduleName)
    {
        this.moduleName = moduleName;
    }

    public String getDefaultStartDate()
    {
        if (defaultStartDate == null || defaultStartDate.isBlank() || LEGACY_DEFAULT_START_DATE.equals(defaultStartDate))
        {
            return LocalDate.now().minusYears(5).format(DateTimeFormatter.BASIC_ISO_DATE);
        }
        return defaultStartDate;
    }

    public void setDefaultStartDate(String defaultStartDate)
    {
        this.defaultStartDate = defaultStartDate;
    }

    public String getStrategyBacktestStartDate()
    {
        if (strategyBacktestStartDate == null
                || strategyBacktestStartDate.isBlank()
                || LEGACY_STRATEGY_BACKTEST_START_DATE.equals(strategyBacktestStartDate))
        {
            return LocalDate.now().minusYears(5).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return strategyBacktestStartDate;
    }

    public void setStrategyBacktestStartDate(String strategyBacktestStartDate)
    {
        this.strategyBacktestStartDate = strategyBacktestStartDate;
    }

    public boolean isNotifyByDefault()
    {
        return notifyByDefault;
    }

    public void setNotifyByDefault(boolean notifyByDefault)
    {
        this.notifyByDefault = notifyByDefault;
    }

    public long getCommandTimeoutSeconds()
    {
        return commandTimeoutSeconds;
    }

    public void setCommandTimeoutSeconds(long commandTimeoutSeconds)
    {
        this.commandTimeoutSeconds = commandTimeoutSeconds;
    }

    public String getLogDir()
    {
        return logDir;
    }

    public void setLogDir(String logDir)
    {
        this.logDir = logDir;
    }

    public String getRequestedModeDefault()
    {
        return requestedModeDefault;
    }

    public void setRequestedModeDefault(String requestedModeDefault)
    {
        this.requestedModeDefault = requestedModeDefault;
    }

    public long getSyncExecutionBudgetSeconds()
    {
        return syncExecutionBudgetSeconds;
    }

    public void setSyncExecutionBudgetSeconds(long syncExecutionBudgetSeconds)
    {
        this.syncExecutionBudgetSeconds = syncExecutionBudgetSeconds;
    }

    public int getShardSymbolChunkSize()
    {
        return shardSymbolChunkSize;
    }

    public void setShardSymbolChunkSize(int shardSymbolChunkSize)
    {
        this.shardSymbolChunkSize = shardSymbolChunkSize;
    }

    public long getWorkerLeaseSeconds()
    {
        return workerLeaseSeconds;
    }

    public void setWorkerLeaseSeconds(long workerLeaseSeconds)
    {
        this.workerLeaseSeconds = workerLeaseSeconds;
    }

    public long getHeartbeatIntervalSeconds()
    {
        return heartbeatIntervalSeconds;
    }

    public void setHeartbeatIntervalSeconds(long heartbeatIntervalSeconds)
    {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    public int getMaxShardRetries()
    {
        return maxShardRetries;
    }

    public void setMaxShardRetries(int maxShardRetries)
    {
        this.maxShardRetries = maxShardRetries;
    }

    public String getRedisQueueKey()
    {
        return redisQueueKey;
    }

    public void setRedisQueueKey(String redisQueueKey)
    {
        this.redisQueueKey = redisQueueKey;
    }

    public String getRedisLeasePrefix()
    {
        return redisLeasePrefix;
    }

    public void setRedisLeasePrefix(String redisLeasePrefix)
    {
        this.redisLeasePrefix = redisLeasePrefix;
    }

    public boolean isAutoWorkerEnabled()
    {
        return autoWorkerEnabled;
    }

    public void setAutoWorkerEnabled(boolean autoWorkerEnabled)
    {
        this.autoWorkerEnabled = autoWorkerEnabled;
    }

    public long getAutoWorkerInitialDelayMillis()
    {
        return autoWorkerInitialDelayMillis;
    }

    public void setAutoWorkerInitialDelayMillis(long autoWorkerInitialDelayMillis)
    {
        this.autoWorkerInitialDelayMillis = autoWorkerInitialDelayMillis;
    }

    public long getAutoWorkerFixedDelayMillis()
    {
        return autoWorkerFixedDelayMillis;
    }

    public void setAutoWorkerFixedDelayMillis(long autoWorkerFixedDelayMillis)
    {
        this.autoWorkerFixedDelayMillis = autoWorkerFixedDelayMillis;
    }

    public int getAutoWorkerMaxConcurrentWorkers()
    {
        return autoWorkerMaxConcurrentWorkers;
    }

    public void setAutoWorkerMaxConcurrentWorkers(int autoWorkerMaxConcurrentWorkers)
    {
        this.autoWorkerMaxConcurrentWorkers = autoWorkerMaxConcurrentWorkers;
    }

    public boolean isAutoWorkerRecoverExpired()
    {
        return autoWorkerRecoverExpired;
    }

    public void setAutoWorkerRecoverExpired(boolean autoWorkerRecoverExpired)
    {
        this.autoWorkerRecoverExpired = autoWorkerRecoverExpired;
    }

    public int getAutoWorkerRecoverLimit()
    {
        return autoWorkerRecoverLimit;
    }

    public void setAutoWorkerRecoverLimit(int autoWorkerRecoverLimit)
    {
        this.autoWorkerRecoverLimit = autoWorkerRecoverLimit;
    }

    public String getAutoWorkerIdPrefix()
    {
        return autoWorkerIdPrefix;
    }

    public void setAutoWorkerIdPrefix(String autoWorkerIdPrefix)
    {
        this.autoWorkerIdPrefix = autoWorkerIdPrefix;
    }
}
