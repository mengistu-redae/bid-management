package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "opportunity_oems")
@Getter
@Setter
@NoArgsConstructor
public class OpportunityOem {

    @EmbeddedId
    private OpportunityOemId id;

    public OpportunityOem(OpportunityOemId id) {
        this.id = id;
    }
}
