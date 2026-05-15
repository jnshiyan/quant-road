package com.quantroad.admin.repository;

import com.quantroad.admin.entity.TradeSignalEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeSignalRepository extends JpaRepository<TradeSignalEntity, Long> {

    List<TradeSignalEntity> findTop50ByOrderBySignalDateDescCreateTimeDesc();

    List<TradeSignalEntity> findBySignalDateOrderBySignalTypeAscStockCodeAsc(LocalDate signalDate);

    long countBySignalDate(LocalDate signalDate);
}

