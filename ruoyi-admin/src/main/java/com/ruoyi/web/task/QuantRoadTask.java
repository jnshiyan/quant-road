package com.ruoyi.web.task;

import org.springframework.stereotype.Component;
import com.ruoyi.web.domain.quant.QuantJobRequest;
import com.ruoyi.web.service.quant.QuantExecutionFacade;
import com.ruoyi.web.service.quant.QuantRoadPythonService;

/**
 * Quant Road 定时任务 Bean
 * 可在若依定时任务中通过 invokeTarget 调用，例如：
 * quantRoadTask.fullDaily()
 */
@Component("quantRoadTask")
public class QuantRoadTask
{
    private final QuantRoadPythonService quantRoadPythonService;
    private final QuantExecutionFacade quantExecutionFacade;

    public QuantRoadTask(QuantRoadPythonService quantRoadPythonService, QuantExecutionFacade quantExecutionFacade)
    {
        this.quantRoadPythonService = quantRoadPythonService;
        this.quantExecutionFacade = quantExecutionFacade;
    }

    public void fullDaily()
    {
        quantRoadPythonService.fullDaily(null, null, null, null);
    }

    public void fullDailyAsync()
    {
        quantExecutionFacade.execute(buildScheduledPortfolioRequest(null, null));
    }

    public void fullDaily(Long strategyId)
    {
        quantRoadPythonService.fullDaily(strategyId, null, null, null);
    }

    public void fullDailyWithParams(Long strategyId, String startDate, String strategyBacktestStartDate, Boolean notify)
    {
        quantRoadPythonService.fullDaily(strategyId, startDate, strategyBacktestStartDate, notify);
    }

    public void fullDailyWithPortfolio(Long strategyId, String startDate, String strategyBacktestStartDate, Boolean notify, Double portfolioTotalCapital)
    {
        quantRoadPythonService.fullDaily(strategyId, startDate, strategyBacktestStartDate, notify, true, portfolioTotalCapital, "quartz");
    }

    public void syncBasic()
    {
        quantRoadPythonService.syncBasic();
    }

    public void syncDaily(String startDate)
    {
        quantRoadPythonService.syncDaily(startDate);
    }

    public void syncValuation()
    {
        quantRoadPythonService.syncValuation(null, null);
    }

    public void syncValuation(String indexCodes, String updateDate)
    {
        quantRoadPythonService.syncValuation(indexCodes, updateDate);
    }

    public void evaluateMarket()
    {
        quantRoadPythonService.evaluateMarket(2);
    }

    public void evaluateMarket(Integer holdDays)
    {
        quantRoadPythonService.evaluateMarket(holdDays);
    }

    public void runStrategy(Long strategyId, String strategyBacktestStartDate)
    {
        quantRoadPythonService.runStrategy(strategyId, strategyBacktestStartDate);
    }

    public void runPortfolio(String strategyBacktestStartDate, Double totalCapital)
    {
        quantRoadPythonService.runPortfolio(strategyBacktestStartDate, totalCapital, "quartz");
    }

    public void runPortfolioAsync(String strategyBacktestStartDate, Double totalCapital)
    {
        quantExecutionFacade.execute(buildScheduledPortfolioRequest(strategyBacktestStartDate, totalCapital));
    }

    public void evaluateRisk(Long strategyId)
    {
        quantRoadPythonService.evaluateRisk(strategyId);
    }

    public void notifySignals()
    {
        quantRoadPythonService.notifySignals();
    }

    public void evaluateExecutionFeedback()
    {
        quantRoadPythonService.evaluateExecutionFeedback(null, 1);
    }

    public void evaluateExecutionFeedback(Integer graceDays)
    {
        quantRoadPythonService.evaluateExecutionFeedback(null, graceDays);
    }

    public void monthlyReport()
    {
        quantRoadPythonService.monthlyReport(6, null);
    }

    public void monthlyReport(Integer months)
    {
        quantRoadPythonService.monthlyReport(months, null);
    }

    public void shadowCompare(Long candidateStrategyId)
    {
        quantRoadPythonService.shadowCompareReport(1L, candidateStrategyId, 6, null);
    }

    public void shadowCompare(Long baselineStrategyId, Long candidateStrategyId, Integer months)
    {
        quantRoadPythonService.shadowCompareReport(baselineStrategyId, candidateStrategyId, months, null);
    }

    public void canaryEvaluate(Long baselineStrategyId, Long candidateStrategyId, Integer months)
    {
        quantRoadPythonService.canaryEvaluate(baselineStrategyId, candidateStrategyId, months);
    }

    private QuantJobRequest buildScheduledPortfolioRequest(String strategyBacktestStartDate, Double totalCapital)
    {
        QuantJobRequest request = new QuantJobRequest();
        request.setRequestedMode("async");
        request.setUsePortfolio(Boolean.TRUE);
        request.setStrategyBacktestStartDate(strategyBacktestStartDate);
        request.setPortfolioTotalCapital(totalCapital);
        request.setActor("quartz");
        return request;
    }
}
