#!/usr/bin/env bash
#
# PRAETOR — put the stack into the state the browser pass needs.
#
# The seed gives `Praetor Demo Round 1` a two-hour window with a 15-minute freeze, so the freeze
# does not open until 1h45m after seeding. Every freeze check is therefore untestable when you sit
# down, which is the sort of thing that eats a session. This script shifts the window so the freeze
# is already running, and optionally puts an accepted submission inside it so the board has
# something real to hide.
#
#   ./scripts/browser-pass-setup.sh check          preflight only, changes nothing
#   ./scripts/browser-pass-setup.sh freeze-open    freeze active now, ~90 min of runway
#   ./scripts/browser-pass-setup.sh hidden-accept  freeze-open, plus an AC inside the freeze
#   ./scripts/browser-pass-setup.sh reset          restore the seeded window
#
# Nothing here drops data. Re-seeding from scratch is still `docker compose down -v && up -d`,
# which is required after any schema change and is deliberately not automated here.

set -euo pipefail

DB=${DB:-praetor-db-1}
BASE=${BASE:-http://localhost:8080}
PASSWORD=${SEED_PASSWORD:-password}
LIVE_TITLE='Praetor Demo Round 1'

psql_q() { docker exec -i "$DB" psql -U praetor -d praetor -tAc "$1"; }

die() { echo "  ✗ $1" >&2; exit 1; }

# --- preflight --------------------------------------------------------------
# Each of these has cost someone an hour at least once, so they are checked before anything else.
preflight() {
  echo "Preflight"

  docker ps --format '{{.Names}}' | grep -q "^${DB}$" \
    || die "$DB is not running — docker compose up -d"
  echo "  ok   database container is up"

  curl -sf "$BASE/actuator/health" >/dev/null \
    || die "backend is not answering on $BASE"
  echo "  ok   backend is healthy"

  # The JDK lives only in the locally-built judge image. A stale one fails every Java submission
  # with "javac: not found", which reads like a code bug and is not one (steps E24–E26).
  if docker run --rm --entrypoint sh praetor-judge:latest -c 'command -v javac' >/dev/null 2>&1; then
    echo "  ok   judge image has javac"
  else
    die "judge image has no javac — docker build -t praetor-judge:latest judge/"
  fi

  # An unhashed bundle means you are looking at an image built before outputHashing landed, and a
  # stale bundle has twice been mistaken for a missing feature.
  if curl -s http://localhost:4200/ | grep -qE 'main-[A-Z0-9]+\.js'; then
    echo "  ok   frontend bundle is hashed (main-<hash>.js)"
  else
    echo "  !!   frontend bundle is NOT hashed — rebuild the frontend before trusting the pass"
  fi
}

contest_id() {
  local id
  id=$(psql_q "SELECT id FROM contests WHERE title = '$LIVE_TITLE'")
  [ -n "$id" ] || die "no contest titled '$LIVE_TITLE' — has the DB been seeded?"
  echo "$id"
}

show_window() {
  echo
  echo "$LIVE_TITLE now:"
  psql_q "SELECT '  starts   ' || to_char(starts_at, 'HH24:MI:SS')
               || E'\n  ends     ' || to_char(ends_at, 'HH24:MI:SS')
               || E'\n  freeze   ' || to_char(ends_at - make_interval(mins => freeze_min), 'HH24:MI:SS')
               || ' (last ' || freeze_min || ' min)'
               || E'\n  state    ' || CASE
                    WHEN now() < starts_at THEN 'not started'
                    WHEN now() > ends_at   THEN 'ended'
                    WHEN now() >= ends_at - make_interval(mins => freeze_min) THEN 'LIVE, FROZEN'
                    ELSE 'live, not yet frozen' END
          FROM contests WHERE title = '$LIVE_TITLE'"
}

# --- window shifts ----------------------------------------------------------
freeze_open() {
  # Started 30 min ago, ends in 90. A 100-minute freeze on a two-hour contest therefore began ten
  # minutes ago and runs to the end: frozen immediately, with 90 minutes to work in.
  psql_q "UPDATE contests
             SET starts_at = now() - interval '30 minutes',
                 ends_at   = now() + interval '90 minutes',
                 freeze_min = 100
           WHERE title = '$LIVE_TITLE'" >/dev/null
  echo "  ok   window shifted — the freeze is running now, for the next 90 minutes"
}

reset_window() {
  # Back to exactly what db/seed.sql writes.
  psql_q "UPDATE contests
             SET starts_at = now() - interval '10 minutes',
                 ends_at   = now() + interval '2 hours',
                 freeze_min = 15
           WHERE title = '$LIVE_TITLE'" >/dev/null
  echo "  ok   seeded window restored — live, freeze opens in 1h45m"
}

# --- an accept the board must hide -----------------------------------------
hidden_accept() {
  local cid=$1 token sub verdict tries

  token=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
            -d "{\"identifier\":\"bob\",\"password\":\"$PASSWORD\"}" \
          | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
  [ -n "$token" ] || die "could not log in as bob — are the seed bcrypt hashes real?"

  sub=$(curl -s -X POST "$BASE/api/submissions" \
          -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
          -d "{\"problemSlug\":\"a-plus-b\",\"contestId\":$cid,\"language\":\"CPP\",
               \"sourceCode\":\"#include <iostream>\\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<\\\"\\\\n\\\";}\"}" \
        | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
  [ -n "$sub" ] || die "submission refused — is bob registered, and is the cooldown clear?"

  for tries in $(seq 1 40); do
    verdict=$(curl -s -H "Authorization: Bearer $token" "$BASE/api/submissions/$sub" \
              | sed -n 's/.*"verdict":"\([^"]*\)".*/\1/p')
    [ -n "$verdict" ] && break
    sleep 1
  done

  [ "$verdict" = "AC" ] || die "submission $sub came back '${verdict:-no verdict}', wanted AC"
  echo "  ok   bob's submission $sub is AC, inside the freeze window"
}

case "${1:-check}" in
  check)
    preflight
    show_window
    ;;

  freeze-open)
    preflight
    echo
    echo "Opening the freeze window"
    freeze_open
    show_window
    cat <<'NOTE'

