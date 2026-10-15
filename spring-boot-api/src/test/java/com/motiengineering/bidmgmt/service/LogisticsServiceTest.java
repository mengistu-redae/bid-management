package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.LogisticsShipment;
import com.motiengineering.bidmgmt.domain.enums.ContractStatus;
import com.motiengineering.bidmgmt.domain.enums.LogisticsStatus;
import com.motiengineering.bidmgmt.dto.CreateLogisticsShipmentRequest;
import com.motiengineering.bidmgmt.repository.ContractRepository;
import com.motiengineering.bidmgmt.repository.LogisticsShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers LogisticsService's guarded status transitions and the contract-status creation gate. */
class LogisticsServiceTest {

    private ContractRepository contractRepository;
    private LogisticsShipmentRepository logisticsShipmentRepository;
    private LogisticsService service;

    @BeforeEach
    void setUp() {
        contractRepository = mock(ContractRepository.class);
        logisticsShipmentRepository = mock(LogisticsShipmentRepository.class);
        when(logisticsShipmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new LogisticsService(logisticsShipmentRepository, contractRepository, mock(StageHistoryService.class));
    }

    private Contract contractWithStatus(ContractStatus status) {
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setStatus(status);
        return contract;
    }

    @Test
    void rejectsCreatingAShipmentForAnAwardedContract() {
        Contract contract = contractWithStatus(ContractStatus.AWARDED);
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        CreateLogisticsShipmentRequest request = new CreateLogisticsShipmentRequest(contract.getId(), "SEA", null, null);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWARDED");
    }

    @Test
    void rejectsCreatingAShipmentForATerminatedContract() {
        Contract contract = contractWithStatus(ContractStatus.TERMINATED);
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        CreateLogisticsShipmentRequest request = new CreateLogisticsShipmentRequest(contract.getId(), "SEA", null, null);

        assertThatThrownBy(() -> service.create(request, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsCreatingAShipmentForASignedContract() {
        Contract contract = contractWithStatus(ContractStatus.SIGNED);
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        CreateLogisticsShipmentRequest request = new CreateLogisticsShipmentRequest(contract.getId(), "AIR", null, null);

        LogisticsShipment shipment = service.create(request, null);

        assertThat(shipment.getStatus()).isEqualTo(LogisticsStatus.PLANNING);
        assertThat(shipment.getShipmentMode().name()).isEqualTo("AIR");
    }

    @Test
    void allowsCreatingAShipmentForAnInDeliveryContract() {
        Contract contract = contractWithStatus(ContractStatus.IN_DELIVERY);
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        CreateLogisticsShipmentRequest request = new CreateLogisticsShipmentRequest(contract.getId(), "LAND", null, null);

        LogisticsShipment shipment = service.create(request, null);

        assertThat(shipment.getContract()).isEqualTo(contract);
    }

    @Test
    void rejectsSkippingStatusesFromPlanningToDelivered() {
        LogisticsShipment shipment = new LogisticsShipment();
        shipment.setId(UUID.randomUUID());
        shipment.setStatus(LogisticsStatus.PLANNING);
        when(logisticsShipmentRepository.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> service.transitionStatus(shipment.getId(), LogisticsStatus.DELIVERED, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLANNING")
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void allowsTheNextStatusInSequenceAndStampsTheRightTimestamp() {
        LogisticsShipment shipment = new LogisticsShipment();
        shipment.setId(UUID.randomUUID());
        shipment.setStatus(LogisticsStatus.PLANNING);
        when(logisticsShipmentRepository.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        LogisticsShipment updated = service.transitionStatus(shipment.getId(), LogisticsStatus.DISPATCHED, null, null);

        assertThat(updated.getStatus()).isEqualTo(LogisticsStatus.DISPATCHED);
        assertThat(updated.getDispatchedAt()).isNotNull();
    }
}
