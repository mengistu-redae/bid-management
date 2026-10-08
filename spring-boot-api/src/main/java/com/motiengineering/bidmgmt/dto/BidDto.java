package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BidDto(
        UUID id,
        OrganizationDto organization,
        String title,
        String referenceNumber,
        String source,
        LocalDate scoutedDate,
        List<UserDto> scouts,
        Instant closingAt,
        Instant openingAt,
        Instant clarificationDeadline,
        Integer bidValidityDays,
        String status,
        String goNoGoDecision,
        String goNoGoReason,
        String exitReason,
        String notes,
        List<LotDto> lots,
        boolean canEdit,
        boolean redFlag,
        String redFlagReason,
        Instant createdAt,
        Instant updatedAt) {
}
