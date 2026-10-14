package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.StageTarget;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StageTargetRepository extends JpaRepository<StageTarget, UUID> {
    List<StageTarget> findByEntityTypeAndStageValueAndActiveTrue(EntityType entityType, String stageValue);

    List<StageTarget> findAllByOrderByEntityTypeAscStageValueAsc();
}
