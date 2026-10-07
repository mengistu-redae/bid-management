package com.motiengineering.bidmgmt.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateOpportunityRequest(
        UUID organizationId,
        String organizationName,
        String sector,
        @NotBlank String title,
        BigDecimal estimatedValue,
        String estimatedValueCurrency,
        LocalDate expectedTenderDate,
        List<String> divisionCodes,
        List<String> oemNames,
        UUID ownerId,
        String notes) {
}
