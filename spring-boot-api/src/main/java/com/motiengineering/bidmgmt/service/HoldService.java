package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.HoldPeriod;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.repository.HoldPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puts a tracked entity "on hold" (clock pauses, reported separately) and
 * ends it again. Also logs a stage_history row at each transition (field
 * "hold") so the unified timeline shows hold periods alongside status
 * changes, even though hold_periods itself is the source of truth for the
 * actual pause math (StageClockService reads holdWindowsFor()).
 */
@Service
@RequiredArgsConstructor
public class HoldService {

    private final HoldPeriodRepository holdPeriodRepository;
    private final StageHistoryService stageHistoryService;

    public Optional<HoldPeriod> activeHold(EntityType entityType, UUID entityId) {
        return holdPeriodRepository.findByEntityTypeAndEntityIdAndEndedAtIsNull(entityType, entityId);
    }

    public boolean isOnHold(EntityType entityType, UUID entityId) {
        return activeHold(entityType, entityId).isPresent();
    }

    @Transactional
    public HoldPeriod start(EntityType entityType, UUID entityId, String reason, AppUser actingUser) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to put a record on hold");
        }
        if (isOnHold(entityType, entityId)) {
            throw new IllegalStateException("Already on hold");
        }
        HoldPeriod hold = new HoldPeriod();
        hold.setEntityType(entityType);
        hold.setEntityId(entityId);
        hold.setStartedAt(Instant.now());
        hold.setReason(reason);
        hold.setStartedBy(actingUser);
        hold = holdPeriodRepository.save(hold);
        stageHistoryService.record(entityType, entityId, "hold", null, reason, null, actingUser);
        return hold;
    }

    @Transactional
    public HoldPeriod end(EntityType entityType, UUID entityId, AppUser actingUser) {
        HoldPeriod hold = activeHold(entityType, entityId)
                .orElseThrow(() -> new IllegalStateException("Not currently on hold"));
        hold.setEndedAt(Instant.now());
        hold.setEndedBy(actingUser);
        hold = holdPeriodRepository.save(hold);
        stageHistoryService.record(entityType, entityId, "hold", hold.getReason(), null, null, actingUser);
        return hold;
    }

    /** [start, end) instant pairs for every hold (closed or still open, open ends at "now") overlapping this entity's lifetime. */
    public List<Instant[]> holdWindowsFor(EntityType entityType, UUID entityId) {
        List<Instant[]> windows = new ArrayList<>();
        for (HoldPeriod hold : holdPeriodRepository.findByEntityTypeAndEntityIdOrderByStartedAt(entityType, entityId)) {
            Instant end = hold.getEndedAt() != null ? hold.getEndedAt() : Instant.now();
            windows.add(new Instant[]{hold.getStartedAt(), end});
        }
        return windows;
    }

    /** Total time this entity has spent on hold, clipped to [from, to]. */
    public java.time.Duration totalHoldDuration(EntityType entityType, UUID entityId, Instant from, Instant to) {
        java.time.Duration total = java.time.Duration.ZERO;
        for (Instant[] window : holdWindowsFor(entityType, entityId)) {
            Instant start = window[0].isBefore(from) ? from : window[0];
            Instant end = window[1].isAfter(to) ? to : window[1];
            if (end.isAfter(start)) {
                total = total.plus(java.time.Duration.between(start, end));
            }
        }
        return total;
    }
}
