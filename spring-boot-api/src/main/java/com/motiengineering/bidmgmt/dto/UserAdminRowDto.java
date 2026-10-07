package com.motiengineering.bidmgmt.dto;

import java.util.List;
import java.util.UUID;

/** Richer than UserDto (which is embedded all over bid/lot responses) - only used by the Director-only user management page. */
public record UserAdminRowDto(
        UUID id,
        String fullName,
        String email,
        String role,
        boolean hasLogin,
        boolean active,
        List<DivisionDto> divisions,
        String telegramChatId) {
}
