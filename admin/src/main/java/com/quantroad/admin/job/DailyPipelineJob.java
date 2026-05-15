package com.quantroad.admin.job;

import com.quantroad.admin.service.NotificationService;
import com.quantroad.admin.service.PythonTaskService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DailyPipelineJob implements Job {

    @Autowired
    private PythonTaskService pythonTaskService;

    @Autowired
    private NotificationService notificationService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        Long strategyId = jobDataMap.getLongValue("strategyId");
        try {
            String output = pythonTaskService.runFullDaily(strategyId, null, null, false);
            log.info("Daily pipeline completed for strategyId={}, output={}", strategyId, output);
            notificationService.pushDailySummary();
        } catch (Exception e) {
            throw new JobExecutionException("Daily pipeline failed for strategyId=" + strategyId, e);
        }
    }
}
