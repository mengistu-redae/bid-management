package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID>, JpaSpecificationExecutor<Bid> {

    List<Bid> findByOrganization_IdAndReferenceNumberIgnoreCase(UUID organizationId, String referenceNumber);

    List<Bid> findByOrganization_Id(UUID organizationId);
}
