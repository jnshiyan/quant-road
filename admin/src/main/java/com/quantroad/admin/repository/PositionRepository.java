package com.quantroad.admin.repository;

import com.quantroad.admin.entity.PositionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    List<PositionEntity> findByLossWarningOrderByStockCodeAsc(Integer lossWarning);
}

