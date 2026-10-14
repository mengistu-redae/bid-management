package com.motiengineering.bidmgmt.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreateContractRequest(
        UUID bidId,
        String contractNumber,
        LocalDate deliveryDeadline,
        String notes) {
}
