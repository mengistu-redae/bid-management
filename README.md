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

## Dashboard redesign (Bid Desk)

The dashboard was rebuilt from a design spec (`docs/Bid Desk Dashboard
Redesign.pdf`) covering a restructured sidebar, a working division filter,
a per-bid "readiness gaps" table, a redesigned delay widget, and a few
new summary panels - all built on real computations, not static mockup
numbers:

- **Sidebar** regrouped into Today (Dashboard, My follow-ups, Delays,
  Calendar - the first two now carry live badge counts), Lifecycle
  (numbered Opportunities/Deal registrations/Bids - Contract and
  Logistics are deliberately left out of the nav, since neither has a
  node-bff UI yet; see the lifecycle-evaluation discussion that preceded
  this work) and Insight (Reports, Bid bonds & guarantees). The two
  sidebar badges come from a new `app.use` middleware in `index.js` that
  calls the existing `/api/my-follow-ups` and `/api/delays-board`
  endpoints once per request and sets `res.locals.navCounts` - no new
  backend endpoint needed, just reuses what Phase 3 already built. This
  does mean every page now makes two extra API calls for the sidebar
  alone; both reuse the same all-in-memory per-request computation the
  rest of this app already leans on, which is fine at today's scale but
  is the first thing to revisit if the app ever feels sluggish.
- **A real, working division filter** - `?division=` on `/dashboard`
  (omitted for all, `none` for the no-division bucket, or a division's
  UUID), threaded through to `DashboardService.build(String
  divisionFilter)`. The filter pills above the stat grid always show
  counts from the *unfiltered* bid set (computed once up front,
  independent of whatever filter is currently applied) so they read as
  stable reference numbers rather than shifting as you click between
  them.
- **Readiness gaps** - a new per-bid check (`DashboardService
  .readinessGaps`) flagging whichever of Division / Owner / Go-no-go /
  MAF / Vendor quote / Closing time / Verify bond / Scope the bid is
  still missing, shown as badges in the "Closing in 7 days" table.
  Division/Owner/MAF/Vendor-quote/Scope map directly to existing fields
  (lot division, assigned account officer, the MAF/vendor-quote
  checklist items reused from `BidRiskEvaluator`, lot scope types);
  Go-no-go reuses the existing (previously UI-less) `Bid.goNoGoDecision`
  field; "Closing time" and "Verify bond" are heuristics documented in
  code (`closingAt` at exactly midnight local time = time still TBC; a
  bond amount recorded with its form still `UNKNOWN` = needs verifying)
  since neither has a dedicated flag in the data model.
- **Where deals are stuck** keeps the card-list design from the prior
  dashboard pass, with a new Critical/Red/Amber/On-track segmented
  summary bar (stale reported separately, since staleness is orthogonal
  to color level) and a days-in-stage/target/waiting-on line per card -
  `DelayRowDto` and `DelaySummaryDto` both gained fields for this
  (`daysUsed`, `targetDays`, `waitingOnParty`, `greenCount`).
- **New summary panels**, each backed by a real (not placeholder)
  computation: by-lifecycle-stage bid counts/ETB totals (a 5-bucket
  grouping of `BidStatus` - Identified/Preparing/Submitted/Evaluation/
  Won, excluding closed-lost outcomes since this is a live-pipeline
  snapshot), a "bids past opening with no result" callout on the Win
  rate card, and a Deal registrations panel (expiring-soon/pending-OEM/
  approved counts, plus PREPARING bids with an OEM brand but no linked
  deal registration).
- **Bid bonds outstanding** reverted from the card-list redesigned
  earlier in this session back to a dense table, matching the spec
  exactly - the two designs were evaluated against each other and the
  table won for this specific, highly tabular dataset.
- **A real header search** - "Search bids, customers, reference numbers"
  submits to `/bids?search=`, backed by a new case-insensitive substring
  match against title/organization name/reference number
  (`BidFilter.search`, `BidService.matches`) rather than a decorative,
  non-functional input.
