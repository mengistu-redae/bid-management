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
@Table(name = "oems")
@Getter
@Setter
@NoArgsConstructor
public class Oem {

    @Id
    @UuidGenerator
    private UUID id;

    private String name;

    public Oem(String name) {
        this.name = name;
    }
}
