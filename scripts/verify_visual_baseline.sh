#!/usr/bin/env bash
#
# scripts/verify_visual_baseline.sh — §6.5 视觉基线的**唯一比对入口**（本机与 CI 同一份）。
#
# 为什么要有这个脚本，而不是直接 `./gradlew :app:verifyRoborazziDebug`：
#   实测（CI run 36247435793 的失败原文）roborazzi 在 verify 模式找的"原图"是
#     app/build/outputs/roborazzi/<类名>.<方法名>.png
#   而不是仓库里提交的 app/src/test/roborazzi/*.png：
#     "Roborazzi: The original file(/home/runner/.../build/outputs/roborazzi/….png) was not found."
#   ⇒ 直接在干净检出上跑 verifyRoborazziDebug 必然三格全红（没有原图可比），
#   而在开发机上跑，比的是**上一次 record 留在 build/ 的残留**——那既不是仓库的基线，
#   也不是"没有基线"，是一种会骗人的绿。（本仓库第一次接线时就踩了这条，见坑表 134。）
#
# 所以这里把契约写明：
#   1. 清掉 build/ 里的历史残留（绝不允许上一次 record 冒充基线）；
#   2. 把**仓库里已提交的基线**放到 roborazzi 真正会读的位置；
#   3. 跑 verify（不匹配即红；这一步永不 record）；
#   4. 把实到图与比对报告留在 build/ 里，由 CI 当产物上传给人看。
#
# Usage: bash scripts/verify_visual_baseline.sh [--gradle-arg ...]
# Exit codes: 0 基线匹配；1 基线不匹配（或基线目录为空 = 没有可比的东西）；2 用法/前置问题。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

ROOT="$(repo_root)"
GOLDEN_DIR="$ROOT/app/src/test/roborazzi"
STAGE_DIR="$ROOT/app/build/outputs/roborazzi"
declare -a EXTRA=()

while [ $# -gt 0 ]; do
  case "$1" in
    --gradle-arg) EXTRA+=("$2"); shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1" ;;
  esac
done

# ── 前置：仓库里必须真的交了基线 ═════────────────────═══════════════════════
[ -d "$GOLDEN_DIR" ] || die "基线目录不存在：$GOLDEN_DIR —— 视觉比对没有可比对象，这一步不许算通过"
N="$(find "$GOLDEN_DIR" -maxdepth 1 -type f -name '*.png' | grep -c . || true)"
[ "$N" -gt 0 ] || die "$GOLDEN_DIR 里一张 PNG 都没有 —— 不许把'没有基线'跑成绿"
for f in "$GOLDEN_DIR"/*.png; do
  sig="$(head -c 8 "$f" | od -An -tx1 | tr -d ' \n')"
  [ "$sig" = "89504e470d0a1a0a" ] ||
    die "基线不是真 PNG（签名 $sig）：$f —— 拿字节数凑出来的基线比对不出任何东西"
done
log "goldens in git: $N file(s), all with a PNG signature"

# ── 1) 清残留（这条是本脚本存在的理由之一）════════════════════════════════
rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR"
# ── 2) 把仓库基线放到 roborazzi 真正读的位置 ───────────────────────────────
cp "$GOLDEN_DIR"/*.png "$STAGE_DIR/"
for f in "$STAGE_DIR"/*.png; do
  log "staged golden $(basename "$f") ($(wc -c <"$f" | tr -d ' ') bytes, sha256 $(sha256_of "$f" | cut -c1-12)…)"
done

# ── 3) 只 verify，永不 record ──────────────────────────────────────────────
# 清掉上一轮的比对产物，免得旧图被当成这一轮的结果看
rm -rf "$ROOT/app/build/intermediates/roborazzi" "$ROOT/app/build/reports/roborazzi"
cd "$ROOT"
set +e
./gradlew :app:verifyRoborazziDebug --no-daemon ${EXTRA[@]+"${EXTRA[@]}"}
rc=$?
set -e

# ── 4) 把实到图/报告位置打出来，供产物上传与人看 ──────────────────────────
found="$(find "$STAGE_DIR" -name '*_actual.png' 2>/dev/null | grep -c . || true)"
log "verify exit=$rc; actual images in $STAGE_DIR: $found; report: app/build/reports/roborazzi/index.html"
if [ "$rc" -ne 0 ]; then
  die "视觉基线不匹配（或跑不起来）——看 $STAGE_DIR/*_actual.png 与 app/build/reports/roborazzi/index.html；重录是人工动作：bash scripts/record_visual_baseline.sh 之后人看过再单独提交"
fi
ok "screenshot baselines match: $N golden(s) verified against this run's actual images"
