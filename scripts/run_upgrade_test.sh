#!/usr/bin/env bash
#
# scripts/run_upgrade_test.sh
#
# The whole v1.3.1 -> candidate覆盖升级 gate, driven from one repo-local script so
# the workflow YAML contains no inline shell (re-audit §8 step 1.2 / §3).
#
# What it does, in order, failing hard at any step:
#   1. verifies BOTH APKs' metadata and signing certificates (candidate must be
#      the final signed/R8 release build signed with the published key — the
#      audited job used assembleDebug)
#   2. installs the published v1.3.1 APK for real (no `|| true`)
#   3. starts it and waits for it to be in the foreground
#   4. writes real fixture data through scripts/write_upgrade_fixture.sh
#   5. clears logcat, then upgrade-installs the candidate with -r (data kept)
#   6. asserts old data readable / schema migrated / screens work and dumps +
#      scans logcat, failing on FATAL EXCEPTION (the audited pipeline's grep
#      returned success even when a crash was present)
#
# Usage:
#   bash scripts/run_upgrade_test.sh --old-apk fixtures/app-release.apk \
#        --candidate-apk app/build/outputs/apk/release/app-release.apk \
#        [--package com.lovebrain.app] [--out-dir fixtures]
#
# Exit code: 0 verified upgrade, 1 any failure, 2 usage/tooling.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"
# shellcheck source=lib/device_lib.sh
. "$SCRIPT_DIR/lib/device_lib.sh"

OLD_APK=""
CANDIDATE_APK=""
OUT_DIR="fixtures"

while [ $# -gt 0 ]; do
  case "$1" in
    --package) PKG="$2"; shift 2 ;;
    --old-apk) OLD_APK="$2"; shift 2 ;;
    --candidate-apk) CANDIDATE_APK="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1" ;;
  esac
done

DATA_ROOT="/data/data/$PKG"
[ -n "$OLD_APK" ] || die_usage "--old-apk is required"
[ -n "$CANDIDATE_APK" ] || die_usage "--candidate-apk is required"
require_file "$OLD_APK" "old (v1.3.1) APK"
require_file "$CANDIDATE_APK" "candidate APK"
mkdir -p "$OUT_DIR"
BASELINE="$SCRIPT_DIR/signing-baseline.txt"

require_cmd adb
export PATH="$(sdk_root)/platform-tools:$PATH"
require_device

# ── 1. both artifacts are what they claim to be ─────────────────────────────
bash "$SCRIPT_DIR/check_apk_metadata.sh" "$OLD_APK" \
  --expected-package "$PKG" --properties "$OUT_DIR/old-apk-metadata.txt" \
  --report "$OUT_DIR/old-apk-metadata.md"
bash "$SCRIPT_DIR/check_apk_metadata.sh" "$CANDIDATE_APK" \
  --expected-package "$PKG" --properties "$OUT_DIR/candidate-metadata.txt" \
  --report "$OUT_DIR/candidate-metadata.md"

OLD_NAME="$(sed -n 's/^version_name=//p' "$OUT_DIR/old-apk-metadata.txt")"
OLD_CODE="$(sed -n 's/^version_code=//p' "$OUT_DIR/old-apk-metadata.txt")"
CAND_NAME="$(sed -n 's/^version_name=//p' "$OUT_DIR/candidate-metadata.txt")"
CAND_CODE="$(sed -n 's/^version_code=//p' "$OUT_DIR/candidate-metadata.txt")"
CAND_SHA="$(sed -n 's/^sha256=//p' "$OUT_DIR/candidate-metadata.txt")"

[ -n "$OLD_CODE" ] && [ -n "$CAND_CODE" ] || die "could not read versionCode from the generated metadata files"
if [ "$CAND_CODE" -le "$OLD_CODE" ]; then
  die "candidate versionCode ($CAND_CODE) is not greater than the installed $OLD_NAME versionCode ($OLD_CODE) — Android would reject this as an upgrade and the test would prove nothing"
fi
if [ "$OLD_NAME" = "$CAND_NAME" ]; then
  die "candidate versionName equals the published old version ($OLD_NAME) — this is not an upgrade pair"
fi
log "upgrade pair: $OLD_NAME($OLD_CODE) -> $CAND_NAME($CAND_CODE)"

