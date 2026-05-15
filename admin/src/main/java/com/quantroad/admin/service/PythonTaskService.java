package com.quantroad.admin.service;

import com.quantroad.admin.config.PythonRunnerProperties;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PythonTaskService {

    private final PythonRunnerProperties properties;

    public String runFullDaily(Long strategyId, String startDate, String strategyStartDate, Boolean notify) {
        return runFullDaily(strategyId, startDate, strategyStartDate, notify, null, null, null);
    }

    public String runFullDaily(
        Long strategyId,
        String startDate,
        String strategyStartDate,
        Boolean notify,
        Boolean usePortfolio,
        Double portfolioTotalCapital,
        String actor
    ) {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("full-daily");
        command.add("--start-date");
        command.add(startDate == null ? properties.getDefaultStartDate() : startDate);
        command.add("--strategy-start-date");
        command.add(strategyStartDate == null ? properties.getStrategyBacktestStartDate() : strategyStartDate);
        if (strategyId != null) {
            command.add("--strategy-id");
            command.add(String.valueOf(strategyId));
        }
        if (Boolean.TRUE.equals(notify) || (notify == null && properties.isNotifyByDefault())) {
            command.add("--notify");
        }
        if (Boolean.TRUE.equals(usePortfolio)) {
            command.add("--use-portfolio");
        }
        if (portfolioTotalCapital != null && portfolioTotalCapital > 0) {
            command.add("--portfolio-total-capital");
            command.add(String.valueOf(portfolioTotalCapital));
        }
        if (actor != null && !actor.isBlank()) {
            command.add("--actor");
            command.add(actor);
        }
        return execute(command);
    }

    public String runSignalsNotify() {
        return execute(List.of(properties.getExecutable(), "-m", properties.getModuleName(), "notify-signals"));
    }

    public String runSyncBasic() {
        return execute(List.of(properties.getExecutable(), "-m", properties.getModuleName(), "sync-basic"));
    }

    public String runSyncDaily(String startDate) {
        return execute(List.of(
            properties.getExecutable(),
            "-m",
            properties.getModuleName(),
            "sync-daily",
            "--start-date",
            startDate == null ? properties.getDefaultStartDate() : startDate
        ));
    }

    public String runSyncValuation(String indexCodes, String updateDate) {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("sync-valuation");
        if (indexCodes != null && !indexCodes.isBlank()) {
            command.add("--index-codes");
            command.add(indexCodes);
        }
        if (updateDate != null && !updateDate.isBlank()) {
            command.add("--update-date");
            command.add(updateDate);
        }
        return execute(command);
    }

    public String runEvaluateMarket(Integer holdDays) {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("evaluate-market");
        if (holdDays != null && holdDays > 0) {
            command.add("--hold-days");
            command.add(String.valueOf(holdDays));
        }
        return execute(command);
    }

    public String runStrategy(Long strategyId, String strategyStartDate) {
        return runStrategy(strategyId, strategyStartDate, null, null);
    }

    public String runStrategy(Long strategyId, String strategyStartDate, Double portfolioCapital, String actor) {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("run-strategy");
        command.add("--start-date");
        command.add(strategyStartDate == null ? properties.getStrategyBacktestStartDate() : strategyStartDate);
        if (strategyId != null) {
            command.add("--strategy-id");
            command.add(String.valueOf(strategyId));
        }
        if (portfolioCapital != null && portfolioCapital > 0) {
            command.add("--portfolio-capital");
            command.add(String.valueOf(portfolioCapital));
        }
        if (actor != null && !actor.isBlank()) {
            command.add("--actor");
            command.add(actor);
        }
        return execute(command);
    }

    public String runPortfolio(String strategyStartDate, Double totalCapital, String actor) {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("run-portfolio");
        command.add("--start-date");
        command.add(strategyStartDate == null ? properties.getStrategyBacktestStartDate() : strategyStartDate);
        if (totalCapital != null && totalCapital > 0) {
            command.add("--total-capital");
            command.add(String.valueOf(totalCapital));
        }
        if (actor != null && !actor.isBlank()) {
            command.add("--actor");
            command.add(actor);
        }
        return execute(command);
    }

    public String runEvaluateExecutionFeedback(String asOfDate, Integer graceDays) {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("evaluate-execution-feedback");
        if (asOfDate != null && !asOfDate.isBlank()) {
            command.add("--as-of-date");
            command.add(asOfDate);
        }
        if (graceDays != null && graceDays >= 0) {
            command.add("--grace-days");
            command.add(String.valueOf(graceDays));
        }
        return execute(command);
    }

    public String runCanaryEvaluate(Long baselineStrategyId, Long candidateStrategyId, Integer months) {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("canary-evaluate");
        command.add("--baseline-strategy-id");
        command.add(String.valueOf(baselineStrategyId == null ? 1L : baselineStrategyId));
        command.add("--candidate-strategy-id");
        command.add(String.valueOf(candidateStrategyId));
        if (months != null && months > 0) {
            command.add("--months");
            command.add(String.valueOf(months));
        }
        return execute(command);
    }

    public String runRisk(Long strategyId) {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("evaluate-risk");
        if (strategyId != null) {
            command.add("--strategy-id");
            command.add(String.valueOf(strategyId));
        }
        return execute(command);
    }

    public String runMonthlyReport(Integer months, String output) {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("monthly-report");
        if (months != null && months > 0) {
            command.add("--months");
            command.add(String.valueOf(months));
        }
        if (output != null && !output.isBlank()) {
            command.add("--output");
            command.add(output);
        }
        return execute(command);
    }

    public String runStrategyCapabilities() {
        return execute(List.of(
            properties.getExecutable(),
            "-m",
            properties.getModuleName(),
            "strategy-capabilities",
            "--format",
            "json"
        ));
    }

    public String runShadowCompareJson(Long baselineStrategyId, Long candidateStrategyId, Integer months) {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("shadow-compare");
        command.add("--baseline-strategy-id");
        command.add(String.valueOf(baselineStrategyId == null ? 1L : baselineStrategyId));
        command.add("--candidate-strategy-id");
        command.add(String.valueOf(candidateStrategyId));
        if (months != null && months > 0) {
            command.add("--months");
            command.add(String.valueOf(months));
        }
        command.add("--format");
        command.add("json");
        return execute(command);
    }

    public String runShadowCompareReport(Long baselineStrategyId, Long candidateStrategyId, Integer months, String output) {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable());
        command.add("-m");
        command.add(properties.getModuleName());
        command.add("shadow-compare");
        command.add("--baseline-strategy-id");
        command.add(String.valueOf(baselineStrategyId == null ? 1L : baselineStrategyId));
        command.add("--candidate-strategy-id");
        command.add(String.valueOf(candidateStrategyId));
        if (months != null && months > 0) {
            command.add("--months");
            command.add(String.valueOf(months));
        }
        command.add("--format");
        command.add("text");
        if (output != null && !output.isBlank()) {
            command.add("--output");
            command.add(output);
        }
        return execute(command);
    }

    private String execute(List<String> command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(properties.getWorkdir()));
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().putIfAbsent("PYTHONPATH", "src");
        StringBuilder output = new StringBuilder();
        try {
            log.info("Running python command: {}", String.join(" ", command));
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Python task failed, exitCode=" + exitCode + ", output=" + output);
            }
            return output.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start python command: " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python task interrupted: " + command, e);
        }
    }
}
