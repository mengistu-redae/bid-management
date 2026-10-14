package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.util.UUID;

public record WaitingOnDto(
        String partyType,
        UUID personId,
        String personName,
        String note,
        Instant updatedAt) {
}
