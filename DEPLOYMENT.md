# Deployment

This app is meant to run on a single on-premise Linux server via the
`docker-compose.yml` at the repo root - no Kubernetes, no cloud services.
Everything (nginx, node-bff, spring-boot-api, Keycloak, Postgres, Redis,
and the dev-only mailpit catcher) runs as containers on one box.

## Prerequisites

- A Linux server (any distro) with Docker Engine + the Docker Compose plugin
  installed (`docker compose version` should work).
- Outbound internet access during the first build (Maven/npm dependency
  downloads) - after that, nothing needs to phone home.
- A real SMTP server/relay to send through (Phase 4's email reminders and
  daily digest) - see "Email" below. The bundled `mailpit` service is a
  local dev-only catcher, not a real mail transport.

## First-time setup

```bash
git clone <this repo> bid-management
cd bid-management
cp .env.example .env
# edit .env: set POSTGRES_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, SESSION_SECRET
# to real random values, and the public-hostname / SMTP vars below.

docker compose up -d --build
```

First boot takes a few minutes (Keycloak's `start-dev` + realm import is
slow cold, and the Maven/npm builds have real dependency downloads the
first time). Once `docker compose ps` shows everything healthy:

1. The Keycloak client secret for `bidmgmt-bff` is auto-generated on realm
   import - open the admin console (`http://<host>:8090`, `admin` / the
   `KEYCLOAK_ADMIN_PASSWORD` you set), go to the `bidmgmt` realm -> Clients
   -> `bidmgmt-bff` -> Credentials, copy the secret into `.env` as
   `BFF_CLIENT_SECRET`, then `docker compose up -d --force-recreate node-bff`.
