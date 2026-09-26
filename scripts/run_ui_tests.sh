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
# 截图这一段的三个设备动作（装包、干净地启动活动、读"此刻前台是谁"）共用升级测试
# 那一套 helper —— 它们本来就会把 Error/Permission Denial/does not exist 判成失败，
# 而 run_ui_tests.sh 里那份自己写的 `adb shell "am start …"` 不会（am 的退出码是 0）。
# shellcheck source=lib/device_lib.sh
. "$SCRIPT_DIR/lib/device_lib.sh"

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

# ── 视觉证据：**设备侧不再产 PNG，理由写进产物** ───────────────────
#
# 这里曾经拍两张"am start 到两块屏幕再 screencap"的图。两次实到把它判死：
#   · run 36234389326：两张图逐字节相同，而产物里 `am-start-*.txt` 写着
#     `Error type 3: Activity class … does not exist`（connected 测试跑完 AGP 会卸掉被测包），
#     `foreground-*.txt` 写着 `com.android.launcher3/.Launcher` —— **拍的是桌面**；
#   · run 36236822959：按规矩修好（装回去、前台做成断言）之后，前台确认是
#     `com.lovebrain.app/.ui.SetupActivity`，`adb exec-out screencap -p` 却交回 **0 字节**。
# 根因不是拿错机器：这三个 Activity 全部设了 `FLAG_SECURE`
#   （`SetupActivity.kt:89`、`KnowledgeBaseActivity.kt:113`、`KbEditActivity.kt:125`，
#   注释写着理由：防止 API Key / 关系数据在最近任务截图里泄露）。
#   `FLAG_SECURE` 就是让系统拒绝把这一屏画进截图 ⇒ "设备拍两张真屏幕"这条要求
#   在这个 app 上结构性拿不到证据；继续要 PNG 只会再次收下一张假证据。
#
# 取代它的是 **JVM 截图基线**（指导书 :610 点名的 roborazzi / paparazzi 二选一，这里选 roborazzi）：
#   基线在 `app/src/test/roborazzi/`，CI 只跑 `:app:verifyRoborazziDebug`（不匹配即红）；
#   重新生成是显式人工动作（`./gradlew :app:recordRoborazziDebug` + 人看过 + 单独一笔提交）。
#   ⚠ 它不能靠普通单测那一步：实测带着一次真实视觉回归（主按钮 min 高度 48dp → 64dp）
#   跑 `:app:testDebugUnitTest` 仍然 rc=0 全绿——roborazzi 默认模式只重录不比对。
#
# 这个文件本身**被断言存在、非空、且写清两件事**（缺一句就红），
# 不许把"不再要 PNG"写成一个看不见的空白。
VISUAL_STATEMENT="$ARTIFACTS/visual-evidence.md"
{
  printf '# 视觉证据口径\n\n'
  printf -- '- **设备侧不产 PNG（有意为之，不是没做）**：三个 Activity 全部设了 `FLAG_SECURE`\n'
  printf -- '  （`SetupActivity.kt:89` / `KnowledgeBaseActivity.kt:113` / `KbEditActivity.kt:125`），\n'
  printf -- '  注释写着理由：防止 API Key 与关系数据在最近任务截图里泄露。\n'
  printf -- '  实到：run 36236822959 在前台确认为 `com.lovebrain.app/.ui.SetupActivity` 之后，\n'
  printf -- '  `adb exec-out screencap -p` 交回 **0 字节**；再之前（run 36234389326）那两张\n'
  printf -- '  "114996 字节的截图"拍的是 `com.android.launcher3/.Launcher`（桌面）。\n'
  printf -- '- **取代它的是 JVM 截图基线**：基线在 `app/src/test/roborazzi/`，\n'
  printf -- '  CI 只跑 `verifyRoborazziDebug`（不匹配即红）；重新生成是显式人工动作 + 单独一笔提交。\n'
  printf -- '  普通单测那一步看不见视觉回归（实测：改高度之后 `testDebugUnitTest` 仍 rc=0）。\n'
  printf -- '- :538 的人工 review 规矩：节点只能由人跑 record、看过图、再提交；CI 永远只跑 verify。\n'
} >"$VISUAL_STATEMENT"
[ -s "$VISUAL_STATEMENT" ] ||
  die "视觉证据口径没写出来（$VISUAL_STATEMENT 为空）——不允许留着空白继续跑"
for needle in 'FLAG_SECURE' 'verifyRoborazziDebug'; do
  grep -q -- "$needle" "$VISUAL_STATEMENT" ||
    die "$VISUAL_STATEMENT 里没写清楚「$needle」—— 撤掉设备截图要求必须连理由一起交"
done
log "visual evidence statement: $VISUAL_STATEMENT (device PNGs intentionally not produced)"

# ── the evidence gate ──────────────────────────────────────────────────────────
# 注意：这里**不再传** --screenshots-dir / --shot-provenance。
#   不是把判据改软——拍屏那一路已被证明拿不到证据（FLAG_SECURE），
#   继续要 PNG 只会让下一跑继续交桌面图。取代它的是上面那份被断言非空的口径声明
#   + CI 里独立的一步 verifyRoborazziDebug。
#   那套额外的截图判据（非空 / 尺寸 / PNG 魔数 / 两两不同 / provenance）保留在
#   assert_artifacts.sh 里继维护，脚本自测 8 格照旧；谁要恢复设备侧截图，把参数加回来即可。
evidence_rc=0
bash "$SCRIPT_DIR/assert_artifacts.sh" \
  --label "ui-test" \
  --xml-dir "${XML_DIRS[0]}" \
  --html-dir "${HTML_DIRS[0]}" \
  --min-tests "$MIN_TESTS" || evidence_rc=$?

if [ "$rc" -ne 0 ]; then
  log "Gradle reported failure (exit $rc); evidence gate reported $evidence_rc"
  exit "$rc"
fi
if [ "$evidence_rc" -ne 0 ]; then
  die "Gradle exited 0 but the evidence gate failed — an empty/short instrumentation report is a FAILURE (audit §8.1 item 6)"
fi

ok "UI tests green with real evidence (exit code 0)"
