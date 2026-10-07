package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.Opportunity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {
}
