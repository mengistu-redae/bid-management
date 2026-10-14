package com.motiengineering.bidmgmt.domain.enums;

public enum ContractStatus {
    AWARDED,
    SIGNED,
    IN_DELIVERY,
    COMPLETED,
    TERMINATED;

    public boolean isTerminal() {
        return this == COMPLETED || this == TERMINATED;
    }
}
