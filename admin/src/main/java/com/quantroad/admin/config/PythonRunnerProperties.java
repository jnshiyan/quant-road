package com.quantroad.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "quant-road.python")
public class PythonRunnerProperties {

    private String executable = "python";
    private String workdir = "../python";
    private String moduleName = "quant_road";
    private String defaultStartDate = "20230101";
    private String strategyBacktestStartDate = "2023-01-01";
    private boolean notifyByDefault = true;
}
