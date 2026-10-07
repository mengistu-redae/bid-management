package com.motiengineering.bidmgmt.dto;

import java.time.LocalDate;

public record BondDetailsRequest(String issuingBank, LocalDate issueDate) {
}
