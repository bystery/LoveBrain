#!/usr/bin/env bash
#
# scripts/test_check_lint_budget.sh
#
# check_lint_budget.sh 判据本身的正反测试。
#
# 触发它的是 CI run 36019334520：verify 红在 "Lint budget must not grow"——CI 量到
# 81 条 / 16 规则，本机 71 条 / 15 规则，账本登记 15 规则。差的 10 条全部来自两条
# "发现不属于这个仓库"的检查（GradleDependency 量的是这台机器解析到的 Maven 版本清单，
# OldTargetApi 量的是本机 SDK 装了多新的平台）。分类之后必须仍然有牙，所以这里
# 一半的格子在测"降级之后还咬不咬得动真债"。
#
# ⚠ 一条硬规矩：合成报告的条数一律**从账本现读**，不许写死在测试里。
#   上一版把 UnusedResources 写成 34，结果本窗口合法还掉一条债（34→33），
#   5 个格子立刻假红——而假红的门禁教出来的习惯就是"把数字抬回去"。
#   同理，两份真产物夹具不再断言"退出码必须 0"，只断言"不得报出新增债"
#   与"两台机器进预算的部分条数相同"：这两条不随预算变小而失效。
#
# Usage:
#   bash scripts/test_check_lint_budget.sh
#   PYTHON=python bash scripts/test_check_lint_budget.sh      # Windows
#
# 退出码：0 全部判对；1 有格子判错；2 前置解释器缺失（=没验证）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

SCRIPT="$SCRIPT_DIR/check_lint_budget.sh"
FIX="$SCRIPT_DIR/fixtures/lint"
CB="$ROOT/scripts/lint-budget.txt"

PYBIN="${PYTHON:-python3}"
if ! command -v "$PYBIN" >/dev/null 2>&1 || [ "$("$PYBIN" -c 'print("alive")' 2>/dev/null)" != "alive" ]; then
  die_unverified "python interpreter unusable ('PYTHON=$PYBIN') — on Windows use PYTHON=python"
fi
export PYTHON="$PYBIN"
# 断言只匹配 ASCII 片段：Windows 控制台是 GBK，被捕获的中文输出会变成另一种字节。
export PYTHONIOENCODING=utf-8

if [ -n "${WORK_DIR:-}" ]; then
  WORK="$WORK_DIR"; mkdir -p "$WORK"
else
  WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
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

dump() {
  printf '       ---- 本次输出 ----\n' >&2
  sed 's/^/       /' "$WORK/out.txt" >&2
  printf '       ----------------\n' >&2
}

# ── 读账本：合成报告的条数全部来自这里 ──────────────────────────────────────
bget() {
  awk -v k="$1" '$1==k{print $2; f=1} END{if(!f) print 0}' "$CB"
}

# budget_pairs —— 账本里进预算的 "<规则> <条数>"（跳过注释与 advisory 段）
budget_pairs() {
  grep -vE '^[[:space:]]*#|^[[:space:]]*$|^advisory[[:space:]]' "$CB" || true
}

# mk_report <out> [规则=条数 ...]
# 默认每条规则正好压在账本登记的数上；参数里点名的规则改成给定条数
# （账本里没有的规则被点名时是**追加**一条新规则，正合"冒出来没登记过的规则"那一格）。
mk_report() {
  local out="$1"; shift
  local overrides="$*"
  {
    printf '<?xml version="1.0" encoding="UTF-8"?>\n<issues format="6" by="lint 8.6.0">\n'
    local seen=""
    while read -r rule n; do
      [ -n "$rule" ] || continue
      local cnt="$n" o
      for o in $overrides; do
        case "$o" in "$rule="*) cnt="${o#*=}" ;; esac
      done
      seen="$seen $rule"
      local i=1
      while [ "$i" -le "$cnt" ]; do
        printf '    <issue id="%s" severity="Warning" message="m" category="Correctness" priority="6"/>\n' "$rule"
        i=$((i + 1))
      done
    done < <(budget_pairs)
    local o
    for o in $overrides; do
      rule="${o%%=*}"; cnt="${o#*=}"
      case " $seen " in *" $rule "*) continue ;; esac   # 已在账本里，上面处理过了
      local j=1
      while [ "$j" -le "$cnt" ]; do
        printf '    <issue id="%s" severity="Warning" message="m" category="Correctness" priority="6"/>\n' "$rule"
        j=$((j + 1))
      done
    done
    printf '</issues>\n'
  } >"$out"
}

# mk_budget <out> <整行> ... —— 逐行原样写，用来构造"缺理由""重复登记"这类坏账本
mk_budget() {
  local out="$1"; shift
  : >"$out"
  local l
  for l in "$@"; do printf '%s\n' "$l" >>"$out"; done
}

# gate <budget> <xml> [script args...]
gate() {
  local budget="$1" xml="$2"; shift 2
  set +e
  LINT_BUDGET="$budget" LINT_XML="$xml" bash "$SCRIPT" "$@" >"$WORK/out.txt" 2>&1
  echo "$?" >"$WORK/rc.txt"
  set -e
}

