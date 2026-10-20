-- ============================================================================
-- Issues - post-sale/operational problem tracking (replaces the manually
-- maintained "Issues 2026.xlsx"). Two categories share one table:
--   OPERATIONAL       - per-customer/item problems (the dated weekly sheet)
--   CONTRACT_CLOSURE  - contracts stuck unclosed, impacting profitability
--                        (the "Project issuse" sheet)
-- Deliberately NOT wired into stage_history/stage_targets/alert_state/
-- waiting_on_state/hold_periods - those drive the sales-pipeline's
-- target-duration SLA math (DelaysBoardService), which doesn't fit an issue
-- log. Issue aging is just "days since opened_at", computed in Java.
-- ============================================================================

CREATE TABLE issues (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category                    VARCHAR(20) NOT NULL CHECK (category IN ('OPERATIONAL', 'CONTRACT_CLOSURE')),
    status                      VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                                    CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING_ON_CUSTOMER', 'WAITING_ON_VENDOR', 'BLOCKED', 'RESOLVED', 'CLOSED')),
    severity                    VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    organization_id             UUID NOT NULL REFERENCES organizations(id),
    division_id                 UUID REFERENCES divisions(id),
    item_or_system              VARCHAR(255),
    description                 TEXT NOT NULL,
    current_status_note         TEXT,
    next_action                 TEXT,
    challenge                   TEXT,
    bid_id                      UUID REFERENCES bids(id),
    contract_id                 UUID REFERENCES contracts(id),
    opportunity_id              UUID REFERENCES opportunities(id),
    financial_impact            NUMERIC(18, 2),
    financial_impact_currency   VARCHAR(3) CHECK (financial_impact_currency IN ('ETB', 'USD')),
    opened_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at                 TIMESTAMPTZ,
    resolution_notes            TEXT,
    assigned_to                 UUID REFERENCES app_users(id),
    created_by                  UUID REFERENCES app_users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_issues_organization ON issues(organization_id);
CREATE INDEX idx_issues_division ON issues(division_id);
CREATE INDEX idx_issues_status ON issues(status);
CREATE INDEX idx_issues_assigned_to ON issues(assigned_to);

-- Issue participates in the same generic activity-log/attachments/follow-up
-- infrastructure the rest of the app uses - widen the sibling tables it
-- actually writes to (not the stage-tracking/indicator ones, see above).
ALTER TABLE activity_log DROP CONSTRAINT activity_log_entity_type_check;
ALTER TABLE activity_log ADD CONSTRAINT activity_log_entity_type_check
    CHECK (entity_type IN ('OPPORTUNITY', 'DEAL_REGISTRATION', 'BID', 'LOT', 'CHECKLIST_ITEM', 'CONTRACT', 'LOGISTICS', 'ISSUE'));

ALTER TABLE attachments DROP CONSTRAINT attachments_entity_type_check;
ALTER TABLE attachments ADD CONSTRAINT attachments_entity_type_check
    CHECK (entity_type IN ('OPPORTUNITY', 'DEAL_REGISTRATION', 'BID', 'LOT', 'CHECKLIST_ITEM', 'CONTRACT', 'LOGISTICS', 'ISSUE'));

ALTER TABLE follow_up_state DROP CONSTRAINT follow_up_state_entity_type_check;
ALTER TABLE follow_up_state ADD CONSTRAINT follow_up_state_entity_type_check
    CHECK (entity_type IN ('OPPORTUNITY', 'DEAL_REGISTRATION', 'BID', 'LOT', 'CHECKLIST_ITEM', 'CONTRACT', 'LOGISTICS', 'ISSUE'));

-- The new importer logs its runs the same way the two existing importers do.
ALTER TABLE import_batches DROP CONSTRAINT import_batches_kind_check;
ALTER TABLE import_batches ADD CONSTRAINT import_batches_kind_check
    CHECK (kind IN ('UPCOMING_BIDS', 'BIDS_TRACKER', 'ISSUES'));
