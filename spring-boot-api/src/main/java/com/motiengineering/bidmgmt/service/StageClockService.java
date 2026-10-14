package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.StageClockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Computes "time in current stage" and completed-stage durations, in both
 * calendar and business-day terms, net of any hold time. Reads
 * stage_history for the stage-entry timestamp and hold_periods (via
 * HoldService) for pauses - never stores a computed duration, always
 * live off those two append-only sources.
 */
@Service
@RequiredArgsConstructor
public class StageClockService {

    private final StageHistoryService stageHistoryService;
    private final HoldService holdService;
    private final BusinessDayCalendarService businessDayCalendarService;

    public record Elapsed(Duration calendar, long businessDays, boolean unknown) {
        public static Elapsed unknownResult() {
            return new Elapsed(Duration.ZERO, 0, true);
        }
    }

    /**
     * Elapsed time since this entity's current value of stageField was
     * entered, net of hold time, as of `asOf`. If the entity has no
     * stage_history row for this field at all, `fallbackEnteredAt` is used
     * (typically the entity's own createdAt, for a record still in its
     * very first stage) - if that is also null, the result is unknown.
     */
    public Elapsed timeInCurrentStage(EntityType entityType, UUID entityId, String stageField, Instant fallbackEnteredAt, Instant asOf) {
        Instant enteredAt = stageHistoryService.currentStageEnteredAt(entityType, entityId, stageField).orElse(fallbackEnteredAt);
        if (enteredAt == null) {
            return Elapsed.unknownResult();
        }
        return elapsedBetween(entityType, entityId, enteredAt, asOf);
    }

    public Elapsed elapsedBetween(EntityType entityType, UUID entityId, Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            return new Elapsed(Duration.ZERO, 0, false);
        }
        List<Instant[]> holdWindows = holdService.holdWindowsFor(entityType, entityId);
        Duration holdDuration = holdService.totalHoldDuration(entityType, entityId, from, to);
        Duration calendar = Duration.between(from, to).minus(holdDuration);
        if (calendar.isNegative()) {
            calendar = Duration.ZERO;
        }
        long businessDays = businessDayCalendarService.businessDaysBetween(from, to, holdWindows);
        return new Elapsed(calendar, businessDays, false);
    }

    /** Duration of a completed stage: from its entry row to the next transition on the same field (or null if it's still current). */
    public Optional<Elapsed> completedStageDuration(EntityType entityType, UUID entityId, String stageField, Instant enteredAt, Instant exitedAt) {
        if (enteredAt == null || exitedAt == null) {
            return Optional.empty();
        }
        return Optional.of(elapsedBetween(entityType, entityId, enteredAt, exitedAt));
    }

    public StageClockDto clockFor(EntityType entityType, UUID entityId, String stageField, String currentValue, Instant fallbackEnteredAt) {
        Instant enteredAt = stageHistoryService.currentStageEnteredAt(entityType, entityId, stageField).orElse(fallbackEnteredAt);
        boolean onHold = holdService.isOnHold(entityType, entityId);
        if (enteredAt == null) {
            return new StageClockDto(stageField, currentValue, null, 0, 0, onHold, true);
        }
        Elapsed elapsed = elapsedBetween(entityType, entityId, enteredAt, Instant.now());
        double calendarDays = elapsed.calendar().toMinutes() / (60.0 * 24.0);
        return new StageClockDto(stageField, currentValue, enteredAt, calendarDays, elapsed.businessDays(), onHold, false);
    }
}
