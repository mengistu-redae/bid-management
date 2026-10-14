package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.util.UUID;

public record LogisticsShipmentDto(
        UUID id,
        UUID contractId,
        String shipmentMode,
        String status,
        Instant dispatchedAt,
        Instant etaAt,
        Instant arrivedAt,
        Instant customsClearedAt,
        Instant deliveredAt,
        String notes,
        boolean onHold,
        IndicatorDto indicator,
        Instant createdAt,
        Instant updatedAt) {
}
