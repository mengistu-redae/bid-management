package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateLogisticsShipmentRequest(
        UUID contractId,
        String shipmentMode,
        Instant etaAt,
        String notes) {
}
