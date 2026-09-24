#!/usr/bin/env bash
#
# scripts/run_ui_tests.sh
#
# The Compose / instrumentation run, with the retry loop that used to live
# inline in ci.yml. Re-audit 2026-09-23 §3 and §8 step 1.2:
#   "修复 emulator retry：把完整 retry 写成仓库内可本地执行的 bash 脚本，
#    workflow 只调用脚本；脚本加 set -euo pipefail 并保留最终退出码"
# The inline YAML block failed on the runner with
#   /usr/bin/sh: Syntax error: end of file unexpected (expecting "fi")
# because a multi-line `if` was pasted into a shell that was not bash.
#
# Behaviour:
#   * runs the Gradle connected test task up to --attempts times (default 2)
#   * every attempt's real exit code is captured, never swallowed
#   * the LAST attempt's exit code is the script's exit code
#   * after the run (pass or fail) logcat and screenshots are captured as
#     evidence and scripts/assert_artifacts.sh decides whether the evidence is
#     real — an empty XML/HTML/screenshot set fails even if Gradle said green
#
# Locally runnable:
#   emulator: demo & device online, then
#   bash scripts/run_ui_tests.sh --min-tests 30
#
# Usage:
#   bash scripts/run_ui_tests.sh [--attempts N] [--task connectedDebugAndroidTest]
#        [--artifacts-dir DIR] [--min-tests N|auto] [-- <extra gradle args>]
#
# Exit codes: the Gradle task's exit code, or 1 when the evidence gate fails on
# a green run, or 2 for usage/tooling problems.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

ATTEMPTS=2
TASK="connectedDebugAndroidTest"
ARTIFACTS="build/gate-artifacts/ui-test"
MIN_TESTS="auto"
GRADLE_ARGS=()
PASSED_THROUGH=0

while [ $# -gt 0 ]; do
  if [ "$PASSED_THROUGH" -eq 1 ]; then
    GRADLE_ARGS+=("$1")
    shift
    continue
  fi
  case "$1" in
    --attempts) ATTEMPTS="$2"; shift 2 ;;
    --task) TASK="$2"; shift 2 ;;
    --artifacts-dir) ARTIFACTS="$2"; shift 2 ;;
    --min-tests) MIN_TESTS="$2"; shift 2 ;;
    --) PASSED_THROUGH=1; shift ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1 (use -- to pass extra gradle args)" ;;
  esac
done

case "$ATTEMPTS" in
  '' | *[!0-9]*) die_usage "--attempts must be a positive integer" ;;
esac
[ "$ATTEMPTS" -ge 1 ] || die_usage "--attempts must be >= 1"

cd "$(repo_root)"
[ -x "./gradlew" ] || die "gradlew is not executable — run: chmod +x gradlew"

if ! command -v adb >/dev/null 2>&1; then
  export PATH="$(sdk_root)/platform-tools:$PATH"
fi
require_cmd adb
require_device

# AGP 8 writes the machine-readable results under androidTest-results/connected
# (one XML per class) and the human report under reports/androidTests/connected.
XML_DIRS=("app/build/outputs/androidTest-results/connected")
HTML_DIRS=("app/build/reports/androidTests/connected")
SCREENSHOT_DIR="$ARTIFACTS/screenshots"
mkdir -p "$SCREENSHOT_DIR"

if [ "$MIN_TESTS" = "auto" ]; then
  # The expectation is derived from the repository itself so a suite that
  # silently stops running cannot pass.
  #
  # Counting must tolerate "zero matches": grep exits 1 on an empty result, and under
  # `set -euo pipefail` that used to abort this script *because the tree had no @Ignore*
  # — i.e. the cleaner the repo, the more certain the CI failure. The fail-closed
  # property stays where it belongs: a zero @Test count still refuses to run.
  SRC_TESTS="$(count_annotations app/src/androidTest '^\s*@Test')"
  IGNORED="$(count_annotations app/src/androidTest '^\s*@Ignore')"
  [ "$SRC_TESTS" -gt 0 ] || die "could not count @Test annotations under app/src/androidTest — refusing to run an unbounded UI gate"
  MIN_TESTS=$((SRC_TESTS - IGNORED))
  [ "$MIN_TESTS" -ge 1 ] || MIN_TESTS=1
  log "expected instrumentation tests derived from sources: $SRC_TESTS declared - $IGNORED @Ignore = $MIN_TESTS"
fi

log "UI test task: :app:$TASK (attempts=$ATTEMPTS, min-tests=$MIN_TESTS)"

