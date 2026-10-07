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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_batches")
@Getter
@Setter
@NoArgsConstructor
public class ImportBatch {

    @Id
    @UuidGenerator
    private UUID id;

    /** UPCOMING_BIDS or BIDS_TRACKER. */
    private String kind;

    private String sourceFilename;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by")
    private AppUser importedBy;

    @CreationTimestamp
    private Instant importedAt;

    private int rowsTotal;

    private int rowsCreated;

    private int rowsMerged;

    private int rowsFlagged;

    private int rowsSkipped;
}
