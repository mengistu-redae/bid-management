package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<Contract, UUID> {
    Optional<Contract> findByBid_Id(UUID bidId);
}
