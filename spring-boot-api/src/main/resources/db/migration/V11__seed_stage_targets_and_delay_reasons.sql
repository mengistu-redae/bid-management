-- Default target list confirmed with the user before seeding (based on
-- typical Ethiopian tender/OEM/import timelines) - all admin-editable
-- afterward via /api/stage-targets. Targets apply at LOT level for the Bid
-- pipeline (not BID), per the confirmed "track at lot level" decision -
-- Bid-level display rolls up the worst indicator across its lots.

-- Opportunity
INSERT INTO stage_targets (entity_type, stage_value, target_type, target_days, uses_business_days) VALUES
    ('OPPORTUNITY', 'LEAD', 'DURATION', 7, TRUE),
    ('OPPORTUNITY', 'QUALIFIED', 'DURATION', 10, TRUE),
    ('OPPORTUNITY', 'RFI_PROPOSAL', 'DURATION', 15, TRUE);

-- Deal Registration
INSERT INTO stage_targets (entity_type, stage_value, target_type, target_days, uses_business_days) VALUES
    ('DEAL_REGISTRATION', 'DRAFT', 'DURATION', 3, TRUE),
    ('DEAL_REGISTRATION', 'SUBMITTED', 'DURATION', 5, TRUE);

-- Bid Lot
INSERT INTO stage_targets (entity_type, stage_value, target_type, target_days, uses_business_days) VALUES
    ('LOT', 'IDENTIFIED', 'DURATION', 2, TRUE),
    ('LOT', 'UNDER_REVIEW', 'DURATION', 2, TRUE),
    ('LOT', 'OPENED', 'DURATION', 3, TRUE),
    ('LOT', 'UNDER_EVALUATION', 'DURATION', 20, TRUE);
INSERT INTO stage_targets (entity_type, stage_value, target_type, deadline_field, deadline_offset_days, uses_business_days, notes) VALUES
    ('LOT', 'PREPARING', 'DEADLINE_RELATIVE', 'closingAt', 1, TRUE, 'Must finish preparing at least 1 business day before the bid closes');

-- Contract
INSERT INTO stage_targets (entity_type, stage_value, target_type, target_days, uses_business_days) VALUES
    ('CONTRACT', 'AWARDED', 'DURATION', 15, TRUE),
    ('CONTRACT', 'SIGNED', 'DURATION', 10, TRUE);
INSERT INTO stage_targets (entity_type, stage_value, target_type, deadline_field, deadline_offset_days, uses_business_days, notes) VALUES
    ('CONTRACT', 'IN_DELIVERY', 'DEADLINE_RELATIVE', 'deliveryDeadline', 0, TRUE, 'Must complete by the contract delivery deadline');

-- Logistics - global defaults, then narrower per-mode overrides for DISPATCHED (transit time varies a lot by mode)
INSERT INTO stage_targets (entity_type, stage_value, target_type, target_days, uses_business_days) VALUES
    ('LOGISTICS', 'PLANNING', 'DURATION', 5, TRUE),
    ('LOGISTICS', 'DISPATCHED', 'DURATION', 10, TRUE),
    ('LOGISTICS', 'CUSTOMS', 'DURATION', 10, TRUE),
    ('LOGISTICS', 'CLEARED', 'DURATION', 3, TRUE);
INSERT INTO stage_targets (entity_type, stage_value, target_type, target_days, uses_business_days, shipment_mode, notes) VALUES
    ('LOGISTICS', 'DISPATCHED', 'DURATION', 5, TRUE, 'AIR', 'Air freight transit time'),
    ('LOGISTICS', 'DISPATCHED', 'DURATION', 35, FALSE, 'SEA', 'Sea freight transit time - calendar days'),
    ('LOGISTICS', 'DISPATCHED', 'DURATION', 10, TRUE, 'LAND', 'Land freight transit time');

INSERT INTO delay_reason_codes (code, label, sort_order) VALUES
    ('WAITING_OEM_MAF', 'Waiting OEM MAF', 10),
    ('PRICING_APPROVAL_PENDING', 'Pricing approval pending', 20),
    ('CUSTOMER_CLARIFICATION_PENDING', 'Customer clarification pending', 30),
    ('FOREX_APPROVAL_DELAY', 'Forex approval delay', 40),
    ('LC_DELAY', 'LC delay', 50),
    ('SHIPPING_DELAY', 'Shipping delay', 60),
    ('CUSTOMS_HOLD', 'Customs hold', 70),
    ('INTERNAL_RESOURCE', 'Internal resource constraint', 80),
    ('OTHER', 'Other', 999);
