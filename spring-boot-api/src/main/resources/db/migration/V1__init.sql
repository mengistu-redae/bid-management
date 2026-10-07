CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- Reference data: divisions, users, organizations
-- ============================================================================

CREATE TABLE divisions (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code  VARCHAR(40) NOT NULL UNIQUE,
    name  VARCHAR(120) NOT NULL
);

-- keycloak_user_id is nullable: account officers/scouts imported from the
-- spreadsheets exist here as plain reference rows (for filtering, "scouted
-- by", "account officer") before they ever get a real login. The first time
-- someone with a matching email logs in, CurrentUserService backfills this
-- column onto the existing row instead of creating a duplicate user.
CREATE TABLE app_users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id  VARCHAR(64) UNIQUE,
    full_name         VARCHAR(255) NOT NULL,
    email             VARCHAR(255) UNIQUE,
    role              VARCHAR(30) NOT NULL CHECK (role IN ('DIRECTOR', 'DIVISION_MANAGER', 'ACCOUNT_OFFICER', 'SCOUT')),
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_divisions (
    user_id      UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    division_id  UUID NOT NULL REFERENCES divisions(id) ON DELETE CASCADE,
    is_manager   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, division_id)
);

CREATE TABLE organizations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    sector      VARCHAR(20) NOT NULL DEFAULT 'OTHER' CHECK (sector IN ('BANK', 'GOVERNMENT', 'UTILITY', 'ENTERPRISE', 'OTHER')),
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Known spelling variants of one organization (e.g. "CBE" / "Commercial Bank
-- of Ethiopia" / "Commercial bank of ethiopia") so the importer and intake
-- form can resolve free-text org names to one canonical row.
CREATE TABLE organization_aliases (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id    UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    alias              VARCHAR(255) NOT NULL,
    alias_normalized    VARCHAR(255) GENERATED ALWAYS AS (lower(trim(alias))) STORED
);
CREATE UNIQUE INDEX idx_org_aliases_normalized ON organization_aliases(alias_normalized);

CREATE TABLE contacts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    full_name        VARCHAR(255) NOT NULL,
    title            VARCHAR(120),
    phone            VARCHAR(40),
    email            VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_contacts_organization ON contacts(organization_id);

-- ============================================================================
-- Bids (tenders), lots, checklist
-- ============================================================================

-- opportunity_id is added by a phase-3 migration once the opportunities
-- table exists - a bid created directly (not converted from an opportunity)
-- simply has it NULL.
CREATE TABLE bids (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL REFERENCES organizations(id),
    title                   VARCHAR(500) NOT NULL,
    reference_number        VARCHAR(255),
    source                  VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' CHECK (source IN ('NEWSPAPER', 'PORTAL', 'INVITATION', 'DIRECT', 'UNKNOWN')),
    scouted_date            DATE,
    closing_at              TIMESTAMPTZ,
    opening_at              TIMESTAMPTZ,
    clarification_deadline  TIMESTAMPTZ,
    bid_validity_days       INT,
    status                  VARCHAR(20) NOT NULL DEFAULT 'IDENTIFIED'
                              CHECK (status IN ('IDENTIFIED', 'UNDER_REVIEW', 'PREPARING', 'SUBMITTED', 'OPENED', 'UNDER_EVALUATION', 'WON', 'LOST', 'DROPPED', 'CANCELLED')),
    go_no_go_decision       VARCHAR(10) CHECK (go_no_go_decision IN ('GO', 'NO_GO')),
    go_no_go_reason         TEXT,
    exit_reason             TEXT, -- populated when status is DROPPED or CANCELLED
    notes                   TEXT,
    created_by              UUID REFERENCES app_users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_bids_organization ON bids(organization_id);
CREATE INDEX idx_bids_status ON bids(status);
CREATE INDEX idx_bids_closing_at ON bids(closing_at);
CREATE INDEX idx_bids_reference_number ON bids(reference_number);

CREATE TABLE bid_scouts (
    bid_id   UUID NOT NULL REFERENCES bids(id) ON DELETE CASCADE,
    user_id  UUID NOT NULL REFERENCES app_users(id),
    PRIMARY KEY (bid_id, user_id)
);

-- Every bid has at least one lot, even when the tender itself has no formal
-- lot structure (a "single-lot" bid is just a bid with exactly one Lot row).
-- This lets a tender that genuinely has multiple lots - each with its own
-- value, bond, division and account officer - share one Bid (one closing
-- date, one submission, one overall status/status history) while each lot
-- tracks its own prep checklist, value and award outcome independently.
CREATE TABLE bid_lots (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bid_id                    UUID NOT NULL REFERENCES bids(id) ON DELETE CASCADE,
    lot_label                 VARCHAR(60) NOT NULL DEFAULT 'Lot 1',
    description               VARCHAR(1000),
    division_id               UUID REFERENCES divisions(id),
    estimated_value           NUMERIC(18, 2),
    estimated_value_currency  VARCHAR(3) CHECK (estimated_value_currency IN ('ETB', 'USD')),
    bid_bond_amount           NUMERIC(18, 2),
    bid_bond_currency         VARCHAR(3) CHECK (bid_bond_currency IN ('ETB', 'USD')),
    bid_bond_validity_days    INT,
    bid_bond_form             VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' CHECK (bid_bond_form IN ('BANK_GUARANTEE', 'CPO', 'INSURANCE_BOND', 'UNKNOWN')),
    oem_brand                 VARCHAR(255),
    -- Defaults to the parent bid's status and is kept in sync by
    -- BidStatusService whenever the bid-level transition endpoint is used;
    -- it only diverges when a lot is individually dropped/won/lost while
    -- its siblings continue (BidWonBank's two-lot submission is the real
    -- example this covers).
    status                    VARCHAR(20) NOT NULL DEFAULT 'IDENTIFIED'
                                CHECK (status IN ('IDENTIFIED', 'UNDER_REVIEW', 'PREPARING', 'SUBMITTED', 'OPENED', 'UNDER_EVALUATION', 'WON', 'LOST', 'DROPPED', 'CANCELLED')),
    exit_reason               TEXT,
    outcome                   VARCHAR(10) CHECK (outcome IN ('WON', 'LOST')),
    winner_name               VARCHAR(255),
    winning_price             NUMERIC(18, 2),
    winning_price_currency    VARCHAR(3) CHECK (winning_price_currency IN ('ETB', 'USD')),
    needs_review              BOOLEAN NOT NULL DEFAULT FALSE, -- set by the importer when a value/amount could not be parsed confidently
    review_note               TEXT,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_bid_lots_bid ON bid_lots(bid_id);
CREATE INDEX idx_bid_lots_division ON bid_lots(division_id);
CREATE INDEX idx_bid_lots_status ON bid_lots(status);

CREATE TABLE lot_account_officers (
    lot_id   UUID NOT NULL REFERENCES bid_lots(id) ON DELETE CASCADE,
    user_id  UUID NOT NULL REFERENCES app_users(id),
    PRIMARY KEY (lot_id, user_id)
);

CREATE TABLE lot_scope_types (
    lot_id      UUID NOT NULL REFERENCES bid_lots(id) ON DELETE CASCADE,
    scope_type  VARCHAR(20) NOT NULL CHECK (scope_type IN ('DELIVERY', 'IMPLEMENTATION', 'TRAINING', 'SUPPORT_RENEWAL')),
    PRIMARY KEY (lot_id, scope_type)
);

CREATE TABLE checklist_item_templates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE checklist_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lot_id       UUID NOT NULL REFERENCES bid_lots(id) ON DELETE CASCADE,
    template_id  UUID REFERENCES checklist_item_templates(id),
    title        VARCHAR(255) NOT NULL,
    owner_id     UUID REFERENCES app_users(id),
    due_date     DATE,
    done         BOOLEAN NOT NULL DEFAULT FALSE,
    done_at      TIMESTAMPTZ,
    done_by      UUID REFERENCES app_users(id),
    sort_order   INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_checklist_items_lot ON checklist_items(lot_id);

-- Exactly one of bid_id/lot_id is set per row - a bid-level transition
-- (e.g. SUBMITTED) logs once against the bid; a lot diverging afterwards
-- (e.g. one lot DROPPED while the bid stays PREPARING) logs against that lot.
CREATE TABLE bid_status_history (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bid_id       UUID REFERENCES bids(id) ON DELETE CASCADE,
    lot_id       UUID REFERENCES bid_lots(id) ON DELETE CASCADE,
    from_status  VARCHAR(20),
    to_status    VARCHAR(20) NOT NULL,
    reason       TEXT,
    changed_by   UUID REFERENCES app_users(id),
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((bid_id IS NOT NULL) <> (lot_id IS NOT NULL))
);
CREATE INDEX idx_bid_status_history_bid ON bid_status_history(bid_id);
CREATE INDEX idx_bid_status_history_lot ON bid_status_history(lot_id);

-- ============================================================================
-- Activity log, attachments, audit log - polymorphic, reused by opportunities
-- and deal registrations once phase 3 adds those entity_type values.
-- ============================================================================

CREATE TABLE activity_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type  VARCHAR(20) NOT NULL CHECK (entity_type IN ('BID', 'LOT', 'OPPORTUNITY', 'DEAL_REGISTRATION')),
    entity_id    UUID NOT NULL,
    author_id    UUID REFERENCES app_users(id),
    note         TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_activity_log_entity ON activity_log(entity_type, entity_id);

CREATE TABLE attachments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type   VARCHAR(20) NOT NULL CHECK (entity_type IN ('BID', 'LOT', 'OPPORTUNITY', 'DEAL_REGISTRATION')),
    entity_id     UUID NOT NULL,
    filename      VARCHAR(500) NOT NULL,
    content_type  VARCHAR(255),
    size_bytes     BIGINT,
    storage_path  VARCHAR(1000) NOT NULL,
    uploaded_by   UUID REFERENCES app_users(id),
    uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attachments_entity ON attachments(entity_type, entity_id);

CREATE TABLE audit_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type  VARCHAR(30) NOT NULL,
    entity_id    UUID NOT NULL,
    field_name   VARCHAR(100) NOT NULL,
    old_value    TEXT,
    new_value    TEXT,
    changed_by   UUID REFERENCES app_users(id),
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);

-- ============================================================================
-- Excel import traceability
-- ============================================================================

CREATE TABLE import_batches (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind           VARCHAR(30) NOT NULL CHECK (kind IN ('UPCOMING_BIDS', 'BIDS_TRACKER')),
    source_filename VARCHAR(500) NOT NULL,
    imported_by    UUID REFERENCES app_users(id),
    imported_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    rows_total     INT NOT NULL DEFAULT 0,
    rows_created   INT NOT NULL DEFAULT 0,
    rows_merged    INT NOT NULL DEFAULT 0,
    rows_flagged   INT NOT NULL DEFAULT 0,
    rows_skipped   INT NOT NULL DEFAULT 0
);
