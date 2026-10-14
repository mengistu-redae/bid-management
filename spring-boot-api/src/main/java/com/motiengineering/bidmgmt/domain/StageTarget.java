package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.domain.enums.ShipmentMode;
import com.motiengineering.bidmgmt.domain.enums.TargetType;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * "What does late mean" for one (entityType, stageValue) pair, with
 * optional narrower overrides (division/sector/OEM-name/shipment mode) -
 * IndicatorService picks the most specific row that matches an instance,
 * falling back to the row with every override column null. DURATION
 * targets cap time spent in the stage; DEADLINE_RELATIVE targets instead
 * require the stage to finish a given offset before some other date field
 * on the same entity (e.g. "signed 15 days before closing").
 */
@Entity
@Table(name = "stage_targets")
@Getter
@Setter
@NoArgsConstructor
public class StageTarget {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    private String stageValue;

    @Enumerated(EnumType.STRING)
    private TargetType targetType;

    private BigDecimal targetDays;

    private boolean usesBusinessDays = true;

    private String deadlineField;

    private BigDecimal deadlineOffsetDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id")
    private Division division;

    @Enumerated(EnumType.STRING)
    private Sector sector;

    private String oemName;

    @Enumerated(EnumType.STRING)
    private ShipmentMode shipmentMode;

    private BigDecimal staleAfterDays;

    private boolean active = true;

    private String notes;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
