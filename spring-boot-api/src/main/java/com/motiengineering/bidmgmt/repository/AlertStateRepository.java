package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.AlertState;
import com.motiengineering.bidmgmt.domain.AlertStateId;
import com.motiengineering.bidmgmt.domain.enums.AlertKind;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlertStateRepository extends JpaRepository<AlertState, AlertStateId> {
    /** Clears every episode of this kind for the entity (contextKey-agnostic) - used when a level drops back to green/not-stale, so the next crossing fires fresh. */
    void deleteById_EntityTypeAndId_EntityIdAndId_AlertKind(EntityType entityType, UUID entityId, AlertKind alertKind);

    /** "Newly" flagged since `since` - the daily digest's source for newly-amber/red items. */
    List<AlertState> findById_AlertKindAndLastSentAtAfter(AlertKind alertKind, Instant since);
}
