package com.motiengineering.bidmgmt.dto;

public record DelayReasonParetoRowDto(String code, String label, long occurrences, double totalDelayDays) {
}
