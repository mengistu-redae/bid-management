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
data but have no login yet - the Director can wire one up later by creating
a matching-email Keycloak user (the app links by email on first login).

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

## What's built so far

**Phase 1** - data model, auth/roles, Bid+Lot CRUD with the full status
lifecycle (Identified -> Under Review -> Preparing -> Submitted -> Opened ->
Under Evaluation -> Won/Lost, with Dropped/Cancelled side exits, each lot
able to diverge independently), per-lot prep checklist, activity log,
status history, and the two-step (preview, then confirm) Excel importer for
both sample files - live-verified end to end against a real `docker compose`
stack and a real browser login.

Not yet built (later phases per the brief): dashboard, Kanban/calendar
views, richer filters, Excel export, Opportunities, Deal Registrations,
Bid Bonds as their own entity, email reminders/digest, reports.

## Tests

```bash
cd spring-boot-api && mvn test
```

Covers the importer's parsing rules (`ParsingUtilsTest`, against real values
from the sample spreadsheets), the status-lifecycle transition rules
(`BidStatusServiceTest`), the per-role permission rules (`BidAccessServiceTest`),
the organization name-matching/merge logic (`OrganizationResolutionServiceTest`),
and a full live import of both real sample files against a real Postgres
(`ImportServiceLiveFilesTest`) - the last one needs a reachable Postgres (the
`docker compose` one works; it uses a separate `bidmgmt_test` database so it
never touches real/seeded data).
