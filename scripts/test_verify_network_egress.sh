#!/usr/bin/env bash
#
# scripts/test_verify_network_egress.sh
#
# P0-04（独立复核 2026-09-24 §3.1）：
#   "修复后必须用合成 pcap/pcap fixture 做正反测试：
#      allow host only → PASS； 表外 SNI → FAIL； 表外 DNS → FAIL；
#      表外直连 IP、无 DNS/SNI → FAIL； 空 pcap → CANNOT-VERIFY； 无 tshark → CANNOT-VERIFY"
#
# 这个脚本就是那六格。它测的是**判据本身**：verify_network_egress.sh 里
# `case "$ip" in ...|*) continue ;;` 那两行会让第 3、4 格静默变绿（假阴性），
# 而"零遥测"这种结论最可怕的地方恰恰是它长得像通过。
#
# 每一格都断言两件事：退出码 + 报告里"表外目的地"的确切条数与内容。
# 只断言退出码不够——第 2 格（表外 SNI）如果同时把 IP 视角也点亮，
# 它也会 FAIL，但那时通过的是一桩不相干的事故。
#
# Usage:
#   bash scripts/test_verify_network_egress.sh
#
# 前置：tshark + python3。缺任何一样本脚本**失败**而不是跳过
# （PYTHON 可指定解释器路径；Windows 上 python3 常是商店占位符，见 docs/避坑指南.md）。
#
# Exit codes: 0 六格全对，1 有格子判错，2 前置工具缺失（=没验证）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

PYTHON="${PYTHON:-python3}"
TSHARK="${TSHARK_BIN:-tshark}"
# WORK_DIR 指到仓库里时，六格的原始输出会留在那儿给 CI 当产物上传；
# 不指就用临时目录并退出时清掉。
if [ -n "${WORK_DIR:-}" ]; then
  WORK="$WORK_DIR"
  mkdir -p "$WORK"
else
  WORK="$(mktemp -d)"
  trap 'rm -rf "$WORK"' EXIT
fi

CDN_ALLOW_HOST="api.example.test"
ALLOW_IPS=(203.0.113.20 198.51.100.53) # Provider IP + 声明过的解析器 IP
# 报告里的每一行是 "kind<TAB>value"，断言时必须用真制表符，不能靠肉眼看对齐
TAB=$'\t'

# ── 前置：缺工具就是"没验证"，不允许绿 ─────────────────────────────────────
if ! command -v "$TSHARK" >/dev/null 2>&1; then
  printf '%s  CANNOT-VERIFY %s not installed — the egress checker cannot be graded without a real pcap reader.\n' \
    "$GATE_LOG_PREFIX" "$TSHARK" >&2
  printf '%s  install it (CI does: apt-get install -y tshark) instead of skipping this gate.\n' "$GATE_LOG_PREFIX" >&2
  exit 2
fi
if ! command -v "$PYTHON" >/dev/null 2>&1; then
  printf '%s  CANNOT-VERIFY python interpreter missing (set PYTHON=...)\n' "$GATE_LOG_PREFIX" >&2
  exit 2
fi

"$PYTHON" "$ROOT/scripts/make_egress_fixtures.py" --out "$WORK/fixtures" >"$WORK/gen.log" 2>&1
FIX="$WORK/fixtures"
for f in allow-only.pcap rogue-sni.pcap rogue-dns.pcap bare-ip.pcap empty.pcap; do
  [ -f "$FIX/$f" ] || die "fixture generator did not produce $f"
done

PASSED=0
FAILED=0
RAN=0

