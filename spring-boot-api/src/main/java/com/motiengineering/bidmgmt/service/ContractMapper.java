package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.ContractDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractMapper {

    private final HoldService holdService;

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
                contract.getCreatedAt(),
                contract.getUpdatedAt());
    }
}
