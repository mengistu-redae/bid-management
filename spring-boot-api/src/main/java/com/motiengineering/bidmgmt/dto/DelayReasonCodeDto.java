package com.motiengineering.bidmgmt.dto;

import java.util.UUID;

public record DelayReasonCodeDto(UUID id, String code, String label, boolean active, int sortOrder) {
}
