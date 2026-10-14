package com.motiengineering.bidmgmt.dto;

public record IndicatorDto(
        String level,
        Double percentUsed,
        Double daysUsed,
        Double targetDays,
        boolean usesBusinessDays,
        boolean stale) {
}
