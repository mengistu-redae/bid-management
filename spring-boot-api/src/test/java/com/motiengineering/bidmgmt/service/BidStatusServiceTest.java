package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.BidStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers the bid status lifecycle from the brief, including the lot-cascade and dropped/cancelled-reason rules. */
class BidStatusServiceTest {

    private BidRepository bidRepository;
    private BidStatusHistoryRepository historyRepository;
    private BidStatusService service;

    @BeforeEach
    void setUp() {
        bidRepository = mock(BidRepository.class);
        historyRepository = mock(BidStatusHistoryRepository.class);
        when(bidRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new BidStatusService(bidRepository, historyRepository);
    }

    private Bid bidAt(BidStatus status) {
        Bid bid = new Bid();
        bid.setStatus(status);
        bid.setLots(new ArrayList<>());
        return bid;
    }

    @Test
    void walksTheHappyPathForward() {
        Bid bid = bidAt(BidStatus.IDENTIFIED);
        service.transitionBid(bid, BidStatus.UNDER_REVIEW, null, null);
        service.transitionBid(bid, BidStatus.PREPARING, null, null);
        service.transitionBid(bid, BidStatus.SUBMITTED, null, null);
        service.transitionBid(bid, BidStatus.OPENED, null, null);
        service.transitionBid(bid, BidStatus.UNDER_EVALUATION, null, null);
        service.transitionBid(bid, BidStatus.WON, null, null);
        assertThat(bid.getStatus()).isEqualTo(BidStatus.WON);
    }

    @Test
    void rejectsSkippingStages() {
        Bid bid = bidAt(BidStatus.IDENTIFIED);
        assertThatThrownBy(() -> service.transitionBid(bid, BidStatus.SUBMITTED, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = BidStatus.class, names = {"WON", "LOST", "DROPPED", "CANCELLED"})
    void terminalStatusesAcceptNoFurtherTransitions(BidStatus terminal) {
        Bid bid = bidAt(terminal);
        for (BidStatus candidate : EnumSet.allOf(BidStatus.class)) {
            assertThatThrownBy(() -> service.transitionBid(bid, candidate, "reason", null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void droppingRequiresAReason() {
        Bid bid = bidAt(BidStatus.PREPARING);
        assertThatThrownBy(() -> service.transitionBid(bid, BidStatus.DROPPED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transitionBid(bid, BidStatus.DROPPED, "  ", null))
                .isInstanceOf(IllegalArgumentException.class);

        service.transitionBid(bid, BidStatus.DROPPED, "customer delayed indefinitely", null);
        assertThat(bid.getExitReason()).isEqualTo("customer delayed indefinitely");
    }

    @Test
    void cancellingRequiresAReasonTooAtAnyNonTerminalStage() {
        Bid bid = bidAt(BidStatus.SUBMITTED);
        assertThatThrownBy(() -> service.transitionBid(bid, BidStatus.CANCELLED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bidLevelTransitionCascadesOnlyToLotsStillAtTheOldStatus() {
        Bid bid = bidAt(BidStatus.PREPARING);
        BidLot stillPreparing = new BidLot();
        stillPreparing.setStatus(BidStatus.PREPARING);
        BidLot alreadyDropped = new BidLot();
        alreadyDropped.setStatus(BidStatus.DROPPED);
        alreadyDropped.setExitReason("customer cancelled this lot only");
        bid.getLots().add(stillPreparing);
        bid.getLots().add(alreadyDropped);

        service.transitionBid(bid, BidStatus.SUBMITTED, null, null);

        assertThat(stillPreparing.getStatus()).isEqualTo(BidStatus.SUBMITTED);
        assertThat(alreadyDropped.getStatus()).isEqualTo(BidStatus.DROPPED); // untouched - already diverged
    }

    @Test
    void aSingleLotCanDivergeFromItsSiblingsIndependently() {
        BidLot lot = new BidLot();
        lot.setStatus(BidStatus.PREPARING);

        service.transitionLot(lot, BidStatus.DROPPED, "this lot's OEM withdrew support", null);

        assertThat(lot.getStatus()).isEqualTo(BidStatus.DROPPED);
        assertThat(lot.getExitReason()).isEqualTo("this lot's OEM withdrew support");
    }
}
