#!/usr/bin/env bash
#
# Deploy script for telegram-campaign-bot.
#
# Purpose: surface DB credential/schema mismatches AT DEPLOY TIME instead of
# letting the app boot half-broken and fail later on a /sync command.
#
# The classic failure: the postgres volume carries a password from its FIRST
# initialization. POSTGRES_PASSWORD in docker-compose.yml is ignored once the
# volume exists. If the two ever diverge, the app cannot connect — and with
# ddl-auto: validate + fail-fast Hikari, the app refuses to start entirely.
# This script verifies that and tells you exactly what to do.
#
# Usage:
#   ./deploy.sh                 # build + start + verify DB connection
#   ./deploy.sh --reset-db      # destroy pgdata volume first (re-seeds from database/init)
#   ./deploy.sh --skip-build    # reuse existing image
#   ./deploy.sh --help

set -euo pipefail
cd "$(dirname "$0")"

APP_SERVICE="ads-manager-reporting-bot"

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
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
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
# Optional: destroy the postgres volume (safe — data re-seeds / re-syncs)
# ---------------------------------------------------------------------------
if [ "$RESET_DB" -eq 1 ]; then
  echo ">>> Stopping stack..."
  docker compose down

  VOLUMES=$(docker volume ls --format '{{.Name}}' | grep -E '_pgdata$' || true)
  if [ -n "$VOLUMES" ]; then
    echo ">>> Removing postgres volume(s): $VOLUMES"
    # shellcheck disable=SC2086
    docker volume rm $VOLUMES
  else
    echo ">>> No pgdata volume found — nothing to remove."
  fi
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
# Start
# ---------------------------------------------------------------------------
echo ">>> Starting stack (DB_PASSWORD=$DB_PASSWORD)..."
docker compose up -d

# ---------------------------------------------------------------------------
# Verify the app actually connected to PostgreSQL
# ---------------------------------------------------------------------------
CONTAINER_ID=$(docker compose ps -q "$APP_SERVICE" 2>/dev/null || true)
if [ -z "$CONTAINER_ID" ]; then
  echo "!!! FATAL: app container not found. Did the build fail?" >&2
  exit 1
fi

echo ">>> Waiting for app to connect to PostgreSQL (fail-fast mode)..."

for i in $(seq 1 30); do
  LOGS=$(docker logs "$CONTAINER_ID" 2>&1 || true)

  # Happy path: Hikari got a connection and the app is up
  if echo "$LOGS" | grep -q "HikariPool-1 - Added connection"; then
    echo ">>> OK: app connected to PostgreSQL."
    echo ">>> Deploy complete. Verify with: docker logs $CONTAINER_ID | grep Started"
    exit 0
  fi

  # Fail-fast path: the exact symptoms we deploy to catch
  if echo "$LOGS" | grep -qE "password authentication failed|Schema-validation|Failed to validate"; then
    echo "!!! FATAL: DB credential or schema mismatch detected." >&2
    echo "!!! The postgres volume was initialized with a DIFFERENT password than DB_PASSWORD=$DB_PASSWORD." >&2
    echo "!!! Fix: ./deploy.sh --reset-db   (volume is disposable; data re-seeds from database/init)" >&2
    echo "!!! Full error:" >&2
    echo "$LOGS" | grep -iE "fatal|validation|authentication" | tail -5 >&2
    exit 1
  fi

  sleep 2
done

echo "!!! FATAL: app did not connect to PostgreSQL within 60s." >&2
docker logs "$CONTAINER_ID" 2>&1 | tail -20 >&2
exit 1
