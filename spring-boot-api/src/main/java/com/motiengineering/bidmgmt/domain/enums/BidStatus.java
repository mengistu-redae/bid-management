package com.motiengineering.bidmgmt.domain.enums;

/**
 * Identified -> Under Review (go/no-go) -> Preparing -> Submitted ->
 * Opened -> Under Evaluation -> Won/Lost, with Dropped (our decision) and
 * Cancelled (customer's decision) as side exits reachable from any
 * non-terminal status. See BidStatusService for the allowed-transition table.
 */
public enum BidStatus {
    IDENTIFIED,
    UNDER_REVIEW,
    PREPARING,
    SUBMITTED,
    OPENED,
    UNDER_EVALUATION,
    WON,
    LOST,
    DROPPED,
    CANCELLED;

    public boolean isTerminal() {
        return this == WON || this == LOST || this == DROPPED || this == CANCELLED;
    }
}
