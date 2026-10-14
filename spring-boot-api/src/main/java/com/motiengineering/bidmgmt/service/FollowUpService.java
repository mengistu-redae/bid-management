package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.FollowUpState;
import com.motiengineering.bidmgmt.domain.FollowUpStateId;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.repository.FollowUpStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowUpService {

    private final FollowUpStateRepository followUpStateRepository;
    private final ActivityLogService activityLogService;

    public Optional<FollowUpState> get(EntityType entityType, UUID entityId) {
        return followUpStateRepository.findById(new FollowUpStateId(entityType, entityId));
    }

    public List<FollowUpState> forOwner(UUID ownerId) {
        return followUpStateRepository.findByFollowUpOwner_Id(ownerId);
    }

    /** Logging a follow-up note and setting the next follow-up date happen together, per the brief - the note goes to the entity's existing activity log. */
    @Transactional
    public FollowUpState logFollowUp(EntityType entityType, UUID entityId, String note, LocalDate nextFollowUpDate, AppUser followUpOwner, AppUser actingUser) {
        if (note != null && !note.isBlank()) {
            activityLogService.addNote(entityType, entityId, actingUser, note);
        }
        FollowUpState state = followUpStateRepository.findById(new FollowUpStateId(entityType, entityId))
                .orElseGet(() -> new FollowUpState(entityType, entityId));
        state.setNextFollowUpDate(nextFollowUpDate);
        if (followUpOwner != null) {
            state.setFollowUpOwner(followUpOwner);
        }
        state.setUpdatedBy(actingUser);
        return followUpStateRepository.save(state);
    }

    @Transactional
    public FollowUpState setOwner(EntityType entityType, UUID entityId, AppUser owner, AppUser actingUser) {
        FollowUpState state = followUpStateRepository.findById(new FollowUpStateId(entityType, entityId))
                .orElseGet(() -> new FollowUpState(entityType, entityId));
        state.setFollowUpOwner(owner);
        state.setUpdatedBy(actingUser);
        return followUpStateRepository.save(state);
    }

    @Transactional
    public FollowUpState snooze(EntityType entityType, UUID entityId, LocalDate snoozedUntil, String reason, AppUser actingUser) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to snooze");
        }
        if (snoozedUntil == null) {
            throw new IllegalArgumentException("A snooze-until date is required");
        }
        FollowUpState state = followUpStateRepository.findById(new FollowUpStateId(entityType, entityId))
                .orElseGet(() -> new FollowUpState(entityType, entityId));
        state.setSnoozedUntil(snoozedUntil);
        state.setSnoozeReason(reason);
        // Snoozing also pushes the next follow-up out to the same date, so "My Follow-ups" and the Delays board agree on when this is next due.
        state.setNextFollowUpDate(snoozedUntil);
        state.setUpdatedBy(actingUser);
        activityLogService.addNote(entityType, entityId, actingUser, "Snoozed until " + snoozedUntil + ": " + reason);
        return followUpStateRepository.save(state);
    }

    public boolean isSnoozed(EntityType entityType, UUID entityId, LocalDate today) {
        return get(entityType, entityId).map(s -> s.isSnoozed(today)).orElse(false);
    }
}
