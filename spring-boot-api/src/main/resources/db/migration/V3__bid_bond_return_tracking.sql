-- Minimal bid-bond-return tracking, pulled forward from phase 3's full Deal
-- Registration/Bid Bond entities because the phase-1 dashboard explicitly
-- needs "bid bonds ... not yet returned after the bid closed" as a metric.
-- Phase 3 can still promote this to a dedicated bid_bonds table (issuing
-- bank, issue date) later without touching this column.
ALTER TABLE bid_lots ADD COLUMN bid_bond_returned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bid_lots ADD COLUMN bid_bond_returned_at DATE;
