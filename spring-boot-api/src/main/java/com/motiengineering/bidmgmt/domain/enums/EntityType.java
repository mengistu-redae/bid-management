package com.motiengineering.bidmgmt.domain.enums;

/**
 * Discriminator for the polymorphic activity_log, attachments and audit_log
 * tables, and (as of stage-tracking) stage_history, hold_periods and
 * stage_milestones too.
 */
public enum EntityType {
    BID,
    LOT,
    OPPORTUNITY,
    DEAL_REGISTRATION,
    CHECKLIST_ITEM,
    CONTRACT,
    LOGISTICS
}
