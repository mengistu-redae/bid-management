package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {
}
