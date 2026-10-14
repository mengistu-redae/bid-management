package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.StageMilestone;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StageMilestoneRepository extends JpaRepository<StageMilestone, UUID> {
    List<StageMilestone> findByEntityTypeAndEntityIdOrderBySortOrder(EntityType entityType, UUID entityId);
}
