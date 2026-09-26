#!/usr/bin/env bash
#
# scripts/test_assert_artifacts.sh
#
# 证据门自己的尺（第 13 窗口，2026-09-26）。
#
# 起因是 CI run 36234389326：`ui-test` 交出的 home.png 与 knowledge-base.png 逐字节相同，
# 产物里 `am-start-*.txt` 早就写着 `Error type 3: Activity class … does not exist`
# （connected 测试跑完 AGP 把被测包卸掉了），`foreground-*.txt` 写着
# `com.android.launcher3/.Launcher` —— **两张都是安卓桌面**。
# 而 `am` 这种失败**退出码仍是 0**，所以脚本里 `if ! adb shell "am start …"` 一句都不会红。
#
# 本脚本测的是补上的那条判据：**每张截图必须有"拍的那一刻前台是谁"的来源证明，
# 且前台必须属于被测包**。它必须能被"坏实现"打破，所以每一格都跑两遍：
#   * 对**工作树里的新门**（scripts/assert_artifacts.sh）——假证据要红；
#   * 对 **门合入前那一版**（`git show $BAD_GATE_REF:…`，基线钉在 `9f29539`，不是活引用）——
#     第 3 格（两张不同的桌面截图 + 前台是 launcher）它必须**放绿**，
#     这一格就是"新判据真有牙"的证据；它要是也红了，说明我加的判据其实早就存在。
# ⚠ 基线不许写成 HEAD：门合进去之后 HEAD 自己就带着新判据，那一格会从"旧版放绿"变成"旧版也红"，把自己的对照搞没。
#
# 每一格断言两件事：退出码 + 输出里点名的那句理由（只断退出码不够——
# 一份产物会因为**别的原因**失败，看起来就像新判据起作用了）。
#
# Usage:
#   bash scripts/test_assert_artifacts.sh
#
# Exit codes: 0 每格都如预期；1 有格判错；2 前置工具缺失（=没验证）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

NEW_GATE="$SCRIPT_DIR/assert_artifacts.sh"
PKG="com.lovebrain.app"

WORK=""
if [ -n "${WORK_DIR:-}" ]; then
  WORK="$WORK_DIR"
  mkdir -p "$WORK"
else
  WORK="$(mktemp -d)"
  trap 'rm -rf "$WORK"' EXIT
fi

# HEAD 那一版（CI 实际跑过的判据）——取不到就**失败**，不许跳过：
# 跳过等于"坏实现对照"这一半没跑，而那正是本脚本存在的理由。
# "坏实现"必须钉在**新判据落地之前**那一笔，不能跟着 HEAD 走：
# 这一格合并之后 HEAD 就带着新门了，`git show HEAD:…` 会取到"已经会红的那一版"，
# 于是"旧版放绿"这条对照**自己变成红**——报的还是"判据有 bug"，其实是我把基线写成了活引用。
BAD_GATE_REF="${BAD_GATE_REF:-9f29539}"   # = ddb2153 的父亲（visual-evidence 门合入前的最后一笔）
HEAD_DIR="$WORK/head"
mkdir -p "$HEAD_DIR/lib"
OLD_GATE="$HEAD_DIR/assert_artifacts.sh"
if ! git -C "$(dirname "$SCRIPT_DIR")" cat-file -e "${BAD_GATE_REF}^{commit}" 2>/dev/null ||
   ! git -C "$(dirname "$SCRIPT_DIR")" merge-base --is-ancestor "$BAD_GATE_REF" HEAD; then
  die "CANNOT-VERIFY: $BAD_GATE_REF 不是 HEAD 的祖先（或不存在）——'坏实现'对照不能跑，本脚本不许算通过"
fi
if ! git -C "$(dirname "$SCRIPT_DIR")" show "$BAD_GATE_REF:scripts/assert_artifacts.sh" >"$OLD_GATE" 2>/dev/null ||
   ! git -C "$(dirname "$SCRIPT_DIR")" show "$BAD_GATE_REF:scripts/lib/gate_lib.sh" >"$HEAD_DIR/lib/gate_lib.sh" 2>/dev/null; then
  die "CANNOT-VERIFY: could not read scripts/assert_artifacts.sh (and lib/gate_lib.sh) from $BAD_GATE_REF — the 'bad implementation' half of this test cannot run"
fi
printf 'bad-implementation baseline: %s\n' "$(git -C "$(dirname "$SCRIPT_DIR")" rev-parse --short "$BAD_GATE_REF")"

