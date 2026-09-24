#!/usr/bin/env bash
#
# scripts/test_check_lint_budget.sh
#
# check_lint_budget.sh 判据本身的正反测试（同一份产物、同一份预算、两台机器量出
# 两个数，那把尺就不配当门禁）。
#
# 触发这件事的是 CI run 36019334520：verify 在 "Lint budget must not grow" 这一步红，
# CI 量到 81 条 / 16 规则，本机 71 条 / 15 规则，预算登记 15 规则。差的 10 条全部来自
# 两条**发现不属于这个仓库**的规则：
#
#   GradleDependency  报"上游又有新版可升"。同一句 androidx.test.ext:junit:1.1.5 声明，
#                     CI 说可升到 1.3.0，本机说可升到 1.2.1 —— 它量的是这台机器缓存到的
#                     Maven 版本清单。
#   OldTargetApi      报"targetSdk 不是最新"。触发与否取决于本机 SDK 里装了多新的平台。
#
# 所以这里的每一格都不只是测"改完对不对"，还测"改完还有没有牙"：
# 分类之后，仓库自己欠的债必须仍然红（OVER）、登记过期必须仍然红（STALE）、
# 冒出来的新规则必须仍然红，而降级成 advisory 必须写理由、且只允许外部状态族那几条。
#
# 夹具是两份真报告（run 36019334520 的产物 + 同一份代码在本机量出的产物），
# 不是手写的：见 scripts/fixtures/lint/README.md。
#
# Usage:
#   bash scripts/test_check_lint_budget.sh              # 本机（Windows 上要先 PYTHON=python）
#   PYTHON=python bash scripts/test_check_lint_budget.sh
#
# 退出码：0 全部判对；1 有格子判错；2 前置解释器缺失（=没验证）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

SCRIPT="$SCRIPT_DIR/check_lint_budget.sh"
FIX="$SCRIPT_DIR/fixtures/lint"
REAL_BUDGET="$ROOT/scripts/lint-budget.txt"

PYBIN="${PYTHON:-python3}"
if ! command -v "$PYBIN" >/dev/null 2>&1 || [ "$("$PYBIN" -c 'print("alive")' 2>/dev/null)" != "alive" ]; then
  die_unverified "python interpreter unusable ('PYTHON=$PYBIN') — on Windows use PYTHON=python"
fi
export PYTHON="$PYBIN"
# 断言只匹配 ASCII 片段：Windows 控制台是 GBK，被捕获的中文输出会变成另一种字节，
# 拿中文当判据的测试在这台机器上会假红。
export PYTHONIOENCODING=utf-8

if [ -n "${WORK_DIR:-}" ]; then
  WORK="$WORK_DIR"
  mkdir -p "$WORK"
else
  WORK="$(mktemp -d)"
  trap 'rm -rf "$WORK"' EXIT
fi

CASES=0
BAD=0
FAILED_NAMES=""

pass() { CASES=$((CASES + 1)); printf '%s  OK   %s\n' "$GATE_LOG_PREFIX" "$1" >&2; }
bad() {
  CASES=$((CASES + 1)); BAD=$((BAD + 1))
  FAILED_NAMES="$FAILED_NAMES
  - $1"
  printf '%s  FAIL %s\n' "$GATE_LOG_PREFIX" "$1" >&2
  shift
  local line
  for line in "$@"; do printf '       %s\n' "$line" >&2; done
}

# ── 夹具：合成报告 ──────────────────────────────────────────────────────────
# mk_report <out> <rule> <n> [<rule> <n> ...]
# 形状照真报告：根元素带 by="lint 8.6.0"，每个 <issue> 一行、属性里有 id 与 severity。
mk_report() {
  local out="$1"; shift
  {
    printf '<?xml version="1.0" encoding="UTF-8"?>\n<issues format="6" by="lint 8.6.0">\n'
    while [ "$#" -gt 0 ]; do
      local rule="$1" n="$2" i=1
      shift 2
      while [ "$i" -le "$n" ]; do
        printf '    <issue id="%s" severity="Warning" message="m" category="Correctness" priority="6"/>\n' "$rule"
        i=$((i + 1))
      done
    done
    printf '</issues>\n'
  } >"$out"
}

