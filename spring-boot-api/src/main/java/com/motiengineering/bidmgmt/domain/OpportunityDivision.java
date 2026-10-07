package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "opportunity_divisions")
@Getter
@Setter
@NoArgsConstructor
public class OpportunityDivision {

    @EmbeddedId
    private OpportunityDivisionId id;

    public OpportunityDivision(OpportunityDivisionId id) {
        this.id = id;
    }
}
