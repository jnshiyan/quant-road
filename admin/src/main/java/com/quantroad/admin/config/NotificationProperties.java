package com.quantroad.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "quant-road.notification")
public class NotificationProperties {

    private boolean enabled;
    private String webhook;
    private String type = "dingding";
}

