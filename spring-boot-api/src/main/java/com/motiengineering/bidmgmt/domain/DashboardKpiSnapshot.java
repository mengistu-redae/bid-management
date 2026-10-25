package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

/** One per calendar day - see DashboardKpiSnapshotJob. */
@Entity
@Table(name = "dashboard_kpi_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class DashboardKpiSnapshot {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(unique = true)
    private LocalDate snapshotDate;

    private int closingSoonCount;
    private int missingReadinessCount;
    private int behindStageTargetCount;
    private BigDecimal openPipelineEtb;
    private BigDecimal openPipelineUsd;
    private BigDecimal bidBondsOutstandingEtb;
    private int dealRegsExpiringCount;

    @CreationTimestamp
    private Instant createdAt;
}
