package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "checklist_item_templates")
@Getter
@Setter
@NoArgsConstructor
public class ChecklistItemTemplate {

    @Id
    @UuidGenerator
    private UUID id;

    private String title;

    private int sortOrder;

    private boolean active = true;
}
