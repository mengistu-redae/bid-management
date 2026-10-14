package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.LogisticsShipment;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.LogisticsShipmentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LogisticsMapper {

    private final HoldService holdService;

    public LogisticsShipmentDto toDto(LogisticsShipment shipment) {
        return new LogisticsShipmentDto(
                shipment.getId(),
                shipment.getContract().getId(),
                shipment.getShipmentMode().name(),
                shipment.getStatus().name(),
                shipment.getDispatchedAt(),
                shipment.getEtaAt(),
                shipment.getArrivedAt(),
                shipment.getCustomsClearedAt(),
                shipment.getDeliveredAt(),
                shipment.getNotes(),
                holdService.isOnHold(EntityType.LOGISTICS, shipment.getId()),
                shipment.getCreatedAt(),
                shipment.getUpdatedAt());
    }
}
