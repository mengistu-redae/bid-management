package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.DelayReasonCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DelayReasonCodeRepository extends JpaRepository<DelayReasonCode, UUID> {
    List<DelayReasonCode> findByActiveTrueOrderBySortOrder();
}
