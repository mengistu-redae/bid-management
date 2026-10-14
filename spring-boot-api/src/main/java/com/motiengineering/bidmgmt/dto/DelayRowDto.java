package com.motiengineering.bidmgmt.dto;

import java.util.UUID;

public record DelayRowDto(UUID bidId, String bidTitle, String lotLabel, String divisionName, String level, Double percentUsed) {
}
