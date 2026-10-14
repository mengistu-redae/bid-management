package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.IndicatorConfig;
import com.motiengineering.bidmgmt.repository.IndicatorConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class IndicatorConfigService {

    private final IndicatorConfigRepository indicatorConfigRepository;
    private final IndicatorService indicatorService;

    public IndicatorConfig get() {
        return indicatorService.indicatorConfig();
    }

    @Transactional
    public IndicatorConfig update(BigDecimal amber, BigDecimal red, BigDecimal critical, BigDecimal defaultStaleAfterDays, AppUser actingUser) {
        IndicatorConfig config = indicatorConfigRepository.findById(1).orElseGet(IndicatorConfig::new);
        config.setId(1);
        config.setAmberThresholdPercent(amber);
        config.setRedThresholdPercent(red);
        config.setCriticalThresholdPercent(critical);
        config.setDefaultStaleAfterDays(defaultStaleAfterDays);
        config.setUpdatedBy(actingUser);
        return indicatorConfigRepository.save(config);
    }
}
