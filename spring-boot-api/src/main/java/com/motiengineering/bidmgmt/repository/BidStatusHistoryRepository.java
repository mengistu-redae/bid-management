package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.BidStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BidStatusHistoryRepository extends JpaRepository<BidStatusHistory, UUID> {
    List<BidStatusHistory> findByBid_IdOrderByChangedAt(UUID bidId);

    List<BidStatusHistory> findByLot_IdOrderByChangedAt(UUID lotId);

    /** For the Director's daily digest's "what changed yesterday" section. */
    List<BidStatusHistory> findByChangedAtBetweenOrderByChangedAt(Instant from, Instant to);
}
