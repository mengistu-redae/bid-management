package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.EntityType;
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

@Entity
@Table(name = "activity_log")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLogEntry {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    private UUID entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private AppUser author;

    private String note;

    @CreationTimestamp
    private Instant createdAt;

    public ActivityLogEntry(EntityType entityType, UUID entityId, AppUser author, String note) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.author = author;
        this.note = note;
    }
}
