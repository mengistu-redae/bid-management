package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "organization_aliases")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationAlias {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private String alias;

    @Column(insertable = false, updatable = false)
    private String aliasNormalized;

    public OrganizationAlias(Organization organization, String alias) {
        this.organization = organization;
        this.alias = alias;
    }
}
