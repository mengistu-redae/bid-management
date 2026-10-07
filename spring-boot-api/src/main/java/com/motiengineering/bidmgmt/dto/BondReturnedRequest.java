package com.motiengineering.bidmgmt.dto;

import java.time.LocalDate;

public record BondReturnedRequest(boolean returned, LocalDate returnedAt) {
}
