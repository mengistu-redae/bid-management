package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.LogisticsStatus;
import com.motiengineering.bidmgmt.domain.enums.ShipmentMode;
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

import java.time.Instant;
import java.util.UUID;

/** A contract can have more than one shipment (partial deliveries) - hence the plain FK, not a unique one. */
@Entity
@Table(name = "logistics_shipments")
@Getter
@Setter
@NoArgsConstructor
public class LogisticsShipment {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    private ShipmentMode shipmentMode = ShipmentMode.SEA;

    @Enumerated(EnumType.STRING)
    private LogisticsStatus status = LogisticsStatus.PLANNING;

    private Instant dispatchedAt;

    private Instant etaAt;

    private Instant arrivedAt;

    private Instant customsClearedAt;

    private Instant deliveredAt;

    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
