package com.motiengineering.bidmgmt.dto;

/** One row of a breakdown-by-dimension table (division/officer/customer/OEM) - dimension names which table this row came from when several are concatenated in one export sheet. */
public record DelayBreakdownRowDto(String dimension, String key, long completedCount, double avgBusinessDays, double totalDelayDays) {
}
