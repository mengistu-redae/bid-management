package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.util.UUID;

/** A bid at Opened or Under Evaluation whose opening date has already passed with no lot outcome recorded yet. */
public record OpenNoResultRowDto(UUID bidId, String title, Instant openingAt) {
}
