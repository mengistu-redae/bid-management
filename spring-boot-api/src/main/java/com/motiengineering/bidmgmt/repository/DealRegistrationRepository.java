package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.DealRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DealRegistrationRepository extends JpaRepository<DealRegistration, UUID> {
    List<DealRegistration> findByOrganization_IdAndOem_Id(UUID organizationId, UUID oemId);
}
