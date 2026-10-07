package com.motiengineering.bidmgmt.importer;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds a parsed-but-not-yet-saved import batch between the preview call and
 * the confirm call, keyed by a one-time token. In-memory is a deliberate
 * simplification: this app runs as a single instance on one on-prem server
 * (see the brief's deployment requirement), so there's no second node that
 * could miss this state, and a preview nobody confirms just ages out.
 */
@Component
public class ImportPreviewCache {

    private static final long TTL_MILLIS = 30 * 60 * 1000L;

    public record UpcomingBatch(String sourceFilename, List<UpcomingBidRow> rows, Instant createdAt) {
    }

    public record TrackerBatch(String sourceFilename, Map<String, List<BidsTrackerRow>> rowsBySheet, Instant createdAt) {
    }

    private final Map<UUID, UpcomingBatch> upcomingBatches = new ConcurrentHashMap<>();
    private final Map<UUID, TrackerBatch> trackerBatches = new ConcurrentHashMap<>();

    public UUID putUpcoming(String filename, List<UpcomingBidRow> rows) {
        UUID token = UUID.randomUUID();
        upcomingBatches.put(token, new UpcomingBatch(filename, rows, Instant.now()));
        return token;
    }

    public UpcomingBatch takeUpcoming(UUID token) {
        evictExpired();
        return upcomingBatches.remove(token);
    }

    public UUID putTracker(String filename, Map<String, List<BidsTrackerRow>> rowsBySheet) {
        UUID token = UUID.randomUUID();
        trackerBatches.put(token, new TrackerBatch(filename, rowsBySheet, Instant.now()));
        return token;
    }

    public TrackerBatch takeTracker(UUID token) {
        evictExpired();
        return trackerBatches.remove(token);
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minusMillis(TTL_MILLIS);
        upcomingBatches.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
        trackerBatches.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }
}
