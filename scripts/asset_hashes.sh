#!/usr/bin/env bash
#
# scripts/asset_hashes.sh
#
# P3-08: 提示词资产内容指纹。
#
# BENCHMARK.md 以前写「Prompt 资产 hash：见目录」——那不是 hash。
# 复核报告 §7.3 要的是一条可以重放、可比对的东西，这个脚本就是：
#   * 逐文件 SHA-256
#   * 回复链路 system prompt 按**拼装顺序**算出的组合 hash
#     （顺序变了 hash 就变，和 PromptBuilder.buildSystemPrompt() 一致）
#
# Usage:
#   bash scripts/asset_hashes.sh                 # 打印 JSON
#   bash scripts/asset_hashes.sh --write dist/prompt-assets.json
#   bash scripts/asset_hashes.sh --check docs/prompt-assets.lock   # 漂移即失败
#
# Exit codes: 0 ok, 1 drift or missing asset, 2 usage/tooling.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets/engine"

WRITE=""
CHECK=""
MODE="print"
while [ $# -gt 0 ]; do
  case "$1" in
    --write) WRITE="$2"; MODE="write"; shift 2 ;;
    --check) CHECK="$2"; MODE="check"; shift 2 ;;
    *) echo "[asset_hashes] unknown argument: $1" >&2; exit 2 ;;
  esac
done

sha256_of() { sha256sum "$1" | cut -d' ' -f1; }

REPLY_ASSETS=(
  "$ASSETS/system_prompt/core.md"
  "$ASSETS/system_prompt/naturalness_check.md"
  "$ASSETS/system_prompt/redline.md"
  "$ASSETS/system_prompt/format.md"
)
OTHER_ASSETS=(
  "$ASSETS/suggest.md"
  "$ASSETS/counseling.md"
  "$ASSETS/proactive.md"
  "$ASSETS/polish.md"
  "$ASSETS/system_prompt/stage.md"
  "$ASSETS/system_prompt/aggressive.md"
)

for f in "${REPLY_ASSETS[@]}" "${OTHER_ASSETS[@]}"; do
  if [ ! -f "$f" ]; then
    echo "[asset_hashes] FAIL missing prompt asset: $f" >&2
    exit 1
  fi
done

# 组合 hash：按拼装顺序把「路径 + 内容」串起来再摘要，
# 这样换顺序也能被发现，而逐文件 hash 相加发现不了。
COMBINED_INPUT=""
for f in "${REPLY_ASSETS[@]}"; do
  COMBINED_INPUT+="$(basename "$f")=$(cat "$f")"
done
COMBINED_HASH="$(printf '%s' "$COMBINED_INPUT" | sha256sum | cut -d' ' -f1)"

# 逗号只能出现在非末项，否则产出的不是合法 JSON。
emit_group() {
  local total=$# n=0 f
  for f in "$@"; do n=$((n + 1)); done
  n=0
  for f in "$@"; do
    n=$((n + 1))
    if [ "$n" -lt "$total" ]; then
      printf '    "%s": "%s",
' "$(basename "$f")" "$(sha256_of "$f")"
    else
      printf '    "%s": "%s"
' "$(basename "$f")" "$(sha256_of "$f")"
    fi
  done
}

REPLY_HASHES="$(emit_group "${REPLY_ASSETS[@]}")"
OTHER_HASHES="$(emit_group "${OTHER_ASSETS[@]}")"

GIT_SHA="$(cd "$ROOT" && git rev-parse HEAD 2>/dev/null || echo unknown)"

DOC="{
  \"generated_from_commit\": \"$GIT_SHA\",
  \"reply_system_ordered_sha256\": \"$COMBINED_HASH\",
  \"reply_system_assets\": {
$REPLY_HASHES
  },
  \"other_assets\": {
$OTHER_HASHES
  }
}"

case "$MODE" in
  print) printf '%s\n' "$DOC" ;;
  write)
    mkdir -p "$(dirname "$WRITE")"
    printf '%s\n' "$DOC" >"$WRITE"
    echo "[asset_hashes]  OK   wrote $WRITE (commit ${GIT_SHA:0:12})"
    ;;
  check)
    if [ ! -f "$CHECK" ]; then
      echo "[asset_hashes] FAIL lock file missing: $CHECK" >&2
      exit 1
    fi
    locked="$(sed -n 's/.*"reply_system_ordered_sha256": "\([0-9a-f]*\)".*/\1/p' "$CHECK")"
    if [ -z "$locked" ]; then
      echo "[asset_hashes] FAIL lock file carries no reply_system_ordered_sha256" >&2
      exit 1
    fi
    if [ "$locked" != "$COMBINED_HASH" ]; then
      echo "[asset_hashes] FAIL prompt assets drifted from $CHECK" >&2
      echo "               locked:   $locked" >&2
      echo "               current:  $COMBINED_HASH" >&2
      echo "               BENCHMARK/README numbers computed against the locked set no longer describe this tree." >&2
      exit 1
    fi
    echo "[asset_hashes]  OK   prompt assets match $CHECK ($locked)"
    ;;
esac
