#!/usr/bin/env bash
#
# scripts/check_lint_budget.sh
#
# 独立复核 §3.3 最后一条：
#   "80 个 lint warning 不应永久作为绿色背景噪声。保存 baseline 后设
#    '新增 warning = 0'，逐批消债。"
#
# 为什么按**每条规则**记账而不是只记一个总数：修掉 3 条 UnusedResources 的同时
# 新加 3 条 ModifierParameter，总数一点没动，只看总数的预算就被"债务搬家"糊过去了。
#
# 用法：
#   bash scripts/check_lint_budget.sh                        # 门禁
#   bash scripts/check_lint_budget.sh --rewrite              # 只允许把预算改**小**
#   bash scripts/check_lint_budget.sh --rewrite --allow-increase   # 显式加债（要写理由）
#
# 退出码：0 合规；1 有新增债 / 预算过期未更新 / 有 Error 级问题；2 报告缺失或不可解析（=没验证）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

XML="${LINT_XML:-$ROOT/app/build/reports/lint-results-debug.xml}"
BUDGET="${LINT_BUDGET:-$ROOT/scripts/lint-budget.txt}"
REWRITE=0
ALLOW_INCREASE=0
for a in "$@"; do
  case "$a" in
    --rewrite) REWRITE=1 ;;
    --allow-increase) ALLOW_INCREASE=1 ;;
    *) die_usage "unknown argument: $a" ;;
  esac
done

[ -f "$XML" ] || die_unverified "lint report missing: $XML — run ./gradlew :app:lintDebug first"
[ -s "$XML" ] || die_unverified "lint report is empty: $XML"
[ -f "$BUDGET" ] || die "lint budget file missing: $BUDGET"

PYBIN="${PYTHON:-python3}"
have_cmd() { command -v "$1" >/dev/null 2>&1; }

# 只测"命令存在"不够：Windows 上的 python3 是 Microsoft Store 的占位符，
# command -v 过、一跑就 exit 49 且**一行输出都没有**，看着像"检查通过（没报警）"。
# 所以这里要求解释器真的能跑并回声一个标记。
if ! have_cmd "$PYBIN"; then
  die_unverified "python interpreter '$PYBIN' not found (set PYTHON=... to a real one)"
fi
if ! echo_out="$("$PYBIN" -c 'print("alive")' 2>/dev/null)" || [ "$echo_out" != "alive" ]; then
  die_unverified "'$PYBIN' exists but cannot run (exit or output unexpected) — on Windows use PYTHON=python"
fi

# 解析 + 比对都交给 python：bash 里没有可靠的 XML 解析器，
# 而"用 grep 数 <issue"会把根元素 <issues> 也算进去（这份报告实测 71 条，
# 裸 grep 数出 72 —— 一个 off-by-one 就写进了三份文档）。
"$PYBIN" - "$XML" "$BUDGET" "$REWRITE" "$ALLOW_INCREASE" <<'PY'
import collections, re, sys

xml_path, budget_path = sys.argv[1], sys.argv[2]
rewrite = sys.argv[3] == "1"
allow_increase = sys.argv[4] == "1"

text = open(xml_path, encoding="utf-8", errors="replace").read()
issues = re.findall(r"<issue\b[^>]*\bid=\"([^\"]+)\"", text)
severities = re.findall(r"severity=\"([^\"]+)\"", text)
counts = collections.Counter(issues)

if not issues:
    print("[lint-budget] CANNOT-VERIFY 报告里 0 个 <issue>，扫描器或路径坏了")
    sys.exit(2)

hard = [s for s in severities if s in ("Fatal", "Error")]
if hard:
    print("[lint-budget] FAIL 报告里有 %d 条 Error/Fatal：%s" % (len(hard), sorted(set(hard))))
    sys.exit(1)

budget = {}
for line in open(budget_path, encoding="utf-8"):
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    rule, _, n = line.partition(" ")
    budget[rule] = int(n)

over = {r: c for r, c in counts.items() if c > budget.get(r, 0)}
stale = {r: b for r, b in budget.items() if counts.get(r, 0) < b}

print("[lint-budget] 实测 %d 条 issue / %d 条规则；预算文件登记 %d 条规则"
      % (len(issues), len(counts), len(budget)))

if rewrite:
    if over and not allow_increase:
        print("[lint-budget] 拒绝 --rewrite：有 %d 条规则超出预算（新增债）。" % len(over))
        for r in sorted(over):
            print("    %s: %d > %d" % (r, over[r], budget.get(r, 0)))
        print("  先修掉，或明确承认加债时再带 --allow-increase 并写理由。")
        sys.exit(1)
    body = ["# lint 预算基线：每条规则允许的最大 issue 数。",
            "# 生成/更新：bash scripts/check_lint_budget.sh --rewrite",
            "# 语义：任何一条超过预算 = 新增债；任何一条低于预算 = 债还掉了，必须回来把数字改小。",
            "# 用每条规则单独记账而不是只记一个总数：修掉 3 条 UnusedResources 同时新加 3 条",
            "# ModifierParameter 会让总数不变，只看总数就看不见这种\"债务搬家\"。", ""]
    for r in sorted(counts):
        body.append("%s %d" % (r, counts[r]))
    open(budget_path, "w", encoding="utf-8", newline="\n").write("\n".join(body) + "\n")
    print("[lint-budget]  OK   预算已按实测重写（只降不升，除非 --allow-increase）")
    sys.exit(0)

fail = False
if over:
    print("[lint-budget] FAIL 新增 lint 债（超过预算的规则）：")
    for r in sorted(over):
        print("    %s: 实测 %d > 预算 %d" % (r, over[r], budget.get(r, 0)))
    fail = True
if stale:
    print("[lint-budget] FAIL 预算已过期——下面这些规则比登记的更干净，说明债还掉了但没落账：")
    for r in sorted(stale):
        print("    %s: 实测 %d < 预算 %d" % (r, counts.get(r, 0), stale[r]))
    print("  跑 bash scripts/check_lint_budget.sh --rewrite 把数字改小。")
    print("  为什么这一半也要红：基线一旦允许虚高，它就慢慢烂回去，最后没人知道还剩多少。")
    fail = True
if fail:
    sys.exit(1)
print("[lint-budget]  OK   没有新增 lint 债，且预算与实扫一致")
PY
