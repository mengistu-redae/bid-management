package com.motiengineering.bidmgmt.domain.enums;

public enum LogisticsStatus {
    PLANNING,
    DISPATCHED,
    IN_TRANSIT,
    CUSTOMS,
    CLEARED,
    DELIVERED;

    public boolean isTerminal() {
        return this == DELIVERED;
    }
}
