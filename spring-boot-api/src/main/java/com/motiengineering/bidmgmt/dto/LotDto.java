package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LotDto(
        UUID id,
        UUID bidId,
        String lotLabel,
        String description,
        DivisionDto division,
        BigDecimal estimatedValue,
        String estimatedValueCurrency,
        BigDecimal bidBondAmount,
        String bidBondCurrency,
        Integer bidBondValidityDays,
        String bidBondForm,
        String oemBrand,
        String status,
        String exitReason,
        String outcome,
        String winnerName,
        BigDecimal winningPrice,
        String winningPriceCurrency,
        boolean needsReview,
        String reviewNote,
        List<String> scopeTypes,
        List<UserDto> accountOfficers,
        List<ChecklistItemDto> checklistItems,
        int checklistProgressPercent) {
}
