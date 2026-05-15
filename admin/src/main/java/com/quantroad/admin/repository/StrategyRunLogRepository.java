package com.quantroad.admin.repository;

import com.quantroad.admin.entity.StrategyRunLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyRunLogRepository extends JpaRepository<StrategyRunLogEntity, Long> {

    List<StrategyRunLogEntity> findTop20ByOrderByRunTimeDesc();

    List<StrategyRunLogEntity> findTop5ByIsInvalidOrderByRunTimeDesc(Integer isInvalid);
}

