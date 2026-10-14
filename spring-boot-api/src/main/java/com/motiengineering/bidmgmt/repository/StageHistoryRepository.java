package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.StageHistoryEntry;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StageHistoryRepository extends JpaRepository<StageHistoryEntry, UUID> {
    List<StageHistoryEntry> findByEntityTypeAndEntityIdOrderByChangedAt(EntityType entityType, UUID entityId);

    List<StageHistoryEntry> findByEntityTypeAndEntityIdAndStageFieldOrderByChangedAt(EntityType entityType, UUID entityId, String stageField);

    boolean existsByEntityTypeAndEntityId(EntityType entityType, UUID entityId);
}
