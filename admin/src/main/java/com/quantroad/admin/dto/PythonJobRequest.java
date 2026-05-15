package com.quantroad.admin.dto;

import lombok.Data;

@Data
public class PythonJobRequest {

    private Long strategyId;
    private String startDate;
    private String strategyBacktestStartDate;
    private Boolean notify;
    private Boolean usePortfolio;
    private Double portfolioTotalCapital;
    private String actor;
}
