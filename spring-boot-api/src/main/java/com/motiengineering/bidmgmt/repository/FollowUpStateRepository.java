package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.FollowUpState;
import com.motiengineering.bidmgmt.domain.FollowUpStateId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FollowUpStateRepository extends JpaRepository<FollowUpState, FollowUpStateId> {
    List<FollowUpState> findByFollowUpOwner_Id(UUID ownerId);

    List<FollowUpState> findByNextFollowUpDateLessThanEqualAndFollowUpOwnerIsNotNull(LocalDate date);
}
