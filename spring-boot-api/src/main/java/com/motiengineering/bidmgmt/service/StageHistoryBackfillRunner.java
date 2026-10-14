package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.BidStatusHistory;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.OpportunityStage;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.BidStatusHistoryRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.OpportunityRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One-time (idempotent - re-runs every startup but skips anything that
 * already has stage_history rows) backfill of stage clocks for data that
 * existed before stage tracking shipped:
 *  - Bid/Lot: replayed in full from bid_status_history, which already
 *    captured every real transition with its real timestamp.
 *  - Opportunity/DealRegistration: no such history exists. A record still
 *    sitting in its very first value (LEAD / DRAFT) gets one synthetic row
 *    at its createdAt, since that lower bound is actually correct. A
 *    record already past its first value has no reliable "entered current
 *    stage" timestamp, so it is deliberately left with NO row - the
 *    clock then reports "unknown" rather than a fabricated zero, per the
 *    brief's backfill requirement.
 */
@Component
@RequiredArgsConstructor
@Order
public class StageHistoryBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StageHistoryBackfillRunner.class);

    private final BidRepository bidRepository;
    private final BidStatusHistoryRepository bidStatusHistoryRepository;
    private final OpportunityRepository opportunityRepository;
    private final DealRegistrationRepository dealRegistrationRepository;
    private final StageHistoryService stageHistoryService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int bids = backfillBidsAndLots();
        int opportunities = backfillOpportunities();
        int dealRegistrations = backfillDealRegistrations();
        log.info("Stage-history backfill: {} bid/lot entities, {} opportunities, {} deal registrations seeded this run", bids, opportunities, dealRegistrations);
    }

    private int backfillBidsAndLots() {
        int seeded = 0;
        for (Bid bid : bidRepository.findAll()) {
            if (!stageHistoryService.hasHistory(EntityType.BID, bid.getId())) {
                replayOrSeedInitial(EntityType.BID, bid.getId(), bid.getCreatedAt(),
                        bidStatusHistoryRepository.findByBid_IdOrderByChangedAt(bid.getId()));
                seeded++;
            }
            for (BidLot lot : bid.getLots()) {
                if (!stageHistoryService.hasHistory(EntityType.LOT, lot.getId())) {
                    replayOrSeedInitial(EntityType.LOT, lot.getId(), lot.getCreatedAt(),
                            bidStatusHistoryRepository.findByLot_IdOrderByChangedAt(lot.getId()));
                    seeded++;
                }
            }
        }
        return seeded;
    }

    /** Every Bid/Lot starts at IDENTIFIED by construction (BidService never calls BidStatusService for the initial save) - that first entry is always known, regardless of how much real history follows it. */
    private void replayOrSeedInitial(EntityType entityType, UUID id, Instant createdAt, List<BidStatusHistory> rows) {
        stageHistoryService.recordBackfilled(entityType, id, "status", null, BidStatus.IDENTIFIED.name(), null, createdAt);
        for (BidStatusHistory row : rows) {
            stageHistoryService.recordBackfilled(entityType, id, "status",
                    row.getFromStatus() == null ? null : row.getFromStatus().name(), row.getToStatus().name(),
                    row.getChangedBy(), row.getChangedAt());
        }
    }

    private int backfillOpportunities() {
        int seeded = 0;
        for (Opportunity opportunity : opportunityRepository.findAll()) {
            if (stageHistoryService.hasHistory(EntityType.OPPORTUNITY, opportunity.getId())) {
                continue;
            }
            if (opportunity.getStage() == OpportunityStage.LEAD) {
                stageHistoryService.recordBackfilled(EntityType.OPPORTUNITY, opportunity.getId(), "stage", null, OpportunityStage.LEAD.name(), opportunity.getCreatedBy(), opportunity.getCreatedAt());
                seeded++;
            }
        }
        return seeded;
    }

    private int backfillDealRegistrations() {
        int seeded = 0;
        for (DealRegistration reg : dealRegistrationRepository.findAll()) {
            if (stageHistoryService.hasHistory(EntityType.DEAL_REGISTRATION, reg.getId())) {
                continue;
            }
            if (reg.getStatus() == DealRegistrationStatus.DRAFT) {
                stageHistoryService.recordBackfilled(EntityType.DEAL_REGISTRATION, reg.getId(), "status", null, DealRegistrationStatus.DRAFT.name(), reg.getCreatedBy(), reg.getCreatedAt());
                seeded++;
            }
        }
        return seeded;
    }
}
