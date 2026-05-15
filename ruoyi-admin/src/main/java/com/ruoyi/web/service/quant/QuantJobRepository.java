package com.ruoyi.web.service.quant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.sql.Types;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.ruoyi.web.domain.quant.QuantAsyncJobStatusResponse;

@Repository
public class QuantJobRepository
{
    private final JdbcTemplate jdbcTemplate;

    public QuantJobRepository(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> resolveSymbols(List<String> requestedSymbols)
    {
        if (requestedSymbols != null && !requestedSymbols.isEmpty())
        {
            List<String> payload = new ArrayList<>();
            for (String item : requestedSymbols)
            {
                if (item == null || item.isBlank())
                {
                    continue;
                }
                payload.add(String.format(Locale.ROOT, "%06d", Integer.parseInt(item.trim())));
            }
            return payload;
        }
        return jdbcTemplate.query(
                """
                SELECT stock_code
                FROM stock_basic
                WHERE COALESCE(is_st, 0) = 0
                ORDER BY stock_code
                """,
                (rs, rowNum) -> rs.getString(1));
    }

    public List<Long> resolveActiveStrategyIds()
    {
        return jdbcTemplate.query(
                """
                SELECT id
                FROM strategy_config
                WHERE status = 1
                ORDER BY id
                """,
                (rs, rowNum) -> rs.getLong(1));
    }

    public Long insertJob(JobRecord record)
    {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO quant_async_job (
                    job_key,
                    job_type,
                    requested_mode,
                    resolved_mode,
                    status,
                    actor,
                    request_payload,
                    normalized_payload,
                    cost_estimate,
                    planned_shard_count,
                    completed_shard_count,
                    failed_shard_count,
                    cancel_requested,
                    error_message
                )
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), ?, 0, 0, 0, ?)
                RETURNING id
                """,
                Long.class,
                record.getJobKey(),
                record.getJobType(),
                record.getRequestedMode(),
                record.getResolvedMode(),
                record.getStatus(),
                record.getActor(),
                record.getRequestJson(),
                record.getNormalizedJson(),
                record.getEstimateJson(),
                record.getPlannedShardCount(),
                record.getErrorMessage());
    }

    public void insertSummary(Long jobId, int totalSymbols, int totalStrategies, String payloadJson)
    {
        jdbcTemplate.update(
                """
                INSERT INTO quant_async_job_summary (
                    job_id,
                    total_symbols,
                    processed_symbols,
                    skipped_symbols,
                    total_strategies,
                    signal_count,
                    invalid_count,
                    runtime_ms,
                    payload,
                    update_time
                )
                VALUES (?, ?, 0, 0, ?, 0, 0, 0, CAST(? AS jsonb), NOW())
                ON CONFLICT (job_id) DO UPDATE
                SET total_symbols = EXCLUDED.total_symbols,
                    total_strategies = EXCLUDED.total_strategies,
                    payload = EXCLUDED.payload,
                    update_time = NOW()
                """,
                jobId,
                totalSymbols,
                totalStrategies,
                payloadJson);
    }

    public void insertShards(Long jobId, List<ShardRecord> shards)
    {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO quant_async_job_shard (
                    job_id,
                    shard_key,
                    strategy_id,
                    shard_index,
                    status,
                    symbol_count,
                    payload,
                    attempt_count,
                    create_time
                )
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 0, NOW())
                """,
                shards,
                shards.size(),
                (ps, shard) -> {
                    ps.setLong(1, jobId);
                    ps.setString(2, shard.getShardKey());
                    if (shard.getStrategyId() == null)
                    {
                        ps.setNull(3, Types.BIGINT);
                    }
                    else
                    {
                        ps.setLong(3, shard.getStrategyId());
                    }
                    ps.setInt(4, shard.getShardIndex());
                    ps.setString(5, shard.getStatus());
                    ps.setInt(6, shard.getSymbolCount());
                    ps.setString(7, shard.getPayloadJson());
                });
    }

    public void markJobFailed(Long jobId, int failedShardCount, String errorMessage)
    {
        jdbcTemplate.update(
                """
                UPDATE quant_async_job
                SET status = 'FAILED',
                    failed_shard_count = ?,
                    error_message = ?,
                    end_time = NOW()
                WHERE id = ?
                """,
                failedShardCount,
                errorMessage,
                jobId);
        jdbcTemplate.update(
                """
                UPDATE quant_async_job_shard
                SET status = 'FAILED',
                    last_error = ?,
                    end_time = NOW()
                WHERE job_id = ?
                  AND status = 'QUEUED'
                """,
                errorMessage,
                jobId);
    }

    public QuantAsyncJobStatusResponse loadJobStatus(Long jobId)
    {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT id, job_type, requested_mode, resolved_mode, status,
                       planned_shard_count, completed_shard_count, failed_shard_count,
                       cancel_requested, error_message
                FROM quant_async_job
                WHERE id = ?
                """,
                jobId);
        QuantAsyncJobStatusResponse response = new QuantAsyncJobStatusResponse();
        response.setJobId(((Number) row.get("id")).longValue());
        response.setJobType(String.valueOf(row.get("job_type")));
        response.setRequestedMode(String.valueOf(row.get("requested_mode")));
        response.setResolvedMode(String.valueOf(row.get("resolved_mode")));
        response.setStatus(String.valueOf(row.get("status")));
        response.setPlannedShardCount(((Number) row.get("planned_shard_count")).intValue());
        response.setCompletedShardCount(((Number) row.get("completed_shard_count")).intValue());
        response.setFailedShardCount(((Number) row.get("failed_shard_count")).intValue());
        response.setCancelRequested(((Number) row.get("cancel_requested")).intValue() == 1);
        response.setErrorMessage(row.get("error_message") == null ? null : String.valueOf(row.get("error_message")));
        return response;
    }

    public void cancelJob(Long jobId)
    {
        jdbcTemplate.update(
                """
                UPDATE quant_async_job
                SET cancel_requested = 1,
                    status = CASE
                        WHEN status IN ('PENDING', 'QUEUED') THEN 'CANCELLED'
                        ELSE 'CANCEL_REQUESTED'
                    END
                WHERE id = ?
                """,
                jobId);
    }

    public int countShards(Long jobId)
    {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM quant_async_job_shard WHERE job_id = ?",
                Integer.class,
                jobId);
        return value == null ? 0 : value;
    }

    public List<String> requeueFailedShards(Long jobId)
    {
        List<String> shardKeys = jdbcTemplate.query(
                """
                SELECT shard_key
                FROM quant_async_job_shard
                WHERE job_id = ?
                  AND status IN ('FAILED', 'PARTIAL_FAILED')
                ORDER BY shard_index
                """,
                (rs, rowNum) -> rs.getString(1),
                jobId);
        if (!shardKeys.isEmpty())
        {
            jdbcTemplate.update(
                    """
                    UPDATE quant_async_job_shard
                    SET status = 'QUEUED',
                        last_error = NULL,
                        lease_owner = NULL,
                        lease_expires_at = NULL,
                        heartbeat_at = NULL
                    WHERE job_id = ?
                      AND status IN ('FAILED', 'PARTIAL_FAILED')
                    """,
                    jobId);
            jdbcTemplate.update(
                    """
                    UPDATE quant_async_job
                    SET status = 'QUEUED',
                        error_message = NULL
                    WHERE id = ?
                    """,
                    jobId);
        }
        return shardKeys;
    }

    public static class JobRecord
    {
        private String jobKey;
        private String jobType;
        private String requestedMode;
        private String resolvedMode;
        private String status;
        private String actor;
        private String requestJson;
        private String normalizedJson;
        private String estimateJson;
        private int plannedShardCount;
        private String errorMessage;

        public String getJobKey()
        {
            return jobKey;
        }

        public void setJobKey(String jobKey)
        {
            this.jobKey = jobKey;
        }

        public String getJobType()
        {
            return jobType;
        }

        public void setJobType(String jobType)
        {
            this.jobType = jobType;
        }

        public String getRequestedMode()
        {
            return requestedMode;
        }

        public void setRequestedMode(String requestedMode)
        {
            this.requestedMode = requestedMode;
        }

        public String getResolvedMode()
        {
            return resolvedMode;
        }

        public void setResolvedMode(String resolvedMode)
        {
            this.resolvedMode = resolvedMode;
        }

        public String getStatus()
        {
            return status;
        }

        public void setStatus(String status)
        {
            this.status = status;
        }

        public String getActor()
        {
            return actor;
        }

        public void setActor(String actor)
        {
            this.actor = actor;
        }

        public String getRequestJson()
        {
            return requestJson;
        }

        public void setRequestJson(String requestJson)
        {
            this.requestJson = requestJson;
        }

        public String getNormalizedJson()
        {
            return normalizedJson;
        }

        public void setNormalizedJson(String normalizedJson)
        {
            this.normalizedJson = normalizedJson;
        }

        public String getEstimateJson()
        {
            return estimateJson;
        }

        public void setEstimateJson(String estimateJson)
        {
            this.estimateJson = estimateJson;
        }

        public int getPlannedShardCount()
        {
            return plannedShardCount;
        }

        public void setPlannedShardCount(int plannedShardCount)
        {
            this.plannedShardCount = plannedShardCount;
        }

        public String getErrorMessage()
        {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage)
        {
            this.errorMessage = errorMessage;
        }
    }

    public static class ShardRecord
    {
        private String shardKey;
        private Long strategyId;
        private int shardIndex;
        private String status;
        private int symbolCount;
        private String payloadJson;

        public String getShardKey()
        {
            return shardKey;
        }

        public void setShardKey(String shardKey)
        {
            this.shardKey = shardKey;
        }

        public Long getStrategyId()
        {
            return strategyId;
        }

        public void setStrategyId(Long strategyId)
        {
            this.strategyId = strategyId;
        }

        public int getShardIndex()
        {
            return shardIndex;
        }

        public void setShardIndex(int shardIndex)
        {
            this.shardIndex = shardIndex;
        }

        public String getStatus()
        {
            return status;
        }

        public void setStatus(String status)
        {
            this.status = status;
        }

        public int getSymbolCount()
        {
            return symbolCount;
        }

        public void setSymbolCount(int symbolCount)
        {
            this.symbolCount = symbolCount;
        }

        public String getPayloadJson()
        {
            return payloadJson;
        }

        public void setPayloadJson(String payloadJson)
        {
            this.payloadJson = payloadJson;
        }
    }
}
