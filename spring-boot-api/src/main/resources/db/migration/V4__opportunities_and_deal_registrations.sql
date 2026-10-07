-- Phase 3: Opportunities, Deal Registrations, and finishing Bid Bond tracking
-- (phase 2 already added bid_lots.bid_bond_returned/_returned_at ahead of
-- schedule; this adds the two fields still missing - issuing bank and issue
-- date - rather than extracting a separate bid_bonds table, since every
-- lot has at most one bond today and the existing fields are already wired
-- through the importer, dashboard and tests).
ALTER TABLE bid_lots ADD COLUMN bid_bond_issuing_bank VARCHAR(255);
ALTER TABLE bid_lots ADD COLUMN bid_bond_issue_date DATE;

-- Shared OEM/vendor reference list - used by both Opportunities ("OEM/
-- vendors involved") and Deal Registrations, so the conflict check
-- ("another registration for the same customer + OEM") has something
-- precise to match on instead of free text.
CREATE TABLE oems (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name  VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE opportunities (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL REFERENCES organizations(id),
    title                VARCHAR(500) NOT NULL,
    estimated_value      NUMERIC(18, 2),
    estimated_value_currency VARCHAR(3) CHECK (estimated_value_currency IN ('ETB', 'USD')),
    expected_tender_date DATE,
    stage                VARCHAR(20) NOT NULL DEFAULT 'LEAD'
                           CHECK (stage IN ('LEAD', 'QUALIFIED', 'RFI_PROPOSAL', 'EXPECTING_TENDER', 'CONVERTED_TO_BID', 'LOST_CLOSED')),
    owner_id             UUID REFERENCES app_users(id),
    lost_reason          TEXT, -- populated when stage = LOST_CLOSED and nothing was converted
    converted_bid_id     UUID REFERENCES bids(id),
    notes                TEXT,
    created_by           UUID REFERENCES app_users(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_opportunities_organization ON opportunities(organization_id);
CREATE INDEX idx_opportunities_stage ON opportunities(stage);

CREATE TABLE opportunity_divisions (
    opportunity_id  UUID NOT NULL REFERENCES opportunities(id) ON DELETE CASCADE,
    division_id     UUID NOT NULL REFERENCES divisions(id),
    PRIMARY KEY (opportunity_id, division_id)
);

CREATE TABLE opportunity_oems (
    opportunity_id  UUID NOT NULL REFERENCES opportunities(id) ON DELETE CASCADE,
    oem_id          UUID NOT NULL REFERENCES oems(id),
    PRIMARY KEY (opportunity_id, oem_id)
);

-- A bid converted from an opportunity carries the link back; a bid created
-- directly (not from an opportunity) just has this NULL.
ALTER TABLE bids ADD COLUMN opportunity_id UUID REFERENCES opportunities(id);

CREATE TABLE deal_registrations (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    oem_id                    UUID NOT NULL REFERENCES oems(id),
    distributor               VARCHAR(255),
    registration_id           VARCHAR(255), -- the OEM/distributor portal's own reference
    organization_id           UUID NOT NULL REFERENCES organizations(id),
    opportunity_id            UUID REFERENCES opportunities(id),
    bid_id                    UUID REFERENCES bids(id),
    submitted_date            DATE,
    status                    VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                                CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'EXPIRED')),
    approval_date             DATE,
    expiry_date               DATE,
    protected_discount_percent NUMERIC(5, 2),
    special_price_reference   VARCHAR(255),
    notes                     TEXT,
    created_by                UUID REFERENCES app_users(id),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_deal_registrations_organization ON deal_registrations(organization_id);
CREATE INDEX idx_deal_registrations_oem ON deal_registrations(oem_id);
CREATE INDEX idx_deal_registrations_status ON deal_registrations(status);

INSERT INTO oems (name) VALUES
    ('Dell'), ('Lenovo'), ('Huawei'), ('Cisco'), ('Oracle'), ('Supermicro'),
    ('Microsoft'), ('Red Hat'), ('Broadcom/VMware'), ('Veeam'), ('Fortinet'), ('Kaspersky');
