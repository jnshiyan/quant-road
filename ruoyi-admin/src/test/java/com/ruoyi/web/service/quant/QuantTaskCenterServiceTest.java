package com.ruoyi.web.service.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuantTaskCenterServiceTest
{
    @Test
    void summaryIncludesCurrentRunScopeDateRangeAndNextStep()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.jobReadiness(null)).thenReturn(Map.of(
                "status", "RUNNING",
                "batchId", 12L,
                "message", "盘后主流程仍在运行，请等待步骤完成。",
                "completedSteps", 2,
                "totalSteps", 5));
        when(queryService.jobSteps(12L)).thenReturn(List.of(
                Map.of(
                        "stepName", "run-portfolio",
                        "status", "RUNNING",
                        "startTime", "2026-05-10 15:31:00")));
        when(queryService.jobSopHints(null)).thenReturn(List.of());
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of("status", "ACTIVE"));
        when(queryService.asyncJobs(5)).thenReturn(List.of(
                Map.of(
                        "task_name", "盘后主流程",
                        "status", "RUNNING",
                        "current_step_name", "run-portfolio",
                        "scope_summary", "全市场股票",
                        "time_range_summary", "2021-05-10 ~ 2026-05-10",
                        "next_step_name", "notify-signals",
                        "progress_summary", "2 / 5")));
        when(queryService.dispatchDefinitions()).thenReturn(List.of());
        when(queryService.dispatchHistory(1, 10, null, null)).thenReturn(Map.of("rows", List.of(), "total", 0));
        when(queryService.nextScheduledDispatch()).thenReturn(Map.of());

        QuantTaskCenterService service = new QuantTaskCenterService(queryService);

        Map<String, Object> payload = service.summary();

        Map<String, Object> primaryTask = cast(payload.get("primaryTask"));
        assertThat(primaryTask).containsKeys("taskName", "currentStep", "scopeSummary", "timeRangeSummary", "nextStep");
    }

    @Test
    void summaryShouldExposeDispatchSpecificFields()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.jobReadiness(null)).thenReturn(Map.of(
                "status", "RUNNING",
                "batchId", 12L,
                "message", "盘后主流程运行中",
                "completedSteps", 2,
                "totalSteps", 5));
        when(queryService.jobSteps(12L)).thenReturn(List.of(
                Map.of(
                        "stepName", "run-portfolio",
                        "status", "RUNNING",
                        "startTime", "2026-05-10 15:31:00")));
        when(queryService.jobSopHints(null)).thenReturn(List.of());
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of("status", "ACTIVE"));
        when(queryService.asyncJobs(5)).thenReturn(List.of(
                Map.of(
                        "id", 88L,
                        "status", "RUNNING",
                        "triggerMode", "manual",
                        "timeRangeSummary", "2021-05-10 ~ 2026-05-10")));
        when(queryService.dispatchDefinitions()).thenReturn(List.of(
                Map.of(
                        "taskCode", "quantRoadTask.fullDailyAsync()",
                        "taskName", "盘后主流程",
                        "triggerModes", List.of("manual", "auto"))));
        when(queryService.dispatchHistory(1, 10, null, null)).thenReturn(Map.of(
                "rows", List.of(
                        Map.of(
                                "dispatchId", 1L,
                                "triggerMode", "auto",
                                "timeRangeSummary", "2021-05-10 ~ 2026-05-10")),
                "total", 1));
        when(queryService.nextScheduledDispatch()).thenReturn(Map.of(
                "taskName", "盘后主流程",
                "nextFireTime", "2026-05-10 15:30:00"));

        QuantTaskCenterService service = new QuantTaskCenterService(queryService);

        Map<String, Object> payload = service.summary();

        assertThat(payload).containsKeys(
                "todayStatus",
                "primaryTask",
                "progressEvents",
                "nextAction",
                "technicalSummary",
                "dispatchDefinitions",
                "dispatchHistory",
                "nextScheduledDispatch");
    }

    @Test
    void summaryShouldShowWarningStatusAndClearActionForBasicFallback()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.jobReadiness(null)).thenReturn(Map.of(
                "status", "READY_WITH_WARNINGS",
                "batchId", 18L,
                "message", "基础标的信息本次使用了库内回退，今日结果可继续查看，但建议后续补跑 sync-basic 确认标的信息是否最新。",
                "completedSteps", 5,
                "totalSteps", 5,
                "canEnterDashboard", true,
                "dataIntegrityStatus", "WARNING",
                "dataIntegrityCategory", "FALLBACK_BASIC",
                "dataIntegrityMessage", "基础标的信息本次使用了库内回退，今日结果可继续查看，但建议后续补跑 sync-basic 确认标的信息是否最新。"));
        when(queryService.jobSteps(18L)).thenReturn(List.of());
        when(queryService.jobSopHints(null)).thenReturn(List.of(
                Map.of(
                        "code", "goDashboard",
                        "severity", "success",
                        "title", "进入量化看板",
                        "suggestedAction", "先去看板确认市场状态、信号、持仓和风险预警。",
                        "targetPage", "/quant/dashboard"),
                Map.of(
                        "code", "checkBasicFallback",
                        "severity", "warning",
                        "title", "确认基础标的回退",
                        "suggestedAction", "今日结果可以继续查看；若你需要最新基础标信息，请在运维中心补跑 sync-basic。",
                        "targetPage", "/quant/operations")));
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of("status", "ACTIVE"));
        when(queryService.asyncJobs(5)).thenReturn(List.of());
        when(queryService.dispatchDefinitions()).thenReturn(List.of());
        when(queryService.dispatchHistory(1, 10, null, null)).thenReturn(Map.of("rows", List.of(), "total", 0));
        when(queryService.nextScheduledDispatch()).thenReturn(Map.of());

        QuantTaskCenterService service = new QuantTaskCenterService(queryService);

        Map<String, Object> payload = service.summary();

        @SuppressWarnings("unchecked")
        Map<String, Object> todayStatus = (Map<String, Object>) payload.get("todayStatus");
        @SuppressWarnings("unchecked")
        Map<String, Object> nextAction = (Map<String, Object>) payload.get("nextAction");

        assertThat(todayStatus)
                .containsEntry("code", "WARNING")
                .containsEntry("label", "警告")
                .containsEntry("reason", "基础标的信息本次使用了库内回退，今日结果可继续查看，但建议后续补跑 sync-basic 确认标的信息是否最新。")
                .containsEntry("suggestion", "今日结果可以继续查看；若你需要最新基础标信息，请在运维中心补跑 sync-basic。");
        assertThat(nextAction)
                .containsEntry("code", "GO_OPERATIONS")
                .containsEntry("label", "确认基础标的回退");
    }

    @Test
    void summaryShouldExposeExplicitStatusCardContractForWarning()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.jobReadiness(null)).thenReturn(Map.of(
                "status", "READY_WITH_WARNINGS",
                "batchId", 18L,
                "message", "基础标的信息本次使用了库内回退，今日结果可继续查看，但建议后续补跑 sync-basic 确认标的信息是否最新。",
                "canEnterDashboard", true,
                "dataIntegrityStatus", "WARNING",
                "dataIntegrityCategory", "FALLBACK_BASIC"));
        when(queryService.jobSteps(18L)).thenReturn(List.of());
        when(queryService.jobSopHints(null)).thenReturn(List.of(
                Map.of(
                        "code", "goDashboard",
                        "level", "success",
                        "title", "进入量化看板",
                        "suggestedAction", "先去看板确认市场状态、信号、持仓和风险预警。",
                        "targetPage", "/quant/dashboard"),
                Map.of(
                        "code", "checkBasicFallback",
                        "level", "warning",
                        "title", "确认基础标的回退",
                        "suggestedAction", "今日结果可以继续查看；若你需要最新基础标信息，请在运维中心补跑 sync-basic。",
                        "targetPage", "/quant/operations")));
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of("status", "ACTIVE"));
        when(queryService.asyncJobs(5)).thenReturn(List.of());
        when(queryService.dispatchDefinitions()).thenReturn(List.of());
        when(queryService.dispatchHistory(1, 10, null, null)).thenReturn(Map.of("rows", List.of(), "total", 0));
        when(queryService.nextScheduledDispatch()).thenReturn(Map.of());

        QuantTaskCenterService service = new QuantTaskCenterService(queryService);

        Map<String, Object> payload = service.summary();

        @SuppressWarnings("unchecked")
        Map<String, Object> todayStatus = (Map<String, Object>) payload.get("todayStatus");

        assertThat(todayStatus).containsEntry("headlineAction", "确认基础标的回退");
        assertThat(todayStatus).containsEntry("statusCode", "WARNING");
        assertThat(todayStatus).containsEntry("statusLabel", "警告");
        assertThat(todayStatus).containsEntry("canContinue", true);
        assertThat(todayStatus).containsEntry("continueLabel", "可以继续查看");
        assertThat(todayStatus).containsEntry("urgency", "before_decision");
        assertThat(todayStatus).containsKey("primaryAction");
        assertThat(todayStatus).containsKey("secondaryAction");
    }

    @Test
    void summaryShouldExposeExplicitStatusCardContractForBlocked()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.jobReadiness(null)).thenReturn(Map.of(
                "status", "BLOCKED",
                "batchId", 21L,
                "message", "步骤 sync-daily 失败，请先恢复后再进入看板。",
                "latestFailedStep", "sync-daily",
                "canEnterDashboard", false,
                "dataIntegrityStatus", "BLOCKED",
                "dataIntegrityCategory", "PARTIAL_DAILY_SYNC"));
        when(queryService.jobSteps(21L)).thenReturn(List.of());
        when(queryService.jobSopHints(null)).thenReturn(List.of(Map.of(
                "code", "recoverBatch",
                "level", "danger",
                "title", "恢复失败批次",
                "suggestedAction", "优先恢复失败批次。",
                "targetPage", "/quant/operations")));
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of("status", "ACTIVE"));
        when(queryService.asyncJobs(5)).thenReturn(List.of());
        when(queryService.dispatchDefinitions()).thenReturn(List.of());
        when(queryService.dispatchHistory(1, 10, null, null)).thenReturn(Map.of("rows", List.of(), "total", 0));
        when(queryService.nextScheduledDispatch()).thenReturn(Map.of());

        QuantTaskCenterService service = new QuantTaskCenterService(queryService);

        Map<String, Object> payload = service.summary();

        @SuppressWarnings("unchecked")
        Map<String, Object> todayStatus = (Map<String, Object>) payload.get("todayStatus");

        assertThat(todayStatus).containsEntry("statusCode", "BLOCKED");
        assertThat(todayStatus).containsEntry("canContinue", false);
        assertThat(todayStatus).containsEntry("continueLabel", "当前不可继续");
        assertThat(todayStatus.get("secondaryAction")).isNull();
    }

    @Test
    void summaryShouldExposeExplicitStatusCardContractForRunning()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.jobReadiness(null)).thenReturn(Map.of(
                "status", "RUNNING",
                "batchId", 12L,
                "message", "盘后主流程仍在运行，请等待步骤完成。",
                "completedSteps", 2,
                "totalSteps", 5));
        when(queryService.jobSteps(12L)).thenReturn(List.of(
                Map.of(
                        "stepName", "run-portfolio",
                        "status", "RUNNING",
                        "startTime", "2026-05-10 15:31:00")));
        when(queryService.jobSopHints(null)).thenReturn(List.of());
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of("status", "ACTIVE"));
        when(queryService.asyncJobs(5)).thenReturn(List.of());
        when(queryService.dispatchDefinitions()).thenReturn(List.of());
        when(queryService.dispatchHistory(1, 10, null, null)).thenReturn(Map.of("rows", List.of(), "total", 0));
        when(queryService.nextScheduledDispatch()).thenReturn(Map.of());

        QuantTaskCenterService service = new QuantTaskCenterService(queryService);

        Map<String, Object> payload = service.summary();

        @SuppressWarnings("unchecked")
        Map<String, Object> todayStatus = (Map<String, Object>) payload.get("todayStatus");

        assertThat(todayStatus).containsEntry("statusCode", "RUNNING");
        assertThat(todayStatus).containsEntry("headlineAction", "等待当前任务完成");
        assertThat(todayStatus).containsEntry("continueLabel", "暂不建议重复提交");
    }

    @Test
    void summaryShouldSeparateCurrentStageAndWaitingTargetWhenFinished()
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.jobReadiness(null)).thenReturn(Map.of(
                "status", "READY",
                "batchId", 31L,
                "message", "盘后主流程已完成，可以进入量化看板。",
                "canEnterDashboard", true));
        when(queryService.jobSteps(31L)).thenReturn(List.of(
                Map.of(
                        "stepName", "notify-signals",
                        "status", "SUCCESS",
                        "endTime", "2026-05-10 15:40:00")));
        when(queryService.jobSopHints(null)).thenReturn(List.of());
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of("status", "ACTIVE"));
        when(queryService.asyncJobs(5)).thenReturn(List.of());
        when(queryService.dispatchDefinitions()).thenReturn(List.of());
        when(queryService.dispatchHistory(1, 10, null, null)).thenReturn(Map.of("rows", List.of(), "total", 0));
        when(queryService.nextScheduledDispatch()).thenReturn(Map.of());

        QuantTaskCenterService service = new QuantTaskCenterService(queryService);

        Map<String, Object> payload = service.summary();

        @SuppressWarnings("unchecked")
        Map<String, Object> primaryTask = (Map<String, Object>) payload.get("primaryTask");

        assertThat(primaryTask).containsEntry("currentStage", "notify-signals");
        assertThat(primaryTask).containsEntry("currentStageLabel", "完成");
        assertThat(primaryTask).containsEntry("waitingKind", "NONE");
        assertThat(primaryTask.get("waitingTarget")).isNull();
        assertThat(primaryTask.get("waitingFor")).isNull();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object value)
    {
        return (Map<String, Object>) value;
    }
}
