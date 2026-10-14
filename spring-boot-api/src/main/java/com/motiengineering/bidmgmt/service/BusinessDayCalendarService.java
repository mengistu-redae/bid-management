package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.WorkingWeekConfig;
import com.motiengineering.bidmgmt.repository.HolidayCalendarRepository;
import com.motiengineering.bidmgmt.repository.WorkingWeekConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the working-week config and holiday calendar from the DB on every
 * call (both are tiny, admin-edited-rarely tables - no caching needed yet)
 * and delegates the actual math to BusinessDayCalculator.
 */
@Service
@RequiredArgsConstructor
public class BusinessDayCalendarService {

    public static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private final WorkingWeekConfigRepository workingWeekConfigRepository;
    private final HolidayCalendarRepository holidayCalendarRepository;

    public BusinessDayCalculator calculator() {
        Set<DayOfWeek> workingDays = workingWeekConfigRepository.findById(1)
                .map(WorkingWeekConfig::workingDaySet)
                .orElse(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));
        Set<LocalDate> holidays = holidayCalendarRepository.findAll().stream()
                .map(com.motiengineering.bidmgmt.domain.HolidayCalendarEntry::getHolidayDate)
                .collect(Collectors.toSet());
        return new BusinessDayCalculator(workingDays, holidays, ZONE);
    }

    public long businessDaysBetween(Instant from, Instant to, List<Instant[]> holdWindows) {
        return calculator().businessDaysBetween(from, to, holdWindows);
    }

    public boolean isWorkingDay(LocalDate date) {
        return calculator().isWorkingDay(date);
    }

    public LocalDate addBusinessDays(LocalDate start, int days) {
        return calculator().addBusinessDays(start, days);
    }
}
