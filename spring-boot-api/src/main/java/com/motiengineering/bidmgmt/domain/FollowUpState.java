package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.EntityType;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Next-follow-up date/owner per entity, plus an optional snooze - the snooze and its reason also get logged to stage_history ("followUp" field) for an audit trail. */
@Entity
@Table(name = "follow_up_state")
@Getter
@Setter
@NoArgsConstructor
public class FollowUpState {

    @EmbeddedId
    private FollowUpStateId id;

    private LocalDate nextFollowUpDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follow_up_owner_id")
    private AppUser followUpOwner;

    private LocalDate snoozedUntil;

    private String snoozeReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    @UpdateTimestamp
    private Instant updatedAt;

    public FollowUpState(EntityType entityType, UUID entityId) {
        this.id = new FollowUpStateId(entityType, entityId);
    }

    public boolean isSnoozed(LocalDate today) {
        return snoozedUntil != null && !snoozedUntil.isBefore(today);
    }
}
