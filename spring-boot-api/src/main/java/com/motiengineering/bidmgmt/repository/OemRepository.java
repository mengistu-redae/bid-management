package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.Oem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OemRepository extends JpaRepository<Oem, UUID> {
    Optional<Oem> findByNameIgnoreCase(String name);
}
