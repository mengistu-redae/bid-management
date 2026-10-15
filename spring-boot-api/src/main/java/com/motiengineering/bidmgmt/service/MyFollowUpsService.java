package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.FollowUpState;
import com.motiengineering.bidmgmt.domain.LogisticsShipment;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.DelayBoardRowDto;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.ContractRepository;
import com.motiengineering.bidmgmt.repository.LogisticsShipmentRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** The per-user view backing the "My Follow-ups" page - same underlying computation as DelaysBoardService, filtered to one person. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyFollowUpsService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");
    private static final BigDecimal AMBER_SOON_BUFFER_PERCENT = BigDecimal.valueOf(15);

    private final BidRepository bidRepository;
    private final ContractRepository contractRepository;
    private final LogisticsShipmentRepository logisticsShipmentRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final DelaysBoardService delaysBoardService;
    private final FollowUpService followUpService;
    private final IndicatorService indicatorService;

    public record MyFollowUpsDto(List<DelayBoardRowDto> overdue, List<DelayBoardRowDto> dueToday,
                                  List<DelayBoardRowDto> turningAmberSoon, List<DelayBoardRowDto> stale) {
    }

    public MyFollowUpsDto build(AppUser user) {
        LocalDate today = LocalDate.now(ZONE);
        BigDecimal amberThreshold = indicatorService.indicatorConfig().getAmberThresholdPercent();
        BigDecimal soonFloor = amberThreshold.subtract(AMBER_SOON_BUFFER_PERCENT);

        List<DelayBoardRowDto> overdue = new ArrayList<>();
        List<DelayBoardRowDto> dueToday = new ArrayList<>();
        List<DelayBoardRowDto> turningAmberSoon = new ArrayList<>();
        List<DelayBoardRowDto> stale = new ArrayList<>();

        for (Bid bid : bidRepository.findAll()) {
            for (BidLot lot : bid.getLots()) {
                if (lot.getStatus() == null || lot.getStatus().isTerminal()) {
                    continue;
                }
                boolean isAssignedOfficer = lotAccountOfficerRepository.findById_LotId(lot.getId()).stream()
                        .anyMatch(loa -> loa.getId().getUserId().equals(user.getId()));
                FollowUpState followUp = followUpService.get(EntityType.LOT, lot.getId()).orElse(null);
                boolean isFollowUpOwner = followUp != null && followUp.getFollowUpOwner() != null && followUp.getFollowUpOwner().getId().equals(user.getId());
                if (!isAssignedOfficer && !isFollowUpOwner) {
                    continue;
                }

                DelayBoardRowDto row = delaysBoardService.computeRow(bid, lot);
                if (row == null) {
                    continue;
                }
                classify(row, isAssignedOfficer, isFollowUpOwner, followUp, today, soonFloor, overdue, dueToday, turningAmberSoon, stale);
            }
        }

        // Contract/Logistics have no account-officer concept (no equivalent of lot_account_officers
        // exists for them yet) - the follow-up owner is the sole stand-in for "the responsible person"
        // on these two, so it gates both the date-based buckets AND the proactive turning-amber-soon/stale ones.
        for (Contract contract : contractRepository.findAll()) {
            if (contract.getStatus() == null || contract.getStatus().isTerminal()) {
                continue;
            }
            FollowUpState followUp = followUpService.get(EntityType.CONTRACT, contract.getId()).orElse(null);
            boolean isFollowUpOwner = followUp != null && followUp.getFollowUpOwner() != null && followUp.getFollowUpOwner().getId().equals(user.getId());
            if (!isFollowUpOwner) {
                continue;
            }
            DelayBoardRowDto row = delaysBoardService.computeContractRow(contract);
            if (row == null) {
                continue;
            }
            classify(row, isFollowUpOwner, isFollowUpOwner, followUp, today, soonFloor, overdue, dueToday, turningAmberSoon, stale);
        }
        for (LogisticsShipment shipment : logisticsShipmentRepository.findAll()) {
            if (shipment.getStatus() == null || shipment.getStatus().isTerminal()) {
                continue;
            }
            FollowUpState followUp = followUpService.get(EntityType.LOGISTICS, shipment.getId()).orElse(null);
            boolean isFollowUpOwner = followUp != null && followUp.getFollowUpOwner() != null && followUp.getFollowUpOwner().getId().equals(user.getId());
            if (!isFollowUpOwner) {
                continue;
            }
            DelayBoardRowDto row = delaysBoardService.computeLogisticsRow(shipment);
            if (row == null) {
                continue;
            }
            classify(row, isFollowUpOwner, isFollowUpOwner, followUp, today, soonFloor, overdue, dueToday, turningAmberSoon, stale);
        }

        return new MyFollowUpsDto(overdue, dueToday, turningAmberSoon, stale);
    }

    /** isProactivelyOwned gates turning-amber-soon/stale - the assigned officer for a LOT, the follow-up owner for everything else. */
    private void classify(DelayBoardRowDto row, boolean isProactivelyOwned, boolean isFollowUpOwner, FollowUpState followUp,
                           LocalDate today, BigDecimal soonFloor,
                           List<DelayBoardRowDto> overdue, List<DelayBoardRowDto> dueToday,
                           List<DelayBoardRowDto> turningAmberSoon, List<DelayBoardRowDto> stale) {
        if (isFollowUpOwner && followUp.getNextFollowUpDate() != null && !followUp.isSnoozed(today)) {
            if (followUp.getNextFollowUpDate().isBefore(today)) {
                overdue.add(row);
            } else if (followUp.getNextFollowUpDate().isEqual(today)) {
                dueToday.add(row);
            }
        }
        if (isProactivelyOwned) {
            if ("GREEN".equals(row.indicatorLevel()) && row.percentUsed() != null
                    && BigDecimal.valueOf(row.percentUsed()).compareTo(soonFloor) >= 0) {
                turningAmberSoon.add(row);
            }
            if (row.stale()) {
                stale.add(row);
            }
        }
    }
}
