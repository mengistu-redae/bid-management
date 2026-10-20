package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Null fields are left unchanged - same "only touch what's provided" convention as UpdateDealRegistrationRequest. */
public record UpdateIssueRequest(
        String status,
        String severity,
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
        String resolutionNotes,
        UUID assignedToId) {
}
