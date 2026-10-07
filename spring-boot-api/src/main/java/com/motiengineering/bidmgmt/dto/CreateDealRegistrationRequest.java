package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateDealRegistrationRequest(
        UUID oemId,
        String oemName,
        String distributor,
        String registrationId,
        UUID organizationId,
        String organizationName,
        String sector,
        UUID opportunityId,
        UUID bidId,
        LocalDate submittedDate,
        String status,
        LocalDate approvalDate,
        LocalDate expiryDate,
        BigDecimal protectedDiscountPercent,
        String specialPriceReference,
        String notes) {
}
