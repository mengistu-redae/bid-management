package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.AlertKind;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * contextKey scopes an alert to one "episode" so it fires once per
 * threshold crossing, not once ever: for INDICATOR it's the current stage
 * value (a new stage = a new episode); for STALE it's the fixed string
 * "STALE"; for FOLLOWUP_DUE it's the due date itself (ISO-8601), so a
 * later due date is a fresh episode.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AlertStateId implements Serializable {

    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    private UUID entityId;

    @Enumerated(EnumType.STRING)
    private AlertKind alertKind;

    private String contextKey;
}
