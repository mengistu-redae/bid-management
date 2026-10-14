package com.motiengineering.bidmgmt.dto;

/**
 * One division filter pill. filterParam is the exact value to send back as
 * the dashboard's ?division= query param: null for "All" (no filter), the
 * literal "none" for the no-division bucket, or a division's UUID string.
 * closingSoonCount always reflects the full, unfiltered bid set regardless
 * of which pill is currently applied, so the pills stay stable reference
 * counts rather than changing as the user clicks between them.
 */
public record DivisionPillDto(String filterParam, String label, int closingSoonCount) {
}
