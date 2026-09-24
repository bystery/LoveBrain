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
# 为什么要区分「进预算」与「advisory」两类（CI run 36019334520 踩出来的）：
#   同一份代码、同一个 lint 8.6.0，CI 量到 81 条 / 16 规则，本机量到 71 条 / 15 规则，
#   verify 就因为这一步红着。差出来的 10 条全部来自两条**发现不属于这个仓库**的规则：
#     GradleDependency —— 报的是"上游又有新版本可升"。同一句 androidx.test.ext:junit:1.1.5
#       声明，CI 说能升到 1.3.0，本机会说能升到 1.2.1：它量的是这台机器缓存到的 Maven
#       版本清单，不是代码。
#     OldTargetApi —— 报的是"targetSdk 不是最新"。触发与否取决于本机 SDK 里装了多新的
#       平台；targetSdk=35 是产品决定，不是工程债。
#   这类条数既不随平台稳定、也不随时间稳定。把它们锁进预算——哪怕按平台各锁一份——
#   等于给门禁装了个定时引信：上游哪天发个新版，CI 就在一个谁都没改代码的日子变红，
#   而红到那时候没人分得清该改代码还是该改数字。
#   所以它们不进债务账本，但每次照样打印条数与理由；登记成 advisory 必须写理由，
#   且只有下面 EXTERNAL_RULES 那个白名单里的规则有资格被降级——仓库自己欠的债
#   （UnusedResources 之类）不许用这条路免检，该红还是红。
#
# 为什么输出第一行要点名"这次是在哪把尺上量的"：本窗口第一次出现"本机全绿、CI 红"，
#   而两边的数没写在同一处，只能重新下载产物才发现差的不是代码。ruler 那行把
#   lint 版本、操作系统、机器架构、报告与账本路径都印出来，两边一对就知道是谁的尺。
#
# 用法：
#   bash scripts/check_lint_budget.sh                        # 门禁
#   bash scripts/check_lint_budget.sh --rewrite              # 只允许把预算改**小**
#   bash scripts/check_lint_budget.sh --rewrite --allow-increase   # 显式加债（要写理由）
#   bash scripts/test_check_lint_budget.sh                   # 判据本身的 28 格正反测试
#
# 环境变量：LINT_XML / LINT_BUDGET 指到别的报告或账本（自测与夹具用）；
#   PYTHON 指定解释器（Windows 上 python3 常是商店占位符）。
#
# 退出码：0 合规；1 有新增债 / 预算过期未更新 / 有 Error 级问题 / 有人想给真债开免检；
#   2 报告缺失、账本读不出或 advisory 没写理由（=没验证）。
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
import collections, platform, re, sys

xml_path, budget_path = sys.argv[1], sys.argv[2]
rewrite = sys.argv[3] == "1"
allow_increase = sys.argv[4] == "1"

PREFIX = "[lint-budget]"


def cannot(msg):
    print("%s CANNOT-VERIFY %s" % (PREFIX, msg))
    sys.exit(2)


text = open(xml_path, encoding="utf-8", errors="replace").read()
issues = re.findall(r"<issue\b[^>]*\bid=\"([^\"]+)\"", text)
severities = re.findall(r"severity=\"([^\"]+)\"", text)
counts = collections.Counter(issues)

# ── 先自报这把尺：本机绿不等于 CI 绿，日志里必须一眼看得出是哪台机器量的 ──
m = re.search(r"<issues\b[^>]*\bby=\"([^\"]*)\"", text)
by = m.group(1).strip() if m else ""
tool, _, ver = by.partition(" ")
print("%s ruler: tool=%s version=%s os=%s/%s machine=%s python=%s"
      % (PREFIX, tool or "?", ver or "?",
         platform.system(), platform.release(), platform.machine(),
         platform.python_version()))
print("%s ruler: report=%s budget=%s" % (PREFIX, xml_path, budget_path))

if not issues:
    cannot("报告里 0 个 <issue>，扫描器或路径坏了")

hard = [s for s in severities if s in ("Fatal", "Error")]
if hard:
    print("%s FAIL 报告里有 %d 条 Error/Fatal：%s" % (PREFIX, len(hard), sorted(set(hard))))
    sys.exit(1)

# ── 有资格被降级成 advisory 的规则：发现来自仓库之外的世界（上游版本清单 / 装了哪些 SDK）
# 这份名单**故意写死在脚本里**而不是写在账本里：想加一条就得改脚本，
# 也就是必须在代码评审里露一次脸，而不是往 txt 里塞一行注释就免检了。
# 实测出现过的是前两条；其余三条是同族的"上游/外部状态"检查，先给它们留位置。
EXTERNAL_RULES = {
    "GradleDependency":            "依赖有没有更新版：取决于这台机器解析到的 Maven 清单",
    "NewerVersionAvailable":       "同上，离线清单变体",
    "OldTargetApi":                "targetSdk 新不新：取决于本机 SDK 里装了多新的平台",
    "ExpiredTargetSdkVersion":     "Google Play 的 targetSdk 期限表，随日期变",
    "GradlePluginVersion":         "AGP 有没有新版：同样是上游清单",
}

ADVISORY_HEADER = [
    "",
    "# ── advisory：不进预算比对的规则（每次仍然打印条数与理由）──",
    "# 只允许「发现来自仓库之外」的检查走这里；名单写死在 check_lint_budget.sh 的",
    "# EXTERNAL_RULES 里，仓库自己欠的债想用这条路免检会被当场拒绝。",
    "# 格式：advisory <规则> <理由>（理由不能空）",
]

