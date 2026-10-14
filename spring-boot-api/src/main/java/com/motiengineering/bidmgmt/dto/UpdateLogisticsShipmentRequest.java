package com.motiengineering.bidmgmt.dto;

import java.time.Instant;

/** All fields optional - only non-null fields are applied. Status changes go through /status instead. */
public record UpdateLogisticsShipmentRequest(
        String shipmentMode,
        Instant dispatchedAt,
        Instant etaAt,
        Instant arrivedAt,
        Instant customsClearedAt,
        Instant deliveredAt,
        String notes) {
}
