#!/usr/bin/env bash
#
# scripts/assert_upgrade_state.sh
#
# Post-覆盖安装 assertions (re-audit 2026-09-23 §3: "没有断言旧数据仍可读、
# schema 已升级、主要页面可用").
#
# Every check below is an assertion that FAILS the job:
#   1. the installed package now reports the candidate's versionCode/versionName
#      (proof the upgrade install replaced the old app instead of no-oping)
#   2. every fixture file written by scripts/write_upgrade_fixture.sh still
#      exists, is non-empty and still contains the fixture sentinel — the old
#      user data survived and is still readable
#   3. the knowledge base reports a schema version >= the candidate's CURRENT
#      schema, i.e. the v1.3.1 data really was migrated
#   4. kb.json is still present, non-empty and parses as JSON
#   5. the main screens start cleanly and end up resumed/foreground
#   6. the app process is alive and the logcat dump carries no fatal evidence
#
# Usage:
#   bash scripts/assert_upgrade_state.sh --manifest fixtures/upgrade-fixture-manifest.txt \
#        --expected-version-name 1.4.0-rc1 --expected-version-code 9 \
#        [--package com.lovebrain.app] [--min-schema-version 3] \
#        [--logcat fixtures/logcat-upgrade.txt] [--out-dir fixtures] \
#        [--activity com.lovebrain.app/.ui.SetupActivity]...
#
# Exit codes: 0 all assertions hold, 1 an assertion failed, 2 usage/tooling.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"
# shellcheck source=lib/device_lib.sh
. "$SCRIPT_DIR/lib/device_lib.sh"

MANIFEST=""
EXPECTED_VERSION_NAME=""
EXPECTED_VERSION_CODE=""
MIN_SCHEMA_VERSION=3
LOGCAT=""
OUT_DIR="fixtures"
declare -a ACTIVITIES=()

while [ $# -gt 0 ]; do
  case "$1" in
    --package) PKG="$2"; shift 2 ;;
    --manifest) MANIFEST="$2"; shift 2 ;;
    --expected-version-name) EXPECTED_VERSION_NAME="$2"; shift 2 ;;
    --expected-version-code) EXPECTED_VERSION_CODE="$2"; shift 2 ;;
    --min-schema-version) MIN_SCHEMA_VERSION="$2"; shift 2 ;;
    --logcat) LOGCAT="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --activity) ACTIVITIES+=("$2"); shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1" ;;
  esac
done

[ -n "$MANIFEST" ] || die_usage "--manifest is required (written by write_upgrade_fixture.sh)"
require_file "$MANIFEST" "fixture manifest"
[ -n "$EXPECTED_VERSION_NAME" ] || die_usage "--expected-version-name is required"
[ -n "$EXPECTED_VERSION_CODE" ] || die_usage "--expected-version-code is required"
[ "${#ACTIVITIES[@]}" -gt 0 ] || ACTIVITIES=("$PKG/.ui.SetupActivity" "$PKG/.ui.KnowledgeBaseActivity")

DATA_ROOT="/data/data/$PKG"
mkdir -p "$OUT_DIR"
PULLED="$OUT_DIR/upgraded-data"
mkdir -p "$PULLED"

init_device_mode

CHECKS=0
FAILURES=0

check() {
  # check <description> <0-or-nonzero-status> — one assertion, always counted.
  local desc="$1" rc="$2"
  CHECKS=$((CHECKS + 1))
  if [ "$rc" -eq 0 ]; then
    ok "$desc"
  else
    FAILURES=$((FAILURES + 1))
    printf '%s  FAIL %s\n' "$GATE_LOG_PREFIX" "$desc" >&2
  fi
}

# ── 1. the candidate is actually installed ──────────────────────────────────
INSTALLED_NAME="$(device_current_version_name)"
INSTALLED_CODE="$(device_current_version_code)"
log "installed package reports versionName=$INSTALLED_NAME versionCode=$INSTALLED_CODE"
[ "$INSTALLED_NAME" = "$EXPECTED_VERSION_NAME" ] ||
  die "the installed versionName is '$INSTALLED_NAME', not the candidate '$EXPECTED_VERSION_NAME' — the upgrade install did not take effect"
[ "$INSTALLED_CODE" = "$EXPECTED_VERSION_CODE" ] ||
  die "the installed versionCode is '$INSTALLED_CODE', not the candidate '$EXPECTED_VERSION_CODE' — the upgrade install did not take effect"

