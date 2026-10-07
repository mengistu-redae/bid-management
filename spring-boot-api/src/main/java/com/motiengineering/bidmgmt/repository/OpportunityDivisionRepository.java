package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.OpportunityDivision;
import com.motiengineering.bidmgmt.domain.OpportunityDivisionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OpportunityDivisionRepository extends JpaRepository<OpportunityDivision, OpportunityDivisionId> {
    List<OpportunityDivision> findById_OpportunityId(UUID opportunityId);

    void deleteById_OpportunityId(UUID opportunityId);
}
