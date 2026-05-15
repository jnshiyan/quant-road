package com.quantroad.admin.controller;

import com.quantroad.admin.dto.PythonJobRequest;
import com.quantroad.admin.service.NotificationService;
import com.quantroad.admin.service.PythonTaskService;
import com.quantroad.admin.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final PythonTaskService pythonTaskService;
    private final SchedulerService schedulerService;
    private final NotificationService notificationService;

    @PostMapping("/full-daily")
    public String runFullDaily(@RequestBody(required = false) PythonJobRequest request) {
        PythonJobRequest payload = request == null ? new PythonJobRequest() : request;
        return pythonTaskService.runFullDaily(
            payload.getStrategyId(),
            payload.getStartDate(),
            payload.getStrategyBacktestStartDate(),
            payload.getNotify(),
            payload.getUsePortfolio(),
            payload.getPortfolioTotalCapital(),
            payload.getActor()
        );
    }

    @PostMapping("/sync-basic")
    public String runSyncBasic() {
        return pythonTaskService.runSyncBasic();
    }

    @PostMapping("/sync-daily")
    public String runSyncDaily(@RequestParam(required = false) String startDate) {
        return pythonTaskService.runSyncDaily(startDate);
    }

    @PostMapping("/sync-valuation")
    public String runSyncValuation(
        @RequestParam(required = false) String indexCodes,
        @RequestParam(required = false) String updateDate
    ) {
        return pythonTaskService.runSyncValuation(indexCodes, updateDate);
    }

    @PostMapping("/evaluate-market")
    public String runEvaluateMarket(@RequestParam(required = false) Integer holdDays) {
        return pythonTaskService.runEvaluateMarket(holdDays);
    }

    @PostMapping("/run-strategy")
    public String runStrategy(@RequestBody(required = false) PythonJobRequest request) {
        PythonJobRequest payload = request == null ? new PythonJobRequest() : request;
        return pythonTaskService.runStrategy(
            payload.getStrategyId(),
            payload.getStrategyBacktestStartDate(),
            payload.getPortfolioTotalCapital(),
            payload.getActor()
        );
    }

    @PostMapping("/run-portfolio")
    public String runPortfolio(@RequestBody(required = false) PythonJobRequest request) {
        PythonJobRequest payload = request == null ? new PythonJobRequest() : request;
        return pythonTaskService.runPortfolio(
            payload.getStrategyBacktestStartDate(),
            payload.getPortfolioTotalCapital(),
            payload.getActor()
        );
    }

    @PostMapping("/risk")
    public String runRisk(@RequestBody(required = false) PythonJobRequest request) {
        PythonJobRequest payload = request == null ? new PythonJobRequest() : request;
        return pythonTaskService.runRisk(payload.getStrategyId());
    }

    @PostMapping("/execution-feedback")
    public String executionFeedback(
        @RequestParam(required = false) String asOfDate,
        @RequestParam(required = false) Integer graceDays
    ) {
        return pythonTaskService.runEvaluateExecutionFeedback(asOfDate, graceDays);
    }

    @PostMapping("/monthly-report")
    public String monthlyReport(
        @RequestParam(required = false) Integer months,
        @RequestParam(required = false) String output
    ) {
        return pythonTaskService.runMonthlyReport(months, output);
    }

    @GetMapping("/strategy-capabilities")
    public String strategyCapabilities() {
        return pythonTaskService.runStrategyCapabilities();
    }

    @GetMapping("/shadow-compare")
    public String shadowCompareJson(
        @RequestParam(defaultValue = "1") Long baselineStrategyId,
        @RequestParam Long candidateStrategyId,
        @RequestParam(defaultValue = "6") Integer months
    ) {
        return pythonTaskService.runShadowCompareJson(baselineStrategyId, candidateStrategyId, months);
    }

    @PostMapping("/shadow-compare")
    public String shadowCompareReport(
        @RequestParam(defaultValue = "1") Long baselineStrategyId,
        @RequestParam Long candidateStrategyId,
        @RequestParam(defaultValue = "6") Integer months,
        @RequestParam(required = false) String output
    ) {
        return pythonTaskService.runShadowCompareReport(baselineStrategyId, candidateStrategyId, months, output);
    }

    @PostMapping("/canary-evaluate")
    public String canaryEvaluate(
        @RequestParam(defaultValue = "1") Long baselineStrategyId,
        @RequestParam Long candidateStrategyId,
        @RequestParam(defaultValue = "6") Integer months
    ) {
        return pythonTaskService.runCanaryEvaluate(baselineStrategyId, candidateStrategyId, months);
    }

    @PostMapping("/notify")
    public boolean notifyNow() {
        return notificationService.pushDailySummary();
    }

    @PostMapping("/refresh-scheduler")
    public String refreshScheduler() {
        schedulerService.refreshStrategyJobs();
        return "ok";
    }

    @PostMapping("/trigger")
    public String triggerStrategy(@RequestParam Long strategyId) {
        schedulerService.triggerNow(strategyId);
        return "triggered";
    }
}
