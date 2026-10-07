package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.ActivityLogEntry;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLogEntry, UUID> {
    List<ActivityLogEntry> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(EntityType entityType, UUID entityId);
}
