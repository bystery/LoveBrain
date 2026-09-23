#!/usr/bin/env bash
#
# scripts/assert_artifacts.sh
#
# Empty-evidence gate (re-audit 2026-09-23 §8.1 item 6: "XML/HTML/截图为空视为
# 失败,不允许 warn 后放行").
#
# This script FAILS when instrumentation test evidence is missing or empty:
#   * no connected-androidTest XML file found            -> fail
#   * an XML file of 0 bytes                            -> fail
#   * XML present but the summed test count is 0        -> fail
#   * failures/errors > 0                               -> fail
#   * test count below --min-tests                      -> fail
#   * HTML report index missing / empty                 -> fail
#   * screenshot dir requested and empty / tiny         -> fail
#
# Usage:
#   bash scripts/assert_artifacts.sh --label "ui-test" \
#        [--xml-dir DIR ...] [--html-dir DIR ...] [--screenshots-dir DIR ...] \
#        [--min-tests N] [--allow-skipped]
#
# Exit codes: 0 evidence is real, 1 evidence missing/empty/failing, 2 usage.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

LABEL="artifacts"
MIN_TESTS=1
declare -a XML_DIRS=() HTML_DIRS=() SHOT_DIRS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --label) LABEL="$2"; shift 2 ;;
    --xml-dir) XML_DIRS+=("$2"); shift 2 ;;
    --html-dir) HTML_DIRS+=("$2"); shift 2 ;;
    --screenshots-dir) SHOT_DIRS+=("$2"); shift 2 ;;
    --min-tests) MIN_TESTS="$2"; shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1" ;;
  esac
done

[ "${#XML_DIRS[@]}" -gt 0 ] || die_usage "at least one --xml-dir is required"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# ── collect XML evidence ─────────────────────────────────────────────────────
FOUND="$WORK/found.txt"
: >"$FOUND"
for d in "${XML_DIRS[@]}"; do
  [ -d "$d" ] || continue
  find "$d" -type f -name '*.xml' >>"$FOUND"
done

XML_COUNT="$(wc -l <"$FOUND" | tr -d ' ')"
log "$LABEL: $XML_COUNT instrumentation XML file(s) under: ${XML_DIRS[*]}"

if [ "$XML_COUNT" -eq 0 ]; then
  die "$LABEL produced no instrumentation XML at all. Either the tests never ran or the result path moved — an empty report is a FAILURE, not a warning."
fi

while read -r f; do
  [ -n "$f" ] || continue
  [ -s "$f" ] || die "$LABEL: instrumentation XML is 0 bytes: $f — the run wrote an empty result file"
done <"$FOUND"

# ── aggregate counters ──────────────────────────────────────────────────────
# Gradle writes  <testsuite name="…" tests="N" skipped="S" failures="F" errors="E">.
# Every attribute is matched as a fixed string (values may contain regex
# metacharacters) and summed across all result files.
count_attr() {
  local attr="$1" total=0 file values v
  while read -r file; do
    [ -n "$file" ] || continue
    values="$(grep -oE "${attr}=\"[0-9]+\"" "$file")" || values=""
    for v in $values; do
      total=$((total + $(printf '%s' "$v" | sed 's/[^0-9]//g')))
    done
  done <"$FOUND"
  printf '%d\n' "$total"
}

TESTS="$(count_attr tests)"
FAILURES="$(count_attr failures)"
ERRORS="$(count_attr errors)"
SKIPPED="$(count_attr skipped)"
CLASSES="$(while read -r file; do [ -n "$file" ] && grep -oE 'testsuite name="[^"]+"' "$file"; done <"$FOUND" | sed 's/.*name="//; s/"//' | sort -u)" || CLASSES=""
CLASS_COUNT="$(printf '%s\n' "$CLASSES" | grep -c .)" || CLASS_COUNT=0

log "$LABEL: tests=$TESTS failures=$FAILURES errors=$ERRORS skipped=$SKIPPED suites=$CLASS_COUNT"

if [ "$TESTS" -eq 0 ]; then
  printf '%s  suites seen:%s\n' "$GATE_LOG_PREFIX" "$(printf '%s\n' "$CLASSES" | sed 's/^/    /')" >&2
  die "$LABEL: XML exists but declares 0 executed tests. Nothing was verified, so this job must not be green."
fi
# --min-tests auto: don't invent a magic number. Cross-check that every test
# the XML *declares* really exists as a <testcase> element, then require the
# caller's floor. A suite that bumps tests="N" without writing N cases fails here.
ACTUAL_CASES=0
while read -r file; do
  [ -n "$file" ] || continue
  ACTUAL_CASES=$((ACTUAL_CASES + $(grep -c '<testcase' "$file" || true)))
done <"$FOUND"
if [ "$MIN_TESTS" = "auto" ]; then
  if [ "$ACTUAL_CASES" -ne "$TESTS" ]; then
    die "$LABEL: XML declares $TESTS test(s) but only $ACTUAL_CASES <testcase> element(s) exist — the report does not match what ran."
  fi
  MIN_TESTS=1
elif ! printf '%s' "$MIN_TESTS" | grep -qE '^[0-9]+$'; then
  die "$LABEL: --min-tests must be a non-negative integer or 'auto' (got: $MIN_TESTS)" 2
fi
if [ "$TESTS" -lt "$MIN_TESTS" ]; then
  die "$LABEL: only $TESTS test(s) executed but at least $MIN_TESTS are required — did a filter silently drop the suite?"
fi
if [ "$((FAILURES + ERRORS))" -gt 0 ]; then
  die "$LABEL: $FAILURES failure(s) and $ERRORS error(s) reported in the instrumentation XML"
fi

# ── HTML report ──────────────────────────────────────────────────────────────
if [ "${#HTML_DIRS[@]}" -gt 0 ]; then
  HTML_OK=0
  for d in "${HTML_DIRS[@]}"; do
    [ -d "$d" ] || continue
    while read -r h; do
      [ -n "$h" ] || continue
      if [ -s "$h" ] && [ "$(wc -c <"$h" | tr -d ' ')" -ge 512 ]; then
        HTML_OK=1
        log "$LABEL: HTML report: $h ($(wc -c <"$h" | tr -d ' ') bytes)"
      else
        die "$LABEL: HTML report is empty or a stub: $h"
      fi
    done < <(find "$d" -type f -name 'index.html')
  done
  [ "$HTML_OK" -eq 1 ] || die "$LABEL: no non-empty index.html under: ${HTML_DIRS[*]} — an empty HTML report is a FAILURE"
fi

# ── screenshots ──────────────────────────────────────────────────────────────
for d in "${SHOT_DIRS[@]}"; do
  [ -d "$d" ] || die "$LABEL: screenshot directory does not exist: $d"
  N="$(find "$d" -type f -name '*.png' | grep -c . )" || N=0
  [ "$N" -gt 0 ] || die "$LABEL: screenshot directory is empty: $d — visual evidence is required"
  while read -r png; do
    [ -n "$png" ] || continue
    size="$(wc -c <"$png" | tr -d ' ')"
    [ "$size" -ge 4096 ] || die "$LABEL: screenshot is a stub ($size bytes): $png"
  done < <(find "$d" -type f -name '*.png')
  log "$LABEL: $N screenshot(s) captured in $d"
done

ok "$LABEL evidence gate passed: $TESTS tests, $CLASS_COUNT suite(s), 0 failures, 0 errors"
