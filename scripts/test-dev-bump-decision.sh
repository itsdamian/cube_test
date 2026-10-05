#!/usr/bin/env bash
# Tests for scripts/dev-bump-decision.sh in a throw-away git repository (also in CI).
#
#   scripts/test-dev-bump-decision.sh
#
#   main:   A - B - C
#   other:       \- D        (never merged)
set -euo pipefail

DECIDE="$(cd "$(dirname "$0")" && pwd)/dev-bump-decision.sh"
REPO="$(mktemp -d)"
trap 'rm -rf "$REPO"' EXIT
cd "$REPO"

git init -q -b main
git config user.name test
git config user.email test@example.invalid
commit() { git commit -q --allow-empty -m "$1"; git rev-parse HEAD; }
A=$(commit A); B=$(commit B); C=$(commit C)
git switch -q -c other "$B"; D=$(commit D); git switch -q main

failed=0
check() {   # check <name> <expected prefix> <new-sha> <current-tag>
  local got
  got=$("$DECIDE" "$3" "$4" main)
  if [[ "$got" == "$2"* ]]; then
    echo "ok    $1: $got"
  else
    echo "FAIL  $1: expected '$2...', got '$got'"; failed=1
  fi
}

check "newer than current"           "bump"                  "$C" "sha-${A:0:7}"
check "older than current (re-run)"  "skip: "                "$A" "sha-${C:0:7}"
check "same commit"                  "skip: dev already runs" "$B" "sha-${B:0:7}"
check "current not in history"       "bump-unknown-current"  "$C" "sha-0000000"
check "current not a sha- tag"       "bump-unknown-current"  "$C" "v0.1.0"
check "current only on other branch" "bump-unknown-current"  "$C" "sha-${D:0:7}"
check "new not on main"              "skip: "                "$D" "sha-${A:0:7}"
check "new does not exist"           "skip: "                "1234567890abcdef1234567890abcdef12345678" "sha-${A:0:7}"

if "$DECIDE" "$C" 2>/dev/null; then echo "FAIL  usage: missing argument accepted"; failed=1; else echo "ok    usage error exits non-zero"; fi
exit "$failed"
