package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.time.LocalDate;

/** All fields optional - only non-null fields are applied. Status changes go through /status instead, so they always get a reason/history row. */
public record UpdateContractRequest(
        String contractNumber,
        Instant signedAt,
        LocalDate deliveryDeadline,
        String notes) {
}
