package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.ActivityLogEntry;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLogEntry, UUID> {
    List<ActivityLogEntry> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(EntityType entityType, UUID entityId);

    /** For the Director's daily digest's "what changed yesterday" section. */
    List<ActivityLogEntry> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
}
