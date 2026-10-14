package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.IndicatorConfig;
import com.motiengineering.bidmgmt.domain.StageTarget;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.IndicatorLevel;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.domain.enums.TargetType;
import com.motiengineering.bidmgmt.repository.ActivityLogRepository;
import com.motiengineering.bidmgmt.repository.IndicatorConfigRepository;
import com.motiengineering.bidmgmt.repository.StageTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers threshold crossings (duration and deadline-relative targets) and most-specific-override-wins target resolution. */
class IndicatorServiceTest {

    private StageTargetRepository stageTargetRepository;
    private IndicatorConfigRepository indicatorConfigRepository;
    private StageHistoryService stageHistoryService;
    private StageClockService stageClockService;
    private IndicatorService service;

    private final UUID entityId = UUID.randomUUID();
    private final Instant enteredAt = Instant.parse("2026-01-05T00:00:00Z");

    @BeforeEach
    void setUp() {
        stageTargetRepository = mock(StageTargetRepository.class);
        indicatorConfigRepository = mock(IndicatorConfigRepository.class);
        ActivityLogRepository activityLogRepository = mock(ActivityLogRepository.class);
        stageHistoryService = mock(StageHistoryService.class);
        stageClockService = mock(StageClockService.class);

        IndicatorConfig config = new IndicatorConfig();
        config.setAmberThresholdPercent(BigDecimal.valueOf(75));
        config.setRedThresholdPercent(BigDecimal.valueOf(100));
        config.setCriticalThresholdPercent(BigDecimal.valueOf(150));
        config.setDefaultStaleAfterDays(BigDecimal.valueOf(5));
        when(indicatorConfigRepository.findById(1)).thenReturn(Optional.of(config));
        when(activityLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(stageHistoryService.history(any(), any())).thenReturn(List.of());
        when(stageHistoryService.currentStageEnteredAt(any(), any(), any())).thenReturn(Optional.of(enteredAt));

        service = new IndicatorService(stageTargetRepository, indicatorConfigRepository, activityLogRepository, stageHistoryService, stageClockService);
    }

    private StageTarget durationTarget(double targetDays) {
        StageTarget target = new StageTarget();
        target.setEntityType(EntityType.LOT);
        target.setStageValue("UNDER_REVIEW");
        target.setTargetType(TargetType.DURATION);
        target.setTargetDays(BigDecimal.valueOf(targetDays));
        target.setUsesBusinessDays(true);
        target.setActive(true);
        return target;
    }

    private void stubElapsedBusinessDays(long businessDays) {
        when(stageClockService.elapsedBetween(any(), any(), any(), any()))
                .thenReturn(new StageClockService.Elapsed(Duration.ofHours(businessDays * 8), businessDays, false));
    }

    @Test
    void underSeventyFivePercentIsGreen() {
        when(stageTargetRepository.findByEntityTypeAndStageValueAndActiveTrue(EntityType.LOT, "UNDER_REVIEW"))
                .thenReturn(List.of(durationTarget(2)));
        stubElapsedBusinessDays(1); // 50%

        var result = service.compute(EntityType.LOT, entityId, "status", "UNDER_REVIEW", enteredAt, TargetContext.EMPTY, Map.of());

        assertThat(result.level()).isEqualTo(IndicatorLevel.GREEN);
        assertThat(result.percentUsed()).isEqualTo(50.0);
    }

    @Test
    void atOrOverHundredPercentIsRed() {
        when(stageTargetRepository.findByEntityTypeAndStageValueAndActiveTrue(EntityType.LOT, "UNDER_REVIEW"))
                .thenReturn(List.of(durationTarget(2)));
        stubElapsedBusinessDays(2); // exactly 100%

        var result = service.compute(EntityType.LOT, entityId, "status", "UNDER_REVIEW", enteredAt, TargetContext.EMPTY, Map.of());

        assertThat(result.level()).isEqualTo(IndicatorLevel.RED);
    }

    @Test
    void seventyFiveToHundredPercentIsAmber() {
        when(stageTargetRepository.findByEntityTypeAndStageValueAndActiveTrue(EntityType.LOT, "UNDER_REVIEW"))
                .thenReturn(List.of(durationTarget(10)));
        stubElapsedBusinessDays(8); // 80%

        var result = service.compute(EntityType.LOT, entityId, "status", "UNDER_REVIEW", enteredAt, TargetContext.EMPTY, Map.of());

        assertThat(result.level()).isEqualTo(IndicatorLevel.AMBER);
        assertThat(result.percentUsed()).isEqualTo(80.0);
    }

    @Test
    void overHundredFiftyPercentIsCritical() {
        when(stageTargetRepository.findByEntityTypeAndStageValueAndActiveTrue(EntityType.LOT, "UNDER_REVIEW"))
                .thenReturn(List.of(durationTarget(2)));
        stubElapsedBusinessDays(4); // 200%

        var result = service.compute(EntityType.LOT, entityId, "status", "UNDER_REVIEW", enteredAt, TargetContext.EMPTY, Map.of());

        assertThat(result.level()).isEqualTo(IndicatorLevel.CRITICAL);
        assertThat(result.isRedOrCritical()).isTrue();
    }

    @Test
    void noMatchingTargetReportsNoTarget() {
        when(stageTargetRepository.findByEntityTypeAndStageValueAndActiveTrue(EntityType.LOT, "UNDER_REVIEW")).thenReturn(List.of());

        var result = service.compute(EntityType.LOT, entityId, "status", "UNDER_REVIEW", enteredAt, TargetContext.EMPTY, Map.of());

        assertThat(result.level()).isEqualTo(IndicatorLevel.NO_TARGET);
        assertThat(result.percentUsed()).isNull();
    }

    @Test
    void unknownEnteredAtReportsUnknownNotZero() {
        when(stageTargetRepository.findByEntityTypeAndStageValueAndActiveTrue(EntityType.LOT, "UNDER_REVIEW"))
                .thenReturn(List.of(durationTarget(2)));
        when(stageHistoryService.currentStageEnteredAt(any(), any(), any())).thenReturn(Optional.empty());

        var result = service.compute(EntityType.LOT, entityId, "status", "UNDER_REVIEW", null, TargetContext.EMPTY, Map.of());

        assertThat(result.level()).isEqualTo(IndicatorLevel.UNKNOWN);
        assertThat(result.percentUsed()).isNull();
    }

    @Test
    void mostSpecificOverrideWinsOverTheGlobalDefault() {
        StageTarget global = durationTarget(2);
        StageTarget divisionOverride = durationTarget(10);
        UUID divisionId = UUID.randomUUID();
        com.motiengineering.bidmgmt.domain.Division division = new com.motiengineering.bidmgmt.domain.Division();
        division.setId(divisionId);
        divisionOverride.setDivision(division);
        when(stageTargetRepository.findByEntityTypeAndStageValueAndActiveTrue(EntityType.LOT, "UNDER_REVIEW"))
                .thenReturn(List.of(global, divisionOverride));
        stubElapsedBusinessDays(1); // 50% of 2 (global) vs 10% of 10 (override)

        var matchingDivision = service.compute(EntityType.LOT, entityId, "status", "UNDER_REVIEW", enteredAt,
                TargetContext.ofDivision(divisionId), Map.of());
        assertThat(matchingDivision.targetDays()).isEqualTo(10.0);
        assertThat(matchingDivision.percentUsed()).isEqualTo(10.0);

        var nonMatchingDivision = service.compute(EntityType.LOT, entityId, "status", "UNDER_REVIEW", enteredAt,
                TargetContext.ofDivision(UUID.randomUUID()), Map.of());
        assertThat(nonMatchingDivision.targetDays()).isEqualTo(2.0);
    }

    @Test
    void deadlineRelativeTargetMeasuresProgressTowardTheOffsetDeadline() {
        StageTarget target = new StageTarget();
        target.setEntityType(EntityType.LOT);
        target.setStageValue("PREPARING");
        target.setTargetType(TargetType.DEADLINE_RELATIVE);
        target.setDeadlineField("closingAt");
        target.setDeadlineOffsetDays(BigDecimal.ONE);
        target.setUsesBusinessDays(true);
        target.setActive(true);
        when(stageTargetRepository.findByEntityTypeAndStageValueAndActiveTrue(EntityType.LOT, "PREPARING")).thenReturn(List.of(target));

        Instant closingAt = Instant.parse("2026-01-15T00:00:00Z"); // deadline - 1 day offset = 2026-01-14
        Instant deadlineWithOffset = Instant.parse("2026-01-14T00:00:00Z");
        // window (enteredAt -> deadlineWithOffset) = 8 business days; elapsed so far (enteredAt -> now) = 4 business days -> 50%
        when(stageClockService.elapsedBetween(eq(EntityType.LOT), eq(entityId), eq(enteredAt), any()))
                .thenAnswer(invocation -> {
                    Instant to = invocation.getArgument(3);
                    if (to.equals(deadlineWithOffset)) {
                        return new StageClockService.Elapsed(Duration.ofDays(8), 8, false);
                    }
                    return new StageClockService.Elapsed(Duration.ofDays(4), 4, false);
                });

        var result = service.compute(EntityType.LOT, entityId, "status", "PREPARING", enteredAt, TargetContext.EMPTY,
                Map.of("closingAt", closingAt));

        assertThat(result.level()).isEqualTo(IndicatorLevel.GREEN);
        assertThat(result.percentUsed()).isEqualTo(50.0);
        assertThat(result.targetDays()).isEqualTo(8.0);
    }

    @Test
    void deadlineAlreadyPassedAtStageEntryIsImmediatelyCritical() {
        StageTarget target = new StageTarget();
        target.setEntityType(EntityType.LOT);
        target.setStageValue("PREPARING");
        target.setTargetType(TargetType.DEADLINE_RELATIVE);
        target.setDeadlineField("closingAt");
        target.setDeadlineOffsetDays(BigDecimal.ONE);
        target.setUsesBusinessDays(true);
        target.setActive(true);
        when(stageTargetRepository.findByEntityTypeAndStageValueAndActiveTrue(EntityType.LOT, "PREPARING")).thenReturn(List.of(target));

        Instant closingAt = enteredAt; // offset puts the deadline before enteredAt itself
        stubElapsedBusinessDays(3);

        var result = service.compute(EntityType.LOT, entityId, "status", "PREPARING", enteredAt, TargetContext.EMPTY,
                Map.of("closingAt", closingAt));

        assertThat(result.level()).isEqualTo(IndicatorLevel.CRITICAL);
    }

    @Test
    void deadlineRelativeTargetWithNoDeadlineValueIsUnknownNotAnOverflow() {
        StageTarget target = new StageTarget();
        target.setEntityType(EntityType.LOT);
        target.setStageValue("PREPARING");
        target.setTargetType(TargetType.DEADLINE_RELATIVE);
        target.setDeadlineField("closingAt");
        target.setDeadlineOffsetDays(BigDecimal.ONE);
        target.setUsesBusinessDays(true);
        target.setActive(true);
        when(stageTargetRepository.findByEntityTypeAndStageValueAndActiveTrue(EntityType.LOT, "PREPARING")).thenReturn(List.of(target));
        stubElapsedBusinessDays(1);

        // Simulates a bid with no closingAt set - IndicatorService.deadlineField() omits the key rather than passing a sentinel like Instant.MAX.
        var result = service.compute(EntityType.LOT, entityId, "status", "PREPARING", enteredAt, TargetContext.EMPTY,
                IndicatorService.deadlineField("closingAt", null));

        assertThat(result.level()).isEqualTo(IndicatorLevel.UNKNOWN);
    }

    @Test
    void sectorOverrideIsIgnoredWhenContextSectorDoesNotMatch() {
        StageTarget bankOnly = durationTarget(5);
        bankOnly.setSector(Sector.BANK);
        when(stageTargetRepository.findByEntityTypeAndStageValueAndActiveTrue(EntityType.LOT, "UNDER_REVIEW")).thenReturn(List.of(bankOnly));
        stubElapsedBusinessDays(1);

        var result = service.compute(EntityType.LOT, entityId, "status", "UNDER_REVIEW", enteredAt,
                new TargetContext(null, Sector.GOVERNMENT, null, null), Map.of());

        assertThat(result.level()).isEqualTo(IndicatorLevel.NO_TARGET);
    }
}
