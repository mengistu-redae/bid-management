package com.motiengineering.bidmgmt.dto;

public record MonthlyStageTrendDto(String stageValue, String yearMonth, long completedCount, double avgBusinessDays) {
}
