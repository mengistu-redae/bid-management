package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.Sector;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization {

    @Id
    @UuidGenerator
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    private Sector sector = Sector.OTHER;

    private String notes;

    @CreationTimestamp
    private Instant createdAt;

    public Organization(String name, Sector sector) {
        this.name = name;
        this.sector = sector;
    }
}
