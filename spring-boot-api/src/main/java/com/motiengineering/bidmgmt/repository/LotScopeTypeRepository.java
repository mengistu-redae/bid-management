package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.LotScopeType;
import com.motiengineering.bidmgmt.domain.LotScopeTypeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LotScopeTypeRepository extends JpaRepository<LotScopeType, LotScopeTypeId> {
    List<LotScopeType> findById_LotId(UUID lotId);

    void deleteById_LotId(UUID lotId);
}
