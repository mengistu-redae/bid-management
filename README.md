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

## Deployment

See `DEPLOYMENT.md` for running this on a single on-premise Linux server:
first-time setup, TLS (self-signed cert included for local HTTPS - read
this before exposing the app outside a trusted network with it), email/SMTP
config, the Keycloak Admin API user-provisioning setup, Telegram bot setup,
backups, and updates.
