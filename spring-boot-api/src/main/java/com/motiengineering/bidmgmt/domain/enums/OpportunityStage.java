package com.motiengineering.bidmgmt.domain.enums;

public enum OpportunityStage {
    LEAD,
    QUALIFIED,
    RFI_PROPOSAL,
    EXPECTING_TENDER,
    CONVERTED_TO_BID,
    LOST_CLOSED;

    public boolean isTerminal() {
        return this == CONVERTED_TO_BID || this == LOST_CLOSED;
    }
}
