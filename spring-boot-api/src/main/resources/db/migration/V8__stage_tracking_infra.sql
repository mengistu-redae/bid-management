-- ============================================================================
-- Stage time-tracking infrastructure: one consistent append-only history
-- for every stage/status/waiting-on transition across all tracked
-- entities, a hold-period table for the "pause the clock" feature, and the
-- business-day calendar (working week + holidays) that every elapsed-time
-- calculation reads. See V9 for the holiday seed data.
-- ============================================================================

CREATE TABLE stage_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type   VARCHAR(20) NOT NULL CHECK (entity_type IN ('OPPORTUNITY', 'DEAL_REGISTRATION', 'BID', 'LOT', 'CHECKLIST_ITEM', 'CONTRACT', 'LOGISTICS')),
    entity_id     UUID NOT NULL,
    stage_field   VARCHAR(40) NOT NULL,
    from_value    VARCHAR(100),
    to_value      VARCHAR(100),
    note          TEXT,
    changed_by    UUID REFERENCES app_users(id),
    changed_at    TIMESTAMPTZ NOT NULL,
    backfilled    BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_stage_history_entity ON stage_history(entity_type, entity_id, changed_at);

CREATE TABLE hold_periods (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type  VARCHAR(20) NOT NULL CHECK (entity_type IN ('OPPORTUNITY', 'DEAL_REGISTRATION', 'BID', 'LOT', 'CHECKLIST_ITEM', 'CONTRACT', 'LOGISTICS')),
    entity_id    UUID NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at     TIMESTAMPTZ,
    reason       TEXT NOT NULL,
    started_by   UUID REFERENCES app_users(id),
    ended_by     UUID REFERENCES app_users(id)
);
CREATE INDEX idx_hold_periods_entity ON hold_periods(entity_type, entity_id);
-- At most one open hold per entity at a time.
CREATE UNIQUE INDEX idx_hold_periods_one_open ON hold_periods(entity_type, entity_id) WHERE ended_at IS NULL;

CREATE TABLE holiday_calendar (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_date  DATE NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Single configurable row (id pinned to 1) - Mon-Sat per the current answer; admin-editable afterward.
CREATE TABLE working_week_config (
    id            INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    working_days  VARCHAR(40) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI,SAT',
    updated_by    UUID REFERENCES app_users(id),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO working_week_config (id, working_days) VALUES (1, 'MON,TUE,WED,THU,FRI,SAT');
