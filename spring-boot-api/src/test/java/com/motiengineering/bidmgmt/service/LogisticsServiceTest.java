package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.LogisticsShipment;
import com.motiengineering.bidmgmt.domain.enums.ContractStatus;
import com.motiengineering.bidmgmt.domain.enums.LogisticsStatus;
import com.motiengineering.bidmgmt.dto.CreateLogisticsShipmentRequest;
import com.motiengineering.bidmgmt.repository.ContractRepository;
import com.motiengineering.bidmgmt.repository.LogisticsShipmentRepository;
import com.motiengineering.bidmgmt.domain.enums.IndicatorLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers LogisticsService's guarded status transitions, the contract-status creation gate, and the delay-reason-on-exit rule. */
class LogisticsServiceTest {

    private ContractRepository contractRepository;
    private LogisticsShipmentRepository logisticsShipmentRepository;
    private IndicatorService indicatorService;
    private LogisticsService service;

    @BeforeEach
    void setUp() {
        contractRepository = mock(ContractRepository.class);
        logisticsShipmentRepository = mock(LogisticsShipmentRepository.class);
        when(logisticsShipmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        indicatorService = mock(IndicatorService.class);
        when(indicatorService.compute(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new IndicatorService.IndicatorResult(IndicatorLevel.GREEN, null, null, null, true, false));
        service = new LogisticsService(logisticsShipmentRepository, contractRepository, mock(StageHistoryService.class), indicatorService);
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

        assertThatThrownBy(() -> service.transitionStatus(shipment.getId(), LogisticsStatus.DELIVERED, null, null, null))
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

        LogisticsShipment updated = service.transitionStatus(shipment.getId(), LogisticsStatus.DISPATCHED, null, null, null);

        assertThat(updated.getStatus()).isEqualTo(LogisticsStatus.DISPATCHED);
        assertThat(updated.getDispatchedAt()).isNotNull();
    }

    @Test
    void requiresADelayReasonWhenExitingARedOrCriticalStage() {
        LogisticsShipment shipment = new LogisticsShipment();
        shipment.setId(UUID.randomUUID());
        shipment.setStatus(LogisticsStatus.PLANNING);
        when(logisticsShipmentRepository.findById(shipment.getId())).thenReturn(Optional.of(shipment));
        when(indicatorService.compute(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new IndicatorService.IndicatorResult(IndicatorLevel.CRITICAL, 300.0, 10.0, 3.0, true, false));

        assertThatThrownBy(() -> service.transitionStatus(shipment.getId(), LogisticsStatus.DISPATCHED, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delay reason");

        LogisticsShipment updated = service.transitionStatus(shipment.getId(), LogisticsStatus.DISPATCHED, null, "WAITING_CUSTOMS", null);
        assertThat(updated.getStatus()).isEqualTo(LogisticsStatus.DISPATCHED);
    }
}