# ── 造一份"看起来完全合法"的产物 ══════════════════════════════════════════
# 一张 1 个用例的 JUnit XML + HTML + 两张**互不相同**、签名正确的 PNG。
mk_png() {
  # mk_png <path> <salt> — PNG 签名 + 足够字节数；salt 让两张图的 sha256 不同。
  printf '\211\120\116\107\015\012\032\012' >"$1"
  awk -v s="$2" 'BEGIN { for (i = 0; i < 600; i++) printf "pad-%d-%s\n", i, s }' >>"$1"
}

mk_suite() {
  # mk_suite <dir> — 建出一套除"前台是谁"之外全部合格的证据。
  local d="$1"
  mkdir -p "$d/xml" "$d/html" "$d/screenshots"
  cat >"$d/xml/TEST-suite.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="probe" tests="1" skipped="0" failures="0" errors="0" time="0.1">
  <testcase name="cell" classname="probe.Cell" time="0.1"/>
</testsuite>
XML
  printf '<html><body>probe report with enough bytes to be a real artifact %s</body></html>\n' \
    "$(awk 'BEGIN{for(i=0;i<500;i++)printf "x"}')" >"$d/html/index.html"
  mk_png "$d/screenshots/home.png" home
  mk_png "$d/screenshots/knowledge-base.png" kb
}

mk_prov() {
  # mk_prov <dir> <fg-line-for-home> <fg-line-for-kb> [am-home am-kb]
  local d="$1" fg_home="$2" fg_kb="$3"
  local am_home="${4:-Starting: Intent { cmp=$PKG/.ui.SetupActivity }
Status: ok
LaunchState: WARM
Activity: $PKG/.ui.SetupActivity
Total Time: 412}"
  local am_kb="${5:-Starting: Intent { cmp=$PKG/.ui.KnowledgeBaseActivity }
Status: ok
LaunchState: WARM
Activity: $PKG/.ui.KnowledgeBaseActivity
Total Time: 388}"
  mkdir -p "$d"
  printf '%s\n' "$fg_home" >"$d/foreground-home.txt"
  printf '%s\n' "$fg_kb" >"$d/foreground-knowledge-base.txt"
  printf '%s\n' "$am_home" >"$d/am-start-home.txt"
  printf '%s\n' "$am_kb" >"$d/am-start-knowledge-base.txt"
}

run_gate() {
  # run_gate <script> <evidence-dir> [--no-provenance]
  local script="$1" d="$2" mode="${3:-prov}" out="$WORK/last.out" rc=0
  set +e
  if [ "$mode" = "prov" ]; then
    bash "$script" --label probe --xml-dir "$d/xml" --html-dir "$d/html" \
      --screenshots-dir "$d/screenshots" --min-tests 1 \
      --shot-provenance "$d" --foreground-pkg "$PKG" >"$out" 2>&1
  else
    bash "$script" --label probe --xml-dir "$d/xml" --html-dir "$d/html" \
      --screenshots-dir "$d/screenshots" --min-tests 1 >"$out" 2>&1
  fi
  rc=$?
  set -e
  return "$rc"
}

PASS_CELLS=0
FAIL_CELLS=0

expect() {
  # expect <格名> <脚本> <期望退出码 0|nonzero> <必须出现在输出里的理由 needle> <参数...>
  local name="$1" script="$2" want="$3" needle="$4" d="$5" mode="${6:-prov}"
  local rc=0
  run_gate "$script" "$d" "$mode" || rc=$?
  local verdict="PASS"
  if [ "$want" = "0" ] && [ "$rc" -ne 0 ]; then verdict="FAIL"; fi
  if [ "$want" = "nonzero" ] && [ "$rc" -eq 0 ]; then verdict="FAIL"; fi
  if [ "$verdict" = "PASS" ] && [ "$want" = "nonzero" ] && [ -n "$needle" ] &&
    ! grep -qi -- "$needle" "$WORK/last.out"; then
    verdict="FAIL"
    printf '  %s: 退出了非 0，但理由不是「%s」——实际输出：\n' "$name" "$needle"
    sed 's/^/      /' "$WORK/last.out" | tail -6
  fi
  if [ "$verdict" = "FAIL" ]; then
    FAIL_CELLS=$((FAIL_CELLS + 1))
    printf 'FAIL  %-46s rc=%s (want %s)\n' "$name" "$rc" "$want"
    sed 's/^/        | /' "$WORK/last.out" | tail -4
  else
    PASS_CELLS=$((PASS_CELLS + 1))
    printf 'ok    %-46s rc=%s (want %s)\n' "$name" "$rc" "$want"
  fi
}

