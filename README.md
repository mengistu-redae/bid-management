# Bid, Deal Registration & Opportunity Management

Internal tool for Moti Engineering's Enterprise IT Infrastructure sales team:
tender/bid tracking from scouting through award, deal registrations with
OEMs/distributors, and a pre-tender opportunity pipeline.

```
browser --> nginx --> node-bff (session, OIDC, PKCE) --> spring-boot-api (JWT)
                 \-> keycloak (login, admin console)         \-> postgres, redis
```

- **spring-boot-api** - the core API. Stateless, validates bearer JWTs,
  Flyway migrations only, `ddl-auto: validate`.
- **node-bff** - the only thing the browser talks to. Runs the OAuth2
  Authorization Code + PKCE flow against Keycloak, holds tokens in a
  server-side (Redis-backed) session, and server-renders the UI (EJS +
  HTMX) by calling spring-boot-api with the session's bearer token.
- **keycloak** - identity provider. One realm (`bidmgmt`), four realm roles
  (director, division_manager, account_officer, scout).
- **postgres / redis** - primary datastore / BFF session store.
- **nginx** - single entry point on `:82` for local dev.

No multi-tenancy here (unlike some of the other projects on this machine) -
this is a single company's internal tool. Role-based access is enforced in
spring-boot-api (`BidAccessService`); division membership is plain app data
(`user_divisions`), not a Keycloak concept, since the Director edits it often.

## Running it

```bash
docker compose up --build
```

First boot takes a couple of minutes (Keycloak's `start-dev` + realm import
is slow cold). Once everything is healthy, spring-boot-api's `SeedDataRunner`
automatically imports both sample spreadsheets from `docs/` into the empty
database, so there's real data to look at immediately.

Then log in at **http://localhost:3002/auth/login**:

| User | Role | Username | Password |
|---|---|---|---|
| Mengistu Redae | Director | `mengistu.redae` | `ChangeMe123!` (forced reset) |
| Ermiyas Mesfin | Server & Storage manager | `ermiyas.mesfin` | `ChangeMe123!` |
| Nardos Kibru | Network + Security manager | `nardos.kibru` | `ChangeMe123!` |
| Eyerusalem Tariku | Datacenter Facility manager | `eyerusalem.tariku` | `ChangeMe123!` |

Account officers and scouts (Selam, Tezana, Abenezer, Zufan, Kaleab, Endris,
Berhanu, Betelhem, Eyerus, Betty) exist as reference rows from the imported
data but have no login yet and no email on file - the Director can add an
email and grant one a real login from `/users` (see Phase 5 below).

Service URLs:

| Service | URL |
|---|---|
| App (via nginx) | http://localhost:82 |
| node-bff directly | http://localhost:3002 |
| Keycloak admin console | http://localhost:8090 (admin/admin) |
| spring-boot-api (debugging) | http://localhost:8091 |

Host ports are deliberately offset from this machine's other Spring/BFF
stacks (`:82/:3002/:5434/:6380/:8090/:8091` instead of the conventional
`:80/:3000/:5432/:6379/:8080/:8081`), so they can all run at once.

**First-time setup note:** after the realm imports, the BFF's Keycloak
client secret is auto-generated - fetch it from the admin console
(`bidmgmt` realm -> Clients -> `bidmgmt-bff` -> Credentials) and put it in
a `.env` file at the repo root as `BFF_CLIENT_SECRET`, then
`docker compose up -d --force-recreate node-bff`. See `.env.example`.

**HTTPS note:** nginx also serves HTTPS on `:8443`, but needs a cert first -
run `infra/nginx/generate-self-signed-cert.sh` once before the first
`docker compose up` (plain HTTP on `:82` works either way; browsers will
warn on the self-signed cert - see `DEPLOYMENT.md` for swapping in a real
one for production).

## What's built so far

