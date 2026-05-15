package com.quantroad.admin.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "trade_signal")
public class TradeSignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false)
    private String stockCode;

    @Column(name = "stock_name")
    private String stockName;

    @Column(name = "signal_type", nullable = false)
    private String signalType;

    @Column(name = "suggest_price")
    private BigDecimal suggestPrice;

    @Column(name = "signal_date", nullable = false)
    private LocalDate signalDate;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @Column(name = "is_execute")
    private Integer isExecute;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}

