package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.BidSource;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.GoNoGo;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bids")
@Getter
@Setter
@NoArgsConstructor
public class Bid {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private String title;

    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    private BidSource source = BidSource.UNKNOWN;

    private java.time.LocalDate scoutedDate;

    private Instant closingAt;

    private Instant openingAt;

    private Instant clarificationDeadline;

    private Integer bidValidityDays;

    @Enumerated(EnumType.STRING)
    private BidStatus status = BidStatus.IDENTIFIED;

    @Enumerated(EnumType.STRING)
    private GoNoGo goNoGoDecision;

    private String goNoGoReason;

    private String exitReason;

    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "bid", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BidLot> lots = new ArrayList<>();
}