- A real CSS bug was caught and fixed during live verification: the new
  `.pill` (division filter) and `.next-week-item` links both read in the
  wrong color once *visited*, because a plain class selector loses to
  `a:visited` at equal specificity - the exact same class of bug the
  bid-card redesign already has a comment about. Fixed by including
  `:visited` explicitly in both selectors, matching the existing
  `.btn:visited` pattern.

Live-verified end to end against the real `bidmgmt` database: every
section rendering real figures (not zeros/placeholders), the division
filter actually narrowing every widget on the page while the pills
themselves stayed stable, the search bar returning the correct bids,
dark theme readability, and the visited-link color bug caught and fixed
before calling this done.

## Lifecycle completeness (closing the pipeline gaps)

A lifecycle evaluation of the five-stage pipeline (Opportunity -> Deal
Registration -> Bid -> Contract -> Logistics) found two different kinds
of gap: Opportunity and Deal Registration had no guarded transitions
(stage/status were plain fields, settable to anything), while Contract
and Logistics had a fully guarded backend and REST API but no node-bff UI
at all - reachable only through direct API calls. A 4-phase plan closes
both, and all 4 phases are now done.

**Phase 1** - guarded transitions for Opportunity and Deal Registration:

- **`OpportunityService`** gained the same `EnumMap<OpportunityStage,
  Set<OpportunityStage>>` guarded-transition pattern `BidStatusService`
  already uses: Lead -> Qualified -> RFI/Proposal -> Expecting tender,
  with Lost/Closed as a side exit from any non-terminal stage (now
  requiring a reason, same as Bid's Dropped/Cancelled). Converted to Bid
  is deliberately *not* reachable through this guard - it stays exclusive
  to `convertToBid()`, which also creates the Bid and links
  `convertedBid`; allowing it through the generic update would let
  someone claim an opportunity converted without an actual Bid behind it.
- **`DealRegistrationService`** gained the equivalent table: Draft ->
  Submitted -> Approved or Rejected, Approved -> Expired. Rejected and
  Expired have no outgoing transitions, matching a new
  `DealRegistrationStatus.isTerminal()` (added for consistency with every
  other lifecycle enum in this codebase) - the existing conflict-check
  logic (`conflictsFor`) was refactored to use it instead of its own
  inline rejected/expired check.
- **A related, previously-broken piece of UX got fixed as part of this**:
  a rejected guarded transition (on Bid too, not just the two new ones)
  used to surface as a bare, message-less 500 - Spring Boot's default
  error handling doesn't include the exception message in the response
  body unless something explicitly puts it there, and nothing did. Added
  `GlobalExceptionHandler` (`@RestControllerAdvice`) mapping
  `IllegalStateException` -> 409, `IllegalArgumentException` -> 400, and
  `NoSuchElementException` -> 404, each with the real message in the
  body. node-bff's `apiClient.js` now prefers that message over its own
  generic "API call failed" string, and the three status-change routes
  (bid, lot, opportunity, deal registration) catch a 400/409 and flash
  the real message back to the same page instead of bouncing to the
  generic error page.

Live-verified end to end: an illegal opportunity stage transition (Qualified
-> Expecting tender, skipping RFI/Proposal) rejected with a clear flash
message and the stage unchanged; the legal next step succeeding; Lost/Closed
correctly requiring a reason and then succeeding once one was supplied;
a terminal-stage opportunity correctly rejecting any further transition;
and the same illegal/legal/terminal sequence repeated for a deal
registration (Approved -> Draft rejected, Approved -> Expired succeeding).

**Phase 2** - Contract management UI, the first node-bff screens Contract
has ever had:

- New `/contracts` (list - bid, contract number, status, delivery
  deadline, indicator), `/contracts/new` (creates from a WON bid - either
  pre-selected via `?bidId=` from that bid's own detail page, or picked
  from a dropdown of WON bids that don't have one yet), and
  `/contracts/:id` (detail - status-transition form reusing phase 1's
  flash-error pattern, an edit form, and a Logistics section listing that
  contract's shipments - empty until phase 3 adds the UI to create one).
  No backend changes were needed - `ContractController`'s REST API was
  already complete; this phase is purely the missing frontend.
- The Bid detail page gained a Contract section, shown only for WON bids:
  a link to the existing contract if one exists, or a "+ Create contract"
  link into `/contracts/new?bidId=` if not.
- Added "4 Contracts" to the sidebar's Lifecycle group (deliberately left
  out until now, since there was nowhere for it to link to).
- Extended node-bff's `statusBadgeClass` helper to color-code Contract
  and Logistics statuses too (it previously only recognized Bid's own
  status names and fell back to an uncolored badge for everything else).

Live-verified end to end against the real `bidmgmt` database: walked a
real bid through its full guarded status chain to WON via the existing
UI, created a contract from its detail page, confirmed the one-contract-
per-bid rule rejects a second attempt with a clear message, an illegal
status transition (Awarded -> In Delivery, skipping Signed) rejected the
same way phase 1's bid/opportunity/deal-registration transitions are,
and the legal next step (Awarded -> Signed) succeeding with `signedAt`
auto-populated - all reflected correctly on both the list page and the
originating bid's detail page.

**Phase 3** - Logistics management UI, mirroring phase 2's pattern
exactly since Logistics' backend (guarded transition table, REST API)
was already just as complete as Contract's:

- New `/logistics` (list across every contract - fetches each contract's
  shipments and flattens them, since there's no single "all shipments"
  endpoint), `/logistics/new` (creates for a contract - pre-selected via
  `?contractId=` from that contract's own detail page, or picked from a
  dropdown of all contracts, since unlike Contract-to-Bid a contract can
  have more than one shipment), and `/logistics/:id` (detail - the same
  status-transition-plus-edit-form shape as Contract's). Again, no
  backend changes needed.
- The Contract detail page's Logistics section (stubbed in phase 2) now
  has a working "+ Add shipment" link, and its shipment rows link out to
  their own detail pages.
- Added "5 Logistics" to the sidebar's Lifecycle group, completing it.

Live-verified end to end: created a shipment from a real contract's
detail page, confirmed it pre-filled and locked the contract; an illegal
status transition (Planning -> Delivered, skipping four stages) rejected
with a clear message; the legal next step (Planning -> Dispatched)
succeeding with `dispatchedAt` auto-populated; and the result reflected
correctly across all three surfaces - the shipment's own detail page, the
flat `/logistics` list (joined back to its contract), and the originating
contract's embedded Logistics table.

**Phase 4** - the two smaller gaps noted during the evaluation, closing
out this initiative:

- **`LogisticsService.create`** now rejects a shipment for a contract
  that isn't Signed or In Delivery (mirroring Contract's own WON-bid
  gate) - previously a shipment could attach to a contract in any status,
  including one that had been Terminated.
- **`Bid.goNoGoDecision`/`goNoGoReason`** were already fully wired on the
  backend (`UpdateBidRequest`, `BidService.update`, audited like every
  other bid field) but had no UI anywhere - not even as part of a larger
  bid-edit form, since the bid detail page has never had general-purpose
  field editing (only status changes and a handful of specific per-lot
  actions). Rather than build that larger, out-of-scope edit form, added
  a small dedicated "Go / no-go" card to the bid detail page with just
  these two fields, following the same pattern as the status-change card
  next to it.

Live-verified end to end: an attempt to add a shipment to a freshly
created (Awarded) contract rejected with "A shipment can only be added to
a contract that is Signed or In Delivery (this one is AWARDED)"; and
setting a bid's Go/No-go decision to Go with a reason saved correctly and
displayed back pre-filled on reload. Added `LogisticsServiceTest` - the
first test coverage this service has had - covering both the new creation
gate (Awarded/Terminated rejected, Signed/In Delivery allowed) and its
existing status-transition guard; all 137 backend tests and all node-bff
tests pass.

## Delay tracking for Contract & Logistics

A follow-on to the lifecycle-completeness work above: Contract and
Logistics already computed their own Green/Amber/Red/Critical indicator
(`ContractMapper`/`LogisticsMapper`, shipped back in stage-tracking phase
2) and showed it as a badge on their own detail pages, but nothing
*aggregated* across them the way BidLot's delays are aggregated - they
never appeared on `/delays`, couldn't have a delay reason recorded when
exiting a bad stage, and had no follow-up/snooze workflow. This closes
that specific gap. One thing stays deliberately out of scope, noted
below rather than silently absorbed into this change: `MyFollowUpsService`
still only surfaces LOT rows. (The dashboard's own "Where deals are
stuck" widget was extended to Contract/Logistics too - see the next
section.)

- **`DelayBoardRowDto`** gained `entityType`/`entityId`/`detailHref` -
  the generic identity a board row needs to point anywhere, not just at
  a bid lot. `bidId`/`bidTitle`/`lotId`/`lotLabel` stay exactly as they
  were for LOT rows (null for the other two) rather than being renamed,
  so the only node-bff changes needed were switching the link and the
  follow-up/snooze form's hidden fields from the old hardcoded
  `/bids/<bidId>` and `entityType=LOT` to the new generic fields - the
  rows' display logic (title/subtitle/badges) didn't need to change at
  all.
- **`DelaysBoardService`** now merges three sources per call: BidLot (as
  before), every open Contract, and every open Logistics shipment -
  `computeContractRow`/`computeLogisticsRow` mirror `ContractMapper`/
  `LogisticsMapper`'s own indicator computation exactly (same
  `TargetContext`, same deadline field), so the board and an entity's
  own badge can never disagree. Contract/Logistics rows carry no
  division, OEM, or account officers - those concepts live on BidLot,
  not on their parent Contract, so those fields are simply null/empty
  rather than guessed at.
- **Delay-reason-on-exit enforcement** was extended to Contract and
  Logistics, mirroring `BidStatusService.transitionLot`'s existing rule
  exactly: exiting a Red/Critical stage now requires a
  `delayReasonCode`, surfaced via the same dropdown (fed by
  `/api/delay-reason-codes`) already used on the bid lot status form.
  `ContractController`/`LogisticsController` already carried
  `StatusChangeRequest.delayReasonCode()` through their `@Valid` request
  body - it just wasn't being passed to the service layer yet.
- Added `ContractServiceTest` (no test existed for this service before -
  the creation gate, the transition table, and the new delay-reason
  rule), extended `LogisticsServiceTest` with the same, and added
  `DelaysBoardServiceTest` (no test existed for this service either) -
  a Critical contract row, a Red logistics row linked back to its bid, a
  Terminated contract correctly excluded, and a plain LOT row proving
  the existing behavior is unaffected.

Live-verified end to end against the real `bidmgmt` database: the
`/delays` board rendered with no errors against real Contract/Logistics
data (confirmed via server logs showing no exceptions from the new
scanning code, exercised against actual JPA-loaded entities, not just
mocks); the new "Delay reason" dropdown rendering correctly on both the
Contract and Logistics status-update forms; and a real status transition
on a live contract (Signed -> In Delivery) still succeeding exactly as
before the change.

## Extending the dashboard's delay widget to Contract & Logistics

A direct follow-on to the above: the dashboard's "Where deals are stuck"
widget and "Behind stage target" stat card had their own separate,
BidLot-only `DelayRowDto`/`delaySummary()` in `DashboardService`, called
out at the time as the one deliberately-deferred piece of the `/delays`
board extension. This closes that gap by having the dashboard reuse
`DelaysBoardService` instead of duplicating a third scanning loop.

- **`DelaysBoardService`** gained a private `allRows()` helper (the
  BidLot/Contract/Logistics scan `list(Filter)` already did, now shared)
  and a new `summary(UUID divisionId, boolean noDivisionOnly, int
  topLimit)` method, returning the same green/amber/red/critical/stale
  tally and top-N-by-`percentUsed` list the dashboard has always shown,
  now sourced from all three entity types instead of BidLot alone.
  `divisionId` scopes to one division (Contract/Logistics rows carry no
  division, so they're naturally excluded - same as the real `/delays`
  board's own filter already behaves); `noDivisionOnly` is a dashboard-only
  concept (the "No division" pill) with no equivalent on the real board,
  satisfied by any row with a null `divisionId` - which includes every
  Contract/Logistics row, since they have no division to exclude them
  either way.
- **`DelaySummaryDto.topDelayed`** changed from `List<DelayRowDto>` to
  `List<DelayBoardRowDto>`, and `DelayRowDto` was deleted outright - its
  only two consumers (`DashboardService`, `WeeklyBottleneckSchedulerService`)
  are both migrated. `DashboardService` no longer scans bids itself for
  this; `delaySummary(String divisionFilter)` just translates the
  dashboard's three-state division-filter string (blank/`"none"`/UUID,
  the same parsing `applyDivisionFilter` already does for every other
  widget) into `DelaysBoardService.summary(...)`'s parameters.
  `IndicatorService` and `WaitingOnService` are no longer dependencies of
  `DashboardService` at all - both were only ever used inside the method
  just deleted.
- **`WeeklyBottleneckSchedulerService`** (the Monday-morning top-10 email)
  now points `toAlertItem` at `DelayBoardRowDto.detailHref()` instead of a
  hardcoded `"/bids/" + row.bidId()`, so a Contract or Logistics entry
  in the weekly digest now links to its own page instead of a bid page
  that may not even be the right one.
- **`dashboard.ejs`**'s delay-item cards switched from `row.bidId`/
  `row.level` to `row.detailHref`/`row.indicatorLevel`, with an
  `entityType` badge next to the title for non-LOT rows - the same
  minimal-diff pattern the `/delays` board template already used.
- Added three tests to `DelaysBoardServiceTest` for the new `summary()`
  method (a Contract counted alongside a Lot in the overall tally and
  ranking, a specific-division scope excluding both the Contract and a
  differently-divisioned Lot, and the "no division" bucket including both
  the Contract and an undivided Lot), and updated `DashboardServiceTest`'s
  setup for the new `DelaysBoardService` dependency. All 152 backend
  tests pass.

Live-verified against the real `bidmgmt` database: the dashboard's stat
card and widget render real counts (55 critical / 69 stale across all
divisions at verification time), the "No division" pill correctly folds
in the system's two live Contracts (both GREEN/UNKNOWN, so invisible in
the "stuck" view, but present in the on-track tally), the "Security"
division pill correctly excludes those same Contracts, and clicking a
delay card's link navigated to the correct bid detail page.

