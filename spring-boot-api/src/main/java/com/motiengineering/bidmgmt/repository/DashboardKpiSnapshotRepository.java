package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.DashboardKpiSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DashboardKpiSnapshotRepository extends JpaRepository<DashboardKpiSnapshot, java.util.UUID> {
    Optional<DashboardKpiSnapshot> findBySnapshotDate(LocalDate snapshotDate);

    /** Nearest snapshot at least this old - a tolerant "about a week ago" lookup for the dashboard's trend comparison. */
    Optional<DashboardKpiSnapshot> findFirstBySnapshotDateLessThanEqualOrderBySnapshotDateDesc(LocalDate onOrBefore);
}
