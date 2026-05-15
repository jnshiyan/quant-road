package com.quantroad.admin;

import com.quantroad.admin.config.NotificationProperties;
import com.quantroad.admin.config.PythonRunnerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({PythonRunnerProperties.class, NotificationProperties.class})
public class QuantRoadAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuantRoadAdminApplication.class, args);
    }
}

