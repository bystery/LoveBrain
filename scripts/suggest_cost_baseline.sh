#!/usr/bin/env bash
#
# scripts/suggest_cost_baseline.sh
#
# 今日锦囊「真实花费」基准的入口脚本（复核报告 2026-09-23 §5.2 / §8 第 1 步第 8 项）。
#
# 为什么需要它：`app/src/test/**/SuggestCostBaselineTest.kt` 自己就写着"不发起真实网络
# 请求"，usage/cost 全是手填数据，证明不了任何一次真实请求花多少钱。复核报告 §9 的判定
# 标准是：任何用"后续完善""理论上""注释已说明""测试文件已新增"替代，都不算完成。
# 所以这里给一条真的会发请求、真的按 Provider 返回的 usage 计价、真的产出
# machine-readable 原始数据的命令；单测退回到它本来能证明的位置（见该文件的 KDoc）。
#
# 它做什么：
#   1. 校验冻结夹具（benchmarks/suggest-baseline/FIXTURE.lock）没漂——漂了直接失败；
#   2. 用生产链路同一套拼装规则（suggest.md + 夹具知识段 + 0.7 temperature +
#      stream/include_usage + thinking disabled）构造**同一条**请求；
#   3. 连续打 N 次用户配置的 Provider；
#   4. 每次请求写一行 JSON：prompt/completion/cached tokens、按 UsagePricer 口径算出的
#      费用、墙钟耗时、有效建议数、重复率、空泛率；
#   5. 汇总 summary.json + csv，并把 commit / prompt hash 记进 run 清单。
#
# 没有 Key 或没有网络时它**不会**假装通过：退出码 2，并打印该做什么。
#
# Usage:
#   LOVEBRAIN_BASELINE_API_KEY=sk-... bash scripts/suggest_cost_baseline.sh --requests 5
#   bash scripts/suggest_cost_baseline.sh --dry-run          # 只构造请求，不发流量
#   bash scripts/suggest_cost_baseline.sh --self-test        # 离线自检（夹具/计价/指标）
#   bash scripts/suggest_cost_baseline.sh --tag old --model <旧模型> --out-dir dist/baseline-old
#
# Exit codes: 0 measured cleanly, 1 a request failed / no billable usage, 2 not verifiable here.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

RUNNER="$SCRIPT_DIR/suggest_cost_baseline.py"
API_KEY_ENV="LOVEBRAIN_BASELINE_API_KEY"
REQUESTS=5
OUT_DIR="dist/suggest-baseline"
KB_DIR=""
FIXTURE_LOCK=""
EXTRA=()
SELF_TEST=0
DRY_RUN=0
WRITE_LOCK=0

while [ $# -gt 0 ]; do
  case "$1" in
    --requests) REQUESTS="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --kb-dir) KB_DIR="$2"; shift 2 ;;
    --fixture-lock) FIXTURE_LOCK="$2"; shift 2 ;;
    --api-key-env) API_KEY_ENV="$2"; shift 2 ;;
    --self-test) SELF_TEST=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    --write-lock) WRITE_LOCK=1; shift ;;
    --tag | --model | --base-url | --temperature | --thinking | --timeout | --sleep |\
    --price-hit | --price-miss | --price-out)
      EXTRA+=( "$1" "$2" ); shift 2 ;;
    *) die_usage "unknown option: $1 (see the header of $0)" ;;
  esac
done

case "$REQUESTS" in
  '' | *[!0-9]*) die_usage "--requests must be a positive integer (got: $REQUESTS)" ;;
esac
[ "$REQUESTS" -ge 1 ] || die_usage "--requests must be >= 1"

[ -f "$RUNNER" ] || die "runner missing: $RUNNER"

