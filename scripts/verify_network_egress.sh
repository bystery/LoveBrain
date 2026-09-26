#!/usr/bin/env bash
#
# scripts/verify_network_egress.sh
#
# P3-05 / §8.3.6: 网络出口验证——把"零遥测"从声明变成可重放的检查。
#
# 复核报告 §7.3 的原文是：README 写「依赖扫描和网络抓包证明只有用户配置 Provider 请求」，
# 但仓库里除了这句话没有任何 pcap、抓包报告或可复现脚本。
# 本脚本补上脚本这一半；另一半（真实抓包）必须在有设备的机器上跑，
# 在没有设备的环境里它会明确失败，而不是假装通过。
#
# Usage:
#   bash scripts/verify_network_egress.sh --pcap <file.pcap> \
#        --allow-host api.deepseek.com [--allow-host <other>]... \
#        [--allow-ip 1.2.3.4]... [--out dist/network-egress.txt] [--json dist/network-egress.json]
#
#   bash scripts/verify_network_egress.sh --capture-on-device \
#        [--serial <adb-serial>] [--duration 120]
#
# Self-test of the checker itself (synthetic captures,正反 both directions):
#   bash scripts/test_verify_network_egress.sh
#
# Exit codes:
#   0  抓包存在且所有外连目的地都在白名单内
#   1  发现表外目的地（即潜在遥测/后端）
#   2  工具或输入缺失（tshark/pcap/设备）——这是"没能验证"，不是"验证通过"
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/lib/gate_lib.sh"

# ── 静默中止防护 ────────────────────────────────────────────────────────────
# 这个脚本在 `set -e` + `pipefail` 下跑，而有一整类命令的**正常结果就是非 0**
# （`grep -v` 一行没选中、`getent` 查不到主机）。这类命令一旦落在没有 `||` 兜住的位置，
# 脚本就在那一行当场死掉：退出码看着像判决，报告却一个字都没写。
# CI run 36206913392 就这么红掉四格：`allow-only`/`rogue-sni` 拿到 exit 2
# （getent 查不到合成 fixture 里的假主机名）、`rogue-dns`/`bare-ip`/`empty-pcap` 拿到 exit 1
# （post_process 里 `grep -v` 空选）。**P0-04 修的是"通配符放过一切"，这三格是同一类病的另一张脸**：
# 结论长得像通过。所以修两处命令本身，再留这一条兜底——以后任何一处再静默中止，
# 都会显式打印 CANNOT-VERIFY 并把退出码钉在 2，而不是留下一个像是判过的数字。
# 生效范围到"打印汇总"为止：那之后的事（数表外条数、写报告）由自测试的
# **退出码 + 条数 + 理由串**三件证人各自盯着，死了照样红。
VERDICT=""
die_unverified() {
  VERDICT="cannot-verify"
  printf '%s  CANNOT-VERIFY %s
' "$GATE_LOG_PREFIX" "$*" >&2
  exit 2
}
on_err() {
  local rc="$1"
  if [ -z "$VERDICT" ]; then
    printf '%s  CANNOT-VERIFY the checker aborted with rc=%s **before printing any verdict**: ' \
      "$GATE_LOG_PREFIX" "$rc" >&2
    printf 'that is a tool bug, not a result — fix the abort, do not soften the assertions.\n' >&2
    printf '%s  (本文件把这类死法定为 exit 2 的理由：P0-04 要防的就是"结论长得像通过"。' \
      "$GATE_LOG_PREFIX" >&2
    printf '查法：bash -x 这一份脚本，看最后一行落在哪条命令上。)\n' >&2
    VERDICT="died"
    exit 2
  fi
}
trap 'on_err $?' ERR

PCAP=""
CAPTURE=0
DURATION=120
SERIAL=""
OUT=""
JSON_OUT=""
ALLOW_HOSTS=()
ALLOW_IPS_EXTRA=()

# TSHARK_BIN 默认就是 PATH 上的 tshark。可覆盖是为了让"工具缺失"这一格
# 能被独立测试脚本确定性地复现（指向一个不存在的路径），而不是靠"这台机器碰巧没装"。
TSHARK_BIN="${TSHARK_BIN:-tshark}"

while [ $# -gt 0 ]; do
  case "$1" in
    --pcap) PCAP="$2"; shift 2 ;;
    --capture-on-device) CAPTURE=1; shift ;;
    --duration) DURATION="$2"; shift 2 ;;
    --serial) SERIAL="$2"; shift 2 ;;
    --allow-host) ALLOW_HOSTS+=("$2"); shift 2 ;;
    --allow-ip) ALLOW_IPS_EXTRA+=("$2"); shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --json) JSON_OUT="$2"; shift 2 ;;
    *) die_usage "verify_network_egress.sh: unknown argument: $1" ;;
  esac
done

