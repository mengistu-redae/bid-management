package com.motiengineering.bidmgmt.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ExpiringDealRegistrationRowDto(UUID id, String oemName, String organizationName, LocalDate expiryDate) {
}
