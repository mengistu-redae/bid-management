package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateIssueRequest(
        String category,
        String severity,
        UUID organizationId,
        String organizationName,
        String sector,
        UUID divisionId,
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
        UUID assignedToId) {
}