have() { command -v "$1" >/dev/null 2>&1; }

# run_tshark <描述> <输出文件> <tshark 参数…>
#
# 复核报告 §3 的那类伪门禁就长这样：
#   tshark … | sort -u > out || true
# tshark 报错 → 输出空 → "表外目的地 0 个" → 脚本宣布零遥测成立。这里把 tshark 的
# 退出码真的当回事：非 0 就是"没能验证"（exit 2），输出为空也是"没能验证"，绝不放行。
run_tshark() {
  local what="$1" out="$2"
  shift 2
  local err
  err="$(mktemp)"
  if ! have "$TSHARK_BIN"; then
    rm -f "$err"
    die_unverified "$TSHARK_BIN is not installed — $what cannot be analysed, so the zero-telemetry claim stays UNVERIFIED"
  fi
  if ! "$TSHARK_BIN" -r "$PCAP" "$@" >"$out" 2>"$err"; then
    printf '%s  tshark failed while computing %s:\n' "$GATE_LOG_PREFIX" "$what" >&2
    sed 's/^/    /' "$err" >&2
    rm -f "$err"
    die_unverified "tshark errored on $what — an unparseable capture is NOT evidence of no telemetry"
  fi
  rm -f "$err"
  if [ ! -s "$out" ]; then
    # 空输出不是"没有遥测"，而是"这个视角什么都没看见"——单独放过没有意义，
    # 但三个视角全空时下面会统一判定 CANNOT-VERIFY。
    warn "$what produced zero rows from $PCAP"
  else
    log "$what: $(wc -l <"$out" | tr -d ' ') row(s)"
  fi
}

# adb 只在"要现场抓包"时才需要。--pcap 模式是离线分析，
# 把它当成全局前置条件会让"没有 adb"盖住"没有 tshark"这一格，测试就分不开了。
if [ "$CAPTURE" -eq 1 ]; then
  have adb || die_unverified "adb not on PATH; cannot produce or pull a capture"
  ADB=(adb)
  [ -n "$SERIAL" ] && ADB+=( -s "$SERIAL" )
  "${ADB[@]}" get-state >/dev/null 2>&1 || die 2 "no device attached"
  PCAP="$(mktemp -t lovebrain-egress-XXXXXX.pcap)"
  REMOTE=/sdcard/lovebrain-egress.pcap
  log "capturing ${DURATION}s of device traffic (this needs root or an emulator)"
  "${ADB[@]}" root >/dev/null 2>&1 || warn "adb root failed; tcpdump may not be permitted"
  "${ADB[@]}" shell "tcpdump -i any -U -w $REMOTE 'not port 5555'" >/dev/null 2>&1 &
  TCPDUMP_PID=$!
  # shellcheck disable=SC2064
  trap 'kill $TCPDUMP_PID 2>/dev/null || true; "${ADB[@]}" shell "pkill tcpdump" >/dev/null 2>&1 || true' EXIT
  sleep "$DURATION"
  "${ADB[@]}" shell "pkill tcpdump" >/dev/null 2>&1 || true
  wait $TCPDUMP_PID 2>/dev/null || true
  "${ADB[@]}" pull "$REMOTE" "$PCAP" >/dev/null
  "${ADB[@]}" shell "rm -f $REMOTE" >/dev/null 2>&1 || true
fi

[ -n "$PCAP" ] || die_usage "provide --pcap <file> or --capture-on-device"
[ -s "$PCAP" ] || die_unverified "pcap missing or empty: $PCAP — nothing was captured, so the claim is UNVERIFIED, not proven"

have "$TSHARK_BIN" || die_unverified "$TSHARK_BIN not installed; cannot parse the capture"

if [ "${#ALLOW_HOSTS[@]}" -eq 0 ]; then
  die_usage "at least one --allow-host is required (the user-configured Provider host)"
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# 外连目的地：IPv4 目的地址 + TLS SNI + DNS 查询名，三个视角交叉，
# 避免只看到 IP 而漏掉直连 IP 的遥测端点。任一视角解析失败或零行 → CANNOT-VERIFY。
run_tshark "destination IPs" "$WORK/dst_ip.raw" -T fields -e ip.dst -e _ws.col.Destination \
  -Y "not ip.dst==224.0.0.0/4 and not ip.dst==255.255.255.255"
run_tshark "TLS SNI names" "$WORK/sni.raw" -T fields -e tls.handshake.extensions_server_name
run_tshark "DNS queries" "$WORK/dns.raw" -Y "dns.flags.response==0" -T fields -e dns.qry.name

