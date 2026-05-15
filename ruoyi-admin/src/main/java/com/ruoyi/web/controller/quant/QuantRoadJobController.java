package com.ruoyi.web.controller.quant;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.domain.quant.QuantAsyncJobRequest;
import com.ruoyi.web.domain.quant.QuantExecutionExceptionRequest;
import com.ruoyi.web.domain.quant.QuantExecutionResponse;
import com.ruoyi.web.domain.quant.QuantExecutionRequest;
import com.ruoyi.web.domain.quant.QuantExecutionMatchConfirmRequest;
import com.ruoyi.web.domain.quant.QuantJobRequest;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.service.quant.QuantAsyncExecutionFacade;
import com.ruoyi.web.service.quant.QuantExecutionFacade;
import com.ruoyi.web.service.quant.QuantJobPlannerService;
import com.ruoyi.web.service.quant.QuantRoadPythonService;
import com.ruoyi.web.service.quant.QuantRoadQueryService;
import com.ruoyi.web.service.quant.QuantRoadSymbolScopeService;

/**
 * Quant Road 手动任务接口
 */
@RestController
@RequestMapping("/quant/jobs")
public class QuantRoadJobController
{
    private final QuantRoadPythonService quantRoadPythonService;
    private final QuantJobPlannerService quantJobPlannerService;
    private final QuantAsyncExecutionFacade quantAsyncExecutionFacade;
    private final QuantExecutionFacade quantExecutionFacade;
    private final QuantRoadQueryService quantRoadQueryService;
    private final QuantRoadSymbolScopeService quantRoadSymbolScopeService;

    public QuantRoadJobController(
            QuantRoadPythonService quantRoadPythonService,
            QuantJobPlannerService quantJobPlannerService,
            QuantAsyncExecutionFacade quantAsyncExecutionFacade,
            QuantExecutionFacade quantExecutionFacade,
            QuantRoadQueryService quantRoadQueryService,
            QuantRoadSymbolScopeService quantRoadSymbolScopeService)
    {
        this.quantRoadPythonService = quantRoadPythonService;
        this.quantJobPlannerService = quantJobPlannerService;
        this.quantAsyncExecutionFacade = quantAsyncExecutionFacade;
        this.quantExecutionFacade = quantExecutionFacade;
        this.quantRoadQueryService = quantRoadQueryService;
        this.quantRoadSymbolScopeService = quantRoadSymbolScopeService;
    }

    @PostMapping("/fullDaily")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    @Deprecated(since = "2026-05-09", forRemoval = false)
    public AjaxResult fullDaily(
            @RequestBody(required = false) QuantJobRequest request,
            HttpServletResponse response)
    {
        applyLegacyEndpointHeaders(response, "fullDaily", "/quant/jobs/execute");
        QuantJobRequest payload = request == null ? new QuantJobRequest() : request;
        List<String> symbols = resolveFullDailySymbols(payload);
        String output = quantRoadPythonService.fullDaily(
                payload.getStrategyId(),
                payload.getStartDate(),
                payload.getStrategyBacktestStartDate(),
                payload.getNotify(),
                payload.getUsePortfolio(),
                payload.getPortfolioTotalCapital(),
                payload.getActor(),
                symbols,
                resolveFullDailyScopeType(payload),
                resolveFullDailyScopePoolCode(payload));
        return AjaxResult.success(output);
    }

    private List<String> resolveFullDailySymbols(QuantJobRequest request)
    {
        if (!hasConstrainedScope(request))
        {
            return null;
        }
        return quantRoadSymbolScopeService.resolveScopeSymbols(
                request.getScopeType(),
                request.getScopePoolCode(),
                request.getSymbols(),
                request.getWhitelist(),
                request.getBlacklist(),
                request.getAdHocSymbols());
    }

    private String resolveFullDailyScopeType(QuantJobRequest request)
    {
        return hasConstrainedScope(request) ? normalizeText(request.getScopeType()) : null;
    }

    private String resolveFullDailyScopePoolCode(QuantJobRequest request)
    {
        return hasConstrainedScope(request) ? normalizeText(request.getScopePoolCode()) : null;
    }

