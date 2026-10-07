package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.BidStatusHistory;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.BidStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the bid status lifecycle from the brief: Identified -&gt; Under
 * Review (go/no-go) -&gt; Preparing -&gt; Submitted -&gt; Opened -&gt; Under
 * Evaluation -&gt; Won/Lost, with Dropped (our call) and Cancelled (the
 * customer's call) as side exits. A bid-level transition cascades to every
 * lot still sitting at the bid's old status (i.e. hasn't already diverged)
 * - see BidLot's own javadoc for why a lot can diverge in the first place.
 */
@Service
@RequiredArgsConstructor
public class BidStatusService {

    private static final Map<BidStatus, Set<BidStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(BidStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(BidStatus.IDENTIFIED, EnumSet.of(BidStatus.UNDER_REVIEW, BidStatus.DROPPED, BidStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BidStatus.UNDER_REVIEW, EnumSet.of(BidStatus.PREPARING, BidStatus.DROPPED, BidStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BidStatus.PREPARING, EnumSet.of(BidStatus.SUBMITTED, BidStatus.DROPPED, BidStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BidStatus.SUBMITTED, EnumSet.of(BidStatus.OPENED, BidStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BidStatus.OPENED, EnumSet.of(BidStatus.UNDER_EVALUATION, BidStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BidStatus.UNDER_EVALUATION, EnumSet.of(BidStatus.WON, BidStatus.LOST, BidStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BidStatus.WON, EnumSet.noneOf(BidStatus.class));
        ALLOWED_TRANSITIONS.put(BidStatus.LOST, EnumSet.noneOf(BidStatus.class));
        ALLOWED_TRANSITIONS.put(BidStatus.DROPPED, EnumSet.noneOf(BidStatus.class));
        ALLOWED_TRANSITIONS.put(BidStatus.CANCELLED, EnumSet.noneOf(BidStatus.class));
    }

    private final BidRepository bidRepository;
    private final BidStatusHistoryRepository historyRepository;

    public static boolean isAllowed(BidStatus from, BidStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    @Transactional
    public Bid transitionBid(Bid bid, BidStatus newStatus, String reason, AppUser actingUser) {
        BidStatus oldStatus = bid.getStatus();
        if (!isAllowed(oldStatus, newStatus)) {
            throw new IllegalStateException("Cannot move a bid from " + oldStatus + " to " + newStatus);
        }
        requireReasonForExit(newStatus, reason);

        bid.setStatus(newStatus);
        if (newStatus == BidStatus.DROPPED || newStatus == BidStatus.CANCELLED) {
            bid.setExitReason(reason);
        }
        recordHistory(bid, null, oldStatus, newStatus, reason, actingUser);

        for (BidLot lot : bid.getLots()) {
            if (lot.getStatus() == oldStatus) {
                lot.setStatus(newStatus);
                if (newStatus == BidStatus.DROPPED || newStatus == BidStatus.CANCELLED) {
                    lot.setExitReason(reason);
                }
                recordHistory(null, lot, oldStatus, newStatus, reason, actingUser);
            }
        }
        return bidRepository.save(bid);
    }

    /** A single lot diverging from its siblings (e.g. one lot dropped while the bid keeps preparing on the others). */
    @Transactional
    public BidLot transitionLot(BidLot lot, BidStatus newStatus, String reason, AppUser actingUser) {
        BidStatus oldStatus = lot.getStatus();
        if (!isAllowed(oldStatus, newStatus)) {
            throw new IllegalStateException("Cannot move a lot from " + oldStatus + " to " + newStatus);
        }
        requireReasonForExit(newStatus, reason);

        lot.setStatus(newStatus);
        if (newStatus == BidStatus.DROPPED || newStatus == BidStatus.CANCELLED) {
            lot.setExitReason(reason);
        }
        recordHistory(null, lot, oldStatus, newStatus, reason, actingUser);
        return lot;
    }

    private void requireReasonForExit(BidStatus newStatus, String reason) {
        if ((newStatus == BidStatus.DROPPED || newStatus == BidStatus.CANCELLED) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException(newStatus + " requires a reason");
        }
    }

    private void recordHistory(Bid bid, BidLot lot, BidStatus from, BidStatus to, String reason, AppUser actingUser) {
        BidStatusHistory history = new BidStatusHistory();
        history.setBid(bid);
        history.setLot(lot);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setReason(reason);
        history.setChangedBy(actingUser);
        historyRepository.save(history);
    }
}
