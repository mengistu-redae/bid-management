package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.dto.IndicatorConfigDto;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.IndicatorConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/indicator-config")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndicatorConfigController {

    private final IndicatorConfigService indicatorConfigService;

    public record UpdateIndicatorConfigRequest(java.math.BigDecimal amberThresholdPercent, java.math.BigDecimal redThresholdPercent,
                                                 java.math.BigDecimal criticalThresholdPercent, java.math.BigDecimal defaultStaleAfterDays) {
    }

    @GetMapping
    public IndicatorConfigDto get() {
        return toDto(indicatorConfigService.get());
    }

    @PutMapping
    @Transactional
    public IndicatorConfigDto update(@RequestBody UpdateIndicatorConfigRequest request) {
        var user = CurrentUserContext.getUser();
        if (user == null || user.getRole() != com.motiengineering.bidmgmt.domain.enums.Role.DIRECTOR) {
            throw new AccessDeniedException("Only the Director can edit indicator thresholds");
        }
        return toDto(indicatorConfigService.update(
                request.amberThresholdPercent(), request.redThresholdPercent(),
                request.criticalThresholdPercent(), request.defaultStaleAfterDays(), user));
    }

    private IndicatorConfigDto toDto(com.motiengineering.bidmgmt.domain.IndicatorConfig config) {
        return new IndicatorConfigDto(config.getAmberThresholdPercent(), config.getRedThresholdPercent(),
                config.getCriticalThresholdPercent(), config.getDefaultStaleAfterDays());
    }
}
