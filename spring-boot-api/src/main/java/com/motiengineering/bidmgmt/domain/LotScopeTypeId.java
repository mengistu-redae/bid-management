package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.ScopeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LotScopeTypeId implements Serializable {

    private UUID lotId;

    @Enumerated(EnumType.STRING)
    private ScopeType scopeType;
}
