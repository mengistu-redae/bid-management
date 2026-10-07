package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.BidScout;
import com.motiengineering.bidmgmt.domain.BidScoutId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BidScoutRepository extends JpaRepository<BidScout, BidScoutId> {
    List<BidScout> findById_BidId(UUID bidId);

    void deleteById_BidId(UUID bidId);
}
