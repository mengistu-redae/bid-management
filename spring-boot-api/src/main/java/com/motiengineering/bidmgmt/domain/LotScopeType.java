package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lot_scope_types")
@Getter
@Setter
@NoArgsConstructor
public class LotScopeType {

    @EmbeddedId
    private LotScopeTypeId id;

    public LotScopeType(LotScopeTypeId id) {
        this.id = id;
    }
}
