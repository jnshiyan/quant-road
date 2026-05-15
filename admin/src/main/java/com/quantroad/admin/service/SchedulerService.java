package com.quantroad.admin.service;

import com.quantroad.admin.entity.StrategyConfigEntity;
import com.quantroad.admin.job.DailyPipelineJob;
import com.quantroad.admin.repository.StrategyConfigRepository;
import java.util.List;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private static final String GROUP = "quant-road";
    private final Scheduler scheduler;
    private final StrategyConfigRepository strategyConfigRepository;

    @PostConstruct
    public void init() {
        refreshStrategyJobs();
    }

    public synchronized void refreshStrategyJobs() {
        try {
            for (JobKey jobKey : scheduler.getJobKeys(org.quartz.impl.matchers.GroupMatcher.jobGroupEquals(GROUP))) {
                scheduler.deleteJob(jobKey);
            }
            List<StrategyConfigEntity> activeStrategies = strategyConfigRepository.findByStatusOrderByIdAsc(1);
            for (StrategyConfigEntity strategy : activeStrategies) {
                scheduleStrategy(strategy);
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to refresh quartz jobs", e);
        }
    }

    public void triggerNow(Long strategyId) {
        try {
            scheduler.triggerJob(JobKey.jobKey(jobName(strategyId), GROUP));
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to trigger strategy job: " + strategyId, e);
        }
    }

    private void scheduleStrategy(StrategyConfigEntity strategy) throws SchedulerException {
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("strategyId", strategy.getId());

        JobDetail jobDetail = JobBuilder.newJob(DailyPipelineJob.class)
            .withIdentity(jobName(strategy.getId()), GROUP)
            .usingJobData(dataMap)
            .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(triggerName(strategy.getId()), GROUP)
            .forJob(jobDetail)
            .withSchedule(CronScheduleBuilder.cronSchedule(strategy.getCronExpr()))
            .build();
        scheduler.scheduleJob(jobDetail, trigger);
        log.info("Scheduled strategy job: strategyId={}, cron={}", strategy.getId(), strategy.getCronExpr());
    }

    private String jobName(Long strategyId) {
        return "strategy-job-" + strategyId;
    }

    private String triggerName(Long strategyId) {
        return "strategy-trigger-" + strategyId;
    }
}