# mk_budget <out> <整行> [<整行> ...] —— 逐行原样写，方便构造"缺理由""重复登记"这类坏账本。
mk_budget() {
  local out="$1"; shift
  : >"$out"
  local l
  for l in "$@"; do printf '%s\n' "$l" >>"$out"; done
}

# gate <budget> <xml> [script args...] —— 跑被测试的脚本，结果落 out.txt / rc.txt
gate() {
  local budget="$1" xml="$2"; shift 2
  set +e
  LINT_BUDGET="$budget" LINT_XML="$xml" bash "$SCRIPT" "$@" >"$WORK/out.txt" 2>&1
  echo "$?" >"$WORK/rc.txt"
  set -e
}

rc() { cat "$WORK/rc.txt"; }

# expect_rc <标题> <期望码>
expect_rc() {
  if [ "$(rc)" = "$2" ]; then pass "$1（退出码 $2）"; else bad "$1" "退出码 $(rc)，应为 $2"; dump; fi
}

# expect_has <标题> <必须出现的片段>
expect_has() {
  if grep -qF -- "$2" "$WORK/out.txt"; then pass "$1"; else bad "$1" "输出里找不到片段：$2"; dump; fi
}

# expect_hasnt <标题> <必须不出现的片段>
expect_hasnt() {
  if grep -qF -- "$2" "$WORK/out.txt"; then bad "$1" "输出里出现了不该有的片段：$2"; dump
  else pass "$1"; fi
}

# expect_stats <标题> <key=value> [...] —— 断言 STATS 那行里的具体数字，别只断言"绿了"
expect_stats() {
  local title="$1"; shift
  local stats want k v missing=""
  stats="$(grep -m1 '^\[lint-budget\] STATS ' "$WORK/out.txt" || true)"
  if [ -z "$stats" ]; then bad "$title" "输出里没有 STATS 行，机器读不到具体条数"; dump; return 0; fi
  for want in "$@"; do
    k="${want%%=*}"; v="${want#*=}"
    if ! printf '%s\n' "$stats" | grep -qE "(^| )${k}=${v}( |$)"; then
      missing="$missing ${want}"
    fi
  done
  if [ -n "$missing" ]; then bad "$title" "STATS 对不上：$missing" "实际：$stats"
  else pass "$title"; fi
}

dump() {
  printf '       ---- 本次输出 ----\n' >&2
  sed 's/^/       /' "$WORK/out.txt" >&2
  printf '       ----------------\n' >&2
}

CB="$REAL_BUDGET"
CI_XML="$FIX/ci-run-36019334520-lint-results-debug.xml"
LOCAL_XML="$FIX/local-at-3d92488-lint-results-debug.xml"

# ── 夹具本身要能读，否则后面全是空跑 ────────────────────────────────────────
require_file "$CI_XML" "fixture (lint report from CI run 36019334520)"
require_file "$LOCAL_XML" "fixture (lint report from the same code, local machine)"

# ── C1 真产物回归：那份把 CI 判红的报告，用同一份预算必须不再报"新增债" ──────
printf '%s C1–C2：两份真报告 vs 同一份预算（两侧同尺）\n' "$GATE_LOG_PREFIX" >&2
gate "$CB" "$CI_XML"
expect_rc "C1 CI 夹具不再报新增债（run 36019334520 这一步当时是红的）" "0"
expect_has "C1 外部状态族被单独打印而不是塞进预算" "ADVISORY GradleDependency=10"
expect_has "C1 OldTargetApi 同样打印条数" "ADVISORY OldTargetApi=1"

# ── C2 两侧同尺：同一份代码在两台机器上进预算的那部分必须一模一样 ────────────
# 取数一律带 `|| true`：旧实现根本没有 STATS 行，`grep` 返回 1 在 `set -euo pipefail`
# 下会把本测试当场掐断——那副样子看着像"测出问题了"，其实是测试自己死了。
gated_of() {
  grep -m1 '^\[lint-budget\] STATS ' "$WORK/out.txt" |
    sed -n 's/.* gated_issues=\([0-9]*\) .*/\1/p' || true
}
gate "$CB" "$LOCAL_XML"
expect_rc "C2 本机夹具同一份预算也合规" "0"
CI_GATED="$(gated_of)"
gate "$CB" "$CI_XML"
CI_GATED2="$(gated_of)"
if [ -n "$CI_GATED" ] && [ "$CI_GATED" = "$CI_GATED2" ]; then
  pass "C2 进预算的条数两台机器相同（$CI_GATED 条）"
