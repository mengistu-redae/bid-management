package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** Single row (id fixed at 1) - global, admin-editable indicator thresholds. */
@Entity
@Table(name = "indicator_config")
@Getter
@Setter
@NoArgsConstructor
public class IndicatorConfig {

    @Id
    private int id = 1;

    private BigDecimal amberThresholdPercent;

    private BigDecimal redThresholdPercent;

    private BigDecimal criticalThresholdPercent;

    private BigDecimal defaultStaleAfterDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    @UpdateTimestamp
    private Instant updatedAt;
}
