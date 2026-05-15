package com.quantroad.admin.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "strategy_run_log")
public class StrategyRunLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @Column(name = "run_time")
    private LocalDateTime runTime;

    @Column(name = "annual_return")
    private BigDecimal annualReturn;

    @Column(name = "max_drawdown")
    private BigDecimal maxDrawdown;

    @Column(name = "win_rate")
    private BigDecimal winRate;

    @Column(name = "total_profit")
    private BigDecimal totalProfit;

    @Column(name = "is_invalid")
    private Integer isInvalid;

    private String remark;
}

