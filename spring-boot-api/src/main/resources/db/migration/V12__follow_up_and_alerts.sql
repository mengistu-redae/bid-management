-- ============================================================================
-- Phase 3: proactive follow-up (next follow-up date/owner, snooze) and the
-- alert-dedup state the 30-minute scheduler uses for "fire once per
-- threshold crossing, not repeatedly".
-- ============================================================================

CREATE TABLE follow_up_state (
    entity_type          VARCHAR(20) NOT NULL CHECK (entity_type IN ('OPPORTUNITY', 'DEAL_REGISTRATION', 'BID', 'LOT', 'CHECKLIST_ITEM', 'CONTRACT', 'LOGISTICS')),
    entity_id            UUID NOT NULL,
    next_follow_up_date  DATE,
    follow_up_owner_id   UUID REFERENCES app_users(id),
    snoozed_until        DATE,
    snooze_reason        TEXT,
    updated_by           UUID REFERENCES app_users(id),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (entity_type, entity_id)
);
CREATE INDEX idx_follow_up_state_owner ON follow_up_state(follow_up_owner_id);
CREATE INDEX idx_follow_up_state_due ON follow_up_state(next_follow_up_date);

CREATE TABLE alert_state (
    entity_type   VARCHAR(20) NOT NULL CHECK (entity_type IN ('OPPORTUNITY', 'DEAL_REGISTRATION', 'BID', 'LOT', 'CHECKLIST_ITEM', 'CONTRACT', 'LOGISTICS')),
    entity_id     UUID NOT NULL,
    alert_kind    VARCHAR(20) NOT NULL CHECK (alert_kind IN ('INDICATOR', 'STALE', 'FOLLOWUP_DUE')),
    context_key   VARCHAR(100) NOT NULL,
    last_level    VARCHAR(20),
    last_sent_at  TIMESTAMPTZ,
    PRIMARY KEY (entity_type, entity_id, alert_kind, context_key)
);

-- Per-user notification preference: amber alerts are digest-only by
-- default (opt in to immediate); red/critical are always immediate
-- regardless of this flag - see DelayAlertSchedulerService.
ALTER TABLE app_users ADD COLUMN amber_immediate BOOLEAN NOT NULL DEFAULT FALSE;
