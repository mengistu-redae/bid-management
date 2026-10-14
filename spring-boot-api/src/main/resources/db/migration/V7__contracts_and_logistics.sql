-- ============================================================================
-- Contract and Logistics - the deal lifecycle stages after a bid is WON.
-- Deliberately minimal (status + key dates); acceptance steps, payment
-- milestones and shipment milestones are generic stage_milestones rows
-- (entity_type CONTRACT/LOGISTICS) rather than their own tables, mirroring
-- checklist_items one level up. There is no explicit "Deal" table - a
-- Contract's deal is reached by following bid_id -> bids.opportunity_id.
-- ============================================================================

-- One contract per won bid; a bid can only be awarded once, so bid_id is unique.
CREATE TABLE contracts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bid_id             UUID NOT NULL UNIQUE REFERENCES bids(id),
    contract_number    VARCHAR(255),
    status             VARCHAR(20) NOT NULL DEFAULT 'AWARDED' CHECK (status IN ('AWARDED', 'SIGNED', 'IN_DELIVERY', 'COMPLETED', 'TERMINATED')),
    signed_at          TIMESTAMPTZ,
    delivery_deadline  DATE,
    completed_at       TIMESTAMPTZ,
    exit_reason        TEXT,
    notes              TEXT,
    created_by         UUID REFERENCES app_users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A contract can ship in more than one consignment (partial deliveries), so this is a plain FK, not 1:1.
CREATE TABLE logistics_shipments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id         UUID NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    shipment_mode       VARCHAR(10) NOT NULL DEFAULT 'SEA' CHECK (shipment_mode IN ('AIR', 'SEA', 'LAND')),
    status              VARCHAR(20) NOT NULL DEFAULT 'PLANNING' CHECK (status IN ('PLANNING', 'DISPATCHED', 'IN_TRANSIT', 'CUSTOMS', 'CLEARED', 'DELIVERED')),
    dispatched_at       TIMESTAMPTZ,
    eta_at              TIMESTAMPTZ,
    arrived_at          TIMESTAMPTZ,
    customs_cleared_at  TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    notes               TEXT,
    created_by          UUID REFERENCES app_users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_logistics_shipments_contract ON logistics_shipments(contract_id);

CREATE TABLE stage_milestones (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type   VARCHAR(20) NOT NULL CHECK (entity_type IN ('CONTRACT', 'LOGISTICS')),
    entity_id     UUID NOT NULL,
    category      VARCHAR(20) NOT NULL CHECK (category IN ('ACCEPTANCE', 'PAYMENT', 'SHIPMENT')),
    title         VARCHAR(255) NOT NULL,
    planned_date  DATE,
    actual_date   DATE,
    amount        NUMERIC(18, 2),
    done          BOOLEAN NOT NULL DEFAULT FALSE,
    done_by       UUID REFERENCES app_users(id),
    done_at       TIMESTAMPTZ,
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_stage_milestones_entity ON stage_milestones(entity_type, entity_id);
