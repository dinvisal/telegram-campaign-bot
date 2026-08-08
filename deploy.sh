#!/usr/bin/env bash
#
# Deploy script for telegram-campaign-bot.
#
# Purpose: make DB credential drift IMPOSSIBLE to ship silently. The classic
# failure: a postgres volume carries the password from its FIRST init, and
# POSTGRES_PASSWORD in docker-compose.yml is ignored once the volume exists.
# If the two ever diverge, the app cannot connect and refuses to start.
#
# This script is SELF-HEALING:
#   1. Starts postgres only, waits for it to be healthy
#   2. Tests the configured DB_PASSWORD against the live database
#   3. If auth fails -> the volume is stale -> resets it automatically
#      (safe: campaigns re-sync from Facebook, pages re-seed from database/init)
#   4. Starts the app and waits for HikariPool connection before declaring success
#
# Usage:
#   ./deploy.sh                  # build + start + verify (self-healing)
#   ./deploy.sh --reset-db       # force volume reset even if auth works
#   ./deploy.sh --skip-build     # reuse existing image
#   ./deploy.sh --help

set -euo pipefail
cd "$(dirname "$0")"

APP_SERVICE="ads-manager-reporting-bot"
DB_SERVICE="postgres"

# ---------------------------------------------------------------------------
# Load .env (docker compose does this automatically; we need it for messages)
# ---------------------------------------------------------------------------
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

DB_PASSWORD="${DB_PASSWORD:-postgres}"

usage() {
  sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
}

RESET_DB=0
SKIP_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --reset-db)   RESET_DB=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    -h|--help)    usage ;;
    *) echo "Unknown argument: $arg" >&2; usage ;;
  esac
done

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
find_pgdata_volume() {
  docker volume ls --format '{{.Name}}' | grep -E '_pgdata$' || true
}

remove_pgdata_volume() {
  local volumes
  volumes=$(find_pgdata_volume)
  if [ -n "$volumes" ]; then
    echo ">>> Removing postgres volume(s): $volumes"
    # shellcheck disable=SC2086
    docker volume rm $volumes >/dev/null
  fi
}

wait_postgres_healthy() {
  echo ">>> Waiting for $DB_SERVICE to be healthy..."
  for i in $(seq 1 60); do
    local status
    status=$(docker inspect --format '{{.State.Health.Status}}' campaign-bot-db 2>/dev/null || echo starting)
    if [ "$status" = "healthy" ]; then
      echo ">>> $DB_SERVICE is healthy."
      return 0
    fi
    sleep 2
  done
  echo "!!! FATAL: $DB_SERVICE never became healthy." >&2
  docker logs campaign-bot-db 2>&1 | tail -10 >&2
  return 1
}

# Test the configured password against the live database.
# Returns 0 (OK) if auth succeeds, 1 (stale volume) if it fails.
test_db_password() {
  local network
  network=$(docker inspect campaign-bot-db --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null || true)
  if [ -z "$network" ]; then
    return 1
  fi
  docker run --rm --network "$network" \
    -e PGPASSWORD="$DB_PASSWORD" \
    postgres:16-alpine \
    psql -h postgres -U postgres -d campaign_bot -tAc "SELECT 1" >/dev/null 2>&1
}

# ---------------------------------------------------------------------------
# Optional: force a volume reset even if auth currently works
# ---------------------------------------------------------------------------
if [ "$RESET_DB" -eq 1 ]; then
  echo ">>> Forced reset requested (--reset-db)."
  docker compose down >/dev/null 2>&1 || true
  remove_pgdata_volume
fi

# ---------------------------------------------------------------------------
# SELF-HEALING: start postgres, verify credentials, reset if stale
# ---------------------------------------------------------------------------
echo ">>> Starting $DB_SERVICE..."
docker compose up -d "$DB_SERVICE" >/dev/null
wait_postgres_healthy

if test_db_password; then
  echo ">>> OK: DB_PASSWORD=$DB_PASSWORD works against the live database."
else
  echo "!!! Stale volume detected: password on volume does NOT match DB_PASSWORD=$DB_PASSWORD."
  echo "!!! Resetting volume automatically (data is disposable: re-seeds from database/init)."
  docker compose down >/dev/null 2>&1 || true
  remove_pgdata_volume
  docker compose up -d "$DB_SERVICE" >/dev/null
  wait_postgres_healthy
  if ! test_db_password; then
    echo "!!! FATAL: auth still failing after volume reset. Check .env / DB_PASSWORD." >&2
    exit 1
  fi
  echo ">>> OK: volume reset and DB_PASSWORD=$DB_PASSWORD now works."
fi

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
if [ "$SKIP_BUILD" -eq 0 ]; then
  echo ">>> Building image..."
  docker compose build
else
  echo ">>> Skipping build (--skip-build)."
fi

# ---------------------------------------------------------------------------
# Start full stack and verify the app connected
# ---------------------------------------------------------------------------
echo ">>> Starting stack..."
docker compose up -d

CONTAINER_ID=$(docker compose ps -q "$APP_SERVICE" 2>/dev/null || true)
if [ -z "$CONTAINER_ID" ]; then
  echo "!!! FATAL: app container not found. Did the build fail?" >&2
  exit 1
fi

echo ">>> Waiting for app to connect to PostgreSQL (fail-fast mode)..."
for i in $(seq 1 30); do
  LOGS=$(docker logs "$CONTAINER_ID" 2>&1 || true)

  if echo "$LOGS" | grep -q "HikariPool-1 - Added connection"; then
    echo ">>> OK: app connected to PostgreSQL."
    echo ">>> Deploy complete. Verify with: docker logs $CONTAINER_ID | grep Started"
    exit 0
  fi

  if echo "$LOGS" | grep -qE "password authentication failed|Schema-validation|Failed to validate|Unable to determine Dialect"; then
    echo "!!! FATAL: app could not connect to / validate the database." >&2
    echo "!!! Root cause:" >&2
    echo "$LOGS" | grep -E "Caused by|PSQLException|Connection refused|UnknownHost|authentication failed|Unable to determine Dialect" | tail -6 >&2
    exit 1
  fi

  sleep 2
done

echo "!!! FATAL: app did not connect to PostgreSQL within 60s." >&2
echo "!!! Root cause:" >&2
docker logs "$CONTAINER_ID" 2>&1 \
  | grep -E "Caused by|PSQLException|Connection refused|UnknownHost|authentication failed|Unable to determine Dialect|APPLICATION FAILED" \
  | tail -8 >&2
docker logs "$CONTAINER_ID" 2>&1 | tail -5 >&2
exit 1
