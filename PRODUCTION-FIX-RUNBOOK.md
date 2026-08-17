# Production DB Repair Runbook — telegram-campaign-bot

**Date:** 2026-08-17 · **Server:** 128.199.150.196 (DigitalOcean droplet, Ubuntu)

## Verified diagnosis (from off-box)

| Check | Result |
|---|---|
| SSH root password auth (`q1w2e3r41Dw`) | Rejected — 8 attempts over 5 days (`Permission denied (publickey,password)`) |
| Postgres port 5432 from the internet | **Open** — server responds |
| Password `postgres` against live DB on 5432 | `FATAL: password authentication failed` |
| App behavior | Same error on every DB access (scheduled 07:00/12:30Z + manual `/sync`) |

**Root cause (confirmed):** the `pgdata` volume holds a password from an earlier initialization. `POSTGRES_PASSWORD` / `DB_PASSWORD` in docker-compose are **ignored once the volume exists**. The volume password is NOT `postgres` and does not match the app's env either — classic credential drift.

## Fix — run on the server (DigitalOcean console or SSH with valid credentials)

```bash
cd /root/www/telegram-campaign-bot 2>/dev/null || cd /var/www/telegram-campaign-bot 2>/dev/null || { echo "project dir not found"; exit 1; }

# Preferred: self-healing deploy script (re-asserts password via local trust socket)
if [ -f deploy.sh ]; then
  sh deploy.sh --skip-build
  exit $?
fi

# Manual equivalent
docker compose up -d postgres
docker exec campaign-bot-db psql -U postgres -c "ALTER USER postgres WITH PASSWORD 'postgres';"
docker compose up -d --force-recreate
```

If `ALTER USER` fails (unlikely — local socket is trusted), the volume must be reset:

```bash
docker compose down && docker compose down -v
docker compose up -d postgres
docker exec campaign-bot-db psql -U postgres -c "ALTER USER postgres WITH PASSWORD 'postgres';"
docker compose up -d --force-recreate
```

**Data safety:** only `facebook_pages` (re-seeded by `database/init/01-facebook-pages.sql` with token refresh) and `campaigns` (re-synced from Facebook) exist. No chat registrations are stored. The reset path loses nothing that isn't rebuilt automatically.

## Verify from anywhere (no SSH needed)

```bash
docker run --rm -e PGPASSWORD=postgres postgres:16-alpine \
  psql -h 128.199.150.196 -p 5432 -U postgres -d campaign_bot -tAc "SELECT 'DB CONNECTED'"
# Expected: DB CONNECTED   (currently: FATAL: password authentication failed)
```

## Prevention (so this never recurs)

1. **Always deploy with `./deploy.sh`** — it re-asserts `ALTER USER ... WITH PASSWORD '$DB_PASSWORD'` on every deploy (CI-tested in `.github/workflows/ci.yml`, job `self-healing-deploy`).
2. **Never rotate `DB_PASSWORD` independently** — if it changes, run `deploy.sh` immediately after.
3. App-side: fail fast at boot when DB auth is broken (the CI negative test asserts this), so broken deploys are caught at deploy time, not surfaced daily by the scheduler.

## Security advisory (found during diagnosis)

- **Postgres port 5432 was exposed to the public internet** on this droplet — it answered from anywhere. The compose file now binds it to `127.0.0.1` (loopback-only): not reachable from the internet, still usable from the host. Apply on production with `docker compose up -d postgres`.
- Defense in depth: also add a DigitalOcean Cloud Firewall rule blocking inbound 5432 from the internet.
- The app never needed the host port — it connects over the compose network (`jdbc:postgresql://postgres/campaign_bot`).
- Similarly, if root SSH password auth is needed, ensure `PermitRootLogin` is restricted and consider key-only auth after this incident.
