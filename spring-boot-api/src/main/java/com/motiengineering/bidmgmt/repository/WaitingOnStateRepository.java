package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.WaitingOnState;
import com.motiengineering.bidmgmt.domain.WaitingOnStateId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitingOnStateRepository extends JpaRepository<WaitingOnState, WaitingOnStateId> {
}
