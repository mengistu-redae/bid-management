package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.FollowUpState;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.repository.FollowUpStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers snooze behavior: required reason/date, the next-follow-up-date push, and the audit-trail note. */
class FollowUpServiceTest {

    private FollowUpStateRepository followUpStateRepository;
    private ActivityLogService activityLogService;
    private FollowUpService service;

    private final UUID entityId = UUID.randomUUID();
    private final AppUser actingUser = new AppUser();

    @BeforeEach
    void setUp() {
        followUpStateRepository = mock(FollowUpStateRepository.class);
        activityLogService = mock(ActivityLogService.class);
        when(followUpStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new FollowUpService(followUpStateRepository, activityLogService);
    }

    @Test
    void snoozeRequiresAReason() {
        assertThatThrownBy(() -> service.snooze(EntityType.LOT, entityId, LocalDate.now().plusDays(3), null, actingUser))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.snooze(EntityType.LOT, entityId, LocalDate.now().plusDays(3), "  ", actingUser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snoozeRequiresADate() {
        assertThatThrownBy(() -> service.snooze(EntityType.LOT, entityId, null, "waiting on customer", actingUser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snoozePushesTheNextFollowUpDateAndLogsAnActivityNote() {
        when(followUpStateRepository.findById(any())).thenReturn(Optional.empty());
        LocalDate until = LocalDate.now().plusDays(10);

        FollowUpState result = service.snooze(EntityType.LOT, entityId, until, "awaiting budget approval", actingUser);

        assertThat(result.getSnoozedUntil()).isEqualTo(until);
        assertThat(result.getSnoozeReason()).isEqualTo("awaiting budget approval");
        assertThat(result.getNextFollowUpDate()).isEqualTo(until);
        verify(activityLogService).addNote(EntityType.LOT, entityId, actingUser, "Snoozed until " + until + ": awaiting budget approval");
    }

    @Test
    void isSnoozedIsTrueOnAndBeforeTheSnoozeDateAndFalseAfter() {
        FollowUpState state = new FollowUpState(EntityType.LOT, entityId);
        state.setSnoozedUntil(LocalDate.of(2026, 6, 15));

        assertThat(state.isSnoozed(LocalDate.of(2026, 6, 14))).isTrue();
        assertThat(state.isSnoozed(LocalDate.of(2026, 6, 15))).isTrue();
        assertThat(state.isSnoozed(LocalDate.of(2026, 6, 16))).isFalse();
    }

    @Test
    void logFollowUpSetsDateAndOwnerAndOptionallyLogsANote() {
        when(followUpStateRepository.findById(any())).thenReturn(Optional.empty());
        AppUser owner = new AppUser();
        LocalDate next = LocalDate.now().plusDays(5);

        FollowUpState result = service.logFollowUp(EntityType.LOT, entityId, "called the customer", next, owner, actingUser);

        assertThat(result.getNextFollowUpDate()).isEqualTo(next);
        assertThat(result.getFollowUpOwner()).isEqualTo(owner);
        verify(activityLogService).addNote(EntityType.LOT, entityId, actingUser, "called the customer");
    }
}