## Extending My Follow-ups to Contract & Logistics

The last of the deliberately-deferred gaps from the two sections above:
`MyFollowUpsService` still only ever surfaced BidLot rows, even though
Contract and Logistics can now have a follow-up owner assigned from the
Delays board. This closes that gap.

- Neither Contract nor Logistics has an account-officer concept (no
  equivalent of `lot_account_officers` exists for them), so unlike
  BidLot - where the officer-or-follow-up-owner split governs which rows
  a user sees, and only the assigned officer sees the proactive
  turning-amber-soon/stale buckets - for these two, follow-up ownership
  alone gates all four buckets. A shared private `classify(...)` helper
  takes a `isProactivelyOwned` flag (the assigned officer for a LOT, the
  follow-up owner for everything else) so the BidLot branch's existing
  behavior is untouched.
- `DelaysBoardService.computeContractRow`/`computeLogisticsRow` were
  made public (mirroring `computeRow`'s existing visibility) so
  `MyFollowUpsService` reuses the identical row computation the Delays
  board uses - the two can never disagree.
- node-bff's `myFollowUps.ejs`/`myFollowUps.js` switched from the old
  `lotId`-only link/form-field pattern to the generic
  `entityType`/`entityId`/`detailHref` fields the Delays board already
  uses, with an entity-type badge for non-LOT rows.
- Added `MyFollowUpsServiceTest` (no test existed for this service
  before) - an overdue Contract follow-up showing up for its owner, one
  owned by someone else correctly excluded, a green Contract near the
  amber threshold landing in "turning amber soon", and a stale Logistics
  shipment landing in "stale".

**Real bug found via live browser verification, not caught by any unit
test**: `activity_log` and `attachments` were both created in
`V1__init.sql`, before `CHECKLIST_ITEM`, `CONTRACT` and `LOGISTICS`
existed in the `EntityType` enum, with a `CHECK (entity_type IN ('BID',
'LOT', 'OPPORTUNITY', 'DEAL_REGISTRATION'))` constraint. Every other
polymorphic table added since (`stage_history`, `hold_periods`,
`stage_targets`, `waiting_on_state`, `follow_up_state`, `alert_state`,
`stage_milestones`) had its constraint correctly widened in V8/V10/V12 -
these two were missed, and nothing had ever tried to write a
`CONTRACT`/`LOGISTICS` row through them until this change made it
reachable: submitting a follow-up note against a Contract from the
Delays board's already-shipped generalized form 500'd with
`activity_log_entity_type_check` violated. Pure-Mockito unit tests
(this one included) mock the repository layer entirely, so they have no
way to catch a database constraint mismatch like this - only a real
Postgres round-trip can. Fixed with
`V13__widen_activity_log_and_attachments_entity_types.sql`, confirmed by
re-running the same live follow-up submission end to end: it reached
`/my-follow-ups`'s Overdue bucket with the correct entity badge, link,
and status, and the Delays board's sidebar count updated accordingly.

## Extending delay/indicator coverage to Opportunity & Deal Registration

The last non-BidLot gap from the stage-tracking initiative's original
scope: Opportunity and DealRegistration already computed their own
indicator badge (stage-tracking phase 2), but had no delay-reason
enforcement on exit, no presence on the Delays board or dashboard
widget, and no My Follow-ups coverage - all three BidLot/Contract/
Logistics already had. This closes that gap the same way the Contract/
Logistics work did, entity by entity.

- **Delay-reason-on-exit**: `OpportunityService.update` and
  `DealRegistrationService.update` each gained a
  `requireDelayReasonIfRedOrCritical` helper - same rule as
  `ContractService.transitionStatus`'s (moving out of a red/critical
  stage needs a `delayReasonCode`), computed via the exact same
  `IndicatorService.compute` call each entity's own mapper already makes
  for its badge, so the enforcement and the badge can never disagree.
  Both `UpdateOpportunityRequest` and `UpdateDealRegistrationRequest`
  gained a `delayReasonCode` field (bound automatically, no controller
  changes needed - same as Contract/Logistics before), and the node-bff
  "Update stage"/status forms gained the same dropdown already used
  everywhere else.
