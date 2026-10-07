package com.motiengineering.bidmgmt.dto;

public record StatusChangeDigestRowDto(String bidTitle, String fromStatus, String toStatus, String changedByName) {
}
