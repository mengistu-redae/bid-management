package com.motiengineering.bidmgmt.dto;

import java.time.Instant;

public record StageClockDto(
        String stageField,
        String currentValue,
        Instant enteredAt,
        double calendarDays,
        long businessDays,
        boolean onHold,
        boolean unknown) {
}
