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
@Table(name = "divisions")
@Getter
@Setter
@NoArgsConstructor
public class Division {

    @Id
    @UuidGenerator
    private UUID id;

    private String code;

    private String name;
}
