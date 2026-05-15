package com.quantroad.admin.repository;

import com.quantroad.admin.entity.StrategyConfigEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyConfigRepository extends JpaRepository<StrategyConfigEntity, Long> {

    List<StrategyConfigEntity> findByStatusOrderByIdAsc(Integer status);
}