# 真实 CI 产物里的两行（run 36234389326 原样）
REAL_LAUNCHER_FG='mResumedActivity: ActivityRecord{dcb9e4f u0 com.android.launcher3/.Launcher t5}'
REAL_APP_HOME_FG='mResumedActivity: ActivityRecord{1a2b3c4 u0 com.lovebrain.app/.ui.SetupActivity t7}'
REAL_APP_KB_FG='mResumedActivity: ActivityRecord{5d6e7f8 u0 com.lovebrain.app/.ui.KnowledgeBaseActivity t7}'
# 桌面在两个时刻的转储只差时间片编号 —— 这就是"两张不同的桌面截图"的形状
REAL_LAUNCHER_FG_LATER='mResumedActivity: ActivityRecord{dcb9e50 u0 com.android.launcher3/.Launcher t5}'

printf '== 视觉证据的门：每张截图必须说得出自己拍的是哪一屏 ==\n'

# ── 1. 全部合格 ──────────────────────────────────────────────────────────────
D="$WORK/good"; mk_suite "$D"; mk_prov "$D" "$REAL_APP_HOME_FG" "$REAL_APP_KB_FG"
expect "1 两屏都真到了前台 → 该绿" "$NEW_GATE" 0 "" "$D"

# ── 2. 真 CI 那一跑：两张逐字节相同的桌面截图 ────────────────────────────────
D="$WORK/ci-identical"; mk_suite "$D"
cp "$D/screenshots/home.png" "$D/screenshots/knowledge-base.png"
mk_prov "$D" "$REAL_LAUNCHER_FG" "$REAL_LAUNCHER_FG"
expect "2 两张同图（CI 原样）→ 该红" "$NEW_GATE" nonzero "逐字节相同" "$D"
expect "2b 同一份产物钉的那一版也红（同图规则是旧的）" "$OLD_GATE" nonzero "逐字节相同" "$D" noprov

# ── 3. 关键一格：两张**不同**的桌面截图（同图规则抓不到） ────────────────────
D="$WORK/launcher-distinct"; mk_suite "$D"
mk_prov "$D" "$REAL_LAUNCHER_FG" "$REAL_LAUNCHER_FG_LATER"
expect "3 两张不同桌面图 → 新门该红" "$NEW_GATE" nonzero "launcher" "$D"
expect "3b 同一份产物钉的那一版放绿（新判据真有牙）" "$OLD_GATE" 0 "" "$D" noprov

# ── 4. am start 里写着 does not exist，但退出码是 0 ──────────────────────────
D="$WORK/am-error"; mk_suite "$D"
mk_prov "$D" "$REAL_APP_HOME_FG" "$REAL_APP_KB_FG" \
  "Starting: Intent { cmp=$PKG/.ui.SetupActivity }
Error type 3
Error: Activity class {$PKG/$PKG.ui.SetupActivity} does not exist."
expect "4 am 报了 does not exist → 该红" "$NEW_GATE" nonzero "启动失败" "$D"

# ── 5. 缺来源证明文件 ────────────────────────────────────────────────────────
# 只制造**一处**缺失：先把两份 provenance 都写全，再只删 home 那份。
# 第一版这里没调 mk_prov（两份都缺），于是"点名哪个文件"取决于 find 的遍历顺序：
# 本机先 home（像正确）、CI 先 knowledge-base（run 36249148671 那一格就这么判错）。
D="$WORK/no-prov"; mk_suite "$D"; mk_prov "$D" "$REAL_APP_HOME_FG" "$REAL_APP_KB_FG"
rm -f "$D/foreground-home.txt"
expect "5 少了 foreground-home.txt → 该红" "$NEW_GATE" nonzero "foreground-home.txt" "$D"

# ── 6. 不传 provenance 时，旧行为不变（别的门禁不受影响）─────────────────────
D="$WORK/good2"; mk_suite "$D"; mk_prov "$D" "$REAL_APP_HOME_FG" "$REAL_APP_KB_FG"
expect "6 不开 provenance → 仍按旧口径绿" "$NEW_GATE" 0 "" "$D" noprov

printf -- '----\n%d cell(s) as expected, %d not\n' "$PASS_CELLS" "$FAIL_CELLS"
[ "$FAIL_CELLS" -eq 0 ] || die "assert_artifacts.sh 的视觉证据判据有 $FAIL_CELLS 格不符合预期"
ok "assert_artifacts evidence-provenance self-test passed ($PASS_CELLS cells, $BAD_GATE_REF compared cell by cell as the bad implementation)"
