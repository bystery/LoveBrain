#!/usr/bin/env bash
#
# scripts/check_logcat_fatal.sh
#
# Crash gate. The audited workflow ran
#     adb logcat -d | grep -i "FATAL EXCEPTION" || true
# which returns success EVEN WHEN IT FOUND A CRASH (re-audit §3: "adb logcat |
# grep 'FATAL EXCEPTION' || true 即使发现崩溃也返回成功"). Here the polarity is
# correct: a match fails the job.
#
# Usage:
#   bash scripts/check_logcat_fatal.sh <logfile> [--package com.lovebrain.app]
#        [--label "upgrade-test"]
#
# Exit codes: 0 no fatal evidence, 1 fatal evidence found, 2 usage/tooling error.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

LOG="${1:-}"
[ -n "$LOG" ] || die_usage "a logcat file is required"
shift

PACKAGE="com.lovebrain.app"
LABEL="logcat"
while [ $# -gt 0 ]; do
  case "$1" in
    --package) PACKAGE="$2"; shift 2 ;;
    --label) LABEL="$2"; shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1" ;;
  esac
done

require_file "$LOG" "logcat dump"
LINE_COUNT="$(wc -l <"$LOG" | tr -d ' ')"
log "$LABEL: scanning $LINE_COUNT logcat line(s) in $LOG for fatal evidence"

PATTERN="FATAL EXCEPTION|FATAL: |Fatal signal |E AndroidRuntime|begin of crash|ANR in ${PACKAGE}|has died: ${PACKAGE}|SIGSEGV|SIGABRT|Native crash|E ActivityManager: Process ${PACKAGE}"

HITS=""
rc=0
if HITS="$(grep -nE "$PATTERN" "$LOG")"; then
  rc=0
else
  rc=$?
fi

case "$rc" in
  0)
    printf '%s  FAIL %s found fatal evidence in the logcat dump. First 40 matching line(s):\n' \
      "$GATE_LOG_PREFIX" "$LABEL" >&2
    printf '%s\n' "$HITS" | head -40 >&2
    die "$LABEL: crash detected — refusing to pass (audit §3: FATAL must fail the job)"
    ;;
  1)
    ok "$LABEL: no FATAL EXCEPTION / ANR / native crash evidence in the logcat dump"
    ;;
  *)
    die "$LABEL: grep failed with exit code $rc while reading $LOG — the crash scan itself errored and cannot be treated as a pass"
    ;;
esac

# A zero-line logcat is not "clean", it is "we looked at nothing".
[ "$LINE_COUNT" -gt 0 ] || die "$LABEL: logcat dump is empty — nothing was observed, which is not evidence of no crash"
