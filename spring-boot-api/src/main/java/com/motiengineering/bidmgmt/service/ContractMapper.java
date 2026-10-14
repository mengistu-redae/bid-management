package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.ContractDto;
import com.motiengineering.bidmgmt.dto.IndicatorDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContractMapper {

    private final HoldService holdService;
    private final IndicatorService indicatorService;

    public ContractDto toDto(Contract contract) {
        return new ContractDto(
                contract.getId(),
                contract.getBid().getId(),
                contract.getBid().getTitle(),
                contract.getContractNumber(),
                contract.getStatus().name(),
                contract.getSignedAt(),
                contract.getDeliveryDeadline(),
                contract.getCompletedAt(),
                contract.getExitReason(),
                contract.getNotes(),
                holdService.isOnHold(EntityType.CONTRACT, contract.getId()),
                contract.getStatus().isTerminal() ? null : indicatorFor(contract),
                contract.getCreatedAt(),
                contract.getUpdatedAt());
    }

    private IndicatorDto indicatorFor(Contract contract) {
        TargetContext context = new TargetContext(
                null,
                contract.getBid().getOrganization() == null ? null : contract.getBid().getOrganization().getSector(),
                null, null);
        Instant deliveryDeadline = contract.getDeliveryDeadline() == null ? null : contract.getDeliveryDeadline().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Map<String, Instant> deadlineFields = IndicatorService.deadlineField("deliveryDeadline", deliveryDeadline);
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.CONTRACT, contract.getId(), "status", contract.getStatus().name(), contract.getCreatedAt(), context, deadlineFields);
        return new IndicatorDto(result.level().name(), result.percentUsed(), result.daysUsed(), result.targetDays(), result.usesBusinessDays(), result.stale());
    }
}
