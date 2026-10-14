package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.HoldPeriod;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HoldPeriodRepository extends JpaRepository<HoldPeriod, UUID> {
    List<HoldPeriod> findByEntityTypeAndEntityIdOrderByStartedAt(EntityType entityType, UUID entityId);

    Optional<HoldPeriod> findByEntityTypeAndEntityIdAndEndedAtIsNull(EntityType entityType, UUID entityId);
}
