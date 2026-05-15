package com.ruoyi.web.service.quant;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.core.config.QuantRoadPythonProperties;
import com.ruoyi.web.domain.quant.QuantExecutionPlan;
import com.ruoyi.web.domain.quant.QuantExecutionPlanStep;
import com.ruoyi.web.domain.quant.QuantJobRequest;

/**
 * 调用 Python 量化层命令
 */
@Service
public class QuantRoadPythonService
{
    private static final Logger log = LoggerFactory.getLogger(QuantRoadPythonService.class);

    private final QuantRoadPythonProperties properties;

    public QuantRoadPythonService(QuantRoadPythonProperties properties)
    {
        this.properties = properties;
    }

    public String fullDaily(Long strategyId, String startDate, String strategyBacktestStartDate, Boolean notify)
    {
        return fullDaily(strategyId, startDate, strategyBacktestStartDate, notify, null, null, null, null, null, null);
    }

    public String recoverBatch(Long batchId, String actor)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("full-daily");
        command.add("--resume-batch-id");
        command.add(String.valueOf(batchId));
        if (actor != null && !actor.isBlank())
        {
            command.add("--actor");
            command.add(actor);
        }
        return execute(command);
    }

    public String runAsyncWorkerOnce(String workerId)
    {
        String resolvedWorkerId = workerId == null || workerId.isBlank() ? "ruoyi-web-worker" : workerId.trim();
        return execute(List.of(
                properties.getExecutable(),
                "-m",
                properties.getModuleName(),
                "run-async-worker",
                "--worker-id",
                resolvedWorkerId,
                "--once"));
    }

    public String recoverAsyncShards(Integer limit)
    {
        int safeLimit = limit == null ? 100 : Math.max(1, limit);
        return execute(List.of(
                properties.getExecutable(),
                "-m",
                properties.getModuleName(),
                "recover-async-shards",
                "--limit",
                String.valueOf(safeLimit)));
    }

    public String fullDaily(
            Long strategyId,
            String startDate,
            String strategyBacktestStartDate,
            Boolean notify,
            Boolean usePortfolio,
            Double portfolioTotalCapital,
            String actor)
    {
        return fullDaily(
                strategyId,
                startDate,
                strategyBacktestStartDate,
                notify,
                usePortfolio,
                portfolioTotalCapital,
                actor,
                null,
                null,
                null);
    }

    public String fullDaily(
            Long strategyId,
            String startDate,
            String strategyBacktestStartDate,
            Boolean notify,
            Boolean usePortfolio,
            Double portfolioTotalCapital,
            String actor,
            List<String> symbols,
            String scopeType,
            String scopePoolCode)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("full-daily");
        command.add("--start-date");
        command.add(startDate == null ? properties.getDefaultStartDate() : startDate);
        command.add("--strategy-start-date");
        command.add(strategyBacktestStartDate == null ? properties.getStrategyBacktestStartDate() : strategyBacktestStartDate);
        if (strategyId != null)
        {
            command.add("--strategy-id");
            command.add(String.valueOf(strategyId));
        }
        if (Boolean.TRUE.equals(notify) || (notify == null && properties.isNotifyByDefault()))
        {
            command.add("--notify");
        }
        if (Boolean.TRUE.equals(usePortfolio))
        {
            command.add("--use-portfolio");
        }
        if (portfolioTotalCapital != null && portfolioTotalCapital > 0)
        {
            command.add("--portfolio-total-capital");
            command.add(String.valueOf(portfolioTotalCapital));
        }
        if (actor != null && !actor.isBlank())
        {
            command.add("--actor");
            command.add(actor);
        }
        appendSymbols(command, symbols);
        if (scopeType != null && !scopeType.isBlank())
        {
            command.add("--scope-type");
            command.add(scopeType.trim());
        }
        if (scopePoolCode != null && !scopePoolCode.isBlank())
        {
            command.add("--scope-pool-code");
            command.add(scopePoolCode.trim());
        }
        return execute(command);
    }

    public String syncBasic()
    {
        return execute(List.of(properties.getExecutable(), "-m", properties.getModuleName(), "sync-basic"));
    }

    public String syncDaily(String startDate)
    {
        return execute(List.of(
                properties.getExecutable(),
                "-m",
                properties.getModuleName(),
                "sync-daily",
                "--start-date",
                startDate == null ? properties.getDefaultStartDate() : startDate));
    }

    public String syncValuation(String indexCodes, String updateDate)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("sync-valuation");
        if (indexCodes != null && !indexCodes.isBlank())
        {
            command.add("--index-codes");
            command.add(indexCodes);
        }
        if (updateDate != null && !updateDate.isBlank())
        {
            command.add("--update-date");
            command.add(updateDate);
        }
        return execute(command);
    }

    public String evaluateMarket(Integer holdDays)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("evaluate-market");
        if (holdDays != null && holdDays > 0)
        {
            command.add("--hold-days");
            command.add(String.valueOf(holdDays));
        }
        return execute(command);
    }

    public String runStrategy(Long strategyId, String strategyBacktestStartDate)
    {
        return runStrategy(strategyId, strategyBacktestStartDate, null, null);
    }

    public String runStrategy(Long strategyId, String strategyBacktestStartDate, Double portfolioCapital, String actor)
    {
        return runStrategy(strategyId, strategyBacktestStartDate, portfolioCapital, actor, null);
    }

    public String runStrategy(Long strategyId, String strategyBacktestStartDate, Double portfolioCapital, String actor, List<String> symbols)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("run-strategy");
        command.add("--start-date");
        command.add(strategyBacktestStartDate == null ? properties.getStrategyBacktestStartDate() : strategyBacktestStartDate);
        if (strategyId != null)
        {
            command.add("--strategy-id");
            command.add(String.valueOf(strategyId));
        }
        appendSymbols(command, symbols);
        if (portfolioCapital != null && portfolioCapital > 0)
        {
            command.add("--portfolio-capital");
            command.add(String.valueOf(portfolioCapital));
        }
        if (actor != null && !actor.isBlank())
        {
            command.add("--actor");
            command.add(actor);
        }
        return execute(command);
    }

    public String runPortfolio(String strategyBacktestStartDate, Double totalCapital, String actor)
    {
        return runPortfolio(strategyBacktestStartDate, totalCapital, actor, null);
    }

    public String runPortfolio(String strategyBacktestStartDate, Double totalCapital, String actor, List<String> symbols)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("run-portfolio");
        command.add("--start-date");
        command.add(strategyBacktestStartDate == null ? properties.getStrategyBacktestStartDate() : strategyBacktestStartDate);
        appendSymbols(command, symbols);
        if (totalCapital != null && totalCapital > 0)
        {
            command.add("--total-capital");
            command.add(String.valueOf(totalCapital));
        }
        if (actor != null && !actor.isBlank())
        {
            command.add("--actor");
            command.add(actor);
        }
        return execute(command);
    }

    public String executePlan(QuantExecutionPlan plan, QuantJobRequest request)
    {
        QuantExecutionPlan resolvedPlan = plan == null ? new QuantExecutionPlan() : plan;
        QuantJobRequest payload = request == null ? new QuantJobRequest() : request;
        List<String> symbols = resolvedPlan.getResolvedSymbols() == null ? payload.getSymbols() : resolvedPlan.getResolvedSymbols();
        if (resolvedPlan.getSteps() == null || resolvedPlan.getSteps().isEmpty())
        {
            return fallbackFullDaily(payload, symbols);
        }
        List<String> outputs = new ArrayList<>();
        for (QuantExecutionPlanStep step : resolvedPlan.getSteps())
        {
            String stepOutput = executePlannedStep(step, payload, symbols);
            if (stepOutput == null)
            {
                return fallbackFullDaily(payload, symbols);
            }
            if (!stepOutput.isBlank())
            {
                outputs.add(stepOutput);
            }
        }
        return String.join(System.lineSeparator(), outputs);
    }

    public String evaluateExecutionFeedback(String asOfDate, Integer graceDays)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("evaluate-execution-feedback");
        if (asOfDate != null && !asOfDate.isBlank())
        {
            command.add("--as-of-date");
            command.add(asOfDate);
        }
        if (graceDays != null && graceDays >= 0)
        {
            command.add("--grace-days");
            command.add(String.valueOf(graceDays));
        }
        return execute(command);
    }

    public String canaryEvaluate(Long baselineStrategyId, Long candidateStrategyId, Integer months)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("canary-evaluate");
        Long baseline = baselineStrategyId == null ? 1L : baselineStrategyId;
        command.add("--baseline-strategy-id");
        command.add(String.valueOf(baseline));
        if (candidateStrategyId == null)
        {
            throw new ServiceException("candidateStrategyId is required for canary evaluation.");
        }
        if (baseline.equals(candidateStrategyId))
        {
            throw new ServiceException("candidateStrategyId must be different from baselineStrategyId.");
        }
        command.add("--candidate-strategy-id");
        command.add(String.valueOf(candidateStrategyId));
        if (months != null && months > 0)
        {
            command.add("--months");
            command.add(String.valueOf(months));
        }
        return execute(command);
    }

    public String evaluateRisk(Long strategyId)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("evaluate-risk");
        if (strategyId != null)
        {
            command.add("--strategy-id");
            command.add(String.valueOf(strategyId));
        }
        return execute(command);
    }

    public String notifySignals()
    {
        return execute(List.of(properties.getExecutable(), "-m", properties.getModuleName(), "notify-signals"));
    }

    public String recordExecution(
            String stockCode,
            String side,
            Integer quantity,
            Double price,
            String tradeDate,
            Long strategyId,
            Long signalId,
            Double commission,
            Double tax,
            Double slippage,
            String externalOrderId)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("record-execution");
        command.add("--stock-code");
        command.add(stockCode);
        command.add("--side");
        command.add(side);
        command.add("--quantity");
        command.add(String.valueOf(quantity));
        command.add("--price");
        command.add(String.valueOf(price));
        command.add("--trade-date");
        command.add(tradeDate);
        command.add("--strategy-id");
        command.add(String.valueOf(strategyId));
        if (signalId != null)
        {
            command.add("--signal-id");
            command.add(String.valueOf(signalId));
        }
        if (commission != null)
        {
            command.add("--commission");
            command.add(String.valueOf(commission));
        }
        if (tax != null)
        {
            command.add("--tax");
            command.add(String.valueOf(tax));
        }
        if (slippage != null)
        {
            command.add("--slippage");
            command.add(String.valueOf(slippage));
        }
        if (externalOrderId != null && !externalOrderId.isBlank())
        {
            command.add("--external-order-id");
            command.add(externalOrderId);
        }
        return execute(command);
    }

    public String importExecutions(String file, Long strategyId)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("import-executions");
        command.add("--file");
        command.add(file);
        if (strategyId != null)
        {
            command.add("--strategy-id");
            command.add(String.valueOf(strategyId));
        }
        return execute(command);
    }

    public String validateExecutionImport(String file, Long strategyId)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("validate-execution-import");
        command.add("--file");
        command.add(file);
        if (strategyId != null)
        {
            command.add("--strategy-id");
            command.add(String.valueOf(strategyId));
        }
        return execute(command);
    }

    public String monthlyReport(Integer months, String output)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("monthly-report");
        if (months != null && months > 0)
        {
            command.add("--months");
            command.add(String.valueOf(months));
        }
        if (output != null && !output.isBlank())
        {
            command.add("--output");
            command.add(output);
        }
        return execute(command);
    }

    public String strategyCapabilities()
    {
        return execute(List.of(
                properties.getExecutable(),
                "-m",
                properties.getModuleName(),
                "strategy-capabilities",
                "--format",
                "json"));
    }

    public String shadowCompareJson(Long baselineStrategyId, Long candidateStrategyId, Integer months)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("shadow-compare");
        Long baseline = baselineStrategyId == null ? 1L : baselineStrategyId;
        if (candidateStrategyId == null)
        {
            throw new ServiceException("candidateStrategyId is required for shadow compare.");
        }
        if (baseline.equals(candidateStrategyId))
        {
            throw new ServiceException("candidateStrategyId must be different from baselineStrategyId.");
        }
        command.add("--baseline-strategy-id");
        command.add(String.valueOf(baseline));
        command.add("--candidate-strategy-id");
        command.add(String.valueOf(candidateStrategyId));
        if (months != null && months > 0)
        {
            command.add("--months");
            command.add(String.valueOf(months));
        }
        command.add("--format");
        command.add("json");
        return execute(command);
    }

    public String shadowCompareReport(Long baselineStrategyId, Long candidateStrategyId, Integer months, String output)
    {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("shadow-compare");
        Long baseline = baselineStrategyId == null ? 1L : baselineStrategyId;
        if (candidateStrategyId == null)
        {
            throw new ServiceException("candidateStrategyId is required for shadow compare.");
        }
        if (baseline.equals(candidateStrategyId))
        {
            throw new ServiceException("candidateStrategyId must be different from baselineStrategyId.");
        }
        command.add("--baseline-strategy-id");
        command.add(String.valueOf(baseline));
        command.add("--candidate-strategy-id");
        command.add(String.valueOf(candidateStrategyId));
        if (months != null && months > 0)
        {
            command.add("--months");
            command.add(String.valueOf(months));
        }
        command.add("--format");
        command.add("text");
        if (output != null && !output.isBlank())
        {
            command.add("--output");
            command.add(output);
        }
        return execute(command);
    }

    private String execute(List<String> command)
    {
        List<String> baseCommand = new ArrayList<>(command);
        File workdir = resolveWorkdir();
        File logDir = resolveLogDir();
        List<String> executableCandidates = buildExecutableCandidates(baseCommand.get(0));
        IOException lastStartException = null;

        for (String executable : executableCandidates)
        {
            baseCommand.set(0, executable);
            ProcessBuilder processBuilder = new ProcessBuilder(baseCommand);
            processBuilder.directory(workdir);
            processBuilder.redirectErrorStream(true);
            configureProcessEnvironment(processBuilder, logDir);
            StringBuffer output = new StringBuffer();
            Thread outputThread = null;
            try
            {
                String commandText = String.join(" ", baseCommand);
                log.info(
                        "Run python command: {}, workdir={}, logDir={}",
                        commandText,
                        workdir.getAbsolutePath(),
                        logDir.getAbsolutePath());
                Process process = processBuilder.start();
                outputThread = startOutputPump(process, output, commandText);
                boolean completed = waitForProcess(process);
                if (!completed)
                {
                    log.error(
                            "Python task timeout after {} seconds, command={}, workdir={}, logDir={}",
                            properties.getCommandTimeoutSeconds(),
                            commandText,
                            workdir.getAbsolutePath(),
                            logDir.getAbsolutePath());
                    process.destroyForcibly();
                    joinOutputThread(outputThread);
                    throw new ServiceException("Python task timeout after " + properties.getCommandTimeoutSeconds()
                            + " seconds, command=" + commandText + ", logDir=" + logDir.getAbsolutePath());
                }
                joinOutputThread(outputThread);
                int exitCode = process.exitValue();
                log.info("Python command finished: exitCode={}, command={}", exitCode, commandText);
                if (exitCode != 0)
                {
                    throw new ServiceException("Python task failed, exitCode=" + exitCode + ", output=" + output);
                }
                return output.toString();
            }
            catch (IOException e)
            {
                if (looksLikeExecutableNotFound(e))
                {
                    lastStartException = e;
                    continue;
                }
                throw new ServiceException("Failed to start python command: " + baseCommand + ", error=" + e.getMessage())
                        .setDetailMessage(e.toString());
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new ServiceException("Python task interrupted: " + baseCommand + ", error=" + e.getMessage())
                        .setDetailMessage(e.toString());
            }
        }

        throw new ServiceException("Failed to start python command with candidates=" + executableCandidates
                + ", workdir=" + workdir.getAbsolutePath()
                + ", command=" + baseCommand
                + ", error=" + (lastStartException == null ? "unknown" : lastStartException.getMessage()))
                .setDetailMessage(lastStartException == null ? "" : lastStartException.toString());
    }

    private File resolveWorkdir()
    {
        List<String> candidates = new ArrayList<>();
        if (properties.getWorkdir() != null && !properties.getWorkdir().isBlank())
        {
            candidates.add(properties.getWorkdir());
        }
        candidates.add("python");
        candidates.add("../python");
        candidates.add("../../python");

        List<String> checkedPaths = new ArrayList<>();
        for (String candidate : candidates)
        {
            File path = new File(candidate);
            if (!path.isAbsolute())
            {
                path = new File(System.getProperty("user.dir"), candidate);
            }
            File normalized = path.getAbsoluteFile().toPath().normalize().toFile();
            checkedPaths.add(normalized.getPath());
            if (normalized.exists() && normalized.isDirectory())
            {
                return normalized;
            }
        }
        throw new ServiceException("Python workdir not found. checked=" + checkedPaths);
    }

    private File resolveLogDir()
    {
        String configured = properties.getLogDir();
        File path = configured == null || configured.isBlank() ? new File("runtime-logs/quant-road") : new File(configured);
        if (!path.isAbsolute())
        {
            path = new File(System.getProperty("user.dir"), configured == null || configured.isBlank() ? "runtime-logs/quant-road" : configured);
        }
        File normalized = path.getAbsoluteFile().toPath().normalize().toFile();
        if (!normalized.exists() && !normalized.mkdirs() && !normalized.exists())
        {
            throw new ServiceException("Python log directory cannot be created: " + normalized.getAbsolutePath());
        }
        return normalized;
    }

    private List<String> buildExecutableCandidates(String configuredExecutable)
    {
        Set<String> candidates = new LinkedHashSet<>();
        if (configuredExecutable != null && !configuredExecutable.isBlank())
        {
            candidates.add(configuredExecutable);
        }
        candidates.add("python");
        if (System.getProperty("os.name", "").toLowerCase().contains("win"))
        {
            candidates.add("py");
        }
        return new ArrayList<>(candidates);
    }

    private boolean looksLikeExecutableNotFound(IOException e)
    {
        String message = e.getMessage();
        if (message == null)
        {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("createprocess error=2")
                || lower.contains("no such file")
                || lower.contains("cannot find the file");
    }

    private void appendSymbols(List<String> command, List<String> symbols)
    {
        if (symbols == null || symbols.isEmpty())
        {
            return;
        }
        List<String> cleaned = new ArrayList<>();
        for (String item : symbols)
        {
            if (item != null && !item.isBlank())
            {
                cleaned.add(item.trim());
            }
        }
        if (cleaned.isEmpty())
        {
            return;
        }
        command.add("--symbols");
        command.add(String.join(",", cleaned));
    }

    private String executePlannedStep(QuantExecutionPlanStep step, QuantJobRequest payload, List<String> symbols)
    {
        if (step == null || step.getStepName() == null || step.getStepName().isBlank())
        {
            return null;
        }
        return switch (step.getStepName())
        {
            case "sync-basic" -> syncBasic();
            case "sync-daily" -> syncDaily(payload.getStartDate());
            case "evaluate-market" -> evaluateMarket(null);
            case "run-portfolio" -> runPortfolio(
                    payload.getStrategyBacktestStartDate(),
                    payload.getPortfolioTotalCapital(),
                    payload.getActor(),
                    symbols);
            case "run-strategy" -> Boolean.TRUE.equals(payload.getUsePortfolio())
                    ? runPortfolio(
                            payload.getStrategyBacktestStartDate(),
                            payload.getPortfolioTotalCapital(),
                            payload.getActor(),
                            symbols)
                    : runStrategy(
                            payload.getStrategyId(),
                            payload.getStrategyBacktestStartDate(),
                            payload.getPortfolioTotalCapital(),
                            payload.getActor(),
                            symbols);
            case "evaluate-risk" -> evaluateRisk(payload.getStrategyId());
            default -> null;
        };
    }

    private String fallbackFullDaily(QuantJobRequest payload, List<String> symbols)
    {
        return fullDaily(
                payload.getStrategyId(),
                payload.getStartDate(),
                payload.getStrategyBacktestStartDate(),
                payload.getNotify(),
                payload.getUsePortfolio(),
                payload.getPortfolioTotalCapital(),
                payload.getActor(),
                symbols,
                payload.getScopeType(),
                payload.getScopePoolCode());
    }

    private void configureProcessEnvironment(ProcessBuilder processBuilder, File logDir)
    {
        String current = processBuilder.environment().get("PYTHONPATH");
        if (current == null || current.isBlank())
        {
            processBuilder.environment().put("PYTHONPATH", "src");
        }
        else if (!current.contains("src"))
        {
            processBuilder.environment().put("PYTHONPATH", current + File.pathSeparator + "src");
        }
        processBuilder.environment().put("PYTHONUNBUFFERED", "1");
        processBuilder.environment().put("QUANT_ROAD_LOG_DIR", logDir.getAbsolutePath());
    }

    private boolean waitForProcess(Process process) throws InterruptedException
    {
        if (properties.getCommandTimeoutSeconds() <= 0)
        {
            process.waitFor();
            return true;
        }
        return process.waitFor(properties.getCommandTimeoutSeconds(), TimeUnit.SECONDS);
    }

    private Thread startOutputPump(Process process, StringBuffer output, String commandText)
    {
        Thread thread = new Thread(() -> pumpProcessOutput(process, output, commandText), "quant-road-python-output");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void pumpProcessOutput(Process process, StringBuffer output, String commandText)
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                output.append(line).append(System.lineSeparator());
                log.info("[python] {} | {}", commandText, line);
            }
        }
        catch (IOException e)
        {
            log.warn("Failed to read python output for command {}: {}", commandText, e.getMessage());
        }
    }

    private void joinOutputThread(Thread outputThread) throws InterruptedException
    {
        if (outputThread != null)
        {
            outputThread.join(5000L);
        }
    }
}
