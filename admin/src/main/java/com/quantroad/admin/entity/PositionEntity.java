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
@Table(name = "position")
public class PositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false)
    private String stockCode;

    @Column(name = "stock_name")
    private String stockName;

    private Integer quantity;

    @Column(name = "cost_price")
    private BigDecimal costPrice;

    @Column(name = "current_price")
    private BigDecimal currentPrice;

    @Column(name = "float_profit")
    private BigDecimal floatProfit;

    @Column(name = "loss_warning")
    private Integer lossWarning;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}

