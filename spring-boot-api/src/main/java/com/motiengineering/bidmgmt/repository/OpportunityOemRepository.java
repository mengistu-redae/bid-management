package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.OpportunityOem;
import com.motiengineering.bidmgmt.domain.OpportunityOemId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OpportunityOemRepository extends JpaRepository<OpportunityOem, OpportunityOemId> {
    List<OpportunityOem> findById_OpportunityId(UUID opportunityId);

    void deleteById_OpportunityId(UUID opportunityId);
}