# The candidate must be the signed release build, and both APKs must carry the
# same published certificate, otherwise a覆盖安装 is impossible.
bash "$SCRIPT_DIR/verify_signing_continuity.sh" "$CANDIDATE_APK" --baseline "$BASELINE" \
  --properties "$OUT_DIR/candidate-signing.txt" --report "$OUT_DIR/candidate-signing.md"
bash "$SCRIPT_DIR/verify_signing_continuity.sh" "$OLD_APK" --baseline "$BASELINE"

# ── 2. install the old version for real ─────────────────────────────────────
adb uninstall "$PKG" >/dev/null 2>&1 || log "no previous $PKG installation to remove (expected on a fresh emulator)"
device_install "$OLD_APK"
[ "$(adb shell "pm list packages $PKG" | tr -d '\r' | grep -c "^package:$PKG\$")" = "1" ] ||
  die "$PKG is not installed after installing $OLD_APK"

# ── 3. first launch of the old version ──────────────────────────────────────
adb logcat -c || die "could not clear logcat before the first launch"
device_start_activity "$PKG/.ui.SetupActivity" >/dev/null
sleep 8
if ! adb shell "pidof $PKG" | tr -d '\r' | grep -q '[0-9]'; then
  die "$PKG died during its first launch — the old version cannot even be started on this image"
fi
adb shell "appops set --uid $PKG SYSTEM_ALERT_WINDOW allow" >/dev/null 2>&1 ||
  log "SYSTEM_ALERT_WINDOW appop could not be set; the overlay permission prompt may block UI (recorded, not asserted)"
adb shell "pm grant $PKG android.permission.POST_NOTIFICATIONS" >/dev/null 2>&1 ||
  log "POST_NOTIFICATIONS grant skipped (not grantable on this API level)"
RESUMED="$(device_resumed_component)"
log "old version foreground component: $RESUMED"
case "$RESUMED" in
  *"$PKG"*) ok "old version is in the foreground" ;;
  *)
    printf '%s  expected our package in the foreground, got: %s\n' "$GATE_LOG_PREFIX" "$RESUMED" >&2
    die "the old version did not stay in the foreground on first launch"
    ;;
esac

# ── 4. write the upgrade fixture ────────────────────────────────────────────
bash "$SCRIPT_DIR/write_upgrade_fixture.sh" --package "$PKG" --out-dir "$OUT_DIR"

# ── 5.覆盖安装 the candidate ──────────────────────────────────────────────────
adb logcat -c || die "could not clear logcat before the upgrade install"
device_upgrade_install "$CANDIDATE_APK"

# ── 6. assertions + crash scan ──────────────────────────────────────────────
LOGCAT="$OUT_DIR/logcat-upgrade.txt"
sleep 5
if ! adb logcat -d -v time >"$LOGCAT"; then
  die "adb logcat -d failed — the crash scan cannot run without the dump"
fi
[ -s "$LOGCAT" ] || die "the logcat dump is empty: $LOGCAT — nothing was observed"
log "logcat captured: $LOGCAT ($(wc -l <"$LOGCAT" | tr -d ' ') lines)"

bash "$SCRIPT_DIR/assert_upgrade_state.sh" \
  --package "$PKG" \
  --manifest "$OUT_DIR/upgrade-fixture-manifest.txt" \
  --expected-version-name "$CAND_NAME" \
  --expected-version-code "$CAND_CODE" \
  --logcat "$LOGCAT" \
  --out-dir "$OUT_DIR" \
  --activity "$PKG/.ui.SetupActivity" \
  --activity "$PKG/.ui.KnowledgeBaseActivity"

{
  printf '# Upgrade test evidence\n\n'
  printf '| item | value |\n|---|---|\n'
  printf '| old (installed first) | `%s` versionCode %s |\n' "$OLD_NAME" "$OLD_CODE"
  printf '| candidate (覆盖安装) | `%s` versionCode %s |\n' "$CAND_NAME" "$CAND_CODE"
  printf '| candidate SHA-256 | `%s` |\n' "$CAND_SHA"
  printf '| package | `%s` |\n' "$PKG"
  printf '\nThe candidate is the final signed/R8 release APK, upgrade-installed over the\n'
  printf 'published old version with `-r` so /data survived. The assertions in\n'
  printf 'scripts/assert_upgrade_state.sh prove the old data is still readable, the\n'
  printf 'schema migrated, the screens come up, and logcat carries no FATAL EXCEPTION.\n'
} >"$OUT_DIR/upgrade-evidence.md"

ok "upgrade test complete: $OLD_NAME -> $CAND_NAME verified (evidence in $OUT_DIR)"
