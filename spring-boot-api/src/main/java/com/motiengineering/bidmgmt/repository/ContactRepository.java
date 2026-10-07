package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {
    List<Contact> findByOrganization_Id(UUID organizationId);
}
