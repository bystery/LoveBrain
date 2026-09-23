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
#        [--out dist/network-egress.txt] [--json dist/network-egress.json]
#
#   bash scripts/verify_network_egress.sh --capture-on-device \
#        [--serial <adb-serial>] [--duration 120]
#
# Exit codes:
#   0  抓包存在且所有外连目的地都在白名单内
#   1  发现表外目的地（即潜在遥测/后端）
#   2  工具或输入缺失（tshark/pcap/设备）——这是"没能验证"，不是"验证通过"
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/lib/gate_lib.sh"

PCAP=""
CAPTURE=0
DURATION=120
SERIAL=""
OUT=""
JSON_OUT=""
ALLOW_HOSTS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --pcap) PCAP="$2"; shift 2 ;;
    --capture-on-device) CAPTURE=1; shift ;;
    --duration) DURATION="$2"; shift 2 ;;
    --serial) SERIAL="$2"; shift 2 ;;
    --allow-host) ALLOW_HOSTS+=("$2"); shift 2 ;;
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
  if ! have tshark; then
    rm -f "$err"
    die_unverified "tshark is not installed — $what cannot be analysed, so the zero-telemetry claim stays UNVERIFIED"
  fi
  if ! tshark -r "$PCAP" "$@" >"$out" 2>"$err"; then
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
die_unverified() { printf '%s  CANNOT-VERIFY %s
' "$GATE_LOG_PREFIX" "$*" >&2; exit 2; }
have adb || die_unverified "adb not on PATH; cannot produce or pull a capture"

if [ "$CAPTURE" -eq 1 ]; then
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

have tshark || die_unverified "tshark not installed; cannot parse the capture"

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
  local raw="$1" sep="$2" out="$3"
  tr "$sep" '\n' <"$raw" | sed 's/:[0-9]*$//' | grep -v '^[[:space:]]*$' | sort -u >"$out"
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
for h in "${ALLOW_HOSTS[@]}"; do
  if have getent; then
    getent hosts "$h" | awk '{print $1}' >>"$ALLOW_IPS"
  fi
  if have python3; then
    python3 - "$h" <<'PY' >>"$ALLOW_IPS"
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
while read -r ip; do
  [ -n "$ip" ] || continue
  case "$ip" in
    10.*|127.*|172.1[6-9].*|172.2[0-9].*|172.3[01].*|192.168.*|169.254.*|fe80:*|::1|2000::*|*) continue ;;
  esac
  grep -qx "$ip" "$ALLOW_IPS" || printf 'ip\t%s\n' "$ip" >>"$UNEXPECTED"
done <"$WORK/dst_ip.txt"
while read -r name; do
  [ -n "$name" ] || continue
  ok=0
  for h in "${ALLOW_HOSTS[@]}"; do
    [ "$name" = "$h" ] || case "$name" in *".$h") ok=1 ;; esac
    [ "$ok" = 1 ] && break
  done
  [ "$ok" = 1 ] || printf 'sni\t%s\n' "$name" >>"$UNEXPECTED"
done <"$WORK/sni.txt"
while read -r name; do
  [ -n "$name" ] || continue
  case "$name" in *.arpa|*) continue ;; esac
  ok=0
  for h in "${ALLOW_HOSTS[@]}"; do
    [ "$name" = "$h" ] || case "$name" in *".$h") ok=1 ;; esac
    [ "$ok" = 1 ] && break
  done
  [ "$ok" = 1 ] || printf 'dns\t%s\n' "$name" >>"$UNEXPECTED"
done <"$WORK/dns.txt"

SUMMARY="$WORK/summary.txt"
{
  echo "pcap                     : $PCAP"
  echo "allowed hosts            : ${ALLOW_HOSTS[*]}"
  echo "distinct destination IPs : $(wc -l <"$WORK/dst_ip.txt" | tr -d ' ')"
  echo "distinct TLS SNI         : $(wc -l <"$WORK/sni.txt" | tr -d ' ')"
  echo "distinct DNS queries     : $(wc -l <"$WORK/dns.txt" | tr -d ' ')"
  echo "out-of-list destinations : $(wc -l <"$UNEXPECTED" | tr -d ' ')"
} >"$SUMMARY"
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
