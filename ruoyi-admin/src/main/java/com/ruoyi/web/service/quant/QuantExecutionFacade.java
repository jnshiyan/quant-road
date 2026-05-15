package com.ruoyi.web.service.quant;

import org.springframework.stereotype.Service;
import com.ruoyi.web.domain.quant.QuantExecutionResponse;
import com.ruoyi.web.domain.quant.QuantJobRequest;
import com.ruoyi.web.domain.quant.QuantExecutionPlan;

@Service
public class QuantExecutionFacade
{
    private final QuantExecutionPlanService executionPlanService;
    private final QuantExecutionEngineService executionEngineService;

    public QuantExecutionFacade(
            QuantExecutionPlanService executionPlanService,
            QuantExecutionEngineService executionEngineService)
    {
        this.executionPlanService = executionPlanService;
        this.executionEngineService = executionEngineService;
    }

    public QuantExecutionResponse execute(QuantJobRequest request)
    {
        QuantJobRequest payload = request == null ? new QuantJobRequest() : request;
        QuantExecutionPlan plan = executionPlanService.buildPlan(payload);
        return executionEngineService.execute(plan, payload);
    }
}
