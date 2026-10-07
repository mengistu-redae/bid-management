package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OpportunityDto(
        UUID id,
        OrganizationDto organization,
        String title,
        BigDecimal estimatedValue,
        String estimatedValueCurrency,
        LocalDate expectedTenderDate,
        String stage,
        UserDto owner,
        String lostReason,
        UUID convertedBidId,
        String notes,
        List<DivisionDto> divisions,
        List<OemDto> oems,
        boolean canEdit,
        Instant createdAt,
        Instant updatedAt) {
}
