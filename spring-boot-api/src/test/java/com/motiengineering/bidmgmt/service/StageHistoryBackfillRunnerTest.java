package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.OpportunityStage;
import com.motiengineering.bidmgmt.dto.CreateBidRequest;
import com.motiengineering.bidmgmt.dto.CreateOpportunityRequest;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backfill with a mix of real history (Bid - replayed from
 * bid_status_history), a record still in its very first stage (Opportunity
 * at LEAD - safe to seed from createdAt), and a record already past its
 * first stage with nothing to replay (simulated by jumping straight to
 * QUALIFIED without going through update(), which is what a pre-existing
 * row with no stage_history would look like) - that last one must come
 * back as "unknown", never a fabricated zero. See OpportunityServiceLiveTest
 * for why this points at the compose Postgres's bidmgmt_test database.
 */
@SpringBootTest
@Transactional
class StageHistoryBackfillRunnerTest {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5434/bidmgmt_test");
        registry.add("spring.datasource.username", () -> "bidmgmt");
        registry.add("spring.datasource.password", () -> "bidmgmt");
        registry.add("bidmgmt.seed.enabled", () -> "false");
    }

    @Autowired
    private StageHistoryBackfillRunner backfillRunner;
    @Autowired
    private OpportunityService opportunityService;
    @Autowired
    private BidService bidService;
    @Autowired
    private BidStatusService bidStatusService;
    @Autowired
    private StageHistoryService stageHistoryService;
    @Autowired
    private DivisionRepository divisionRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void replaysRealBidHistoryAndLeavesUnknownStagesUnseeded() {
        Division security = divisionRepository.findByCode("SECURITY").orElseThrow();
        var director = appUserRepository.findAll().stream().findFirst().orElseThrow();

        // A Bid whose status actually moved via BidStatusService - bid_status_history has the real timestamps to replay.
        CreateBidRequest bidRequest = new CreateBidRequest(
                null, "Backfill Test Bank", null, "Backfill Test Bid", null, "DIRECT", null, List.of(),
                null, null, null, null, null, null, null);
        Bid bid = bidService.create(bidRequest, director);
        entityManager.flush(); // force the lot's initial INSERT before mutating it again - see BidService.create()'s javadoc on this entity's isNew()/merge() pitfall
        BidLot lot = bid.getLots().get(0);
        bidStatusService.transitionLot(lot, BidStatus.UNDER_REVIEW, null, director);
        entityManager.flush();

        // Wipe the live stage_history rows BidStatusService/BidService just wrote, to simulate data that predates stage tracking.
        stageHistoryService.history(EntityType.LOT, lot.getId()).forEach(e -> entityManager.remove(e));
        stageHistoryService.history(EntityType.BID, bid.getId()).forEach(e -> entityManager.remove(e));
        entityManager.flush();
        assertThat(stageHistoryService.hasHistory(EntityType.LOT, lot.getId())).isFalse();

        // An Opportunity still in its very first stage (LEAD) - createdAt is a safe lower bound.
        CreateOpportunityRequest leadRequest = new CreateOpportunityRequest(
                null, "Backfill Lead Co", "OTHER", "Still a lead", null, null, null,
                List.of(security.getCode()), List.of(), null, null);
        Opportunity stillLead = opportunityService.create(leadRequest, director);

        // An Opportunity already past LEAD with no stage_history - as if it moved before stage tracking existed.
        CreateOpportunityRequest pastLeadRequest = new CreateOpportunityRequest(
                null, "Backfill Qualified Co", "OTHER", "Already qualified", null, null, null,
                List.of(security.getCode()), List.of(), null, null);
        Opportunity pastLead = opportunityService.create(pastLeadRequest, director);
        entityManager.flush(); // force the initial INSERT before mutating it again - see BidService.create()'s javadoc on this entity's isNew()/merge() pitfall
        pastLead.setStage(OpportunityStage.QUALIFIED);
        entityManager.flush();
        stageHistoryService.history(EntityType.OPPORTUNITY, pastLead.getId()).forEach(e -> entityManager.remove(e));
        entityManager.flush();
        assertThat(stageHistoryService.hasHistory(EntityType.OPPORTUNITY, pastLead.getId())).isFalse();

        backfillRunner.run(null);
        entityManager.flush();

        // The Lot's real history round-trips through the backfill.
        var lotHistory = stageHistoryService.historyForField(EntityType.LOT, lot.getId(), "status");
        assertThat(lotHistory).extracting("fromValue", "toValue")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(null, "IDENTIFIED"),
                        org.assertj.core.groups.Tuple.tuple("IDENTIFIED", "UNDER_REVIEW"));
        assertThat(lotHistory).allMatch(e -> e.isBackfilled());

        // Still-LEAD opportunity gets a synthetic row at createdAt.
        assertThat(stageHistoryService.currentStageEnteredAt(EntityType.OPPORTUNITY, stillLead.getId(), "stage")).isPresent();

        // Past-LEAD opportunity with nothing to replay stays unseeded - "unknown", not a fabricated zero.
        assertThat(stageHistoryService.currentStageEnteredAt(EntityType.OPPORTUNITY, pastLead.getId(), "stage")).isEmpty();
    }
}
