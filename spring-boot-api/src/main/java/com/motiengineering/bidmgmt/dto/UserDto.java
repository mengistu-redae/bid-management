package com.motiengineering.bidmgmt.dto;

import java.util.UUID;

public record UserDto(UUID id, String fullName, String email, String role, boolean hasLogin) {
}
