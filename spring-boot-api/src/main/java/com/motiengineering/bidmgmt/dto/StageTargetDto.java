package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StageTargetDto(
        UUID id,
        String entityType,
        String stageValue,
        String targetType,
        BigDecimal targetDays,
        boolean usesBusinessDays,
        String deadlineField,
        BigDecimal deadlineOffsetDays,
        UUID divisionId,
        String divisionName,
        String sector,
        String oemName,
        String shipmentMode,
        BigDecimal staleAfterDays,
        boolean active,
        String notes) {
}
