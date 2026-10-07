package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    List<Organization> findByNameIgnoreCase(String name);

    List<Organization> findByNameContainingIgnoreCase(String fragment);
}
