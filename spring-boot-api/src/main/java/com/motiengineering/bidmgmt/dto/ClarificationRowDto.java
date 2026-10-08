package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClarificationRowDto(UUID bidId, String title, String organizationName, Instant clarificationDeadline, List<String> divisionNames) {
}