rc() { cat "$WORK/rc.txt"; }

expect_rc() {
  if [ "$(rc)" = "$2" ]; then pass "$1（退出码 $2）"; else bad "$1" "退出码 $(rc)，应为 $2"; dump; fi
}

expect_has() {
  if grep -qF -- "$2" "$WORK/out.txt"; then pass "$1"; else bad "$1" "输出里找不到片段：$2"; dump; fi
}

stats_of() {   # 从 STATS 行取某个键
  grep -m1 '^\[lint-budget\] STATS ' "$WORK/out.txt" |
    sed -n "s/.* ${1}=\([0-9]*\).*/\1/p" || true
}

# ── 夹具本身要能读 ──────────────────────────────────────────────────────────
require_file "$CB" "lint budget"
require_file "$FIX/ci-run-36019334520-lint-results-debug.xml" "fixture (CI report)"
require_file "$FIX/local-at-3d92488-lint-results-debug.xml" "fixture (local report)"

CI_XML="$FIX/ci-run-36019334520-lint-results-debug.xml"
LOCAL_XML="$FIX/local-at-3d92488-lint-results-debug.xml"

# ── C1–C2 两份真产物：两侧同尺，且不许报出新增债 ────────────────────────────
printf '%s C1–C2：两份真报告 vs 同一份账本（两侧同尺）\n' "$GATE_LOG_PREFIX" >&2

# 真产物是某个 SHA 上的快照，账本只会越还越小，所以这里**不**断言"退出码 0"也
# **不**断言"不许出现 OVER"（夹具那 34 条 UnusedResources 对着今天 33 的账本就绪判超，
# 那是历史，不是新债）。要钉的是这条事故本身：两台机器进预算的部分必须逐条相等，
# 而 CI 独有的规则必须全部落在 advisory 里。这两条都不随还债失效。
gate "$CB" "$CI_XML"
expect_has "C1 CI 那份报告的外部状态族被点名打印（不是藏起来）" "ADVISORY GradleDependency=10"
expect_has "C1 OldTargetApi 同样打印条数" "ADVISORY OldTargetApi=1"
CI_STATS="$(cat "$WORK/out.txt")"
gate "$CB" "$LOCAL_XML"
LOCAL_STATS="$(cat "$WORK/out.txt")"

if "$PYBIN" - "$CI_XML" "$LOCAL_XML" "$CB" <<'PY'
import collections, re, sys

def counts(p):
    t = open(p, encoding="utf-8", errors="replace").read()
    return collections.Counter(re.findall(r"<issue\b[^>]*\bid=\"([^\"]+)\"", t))

ci, local, budget_path = counts(sys.argv[1]), counts(sys.argv[2]), sys.argv[3]
advisory = set()
for line in open(budget_path, encoding="utf-8"):
    line = line.strip()
    if line.startswith("advisory "):
        advisory.add(line.split(None, 2)[1])

ci_gated = {r: n for r, n in ci.items() if r not in advisory}
lo_gated = {r: n for r, n in local.items() if r not in advisory}
bad = []
if ci_gated != lo_gated:
    only_ci = {r: (ci_gated.get(r), lo_gated.get(r)) for r in set(ci_gated) | set(lo_gated)
               if ci_gated.get(r) != lo_gated.get(r)}
    bad.append("进预算的部分两台机器对不上（规则, CI, 本机）：%s" % only_ci)
for r in sorted((set(ci) - set(local)) - advisory):
    bad.append("CI 独有而本机没有的规则 %s 没登记成 advisory —— 它要么是新债，要么是漏判" % r)
for m in bad:
    print("  " + m)
sys.exit(1 if bad else 0)
PY
then
  pass "C2 两份真产物进预算的部分逐条相等，且 CI 独有规则全在 advisory 里"
else
  bad "C2 两侧同尺没成立（上面列出差在哪条规则）"
fi

# ── C3–C6 分类之后还得有牙 ──────────────────────────────────────────────────
printf '%s C3–C6：降级之后判据还有没有牙\n' "$GATE_LOG_PREFIX" >&2

UNUSED="$(bget UnusedResources)"
mk_report "$WORK/at-budget.xml"
gate "$CB" "$WORK/at-budget.xml"
expect_rc "C3a 每条正好压在账本上时应当合规" "0"

mk_report "$WORK/over.xml" "UnusedResources=$((UNUSED + 1))"
gate "$CB" "$WORK/over.xml"
expect_rc "C3b 真债多一条必须红（UnusedResources $((UNUSED + 1)) > $UNUSED）" "1"
expect_has "C3c 红的时候点名是哪条规则" "OVER UnusedResources"

mk_budget "$WORK/tiny.txt" "UnusedResources $((UNUSED + 7))" "ModifierParameter $(( $(bget ModifierParameter) + 7 ))"
gate "$WORK/tiny.txt" "$WORK/at-budget.xml"
expect_has "C4 实测比登记干净时仍然红（STALE）" "STALE UnusedResources"

