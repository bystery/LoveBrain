#!/usr/bin/env bash
#
# scripts/test_verify_network_egress.sh
#
# P0-04（独立复核 2026-09-24 §3.1）：
#   "修复后必须用合成 pcap/pcap fixture 做正反测试：
#      allow host only → PASS； 表外 SNI → FAIL； 表外 DNS → FAIL；
#      表外直连 IP、无 DNS/SNI → FAIL； 空 pcap → CANNOT-VERIFY； 无 tshark → CANNOT-VERIFY"
#
# 这个脚本就是那六格，外加本仓库自己补的第 7 格（"有 tshark 但文件打不开"）：
# 指导书那六格没有把"工具缺失"和"工具拒绝这个文件"分开，于是 pcap magic 写错时
# 第 6 格会替第 1–4 格掩盖过去。它测的是**判据本身**：verify_network_egress.sh 里
# `case "$ip" in ...|*) continue ;;` 那两行会让第 3、4 格静默变绿（假阴性），
# 而"零遥测"这种结论最可怕的地方恰恰是它长得像通过。
#
# 每一格都断言两件事：退出码 + 报告里"表外目的地"的确切条数与内容。
# 只断言退出码不够——第 2 格（表外 SNI）如果同时把 IP 视角也点亮，
# 它也会 FAIL，但那时通过的是一桩不相干的事故。
# 三格 CANNOT-VERIFY 退出码相同，所以各自的 needle 必须点出自己的理由（见下面 5/6/7）。
#
# Usage:
#   bash scripts/test_verify_network_egress.sh
#
# 前置：tshark + python3。缺任何一样本脚本**失败**而不是跳过
# （PYTHON 可指定解释器路径；Windows 上 python3 常是商店占位符，见 docs/避坑指南.md）。
#
# Exit codes: 0 七格全对，1 有格子判错，2 前置工具缺失（=没验证）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

PYTHON="${PYTHON:-python3}"
TSHARK="${TSHARK_BIN:-tshark}"
# WORK_DIR 指到仓库里时，七格的原始输出会留在那儿给 CI 当产物上传；
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
for f in allow-only.pcap rogue-sni.pcap rogue-dns.pcap bare-ip.pcap empty.pcap bad-magic.pcap; do
  [ -f "$FIX/$f" ] || die "fixture generator did not produce $f"
done

PASSED=0
FAILED=0
RAN=0
LABELS=()   # 格子名查重：同名两格会互相盖掉 $WORK/<label>.out，第 6 格就是这么蒙对的

# check_case <格子名> <期望退出码> <期望表外条数> <期望出现的字符串> [--tshark <路径>] -- <脚本参数…>
#
# --tshark 走子 shell 里的 `export TSHARK_BIN=`，**不走 `env VAR=… cmd`**：
# 本机 PATH 上 /c/Users/abyss/.local/bin/env 是个把参数整个吞掉还返回 0 的空壳（坑表 107），
# 用它的话第 6 格会静默退化成"拿真 tshark 跑 allow-only"——格子还在、RAN 也对，牙没了。
check_case() {
  local label="$1" want_rc="$2" want_count="$3" needle="$4"
  shift 4
  local tshark_override=""
  while [ $# -gt 0 ] && [ "$1" != "--" ]; do
    case "$1" in
      --tshark)
        [ $# -ge 2 ] || die "check_case($label): --tshark 后面要跟一个路径"
        tshark_override="$2"; shift 2 ;;
      *) die "check_case($label): '--' 之前只接受 --tshark，收到 '$1'" ;;
    esac
  done
  [ "${1:-}" = "--" ] || die "check_case($label): 缺少 -- 分隔符（少了它脚本参数会被当环境变量吃掉）"
  shift
  local seen
  for seen in ${LABELS[@]+"${LABELS[@]}"}; do
    [ "$seen" != "$label" ] || die "check_case: 格子名 '$label' 出现了两次——同名两格会互相盖掉 $WORK/$label.out"
  done
  LABELS+=("$label")
  RAN=$((RAN + 1))

  local out rc=0
  out="$WORK/$label.out"
  set +e
  (
    if [ -n "$tshark_override" ]; then export TSHARK_BIN="$tshark_override"; fi
    bash "$ROOT/scripts/verify_network_egress.sh" "$@" --out "$WORK/$label.txt"
  ) >"$out" 2>&1
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

# 5/6/7) 三格 CANNOT-VERIFY。它们的 needle **各自点名自己的理由**，不写通用的
#    "CANNOT-VERIFY"——到 `78fec29` 为止三格共用那一个词，于是 fixture 的 pcap magic
#    写错（tshark 打不开）时第 6 格"没有 tshark"**因为文件读不开而蒙对**，
#    把真因盖成了通过。退出码 2 三家也一样，只有理由串能分开它们。
# 5) 空 pcap（只有全局头、零个包）→ CANNOT-VERIFY，不能算"零遥测成立"
check_case empty-pcap 2 - "no destination, SNI or DNS record at all" -- \
  --pcap "$FIX/empty.pcap" "${common_allow[@]}"

# 6) 没有 tshark → CANNOT-VERIFY（用不存在的路径确定性地复现，不靠"这台机器碰巧没装"）
#    needle 用 "not installed"：verify_network_egress.sh 里有两条"工具不在"的分支
#    （--pcap 前置那一条、run_tshark 里那一条），先走哪条都算这一格成立；
#    但两条都造不出第 5、7 格的理由串，所以三格仍然互不遮蔽。
check_case no-tshark 2 - "not installed" --tshark "$WORK/no-such-tshark" -- \
  --pcap "$FIX/allow-only.pcap" "${common_allow[@]}"

# 7) 有 tshark、文件却打不开 → CANNOT-VERIFY，而且**不能和第 6 格混成一格**。
#    这一格就是 pcap magic 写错那次的形状：工具在、包体也合法，只是全局头没人认。
check_case bad-magic 2 - "an unparseable capture" -- \
  --pcap "$FIX/bad-magic.pcap" "${common_allow[@]}"

printf '\n%s  egress checker self-test: %d/%d case(s) correct\n' "$GATE_LOG_PREFIX" "$PASSED" "$RAN"
[ "$RAN" -eq 7 ] || die "expected 7 self-test cases, ran $RAN — a case went missing"
[ "$FAILED" -eq 0 ] || die "$FAILED of $RAN egress self-test case(s) judged wrong"
ok "verify_network_egress.sh grades all seven fixture cases correctly"
