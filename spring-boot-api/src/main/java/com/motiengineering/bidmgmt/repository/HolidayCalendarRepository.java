package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.HolidayCalendarEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendarEntry, UUID> {
    List<HolidayCalendarEntry> findByHolidayDateBetween(LocalDate from, LocalDate to);
}
