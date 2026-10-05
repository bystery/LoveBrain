#!/usr/bin/env bash
#
# scripts/record_visual_baseline.sh — 第6节第5条 视觉基线的**人工重录**入口（与 verify 那一条相对）。
#
# 为什么要有这个脚本（它补的是坑表里那条"文档在、工具不在"）：
#   `scripts/verify_visual_baseline.sh:74` 的失败提示一直让人去跑
#   `bash scripts/record_visual_baseline.sh`，而这个文件此前**不存在**（全仓仅此一处引用）。
#    第76节 把这条记为待修：
#   「人工重录这条路目前是文档在、工具不在，要单独修」。
#
# ⚠⚠ 这个脚本的**边界**，写死在这里：
#   1. 它只重录，不判定。record 出来的图一律落在 build/ 里（roborazzi 的
#      `app/build/outputs/roborazzi/`），**不进** `app/src/test/roborazzi/`；
#   2. 它**绝不** `git add` / `git commit` / `git push`，也**绝不**把实到图 cp 进基线目录。
#      基线是"人看过之后单独一笔提交"（ :538；ci.yml:59 同一条），
#      自动放进基线目录 = 把"没人看过"伪装成"已过审"，那正是 第6节第5条 要堵的路；
#   3. 它**先清 build/ 残留**再录，与 verify 那条契约同一理由：上一发 record 留在 build/ 的图
#      会冒充这一轮的东西（本仓库第一次接线就踩在这里，见 verify 脚本头部）；
#   4. 它**不许**改动已提交的基线。录之前先给 `app/src/test/roborazzi/` 拍一份哈希快照，
#      录之后逐张比对：只要基线目录里有文件新增/变化就当场 FAIL 报警并停手，
#      还原还是保留由人决定（脚本不替你 `git checkout --`，也不替你提交）。
#
# 录完人要自己看图：脚本列出每张图的路径、字节、sha256，并标出哪些是**没有已提交基线**的
# 新档（那些才是需要人过眼 + 单独提交的那几张）。
#
# Usage:
#   bash scripts/record_visual_baseline.sh [--gradle-arg ...] [--tests <pattern>]...
#     --gradle-arg 透给 gradle（例如 --offline）
#     --tests     只录某几格（可重复），省略号按 gradle 的 --tests 语义
#   例：bash scripts/record_visual_baseline.sh --gradle-arg --offline
#
# Exit codes: 0 录制跑完（图等人看，未提交任何东西）；1 跑不起来或基线目录被动过；2 用法问题。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

ROOT="$(repo_root)"
GOLDEN_DIR="$ROOT/app/src/test/roborazzi"
STAGE_DIR="$ROOT/app/build/outputs/roborazzi"
declare -a EXTRA=()
declare -a TEST_FILTER=()

while [ $# -gt 0 ]; do
  case "$1" in
    --gradle-arg) EXTRA+=("$2"); shift 2 ;;
    --tests) TEST_FILTER+=("--tests" "$2"); shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1" ;;
  esac
done

[ -d "$GOLDEN_DIR" ] || die_unverified "基线目录不存在：$GOLDEN_DIR —— 没地方对照哪些是新档"

# ── 0) 录之前先给基线目录拍快照（第 4 条边界的证人）────────────────────────
before="$(mktemp)"
find "$GOLDEN_DIR" -maxdepth 1 -type f -name '*.png' | LC_ALL=C sort | while read -r f; do
  printf '%s  %s\n' "$(sha256_of "$f")" "$(basename "$f")"
done > "$before"
log "golden snapshot: $(grep -c . "$before" || true) committed baseline(s) hashed"

# ── 1) 清 build 残留（与 verify 同一条理由）───────────────────────────────
rm -rf "$STAGE_DIR" "$ROOT/app/build/intermediates/roborazzi" "$ROOT/app/build/reports/roborazzi"
mkdir -p "$STAGE_DIR"
log "cleared build residue: $(basename "$STAGE_DIR")/ + intermediates/ + reports/"

# ── 2) record：roborazzi 的实到图只进 build/，不进基线目录 ─────────────────
cd "$ROOT"
set +e
./gradlew :app:recordRoborazziDebug --no-daemon \
  ${EXTRA[@]+"${EXTRA[@]}"} ${TEST_FILTER[@]+"${TEST_FILTER[@]}"}
rc=$?
set -e
[ "$rc" -eq 0 ] || die "record 跑不起来（gradle exit=$rc）——这一轮没有任何图可看，别拿的残留交差"

# ── 3) 基线目录必须一动不动（第 4 条边界）─────────────────────────────────
after="$(mktemp)"
find "$GOLDEN_DIR" -maxdepth 1 -type f -name '*.png' | LC_ALL=C sort | while read -r f; do
  printf '%s  %s\n' "$(sha256_of "$f")" "$(basename "$f")"
done > "$after"
if ! cmp -s "$before" "$after"; then
  log "diff（< 录之前 / > 录之后）："
  diff "$before" "$after" >&2 || true
  die "record 动了已提交的基线目录 $GOLDEN_DIR —— 本脚本不替你还原，也不替你提交：请人工决定（git checkout -- 该文件 / 或把它当成一次正式的基线变更单独提交），并回来查为什么 record 会写到那里"
fi
log "golden dir untouched: $(grep -c . "$after" || true) committed baseline(s) byte-identical"

# ── 4) 把实到图列给人看；新档单独标出来 ───────────────────────────────────
found="$(find "$STAGE_DIR" -maxdepth 1 -type f -name '*.png' | grep -c . || true)"
[ "$found" -gt 0 ] || die "record 说绿了，但 $STAGE_DIR 里一张 PNG 都没有 —— 这条"人工重录"路根本没产出，别把它当录过了"
log "actual images recorded into $STAGE_DIR: $found"
new_count=0
while read -r f; do
  name="$(basename "$f")"
  # roborazzi 的 verify 产物带 _actual 后缀，record 产物不带；对照基线时两种名字都认
  base="${name%_actual.png}.png"
  if [ -f "$GOLDEN_DIR/$base" ]; then
    printf '  [已有基线] %s  %s bytes  sha256 %s…\n' "$name" "$(wc -c <"$f" | tr -d ' ')" "$(sha256_of "$f" | cut -c1-12)"
  else
    new_count=$((new_count + 1))
    printf '  [没有基线] %s  %s bytes  sha256 %s…  ← 需要人过眼后单独提交\n' \
      "$name" "$(wc -c <"$f" | tr -d ' ')" "$(sha256_of "$f" | cut -c1-12)"
  fi
done < <(find "$STAGE_DIR" -maxdepth 1 -type f -name '*.png' | LC_ALL=C sort)

ok "record 完成：$found 张实到图在 $STAGE_DIR（$new_count 张没有已提交基线），基线目录未改动，未提交任何东西"
cat >&2 <<'EOTEXT'
[gate] 下一步是**人的动作**（本脚本不会替你做）：
  1. 打开 app/build/reports/roborazzi/index.html 或直接看上面列出的 PNG，逐张过眼；
  2. 只把看过并认可的那几张 cp 进 app/src/test/roborazzi/，命名保持 <全限定类名>.<方法名>.png；
  3. 那一笔提交单独开，标题写清录制的 locale / 设备档位 / 是哪一次改动带出来的；
  4. 提交前用 bash scripts/verify_visual_baseline.sh 复跑一次（verify 永不 record）。
EOTEXT
rm -f "$before" "$after"
