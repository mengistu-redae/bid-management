package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** All fields optional - only non-null fields are applied. */
public record UpdateOpportunityRequest(
        String title,
        BigDecimal estimatedValue,
        String estimatedValueCurrency,
        LocalDate expectedTenderDate,
        String stage,
        String lostReason,
        List<String> divisionCodes,
        List<String> oemNames,
        UUID ownerId,
        String notes) {
}