# check_case <格子名> <期望退出码> <期望表外条数> <期望出现的字符串> [TSHARK_BIN=... ] -- <脚本参数…>
check_case() {
  local label="$1" want_rc="$2" want_count="$3" needle="$4"
  shift 4
  if [ "$1" = "--" ]; then shift; fi
  RAN=$((RAN + 1))

  local out rc=0
  out="$WORK/$label.out"
  set +e
  bash "$ROOT/scripts/verify_network_egress.sh" "$@" --out "$WORK/$label.txt" >"$out" 2>&1
  rc=$?
  set -e

  local problems=""
  [ "$rc" -eq "$want_rc" ] || problems+="exit code: want $want_rc got $rc; "
  if [ "$want_count" = "-" ]; then
    : # CANNOT-VERIFY 格不查条数（它根本不该走到计数）
  else
    local got_count
    got_count="$(sed -n 's/^out-of-list destinations *: *//p' "$out" | head -1)"
    [ "${got_count:-no-line}" = "$want_count" ] || problems+="out-of-list count: want $want_count got '${got_count:-<none>}'; "
  fi
  if [ -n "$needle" ]; then
    grep -qF -- "$needle" "$out" || problems+="expected text not found: '$needle'; "
  fi

  if [ -z "$problems" ]; then
    printf '%s  PASS  %-12s (exit %s)\n' "$GATE_LOG_PREFIX" "$label" "$rc"
    PASSED=$((PASSED + 1))
  else
    printf '%s  FAIL  %-12s %s\n' "$GATE_LOG_PREFIX" "$label" "$problems"
    printf '%s        ---- %s output ----\n' "$GATE_LOG_PREFIX" "$label"
    sed 's/^/        /' "$out" || true
    FAILED=$((FAILED + 1))
  fi
}

common_allow=(--allow-host "$CDN_ALLOW_HOST")
for ip in "${ALLOW_IPS[@]}"; do common_allow+=(--allow-ip "$ip"); done

# 1) 表内目的地 → PASS，且表外计数必须是 0
check_case allow-only 0 0 "no destination outside the declared egress list" -- \
  --pcap "$FIX/allow-only.pcap" "${common_allow[@]}"

# 2) 表外 SNI → FAIL，且只有 SNI 这一条被点亮
check_case rogue-sni 1 1 "sni	telemetry.example.test" -- \
  --pcap "$FIX/rogue-sni.pcap" "${common_allow[@]}"

# 3) 表外 DNS → FAIL，且只有 DNS 这一条被点亮
check_case rogue-dns 1 1 "dns	ads.example.test" -- \
  --pcap "$FIX/rogue-dns.pcap" "${common_allow[@]}"

# 4) 表外直连 IP、无 DNS/SNI → FAIL。**这一格就是旧 `|*) continue` 的死刑证据**：
#    通配兜底还在时它是 exit 0 / count 0。
check_case bare-ip 1 1 "ip	198.51.100.77" -- \
  --pcap "$FIX/bare-ip.pcap" "${common_allow[@]}"

# 5) 空 pcap（只有全局头）→ CANNOT-VERIFY，不能算"零遥测成立"
check_case empty-pcap 2 - "CANNOT-VERIFY" -- \
  --pcap "$FIX/empty.pcap" "${common_allow[@]}"

# 6) 没有 tshark → CANNOT-VERIFY（用不存在的路径确定性地复现，不靠"这台机器碰巧没装"）
TSHARK_MISSING="$WORK/no-tshark"
check_case no-tshark 2 - "CANNOT-VERIFY" -- \
  --pcap "$FIX/allow-only.pcap" "${common_allow[@]}"

# 第 6 格需要覆盖 TSHARK_BIN，check_case 不透传环境变量，所以单独跑一遍。
RAN=$((RAN + 1))
rc6=0
TSHARK_BIN="$TSHARK_MISSING" bash "$ROOT/scripts/verify_network_egress.sh" \
  --pcap "$FIX/allow-only.pcap" "${common_allow[@]}" >"$WORK/no-tshark.out" 2>&1 || rc6=$?
if [ "$rc6" -eq 2 ] && grep -qF "CANNOT-VERIFY" "$WORK/no-tshark.out"; then
  printf '%s  PASS  %-12s (exit 2)\n' "$GATE_LOG_PREFIX" "no-tshark"
  PASSED=$((PASSED + 1))
else
  printf '%s  FAIL  %-12s exit=%s, want 2 + CANNOT-VERIFY\n' "$GATE_LOG_PREFIX" "no-tshark" "$rc6"
  FAILED=$((FAILED + 1))
fi

printf '\n%s  egress checker self-test: %d/%d case(s) correct\n' "$GATE_LOG_PREFIX" "$PASSED" "$RAN"
[ "$RAN" -eq 6 ] || die "expected 6 self-test cases, ran $RAN — a case went missing"
[ "$FAILED" -eq 0 ] || die "$FAILED of $RAN egress self-test case(s) judged wrong"
ok "verify_network_egress.sh grades all six fixture cases correctly"
