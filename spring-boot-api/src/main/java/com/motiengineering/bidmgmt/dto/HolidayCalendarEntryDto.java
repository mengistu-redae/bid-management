package com.motiengineering.bidmgmt.dto;

import java.time.LocalDate;
import java.util.UUID;

public record HolidayCalendarEntryDto(UUID id, LocalDate holidayDate, String name) {
}
