package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.Currency;
import com.motiengineering.bidmgmt.domain.enums.OpportunityStage;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "opportunities")
@Getter
@Setter
@NoArgsConstructor
public class Opportunity {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private String title;

    private BigDecimal estimatedValue;

    @Enumerated(EnumType.STRING)
    private Currency estimatedValueCurrency;

    private LocalDate expectedTenderDate;

    @Enumerated(EnumType.STRING)
    private OpportunityStage stage = OpportunityStage.LEAD;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    private String lostReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_bid_id")
    private Bid convertedBid;

    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
