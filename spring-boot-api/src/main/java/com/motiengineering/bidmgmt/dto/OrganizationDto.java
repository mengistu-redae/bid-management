package com.motiengineering.bidmgmt.dto;

import java.util.UUID;

public record OrganizationDto(UUID id, String name, String sector, String notes) {
}
