package com.quantroad.admin.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StrategyConfigRequest {

    @NotBlank
    private String strategyName;

    @NotBlank
    private String strategyType;

    @NotBlank
    private String params;

    @NotBlank
    private String cronExpr;

    @NotNull
    private Integer status;
}

