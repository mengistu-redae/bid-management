package com.motiengineering.bidmgmt.domain.enums;

/** Discriminator for the polymorphic activity_log, attachments and audit_log tables. */
public enum EntityType {
    BID,
    LOT,
    OPPORTUNITY,
    DEAL_REGISTRATION
}
