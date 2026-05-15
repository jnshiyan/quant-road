package com.quantroad.admin.controller;

import com.quantroad.admin.dto.StrategyConfigRequest;
import com.quantroad.admin.entity.StrategyConfigEntity;
import com.quantroad.admin.service.StrategyConfigService;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/strategies")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyConfigService strategyConfigService;

    @GetMapping
    public List<StrategyConfigEntity> list() {
        return strategyConfigService.list();
    }

    @PostMapping
    public StrategyConfigEntity create(@Valid @RequestBody StrategyConfigRequest request) {
        return strategyConfigService.create(request);
    }

    @PutMapping("/{id}")
    public StrategyConfigEntity update(@PathVariable Long id, @Valid @RequestBody StrategyConfigRequest request) {
        return strategyConfigService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public StrategyConfigEntity toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        return strategyConfigService.toggleStatus(id, status);
    }
}

