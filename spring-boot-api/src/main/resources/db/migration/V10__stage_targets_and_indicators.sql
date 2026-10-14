-- ============================================================================
-- Phase 2: stage targets ("what does late mean"), indicator thresholds,
-- waiting-on tracking, and delay reason codes.
-- ============================================================================

CREATE TABLE stage_targets (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type            VARCHAR(20) NOT NULL CHECK (entity_type IN ('OPPORTUNITY', 'DEAL_REGISTRATION', 'BID', 'LOT', 'CHECKLIST_ITEM', 'CONTRACT', 'LOGISTICS')),
    stage_value            VARCHAR(40) NOT NULL,
    target_type            VARCHAR(20) NOT NULL CHECK (target_type IN ('DURATION', 'DEADLINE_RELATIVE')),
    target_days            NUMERIC(6, 1),
    uses_business_days     BOOLEAN NOT NULL DEFAULT TRUE,
    deadline_field         VARCHAR(40),
    deadline_offset_days   NUMERIC(6, 1),
    division_id            UUID REFERENCES divisions(id),
    sector                 VARCHAR(20),
    oem_name                VARCHAR(255),
    shipment_mode           VARCHAR(10),
    stale_after_days        NUMERIC(6, 1),
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_stage_targets_lookup ON stage_targets(entity_type, stage_value, active);

-- Single configurable row (id pinned to 1), matching working_week_config's pattern.
CREATE TABLE indicator_config (
    id                              INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    amber_threshold_percent         NUMERIC(5, 1) NOT NULL DEFAULT 75,
    red_threshold_percent           NUMERIC(5, 1) NOT NULL DEFAULT 100,
    critical_threshold_percent      NUMERIC(5, 1) NOT NULL DEFAULT 150,
    default_stale_after_days        NUMERIC(6, 1) NOT NULL DEFAULT 5,
    updated_by                      UUID REFERENCES app_users(id),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO indicator_config (id) VALUES (1);

-- Current "waiting on" per entity - full change history lives in stage_history (stage_field = 'waitingOn').
CREATE TABLE waiting_on_state (
    entity_type  VARCHAR(20) NOT NULL CHECK (entity_type IN ('OPPORTUNITY', 'DEAL_REGISTRATION', 'BID', 'LOT', 'CHECKLIST_ITEM', 'CONTRACT', 'LOGISTICS')),
    entity_id    UUID NOT NULL,
    party_type   VARCHAR(30) NOT NULL CHECK (party_type IN ('INTERNAL', 'CUSTOMER', 'OEM', 'DISTRIBUTOR', 'IMPLEMENTATION_PARTNER', 'BANK', 'FREIGHT_FORWARDER_CUSTOMS', 'OTHER')),
    person_id    UUID REFERENCES app_users(id),
    note         TEXT,
    updated_by   UUID REFERENCES app_users(id),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (entity_type, entity_id)
);

CREATE TABLE delay_reason_codes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(60) NOT NULL UNIQUE,
    label       VARCHAR(255) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order  INT NOT NULL DEFAULT 0
);

ALTER TABLE stage_history ADD COLUMN delay_reason_code VARCHAR(60);
