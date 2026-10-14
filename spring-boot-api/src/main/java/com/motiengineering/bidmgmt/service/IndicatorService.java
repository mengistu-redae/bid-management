package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.ActivityLogEntry;
import com.motiengineering.bidmgmt.domain.IndicatorConfig;
import com.motiengineering.bidmgmt.domain.StageHistoryEntry;
import com.motiengineering.bidmgmt.domain.StageTarget;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.IndicatorLevel;
import com.motiengineering.bidmgmt.domain.enums.TargetType;
import com.motiengineering.bidmgmt.repository.ActivityLogRepository;
import com.motiengineering.bidmgmt.repository.IndicatorConfigRepository;
import com.motiengineering.bidmgmt.repository.StageTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves "what does late mean" for one entity's current stage
 * (stage_targets, most-specific-override-wins) and turns elapsed time
 * (StageClockService) into a GREEN/AMBER/RED/CRITICAL level against the
 * global thresholds in indicator_config. Also flags staleness
 * independently of targets (no activity_log/stage_history entry in
 * stale_after_days).
 */
@Service
@RequiredArgsConstructor
public class IndicatorService {

    private final StageTargetRepository stageTargetRepository;
    private final IndicatorConfigRepository indicatorConfigRepository;
    private final ActivityLogRepository activityLogRepository;
    private final StageHistoryService stageHistoryService;
    private final StageClockService stageClockService;

    public record IndicatorResult(IndicatorLevel level, Double percentUsed, Double daysUsed, Double targetDays, boolean usesBusinessDays, boolean stale) {

        public boolean isRedOrCritical() {
            return level == IndicatorLevel.RED || level == IndicatorLevel.CRITICAL;
        }
    }

    /** A single-entry deadline-field map, or empty when the value is unknown - never pass a sentinel like Instant.MAX, it overflows date arithmetic on DEADLINE_RELATIVE targets. */
    public static Map<String, Instant> deadlineField(String name, Instant value) {
        return value == null ? Map.of() : Map.of(name, value);
    }

    public IndicatorResult compute(EntityType entityType, UUID entityId, String stageField, String stageValue,
                                    Instant fallbackEnteredAt, TargetContext context, Map<String, Instant> deadlineFieldValues) {
        Optional<StageTarget> targetOpt = resolveTarget(entityType, stageValue, context);
        boolean stale = isStale(entityType, entityId, targetOpt.map(StageTarget::getStaleAfterDays).orElse(null));

        if (targetOpt.isEmpty()) {
            return new IndicatorResult(IndicatorLevel.NO_TARGET, null, null, null, true, stale);
        }
        StageTarget target = targetOpt.get();

        Instant enteredAt = stageHistoryService.currentStageEnteredAt(entityType, entityId, stageField).orElse(fallbackEnteredAt);
        if (enteredAt == null) {
            return new IndicatorResult(IndicatorLevel.UNKNOWN, null, null, null, target.isUsesBusinessDays(), stale);
        }

        Instant now = Instant.now();
        StageClockService.Elapsed elapsedSoFar = stageClockService.elapsedBetween(entityType, entityId, enteredAt, now);
        double daysUsed = target.isUsesBusinessDays() ? elapsedSoFar.businessDays() : calendarDays(elapsedSoFar);

        double percentUsed;
        Double targetDaysValue;
        if (target.getTargetType() == TargetType.DURATION) {
            targetDaysValue = target.getTargetDays() == null ? null : target.getTargetDays().doubleValue();
            if (targetDaysValue == null || targetDaysValue <= 0) {
                return new IndicatorResult(IndicatorLevel.UNKNOWN, null, daysUsed, targetDaysValue, target.isUsesBusinessDays(), stale);
            }
            percentUsed = (daysUsed / targetDaysValue) * 100.0;
        } else {
            Instant deadline = deadlineFieldValues == null ? null : deadlineFieldValues.get(target.getDeadlineField());
            if (deadline == null) {
                return new IndicatorResult(IndicatorLevel.UNKNOWN, null, daysUsed, null, target.isUsesBusinessDays(), stale);
            }
            long offsetDays = target.getDeadlineOffsetDays() == null ? 0 : target.getDeadlineOffsetDays().longValue();
            Instant deadlineWithOffset = deadline.minus(offsetDays, ChronoUnit.DAYS);
            if (!deadlineWithOffset.isAfter(enteredAt)) {
                // Already overdue at the moment the stage was entered - unambiguously critical.
                percentUsed = 999;
                targetDaysValue = 0.0;
            } else {
                StageClockService.Elapsed window = stageClockService.elapsedBetween(entityType, entityId, enteredAt, deadlineWithOffset);
                double windowDays = target.isUsesBusinessDays() ? window.businessDays() : calendarDays(window);
                targetDaysValue = windowDays;
                percentUsed = windowDays <= 0 ? 999 : (daysUsed / windowDays) * 100.0;
            }
        }

        return new IndicatorResult(levelFor(percentUsed), percentUsed, daysUsed, targetDaysValue, target.isUsesBusinessDays(), stale);
    }

