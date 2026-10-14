package com.motiengineering.bidmgmt.service;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers elapsed-time/business-day math: the working week, holidays and on-hold pauses. */
class BusinessDayCalculatorTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final Set<DayOfWeek> MON_TO_SAT = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

    @Test
    void sundayIsNeverAWorkingDayUnderMonToSat() {
        BusinessDayCalculator calculator = new BusinessDayCalculator(MON_TO_SAT, Set.of(), UTC);
        assertThat(calculator.isWorkingDay(LocalDate.of(2026, 1, 11))).isFalse(); // a Sunday
        assertThat(calculator.isWorkingDay(LocalDate.of(2026, 1, 10))).isTrue(); // the Saturday before it
    }

    @Test
    void aHolidayOnAWorkingDayDoesNotCount() {
        LocalDate newYear = LocalDate.of(2026, 9, 11); // Enkutatash, a Friday
        BusinessDayCalculator calculator = new BusinessDayCalculator(MON_TO_SAT, Set.of(newYear), UTC);
        assertThat(calculator.isWorkingDay(newYear)).isFalse();
    }

    @Test
    void businessDaysBetweenCountsOnlyWorkingDaysExcludingHolidays() {
        BusinessDayCalculator calculator = new BusinessDayCalculator(MON_TO_SAT, Set.of(LocalDate.of(2026, 1, 7)), UTC);
        // Mon 2026-01-05 through Sat 2026-01-10 (exclusive end): Mon,Tue,Wed(holiday),Thu,Fri,Sat -> 5 business days, Sunday the 11th excluded by range end anyway.
        Instant from = LocalDate.of(2026, 1, 5).atStartOfDay(UTC).toInstant();
        Instant to = LocalDate.of(2026, 1, 11).atStartOfDay(UTC).toInstant();
        long days = calculator.businessDaysBetween(from, to, List.of());
        assertThat(days).isEqualTo(5);
    }

    @Test
    void businessDaysBetweenReturnsZeroForNonPositiveRange() {
        BusinessDayCalculator calculator = new BusinessDayCalculator(MON_TO_SAT, Set.of(), UTC);
        Instant now = Instant.now();
        assertThat(calculator.businessDaysBetween(now, now, List.of())).isZero();
        assertThat(calculator.businessDaysBetween(now, now.minusSeconds(60), List.of())).isZero();
    }

    @Test
    void aHoldWindowRemovesTheDaysItCoversFromTheCount() {
        BusinessDayCalculator calculator = new BusinessDayCalculator(MON_TO_SAT, Set.of(), UTC);
        // Mon 2026-01-05 through Fri 2026-01-09 (exclusive): 4 working days (Mon-Thu) without a hold.
        Instant from = LocalDate.of(2026, 1, 5).atStartOfDay(UTC).toInstant();
        Instant to = LocalDate.of(2026, 1, 9).atStartOfDay(UTC).toInstant();
        assertThat(calculator.businessDaysBetween(from, to, List.of())).isEqualTo(4);

        // Put Tue-Wed (01-06 to 01-08) on hold - only Mon and Thu should still count.
        Instant holdStart = LocalDate.of(2026, 1, 6).atStartOfDay(UTC).toInstant();
        Instant holdEnd = LocalDate.of(2026, 1, 8).atStartOfDay(UTC).toInstant();
        long daysExcludingHold = calculator.businessDaysBetween(from, to, List.of(new Instant[][]{{holdStart, holdEnd}}));
        assertThat(daysExcludingHold).isEqualTo(2);
    }

    @Test
    void addBusinessDaysSkipsWeekendsAndHolidays() {
        BusinessDayCalculator calculator = new BusinessDayCalculator(
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                Set.of(), UTC);
        // Fri 2026-01-02 + 2 business days -> skip Sat/Sun -> Mon 01-05, Tue 01-06.
        LocalDate result = calculator.addBusinessDays(LocalDate.of(2026, 1, 2), 2);
        assertThat(result).isEqualTo(LocalDate.of(2026, 1, 6));
    }
}