# ── 2. old data survived and is still readable ──────────────────────────────
FIXTURE_ROWS=0
KB_DIR=""
while IFS='|' read -r rel sentinel desc; do
  [ -n "$rel" ] || continue
  case "$rel" in
    files/*) ;;
    *)
      if [ "$rel" = "kb_name" ]; then
        KB_DIR="files/knowledge/$sentinel"
      fi
      continue
      ;;
  esac
  FIXTURE_ROWS=$((FIXTURE_ROWS + 1))
  assert_safe_path "$rel"
  rc=0
  dev_run "test -s $rel" || rc=1
  check "fixture file still exists and is non-empty: $rel ($desc)" "$rc"
  rc=0
  if [ "$DEVICE_MODE" = "run-as" ]; then
    dev_capture "grep -q $sentinel $rel && echo YES" | grep -q '^YES$' || rc=1
  else
    adb shell "grep -q $sentinel $DATA_ROOT/$rel && echo YES" | tr -d '\r' | grep -q '^YES$' || rc=1
  fi
  check "old user data is still readable after the upgrade: $rel carries '$sentinel'" "$rc"
  # keep a copy of the evidence off the device
  local_dest="$PULLED/$(basename "$rel")"
  rc=0
  device_pull_file "$rel" "$local_dest" || rc=1
  check "upgrade evidence extracted: $local_dest" "$rc"
done <"$MANIFEST"

if [ "$FIXTURE_ROWS" -gt 0 ]; then
  check "the fixture manifest listed at least one data file" 0
else
  check "the fixture manifest listed at least one data file" 1
fi
[ "$FIXTURE_ROWS" -gt 0 ] ||
  die "no fixture files were listed in $MANIFEST — nothing was verified, so the upgrade gate cannot pass"

[ -n "$KB_DIR" ] || die "the manifest does not record kb_name; the migrated schema version cannot be located"

# ── 3. schema migrated ──────────────────────────────────────────────────────
PRE_SCHEMA="$(grep -m1 '^pre_schema_version|' "$MANIFEST" | cut -d'|' -f2)"
SCHEMA_RAW="$(dev_capture "cat $KB_DIR/.schema_version")"
SCHEMA="$(printf '%s' "$SCHEMA_RAW" | tr -d ' \r\n')"
log "pre-upgrade .schema_version='$PRE_SCHEMA'  post-upgrade .schema_version='$SCHEMA'"
if [ -z "$SCHEMA" ]; then
  check "the knowledge base records a .schema_version after the upgrade" 1
  dev_capture "ls -l $KB_DIR" >&2
else
  case "$SCHEMA" in
    '' | *[!0-9]*) check ".schema_version is a number (got '$SCHEMA')" 1 ;;
    *)
      if [ "$SCHEMA" -ge "$MIN_SCHEMA_VERSION" ]; then
        check "schema upgraded: .schema_version=$SCHEMA >= $MIN_SCHEMA_VERSION (was '$PRE_SCHEMA' before)" 0
      else
        check "schema upgraded: .schema_version=$SCHEMA but >= $MIN_SCHEMA_VERSION is required (was '$PRE_SCHEMA')" 1
      fi
      ;;
  esac
fi

# ── 4. kb.json still loads ──────────────────────────────────────────────────
rc=0
dev_run "test -s $KB_DIR/kb.json" || rc=1
check "kb.json is still present and non-empty" "$rc"
rc=1
if device_pull_file "$KB_DIR/kb.json" "$PULLED/kb.json"; then
  if validate_json "$PULLED/kb.json" 2>/dev/null; then
    rc=0
  fi
fi
check "kb.json still parses as JSON after the migration" "$rc"

# ── 5. main screens ─────────────────────────────────────────────────────────
for act in "${ACTIVITIES[@]}"; do
  rc=0
  OUT="$(device_start_activity "$act")" || rc=1
  if [ "$rc" -eq 0 ]; then
    sleep 3
    RESUMED="$(device_resumed_component)"
    log "resumed component after starting $act: $RESUMED"
    case "$RESUMED" in
      *"$PKG"*) : ;;
      *)
        rc=1
        printf '%s  our package is not in the foreground: %s\n' "$GATE_LOG_PREFIX" "$RESUMED" >&2
        ;;
    esac
  fi
  check "screen works after the upgrade: $act" "$rc"
done

# ── 6. process alive + no fatal evidence ────────────────────────────────────
PID="$(adb shell "pidof $PKG" | tr -d '\r')" || PID=""
PID="$(first_nonempty_line "$PID")"
if [ -n "$PID" ]; then
  check "the app process is alive after the upgrade (pid '$PID')" 0
else
  check "the app process is alive after the upgrade (pid 'none')" 1
fi

if [ -n "$LOGCAT" ]; then
  require_file "$LOGCAT" "logcat dump"
  if ! bash "$SCRIPT_DIR/check_logcat_fatal.sh" "$LOGCAT" --package "$PKG" --label "upgrade-test"; then
    FAILURES=$((FAILURES + 1))
    CHECKS=$((CHECKS + 1))
  else
    CHECKS=$((CHECKS + 1))
  fi
else
  die "--logcat is required: the upgrade gate must prove the logcat dump carries no FATAL EXCEPTION"
fi

# ── verdict ─────────────────────────────────────────────────────────────────
log "assertions run: $CHECKS, failures: $FAILURES"
if [ "$FAILURES" -ne 0 ]; then
  die "upgrade assertions FAILED ($FAILURES of $CHECKS) — see the evidence in $OUT_DIR"
fi
ok "upgrade state verified: version replaced, old data readable, schema migrated, screens working, no crash"
