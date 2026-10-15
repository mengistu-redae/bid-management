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

### Stage time tracking, delay indicators & proactive follow-up - Phase 4

- Added `DelayAnalyticsService`, a single-pass-per-lot aggregator over
  `stage_history` producing: per-stage duration stats (avg/median/P90
  business days, % over target), monthly trend, delay-day breakdowns by
  division/officer/customer/OEM, a delay-reason-code Pareto, a date-
  slippage report (by field and top 15 most-revised records, reusing
  `audit_log`/`KeyDateHistoryService`), and waiting-on totals by party
  type (including the still-open final episode through "now").
- Exposed `IndicatorService.resolveTarget` and
  `KeyDateHistoryService.slippageDays` (previously private) so historical
  analytics reuse the exact same target-resolution and slippage math as
  the live indicators, rather than duplicating it.
- Added `DelayAnalyticsExportService` (Apache POI, 7-sheet `.xlsx`) and
  `GET /api/delay-analytics` / `GET /api/delay-analytics/export`
  (both accept optional `from`/`to` date-range filters).
- Added `/reports/delay-analytics` in node-bff - tables and bar charts
  (`partials/barChart`) for all seven report sections, a date-range
  filter form, and an Export-to-Excel link, following the Reports area's
  existing conventions.
- Scoped to BidLot only, same as phases 2 and 3.

### Dashboard redesign (Bid Desk)

- Rebuilt the dashboard from a design spec, with a working division
  filter (`?division=` on `/dashboard`, threaded through
  `DashboardService.build(String)`), a per-bid readiness-gap table
  (Division/Owner/Go-no-go/MAF/Vendor quote/Closing time/Verify bond/
  Scope, several reusing existing fields and the previously UI-less
  `Bid.goNoGoDecision`), and a redesigned "Where deals are stuck" widget
  with a Critical/Red/Amber/On-track segmented summary bar and a days-
  in-stage/target/waiting-on line per card.
- Added by-lifecycle-stage bid counts/ETB totals, a "bids past opening
  with no result" callout on Win rate, and a Deal registrations summary
  panel (expiring-soon/pending-OEM/approved counts, plus PREPARING bids
  with an OEM brand but no linked registration).
- Added a real header search (`BidFilter.search`, case-insensitive
  substring match against title/organization/reference number) backing
  the "Search bids, customers, reference numbers" bar.
- Sidebar regrouped into Today/Lifecycle/Insight, with live badge counts
  on My follow-ups and Delays (reuses the existing `/api/my-follow-ups`
  and `/api/delays-board` endpoints via a new per-request node-bff
  middleware - no new backend endpoint needed).
- Fixed a CSS bug caught during live verification: new `.pill` and
  `.next-week-item` links read in the wrong color once visited, because
  a plain class selector loses to `a:visited` at equal specificity - the
  same class of bug the earlier bid-card redesign already worked around.

### Lifecycle completeness (closing the pipeline gaps) - Phase 1

- Added a guarded transition table to `OpportunityService` (Lead ->
  Qualified -> RFI/Proposal -> Expecting tender, Lost/Closed as a side
  exit requiring a reason from any non-terminal stage) - same pattern as
  `BidStatusService`. Converted to Bid stays reachable only via
  `convertToBid()`, not the generic update, so it can never be set
  without the Bid it's supposed to link to actually existing.
- Added the equivalent table to `DealRegistrationService` (Draft ->
  Submitted -> Approved or Rejected, Approved -> Expired) and a new
  `DealRegistrationStatus.isTerminal()`, which the existing conflict
  check now uses instead of its own inline logic.
- Added `GlobalExceptionHandler` so a rejected transition (on Bid, too)
  actually surfaces its real message instead of a bare, message-less 500
  - Spring Boot's default error handling doesn't include it otherwise.
  node-bff's bid/lot/opportunity/deal-registration status routes now
  flash that message back to the same page instead of bouncing to the
  generic error page.

### Lifecycle completeness (closing the pipeline gaps) - Phase 2

- Added Contract's first-ever node-bff UI: `/contracts` (list),
  `/contracts/new` (create from a WON bid, pre-selected via `?bidId=` or
  picked from a dropdown), and `/contracts/:id` (detail - status
  transitions reusing phase 1's flash-error pattern, an edit form, and a
  Logistics section listing its shipments). No backend changes needed -
  `ContractController`'s REST API was already complete.
- Added a Contract section to the Bid detail page (WON bids only) linking
  to the existing contract or offering to create one.
- Added "4 Contracts" to the sidebar's Lifecycle group.
- Extended `statusBadgeClass` (node-bff) to color-code Contract and
  Logistics statuses, not just Bid's.

### Lifecycle completeness (closing the pipeline gaps) - Phase 3

- Added Logistics' first-ever node-bff UI, mirroring Phase 2's pattern:
  `/logistics` (list across every contract), `/logistics/new` (create
  for a contract, pre-selected via `?contractId=` or picked from a
  dropdown), and `/logistics/:id` (detail - status transitions, edit
  form). No backend changes needed.
- Gave the Contract detail page's Logistics section (stubbed empty in
  Phase 2) a working "+ Add shipment" link and linked its shipment rows.
- Added "5 Logistics" to the sidebar's Lifecycle group, completing it.

### Lifecycle completeness (closing the pipeline gaps) - Phase 4

- `LogisticsService.create` now rejects a shipment for a contract that
  isn't Signed or In Delivery, mirroring Contract's own WON-bid gate.
- Added a "Go / no-go" card to the Bid detail page for the previously
  UI-less `Bid.goNoGoDecision`/`goNoGoReason` fields (already fully wired
  on the backend) - a small dedicated form rather than a general bid-edit
  form, since the detail page has never had one.
- Added `LogisticsServiceTest`, the first test coverage `LogisticsService`
  has had, covering the new creation gate and its existing status guard.

All four phases of the lifecycle-completeness initiative are now done.