Now testable that was not before:
  - a frozen standings board on /contests/1 and /standings/1
  - staff seeing through the freeze: draenor08 and setter01 get the live board,
    alice and gita get the frozen one, same page
  - the first-solve highlight under a freeze — a hidden accept must not be announced

Expectations this deliberately changes:
  - C10 assumes you are NOT inside the last 15 minutes and expects no frozen cell.
    While this window is open, expect frozen cells instead. Run `reset` for C10.

Still ~90 minutes on the clock, so the time-bound steps (3, 15, C2, C3, E11, E19-E21,
G1-G6) all have room.
NOTE
    ;;

  hidden-accept)
    preflight
    echo
    echo "Opening the freeze window"
    freeze_open
    echo
    echo "Placing an accepted submission inside the freeze"
    hidden_accept "$(contest_id)"
    show_window
    cat <<'NOTE'

The decisive check this sets up:
  Open /standings/1 as `alice` and as `draenor08` side by side. Bob's accept is in the
  board staff can see and absent from the one contestants get. If it shows to alice, the
  freeze is leaking — which is the single most demo-critical behaviour in the project.

Also worth doing while it is open: /api/users/bob/stats must NOT count that submission
(the counts exclude any contest that has not ended). The e2e proves it, but it is the
same leak seen from a different angle and it is cheap to eyeball.
NOTE
    ;;

  reset)
    preflight
    echo
    echo "Restoring the seeded window"
    reset_window
    show_window
    ;;

  *)
    echo "usage: $0 {check|freeze-open|hidden-accept|reset}" >&2
    exit 2
    ;;
esac
