-- Quant Road RuoYi quartz job bootstrap (PostgreSQL)
-- Usage:
--   psql -h <host> -U <user> -d <db-quant> -f sql/ruoyi_quant_jobs.sql
--
-- Idempotent by (job_name, job_group):
--   - update existing rows
--   - insert missing rows

BEGIN;

DO
$$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN
        SELECT *
        FROM (
            VALUES
                (
                    'Quant Full Daily',
                    'DEFAULT',
                    'quantRoadTask.fullDailyAsync()',
                    '0 30 15 * * ?',
                    '3',
                    '1',
                    '0',
                    'Quant Road async main pipeline submission'
                ),
                (
                    'Quant Monthly Report',
                    'DEFAULT',
                    'quantRoadTask.monthlyReport(6)',
                    '0 0 9 1 * ?',
                    '3',
                    '1',
                    '0',
                    '每月策略评估报告'
                ),
                (
                    'Quant Shadow Compare',
                    'DEFAULT',
                    'quantRoadTask.shadowCompare(1L,2L,6)',
                    '0 0 10 ? * SAT',
                    '3',
                    '1',
                    '0',
                    '每周影子策略对比（基线=1, 候选=2）'
                ),
                (
                    'Quant Sync Valuation',
                    'DEFAULT',
                    'quantRoadTask.syncValuation()',
                    '0 20 15 * * ?',
                    '3',
                    '1',
                    '1',
                    '估值快照任务（默认暂停；通常由fullDaily包含执行）'
                ),
                (
                    'Quant Evaluate Market',
                    'DEFAULT',
                    'quantRoadTask.evaluateMarket(2)',
                    '0 25 15 * * ?',
                    '3',
                    '1',
                    '1',
                    '市场风格判断任务（默认暂停；通常由fullDaily包含执行）'
                ),
                (
                    'Quant Execution Feedback',
                    'DEFAULT',
                    'quantRoadTask.evaluateExecutionFeedback(1)',
                    '0 40 15 * * ?',
                    '3',
                    '1',
                    '1',
                    'T+1执行反馈评估（默认暂停；通常由fullDaily包含执行）'
                ),
                (
                    'Quant Canary Evaluate',
                    'DEFAULT',
                    'quantRoadTask.canaryEvaluate(1L,2L,6)',
                    '0 0 11 ? * SAT',
                    '3',
                    '1',
                    '1',
                    '每周Canary评估（默认暂停）'
                )
        ) AS t(
            job_name,
            job_group,
            invoke_target,
            cron_expression,
            misfire_policy,
            concurrent,
            status,
            remark
        )
    LOOP
        UPDATE sys_job
           SET invoke_target = rec.invoke_target,
               cron_expression = rec.cron_expression,
               misfire_policy = rec.misfire_policy,
               concurrent = rec.concurrent,
               status = rec.status,
               remark = rec.remark,
               update_by = 'admin',
               update_time = NOW()
         WHERE job_name = rec.job_name
           AND job_group = rec.job_group;

        IF NOT FOUND THEN
            INSERT INTO sys_job (
                job_name,
                job_group,
                invoke_target,
                cron_expression,
                misfire_policy,
                concurrent,
                status,
                remark,
                create_by,
                create_time
            )
            VALUES (
                rec.job_name,
                rec.job_group,
                rec.invoke_target,
                rec.cron_expression,
                rec.misfire_policy,
                rec.concurrent,
                rec.status,
                rec.remark,
                'admin',
                NOW()
            );
        END IF;
    END LOOP;
END
$$;

COMMIT;

-- Verification query (optional):
-- SELECT job_id, job_name, job_group, invoke_target, cron_expression, status
-- FROM sys_job
-- WHERE job_name LIKE 'Quant %'
-- ORDER BY job_id;
