package com.motiengineering.bidmgmt.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Pure business-day math: no Spring, no DB - just a working-week set, a
 * holiday set and a timezone, so it can be unit-tested directly (see
 * BusinessDayCalculatorTest) without standing up the application context.
 * BusinessDayCalendarService is the Spring-aware wrapper that loads the
 * working week and holidays from the DB and feeds them in here.
 */
public final class BusinessDayCalculator {

    private final Set<DayOfWeek> workingDays;
    private final Set<LocalDate> holidays;
    private final ZoneId zone;

    public BusinessDayCalculator(Set<DayOfWeek> workingDays, Set<LocalDate> holidays, ZoneId zone) {
        this.workingDays = workingDays;
        this.holidays = holidays;
        this.zone = zone;
    }

    public boolean isWorkingDay(LocalDate date) {
        return workingDays.contains(date.getDayOfWeek()) && !holidays.contains(date);
    }

    /**
     * Business days in the half-open range [from, to), at local dates in
     * this calculator's zone, excluding any day that falls inside one of
     * holdWindows (each a [start, end) instant pair - an open hold's end
     * should be passed as "now" by the caller).
     */
    public long businessDaysBetween(Instant from, Instant to, List<Instant[]> holdWindows) {
        if (to == null || from == null || !to.isAfter(from)) {
            return 0;
        }
        LocalDate fromDate = from.atZone(zone).toLocalDate();
        LocalDate toDate = to.atZone(zone).toLocalDate();
        long count = 0;
        for (LocalDate d = fromDate; d.isBefore(toDate); d = d.plusDays(1)) {
            if (!isWorkingDay(d)) {
                continue;
            }
            if (isHeld(d, holdWindows)) {
                continue;
            }
            count++;
        }
        return count;
    }

    /** Rolls forward from `start` (exclusive) by `days` working days - used for deadline-relative targets. */
    public LocalDate addBusinessDays(LocalDate start, int days) {
        LocalDate d = start;
        int remaining = days;
        int step = remaining >= 0 ? 1 : -1;
        remaining = Math.abs(remaining);
        while (remaining > 0) {
            d = d.plusDays(step);
            if (isWorkingDay(d)) {
                remaining--;
            }
        }
        return d;
    }

    private boolean isHeld(LocalDate date, List<Instant[]> holdWindows) {
        if (holdWindows == null || holdWindows.isEmpty()) {
            return false;
        }
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        for (Instant[] window : holdWindows) {
            Instant holdStart = window[0];
            Instant holdEnd = window[1];
            if (holdStart.isBefore(dayEnd) && holdEnd.isAfter(dayStart)) {
                return true;
            }
        }
        return false;
    }
}