budget = {}
advisory = {}
for lineno, raw in enumerate(open(budget_path, encoding="utf-8"), 1):
    line = raw.strip()
    if not line or line.startswith("#"):
        continue
    parts = line.split(None, 2)
    if parts[0] == "advisory":
        if len(parts) < 3 or not parts[2].strip():
            cannot("预算文件第 %d 行：advisory 必须写成 'advisory <规则> <理由>'。"
                   "没有理由的 advisory 就是没有评审的免检通道。" % lineno)
        rule, reason = parts[1], parts[2].strip()
        if rule not in EXTERNAL_RULES:
            print("%s FAIL ADVISORY-FORBIDDEN %s：这条的发现来自这个仓库自己，"
                  "不许降级成 advisory。" % (PREFIX, rule))
            print("  要么修掉它，要么明确承认加债：bash scripts/check_lint_budget.sh --rewrite --allow-increase 并写理由。")
            print("  有资格被降级的只有外部状态族：%s" % ", ".join(sorted(EXTERNAL_RULES)))
            sys.exit(1)
        if rule in budget:
            cannot("预算文件第 %d 行：%s 既登记成预算规则又登记成 advisory，读不出该信哪条" % (lineno, rule))
        advisory[rule] = reason
        continue
    if len(parts) != 2 or not re.fullmatch(r"\d+", parts[1]):
        cannot("预算文件第 %d 行格式不对（应为 '<规则> <条数>'）：%s" % (lineno, line))
    if parts[0] in advisory:
        cannot("预算文件：%s 先出现在 advisory 之后又被登记成预算规则，读不出该信哪条" % parts[0])
    budget[parts[0]] = int(parts[1])

gated = {r: c for r, c in counts.items() if r not in advisory}
adv_seen = {r: c for r, c in counts.items() if r in advisory}
over = {r: c for r, c in gated.items() if c > budget.get(r, 0)}
stale = {r: b for r, b in budget.items() if gated.get(r, 0) < b}

print("%s STATS measured_issues=%d measured_rules=%d gated_issues=%d gated_rules=%d "
      "advisory_issues=%d advisory_rules=%d budget_rules=%d"
      % (PREFIX, len(issues), len(counts), sum(gated.values()), len(gated),
         sum(adv_seen.values()), len(adv_seen), len(budget)))
print("%s 实测 %d 条 / %d 规则；进预算 %d 条 / %d 规则；advisory %d 条 / %d 规则；账本登记 %d 规则"
      % (PREFIX, len(issues), len(counts), sum(gated.values()), len(gated),
         sum(adv_seen.values()), len(adv_seen), len(budget)))
for r in sorted(advisory):
    n = adv_seen.get(r, 0)
    note = "" if n else "（这台尺没报，登记理由留着等它哪天报）"
    print("%s ADVISORY %s=%d 不进预算%s：%s" % (PREFIX, r, n, note, advisory[r]))

if rewrite:
    if over and not allow_increase:
        print("%s 拒绝 --rewrite：有 %d 条规则超出预算（新增债）。" % (PREFIX, len(over)))
        for r in sorted(over):
            print("    OVER %s: 实测 %d > 预算 %d" % (r, over[r], budget.get(r, 0)))
        print("  先修掉，或明确承认加债时再带 --allow-increase 并写理由。")
        sys.exit(1)
    orig = open(budget_path, encoding="utf-8").read().splitlines()
    head = []
    for raw in orig:
        if raw.strip().startswith("#") or not raw.strip():
            head.append(raw.rstrip())
            continue
        break
    while head and not head[-1].strip():
        head.pop()
    adv_lines = sorted(
        l for l in (raw.strip() for raw in orig) if l.startswith("advisory ")
    )
    body = head
    body.append("")
    for r in sorted(gated):
        body.append("%s %d" % (r, gated[r]))
    if adv_lines:
        body.extend(ADVISORY_HEADER)
        body.extend(adv_lines)
    open(budget_path, "w", encoding="utf-8", newline="\n").write("\n".join(body) + "\n")
    print("%s  OK   预算已按实测重写（只降不升，除非 --allow-increase；advisory 段原样保留）" % PREFIX)
    sys.exit(0)

fail = False
if over:
    print("%s FAIL 新增 lint 债（超过预算的规则）：" % PREFIX)
    for r in sorted(over):
        if r in budget:
            print("    OVER %s: 实测 %d > 预算 %d" % (r, over[r], budget[r]))
        else:
            print("    OVER %s: 实测 %d 条，账本里根本没登记过这条规则" % (r, over[r]))
            print("         先问一句：它的发现属于这个仓库吗？属于就修代码；")
            print("         属于外部状态族（上游版本清单 / 本机 SDK）才谈 advisory，且要写理由。")
    fail = True
if stale:
    print("%s FAIL 预算已过期——下面这些规则比登记的更干净，说明债还掉了但没落账：" % PREFIX)
    for r in sorted(stale):
        print("    STALE %s: 实测 %d < 预算 %d" % (r, gated.get(r, 0), stale[r]))
    print("  跑 bash scripts/check_lint_budget.sh --rewrite 把数字改小。")
    print("  为什么这一半也要红：基线一旦允许虚高，它就慢慢烂回去，最后没人知道还剩多少。")
    fail = True
if fail:
    print("  对一眼另一台机器上那行 'ruler:' 再决定动谁：条数不同不一定是代码不同。")
    sys.exit(1)
print("%s  OK   没有新增 lint 债，且预算与实扫一致（进预算的 %d 条规则；advisory 的条数见上）"
      % (PREFIX, len(budget)))
PY
