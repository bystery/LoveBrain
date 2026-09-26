#!/usr/bin/env python3
"""Build the synthetic captures that grade scripts/verify_network_egress.sh.

P0-04 (独立复核 2026-09-24): the checker used to contain

    case "$ip" in ...|*) continue ;; esac
    case "$name" in *.arpa|*) continue ;; esac

`*` matches everything, so every destination IP and every DNS name was skipped and
the script silently degenerated into an SNI-only check — a direct-IP telemetry
endpoint would have been waved through.  Fixing the wildcards is only half of it:
without a capture that *contains* an out-of-list destination, nothing proves the
fixed branch is reachable.  These fixtures are that proof, one per judgement the
gate can make:

    allow-only.pcap   目的地全在表内            -> exit 0
    rogue-sni.pcap    表外 TLS SNI              -> exit 1 (kind=sni)
    rogue-dns.pcap    表外 DNS 查询             -> exit 1 (kind=dns)
    bare-ip.pcap      表外直连 IP，无 DNS/SNI   -> exit 1 (kind=ip)   <- 旧脚本会放行
    empty.pcap        只有全局头，零个包        -> exit 2 CANNOT-VERIFY
    bad-magic.pcap    包体合法、全局头 magic 是"校验和 pcap"变体
                                              -> exit 2 CANNOT-VERIFY（tshark 根本不开这个文件）

The byte layouts are written by hand (no scapy dependency) and use TEST-NET
documentation addresses (192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24), which is
exactly what tshark needs to see: ordinary routable IPv4 with a real DNS query and
a real TLS ClientHello.

一份"能被 tshark 打开"的 fixture 才有资格当尺。到 `78fec29` 之前这里写的是
`0xA1B2C213`（带内校验和的 pcap 变体，不是标准抓包文件），CI run 36205285000 上
`tshark: ... isn't a capture file in a format TShark understands`——五格 fixture 全废，
而第 6 格"没有 tshark"因为 needle 只写了 `CANNOT-VERIFY`，反而**因为文件读不开而蒙对**。
本机的 `--inspect` 一直是绿的，因为它和写方比的是同一个错常数。所以下面把魔数提成
命名常数，并让解析器**点名拒绝**已知的错变体；bad-magic.pcap 就是那个旧 bug 的标本。

Usage:
    python3 scripts/make_egress_fixtures.py --out build/gate-fixtures/network-egress
    python3 scripts/make_egress_fixtures.py --inspect <file.pcap>   # 自带解析器复述内容
"""

from __future__ import annotations

import argparse
import os
import struct
import sys

# 设备侧地址：RFC1918，脚本必须把它当"出不了本机"而跳过
PHONE_IP = "10.0.0.2"
PHONE_MAC = bytes.fromhex("020000000001")
GW_MAC = bytes.fromhex("020000000002")

RESOLVER_IP = "198.51.100.53"      # 允许的递归解析器
PROVIDER_IP = "203.0.113.20"       # 允许的 Provider 主机
ROUGE_IP = "198.51.100.77"         # 表外直连 IP（无 DNS、无 SNI）
ALLOW_HOST = "api.example.test"
ROGUE_SNI = "telemetry.example.test"
ROGUE_DNS = "ads.example.test"

# pcap 全局头 magic（小端写入文件）。0xA1B2C3D4 = 微秒时间戳的经典 pcap，
# 这是 libpcap / tshark / libmagic(`file`) 三家都认的那一个。
PCAP_MAGIC_US = 0xA1B2C3D4
# 点名拒绝的"看着像 pcap 但不是"的 magic。写这些名字的用途只有一个：
# 下一次再写错时，报错里要直接说出它写成了什么，而不是笼统一句 not a pcap。
# 三家来历里只有 0xA1B2C213 那条是**本机以外测到的**（CI run 36205285000 的 tshark 拒收）；
# 另两条是格式常识，用来挡住"顺手换成纳秒/pcapng"。
BAD_MAGICS = {
    0xA1B2C213: "带内校验和的 pcap 变体 —— CI 上的 tshark 实测拒收",
    0xA1B23C4D: "纳秒时间戳 pcap —— 本脚本按微秒写，别换成它",
    0x0A0D0D0A: "pcapng —— 另一种文件格式，本脚本只写 classic pcap",
}


