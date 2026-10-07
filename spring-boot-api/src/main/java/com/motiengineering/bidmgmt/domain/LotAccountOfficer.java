package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lot_account_officers")
@Getter
@Setter
@NoArgsConstructor
public class LotAccountOfficer {

    @EmbeddedId
    private LotAccountOfficerId id;

    public LotAccountOfficer(LotAccountOfficerId id) {
        this.id = id;
    }
}