rc=0
attempt=1
while [ "$attempt" -le "$ATTEMPTS" ]; do
  log "attempt $attempt/$ATTEMPTS: ./gradlew :app:$TASK ${GRADLE_ARGS[*]+${GRADLE_ARGS[*]}}"
  rc=0
  set +e
  # tee keeps the console output the same as before the extraction while the
  # pipeline exit status (pipefail) remains Gradle's own.
  ./gradlew ":app:$TASK" --no-daemon ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"} 2>&1 |
    tee "$ARTIFACTS/gradle-attempt-$attempt.log"
  rc=${PIPESTATUS[0]}
  set -e
  if [ "$rc" -eq 0 ]; then
    log "attempt $attempt succeeded"
    break
  fi
  log "attempt $attempt FAILED with exit code $rc"
  if [ "$attempt" -lt "$ATTEMPTS" ]; then
    # Give the emulator a moment to settle, then retry. The failure itself is
    # never forgiven here — only the final attempt decides the exit code.
    adb logcat -d -v time >"$ARTIFACTS/logcat-attempt-$attempt.txt" ||
      log "could not capture logcat for attempt $attempt (the retry will still run)"
    log "retrying :app:$TASK"
    sleep 10
    if ! adb wait-for-device; then
      log "the device never came back — reporting the original failure"
      break
    fi
  fi
  attempt=$((attempt + 1))
done

# ── evidence capture (required, not optional) ────────────────────────────────
if ! adb logcat -d -v time >"$ARTIFACTS/logcat-final.txt"; then
  die "could not capture the final logcat dump into $ARTIFACTS/logcat-final.txt"
fi
[ -s "$ARTIFACTS/logcat-final.txt" ] || die "the captured logcat dump is empty: $ARTIFACTS/logcat-final.txt"

for pair in "home:com.lovebrain.app/.ui.SetupActivity" "knowledge-base:com.lovebrain.app/.ui.KnowledgeBaseActivity"; do
  name="${pair%%:*}"
  comp="${pair#*:}"
  shot="$SCREENSHOT_DIR/$name.png"
  # The screens are captured as visual evidence for the release packet
  # (re-audit §9 item 9). A failed launch here is a real regression, so it dies.
  if ! adb shell "am start -W -n $comp" >"$ARTIFACTS/am-start-$name.txt" 2>&1; then
    cat "$ARTIFACTS/am-start-$name.txt" >&2
    die "could not launch $comp to capture $shot — the main screen is not reachable"
  fi
  sleep 2
  # 这张图到底拍的是哪一屏，必须由产物自己说清楚：CI 上真出过 home.png 与
  # knowledge-base.png 逐字节相同（各 115128 B），也就是第二个 activity 其实没把
  # 前一层换掉（立刻 finish、被转走、或者压根没到前台）。光有 PNG 不算视觉证据。
  adb shell dumpsys activity activities \
    | grep -E "mResumedActivity|topResumedActivity|mFocusedApp" | head -3 \
    >"$ARTIFACTS/foreground-$name.txt" || true
  if [ ! -s "$ARTIFACTS/foreground-$name.txt" ]; then
    die "could not read the foreground activity before capturing $shot — the screenshot has no provenance"
  fi
  log "foreground for $name: $(tr -s ' \t' ' ' <"$ARTIFACTS/foreground-$name.txt")"
  if ! adb exec-out screencap -p >"$shot"; then
    die "screencap failed for $shot — visual evidence cannot be produced"
  fi
  [ -s "$shot" ] || die "screenshot is empty: $shot"
  log "captured $shot ($(wc -c <"$shot" | tr -d ' ') bytes)"
done

# ── the evidence gate ────────────────────────────────────────────────────────
evidence_rc=0
bash "$SCRIPT_DIR/assert_artifacts.sh" \
  --label "ui-test" \
  --xml-dir "${XML_DIRS[0]}" \
  --html-dir "${HTML_DIRS[0]}" \
  --screenshots-dir "$SCREENSHOT_DIR" \
  --min-tests "$MIN_TESTS" || evidence_rc=$?

if [ "$rc" -ne 0 ]; then
  log "Gradle reported failure (exit $rc); evidence gate reported $evidence_rc"
  exit "$rc"
fi
if [ "$evidence_rc" -ne 0 ]; then
  die "Gradle exited 0 but the evidence gate failed — an empty/short instrumentation report is a FAILURE (audit §8.1 item 6)"
fi

ok "UI tests green with real evidence (exit code 0)"
