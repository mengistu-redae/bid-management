package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.Division;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DivisionRepository extends JpaRepository<Division, UUID> {
    Optional<Division> findByCode(String code);
}
