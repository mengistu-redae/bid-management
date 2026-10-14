package com.motiengineering.bidmgmt.dto;

public record StageDurationStatDto(
        String stageValue,
        long completedCount,
        double avgBusinessDays,
        double medianBusinessDays,
        double p90BusinessDays,
        double percentOverTarget) {
}
