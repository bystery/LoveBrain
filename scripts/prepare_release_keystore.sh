#!/usr/bin/env bash
#
# scripts/prepare_release_keystore.sh
#
# Materialises the release keystore from CI secrets and PROVES it is the right
# key before a single Gradle task runs.
#
# The audited release workflow piped an unverified secret straight into
# keystore.properties. Here the keystore is loaded, its alias checked and its
# certificate compared against scripts/signing-baseline.txt, so a rotated or
# wrong secret fails immediately with an actionable message instead of
# producing an un-upgradable APK.
#
# Required environment (never defaulted, never skipped):
#   KEYSTORE_BASE64   base64 of the JKS/PKCS12 file
#   KEYSTORE_PASSWORD
#   KEY_ALIAS
#   KEY_PASSWORD
#
# Usage:
#   bash scripts/prepare_release_keystore.sh [--keystore-path keystore/release.jks]
#        [--properties-path keystore.properties] [--baseline scripts/signing-baseline.txt]
#
# Exit codes: 0 ready, 1 verification failed, 2 usage/tooling, 3 missing input.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

KEYSTORE_PATH="keystore/release.jks"
PROPERTIES_PATH="keystore.properties"
BASELINE="$SCRIPT_DIR/signing-baseline.txt"

while [ $# -gt 0 ]; do
  case "$1" in
    --keystore-path) KEYSTORE_PATH="$2"; shift 2 ;;
    --properties-path) PROPERTIES_PATH="$2"; shift 2 ;;
    --baseline) BASELINE="$2"; shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1" ;;
  esac
done

missing=0
for var in KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
  if [ -z "${!var:-}" ]; then
    printf '%s  FAIL required secret %s is empty or unset\n' "$GATE_LOG_PREFIX" "$var" >&2
    missing=$((missing + 1))
  fi
done
if [ "$missing" -ne 0 ]; then
  printf '%s       A release must never fall back to a debug key or an unsigned\n' "$GATE_LOG_PREFIX" >&2
  printf '%s       artifact presented as a release. Configure the four secrets.\n' "$GATE_LOG_PREFIX" >&2
  exit 3
fi

require_cmd keytool
[ -f "$BASELINE" ] || die "signing baseline is missing: $BASELINE — signing continuity cannot be enforced"

mkdir -p "$(dirname "$KEYSTORE_PATH")"
TMP_B64="$(mktemp)"
TMP_JKS="$(mktemp)"
cleanup() { rm -f "$TMP_B64" "$TMP_JKS"; }
trap cleanup EXIT

printf '%s' "$KEYSTORE_BASE64" >"$TMP_B64"
if ! base64 --decode "$TMP_B64" >"$TMP_JKS" 2>/dev/null; then
  die "KEYSTORE_BASE64 is not valid base64 — the keystore could not be decoded"
fi
[ -s "$TMP_JKS" ] || die "decoding KEYSTORE_BASE64 produced an empty file"

if ! keytool -list -keystore "$TMP_JKS" -storepass "$KEYSTORE_PASSWORD" >/dev/null 2>&1; then
  die "the decoded keystore cannot be opened with KEYSTORE_PASSWORD — wrong secret or corrupted keystore"
fi

log "keystore opened; checking alias $KEY_ALIAS"
if ! keytool -list -keystore "$TMP_JKS" -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS" >/dev/null 2>&1; then
  die "alias '$KEY_ALIAS' does not exist in the provided keystore"
fi

EXPECTED="$(baseline_value cert_sha256 "$BASELINE" | tr 'A-Z' 'a-f')"
[ -n "$EXPECTED" ] || die "signing baseline has no cert_sha256: $BASELINE"

CERT_DUMP="$(keytool -list -v -keystore "$TMP_JKS" -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS" 2>/dev/null)" ||
  die "keytool -list -v failed for alias $KEY_ALIAS"
FP_RAW="$(first_match 'SHA256:[[:space:]]*[0-9A-Fa-f:]+' "$CERT_DUMP")"
FP_SHA256="$(normalize_fingerprint "$(printf '%s' "$FP_RAW" | sed 's/^SHA256:[[:space:]]*//')" )"
[ -n "$FP_SHA256" ] || die "could not read the SHA-256 certificate fingerprint from the keystore. keytool output was: $(printf '%s' "$CERT_DUMP" | grep -a -i 'SHA256' | head -1)"
log "keystore certificate SHA-256: $FP_SHA256"
log "published baseline       SHA-256: $EXPECTED"
if [ "$FP_SHA256" != "$EXPECTED" ]; then
  printf '%s  FAIL the CI keystore is NOT the key that signed the published %s release\n' \
    "$GATE_LOG_PREFIX" "$(baseline_value baseline_version "$BASELINE")" >&2
  printf '%s       Refusing to build a release nobody can upgrade to.\n' "$GATE_LOG_PREFIX" >&2
  exit 1
fi
ok "keystore matches the published release certificate"

umask 077
{
  printf 'storeFile=../%s\n' "$KEYSTORE_PATH"
  printf 'storePassword=%s\n' "$KEYSTORE_PASSWORD"
  printf 'keyAlias=%s\n' "$KEY_ALIAS"
  printf 'keyPassword=%s\n' "$KEY_PASSWORD"
} >"$PROPERTIES_PATH"
chmod 600 "$PROPERTIES_PATH"
cp "$TMP_JKS" "$KEYSTORE_PATH"
chmod 600 "$KEYSTORE_PATH"

ok "release signing configured: $PROPERTIES_PATH + $KEYSTORE_PATH (0600)"
