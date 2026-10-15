package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.LogisticsShipment;
import com.motiengineering.bidmgmt.domain.enums.ContractStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.LogisticsStatus;
import com.motiengineering.bidmgmt.domain.enums.ShipmentMode;
import com.motiengineering.bidmgmt.dto.CreateLogisticsShipmentRequest;
import com.motiengineering.bidmgmt.dto.UpdateLogisticsShipmentRequest;
import com.motiengineering.bidmgmt.repository.ContractRepository;
import com.motiengineering.bidmgmt.repository.LogisticsShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LogisticsService {

    private static final Map<LogisticsStatus, Set<LogisticsStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(LogisticsStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(LogisticsStatus.PLANNING, EnumSet.of(LogisticsStatus.DISPATCHED));
        ALLOWED_TRANSITIONS.put(LogisticsStatus.DISPATCHED, EnumSet.of(LogisticsStatus.IN_TRANSIT));
        ALLOWED_TRANSITIONS.put(LogisticsStatus.IN_TRANSIT, EnumSet.of(LogisticsStatus.CUSTOMS));
        ALLOWED_TRANSITIONS.put(LogisticsStatus.CUSTOMS, EnumSet.of(LogisticsStatus.CLEARED));
        ALLOWED_TRANSITIONS.put(LogisticsStatus.CLEARED, EnumSet.of(LogisticsStatus.DELIVERED));
        ALLOWED_TRANSITIONS.put(LogisticsStatus.DELIVERED, EnumSet.noneOf(LogisticsStatus.class));
    }

    private final LogisticsShipmentRepository logisticsShipmentRepository;
    private final ContractRepository contractRepository;
    private final StageHistoryService stageHistoryService;

    public LogisticsShipment get(UUID id) {
        return logisticsShipmentRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Shipment not found: " + id));
    }

    public List<LogisticsShipment> forContract(UUID contractId) {
        return logisticsShipmentRepository.findByContract_IdOrderByCreatedAt(contractId);
    }

    @Transactional
    public LogisticsShipment create(CreateLogisticsShipmentRequest request, AppUser creator) {
        Contract contract = contractRepository.findById(request.contractId())
                .orElseThrow(() -> new NoSuchElementException("Contract not found: " + request.contractId()));
        if (contract.getStatus() != ContractStatus.SIGNED && contract.getStatus() != ContractStatus.IN_DELIVERY) {
            throw new IllegalStateException("A shipment can only be added to a contract that is Signed or In Delivery (this one is " + contract.getStatus() + ")");
        }

        LogisticsShipment shipment = new LogisticsShipment();
        shipment.setContract(contract);
        shipment.setShipmentMode(request.shipmentMode() != null ? ShipmentMode.valueOf(request.shipmentMode().toUpperCase()) : ShipmentMode.SEA);
        shipment.setEtaAt(request.etaAt());
        shipment.setNotes(request.notes());
        shipment.setCreatedBy(creator);
        shipment = logisticsShipmentRepository.save(shipment);

        stageHistoryService.record(EntityType.LOGISTICS, shipment.getId(), "status", null, LogisticsStatus.PLANNING.name(), null, creator);
        return shipment;
    }

    @Transactional
    public LogisticsShipment update(UUID id, UpdateLogisticsShipmentRequest request, AppUser actingUser) {
        LogisticsShipment shipment = get(id);
        if (request.shipmentMode() != null) {
            shipment.setShipmentMode(ShipmentMode.valueOf(request.shipmentMode().toUpperCase()));
        }
        if (request.dispatchedAt() != null) {
            shipment.setDispatchedAt(request.dispatchedAt());
        }
        if (request.etaAt() != null) {
            shipment.setEtaAt(request.etaAt());
        }
        if (request.arrivedAt() != null) {
            shipment.setArrivedAt(request.arrivedAt());
        }
        if (request.customsClearedAt() != null) {
            shipment.setCustomsClearedAt(request.customsClearedAt());
        }
        if (request.deliveredAt() != null) {
            shipment.setDeliveredAt(request.deliveredAt());
        }
        if (request.notes() != null) {
            shipment.setNotes(request.notes());
        }
        return logisticsShipmentRepository.save(shipment);
    }

    @Transactional
    public LogisticsShipment transitionStatus(UUID id, LogisticsStatus newStatus, String reason, AppUser actingUser) {
        LogisticsShipment shipment = get(id);
        LogisticsStatus oldStatus = shipment.getStatus();
        if (!ALLOWED_TRANSITIONS.getOrDefault(oldStatus, Set.of()).contains(newStatus)) {
            throw new IllegalStateException("Cannot move a shipment from " + oldStatus + " to " + newStatus);
        }

        Instant now = Instant.now();
        shipment.setStatus(newStatus);
        switch (newStatus) {
            case DISPATCHED -> shipment.setDispatchedAt(now);
            case CUSTOMS -> shipment.setArrivedAt(now);
            case CLEARED -> shipment.setCustomsClearedAt(now);
            case DELIVERED -> shipment.setDeliveredAt(now);
            default -> { /* PLANNING, IN_TRANSIT carry no extra timestamp */ }
        }
        stageHistoryService.record(EntityType.LOGISTICS, shipment.getId(), "status", oldStatus.name(), newStatus.name(), reason, actingUser);
        return logisticsShipmentRepository.save(shipment);
    }
}
