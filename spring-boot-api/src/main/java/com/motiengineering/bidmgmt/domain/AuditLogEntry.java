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
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogEntry {

    @Id
    @UuidGenerator
    private UUID id;

    private String entityType;

    private UUID entityId;

    private String fieldName;

    private String oldValue;

    private String newValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private AppUser changedBy;

    @CreationTimestamp
    private Instant changedAt;

    public AuditLogEntry(String entityType, UUID entityId, String fieldName, String oldValue, String newValue, AppUser changedBy) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedBy = changedBy;
    }
}
