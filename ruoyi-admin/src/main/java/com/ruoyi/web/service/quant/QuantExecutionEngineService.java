package com.ruoyi.web.service.quant;

import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.domain.quant.QuantExecutionPlan;
import com.ruoyi.web.domain.quant.QuantExecutionResponse;
import com.ruoyi.web.domain.quant.QuantJobRequest;

@Service
public class QuantExecutionEngineService
{
    private final QuantJobPlannerService jobPlannerService;
    private final QuantRoadPythonService pythonService;

    public QuantExecutionEngineService(QuantJobPlannerService jobPlannerService, QuantRoadPythonService pythonService)
    {
        this.jobPlannerService = jobPlannerService;
        this.pythonService = pythonService;
    }

    public QuantExecutionResponse execute(QuantExecutionPlan plan, QuantJobRequest request)
    {
        QuantExecutionPlan resolvedPlan = plan == null ? new QuantExecutionPlan() : plan;
        QuantJobRequest payload = request == null ? new QuantJobRequest() : request;
        if (shouldRouteAsync(resolvedPlan, payload))
        {
            return jobPlannerService.submitExecutionPlan(resolvedPlan, payload);
        }

        pythonService.executePlan(resolvedPlan, payload);

        QuantExecutionResponse response = new QuantExecutionResponse();
        response.setStatus("SUCCESS");
        response.setResolvedExecutionMode("sync");
        response.setPlanSummary(resolvedPlan.getPlanSummary());
        response.setEstimatedCost(resolvedPlan.getEstimatedCost());
        return response;
    }

    private boolean shouldRouteAsync(QuantExecutionPlan plan, QuantJobRequest request)
    {
        if ("sync".equalsIgnoreCase(request.getRequestedMode()) && "async".equalsIgnoreCase(plan.getResolvedExecutionMode()))
        {
            throw new ServiceException("sync execution budget exceeded; resubmit with requestedMode=async or auto");
        }
        if ("async".equalsIgnoreCase(request.getRequestedMode()))
        {
            return true;
        }
        return "async".equalsIgnoreCase(plan.getResolvedExecutionMode());
    }
}