    private boolean hasConstrainedScope(QuantJobRequest request)
    {
        if (hasText(request.getScopePoolCode()))
        {
            return true;
        }
        if (hasItems(request.getSymbols())
                || hasItems(request.getWhitelist())
                || hasItems(request.getBlacklist())
                || hasItems(request.getAdHocSymbols()))
        {
            return true;
        }
        return hasText(request.getScopeType()) && !"all_stocks".equalsIgnoreCase(request.getScopeType().trim());
    }

    private boolean hasItems(List<String> values)
    {
        if (values == null)
        {
            return false;
        }
        for (String value : values)
        {
            if (hasText(value))
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    private String normalizeText(String value)
    {
        return hasText(value) ? value.trim() : null;
    }

    private void applyLegacyEndpointHeaders(HttpServletResponse response, String endpointName, String replacement)
    {
        if (response == null)
        {
            return;
        }
        response.addHeader("X-Quant-Legacy-Endpoint", endpointName);
        response.addHeader("X-Quant-Replacement-Endpoint", replacement);
        response.addHeader(
                "Warning",
                String.format(
                        Locale.ROOT,
                        "299 quant-road \"%s is a legacy compatibility endpoint; use %s\"",
                        endpointName,
                        replacement));
    }

    @PostMapping("/syncBasic")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult syncBasic()
    {
        return AjaxResult.success(quantRoadPythonService.syncBasic());
    }

    @PostMapping("/syncDaily")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult syncDaily(@RequestParam(required = false) String startDate)
    {
        return AjaxResult.success(quantRoadPythonService.syncDaily(startDate));
    }

    @PostMapping("/syncValuation")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult syncValuation(
            @RequestParam(required = false) String indexCodes,
            @RequestParam(required = false) String updateDate)
    {
        return AjaxResult.success(quantRoadPythonService.syncValuation(indexCodes, updateDate));
    }

    @PostMapping("/evaluateMarket")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult evaluateMarket(@RequestParam(required = false) Integer holdDays)
    {
        return AjaxResult.success(quantRoadPythonService.evaluateMarket(holdDays));
    }

    @PostMapping("/runStrategy")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    @Deprecated(since = "2026-05-09", forRemoval = false)
    public AjaxResult runStrategy(
            @RequestBody(required = false) QuantAsyncJobRequest request,
            HttpServletResponse response)
    {
        applyLegacyEndpointHeaders(response, "runStrategy", "/quant/jobs/execute");
        QuantAsyncJobRequest payload = request == null ? new QuantAsyncJobRequest() : request;
        return AjaxResult.success(quantAsyncExecutionFacade.submitLegacyRunStrategy(payload));
    }

    @PostMapping("/runPortfolio")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    @Deprecated(since = "2026-05-09", forRemoval = false)
    public AjaxResult runPortfolio(
            @RequestBody(required = false) QuantJobRequest request,
            HttpServletResponse response)
    {
        applyLegacyEndpointHeaders(response, "runPortfolio", "/quant/jobs/execute");
        QuantJobRequest payload = request == null ? new QuantJobRequest() : request;
        return AjaxResult.success(quantAsyncExecutionFacade.submitLegacyRunPortfolio(payload));
    }

    @PostMapping("/execute")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult execute(@RequestBody(required = false) QuantJobRequest request)
    {
        QuantJobRequest payload = request == null ? new QuantJobRequest() : request;
        QuantExecutionResponse response = quantExecutionFacade.execute(payload);
        response.setTriggerMode("manual");
        Map<String, Object> timeRange = new LinkedHashMap<>();
        timeRange.put("startDate", payload.getStrategyBacktestStartDate());
        timeRange.put("endDate", payload.getEndDate() == null || payload.getEndDate().isBlank()
                ? LocalDate.now().toString()
                : payload.getEndDate());
        response.setTimeRange(timeRange);
        return AjaxResult.success(response);
    }

    @GetMapping("/status/{jobId}")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult getJobStatus(@PathVariable Long jobId)
    {
        return AjaxResult.success(quantJobPlannerService.getJobStatus(jobId));
    }

    @PostMapping("/cancel/{jobId}")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult cancelJob(@PathVariable Long jobId)
    {
        quantJobPlannerService.cancelJob(jobId);
        return AjaxResult.success();
    }

    @PostMapping("/retryFailedShards/{jobId}")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult retryFailedShards(@PathVariable Long jobId)
    {
        return AjaxResult.success(quantJobPlannerService.retryFailedShards(jobId));
    }

    @PostMapping("/runAsyncWorkerOnce")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult runAsyncWorkerOnce(@RequestParam(required = false) String workerId)
    {
        String resolvedWorkerId = workerId == null || workerId.isBlank() ? "ruoyi-web-worker" : workerId.trim();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("workerId", resolvedWorkerId);
        result.put("output", quantRoadPythonService.runAsyncWorkerOnce(resolvedWorkerId));
        result.put("asyncWorkerSummary", quantRoadQueryService.asyncWorkerSummary());
        return AjaxResult.success(result);
    }

    @PostMapping("/recoverAsyncShards")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult recoverAsyncShards(@RequestParam(defaultValue = "100") Integer limit)
    {
        int safeLimit = limit == null ? 100 : Math.max(1, limit);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("limit", safeLimit);
        result.put("output", quantRoadPythonService.recoverAsyncShards(safeLimit));
        result.put("asyncWorkerSummary", quantRoadQueryService.asyncWorkerSummary());
        return AjaxResult.success(result);
    }

    @PostMapping("/recoverBatch/{batchId}")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult recoverBatch(
            @PathVariable Long batchId,
            @RequestParam(required = false) String actor)
    {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("output", quantRoadPythonService.recoverBatch(batchId, actor));
        result.put("jobReadiness", quantRoadQueryService.jobReadiness(batchId));
        result.put("jobErrorCategories", quantRoadQueryService.jobErrorCategories(batchId));
        result.put("jobSopHints", quantRoadQueryService.jobSopHints(batchId));
        return AjaxResult.success(result);
    }

    @PostMapping("/evaluateRisk")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult evaluateRisk(@RequestBody(required = false) QuantJobRequest request)
    {
        QuantJobRequest payload = request == null ? new QuantJobRequest() : request;
        return AjaxResult.success(quantRoadPythonService.evaluateRisk(payload.getStrategyId()));
    }

    @PostMapping("/notifySignals")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult notifySignals()
    {
        return AjaxResult.success(quantRoadPythonService.notifySignals());
    }

    @PostMapping("/evaluateExecutionFeedback")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult evaluateExecutionFeedback(
            @RequestParam(required = false) String asOfDate,
            @RequestParam(required = false) Integer graceDays)
    {
        return AjaxResult.success(quantRoadPythonService.evaluateExecutionFeedback(asOfDate, graceDays));
    }

    @PostMapping("/recordExecution")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult recordExecution(@RequestBody QuantExecutionRequest request)
    {
        if (request == null)
        {
            return AjaxResult.error("request is required");
        }
        return AjaxResult.success(
                quantRoadPythonService.recordExecution(
                        request.getStockCode(),
                        request.getSide(),
                        request.getQuantity(),
                        request.getPrice(),
                        request.getTradeDate(),
                        request.getStrategyId(),
                        request.getSignalId(),
                        request.getCommission(),
                        request.getTax(),
                        request.getSlippage(),
                        request.getExternalOrderId()));
    }

    @PostMapping("/importExecutions")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult importExecutions(
            @RequestParam String file,
            @RequestParam(required = false) Long strategyId)
    {
        return AjaxResult.success(parseJsonPayload(
                quantRoadPythonService.importExecutions(file, strategyId),
                "Invalid execution import payload"));
    }

    @PostMapping("/validateExecutionImport")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult validateExecutionImport(
            @RequestParam String file,
            @RequestParam(required = false) Long strategyId)
    {
        return AjaxResult.success(parseJsonPayload(
                quantRoadPythonService.validateExecutionImport(file, strategyId),
                "Invalid execution import validation payload"));
    }

    @PostMapping(value = "/validateExecutionImportUpload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult validateExecutionImportUpload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long strategyId) throws IOException
    {
        Path tempFile = writeTempCsv(file);
        try
        {
            return AjaxResult.success(parseJsonPayload(
                    quantRoadPythonService.validateExecutionImport(tempFile.toString(), strategyId),
                    "Invalid execution import validation payload"));
        }
        finally
        {
            cleanupTempFile(tempFile);
        }
    }

    @PostMapping(value = "/importExecutionsUpload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult importExecutionsUpload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long strategyId) throws IOException
    {
        Path tempFile = writeTempCsv(file);
        try
        {
            return AjaxResult.success(parseJsonPayload(
                    quantRoadPythonService.importExecutions(tempFile.toString(), strategyId),
                    "Invalid execution import payload"));
        }
        finally
        {
            cleanupTempFile(tempFile);
        }
    }

    @PostMapping("/confirmExecutionMatch")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult confirmExecutionMatch(@RequestBody QuantExecutionMatchConfirmRequest request)
    {
        if (request == null)
        {
            return AjaxResult.error("request is required");
        }
        java.util.Map<String, Object> result = quantRoadQueryService.confirmExecutionMatch(
                request.getSignalId(),
                request.getExecutionRecordId(),
                request.getActor(),
                request.getRemark());
        quantRoadPythonService.evaluateExecutionFeedback(null, 1);
        result.put("executionReconciliationSummary", quantRoadQueryService.executionReconciliationSummary());
        Object signal = result.get("signal");
        if (signal instanceof java.util.Map<?, ?> signalMap)
        {
            Object strategyId = signalMap.get("strategyId");
            Object stockCode = signalMap.get("stockCode");
            result.put("positionSyncResult", quantRoadQueryService.positionSyncResult(
                    strategyId == null ? null : Long.valueOf(String.valueOf(strategyId)),
                    stockCode == null ? null : String.valueOf(stockCode)));
        }
        else
        {
            result.put("positionSyncResult", java.util.Map.of());
        }
        return AjaxResult.success(result);
    }

    @PostMapping("/markExecutionException")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult markExecutionException(@RequestBody QuantExecutionExceptionRequest request)
    {
        if (request == null)
        {
            return AjaxResult.error("request is required");
        }
        java.util.Map<String, Object> result = quantRoadQueryService.markExecutionException(
                request.getSignalId(),
                request.getExceptionType(),
                request.getRemark(),
                request.getActor());
        result.put("executionReconciliationSummary", quantRoadQueryService.executionReconciliationSummary());
        return AjaxResult.success(result);
    }

    @PostMapping("/monthlyReport")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult monthlyReport(
            @RequestParam(required = false) Integer months,
            @RequestParam(required = false) String output)
    {
        return AjaxResult.success(quantRoadPythonService.monthlyReport(months, output));
    }

    @PostMapping("/shadowCompare")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult shadowCompare(
            @RequestParam(defaultValue = "1") Long baselineStrategyId,
            @RequestParam Long candidateStrategyId,
            @RequestParam(defaultValue = "6") Integer months,
            @RequestParam(required = false) String output)
    {
        return AjaxResult.success(
                quantRoadPythonService.shadowCompareReport(
                        baselineStrategyId,
                        candidateStrategyId,
                        months,
                        output));
    }

    @PostMapping("/canaryEvaluate")
    @PreAuthorize("@ss.hasPermi('quant:job:run')")
    public AjaxResult canaryEvaluate(
            @RequestParam(defaultValue = "1") Long baselineStrategyId,
            @RequestParam Long candidateStrategyId,
            @RequestParam(defaultValue = "6") Integer months)
    {
        return AjaxResult.success(quantRoadPythonService.canaryEvaluate(baselineStrategyId, candidateStrategyId, months));
    }

    private Object parseJsonPayload(String payload, String errorPrefix)
    {
        try
        {
            return JSON.parse(payload);
        }
        catch (Exception ex)
        {
            throw new ServiceException(errorPrefix + ": " + payload).setDetailMessage(ex.toString());
        }
    }

    private Path writeTempCsv(MultipartFile file) throws IOException
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("csv file is required");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".csv"))
        {
            throw new ServiceException("only csv file is supported");
        }
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "quant-road", "imports");
        Files.createDirectories(tempDir);
        String tempName = "executions-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".csv";
        Path tempFile = tempDir.resolve(tempName);
        try (InputStream input = file.getInputStream())
        {
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private void cleanupTempFile(Path tempFile)
    {
        try
        {
            Files.deleteIfExists(tempFile);
        }
        catch (IOException ignored)
        {
            // ignore temp cleanup failure, keep import result as source of truth
        }
    }
}
