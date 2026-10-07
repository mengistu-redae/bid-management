package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.UserDivision;
import com.motiengineering.bidmgmt.domain.UserDivisionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserDivisionRepository extends JpaRepository<UserDivision, UserDivisionId> {
    List<UserDivision> findById_UserId(UUID userId);

    List<UserDivision> findById_DivisionId(UUID divisionId);

    void deleteById_UserId(UUID userId);
}