# Windows Git-Bash ships a `python3` that is the Microsoft Store stub and dies without
# ever parsing anything, so probe it before trusting the name.
resolve_python() {
  local c
  for c in "${PYTHON_BIN:-}" python3 python; do
    [ -n "$c" ] || continue
    if command -v "$c" >/dev/null 2>&1 && "$c" -c 'import sys; sys.exit(0 if sys.version_info[:2] >= (3, 8) else 1)' >/dev/null 2>&1; then
      printf '%s\n' "$c"
      return 0
    fi
  done
  return 1
}

PY="$(resolve_python)" || die "no usable python3/python (>=3.8) on PATH — the runner needs it. Set PYTHON_BIN=/path/to/python."
log "python: $PY"

# The runner records this into the summary identity, so the numbers can be tied to a SHA.
COMMIT="$(cd "$SCRIPT_DIR/.." && git rev-parse HEAD 2>/dev/null)" || COMMIT="unknown"
export GIT_COMMIT="$COMMIT"
log "git commit for this run: $COMMIT"

ARGS=( "$RUNNER" "--requests" "$REQUESTS" )
[ -n "$KB_DIR" ] && ARGS+=( --kb-dir "$KB_DIR" )
[ -n "$FIXTURE_LOCK" ] && ARGS+=( --fixture-lock "$FIXTURE_LOCK" )
ARGS+=( --api-key-env "$API_KEY_ENV" )
[ "$SELF_TEST" -eq 1 ] && ARGS+=( --self-test )
[ "$DRY_RUN" -eq 1 ] && ARGS+=( --dry-run )
[ "$WRITE_LOCK" -eq 1 ] && ARGS+=( --write-lock )
if [ "$SELF_TEST" -ne 1 ] && [ "$WRITE_LOCK" -ne 1 ]; then
  mkdir -p "$OUT_DIR"
  ARGS+=( --out "$OUT_DIR/suggest-baseline.jsonl"
          --summary "$OUT_DIR/suggest-baseline.summary.json"
          --csv "$OUT_DIR/suggest-baseline.csv" )
fi
[ "${#EXTRA[@]}" -gt 0 ] && ARGS+=( "${EXTRA[@]}" )

if [ "$SELF_TEST" -eq 1 ]; then
  "$PY" "${ARGS[@]}"
  exit $?
fi

# Capture the runner's real exit code instead of letting `set -e` hide it: the
# caller (CI or a human) must see exactly 0/1/2 from the runner.
rc=0
"$PY" "${ARGS[@]}" || rc=$?

if [ "$WRITE_LOCK" -eq 1 ] || [ "$rc" -ne 0 ]; then
  exit "$rc"
fi

# ── run identity: what exactly produced these numbers ────────────────────────
mkdir -p "$OUT_DIR"
{
  printf 'generated_at_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'git_commit=%s\n' "$COMMIT"
  printf 'suggest_md_sha256=%s\n' "$(sha256_of "$SCRIPT_DIR/../app/src/main/assets/engine/suggest.md")"
  printf 'fixture_lock_sha256=%s\n' "$(sha256_of "$SCRIPT_DIR/../benchmarks/suggest-baseline/FIXTURE.lock")"
  printf 'requests=%s\n' "$REQUESTS"
  if [ "$DRY_RUN" -eq 1 ]; then
    printf 'mode=dry-run (NO request was sent; every usage/cost field in the jsonl is null)\n'
  else
    printf 'mode=live\n'
  fi
  printf 'extra_args=%s\n' "${EXTRA[*]-}"
  printf 'api_key_env=%s (value never recorded)\n' "$API_KEY_ENV"
} >"$OUT_DIR/run-identity.txt"

ok "baseline artifacts in $OUT_DIR (jsonl / summary.json / csv / run-identity.txt)"
printf '%s  next: fill the old-vs-new table in BENCHMARK.md from these files; the fixture is\n' "$GATE_LOG_PREFIX"
printf '%s        frozen by benchmarks/suggest-baseline/FIXTURE.lock, so both arms must be run\n' "$GATE_LOG_PREFIX"
printf '%s        against the same lock (see BENCHMARK.md 第 5 节 for the exact replay commands).\n' "$GATE_LOG_PREFIX"
