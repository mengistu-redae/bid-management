package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bid_scouts")
@Getter
@Setter
@NoArgsConstructor
public class BidScout {

    @EmbeddedId
    private BidScoutId id;

    public BidScout(BidScoutId id) {
        this.id = id;
    }
}
