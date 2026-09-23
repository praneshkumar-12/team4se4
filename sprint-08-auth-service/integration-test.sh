#!/usr/bin/env bash
# SEC4-629 integration test: obtains a real token from the running auth
# service and calls a protected Trade REST API route with it, then confirms
# refusal with no token and with a token signed by a key the platform
# should not trust.
#
# Requires the stack already running: from the repo root,
#   docker compose up -d --build postgres kafka trade-api auth-service
#
# Usage: ./integration-test.sh [auth-base-url] [trade-api-base-url]
set -euo pipefail

# Resolve relative to this script, not the caller's cwd: the `node -e` call
# below needs to `require("jsonwebtoken")`, which only resolves under this
# directory's node_modules (found the hard way - it works when you `cd`
# here first and silently breaks otherwise, since `node -e` resolves
# modules relative to the process's cwd).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

AUTH_BASE="${1:-http://localhost:3000}"
TRADE_BASE="${2:-http://localhost:8085}"
ACCOUNT_ID=1
USERNAME="integration-test-$(date +%s)"
PASSWORD="correct horse battery staple"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

expect_status() {
  local description="$1" expected="$2" actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    fail "$description: expected HTTP $expected, got $actual"
  fi
  echo "OK: $description ($actual)"
}

echo "== Registering a user against account $ACCOUNT_ID on $AUTH_BASE =="
register_status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$AUTH_BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"accountId\":$ACCOUNT_ID}")
expect_status "register" 201 "$register_status"

echo "== Logging in to get a real token pair =="
login_response=$(curl -s -X POST "$AUTH_BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
access_token=$(node -e "console.log(JSON.parse(process.argv[1]).accessToken)" "$login_response")
[[ -n "$access_token" && "$access_token" != "undefined" ]] || fail "no accessToken in login response: $login_response"

echo "== Calling a protected Trade REST API route WITH the auth-service token =="
status_with_token=$(curl -s -o /dev/null -w '%{http_code}' "$TRADE_BASE/api/v1/accounts/$ACCOUNT_ID" \
  -H "Authorization: Bearer $access_token")
expect_status "protected route with a valid token from this auth service" 200 "$status_with_token"

echo "== Calling the same route with NO token =="
status_no_token=$(curl -s -o /dev/null -w '%{http_code}' "$TRADE_BASE/api/v1/accounts/$ACCOUNT_ID")
expect_status "protected route with no token" 401 "$status_no_token"

echo "== Calling the same route with a token signed by a key the platform should not trust =="
untrusted_token=$(cd "$SCRIPT_DIR" && node -e "
const jwt = require('jsonwebtoken');
console.log(jwt.sign(
  { accountId: $ACCOUNT_ID, roles: ['CUSTOMER'] },
  'a-key-the-platform-should-not-trust-32-characters',
  { subject: 'untrusted-subject', issuer: 'auth-service', algorithm: 'HS256', expiresIn: 900 },
));
")
status_untrusted=$(curl -s -o /dev/null -w '%{http_code}' "$TRADE_BASE/api/v1/accounts/$ACCOUNT_ID" \
  -H "Authorization: Bearer $untrusted_token")
expect_status "protected route with a token signed by an untrusted key" 401 "$status_untrusted"

echo
echo "All SEC4-629 integration checks passed."
