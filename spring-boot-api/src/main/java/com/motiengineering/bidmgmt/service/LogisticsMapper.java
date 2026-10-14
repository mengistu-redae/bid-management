package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.LogisticsShipment;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.IndicatorDto;
import com.motiengineering.bidmgmt.dto.LogisticsShipmentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class LogisticsMapper {

    private final HoldService holdService;
    private final IndicatorService indicatorService;

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
                shipment.getStatus().isTerminal() ? null : indicatorFor(shipment),
                shipment.getCreatedAt(),
                shipment.getUpdatedAt());
    }

    private IndicatorDto indicatorFor(LogisticsShipment shipment) {
        TargetContext context = new TargetContext(null, null, null, shipment.getShipmentMode());
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.LOGISTICS, shipment.getId(), "status", shipment.getStatus().name(), shipment.getCreatedAt(), context, Map.of());
        return new IndicatorDto(result.level().name(), result.percentUsed(), result.daysUsed(), result.targetDays(), result.usesBusinessDays(), result.stale());
    }
}
