package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.MilestoneCategory;
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
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Generic milestone row (acceptance step, payment milestone, shipment
 * milestone) attached to a Contract or LogisticsShipment via entityType
 * + entityId - the same polymorphic shape as ActivityLogEntry, just for a
 * planned-date/actual-date/done tracked item instead of a free note. Mirrors
 * ChecklistItem's shape deliberately, one level up (contract/shipment
 * instead of bid lot).
 */
@Entity
@Table(name = "stage_milestones")
@Getter
@Setter
@NoArgsConstructor
public class StageMilestone {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    private UUID entityId;

    @Enumerated(EnumType.STRING)
    private MilestoneCategory category;

    private String title;

    private LocalDate plannedDate;

    private LocalDate actualDate;

    private BigDecimal amount;

    private boolean done;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "done_by")
    private AppUser doneBy;

    private Instant doneAt;

    private int sortOrder;

    @CreationTimestamp
    private Instant createdAt;
}