post_process() {
  # post_process <raw-file> <separator-regex> <out> — 拆字段 + 去端口 + 去空 + 排序去重
  #
  # 去空这一步**用 `sed /d`，不用 `grep -v`**：`grep -v` 一行都没选中时返回 1，
  # 在 `pipefail` 下会把整个脚本当场带走（CI 上 `bare-ip`/`rogue-dns`/`empty` 三格
  # 就是这么拿到 exit 1 的——那一格里"这个视角没有行"是**正常输入**）。
  # 空不空由后面的 `[ -s ]` 判，不是由命令的退出码判。
  local raw="$1" sep="$2" out="$3"
  tr "$sep" '\n' <"$raw" | sed 's/:[0-9]*$//' | sed '/^[[:space:]]*$/d' | sort -u >"$out"
}
post_process "$WORK/dst_ip.raw" '\t' "$WORK/dst_ip.txt"
post_process "$WORK/sni.raw" ',' "$WORK/sni.txt"
post_process "$WORK/dns.raw" ',' "$WORK/dns.txt"

# 三个视角全空 = 抓包里根本没有可判定的外连，这是"没验证"，不是"零遥测成立"。
if [ ! -s "$WORK/dst_ip.txt" ] && [ ! -s "$WORK/sni.txt" ] && [ ! -s "$WORK/dns.txt" ]; then
  die_unverified "the capture yielded no destination, SNI or DNS record at all — the app never talked to anyone during the capture, so 'no telemetry' is UNVERIFIED, not proven"
fi

# 把允许的主机解析成 IP，允许 DNS/SNI 名与 IP 任一对上。
# 解析不到任何允许 IP 时不算失败（离线/无 DNS 环境），但会显式记录，
# 因为那种情况下判定只依赖 SNI/DNS 名，报告里必须看得见这一点。
ALLOW_IPS="$WORK/allow_ips.txt"
: >"$ALLOW_IPS"
# 显式声明的 IP 优先于 DNS 解析结果：合成抓包（fixture）里的主机名是假的，
# 只有 IP 是真的；离线环境也没有 DNS 可查。
for ip in ${ALLOW_IPS_EXTRA[@]+"${ALLOW_IPS_EXTRA[@]}"}; do
  printf '%s\n' "$ip" >>"$ALLOW_IPS"
done
for h in "${ALLOW_HOSTS[@]}"; do
  # 两条探测都是"**查不到**是正常输入"：合成 fixture 里的主机名本来就是假的
  # （TEST-NET 的 .test），离线环境也没有 DNS。`getent`/`python3` 查不到时返回非 0，
  # 而这里以前没有兜底 —— CI 上 `allow-only`/`rogue-sni` 就是死在 getent 那行的 exit 2，
  # 本机（Windows 的 python3 是商店占位符，返回 49）死在第二行。
  # 吞掉的只是**探测命令的状态**；"一个允许 IP 都没解析出来"这件事仍由下面那条 warn
  # 显式写进报告，判据没变软。
  if have getent; then
    getent hosts "$h" | awk '{print $1}' >>"$ALLOW_IPS" || true
  fi
  if have python3; then
    python3 - "$h" <<'PY' >>"$ALLOW_IPS" || true
import socket,sys
try:
    for fam,_,_,_,sa in socket.getaddrinfo(sys.argv[1], None):
        if fam == socket.AF_INET:
            print(sa[0])
except Exception:
    pass
PY
  fi
done
sort -u "$ALLOW_IPS" -o "$ALLOW_IPS"
if [ ! -s "$ALLOW_IPS" ]; then
  warn "no allowed host resolved to an IP — the IP view is unchecked, only SNI/DNS names are"
fi

UNEXPECTED="$WORK/unexpected.txt"
: >"$UNEXPECTED"

# 只跳过"根本出不了本机/本链路"的地址：回环、RFC1918、链路本地、ULA、组播。
#
# 这一格以前结尾写的是 `|*) continue ;;`。`*` 匹配任何输入，于是**每一个**目的 IP
# 都在这里被 continue 掉——整个 IP 视角形同不存在，直连 IP 的遥测端点会被放行
# （独立复核 P0-04 指出的假阴性）。兜底语义必须是"没列出来的算表外"，
# 不是"没列出来的都放过"。
#
# 同时删掉了旧列表里的 `2000::*`：2000::/3 是全局单播聚合，公网地址几乎全在里面，
# 留着它和留个 `*` 没有本质区别。
is_nonroutable() {
  case "$1" in
    10.*|127.*|192.168.*|169.254.*|0.0.0.0|::) return 0 ;;
    172.1[6-9].*|172.2[0-9].*|172.3[01].*) return 0 ;;
    224.*|239.*|255.255.255.255) return 0 ;;
    ::1|fe[89ab]*:*|fc*:*|fd*:*|ff*:*) return 0 ;;
  esac
  return 1
}