2. spring-boot-api's `SeedDataRunner` auto-imports the two sample
   spreadsheets from `docs/` into an empty database on first boot. Remove
   those volume mounts in `docker-compose.yml` (or just don't rely on them)
   once the real spreadsheets have been superseded by live data.
3. Log in at `http://<host>:82` (or whatever hostname fronts nginx) with the
   seed users from `infra/keycloak/realm-export.json` - see the main
   README's login table. Everyone has a forced password reset on first
   login; have the Director reset the four seeded accounts' passwords.
4. (Optional) run `infra/nginx/generate-self-signed-cert.sh` to enable
   HTTPS on `:8443` - see "TLS" below.
5. Wire up logins for the account officers/scouts seeded as reference rows
   from the Excel import: Director -> Users -> find the person -> "Grant
   login" (add an email first via "Edit" if they don't have one on file).
   This calls Keycloak's Admin REST API directly from the app - no more
   manual admin-console user creation needed. See "User management" below.

## Running on a real hostname (not localhost)

This matters once the app isn't being hit at `http://localhost:82` from the
same machine. Keycloak embeds a browser-facing issuer URL in every token
(see `node-bff/src/auth/oidc.js`'s comment on the public/internal issuer
split), and node-bff's own cookies/redirects need to know their own public
origin. Set in `.env`:

```
KEYCLOAK_ISSUER_PUBLIC_URI=https://bidmgmt.example.com/realms/bidmgmt
BFF_BASE_URL=https://bidmgmt.example.com
```

Also update the `bidmgmt-bff` Keycloak client's **Valid redirect URIs** and
**Valid post logout redirect URIs** in the admin console (they're seeded
from `infra/keycloak/realm-export.json` pointing at `localhost:3002`) to
match the real hostname, and restart node-bff and spring-boot-api
(`docker compose up -d --force-recreate node-bff spring-boot-api`) to pick
up the new env vars.

## TLS

nginx serves HTTPS on `:8443` alongside plain HTTP on `:82` (the HTTP
listener is never redirected, so existing local workflows against `:82`
keep working unchanged). The cert is **self-signed** - generate it once,
on the host, before first starting nginx:

```bash
infra/nginx/generate-self-signed-cert.sh
```

This writes `infra/nginx/tls/fullchain.pem` and `privkey.pem` (gitignored,
bind-mounted read-only into the nginx container). It only needs to run
once; re-running is a no-op unless you delete those files first. Browsers
will show a certificate warning for a self-signed cert - expected, and not
something to click through for anything beyond local testing.

For a real deployment, replace those same two files with a real
certificate (e.g. from Let's Encrypt via certbot, or your corporate CA) at
the same two paths, and change nginx.conf's plain-HTTP `server` block to
`return 301 https://$host$request_uri;` instead of proxying - see the
comments in `infra/nginx/nginx.conf`. Until that's done, treat `:82`
(HTTP, credentials sent in the clear) as unsuitable for anything beyond a
trusted local network.

### Why not generate the cert in the nginx container itself?

An earlier version did `apk add --no-cache openssl` inside nginx's startup
command, since `nginx:alpine` doesn't ship the `openssl` CLI by default
(only the library it's linked against). That works, but pulling a package
from Alpine's CDN on every fresh `docker compose up` made first start
unreliably slow on a constrained connection - generating the cert on the
host instead has no such dependency.

## User management

The Director-only `/users` page (spring-boot-api's `UserController`/
`UserService`) creates `app_users` rows directly and, separately, grants a
real Keycloak login on demand via Keycloak's Admin REST API. That API call
authenticates as the Keycloak admin using the master realm's built-in
`admin-cli` client (Resource Owner Password Credentials grant) - the same
`KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` credentials already set for the
`keycloak` compose service above, reused automatically (see
`KEYCLOAK_ADMIN_BASE_URL` in `docker-compose.yml`, pointed at the internal
`http://keycloak:8080`). No separate credential or Keycloak client setup is
needed - if you can reach the Keycloak admin console with those
credentials, "Grant login" works.

A person needs an email on file before they can be granted a login (it
becomes their Keycloak username). Once granted, their email becomes
read-only in the app - Keycloak's own record is the source of truth from
that point on, and the app never writes back to it.

## Email (Phase 4 reminders and digest)

spring-boot-api sends bid-closing/clarification/deal-registration-expiry/
bid-bond reminders and the Director's daily digest via SMTP
(`spring-boot-starter-mail`). In `docker-compose.yml`, the dev default
points `spring-boot-api` at the bundled `mailpit` service (a local SMTP
catcher with a web UI at `:8026` - nothing it "sends" actually leaves the
server). For production, set in `.env`:

```
MAIL_HOST=smtp.your-provider.com
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
BIDMGMT_NOTIFICATIONS_FROM=bidmgmt@motiengineering.com
BIDMGMT_DIRECTOR_EMAIL=mengistu.redae@motiengineering.com
BIDMGMT_APP_BASE_URL=https://bidmgmt.example.com
```

then `docker compose up -d --force-recreate spring-boot-api`. Set
`BIDMGMT_NOTIFICATIONS_ENABLED=false` to disable sending entirely (the
reminder job still runs on its daily schedule but every send is a no-op) -
useful while testing other things without spamming real inboxes. The
reminder/digest schedule itself (`BIDMGMT_REMINDER_CRON` in
`spring-boot-api/src/main/resources/application.yml`, default `0 0 7 * * *`
- 7am `Africa/Addis_Ababa` daily) isn't exposed as a compose env var yet;
edit `application.yml` directly and rebuild if it needs to change.

Every send is deduped against the `notification_log` table (one row per
reminder type + entity + recipient + day), so the job is safe to run more
than once in a day or to restart mid-run without double-sending.

## Telegram notifications (optional)

`TelegramNotificationChannel` is a second, additive channel alongside
email - nothing to configure means it silently sends to no one. To turn it
on:

1. Create a bot via [@BotFather](https://t.me/BotFather) in Telegram
   (`/newbot`, follow the prompts) and copy the token it gives you.
2. Set `BIDMGMT_TELEGRAM_BOT_TOKEN` in `.env` to that token, then
   `docker compose up -d --force-recreate spring-boot-api`.
3. Each recipient needs their own Telegram chat id saved on their profile:
   have them message the bot, then look up their chat id (e.g. via
   `https://api.telegram.org/bot<token>/getUpdates`, or a helper bot like
   `@userinfobot`), and enter it on their user management edit page
   (Director -> Users -> Edit -> "Telegram chat ID").

Messages are plain text, not the HTML `EmailTemplates` builds for email -
Telegram's Bot API only renders a small HTML subset, so tags are stripped
rather than translated. This hasn't been live-verified against a real bot
(none was available while building it) - `TelegramNotificationChannelTest`
covers the request shape against a mocked HTTP server instead. Verify a
real send once a bot token is available before relying on it.

## Backups

Two Docker volumes hold everything that matters: `postgres_data` (all app
data - bids, deal registrations, opportunities, users, notification log)
and `uploads_data` (bid attachments). Back up both. A simple daily
`pg_dump` cron on the host, run outside any container's lifecycle:

```bash
#!/bin/sh
# /etc/cron.daily/bidmgmt-backup (chmod +x)
BACKUP_DIR=/var/backups/bidmgmt
mkdir -p "$BACKUP_DIR"
docker compose -f /path/to/bid-management/docker-compose.yml exec -T postgres \
  pg_dump -U bidmgmt bidmgmt | gzip > "$BACKUP_DIR/bidmgmt-$(date +%F).sql.gz"
# Keep 30 days
find "$BACKUP_DIR" -name '*.sql.gz' -mtime +30 -delete
```

For `uploads_data`, either back up the volume directly
(`docker run --rm -v bid-management_uploads_data:/data -v "$BACKUP_DIR":/backup alpine tar czf /backup/uploads-$(date +%F).tar.gz -C /data .`)
or mount it to a host path instead of a named volume if your backup tooling
prefers plain filesystem paths.

Restore is the reverse: `gunzip -c backup.sql.gz | docker compose exec -T postgres psql -U bidmgmt bidmgmt` for the database, and untar back into the volume for uploads. Test this at least once before relying on it.

## Updating

```bash
cd bid-management
git pull
docker compose up -d --build
```

Flyway migrations run automatically on spring-boot-api startup against
whatever schema is currently there - no manual migration step. Compose
only recreates containers whose image actually changed, so this is safe to
run even when only one service changed.

## Logs

```bash
docker compose logs -f spring-boot-api   # reminder job output, API errors
docker compose logs -f node-bff          # auth/session/UI errors
docker compose ps                        # health status of every service
```

There's no centralized log shipping - this is a single-server deployment,
and `docker compose logs` (optionally with `--since`) is the whole story.
If retention beyond Docker's default log rotation matters, configure a
`logging` driver in `docker-compose.yml` (e.g. `json-file` with `max-size`/
`max-file`) or point the Docker daemon at a log forwarder.
