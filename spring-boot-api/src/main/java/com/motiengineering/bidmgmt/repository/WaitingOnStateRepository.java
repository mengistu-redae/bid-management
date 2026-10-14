package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.WaitingOnState;
import com.motiengineering.bidmgmt.domain.WaitingOnStateId;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaitingOnStateRepository extends JpaRepository<WaitingOnState, WaitingOnStateId> {
    List<WaitingOnState> findById_EntityType(EntityType entityType);
}
