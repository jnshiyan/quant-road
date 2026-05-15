package com.quantroad.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class LegacyStartupWarning implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyStartupWarning.class);

    @Override
    public void run(ApplicationArguments args) {
        log.warn("Starting legacy admin backend on port 18080. The supported mainline backend is ruoyi-admin on port 8080.");
    }
}