**Phase 1** - data model, auth/roles, Bid+Lot CRUD with the full status
lifecycle (Identified -> Under Review -> Preparing -> Submitted -> Opened ->
Under Evaluation -> Won/Lost, with Dropped/Cancelled side exits, each lot
able to diverge independently), per-lot prep checklist, activity log,
status history, and the two-step (preview, then confirm) Excel importer for
both sample files - live-verified end to end against a real `docker compose`
stack and a real browser login.

**Phase 2** - dashboard (bids closing soon with checklist-progress red flags,
upcoming clarification deadlines, pipeline value by division/status with
ETB and USD always kept separate, win rate by division/officer/OEM, bid
bonds outstanding with an estimated expiry), a read-only Kanban board, a
month calendar, filters on the bid list (division/status/officer/
organization/closing date range), and Excel export of the filtered list -
also live-verified end to end.

A minimal bid-bond-returned flag was pulled forward from phase 3's planned
Deal Registration/Bid Bond entities (see `V3__bid_bond_return_tracking.sql`),
since the phase-1 dashboard spec explicitly needs "not yet returned" - phase
3 then finished bid bond tracking in place (issuing bank + issue date added
directly to `bid_lots` rather than extracting a separate table, since every
lot has at most one bond and the existing fields were already wired through
the importer, dashboard and tests).

**Phase 3** - Opportunities (organization, title, value, division(s),
OEM(s)/vendor(s), stage Lead -> Qualified -> RFI/Proposal -> Expecting
Tender -> Converted to Bid / Lost-Closed, owner, notes, activity log) with
a "Convert to Bid" action that creates a linked Bid (carrying the first
division's value/OEM onto Lot 1; any additional divisions become empty,
needs-review lots rather than guessing a value split); Deal Registrations
(OEM, distributor, registration ID, customer, linked opportunity/bid,
status Draft -> Submitted -> Approved/Rejected/Expired, approval/expiry
dates, protected discount, conflict detection when another active
registration already exists for the same customer + OEM) with the same
intake-form conflict warning pattern as the bid duplicate check; a shared
OEM reference list seeded with the brief's named vendors; and the
dashboard's "deal registrations expiring in 30 days" tile, deferred from
phase 2 since the entity didn't exist yet.

A real bug was caught here via a live-database integration test
(`OpportunityServiceLiveTest`) before it ever reached the browser: mutating
a just-created Bid's `opportunity` field and calling `.save()` a second
time made Spring Data route through `merge()` instead of `persist()` (since
the UUID primary key was already assigned), which Hibernate executed as an
UPDATE - and `@CreationTimestamp` only ever fires for INSERT, so
`created_at` landed NULL and the DB's own NOT NULL constraint caught it.
Fixed by setting the opportunity link before the first save instead of
mutating an already-persisted-but-unflushed entity afterward.

