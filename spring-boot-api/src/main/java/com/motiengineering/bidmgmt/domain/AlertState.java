package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "alert_state")
@Getter
@Setter
@NoArgsConstructor
public class AlertState {

    @EmbeddedId
    private AlertStateId id;

    /** For INDICATOR: the IndicatorLevel name already notified. For STALE: "STALE" or "STALE_DOUBLE". For FOLLOWUP_DUE: "NOTIFIED". */
    private String lastLevel;

    private Instant lastSentAt;

    public AlertState(AlertStateId id, String lastLevel, Instant lastSentAt) {
        this.id = id;
        this.lastLevel = lastLevel;
        this.lastSentAt = lastSentAt;
    }
}
