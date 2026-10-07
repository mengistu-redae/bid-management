package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.BidLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface BidLotRepository extends JpaRepository<BidLot, UUID>, JpaSpecificationExecutor<BidLot> {
    List<BidLot> findByBid_IdOrderByLotLabel(UUID bidId);
}