else
  bad "C2 两侧同尺没成立" "本机夹具 gated_issues=$CI_GATED" "CI 夹具 gated_issues=$CI_GATED2"
fi

# ── C3 还有牙：真债多一条必须红，分类不是免检通道 ────────────────────────────
printf '%s C3–C5：降级之后判据还得有牙\n' "$GATE_LOG_PREFIX" >&2
mk_report "$WORK/over.xml" UnusedResources 35 ModifierParameter 8 AutoboxingStateCreation 6 \
  ClickableViewAccessibility 1 DataExtractionRules 1 DefaultLocale 2 GradleDependency 1 \
  IconLauncherShape 5 IconLocation 3 InlinedApi 1 PluralsCandidate 3 RedundantLabel 1 \
  ReturnFromAwaitPointerEventScope 3 StaticFieldLeak 1 SwitchIntDef 1
gate "$CB" "$WORK/over.xml"
expect_rc "C3 UnusedResources 34→35 必须红" "1"
expect_has "C3 红的时候点名是哪条规则" "OVER UnusedResources"

# ── C4 还有牙：预算过期（债还掉了没落账）必须红 ─────────────────────────────
mk_budget "$WORK/tiny.txt" "UnusedResources 40" "ModifierParameter 9"
gate "$WORK/tiny.txt" "$WORK/over.xml"
expect_has "C4 实测比登记干净时仍然红（STALE）" "STALE UnusedResources"

# ── C5 还有牙：冒出来一条谁都没登记过的新规则，必须红 ───────────────────────
mk_report "$WORK/newrule.xml" UnusedResources 34 ModifierParameter 8 AutoboxingStateCreation 6 \
  ClickableViewAccessibility 1 DataExtractionRules 1 DefaultLocale 2 GradleDependency 1 \
  IconLauncherShape 5 IconLocation 3 InlinedApi 1 PluralsCandidate 3 RedundantLabel 1 \
  ReturnFromAwaitPointerEventScope 3 StaticFieldLeak 1 SwitchIntDef 1 SomethingBrandNew 2
gate "$CB" "$WORK/newrule.xml"
expect_rc "C5 未登记的新规则仍然红" "1"
expect_has "C5 说得出是新登记问题而非超预算" "OVER SomethingBrandNew"

# ── C6 advisory 不锁数：上游版本清单会漂，漂了不红，但必须看得见 ────────────
printf '%s C6–C9：advisory 的边界\n' "$GATE_LOG_PREFIX" >&2
mk_report "$WORK/adv-drift.xml" UnusedResources 34 ModifierParameter 8 AutoboxingStateCreation 6 \
  ClickableViewAccessibility 1 DataExtractionRules 1 DefaultLocale 2 GradleDependency 99 \
  IconLauncherShape 5 IconLocation 3 InlinedApi 1 PluralsCandidate 3 RedundantLabel 1 \
  ReturnFromAwaitPointerEventScope 3 StaticFieldLeak 1 SwitchIntDef 1
gate "$CB" "$WORK/adv-drift.xml"
expect_rc "C6 GradleDependency 10→99 不该把门禁判红（它量的不是这个仓库）" "0"
expect_has "C6 但 99 条要照样打印" "ADVISORY GradleDependency=99"

# ── C7 降级必须写理由 ───────────────────────────────────────────────────────
mk_budget "$WORK/noreason.txt" "UnusedResources 34" "advisory GradleDependency"
gate "$WORK/noreason.txt" "$WORK/adv-drift.xml"
expect_rc "C7 没有理由的 advisory 不算登记（=没验证）" "2"
expect_has "C7 说清为什么" "CANNOT-VERIFY"

