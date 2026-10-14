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

### Stage time tracking, delay indicators & proactive follow-up - Phase 3

- Added `follow_up_state` (next follow-up date/owner, snooze with a
  required reason and date) and `alert_state` (single-fire-per-threshold-
  crossing dedup for the new alert scheduler).
- Added `DelayAlertSchedulerService` (every 30 minutes), escalating
  Amber -> owner, Red -> +division manager, Critical -> +Director, plus
  stale (escalating to the manager at 2x the stale limit) and follow-up-
  due alerts. A new per-user `amber_immediate` preference (editable on
  the Users page) makes amber alerts digest-only by default; red/
  critical are always immediate.
- Added `DailyDigestSchedulerService` (per-user: due follow-ups, newly
  amber/red items, stale items; Director also gets a critical count) and
  `WeeklyBottleneckSchedulerService` (Monday mornings: top 10 delayed
  records, red/critical counts, open items by waiting-on party - to the
  Director and division managers).
- Added the Delays board (`/delays`) - every open lot currently amber/
  red/critical/stale, filterable and sortable, with one-step follow-up/
  snooze logging - and My Follow-ups (`/my-follow-ups`) - overdue, due
  today, turning-amber-soon, and stale items relevant to the current
  user.
- New REST endpoints: `/api/follow-up`, `/api/follow-up/snooze`,
  `/api/my-follow-ups`, `/api/delays-board`.
- Deliberately deferred: key-date-changed notifications (the change
  history is already captured, just not alerted on yet) and extending
  alert/delay-reason coverage beyond BidLot to Contract/Logistics/
  Opportunity/DealRegistration.
