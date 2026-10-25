package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.util.UUID;

/** One entry in the dashboard's "Recent activity" feed - merged from bid_status_history and activity_log (BID entity type only). */
public record RecentActivityRowDto(UUID bidId, String bidTitle, String organizationName, String message, String authorName, Instant occurredAt) {
}
