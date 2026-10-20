package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IssueDto(
        UUID id,
        String category,
        String status,
        String severity,
        OrganizationDto organization,
        DivisionDto division,
        String itemOrSystem,
        String description,
        String currentStatusNote,
        String nextAction,
        String challenge,
        UUID bidId,
        UUID contractId,
        UUID opportunityId,
        BigDecimal financialImpact,
        String financialImpactCurrency,
        Instant openedAt,
        long daysOpen,
        Instant resolvedAt,
        String resolutionNotes,
        UserDto assignedTo,
        UserDto createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
