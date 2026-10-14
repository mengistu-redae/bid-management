package com.motiengineering.bidmgmt.dto;

import java.util.UUID;

public record DateSlippageRowDto(String entityType, UUID entityId, String label, String fieldName, int changeCount, Long slippageDays) {
}
