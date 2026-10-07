package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.OrganizationAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationAliasRepository extends JpaRepository<OrganizationAlias, UUID> {
    Optional<OrganizationAlias> findByAliasNormalized(String aliasNormalized);

    List<OrganizationAlias> findByOrganization_Id(UUID organizationId);
}
