package com.motiengineering.bidmgmt.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

public record UpsertStageTargetRequest(
        @NotBlank String entityType,
        @NotBlank String stageValue,
        @NotBlank String targetType,
        BigDecimal targetDays,
        boolean usesBusinessDays,
        String deadlineField,
        BigDecimal deadlineOffsetDays,
        UUID divisionId,
        String sector,
        String oemName,
        String shipmentMode,
        BigDecimal staleAfterDays,
        boolean active,
        String notes) {
}
