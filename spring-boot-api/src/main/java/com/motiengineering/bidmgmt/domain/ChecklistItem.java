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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "checklist_items")
@Getter
@Setter
@NoArgsConstructor
public class ChecklistItem {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private BidLot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private ChecklistItemTemplate template;

    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    private LocalDate dueDate;

    private boolean done;

    private Instant doneAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "done_by")
    private AppUser doneBy;

    private int sortOrder;

    @CreationTimestamp
    private Instant createdAt;
}
