package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DealRegistrationDto(
        UUID id,
        OemDto oem,
        String distributor,
        String registrationId,
        OrganizationDto organization,
        UUID opportunityId,
        UUID bidId,
        LocalDate submittedDate,
        String status,
        LocalDate approvalDate,
        LocalDate expiryDate,
        BigDecimal protectedDiscountPercent,
        String specialPriceReference,
        String notes,
        int conflictCount,
        boolean expiringSoon,
        Instant createdAt,
        Instant updatedAt) {
}
