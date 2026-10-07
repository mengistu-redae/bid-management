package com.motiengineering.bidmgmt.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateBidRequest(
        UUID organizationId,
        String organizationName,
        String sector,
        @NotBlank String title,
        String referenceNumber,
        String source,
        LocalDate scoutedDate,
        List<String> scoutNames,
        Instant closingAt,
        Instant openingAt,
        Instant clarificationDeadline,
        Integer bidValidityDays,
        String notes,
        UUID opportunityId,
        CreateLotRequest firstLot) {
}
