package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.LotAccountOfficerId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LotAccountOfficerRepository extends JpaRepository<LotAccountOfficer, LotAccountOfficerId> {
    List<LotAccountOfficer> findById_LotId(UUID lotId);

    void deleteById_LotId(UUID lotId);
}
