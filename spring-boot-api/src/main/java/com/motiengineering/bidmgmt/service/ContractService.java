package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.ContractStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.CreateContractRequest;
import com.motiengineering.bidmgmt.dto.UpdateContractRequest;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.ContractRepository;
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

/**
 * Contracts are created manually once a bid is WON (not auto-created - the
 * business side still has to negotiate/produce the actual paper), one per
 * bid. Status transitions follow the same guarded-transition + history
 * pattern as BidStatusService, just against stage_history instead of a
 * dedicated table, since there's no legacy contract_status_history to stay
 * compatible with.
 */
@Service
@RequiredArgsConstructor
public class ContractService {

    private static final Map<ContractStatus, Set<ContractStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(ContractStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(ContractStatus.AWARDED, EnumSet.of(ContractStatus.SIGNED, ContractStatus.TERMINATED));
        ALLOWED_TRANSITIONS.put(ContractStatus.SIGNED, EnumSet.of(ContractStatus.IN_DELIVERY, ContractStatus.TERMINATED));
        ALLOWED_TRANSITIONS.put(ContractStatus.IN_DELIVERY, EnumSet.of(ContractStatus.COMPLETED, ContractStatus.TERMINATED));
        ALLOWED_TRANSITIONS.put(ContractStatus.COMPLETED, EnumSet.noneOf(ContractStatus.class));
        ALLOWED_TRANSITIONS.put(ContractStatus.TERMINATED, EnumSet.noneOf(ContractStatus.class));
    }

    private final ContractRepository contractRepository;
    private final BidRepository bidRepository;
    private final StageHistoryService stageHistoryService;
    private final IndicatorService indicatorService;

    public Contract get(UUID id) {
        return contractRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Contract not found: " + id));
    }

    public List<Contract> list() {
        return contractRepository.findAll();
    }

    @Transactional
    public Contract create(CreateContractRequest request, AppUser creator) {
        Bid bid = bidRepository.findById(request.bidId())
                .orElseThrow(() -> new NoSuchElementException("Bid not found: " + request.bidId()));
        if (bid.getStatus() != BidStatus.WON) {
            throw new IllegalStateException("Only a WON bid can have a contract");
        }
        contractRepository.findByBid_Id(bid.getId()).ifPresent(existing -> {
            throw new IllegalStateException("Bid already has a contract: " + existing.getId());
        });

        Contract contract = new Contract();
        contract.setBid(bid);
        contract.setContractNumber(request.contractNumber());
        contract.setDeliveryDeadline(request.deliveryDeadline());
        contract.setNotes(request.notes());
        contract.setCreatedBy(creator);
        contract = contractRepository.save(contract);

        stageHistoryService.record(EntityType.CONTRACT, contract.getId(), "status", null, ContractStatus.AWARDED.name(), null, creator);
        return contract;
    }

    @Transactional
    public Contract update(UUID id, UpdateContractRequest request, AppUser actingUser) {
        Contract contract = get(id);
        if (request.contractNumber() != null) {
            contract.setContractNumber(request.contractNumber());
        }
        if (request.signedAt() != null) {
            contract.setSignedAt(request.signedAt());
        }
        if (request.deliveryDeadline() != null) {
            contract.setDeliveryDeadline(request.deliveryDeadline());
        }
        if (request.notes() != null) {
            contract.setNotes(request.notes());
        }
        return contractRepository.save(contract);
    }

    @Transactional
    public Contract transitionStatus(UUID id, ContractStatus newStatus, String reason, String delayReasonCode, AppUser actingUser) {
        Contract contract = get(id);
        ContractStatus oldStatus = contract.getStatus();
        if (!ALLOWED_TRANSITIONS.getOrDefault(oldStatus, Set.of()).contains(newStatus)) {
            throw new IllegalStateException("Cannot move a contract from " + oldStatus + " to " + newStatus);
        }
        if (newStatus == ContractStatus.TERMINATED && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("TERMINATED requires a reason");
        }
        requireDelayReasonIfRedOrCritical(contract, delayReasonCode);

        contract.setStatus(newStatus);
        if (newStatus == ContractStatus.SIGNED) {
            contract.setSignedAt(Instant.now());
        }
        if (newStatus == ContractStatus.COMPLETED) {
            contract.setCompletedAt(Instant.now());
        }
        if (newStatus == ContractStatus.TERMINATED) {
            contract.setExitReason(reason);
        }
        stageHistoryService.record(EntityType.CONTRACT, contract.getId(), "status", oldStatus.name(), newStatus.name(), reason, delayReasonCode, actingUser);
        return contractRepository.save(contract);
    }

    /** Red/critical is measured against the contract's CURRENT (about-to-be-exited) status - same rule as BidStatusService.transitionLot. */
    private void requireDelayReasonIfRedOrCritical(Contract contract, String delayReasonCode) {
        TargetContext context = new TargetContext(null,
                contract.getBid().getOrganization() == null ? null : contract.getBid().getOrganization().getSector(), null, null);
        Instant deliveryDeadline = contract.getDeliveryDeadline() == null ? null
                : contract.getDeliveryDeadline().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Map<String, Instant> deadlineFields = IndicatorService.deadlineField("deliveryDeadline", deliveryDeadline);
        IndicatorService.IndicatorResult indicator = indicatorService.compute(
                EntityType.CONTRACT, contract.getId(), "status", contract.getStatus().name(), contract.getCreatedAt(), context, deadlineFields);
        if (indicator.isRedOrCritical() && (delayReasonCode == null || delayReasonCode.isBlank())) {
            throw new IllegalArgumentException("A delay reason is required when moving a contract out of a stage that is red or critical");
        }
    }
}
