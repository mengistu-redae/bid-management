# Changelog

## Unreleased

### Stage time tracking, delay indicators & proactive follow-up - Phase 1

- Added minimal `Contract` and `Logistics` entities (the lifecycle
  previously stopped at a won Bid) and a generic `stage_milestones` table
  for acceptance/payment/shipment milestones.
- Added `stage_history`, one polymorphic append-only table recording every
  status/stage/waiting-on/hold transition across Opportunity,
  DealRegistration, Bid, Lot, ChecklistItem, Contract and Logistics.
- Added `hold_periods` (pause-the-clock support with a required reason),
  `holiday_calendar` (seeded with best-effort 2026 Ethiopian public
  holidays - movable Easter/Islamic-calendar dates flagged "estimated,
  verify") and `working_week_config` (Mon-Sat by default, admin-editable).
- Extended key-date change tracking (reusing the existing `audit_log`
  table) to Bid's `openingAt`/`clarificationDeadline`, Opportunity's
  `expectedTenderDate`, and DealRegistration's `submittedDate`/
  `approvalDate`/`expiryDate`.
- Added `StageHistoryBackfillRunner`, an idempotent startup job that
  replays real history for existing Bids/Lots and seeds a synthetic entry
  for Opportunities/DealRegistrations still in their very first stage,
  leaving anything else unseeded ("unknown") rather than fabricating zero.
- New REST endpoints: `/api/contracts`, `/api/logistics`,
  `/api/stage-history`, `/api/stage-clock`, `/api/holds`.
- No UI yet (indicators, the Deal 360 timeline, and the admin screens for
  targets/holidays/working week all land in phase 2).

### Stage time tracking, delay indicators & proactive follow-up - Phase 2

- Added `stage_targets` (duration and deadline-relative targets per entity
  type/stage, with division/sector/OEM/shipment-mode overrides) seeded
  with the confirmed default list, and `indicator_config` (global,
  admin-editable Green/Amber/Red/Critical thresholds and default
  stale-after-days).
- Added `IndicatorService`, computing a GREEN/AMBER/RED/CRITICAL/UNKNOWN/
  NO_TARGET indicator plus a stale flag for any tracked entity - now
  embedded in every relevant DTO (Bid's lots, rolled up to the Bid as the
  worst level; Opportunity; DealRegistration; Contract; Logistics) and
  shown as a badge on the bid list, Kanban, bid detail, Opportunities,
  Deal Registrations, and a new dashboard "Delays" section.
- Added `waiting_on_state` (current value) + delay reason codes
  (`delay_reason_codes`, admin-editable, reusing `stage_history` for the
  change log). A delay reason is now required server-side when moving a
  `BidLot` out of a stage that's currently red/critical.
- Added the Deal 360 page (`/bids/:id/deal360`): the full chain
  (Opportunity -> Deal Registrations -> Bid Lots -> Contract ->
  Logistics), assembled on the fly from existing FK links, each row its
  own proportional stage-history timeline with over-target segments
  highlighted.
- New REST endpoints: `/api/stage-targets`, `/api/indicator-config`,
  `/api/delay-reason-codes`, `/api/waiting-on`.
- Fixed a `DateTimeException` overflow (`Instant.MAX` used as a "no
  deadline" sentinel broke date arithmetic on deadline-relative targets)
  found live while verifying the dashboard against real data.