mk_report "$WORK/newrule.xml" "SomethingBrandNew=2"
gate "$CB" "$WORK/newrule.xml"
expect_rc "C5 冒出来一条没登记过的规则仍然红" "1"
expect_has "C5 说得出是没登记而不是超预算" "OVER SomethingBrandNew"

mk_report "$WORK/adv-drift.xml" "GradleDependency=99"
gate "$CB" "$WORK/adv-drift.xml"
expect_rc "C6 GradleDependency 涨到 99 不该判红（它量的不是这个仓库）" "0"
expect_has "C6 但 99 要照样打印" "ADVISORY GradleDependency=99"

# ── C7–C9 advisory 的边界 ───────────────────────────────────────────────────
printf '%s C7–C9：advisory 的边界\n' "$GATE_LOG_PREFIX" >&2

mk_budget "$WORK/noreason.txt" "UnusedResources $UNUSED" "advisory GradleDependency"
gate "$WORK/noreason.txt" "$WORK/adv-drift.xml"
expect_rc "C7 没写理由的 advisory 不算登记（=没验证）" "2"
expect_has "C7 说清为什么" "CANNOT-VERIFY"

mk_budget "$WORK/demote.txt" "ModifierParameter $(bget ModifierParameter)" \
  "advisory UnusedResources 我看它不顺眼"
gate "$WORK/demote.txt" "$WORK/adv-drift.xml"
expect_rc "C8 把仓库自己的债降级成 advisory 必须被拒" "1"
expect_has "C8 拒的时候要给出路" "ADVISORY-FORBIDDEN UnusedResources"

mk_budget "$WORK/dup.txt" "UnusedResources $UNUSED" "GradleDependency 1" \
  "advisory GradleDependency 上游一发新版条数就变"
gate "$WORK/dup.txt" "$WORK/adv-drift.xml"
expect_rc "C9 同一条既进预算又进 advisory = 读不出该信哪条" "2"

# ── C10 尺要自报家门 ────────────────────────────────────────────────────────
printf '%s C10–C12：尺必须自报 + 改账本的规矩\n' "$GATE_LOG_PREFIX" >&2
gate "$CB" "$CI_XML"
expect_has "C10 输出点名这次用的是哪把尺" "ruler"
expect_has "C10 尺上带 lint 版本（从报告根元素读）" "version=8.6.0"
expect_has "C10 尺上带操作系统" "os="

# ── C11 --rewrite 保住 advisory 且不把 advisory 回写成预算 ──────────────────
cp "$CB" "$WORK/budget-copy.txt"
gate "$WORK/budget-copy.txt" "$WORK/at-budget.xml" --rewrite
expect_rc "C11 合规时 --rewrite 退出 0" "0"
if grep -q '^advisory GradleDependency ' "$WORK/budget-copy.txt"; then
  pass "C11 --rewrite 没有抹掉 advisory 段"
else
  bad "C11 --rewrite 抹掉了 advisory 段" "重写后的账本：" "$(sed -n '1,20p' "$WORK/budget-copy.txt")"
fi
if grep -qE '^GradleDependency [0-9]' "$WORK/budget-copy.txt"; then
  bad "C11 --rewrite 又把 advisory 规则写回预算里了"
else
  pass "C11 advisory 规则不会被回写成预算"
fi
gate "$WORK/budget-copy.txt" "$WORK/at-budget.xml"
expect_rc "C11 重写之后立刻再判一遍是自洽的" "0"

# ── C12 --rewrite 不许借道抬预算 ────────────────────────────────────────────
cp "$CB" "$WORK/budget-copy2.txt"
before="$(sha256_of "$WORK/budget-copy2.txt")"
gate "$WORK/budget-copy2.txt" "$WORK/over.xml" --rewrite
expect_rc "C12 有新增债时拒绝重写" "1"
after="$(sha256_of "$WORK/budget-copy2.txt")"
if [ "$before" = "$after" ]; then pass "C12 拒绝重写时账本逐字节未动"
else bad "C12 拒绝重写却改了账本" "before=$before" "after=$after"; fi

# ── C13 读不到 = 没验证 ─────────────────────────────────────────────────────
printf '%s C13：读不到就是没验证\n' "$GATE_LOG_PREFIX" >&2
gate "$CB" "$WORK/does-not-exist.xml"
expect_rc "C13 报告缺失 → CANNOT-VERIFY" "2"
printf '<?xml version="1.0" encoding="UTF-8"?>\n<issues format="6" by="lint 8.6.0">\n</issues>\n' \
  >"$WORK/zero.xml"
gate "$CB" "$WORK/zero.xml"
expect_rc "C13 报告 0 条 issue → CANNOT-VERIFY（宁可信其有错）" "2"

# ── 汇总 ────────────────────────────────────────────────────────────────────
printf '\n' >&2
if [ "$BAD" -gt 0 ]; then
  printf '%s  FAIL %d/%d 格判错：%s\n' "$GATE_LOG_PREFIX" "$BAD" "$CASES" "$FAILED_NAMES" >&2
  exit 1
fi
printf '%s  OK   lint 预算判据 %d 格全对（产物 %s）\n' "$GATE_LOG_PREFIX" "$CASES" "$WORK" >&2
