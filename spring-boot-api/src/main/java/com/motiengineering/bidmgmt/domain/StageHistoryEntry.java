package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.EntityType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One append-only row per stage/status/waiting-on transition, for any
 * tracked entity (Opportunity, DealRegistration, Bid, Lot, ChecklistItem,
 * Contract, Logistics) - the single consistent structure every stage-clock
 * and delay report reads from. stageField says which attribute moved
 * ("status", "stage", "waitingOn", "hold"); fromValue/toValue are the plain
 * string form of the old/new value so this table never has to know about
 * every entity's enum types. isBackfilled marks rows synthesized from
 * pre-existing data (e.g. replayed from bid_status_history) rather than
 * recorded live, so reporting can tell real timestamps from reconstructed
 * ones.
 */
@Entity
@Table(name = "stage_history")
@Getter
@Setter
@NoArgsConstructor
public class StageHistoryEntry {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    private UUID entityId;

    private String stageField;

    private String fromValue;

    private String toValue;

    private String note;

    /** Required when exiting a stage that was RED/CRITICAL at the time - see IndicatorService. */
    private String delayReasonCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private AppUser changedBy;

    private Instant changedAt;

    private boolean backfilled;

    public StageHistoryEntry(EntityType entityType, UUID entityId, String stageField, String fromValue, String toValue, String note, AppUser changedBy, Instant changedAt, boolean backfilled) {
        this(entityType, entityId, stageField, fromValue, toValue, note, null, changedBy, changedAt, backfilled);
    }

    public StageHistoryEntry(EntityType entityType, UUID entityId, String stageField, String fromValue, String toValue, String note, String delayReasonCode, AppUser changedBy, Instant changedAt, boolean backfilled) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.stageField = stageField;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.note = note;
        this.delayReasonCode = delayReasonCode;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.backfilled = backfilled;
    }
}