    private double calendarDays(StageClockService.Elapsed elapsed) {
        return elapsed.calendar().toMinutes() / (60.0 * 24.0);
    }

    /** Exposed for DelayAnalyticsService, which needs just the resolved target (not a full elapsed-time computation) for historical/completed stage segments. */
    public Optional<StageTarget> resolveTarget(EntityType entityType, String stageValue, TargetContext context) {
        List<StageTarget> candidates = stageTargetRepository.findByEntityTypeAndStageValueAndActiveTrue(entityType, stageValue);
        StageTarget best = null;
        int bestScore = -1;
        for (StageTarget candidate : candidates) {
            Integer score = matchScore(candidate, context);
            if (score != null && score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Null means this target's override doesn't apply to this instance; otherwise the number of overrides it matched (more specific wins). */
    private Integer matchScore(StageTarget target, TargetContext context) {
        int score = 0;
        if (target.getDivision() != null) {
            if (context.divisionId() == null || !target.getDivision().getId().equals(context.divisionId())) {
                return null;
            }
            score++;
        }
        if (target.getSector() != null) {
            if (context.sector() == null || target.getSector() != context.sector()) {
                return null;
            }
            score++;
        }
        if (target.getOemName() != null) {
            if (context.oemName() == null || !target.getOemName().equalsIgnoreCase(context.oemName())) {
                return null;
            }
            score++;
        }
        if (target.getShipmentMode() != null) {
            if (context.shipmentMode() == null || target.getShipmentMode() != context.shipmentMode()) {
                return null;
            }
            score++;
        }
        return score;
    }

    private IndicatorLevel levelFor(double percentUsed) {
        IndicatorConfig config = indicatorConfig();
        if (percentUsed >= config.getCriticalThresholdPercent().doubleValue()) {
            return IndicatorLevel.CRITICAL;
        }
        if (percentUsed >= config.getRedThresholdPercent().doubleValue()) {
            return IndicatorLevel.RED;
        }
        if (percentUsed >= config.getAmberThresholdPercent().doubleValue()) {
            return IndicatorLevel.AMBER;
        }
        return IndicatorLevel.GREEN;
    }

    public boolean isStale(EntityType entityType, UUID entityId, BigDecimal staleAfterDaysOverride) {
        Instant lastActivity = latestActivityInstant(entityType, entityId);
        if (lastActivity == null) {
            return false;
        }
        double staleDays = staleAfterDaysOverride != null ? staleAfterDaysOverride.doubleValue() : indicatorConfig().getDefaultStaleAfterDays().doubleValue();
        long businessDaysSince = stageClockService.elapsedBetween(entityType, entityId, lastActivity, Instant.now()).businessDays();
        return businessDaysSince >= staleDays;
    }

    private Instant latestActivityInstant(EntityType entityType, UUID entityId) {
        Instant latest = null;
        List<StageHistoryEntry> history = stageHistoryService.history(entityType, entityId);
        if (!history.isEmpty()) {
            latest = history.get(history.size() - 1).getChangedAt();
        }
        List<ActivityLogEntry> activity = activityLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
        if (!activity.isEmpty()) {
            Instant fromActivity = activity.get(0).getCreatedAt();
            if (latest == null || fromActivity.isAfter(latest)) {
                latest = fromActivity;
            }
        }
        return latest;
    }

    public IndicatorConfig indicatorConfig() {
        return indicatorConfigRepository.findById(1).orElseGet(this::defaultConfig);
    }

    private IndicatorConfig defaultConfig() {
        IndicatorConfig config = new IndicatorConfig();
        config.setAmberThresholdPercent(BigDecimal.valueOf(75));
        config.setRedThresholdPercent(BigDecimal.valueOf(100));
        config.setCriticalThresholdPercent(BigDecimal.valueOf(150));
        config.setDefaultStaleAfterDays(BigDecimal.valueOf(5));
        return config;
    }
}
