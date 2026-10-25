-- ============================================================================
-- Dashboard KPI snapshots - one row captured per calendar day by
-- DashboardKpiSnapshotJob, storing the same 6 numbers the Overview tab's
-- stat cards already compute live. Lets the dashboard show a genuine
-- "vs last week" trend once history accumulates, instead of a fabricated
-- percentage with nothing real behind it.
-- ============================================================================

CREATE TABLE dashboard_kpi_snapshot (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_date               DATE NOT NULL UNIQUE,
    closing_soon_count          INT NOT NULL,
    missing_readiness_count     INT NOT NULL,
    behind_stage_target_count   INT NOT NULL,
    open_pipeline_etb           NUMERIC(18, 2) NOT NULL,
    open_pipeline_usd           NUMERIC(18, 2) NOT NULL,
    bid_bonds_outstanding_etb   NUMERIC(18, 2) NOT NULL,
    deal_regs_expiring_count    INT NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
