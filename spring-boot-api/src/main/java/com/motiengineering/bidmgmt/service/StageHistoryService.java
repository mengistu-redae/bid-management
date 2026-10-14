package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.StageHistoryEntry;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.repository.StageHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StageHistoryService {

    private final StageHistoryRepository stageHistoryRepository;

    @Transactional
    public StageHistoryEntry record(EntityType entityType, UUID entityId, String stageField, String fromValue, String toValue, String note, AppUser changedBy) {
        return record(entityType, entityId, stageField, fromValue, toValue, note, null, changedBy);
    }

    @Transactional
    public StageHistoryEntry record(EntityType entityType, UUID entityId, String stageField, String fromValue, String toValue, String note, String delayReasonCode, AppUser changedBy) {
        return stageHistoryRepository.save(new StageHistoryEntry(entityType, entityId, stageField, fromValue, toValue, note, delayReasonCode, changedBy, Instant.now(), false));
    }

    /** Used only by the one-time backfill, so a historical changedAt can be set instead of "now". */
    @Transactional
    public StageHistoryEntry recordBackfilled(EntityType entityType, UUID entityId, String stageField, String fromValue, String toValue, AppUser changedBy, Instant changedAt) {
        return stageHistoryRepository.save(new StageHistoryEntry(entityType, entityId, stageField, fromValue, toValue, null, changedBy, changedAt, true));
    }

    public List<StageHistoryEntry> history(EntityType entityType, UUID entityId) {
        return stageHistoryRepository.findByEntityTypeAndEntityIdOrderByChangedAt(entityType, entityId);
    }

    public List<StageHistoryEntry> historyForField(EntityType entityType, UUID entityId, String stageField) {
        return stageHistoryRepository.findByEntityTypeAndEntityIdAndStageFieldOrderByChangedAt(entityType, entityId, stageField);
    }

    /** The timestamp the entity entered its current value of stageField, i.e. the toValue of the latest row - empty if no history exists yet (unknown, not zero). */
    public Optional<Instant> currentStageEnteredAt(EntityType entityType, UUID entityId, String stageField) {
        List<StageHistoryEntry> entries = historyForField(entityType, entityId, stageField);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(entries.size() - 1).getChangedAt());
    }

    public boolean hasHistory(EntityType entityType, UUID entityId) {
        return stageHistoryRepository.existsByEntityTypeAndEntityId(entityType, entityId);
    }
}
