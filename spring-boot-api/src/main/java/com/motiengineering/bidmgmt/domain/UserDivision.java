package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_divisions")
@Getter
@Setter
@NoArgsConstructor
public class UserDivision {

    @EmbeddedId
    private UserDivisionId id;

    private boolean isManager;

    public UserDivision(UserDivisionId id, boolean isManager) {
        this.id = id;
        this.isManager = isManager;
    }
}
