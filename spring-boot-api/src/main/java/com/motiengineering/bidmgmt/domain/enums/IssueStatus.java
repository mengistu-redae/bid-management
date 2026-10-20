package com.motiengineering.bidmgmt.domain.enums;

public enum IssueStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_ON_CUSTOMER,
    WAITING_ON_VENDOR,
    BLOCKED,
    RESOLVED,
    CLOSED;

    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED;
    }
}