# ── 校验和 ────────────────────────────────────────────────────────────────
def checksum(data: bytes) -> int:
    if len(data) % 2:
        data += b"\x00"
    total = sum(struct.unpack("!%dH" % (len(data) // 2), data))
    while total >> 16:
        total = (total & 0xFFFF) + (total >> 16)
    return (~total) & 0xFFFF


# ── 各层 ──────────────────────────────────────────────────────────────────
def eth(src: bytes, dst: bytes, payload: bytes) -> bytes:
    return dst + src + struct.pack("!H", 0x0800) + payload


def ipv4(src: str, dst: str, proto: int, payload: bytes) -> bytes:
    """20 字节 IPv4 头，checksum 按 RFC1071 真实计算（tshark 会标记坏校验和）。"""
    hdr = bytearray(
        struct.pack("!BBHHHBBH", 0x45, 0x00, 20 + len(payload), 0x0000, 0x4000, 64, proto, 0)
        + bytes(map(int, src.split("."))) + bytes(map(int, dst.split(".")))
    )
    hdr[10:12] = struct.pack("!H", checksum(bytes(hdr)))
    return bytes(hdr) + payload


def udp(sport: int, dport: int, src: str, dst: str, payload: bytes) -> bytes:
    hdr = struct.pack("!HHHH", sport, dport, 8 + len(payload), 0)
    pseudo = (bytes(map(int, src.split("."))) + bytes(map(int, dst.split(".")))
              + struct.pack("!BBH", 17, 0, 8 + len(payload)))
    csum = checksum(pseudo + hdr + payload) or 0xFFFF
    return struct.pack("!HHHH", sport, dport, 8 + len(payload), csum) + payload


def tcp(sport: int, dport: int, src: str, dst: str, payload: bytes, flags: int = 0x18,
        seq: int = 1000, ack: int = 0) -> bytes:
    """20 字节 TCP 头（无选项）：sport/dport/seq/ack/off+flags/window/csum/urg。"""
    fixed = struct.pack("!HHII", sport, dport, seq, ack) + struct.pack("!BB", 0x50, flags) \
        + struct.pack("!HHH", 0x7210, 0, 0)
    pseudo = (bytes(map(int, src.split("."))) + bytes(map(int, dst.split(".")))
              + struct.pack("!BBH", 6, 0, len(fixed) + len(payload)))
    csum = checksum(pseudo + fixed + payload)
    return fixed[:16] + struct.pack("!H", csum) + fixed[18:] + payload


def dns_query(name: str, qid: int = 0x1234) -> bytes:
    labels = b"".join(bytes([len(p)]) + p.encode("ascii") for p in name.split(".")) + b"\x00"
    return struct.pack("!HHHHHH", qid, 0x0100, 1, 0, 0, 0) + labels + struct.pack("!HH", 1, 1)


def tls_client_hello(sni: str) -> bytes:
    """最小但结构完整的 ClientHello：tshark 要能从中读出 extensions_server_name。"""
    host = sni.encode("ascii")
    name_entry = struct.pack("!BH", 0, len(host)) + host
    name_list = struct.pack("!H", len(name_entry)) + name_entry
    sni_ext = struct.pack("!HH", 0x0000, len(name_list)) + name_list
    supported_versions = struct.pack("!HH", 0x002B, 3) + struct.pack("!B", 1) + struct.pack("!H", 0x0304)
    sig_algs = struct.pack("!HH", 0x000D, 6) + struct.pack("!H", 4) \
        + struct.pack("!HH", 0x0403, 0x0804)
    extensions = sni_ext + supported_versions + sig_algs
    body = (struct.pack("!H", 0x0303) + (b"\x11" * 32) + b"\x00"
            + struct.pack("!H", 4) + struct.pack("!HH", 0x1301, 0x1303)
            + struct.pack("!B", 1) + b"\x00"
            + struct.pack("!H", len(extensions)) + extensions)
    handshake = struct.pack("!B", 1) + struct.pack("!I", len(body))[1:] + body
    record = struct.pack("!BHH", 0x16, 0x0301, len(handshake)) + handshake
    return record


# ── pcap ──────────────────────────────────────────────────────────────────
def to_pcap(packets: list[bytes], magic: int = PCAP_MAGIC_US) -> bytes:
    out = [struct.pack("<IHHIIII", magic, 2, 4, 0, 0, 65535, 1)]
    for i, pkt in enumerate(packets):
        out.append(struct.pack("<IIII", 1_700_000_000 + i, i * 1000, len(pkt), len(pkt)))
        out.append(pkt)
    return b"".join(out)


def packet_dns_query(name: str) -> bytes:
    d = dns_query(name)
    return eth(PHONE_MAC, GW_MAC,
               ipv4(PHONE_IP, RESOLVER_IP, 17, udp(41234, 53, PHONE_IP, RESOLVER_IP, d)))


def packet_https_hello(sni: str, dst_ip: str) -> bytes:
    p = tls_client_hello(sni)
    return eth(PHONE_MAC, GW_MAC,
               ipv4(PHONE_IP, dst_ip, 6, tcp(45678, 443, PHONE_IP, dst_ip, p)))


def packet_tcp_syn(dst_ip: str) -> bytes:
    return eth(PHONE_MAC, GW_MAC,
               ipv4(PHONE_IP, dst_ip, 6, tcp(44000, 443, PHONE_IP, dst_ip, b"", flags=0x02)))


def build(out_dir: str) -> dict[str, dict]:
    """写出 fixture；**每格期望的退出码只在 test_verify_network_egress.sh 里定义**，
    这里不放第二份"期望表"，否则两处一旦不同口径，测试就成了自证。"""
    os.makedirs(out_dir, exist_ok=True)
    fixtures: dict[str, dict] = {}

    def write(name: str, packets: list[bytes], purpose: str, magic: int = PCAP_MAGIC_US):
        path = os.path.join(out_dir, name)
        with open(path, "wb") as fh:
            fh.write(to_pcap(packets, magic))
        fixtures[name] = {"path": path, "packets": len(packets), "purpose": purpose}

    write("allow-only.pcap", [
        packet_dns_query(ALLOW_HOST),
        packet_https_hello(ALLOW_HOST, PROVIDER_IP),
    ], "目的地全在表内")
    write("rogue-sni.pcap", [
        packet_dns_query(ALLOW_HOST),
        packet_https_hello(ROGUE_SNI, PROVIDER_IP),
    ], "表外 TLS SNI")
    write("rogue-dns.pcap", [
        packet_dns_query(ALLOW_HOST),
        packet_dns_query(ROGUE_DNS),
    ], "表外 DNS 查询")
    write("bare-ip.pcap", [
        packet_tcp_syn(ROUGE_IP),
    ], "表外直连 IP，无 DNS、无 SNI（旧脚本的 |*) continue 会放行这一格）")
    write("empty.pcap", [], "只有全局头，零个包")
    # 标本：包体一个字节都不改，只把全局头写成 `78fec29` 当时那个 magic。
    # 有了它，"tshark 打不开文件"才第一次和"这台机器没装 tshark"分成两格——
    # 在那之前第 6 格因为文件读不开而**蒙对**过（见模块 docstring）。
    write("bad-magic.pcap", [packet_dns_query(ALLOW_HOST)],
          "全局头是 checksummed-pcap 变体：tshark 根本不打开它", magic=0xA1B2C213)
    return fixtures


# ── 自带解析器：在没有 tshark 的机器上复述 fixture 内容 ─────────────────────
def inspect(path: str) -> int:
    """复述 fixture 的**内容**（哪台->哪台、DNS 查了什么、SNI 是什么）。

    它**不能**证明"tshark 认这个文件"——到 `78fec29` 为止它就是这样被误用的：
    它比的常数与 to_pcap() 写的是同一个，于是写错时两边一起错，本机永远绿。
    现在魔数只有 PCAP_MAGIC_US 一个来源，且下面点名拒绝已知变体；
    "认不认"由两个外部读者判：`file`(libmagic) 在本机量，tshark 在 CI 量。
    """
    with open(path, "rb") as fh:
        blob = fh.read()
    magic, _ma, _mi, _tz, _sig, _snap, net = struct.unpack("<IHHIIII", blob[:24])
    if magic != PCAP_MAGIC_US:
        why = BAD_MAGICS.get(magic, "未知 magic")
        print("%08x: not a microsecond LINKTYPE_ETHERNET pcap —— %s（net=%d）"
              % (magic, why, net), file=sys.stderr)
        return 1
    if net != 1:
        print("LINKTYPE=%d，不是 LINKTYPE_ETHERNET(1)" % net, file=sys.stderr)
        return 1
    off, idx, found = 24, 0, []
    while off + 16 <= len(blob):
        _ts, _us, incl, _orig = struct.unpack("<IIII", blob[off:off + 16])
        pkt = blob[off + 16: off + 16 + incl]
        off += 16 + incl
        idx += 1
        if len(pkt) < 34 or pkt[12:14] != b"\x08\x00":
            continue
        ihl = (pkt[14] & 0x0F) * 4
        proto = pkt[23]
        src = ".".join(str(b) for b in pkt[26:30])
        dst = ".".join(str(b) for b in pkt[30:34])
        l4 = pkt[14 + ihl:]
        detail = "proto=%d %s -> %s" % (proto, src, dst)
        if proto == 17 and struct.unpack("!H", l4[2:4])[0] == 53:
            name, i = [], 14 + ihl + 8 + 12  # eth + ip + udp + dns header
            while pkt[i] != 0:
                n = pkt[i]
                name.append(pkt[i + 1:i + 1 + n].decode("ascii"))
                i += 1 + n
            detail += " dns-query=%s" % ".".join(name)
        if proto == 6:
            doff = (l4[12] >> 4) * 4
            payload = l4[doff:]
            if payload[:1] == b"\x16":
                hs = payload[5:]
                # 握手头(4) + client_version(2) + random(32) → session_id 长度字节
                pos = 4 + 2 + 32
                pos += 1 + hs[pos]                      # session id
                pos += 2 + struct.unpack("!H", hs[pos:pos + 2])[0]  # cipher suites
                pos += 1 + hs[pos]                      # compression
                ext_len = struct.unpack("!H", hs[pos:pos + 2])[0]
                end, pos = pos + 2 + ext_len, pos + 2
                while pos + 4 <= end:
                    etype, elen = struct.unpack("!HH", hs[pos:pos + 4])
                    if etype == 0:
                        host_len = struct.unpack("!H", hs[pos + 7:pos + 9])[0]
                        detail += " sni=%s" % hs[pos + 9:pos + 9 + host_len].decode("ascii")
                    pos += 4 + elen
        found.append(detail)
    print("%s: %d packet(s)" % (path, idx))
    for line in found:
        print("   ", line)
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", default=os.path.join("build", "gate-fixtures", "network-egress"))
    ap.add_argument("--inspect", metavar="PCAP")
    args = ap.parse_args()

    if args.inspect:
        return inspect(args.inspect)

    fixtures = build(args.out)
    for name, meta in sorted(fixtures.items()):
        print("%-18s packets=%d  %s" % (name, meta["packets"], meta["purpose"]))
    print("wrote %d fixture(s) under %s" % (len(fixtures), args.out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
