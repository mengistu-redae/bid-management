package com.motiengineering.bidmgmt.dto;

public record FieldSlippageSummaryDto(String entityType, String fieldName, long totalChangeCount, double totalSlippageDays) {
}
