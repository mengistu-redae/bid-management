package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.BidBondForm;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.Currency;
import com.motiengineering.bidmgmt.domain.enums.Outcome;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bid_lots")
@Getter
@Setter
@NoArgsConstructor
public class BidLot {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bid_id", nullable = false)
    private Bid bid;

    private String lotLabel = "Lot 1";

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id")
    private Division division;

    private BigDecimal estimatedValue;

    @Enumerated(EnumType.STRING)
    private Currency estimatedValueCurrency;

    private BigDecimal bidBondAmount;

    @Enumerated(EnumType.STRING)
    private Currency bidBondCurrency;

    private Integer bidBondValidityDays;

    @Enumerated(EnumType.STRING)
    private BidBondForm bidBondForm = BidBondForm.UNKNOWN;

    private String oemBrand;

    @Enumerated(EnumType.STRING)
    private BidStatus status = BidStatus.IDENTIFIED;

    private String exitReason;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    private String winnerName;

    private BigDecimal winningPrice;

    @Enumerated(EnumType.STRING)
    private Currency winningPriceCurrency;

    private boolean needsReview;

    private String reviewNote;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "lot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChecklistItem> checklistItems = new ArrayList<>();
}