**Phase 4** - email reminders (bid closing at 7/3/1 days out, clarification
deadline at 2 days, deal registration expiry at 30/7 days, bid bond not
returned 30 days after opening) and a Director's daily digest (closing this
week, at-risk red flags, what changed yesterday), all sent through a
`NotificationChannel` abstraction designed so a Telegram bot channel can be
added later alongside email (per the brief) without touching the reminder
logic itself; exact-day-match due-date computation (never "every day from
N onward") with send-dedup via a `notification_log` table keyed on
(reminder type, entity, recipient, day), so the daily cron job is safe to
re-run or restart mid-run. A monthly summary report (bids identified,
submitted, won, lost, dropped, and won value, by division) with a
`/reports` page and Excel export. A `mailpit` service for local email
testing and `DEPLOYMENT.md` for running this on a real on-premise server.

Live-verified end to end: a real email sent through `NotificationService`
landing in mailpit (`NotificationServiceLiveTest`, read back via mailpit's
own REST API), the `/reports` page rendering real seeded data in a browser,
and its Excel export downloading a genuine `.xlsx` file.

**Phase 5** - four independent gaps from phase 4's "not yet built" list,
all user-driven (no formal phase 5 spec existed, so each piece was scoped
and confirmed with the user before building):

- **TLS** - nginx now also serves HTTPS on `:8443` with a self-signed cert
  generated on the host (`infra/nginx/generate-self-signed-cert.sh`, not
  in-container - see its comment for why an earlier `apk add openssl`
  in-container approach was abandoned: `nginx:alpine` doesn't ship the
  `openssl` CLI, and pulling the package live made first start unreliably
  slow). Plain HTTP on `:82` keeps working unredirected, so existing local
  workflows aren't broken - see `DEPLOYMENT.md` for going further in
  production (a real cert, forcing the redirect).
- **In-app user management** - a Director-only `/users` page to create a
  person (an `app_users` row only - most account officers/scouts never need
  a login), separately grant them a real Keycloak login on demand (via
  Keycloak's Admin REST API, master-realm `admin-cli` password grant,
  reusing the same `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` credentials
  the `keycloak` compose service is seeded with - see the `keycloakadmin`
  package, adapted from the same pattern in the sibling
  `clinic-management-saas` project), and edit role/division(s)/email/
  Telegram chat id. A person's email is only editable before they have a
  login - once Keycloak has a real account, its own email is the source of
  truth and the app never talks back to Keycloak to keep them in sync.
- **Telegram notification channel** - `TelegramNotificationChannel`, the
  second `NotificationChannel` the Phase 4 design was built to accept, per
  the brief's "designed so a Telegram bot channel can be added later" note.
  Messages are sent as plain text (Telegram's Bot API only renders a small
  HTML subset, nothing like `EmailTemplates`'s markup, so tags are stripped
  rather than mapped). Not live-verified end to end - the user doesn't have
  a Telegram bot token yet - but thoroughly unit-tested against a mocked
  HTTP server (`TelegramNotificationChannelTest`), which caught a real bug
  before it ever shipped: building the request URI from a `"/bot{token}/..."`
  template percent-encodes the `:` every real bot token contains, which
  would have 404'd on every single send.
- **Import UX improvements** - the Excel import preview table is now
  editable per row (organization, title, division) with a skip checkbox,
  replacing the old "confirm persists the cached preview exactly as parsed"
  behavior (`ImportRowEditor`, a plain function over the cached row lists so
  it's testable without a cache, a token, or a controller in the way).

A real gap was caught live while testing user management: granting a login
to an account officer/scout reference row with no email on file (the
common case for data seeded from the Excel import) tried to create a
Keycloak user with a null username. Fixed by validating upfront (a clear
error instead of a confusing upstream failure) and adding an email field to
the edit page so the Director can add one first.

Live-verified end to end: HTTPS actually terminating TLS at `:8443`
(confirmed via a real cert-authority browser warning - the expected result
for a self-signed cert - and via `curl -k`) alongside HTTP still working at
`:82`; creating a person, granting them a login, and confirming the
resulting Keycloak user for real via its own Admin REST API; editing
role/division/email and deactivating/reactivating; and a full import run
where an edited organization name and division, and a skipped row, both
landed correctly on the persisted data and in the confirm summary's counts.

## Stage time tracking, delay indicators & proactive follow-up

A second initiative, layered on top of the lifecycle above, to answer "how
long has this been sitting, is that on time, who's it waiting on, and will
someone follow up before it becomes a problem" for every record in every
stage - plus bottleneck analytics. Phased separately from Phases 1-5 above;
phase numbers below are relative to this initiative, not those.

**Phase 1** - the shared infrastructure every later phase builds on, backend
only (no UI yet - indicators and the Deal 360 timeline are phase 2):

- **Contract and Logistics** didn't exist anywhere in the codebase before
  this (the lifecycle stopped at a won Bid) - added as a deliberately
  minimal pair of entities (`Contract`: status Awarded -> Signed ->
  In Delivery -> Completed/Terminated, signed-at, delivery deadline;
  `LogisticsShipment`: status Planning -> Dispatched -> In Transit ->
  Customs -> Cleared -> Delivered, mode Air/Sea/Land, one Contract to many
  shipments) plus a generic `stage_milestones` table (acceptance steps,
  payment milestones, shipment milestones - the same shape as
  `checklist_items`, one level up) rather than bespoke milestone tables per
  entity. Full CRUD + guarded status transitions (`ContractService`,
  `LogisticsService`), REST at `/api/contracts` and `/api/logistics`.
- **`stage_history`** - one polymorphic, append-only table (entity type +
  id, same discriminator pattern as `activity_log`/`attachments`/
  `audit_log`) recording every status/stage/waiting-on/hold transition
  across Opportunity, DealRegistration, Bid, Lot, ChecklistItem, Contract
  and Logistics, instead of a separate history table per entity.
  `BidStatusService`, `OpportunityService`, `DealRegistrationService`,
  `ChecklistService`, and Bid/Lot creation all now dual-write into it
  alongside their existing behavior (`bid_status_history` itself is
  untouched - nothing that reads it today had to change).
- **Key-date change history** reuses the *existing* generic `audit_log`
  table and `AuditService.recordIfChanged` rather than a new one - a bid's
  `closingAt` was already audited; `openingAt`, `clarificationDeadline`,
  and DealRegistration's `submittedDate`/`approvalDate`/`expiryDate` and
  Opportunity's `expectedTenderDate` were wired in the same way.
  `KeyDateHistoryService` reports original vs current value, change count,
  and slippage in days off that same table.
- **Business-day calendar** - `BusinessDayCalculator` (a pure, Spring-free
  class - working-week set + holiday set + zone - so it unit-tests without
  a database) wrapped by `BusinessDayCalendarService`, which loads the
  admin-editable `working_week_config` (single row, Mon-Sat by default)
  and `holiday_calendar` tables. Seeded with a best-effort pass at 2026
  Ethiopian public holidays (`V9__seed_2026_holidays.sql`) - the Orthodox
  Easter- and Islamic-lunar-calendar-dependent ones are marked
  "estimated, verify" and need a human check before anyone relies on them
  for real escalation math.
- **On hold** - `hold_periods` (one open hold per entity, enforced by a
  partial unique index) plus `HoldService`, which also logs a
  `stage_history` row at start/end so holds show up in the same unified
  timeline. `StageClockService` subtracts hold time from both calendar and
  business-day elapsed calculations.
- **Backfill** (`StageHistoryBackfillRunner`, an idempotent
  `ApplicationRunner` - safe to re-run every startup, skips anything that
  already has `stage_history` rows) replays `bid_status_history` in full
  for every Bid/Lot (its very first IDENTIFIED entry is always known,
  since that's the hardcoded construction default, regardless of how much
  real history follows it), and seeds one synthetic entry for an
  Opportunity/DealRegistration still sitting in its very first value
  (LEAD/DRAFT) at its `createdAt`. A record already past its first stage
  with no real history is deliberately left unseeded, so its clock reports
  "unknown" rather than a fabricated zero.

Everything above is additive: new tables/columns only, no existing column
touched, `bid_status_history` and `audit_log` keep working exactly as they
did. Verified via `mvn test` against the real `bidmgmt_test` database
(`StageHistoryBackfillRunnerTest`) and a pure unit test for the calendar
math (`BusinessDayCalculatorTest`); not yet click-through-verified in the
browser, since there's no UI surface until phase 2 - verified instead via
the new `/api/stage-history`, `/api/stage-clock`, `/api/holds`,
`/api/contracts` and `/api/logistics` endpoints against the running
`docker compose` stack.

**Phase 2** - targets, indicators everywhere, waiting-on, delay reasons, and
the Deal 360 stage timeline - the first UI surface for this initiative:

- **`stage_targets`** - "what does late mean" per (entity type, stage
  value), with optional narrower overrides by division, customer sector,
  OEM name, or shipment mode (most-specific-match-wins resolution in
  `IndicatorService`). Seeded with the default list confirmed with the
  user beforehand (business-day durations for Opportunity/DealRegistration/
  BidLot/Contract stages, deadline-relative targets for Preparing-before-
  closing and In-Delivery-before-delivery-deadline, and per-shipment-mode
  Logistics transit times - Air 5 business days, Sea 35 calendar days,
  Land 10 business days). Admin-editable via `/api/stage-targets`
  (Director-only writes).
- **`indicator_config`** - single-row, admin-editable thresholds (Amber
  75%, Red 100%, Critical 150% of target by default, plus a default stale-
  after-days) via `/api/indicator-config`.
- **Indicators everywhere** - every DTO for a trackable entity (Bid's lots
  individually, rolled up to the Bid as the worst level across its lots per
  the confirmed lot-level-granularity decision; Opportunity; Deal
  Registration; Contract; Logistics) now carries a computed
  GREEN/AMBER/RED/CRITICAL/UNKNOWN/NO_TARGET indicator plus a stale flag,
  shown as a colored badge on the bid list, Kanban board, bid detail (bid
  and per-lot), Opportunities list/detail, Deal Registrations list/detail,
  and a new "Delays" section on the dashboard (amber/red/critical/stale
  counts plus the top 10 most-delayed lots).
- **Waiting-on** - `waiting_on_state` (current value per entity) +
  `stage_history` (the change log, reusing phase 1's table rather than a
  new one) for who/what an open stage is waiting on (Internal with a
  person, Customer, OEM, Distributor, Implementation Partner, Bank,
  Freight Forwarder/Customs, Other). Editable inline on the bid lot card.
- **Delay reasons** - an admin-editable `delay_reason_codes` list (seeded
  with the brief's examples). A reason code is now required, server-side,
  when moving a `BidLot` out of a stage that's currently RED or CRITICAL
  (`BidStatusService.transitionLot`) - deliberately scoped to lots only
  for this phase (the highest-churn part of the pipeline); the same
  pattern can be extended to Contract/Logistics/Opportunity/
  DealRegistration transitions later without new infrastructure.
- **Deal 360** - a new page per bid (`/bids/:id/deal360`) assembling the
  whole chain on the fly (Opportunity found by `convertedBidId`, Deal
  Registrations by `bidId`, Contract by `bidId`, Logistics by
  `contractId` - no new "Deal" table, per the confirmed on-the-fly-
  grouping decision) and rendering each entity's own `stage_history` as a
  horizontal bar of proportionally-sized segments (`dealTimeline.js`,
  calendar-day approximation, not the precise business-day figures already
  shown in the indicator badges elsewhere), colored green/red against its
  current stage target, with the ongoing stage shown as a diagonal-striped
  segment.

A real bug was caught live during browser verification: both the delay-
reason-enforcement check and several indicator computations built their
"no deadline set" fallback as `Instant.MAX`, which overflows
(`DateTimeException: Invalid value for EpochDay`) the moment a
DEADLINE_RELATIVE target tries ordinary date arithmetic on it (e.g.
`minus(offsetDays, DAYS)`) - every bid with no `closingAt` 500'd on its
dashboard delay summary. Fixed by `IndicatorService.deadlineField(name,
value)`, which omits the map entry entirely when the value is null instead
of passing a sentinel, so the target correctly resolves to UNKNOWN.

Live-verified end to end against the real `bidmgmt` database: the
dashboard's Delays section rendering real counts (57 lots critical in the
existing imported/historical data - expected, since those records predate
this feature and were never actively tracked against these targets);
changing a lot's status without a delay reason correctly rejected by the
API; the same change succeeding once a reason was supplied, with the
lot's badge immediately flipping to GREEN (a fresh `stage_history` entry
resets the clock); and the Deal 360 page rendering a real two-segment
timeline (a long over-target IDENTIFIED bar, then the new ongoing
UNDER_REVIEW segment) for that same lot.

**Phase 3** - proactive follow-up, a Delays board, and notifications/
escalation - the first phase of this initiative that actually sends
anything beyond the existing daily reminders:

- **Follow-up state** (`follow_up_state`, one row per entity) - a next
  follow-up date and owner, settable together with an activity-log note
  in one step (`FollowUpService.logFollowUp`). Snoozing
  (`FollowUpService.snooze`) requires a reason and a date, pushes the
  next follow-up date out to match, and logs the reason to the same
  activity log.
- **`DelayAlertSchedulerService`** (every 30 minutes by default,
  `bidmgmt.notifications.alert-cron`) - the single-fire-per-threshold-
  crossing engine. `alert_state` (entity + alert kind + a context key
  scoping one "episode" - the current stage value for indicator alerts,
  so a new stage always starts fresh) tracks what's already been sent;
  a level recrossing above what's recorded fires again, dropping back to
  green/not-stale/not-due clears the row so the next crossing is fresh.
  Escalation matches the brief exactly: Amber -> owner (digest-only
  unless they opt into immediate amber alerts - a new per-user
  `amber_immediate` preference, editable on the Users page), Red ->
  + division manager, Critical -> + Director. Stale follows the same
  pattern, escalating to the manager at 2x the stale-after-days
  threshold. Follow-up-due fires once per due date, to the follow-up
  owner only. Snoozed entities are skipped entirely. Scoped to BidLot,
  matching phase 2's delay-reason enforcement scope.
- **`DailyDigestSchedulerService`** (same daily cron as the existing
  reminders) - one email per relevant user, built from the last 24h of
  `alert_state` rows (so "newly amber/red" means exactly what the alert
  scheduler already decided was worth a fresh alert) plus due-or-
  overdue follow-ups. The Director's bucket also gets a running critical
  count. Fully additive - the existing Director-only
  `DIRECTOR_DIGEST` keeps working unchanged alongside it.
- **`WeeklyBottleneckSchedulerService`** (Monday 7am by default,
  `bidmgmt.notifications.weekly-digest-cron`) - top 10 most delayed
  records (reusing `DashboardService`'s own delay summary, now exposed
  as a standalone `delaySummary()`), red/critical counts by level, and
  open-item counts by waiting-on party, to the Director and every
  division manager. The "who we're waiting on most" figure is a current-
  open-item count, not total days waited - the deeper time-waiting
  analysis is phase 4's bottleneck-analytics territory, which has the
  `stage_history` data to compute it properly.
- **Delays board** (`/delays`, backed by `DelaysBoardService` +
  `/api/delays-board`) - every open lot currently amber/red/critical/
  stale, filterable by division/officer/customer/OEM/waiting-on/level,
  sorted by % of target used, with a one-step "log follow-up or snooze"
  panel on every row.
- **My Follow-ups** (`/my-follow-ups`, backed by `MyFollowUpsService`) -
  overdue and due-today follow-ups the current user owns, plus (for
  items they're the assigned officer on) green lots "turning amber
  soon" (within 15 points of the amber threshold) and currently-stale
  items.
- Deliberately deferred for now: key-date-changed notifications (phase
  1/2 already capture the change history via `audit_log` -
  `KeyDateHistoryService` reports it - but nothing sends an alert on the
  change yet) and extending delay-reason/alert coverage beyond BidLot to
  Contract/Logistics/Opportunity/DealRegistration. Both are additive
  follow-ons, not redesigns.

Live-verified end to end: the Delays board rendering real filtered/sorted
data, logging a follow-up from a board row (date-picker quirks aside, the
save round-tripped and the row's "Next follow-up" updated immediately),
My Follow-ups rendering its four empty-state sections correctly for a user
with no matching items, and the amber-immediate preference toggle saving
on the Users page. `DelayAlertSchedulerServiceTest` covers single-fire
behavior, all three escalation tiers' recipients, de-escalation-clears-
state, and snooze suppression with mocked collaborators (no real cron
wait needed); `FollowUpServiceTest` covers the required-reason/required-
date snooze validation and the next-follow-up-date push.

**Phase 4** - delay analytics / bottleneck reports, the last phase of this
initiative: turning the `stage_history` data every earlier phase has been
recording into retrospective reporting, rather than the live/forward-
looking indicators and alerts of phases 2-3:

- **`DelayAnalyticsService`** - a single-pass aggregator: for every BidLot,
  its full `stage_history` is fetched once and walked once, deriving all
  seven report sections from that one pass rather than a separate query
  per section (the same "all-in-memory, deliberate simplification at this
  scale" approach `DashboardService` already documents and uses). Per
  completed stage segment, elapsed business days come from the same
  `BusinessDayCalendarService` phase 1 built, and the target it's compared
  against comes from `IndicatorService.resolveTarget` (made public for
  this - the exact same most-specific-override-wins resolution live
  indicators use, so historical analytics and live badges can never
  drift apart) and `KeyDateHistoryService.slippageDays` (also made public,
  for the date-slippage section below) are reused rather than
  reimplemented.
- **Per-stage duration stats** - completed count, avg/median/P90 business
  days, and % of completed segments over target, per stage value.
- **Monthly trend** - the same duration figures bucketed by stage +
  calendar month.
- **Breakdowns** - total delay days (actual minus target, summed) by
  division, account officer, customer, and OEM.
- **Delay-reason Pareto** - total excess-over-target days grouped by the
  `delay_reason_code` recorded on the exit transition (phase 2's required-
  reason-when-leaving-a-red/critical-stage rule is what guarantees most
  rows carry one).
- **Date slippage** - reuses `audit_log` the same way `KeyDateHistoryService`
  already does (phase 1), scanning it for `closingAt`/`openingAt`/
  `clarificationDeadline` (Bid), `expectedTenderDate` (Opportunity), and
  `submittedDate`/`approvalDate`/`expiryDate` (DealRegistration) changes
  in range: a by-field total-slippage summary, and the top 15 most-
  revised individual records.
- **Who we're waiting on most** - total days by waiting-on party type,
  deliberately including the still-open final episode through "now" (not
  just resolved waits) - a currently-painful 60-day OEM wait should show
  up immediately, not only once it's finally resolved. This is the
  deeper version of phase 3's weekly digest "open-item count" figure,
  now with actual elapsed time.
- **Excel export** (`DelayAnalyticsExportService`, Apache POI, following
  `ReportExportService`'s existing conventions) - all seven sections as
  separate sheets, from `/api/delay-analytics/export`.
- Date-range filterable (`from`/`to` query params on
  `/api/delay-analytics` and its export, both optional - defaulting to
  all-time) via `GET /reports/delay-analytics` in node-bff, following the
  same table/bar-chart presentation (`partials/barChart`, no new charting
  library) as the rest of the Reports area.
- Scoped to BidLot only, same as phases 2 and 3's delay-reason enforcement
  and alert scheduling - Contract/Logistics/Opportunity/DealRegistration
  coverage remains a bounded, additive follow-on.

Live-verified end to end against the real `bidmgmt` database: all seven
report sections rendering real data on `/reports/delay-analytics`
(per-stage duration, monthly trend, four breakdowns, delay-reason Pareto,
date slippage, waiting-on totals); the date-range filter correctly
returning "no completed stage segments" for a range with no data and the
real figures for an unfiltered request; and the Excel export verified via
an in-page `fetch` confirming a `200` response with the correct
`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
content type and a non-empty body.

## Tests

```bash
cd spring-boot-api && mvn test
```

Covers the importer's parsing rules (`ParsingUtilsTest`, against real values
from the sample spreadsheets), the status-lifecycle transition rules
(`BidStatusServiceTest`), the per-role permission rules (`BidAccessServiceTest`,
including opportunity and deal-registration editing), the organization
name-matching/merge logic (`OrganizationResolutionServiceTest`), the
dashboard's currency-separation/win-rate/red-flag rules (`DashboardServiceTest`),
the deal-registration conflict check (`DealRegistrationServiceTest`), and two
tests against a real Postgres: a full live import of both real sample files
(`ImportServiceLiveFilesTest`) and opportunity-to-bid conversion
(`OpportunityServiceLiveTest`, which caught a real Hibernate flush-timing bug
no mocked test could have). The reminder scheduler's exact-day-match rules
(`ReminderSchedulerServiceTest`) and the monthly report's month-boundary/
division-grouping rules (`ReportServiceTest`) are covered the same way as
the dashboard. A third live test (`NotificationServiceLiveTest`) sends a
real email through `NotificationService` over SMTP and reads it back out of
mailpit's REST API, confirming the message actually left
`EmailNotificationChannel` with a deliverable envelope - needs mailpit
running (`docker compose up -d mailpit`) in addition to Postgres. All three
live tests use a separate `bidmgmt_test` database so they never touch
real/seeded data.

Phase 5 added `UserServiceTest` (email-uniqueness on create, divisions only
ever applying to `DIVISION_MANAGER`, the grant-login/blank-email guard),
`TelegramNotificationChannelTest` (the `supports()` gate and the actual
HTTP request shape sent to Telegram's Bot API via `MockRestServiceServer` -
the test that caught the token-encoding bug above), and
`ImportRowEditorTest` (per-row edit/skip rules, including that a blank edit
field never clobbers the parsed value).

Stage-tracking phase 1 added `BusinessDayCalculatorTest` (the working week,
holidays, and on-hold pauses, all as pure math with no database) and
`StageHistoryBackfillRunnerTest` (a live test: real `bid_status_history`
replayed in full, a still-LEAD Opportunity seeded from `createdAt`, and a
past-LEAD Opportunity with nothing to replay correctly left unseeded -
i.e. "unknown" rather than zero).

Stage-tracking phase 2 added `IndicatorServiceTest` (duration and deadline-
relative threshold crossings at the Green/Amber/Red/Critical boundaries,
most-specific-override-wins target resolution across division/sector/OEM/
shipment-mode, the no-target and unknown-elapsed-time cases, and the
`Instant.MAX` overflow regression) and extended `BidStatusServiceTest` and
`DashboardServiceTest` with a mocked `IndicatorService`. `dealTimeline.js`'s
segment-building math is covered on the node-bff side too
(`node-bff/test/dealTimeline.test.js`, run via `npm test` - the first test
added for node-bff, since none existed before).

Stage-tracking phase 3 added `DelayAlertSchedulerServiceTest` (single-fire-
per-threshold-crossing, the Amber/Red/Critical recipient escalation tiers,
de-escalation clearing state, snooze suppression, and follow-up-due firing
once) and `FollowUpServiceTest` (snooze's required reason/date, the next-
follow-up-date push, and `isSnoozed`'s date-boundary behavior) - both
pure-Mockito unit tests, no database needed.

Stage-tracking phase 4 added `DelayAnalyticsServiceTest` (avg/median/P90
and %-over-target computation for a completed stage segment, delay-reason-
code aggregation by excess-over-target days, waiting-on totals correctly
including a still-open episode through "now", and the date-slippage
report's original-vs-current/change-count math) - a pure-Mockito unit
test, no database needed.

## Deployment

See `DEPLOYMENT.md` for running this on a single on-premise Linux server:
first-time setup, TLS (self-signed cert included for local HTTPS - read
this before exposing the app outside a trusted network with it), email/SMTP
config, the Keycloak Admin API user-provisioning setup, Telegram bot setup,
backups, and updates.
