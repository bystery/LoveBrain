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
#   bash scripts/assert_artifacts.sh --label "lint" \
#        --lint-xml app/build/reports/lint-results-debug.xml \
#        [--lint-html app/build/reports/lint-results-debug.html]
#
#   --shot-provenance DIR + --foreground-pkg PKG 开启**视觉证据的来源证明**：
#   每张 <name>.png 都必须在 DIR 里有 `foreground-<name>.txt`（拍那一刻的前台活动）
#   与 `am-start-<name>.txt`（启动它的命令原文），并且
#     * 前台活动属于 PKG（不是 launcher、不是别的应用、不是黑屏）；
#     * am-start 的输出里没有 `Error` / `Exception` / `does not exist` / `Permission Denial`，
#       有 `Status:` 行时必须是 `ok`。
#   为什么需要这一条：CI run 36234389326 交出的两张"截图"**逐字节相同**，而产物里的
#   `am-start-*.txt` 其实早就写着 `Error type 3: Activity class … does not exist`
#   （connected 测试跑完 AGP 会把被测包卸掉），`foreground-*.txt` 写的是
#   `com.android.launcher3/.Launcher`——两张都是桌面。`am` 遇到这种错误**退出码仍是 0**，
#   所以"命令没失败"完全不能当证据用。
#
#   --lint-xml switches to LINT-EVIDENCE mode: the JUnit counters do not apply to
#   a lint report, so instead of "tests ran" this proves "lint really produced a
#   report with parseable content" (non-empty, <issues> root, issue elements, no
#   fatal severity, and a non-stub HTML companion when asked for).
#
# Exit codes: 0 evidence is real, 1 evidence missing/empty/failing, 2 usage.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

LABEL="artifacts"
MIN_TESTS=1
LINT_XML=""
LINT_HTML=""
FG_PKG=""
declare -a XML_DIRS=() HTML_DIRS=() SHOT_DIRS=() PROV_DIRS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --label) LABEL="$2"; shift 2 ;;
    --xml-dir) XML_DIRS+=("$2"); shift 2 ;;
    --html-dir) HTML_DIRS+=("$2"); shift 2 ;;
    --screenshots-dir) SHOT_DIRS+=("$2"); shift 2 ;;
    --shot-provenance) PROV_DIRS+=("$2"); shift 2 ;;
    --foreground-pkg) FG_PKG="$2"; shift 2 ;;
    --min-tests) MIN_TESTS="$2"; shift 2 ;;
    --lint-xml) LINT_XML="$2"; shift 2 ;;
    --lint-html) LINT_HTML="$2"; shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1" ;;
  esac
done

# ── lint mode ────────────────────────────────────────────────────────────────
if [ -n "$LINT_XML" ]; then
  if [ "${#XML_DIRS[@]}" -gt 0 ] || [ "${#HTML_DIRS[@]}" -gt 0 ] || [ "${#SHOT_DIRS[@]}" -gt 0 ]; then
    die_usage "--lint-xml cannot be combined with --xml-dir/--html-dir/--screenshots-dir"
  fi
  LINT_FILE="$LINT_XML"
  if [ -d "$LINT_XML" ]; then
    found="$(find "$LINT_XML" -type f -name 'lint-results*.xml' | head -1)" || found=""
    [ -n "$found" ] || die "$LABEL: no lint-results*.xml under $LINT_XML — lint wrote no report, which is a FAILURE not a pass"
    LINT_FILE="$found"
  fi
  require_file "$LINT_FILE" "lint XML report"
  grep -q '<issues' "$LINT_FILE" ||
    die "$LABEL: $LINT_FILE has no <issues> root — it is not a lint report, so it proves nothing"
  ISSUE_COUNT="$(grep -c '<issue' "$LINT_FILE")" || ISSUE_COUNT=0
  ERR_COUNT="$(grep -c 'severity="Error"' "$LINT_FILE")" || ERR_COUNT=0
  FATAL_COUNT="$(grep -c 'severity="Fatal"' "$LINT_FILE")" || FATAL_COUNT=0
  BYTES="$(wc -c <"$LINT_FILE" | tr -d ' ')"
  log "$LABEL: $LINT_FILE — $BYTES bytes, $ISSUE_COUNT issue element(s), $ERR_COUNT Error, $FATAL_COUNT Fatal"
  [ "$ISSUE_COUNT" -gt 0 ] ||
    die "$LABEL: the lint XML declares 0 <issue> elements in $BYTES bytes — an empty report is not evidence that lint ran"
  if [ "$FATAL_COUNT" -gt 0 ]; then
    die "$LABEL: lint reported $FATAL_COUNT Fatal issue(s) — the build must not go green on top of them"
  fi
  if [ -n "$LINT_HTML" ]; then
    require_file "$LINT_HTML" "lint HTML report"
    html_bytes="$(wc -c <"$LINT_HTML" | tr -d ' ')"
    [ "$html_bytes" -ge 4096 ] ||
      die "$LABEL: lint HTML report is a stub ($html_bytes bytes): $LINT_HTML"
    log "$LABEL: lint HTML report: $LINT_HTML ($html_bytes bytes)"
  fi
  ok "$LABEL lint evidence gate passed: $ISSUE_COUNT issue(s), $ERR_COUNT error(s), 0 fatal, report is real"
  exit 0
