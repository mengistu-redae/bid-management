package com.motiengineering.bidmgmt.dto;

import jakarta.validation.constraints.NotBlank;

/** delayReasonCode is only enforced where the target entity checks it (currently BidLot) - see BidStatusService.transitionLot. */
public record StatusChangeRequest(@NotBlank String newStatus, String reason, String delayReasonCode) {
}
