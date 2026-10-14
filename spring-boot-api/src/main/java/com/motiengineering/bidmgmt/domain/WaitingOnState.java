package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.WaitingOnParty;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Current "who's this waiting on" per entity - the full history of changes lives in stage_history (stageField "waitingOn"). */
@Entity
@Table(name = "waiting_on_state")
@Getter
@Setter
@NoArgsConstructor
public class WaitingOnState {

    @EmbeddedId
    private WaitingOnStateId id;

    @Enumerated(EnumType.STRING)
    private WaitingOnParty partyType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private AppUser person;

    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    @UpdateTimestamp
    private Instant updatedAt;

    public WaitingOnState(EntityType entityType, java.util.UUID entityId) {
        this.id = new WaitingOnStateId(entityType, entityId);
    }
}
