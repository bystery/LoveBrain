#!/usr/bin/env bash
# 大文件棘轮（ 第7节 第二步完成定义第 5 条：新代码无 >500 行文件、现存 >800 行持续下降；
# 也是 第3节第2条 里唯一从没被任何机器判过的那一半——之前只有人在文档里抄数，没有闸）。
#
# 判什么：MAIN_ROOT 下每个 .kt 的行数，对两份登记清单（清单里的路径**相对 MAIN_ROOT**，
# 这样判据自测可以拿任意小夹具跑，真仓库与 CI 跑的是同一把尺）。
#  - 新增一个跨线文件  -> 红（点名是谁、多少行、撞的是哪条线）
#  - 登记过的搬走了    -> 也红（"还了债不改账本"会让清单腐烂，最后没人知道还剩多少）
#  - 已登记的变长/变短 -> 只打印，不红：这一格要的是"别新增巨石"，不是给重构定 KPI；
#    真要按行数奖惩会把人推向"机械切文件"（复核 第9节 第 8 条明确禁止）。
set -uo pipefail

BUDGET_DIR="${BUDGET_DIR:-scripts}"
MAIN_ROOT="${MAIN_ROOT:-app/src/main}"
LINE_500="$BUDGET_DIR/.txt"
LINE_800="$BUDGET_DIR/.txt"

for f in "$LINE_500" "$LINE_800"; do
  [ -f "$f" ] || { echo "[gate] CANNOT-VERIFY 缺登记文件 $f —— 没有清单就等于没有闸"; exit 2; }
done
if [ ! -d "$MAIN_ROOT" ]; then
  echo "[gate] CANNOT-VERIFY 找不到目录 $MAIN_ROOT —— 扫描根接错时会'零违例通过'，那是最坏的一种绿"; exit 2
fi

# 尺自报：谁在量、在哪台机器、量的是哪个 SHA（与 lint 预算那把尺同一口径）
echo "[gate] ruler: tool=wc -l root=$MAIN_ROOT sha=$(git rev-parse --short HEAD 2>/dev/null || echo NO-GIT) os=$(uname -s)"

measured_file="$(mktemp)"
registered_file="$(mktemp)"
cleanup() { rm -f "$measured_file" "$registered_file"; }
trap cleanup EXIT

# 清单里的键 = 去掉 `MAIN_ROOT/` 与 `java/com/lovebrain/app/` 两层前缀之后的路径
# （与 PackageDependencyTest 那份 `viewmodel/…` 的登记风格一致，也让小夹具能原样复用同一判据）
normalize() { local p="${1#"$MAIN_ROOT"/}"; printf '%s\n' "${p#java/com/lovebrain/app/}"; }

scanned=0
: > "$measured_file"
while IFS= read -r -d '' f; do
  scanned=$((scanned + 1))
  n=$(wc -l < "$f" | tr -d ' ')
  [ "$n" -gt 500 ] && printf '%s %s\n' "$n" "$(normalize "$f")" >> "$measured_file"
done < <(find "$MAIN_ROOT" -name '*.kt' -print0)

# 扫描器自己也要能被证伪：整个根接错时不许给"全绿"。阈值可由 MIN_SCANNED 覆盖，
# 因为判据自测要拿小夹具跑（那种"清单与实到对账"的恒绿由上面两条 + 幽灵条目那一支负责）。
min_scanned="${MIN_SCANNED:-50}"
if [ "$scanned" -lt "$min_scanned" ]; then
  echo "[gate] CANNOT-VERIFY 只扫到 $scanned 个 .kt（门槛 $min_scanned）—— 根接错了"; exit 2
fi

sort -k2 "$measured_file" -o "$measured_file"
sed 's/[[:space:]]*#.*$//' "$LINE_500" "$LINE_800" | grep -v '^[[:space:]]*$' | sed 's/^ *//' | sort -u > "$registered_file"

rc=0
while read -r lines path; do
  [ -n "${path:-}" ] || continue
  if ! grep -qxF "$path" "$registered_file"; then
    tier=$([ "${lines:-0}" -gt 800 ] && echo '>800' || echo '>500')
    ledger=$([ "${lines:-0}" -gt 800 ] && echo "$LINE_800" || echo "$LINE_500")
    echo "[gate] FAIL 新的大文件：$path（$lines 行）撞了 $tier，不在 $ledger 里"
    echo "[gate]      要么先拆它，要么在 $ledger 里登记并写明「为什么这次不得不过界」"
    rc=1
  fi
done < "$measured_file"

while read -r path; do
  [ -n "$path" ] || continue
  if ! awk '{print $2}' "$measured_file" | grep -qxF "$path"; then
    echo "[gate] FAIL 登记的 $path 现在其实没跨线了 —— 把清单改短（搬家成功的证据要落进账本，别留幽灵条目）"
    rc=1
  fi
done < "$registered_file"

over800=$(awk '$1+0 > 800' "$measured_file" | wc -l | tr -d ' ')
over500=$(wc -l < "$measured_file" | tr -d ' ')
reg500=$(sed 's/[[:space:]]*#.*$//' "$LINE_500" | grep -v '^[[:space:]]*$' | wc -l | tr -d ' ')
reg800=$(sed 's/[[:space:]]*#.*$//' "$LINE_800" | grep -v '^[[:space:]]*$' | wc -l | tr -d ' ')
echo "[gate] measured: 扫了 $scanned 个 .kt，>500 行 $over500 个 / >800 行 $over800 个（登记：500–800 档 $reg500 个 + >800 档 $reg800 个，两边都已剥注释）"
if [ "$rc" = 0 ]; then
  echo "[gate]  OK   big-file ratchet holds: 没有新的巨石，登记清单与实到一致"
else
  echo "[gate]  big-file ratchet FAILED（上面逐条点名）"
fi
exit $rc
