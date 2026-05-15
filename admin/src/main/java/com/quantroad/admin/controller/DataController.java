package com.quantroad.admin.controller;

import com.quantroad.admin.entity.PositionEntity;
import com.quantroad.admin.entity.StrategyRunLogEntity;
import com.quantroad.admin.entity.TradeSignalEntity;
import com.quantroad.admin.repository.PositionRepository;
import com.quantroad.admin.repository.StrategyRunLogRepository;
import com.quantroad.admin.repository.TradeSignalRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataController {

    private final TradeSignalRepository tradeSignalRepository;
    private final PositionRepository positionRepository;
    private final StrategyRunLogRepository strategyRunLogRepository;

    @GetMapping("/signals")
    public List<TradeSignalEntity> signals(@RequestParam(required = false) String signalDate) {
        LocalDate date = signalDate == null ? LocalDate.now() : LocalDate.parse(signalDate);
        return tradeSignalRepository.findBySignalDateOrderBySignalTypeAscStockCodeAsc(date);
    }

    @GetMapping("/positions")
    public List<PositionEntity> positions() {
        return positionRepository.findAll();
    }

    @GetMapping("/strategy-logs")
    public List<StrategyRunLogEntity> strategyLogs() {
        return strategyRunLogRepository.findTop20ByOrderByRunTimeDesc();
    }
}

