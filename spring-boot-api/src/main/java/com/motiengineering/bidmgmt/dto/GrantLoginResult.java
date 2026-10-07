package com.motiengineering.bidmgmt.dto;

/** temporaryPassword is returned exactly once here and never persisted - the Director hands it to the person out of band. */
public record GrantLoginResult(UserAdminRowDto user, String temporaryPassword) {
}