- **Delays board**: `DelaysBoardService.computeOpportunityRow`/
  `computeDealRegistrationRow` mirror `OpportunityMapper`/
  `DealRegistrationMapper`'s `indicatorFor` exactly - including
  DealRegistrationMapper's APPROVED exclusion (not technically terminal,
  since it can still move to EXPIRED, but no longer "in progress," so it
  shouldn't clutter a delay board). Since `DashboardService` already
  delegates its "Where deals are stuck" widget to this board's
  `summary()` (from the dashboard-widget extension above), the dashboard
  picked up both new entity types automatically - no changes needed
  there at all.
- **My Follow-ups**: extended the same way Contract/Logistics were -
  follow-up ownership alone gates all four buckets (neither Opportunity
  nor DealRegistration has an account-officer concept). With this
  extension, the Contract/Logistics/Opportunity/DealRegistration loops
  became four copies of an identical pattern, so they were factored into
  one generic `evaluateFollowUpOwnerOnly(entities, entityType, isOpen,
  idOf, rowOf, ...)` helper instead of a fifth copy-pasted block.
- **A real template gap surfaced by Opportunity specifically**: every
  prior non-LOT row type (Contract, Logistics, Deal Registration) had a
  natural "parent" to show as the row's subtitle (the bid title, or the
  registration ID) - Opportunity doesn't, since it's the top of its own
  chain. `DelayBoardRowDto.lotLabel()` is `null` for an Opportunity row,
  which `delays.ejs`/`myFollowUps.ejs` previously rendered as the
  literal string "null" (JavaScript's `String(null)`). Both templates
  now skip the subtitle span entirely when `lotLabel` is falsy.
- Added 2 new delay-reason tests each to `OpportunityServiceTest`/
  `DealRegistrationServiceTest`, 4 new `DelaysBoardServiceTest` cases (a
  critical Opportunity row, a converted-to-bid one excluded, a red Deal
  Registration row reusing `oemBrand` for OEM-filter parity with LOT
  rows, an approved one excluded), and 4 new `MyFollowUpsServiceTest`
  cases mirroring the Contract/Logistics ones.
- Live-verified end to end against the real `bidmgmt` database: created
  a test Opportunity and a test Deal Registration, confirmed the new
  delay-reason dropdown renders on both detail pages, a normal
  (non-red/critical) stage/status transition still succeeds without a
  delay reason, and a follow-up logged against each via the Delays
  board's existing generic form shows up correctly in My Follow-ups
  (right entity badge, title, link, date) and clears correctly - all
  with zero errors in the server logs.
- **Deliberately out of scope, not silently absorbed**:
  `DelayAlertSchedulerService` (the proactive email/escalation alerts)
  and `DelayAnalyticsService` (bottleneck reports) remain BidLot/
  Contract/Logistics only. Opportunity has an `owner` field that could
  plausibly receive alerts the way BidLot's account officers do, but
  DealRegistration has neither an owner nor a division - who should
  receive an Opportunity/DealRegistration alert is a product decision,
  not something to guess at, so it's left as a follow-up for the user to
  weigh in on rather than done here.

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

The dashboard redesign extended `DashboardServiceTest` with three more
cases: `readinessGapsFlagsEveryMissingSignal` (a bid missing every one of
the eight signals - division, officer, go-no-go decision, MAF, closing
time, bond verification, scope - gets every matching gap badge, asserted
`containsExactlyInAnyOrder` so the test fails if a gap is silently added
or dropped later), `readinessGapsEmptyWhenBidIsFullyReady` (the inverse -
a fully staffed, priced, decided bid produces zero gaps), and
`divisionFilterNarrowsEveryWidgetWhilePillCountsStayUnfiltered` (filtering
to one division narrows both `closingThisWeek` and `pipelineByDivision`,
while the division pills themselves keep reporting both divisions' real,
unfiltered counts - the regression this test caught during development:
the pills were silently empty because the test's `DivisionRepository`
mock hadn't been given the test's own `Division` objects).

The lifecycle-enforcement work added a new `OpportunityServiceTest`
(pure-Mockito, mirroring `BidStatusServiceTest`'s pattern) covering
skipped-stage rejection, the legal next-stage success path, Converted-to-
Bid being unreachable through the generic guard, Lost/Closed's required-
reason rule both failing and succeeding, and a terminal stage rejecting
any further move - and extended `DealRegistrationServiceTest` with the
equivalent three cases for its own transition table.

Phase 4 added `LogisticsServiceTest` - the first test coverage
`LogisticsService` has ever had - covering the new contract-status
creation gate (rejected for Awarded and Terminated, allowed for Signed
and In Delivery) and the existing status-transition guard (skipped-stage
rejection, the legal next-stage success path, and the right timestamp
field getting stamped on each transition).

The Contract/Logistics delay-tracking follow-on added `ContractServiceTest`
(no test existed for this service before - the WON-bid/one-per-bid
creation gate, the transition table, and the new delay-reason-on-exit
rule), extended `LogisticsServiceTest` with the equivalent delay-reason
case, and added `DelaysBoardServiceTest` (no test existed for this
service either, despite it already backing a real page) - a Critical
contract row with the right title/subtitle/link, a Red logistics row
correctly linked back to its parent bid, a Terminated contract excluded
from the board entirely, and a plain BidLot row proving the merge didn't
change existing behavior.

## Deployment

See `DEPLOYMENT.md` for running this on a single on-premise Linux server:
first-time setup, TLS (self-signed cert included for local HTTPS - read
this before exposing the app outside a trusted network with it), email/SMTP
config, the Keycloak Admin API user-provisioning setup, Telegram bot setup,
backups, and updates.
