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
@Table(name = "delay_reason_codes")
@Getter
@Setter
@NoArgsConstructor
public class DelayReasonCode {

    @Id
    @UuidGenerator
    private UUID id;

    private String code;

    private String label;

    private boolean active = true;

    private int sortOrder;
}