fi

[ "${#XML_DIRS[@]}" -gt 0 ] || die_usage "at least one --xml-dir is required (or use --lint-xml for the lint report)"

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
# 光数张数和字节数不够：CI 上真出过 home.png 与 knowledge-base.png **字节数完全相同**
# （各 115128 B），也就是两次 screencap 拍到的是同一屏。那种"视觉证据"什么都没证明，
# 却能让"截图非空"这一条过关。所以这里还要断言两两不同，并把每张的指纹打出来，
# 让产物本身可被复核。
for d in "${SHOT_DIRS[@]}"; do
  [ -d "$d" ] || die "$LABEL: screenshot directory does not exist: $d"
  N="$(find "$d" -type f -name '*.png' | grep -c . )" || N=0
  [ "$N" -gt 0 ] || die "$LABEL: screenshot directory is empty: $d — visual evidence is required"
  seen_hashes=""
  duplicates=""
  while read -r png; do
    [ -n "$png" ] || continue
    size="$(wc -c <"$png" | tr -d ' ')"
    [ "$size" -ge 4096 ] || die "$LABEL: screenshot is a stub ($size bytes): $png"
    # 字节数够不代表是图：先认 PNG 签名，否则一个塞满 0 的文件也能冒充"视觉证据"。
    head8="$(head -c 8 "$png" | od -An -tx1 | tr -d ' \n')"
    [ "$head8" = "89504e470d0a1a0a" ] ||
      die "$LABEL: $png 开头不是 PNG 签名（$head8）——这不是截图，是拿字节数凑出来的证据"
    h="$(sha256_of "$png")"
    log "$LABEL: shot $(basename "$png") — $size bytes, sha256 ${h:0:16}…"
    case " $seen_hashes " in
      *" $h "*) duplicates="$duplicates $(basename "$png")" ;;
      *) seen_hashes="$seen_hashes $h" ;;
    esac
  done < <(find "$d" -type f -name '*.png')
  [ -z "$duplicates" ] ||
    die "$LABEL: 这些截图与前面某张逐字节相同：$duplicates —— 同一屏拍两张不是两块屏幕的视觉证据（CI 上 home/knowledge-base 各 115128 字节正是这种）"
  log "$LABEL: $N screenshot(s) captured in $d, all $N distinct"
done

# ── 视觉证据的来源证明（每张截图都要说得出它拍的是哪一屏）─────────────────────
# 上面那条"两两不同"只能证明不是同一张图，证明不了图里是被测应用：
# CI run 36234389326 那两张桌面截图如果内容碰巧差一像素，就能带着"两两不同"过关。
# 这里改成读**产物自己**说的两件事：启动命令的输出干净、拍的那一刻前台属于被测包。
if [ "${#PROV_DIRS[@]}" -gt 0 ]; then
  [ -n "$FG_PKG" ] ||
    die "$LABEL: --shot-provenance 需要同时给 --foreground-pkg（否则没法判断前台是不是被测应用自己）"
  for d in "${PROV_DIRS[@]}"; do
    [ -d "$d" ] || die "$LABEL: provenance directory does not exist: $d"
    for png_d in "${SHOT_DIRS[@]}"; do
      while read -r png; do
        [ -n "$png" ] || continue
        nm="$(basename "$png" .png)"
        fg="$d/foreground-$nm.txt"
        am="$d/am-start-$nm.txt"
        [ -f "$fg" ] ||
          die "$LABEL: screenshot $png has no provenance file $fg —— 光有 PNG 不算视觉证据"
        [ -f "$am" ] ||
          die "$LABEL: screenshot $png has no launch record $am —— 没留下启动命令的输出，就无法判断它真到过前台"
        [ -s "$fg" ] || die "$LABEL: $fg is empty — $nm.png 没有来源证明"
        [ -s "$am" ] || die "$LABEL: $am is empty — 启动 $nm 的那条命令什么都没输出"
        if grep -qE 'Error: |Exception|does not exist|Permission Denial' "$am"; then
          die "$LABEL: $am 里写着启动失败（$(grep -m1 -E 'Error|Exception|Permission Denial' "$am" | tr -s ' \t' ' ')）—— $nm.png 不可能是那一屏的证据；am 的退出码是 0，别拿它当结论"
        fi
        if ! grep -q "$FG_PKG/" "$fg"; then
          die "$LABEL: 拍 $nm.png 的那一刻前台不是 $FG_PKG，实到：$(tr -s ' \t' ' ' <"$fg" | head -c 200) —— 这张图拍的是别的界面（launcher/别的应用/黑屏），不能当视觉证据"
        fi
        log "$LABEL: provenance for $nm OK — $(tr -s ' \t' ' ' <"$fg" | grep -o "$FG_PKG/[^ }]*" | head -1)"
      done < <(find "$png_d" -type f -name '*.png' 2>/dev/null)
    done
  done
fi

ok "$LABEL evidence gate passed: $TESTS tests, $CLASS_COUNT suite(s), 0 failures, 0 errors"
