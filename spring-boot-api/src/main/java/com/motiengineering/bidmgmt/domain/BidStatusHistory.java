package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.BidStatus;
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

import java.time.Instant;
import java.util.UUID;

/** Exactly one of bid/lot is set - see the DB check constraint on bid_status_history. */
@Entity
@Table(name = "bid_status_history")
@Getter
@Setter
@NoArgsConstructor
public class BidStatusHistory {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bid_id")
    private Bid bid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private BidLot lot;

    @Enumerated(EnumType.STRING)
    private BidStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private BidStatus toStatus;

    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private AppUser changedBy;

    @CreationTimestamp
    private Instant changedAt;
}
