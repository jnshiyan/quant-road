package com.quantroad.admin.entity;

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
@Table(name = "strategy_config")
public class StrategyConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_name", nullable = false)
    private String strategyName;

    @Column(name = "strategy_type")
    private String strategyType;

    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private String params;

    @Column(name = "cron_expr", nullable = false)
    private String cronExpr;

    private Integer status;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