# ── C8 不许把仓库自己的债降级 ───────────────────────────────────────────────
mk_budget "$WORK/demote.txt" "ModifierParameter 8" "advisory UnusedResources 我看它不顺眼"
gate "$WORK/demote.txt" "$WORK/adv-drift.xml"
expect_rc "C8 把 UnusedResources 降级成 advisory 必须被拒" "1"
expect_has "C8 拒的时候要给出路" "ADVISORY-FORBIDDEN UnusedResources"

# ── C9 同一条规则不许既进预算又进 advisory ─────────────────────────────────
mk_budget "$WORK/dup.txt" "UnusedResources 34" "GradleDependency 1" \
  "advisory GradleDependency 上游一发新版条数就变"
gate "$WORK/dup.txt" "$WORK/adv-drift.xml"
expect_rc "C9 重复登记按'读不出该信哪条'处理" "2"

# ── C10 尺要自报家门：本机绿不等于 CI 绿，日志里得先看得出是哪台机器量的 ──────
printf '%s C10–C12：尺必须自报 + 改账本的规矩\n' "$GATE_LOG_PREFIX" >&2
gate "$CB" "$CI_XML"
expect_has "C10 输出点名这次用的是哪把尺" "ruler"
expect_has "C10 尺上带 lint 版本（从报告根元素读，不是猜的）" "version=8.6.0"
expect_has "C10 尺上带操作系统" "os="

# ── C11 --rewrite 保住 advisory，且只动进预算的数字 ─────────────────────────
cp "$CB" "$WORK/budget-copy.txt"
gate "$WORK/budget-copy.txt" "$WORK/adv-drift.xml" --rewrite
expect_rc "C11 --rewrite 在合规时退出 0" "0"
if grep -q '^advisory GradleDependency ' "$WORK/budget-copy.txt"; then
  pass "C11 --rewrite 没有把 advisory 段抹掉"
else
  bad "C11 --rewrite 抹掉了 advisory 段" "重写后的账本："
  sed 's/^/       /' "$WORK/budget-copy.txt" >&2
fi
if grep -qE '^GradleDependency [0-9]' "$WORK/budget-copy.txt"; then
  bad "C11 --rewrite 把 advisory 规则又写回预算里了"
else
  pass "C11 advisory 规则不会回写成预算"
fi
gate "$WORK/budget-copy.txt" "$WORK/adv-drift.xml"
expect_rc "C11 重写之后立刻再判一遍是绿的（自洽）" "0"

# ── C12 --rewrite 不许借道抬预算 ───────────────────────────────────────────
cp "$CB" "$WORK/budget-copy2.txt"
before="$(sha256_of "$WORK/budget-copy2.txt")"
gate "$WORK/budget-copy2.txt" "$WORK/over.xml" --rewrite
expect_rc "C12 有新增债时拒绝重写" "1"
after="$(sha256_of "$WORK/budget-copy2.txt")"
if [ "$before" = "$after" ]; then pass "C12 拒绝重写时账本逐字节未动"
else bad "C12 拒绝重写却改了账本" "before=$before" "after=$after"; fi

# ── C13 报告读不到 = 没验证，不是通过 ──────────────────────────────────────
printf '%s C13：读不到就是没验证\n' "$GATE_LOG_PREFIX" >&2
gate "$CB" "$WORK/does-not-exist.xml"
expect_rc "C13 报告缺失 → CANNOT-VERIFY" "2"
printf '<?xml version="1.0" encoding="UTF-8"?>\n<issues format="6" by="lint 8.6.0">\n</issues>\n' >"$WORK/zero.xml"
gate "$CB" "$WORK/zero.xml"
expect_rc "C13 报告 0 条 issue → CANNOT-VERIFY（宁可信其有错）" "2"

# ── 汇总 ────────────────────────────────────────────────────────────────────
printf '\n' >&2
if [ "$BAD" -gt 0 ]; then
  printf '%s  FAIL %d/%d 格判错：%s\n' "$GATE_LOG_PREFIX" "$BAD" "$CASES" "$FAILED_NAMES" >&2
  exit 1
fi
printf '%s  OK   lint 预算判据 %d 格全对（产物 %s）\n' "$GATE_LOG_PREFIX" "$CASES" "$WORK" >&2
