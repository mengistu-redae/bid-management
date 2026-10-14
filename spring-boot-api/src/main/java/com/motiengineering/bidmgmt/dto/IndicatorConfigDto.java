package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;

public record IndicatorConfigDto(
        BigDecimal amberThresholdPercent,
        BigDecimal redThresholdPercent,
        BigDecimal criticalThresholdPercent,
        BigDecimal defaultStaleAfterDays) {
}
