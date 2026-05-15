package com.quantroad.admin.service;

import com.quantroad.admin.dto.StrategyConfigRequest;
import com.quantroad.admin.entity.StrategyConfigEntity;
import com.quantroad.admin.repository.StrategyConfigRepository;
import java.util.List;
import javax.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StrategyConfigService {

    private final StrategyConfigRepository strategyConfigRepository;
    private final SchedulerService schedulerService;

    public List<StrategyConfigEntity> list() {
        return strategyConfigRepository.findAll();
    }

    @Transactional
    public StrategyConfigEntity create(StrategyConfigRequest request) {
        StrategyConfigEntity entity = new StrategyConfigEntity();
        apply(entity, request);
        StrategyConfigEntity saved = strategyConfigRepository.save(entity);
        schedulerService.refreshStrategyJobs();
        return saved;
    }

    @Transactional
    public StrategyConfigEntity update(Long id, StrategyConfigRequest request) {
        StrategyConfigEntity entity = strategyConfigRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Strategy not found: " + id));
        apply(entity, request);
        StrategyConfigEntity saved = strategyConfigRepository.save(entity);
        schedulerService.refreshStrategyJobs();
        return saved;
    }

    @Transactional
    public StrategyConfigEntity toggleStatus(Long id, Integer status) {
        StrategyConfigEntity entity = strategyConfigRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Strategy not found: " + id));
        entity.setStatus(status);
        StrategyConfigEntity saved = strategyConfigRepository.save(entity);
        schedulerService.refreshStrategyJobs();
        return saved;
    }

    private void apply(StrategyConfigEntity entity, StrategyConfigRequest request) {
        entity.setStrategyName(request.getStrategyName());
        entity.setStrategyType(request.getStrategyType());
        entity.setParams(request.getParams());
        entity.setCronExpr(request.getCronExpr());
        entity.setStatus(request.getStatus());
    }
}

