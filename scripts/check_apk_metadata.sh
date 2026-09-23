#!/usr/bin/env bash
#
# scripts/check_apk_metadata.sh
#
# Extracts and VERIFIES release APK metadata: SHA-256, package, versionCode,
# versionName, minSdk/targetSdk and launchable activity.
#
# Re-audit 2026-09-23 (§3, §8 step 3.8): "APK SHA-256、versionCode/versionName"
# must be real evidence, not a comment. Any expectation mismatch fails the job.
#
# Usage:
#   bash scripts/check_apk_metadata.sh <apk> \
#        [--expected-package com.lovebrain.app] \
#        [--expected-version-name 1.4.0-rc1] \
#        [--expected-version-code 9] \
#        [--min-version-code 1] \
#        [--expected-sha256 <hex>] \
#        [--properties <key=value out file>] \
#        [--report <markdown out file>]
#
# Exit codes: 0 verified, 1 verification failed, 2 usage / missing tooling.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

APK=""
EXPECTED_PACKAGE="com.lovebrain.app"
EXPECTED_VERSION_NAME=""
EXPECTED_VERSION_CODE=""
MIN_VERSION_CODE=""
EXPECTED_SHA256=""
PROPERTIES=""
REPORT=""

while [ $# -gt 0 ]; do
  case "$1" in
    --expected-package) EXPECTED_PACKAGE="$2"; shift 2 ;;
    --expected-version-name) EXPECTED_VERSION_NAME="$2"; shift 2 ;;
    --expected-version-code) EXPECTED_VERSION_CODE="$2"; shift 2 ;;
    --min-version-code) MIN_VERSION_CODE="$2"; shift 2 ;;
    --expected-sha256) EXPECTED_SHA256="$2"; shift 2 ;;
    --properties) PROPERTIES="$2"; shift 2 ;;
    --report) REPORT="$2"; shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    -*) die_usage "unknown option: $1" ;;
    *)
      [ -z "$APK" ] || die_usage "more than one APK argument given"
      APK="$1"
      shift
      ;;
  esac
done

[ -n "$APK" ] || die_usage "an APK path is required"
require_file "$APK" "APK"

AAPT2="$(find_build_tool aapt2)"
log "using aapt2: $AAPT2"

BADGING="$("$AAPT2" dump badging "$(to_native_path "$APK")" 2>/dev/null)" ||
  die "aapt2 failed to read $APK"
[ -n "$BADGING" ] || die "aapt2 produced no badging output for $APK"

parse_pkg_field() {
  # The `package:` line lists `name='…' versionCode='…' versionName='…'` first;
  # require a field boundary before the key so that trailing keys such as
  # `compileSdkVersionCodename` cannot be matched by the `name` lookup.
  local key="$1" line frag
  line="$(first_match '^package:.*$' "$BADGING")"
  [ -n "$line" ] || return 0
  frag="$(first_match "(^|[[:space:]])${key}='[^']*'" "$line")"
  printf '%s\n' "$frag" | sed "s/^[[:space:]]*//; s/^${key}='//; s/'$//"
}

digits_only() {
  local line
  line="$(first_match "^$1.*\$" "$BADGING")"
  [ -n "$line" ] || return 0
  printf '%s\n' "$line" | sed 's/[^0-9]//g'
}

PACKAGE="$(parse_pkg_field name)"
VERSION_CODE="$(parse_pkg_field versionCode)"
VERSION_NAME="$(parse_pkg_field versionName)"
MIN_SDK="$(digits_only 'minSdkVersion:')"
TARGET_SDK="$(digits_only 'targetSdkVersion:')"
LAUNCHABLE_LINE="$(first_match '^launchable-activity:.*$' "$BADGING")"
LAUNCHABLE="$(printf '%s\n' "$LAUNCHABLE_LINE" | sed -n "s/^[^']*'\([^']*\)'.*/\1/p")"
SHA256="$(sha256_of "$APK")"
BYTES="$(wc -c <"$APK" | tr -d ' ')"

for pair in "package:$PACKAGE" "versionCode:$VERSION_CODE" "versionName:$VERSION_NAME"; do
  [ -n "${pair#*:}" ] || die "aapt2 did not report ${pair%%:*} for $APK — refusing to publish unknown metadata"
done

[ "$PACKAGE" = "$EXPECTED_PACKAGE" ] \
  || die "applicationId mismatch: APK says '$PACKAGE', expected '$EXPECTED_PACKAGE'"

if [ -n "$EXPECTED_VERSION_NAME" ] && [ "$VERSION_NAME" != "$EXPECTED_VERSION_NAME" ]; then
  die "versionName mismatch: APK says '$VERSION_NAME', expected '$EXPECTED_VERSION_NAME'"
fi
if [ -n "$EXPECTED_VERSION_CODE" ] && [ "$VERSION_CODE" != "$EXPECTED_VERSION_CODE" ]; then
  die "versionCode mismatch: APK says '$VERSION_CODE', expected '$EXPECTED_VERSION_CODE'"
fi
if [ -n "$MIN_VERSION_CODE" ]; then
  [ "$VERSION_CODE" -ge "$MIN_VERSION_CODE" ] \
    || die "versionCode $VERSION_CODE is lower than the required minimum $MIN_VERSION_CODE — an upgrade install would be rejected by Android"
fi
if [ -n "$EXPECTED_SHA256" ]; then
  normalized_expected="$(normalize_fingerprint "$EXPECTED_SHA256")"
  [ "$SHA256" = "$normalized_expected" ] \
    || die "APK SHA-256 mismatch: got $SHA256, expected $normalized_expected (artifact was swapped, truncated or rebuilt differently)"
fi

log "APK        : $APK"
log "package    : $PACKAGE"
log "versionCode: $VERSION_CODE"
log "versionName: $VERSION_NAME"
log "minSdk     : $MIN_SDK"
log "targetSdk  : $TARGET_SDK"
log "launcher   : ${LAUNCHABLE:-<none reported>}"
log "size       : $BYTES bytes"
log "sha256     : $SHA256"

if [ -n "$PROPERTIES" ]; then
  mkdir -p "$(dirname "$PROPERTIES")"
  {
    printf 'apk=%s\n' "$APK"
    printf 'package=%s\n' "$PACKAGE"
    printf 'version_code=%s\n' "$VERSION_CODE"
    printf 'version_name=%s\n' "$VERSION_NAME"
    printf 'min_sdk=%s\n' "$MIN_SDK"
    printf 'target_sdk=%s\n' "$TARGET_SDK"
    printf 'launchable_activity=%s\n' "$LAUNCHABLE"
    printf 'size_bytes=%s\n' "$BYTES"
    printf 'sha256=%s\n' "$SHA256"
  } >"$PROPERTIES"
  ok "properties written: $PROPERTIES"
fi

if [ -n "$REPORT" ]; then
  mkdir -p "$(dirname "$REPORT")"
  {
    printf '| APK file | `%s` |\n' "$(basename "$APK")"
    printf '| package | `%s` |\n' "$PACKAGE"
    printf '| versionCode | `%s` |\n' "$VERSION_CODE"
    printf '| versionName | `%s` |\n' "$VERSION_NAME"
    printf '| minSdk / targetSdk | `%s` / `%s` |\n' "$MIN_SDK" "$TARGET_SDK"
    printf '| launchable activity | `%s` |\n' "${LAUNCHABLE:-?}"
    printf '| size | `%s` bytes |\n' "$BYTES"
    printf '| SHA-256 | `%s` |\n' "$SHA256"
  } >"$REPORT"
  ok "report written: $REPORT"
fi

ok "APK metadata verified"
