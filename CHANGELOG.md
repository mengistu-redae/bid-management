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

### Delay tracking for Contract & Logistics

- `DelaysBoardService` now merges BidLot, Contract, and Logistics rows
  into one board - previously `/delays` only ever showed bid lots, even
  though Contract/Logistics already computed their own Green/Amber/Red/
  Critical indicator. `DelayBoardRowDto` gained generic
  `entityType`/`entityId`/`detailHref` fields so a row can point anywhere,
  not just at a bid.
- Extended delay-reason-on-exit enforcement (previously BidLot-only) to
  Contract and Logistics status transitions, with the same dropdown UI
  already used for bid lots.
- Added `ContractServiceTest` and `DelaysBoardServiceTest` (neither
  service had any test coverage before this), and extended
  `LogisticsServiceTest` with the new delay-reason case.
- Deliberately out of scope, not silently absorbed: the dashboard's own
  "Where deals are stuck" widget/stat card and `MyFollowUpsService` both
  still only cover BidLot.

### Extending the dashboard's delay widget to Contract & Logistics

- The dashboard's "Where deals are stuck" widget and "Behind stage
  target" stat card now scan BidLot, Contract, and Logistics, reusing
  `DelaysBoardService`'s existing merged scan via a new `summary(UUID
  divisionId, boolean noDivisionOnly, int topLimit)` method instead of
  duplicating a third scanning loop - previously BidLot-only, via a
  separate `DelayRowDto`/`delaySummary()` in `DashboardService`.
- Deleted `DelayRowDto`; `DelaySummaryDto.topDelayed` is now
  `List<DelayBoardRowDto>`. `DashboardService` no longer depends on
  `IndicatorService`/`WaitingOnService` directly.
- `WeeklyBottleneckSchedulerService`'s weekly email now links each item
  via `detailHref` instead of a hardcoded bid URL, so Contract/Logistics
  entries link correctly.
- Added three `DelaysBoardServiceTest` cases for `summary()` (overall
  tally, division scope, "no division" bucket).

### Extending My Follow-ups to Contract & Logistics

- `MyFollowUpsService` now includes Contract and Logistics rows the
  current user owns the follow-up for - previously BidLot-only, called
  out as deliberately deferred in the Delays board extension above.
  Neither entity has an account-officer concept, so follow-up ownership
  alone gates all four buckets (overdue/due-today/turning-amber-soon/
  stale) for them, versus BidLot's existing officer-or-follow-up-owner
  split.
- `DelaysBoardService.computeContractRow`/`computeLogisticsRow` made
  public (mirroring `computeRow`) so `MyFollowUpsService` reuses the
  exact same row computation as the Delays board.
- node-bff's `myFollowUps.ejs`/`myFollowUps.js` switched from
  `lotId`-only forms to the generic `entityType`/`entityId` pair already
  used by the Delays board, with an entity-type badge and `detailHref`
  link for non-LOT rows.
- Added `MyFollowUpsServiceTest` (no prior test coverage).
- **Real bug found via live browser verification**: `activity_log` and
  `attachments` were created in `V1__init.sql` before `CHECKLIST_ITEM`,
  `CONTRACT` and `LOGISTICS` existed, and their `CHECK` constraints on
  `entity_type` were never widened when every other polymorphic table's
  was (V8/V10/V12). Logging a follow-up note against a Contract (now
  reachable from the Delays board's generalized form) 500'd on insert.
  Fixed with `V13__widen_activity_log_and_attachments_entity_types.sql`.

### Extending delay/indicator coverage to Opportunity & Deal Registration

- Delay-reason-on-exit enforcement (BidLot, then Contract/Logistics)
  extended to `OpportunityService.update`'s stage transitions and
  `DealRegistrationService.update`'s status transitions - both gained a
  `requireDelayReasonIfRedOrCritical` helper mirroring
  `ContractService`'s exactly, reusing the same `IndicatorService.compute`
  call each entity's own mapper already made for its badge.
  `UpdateOpportunityRequest`/`UpdateDealRegistrationRequest` both gained a
  `delayReasonCode` field; the node-bff forms gained the same dropdown
  already used on Bid/Contract/Logistics status forms.
- `DelaysBoardService` now also scans Opportunity and Deal Registration:
  `computeOpportunityRow`/`computeDealRegistrationRow` mirror
  `OpportunityMapper`/`DealRegistrationMapper`'s own indicator computation
  exactly, including DealRegistrationMapper's APPROVED exclusion (not
  technically terminal, since it can still move to EXPIRED, but no longer
  "in progress"). Since `DashboardService` already delegates to this
  board's `summary()`, the dashboard widget picked up the new entity
  types with no changes of its own.
- `MyFollowUpsService` extended the same way - follow-up ownership alone
  gates all four buckets for these two (no account-officer concept, same
  as Contract/Logistics). The four now-identical "follow-up-owner-only"
  loops (Contract/Logistics/Opportunity/DealRegistration) were factored
  into one generic `evaluateFollowUpOwnerOnly` helper rather than
  copy-pasting a fifth near-identical block.
- `DelayBoardRowDto`'s generic title/subtitle fields needed a real `null`
  subtitle for the first time (Opportunity has no natural "parent" to
  show, unlike Contract/Logistics' bid title) - `delays.ejs`/
  `myFollowUps.ejs` now skip rendering the subtitle span entirely when
  absent instead of printing the literal string "null".
- Added 4 new `OpportunityServiceTest`/`DealRegistrationServiceTest`
  cases for the delay-reason rule, 4 new `DelaysBoardServiceTest` cases
  (a critical Opportunity row, a converted one excluded, a red Deal
  Registration row, an approved one excluded), and 4 new
  `MyFollowUpsServiceTest` cases mirroring the existing Contract/
  Logistics ones.
- Live-verified end to end: created a test Opportunity and Deal
  Registration, confirmed the delay-reason dropdown renders on both
  detail pages, a GREEN-path stage/status transition still succeeds with
  no delay reason required, and a follow-up logged against each via the
  Delays board's generic form shows up correctly in My Follow-ups
  (right entity badge, title, link) and clears correctly - all against
  the real `bidmgmt` database, no errors in either service's logs.
- Deliberately out of scope, not silently absorbed: `DelayAlertSchedulerService`
  (the proactive email/escalation alerts) and `DelayAnalyticsService`
  (bottleneck reports) are still BidLot/Contract/Logistics only.
  Opportunity has an `owner` field that could plausibly receive alerts
  the way BidLot's account officers do, but DealRegistration has neither
  an owner nor a division - extending alert-recipient resolution to
  these two needs a recipient-policy decision from the user, not a guess,
  so it's left as a follow-up rather than done here.

### Admin settings UI

- New `/settings` page (Director-only, linked from the sidebar's Admin
  section): indicator thresholds, working week, holiday calendar, and
  delay reason codes all editable from one page, plus a link to a
  separate `/settings/stage-targets` list/edit flow (20 rows, too many
  fields per row for the same page). All five REST surfaces already
  existed from stage-tracking phase 2 except two: `HolidayCalendarController`
  (`/api/holidays`, GET/POST/PUT/DELETE) and `WorkingWeekConfigController`
  (`/api/working-week-config`, GET/PUT) are new, mirroring
  `StageTargetController`/`IndicatorConfigController`'s existing
  Director-only-writes-open-reads pattern exactly.
- **Real gap found and fixed while wiring this up**: `DelayReasonCodeController`'s
  POST/PUT had no Director check at all, unlike its three sibling settings
  controllers - any authenticated user could create or edit delay reason
  codes via the API. Fixed with the same inline `requireDirector()` check
  the others already use.
- `req.api` (node-bff's server-side API client) gained a `delete` method -
  only `get`/`post`/`put` existed before, and nothing needed DELETE until
  removing a holiday did.
- Working week storage keeps its existing 3-letter-code format
  (`MON,TUE,...`) to stay within the `working_days VARCHAR(40)` column -
  `WorkingWeekConfigController` translates to/from full `DayOfWeek` names
  (`MONDAY`, `TUESDAY`, ...) at the API boundary so the UI/DTO never has
  to know about the abbreviation.
- Live-verified end to end against the real `bidmgmt` database: all four
  settings sections and the stage-targets list/edit flow render real data
  (indicator thresholds 75/100/150/5, the Mon-Sat working week, 13
  holidays, 9 delay reason codes, 20 stage targets including
  DEADLINE_RELATIVE and shipment-mode-override rows); a stage target's
  edit form round-trips correctly including the duration/deadline-relative
  field-visibility toggle; the working week checkbox save/reload round
  trip confirmed (toggled Sunday on, saw it persist, reverted); and a
  holiday add-then-remove round trip confirmed. One browser-automation
  quirk hit along the way (native `<input type="date">` doesn't accept
  plain keystroke typing in this tooling - same "date-picker quirks"
  noted in an earlier phase's own live-verification writeup) - worked
  around by setting the input's value via JS and dispatching a real
  `input` event before submitting, so the actual form POST path was still
  exercised for real, not bypassed.

### Key-date-changed notifications

- New `KeyDateChangeNotificationService` (notification package), on the
  same `bidmgmt.notifications.alert-cron` cadence as
  `DelayAlertSchedulerService` (every 30 min by default): scans
  `audit_log` for new rows on `DelayAnalyticsService.KEY_DATE_FIELDS`
  (made `public static` for this reuse - the exact same curated field
  list the date-slippage report already uses, so the two can never
  drift) and sends one `KEY_DATE_CHANGED` email per change via the
  `EmailTemplates.keyDateChangedAlert` template and the
  `ReminderType.KEY_DATE_CHANGED` enum value - both already existed,
  reserved but never wired up by an earlier phase.
- **Why a new table wasn't needed**: `alert_state` (phase 3's
  episode-tracking table) gained a 4th `AlertKind`
  (`KEY_DATE_CHANGED`), with the audit_log row's own id as the
  `contextKey` - this is what makes a given change notify exactly once,
  ever, no matter how many times the 30-minute scan re-runs.
  `NotificationService.sendIfNotAlreadySent`'s own dedup is "once per
  calendar day", not "once ever" (it hardcodes `LocalDate.now()`), so
  without this a change notified today would re-fire again on every
  later day's scan, since `audit_log` rows are permanent and the service
  has no way to tell "already handled" from "not yet seen" on its own.
- **Real CHECK-constraint bug found before it could bite**: `alert_state`'s
  `alert_kind` constraint only listed the 3 values that existed when
  V12 created the table, same stale-constraint class of bug fixed for
  `activity_log`/`attachments` in V13 - caught this time by checking
  `pg_constraint` against the real database before writing any code,
  per the lesson recorded then. Fixed in
  `V14__widen_alert_kind_for_key_date_changes.sql`.
- Recipients mirror each entity's existing reminder/alert audience
  rather than inventing a new one: Bid reuses
  `ReminderSchedulerService`'s lot-officers-plus-division-managers
  resolution (re-derived locally, matching that class's own precedent of
  not sharing this logic with `DelayAlertSchedulerService` either);
  Opportunity notifies its `owner` plus division managers for its linked
  divisions; DealRegistration (no owner, no division) notifies
  `createdBy` plus the Director, the same pair
  `ReminderSchedulerService`'s expiry reminders already use.
- Added `KeyDateChangeNotificationServiceTest` (pure Mockito, no DB/Spring
  context, same convention as `DelayAlertSchedulerServiceTest`) - one
  case per entity type's recipients, one for an irrelevant field being
  ignored, one proving an already-notified change is never reprocessed.
- Live-verified: the app starts cleanly with the new scheduled bean and
  migration (confirms the Spring wiring and the CHECK-constraint fix are
  both correct), and the full 173-test backend suite stays green. The
  scheduler's own firing logic was not exercised via a real 30-minute
  cron wait - same situation `DelayAlertSchedulerService` was in per its
  own test file's convention - its 5 passing unit tests substitute for
  that, asserting the exact recipients and dedup behavior directly.