# 表内主机名 = 与允许主机**完全相等**，或它是某个允许主机的**子域**。
#
# ⚠ 这一格是 CI 逼出来的第二版。第一版写成
#       [ "$name" = "$h" ] || case "$name" in *".$h") ok=1 ;; esac
#   读起来像"相等就放过，否则看后缀"，实际是：**相等时 `||` 短路，右边那句根本不执行，
#   ok 一直是 0** ⇒ 完全匹配的那颗主机反而被判成表外。本机一句就能复现：
#   `name=h=api.example.test` 走一遍 ⇒ ok 仍是 0。
#   以前看不见这条，是因为五格 fixture 从没被 tshark 真读开过（pcap magic 写错，账本 §58）。
#   SNI 与 DNS 两派当时**各抄了一遍**这一句，所以同一个 bug 会点亮两条视角；
#   现在收成一处——同一条判据不许有两份实现。
matches_allow_host() {
  local name="$1" h
  for h in "${ALLOW_HOSTS[@]}"; do
    if [ "$name" = "$h" ]; then return 0; fi
    case "$name" in *".$h") return 0 ;; esac
  done
  return 1
}

while read -r ip; do
  [ -n "$ip" ] || continue
  if is_nonroutable "$ip"; then continue; fi
  grep -qx -- "$ip" "$ALLOW_IPS" || printf 'ip\t%s\n' "$ip" >>"$UNEXPECTED"
done <"$WORK/dst_ip.txt"
while read -r name; do
  [ -n "$name" ] || continue
  matches_allow_host "$name" || printf 'sni\t%s\n' "$name" >>"$UNEXPECTED"
done <"$WORK/sni.txt"
PTR_SKIPPED=0
while read -r name; do
  [ -n "$name" ] || continue
  # 反向解析（*.arpa）由系统产生、不是 App 的目的地，跳过但必须在报告里数得出来，
  # 不能像旧的 `*.arpa|*` 那样顺手把正向查询一起吞掉。
  case "$name" in
    *.arpa)
      PTR_SKIPPED=$((PTR_SKIPPED + 1))
      continue
      ;;
  esac
  matches_allow_host "$name" || printf 'dns\t%s\n' "$name" >>"$UNEXPECTED"
done <"$WORK/dns.txt"

SUMMARY="$WORK/summary.txt"
{
  echo "pcap                     : $PCAP"
  echo "allowed hosts            : ${ALLOW_HOSTS[*]}"
  echo "distinct destination IPs : $(wc -l <"$WORK/dst_ip.txt" | tr -d ' ')"
  echo "distinct TLS SNI         : $(wc -l <"$WORK/sni.txt" | tr -d ' ')"
  echo "distinct DNS queries     : $(wc -l <"$WORK/dns.txt" | tr -d ' ')"
  echo "reverse (PTR) queries    : $PTR_SKIPPED  (skipped by policy, counted on purpose)"
  echo "out-of-list destinations : $(wc -l <"$UNEXPECTED" | tr -d ' ')"
} >"$SUMMARY"
# 从这里开始，"死了没人知道"那一段就结束了：报告要打印，后面的每一步
# （数表外条数、写 --out/--json、出判决）都由自测试的退出码+条数+理由串三件证人盯着。
VERDICT="summary"
cat "$SUMMARY"
if [ -s "$UNEXPECTED" ]; then
  echo "[gate] out-of-list destinations:" >&2
  sed 's/^/    /' "$UNEXPECTED" >&2
fi

if [ -n "$OUT" ]; then
  mkdir -p "$(dirname "$OUT")"
  { cat "$SUMMARY"; echo; echo "--- all destinations ---"; cat "$WORK/dst_ip.txt"; echo "--- SNI ---"; cat "$WORK/sni.txt"; echo "--- DNS ---"; cat "$WORK/dns.txt"; } >"$OUT"
  log "wrote $OUT"
fi
if [ -n "$JSON_OUT" ]; then
  mkdir -p "$(dirname "$JSON_OUT")"
  {
    printf '{\n  "pcap": "%s",\n  "allowed_hosts": [' "$PCAP"
    first=1
    for h in "${ALLOW_HOSTS[@]}"; do
      [ $first -eq 1 ] || printf ','
      first=0; printf '"%s"' "$h"
    done
    printf '],\n  "unexpected": [\n'
    first=1
    while read -r kind val; do
      [ $first -eq 1 ] || printf ',\n'
      first=0; printf '    {"kind": "%s", "value": "%s"}' "$kind" "$val"
    done <"$UNEXPECTED"
    printf '\n  ]\n}\n'
  } >"$JSON_OUT"
  require_tool python3 && python3 -c "import json,sys; json.load(open(sys.argv[1]))" "$JSON_OUT" \
    || die 2 "JSON evidence unreadable: $JSON_OUT"
  log "wrote $JSON_OUT"
fi

if [ -s "$UNEXPECTED" ]; then
  die 1 "capture contains destinations outside the declared egress list — the zero-telemetry claim is false"
fi
ok "capture contains no destination outside the declared egress list"
