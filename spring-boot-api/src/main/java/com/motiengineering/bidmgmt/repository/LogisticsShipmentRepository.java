package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.LogisticsShipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LogisticsShipmentRepository extends JpaRepository<LogisticsShipment, UUID> {
    List<LogisticsShipment> findByContract_IdOrderByCreatedAt(UUID contractId);
}
