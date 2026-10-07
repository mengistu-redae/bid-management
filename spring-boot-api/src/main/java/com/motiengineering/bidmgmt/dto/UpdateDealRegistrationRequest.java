package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** All fields optional - only non-null fields are applied. */
public record UpdateDealRegistrationRequest(
        String distributor,
        String registrationId,
        LocalDate submittedDate,
        String status,
        LocalDate approvalDate,
        LocalDate expiryDate,
        BigDecimal protectedDiscountPercent,
        String specialPriceReference,
        String notes) {
}
