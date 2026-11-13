# -*- coding: utf-8 -*-
"""S52 出图 v1.1：读 summary.json（v1.1 终版 schema；缺则回退 summary-partial.json 做渲染验证），
产出 user-value-results.svg —— H/N 两组 × 六槽（M1–M5 + E5）L vs B1 分组条形图。

v1.1 口径（scoring-templates-v1.1.md / protocol-v1.1-addendum.md §2/§3）：
- 指标更名 E1–E5 → M1–M5 + E5（E5 更名"盲评回复偏好/自然度与可发送性代理评分"）；
- E5 裁决五选一：tie / both_unqualified / insufficient_info 全部单列、不计入胜率分母；
- M4（原 E4）三维 0–3 分开评，主判＝三维全合格率，仅 N 瀹（H 线槽位如实标注不适用）；
- M5 六子指标（图中主条＝子指标1 首选可用率，其余子指标进底注）；
- E5-N v2 回填块臂名 L2/B1（npair2 地图）：渲染与 E5-H（L/B1）同构，L 键缺时回退 L2 键，底注按块内实际臂名显示。
纪律：缺字段画「待真实数值」占位，不画假数据；旧口径 E4 anchor==2 率不冒充 M4 主判；
字号（P2 移动端可读性修复，1740 宽画布，README width=720 与手机缩放后可读）：
标题 34 / 副题 25 / 口径行与底注 24 / 组题 28 / 轴刻度 28 / 图例 28 / 指标名 24 / 指标副题 17 /
条形数值 20（上限：E5 三位布局+同高等值对 "100%/100%" 需中心距≥53px，槽宽 131.7px 几何封顶）/
占位 14（槽宽硬约束上限，v1.1 重评落盘后占位消失）；底注按 ｜；，断行、行长≤68 字符。
≤3 色系（蓝/橙/灰+中性黑白）；图中标注两线模型身份；无路径脚注。
零外部 API、零 LLM、纯标准库。
"""
import json
import os
import sys

AGG = os.path.dirname(os.path.abspath(__file__))

# ---- 版式常量（仅此处调参） ----
W = 1740
X0, X1 = 110, 1690          # 绘图区左右
Y0, Y1 = 175, 580           # 绘图区上（100%）下（0% 基线）
SLOT = (X1 - X0) / 12.0     # 12 个指标槽（H×6 + N×6：M1–M5 + E5）
BAR_W, BAR_GAP = 30, 25    # 间距 8→25：条形数值 20px 时同高相邻对（如 100%/100%）中心距需≥53px 不重叠
C_L, C_B1, C_TIE = "#4472C4", "#ED7D31", "#A6A6A6"   # 三个色系
C_TEXT, C_GRID, C_PEND = "#333333", "#DDDDDD", "#999999"
FONT = "'Microsoft YaHei','PingFang SC','Noto Sans CJK SC',sans-serif"

METRICS = ["M1", "M2", "M3", "M4", "M5", "E5"]
METRIC_SUB = {
    "M1": "长程事实正确使用",
    "M2": "纠正与旧事抑制",
    "M3": "未确认事实与隐私",
    "M4": "情境与关系目标",
    "M5": "完整多候选价值",
    "E5": "盲评回复偏好",
}
MAIN_KEYS = {  # 每臂 rec 下主判率的候选嵌套键（依 PLOT-V11-NOTES.md schema）
    "M1": ("primary_rate", "fact_use"),
    "M2": ("pair_pass", "pair", "m_pair"),
    "M3": ("primary_pass", "primary"),
    "M4": ("all_three_qualified", "main"),
    "M5": ("usable_primary", "primary_usable"),
}


def esc(s):
    return (str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def text(x, y, s, size, weight="normal", anchor="middle", fill=C_TEXT):
    return ('<text x="%.1f" y="%.1f" font-family="%s" font-size="%d" font-weight="%s" '
            'text-anchor="%s" fill="%s">%s</text>'
            % (x, y, FONT, size, weight, anchor, fill, esc(s)))


def yv(v):
    """百分比 -> y 坐标。"""
    return Y1 - (v / 100.0) * (Y1 - Y0)


def fmt_pct(v):
    return ("%g" % round(v, 1)) + "%"


def bar(x, v, color):
    if v > 0:
        rect = ('<rect x="%.1f" y="%.1f" width="%d" height="%.1f" fill="%s"/>'
                % (x, yv(v), BAR_W, Y1 - yv(v), color))
    else:
        rect = ""
    return rect + text(x + BAR_W / 2.0, yv(v) - 6, fmt_pct(v), 20)


def placeholder(cx, line1, line2):
    w, h = 118, 66
    x, y = cx - w / 2.0, Y1 - h - 14   # 8→14：抬高 6px，避开 E5 平局 0% 标签（20px）的墨迹区
    return ('<rect x="%.1f" y="%.1f" width="%d" height="%d" fill="none" stroke="%s" '
            'stroke-dasharray="6,4"/>' % (x, y, w, h, C_PEND)
            + text(cx, y + 27, line1, 14, fill=C_PEND)
            + text(cx, y + 50, line2, 14, fill=C_PEND))


def slot_bars(slot_i, values):
    """values: [(rate%, color)]，居中排布。"""
    n = len(values)
    total = n * BAR_W + (n - 1) * BAR_GAP
    x = X0 + slot_i * SLOT + (SLOT - total) / 2.0
    out = []
    for v, color in values:
        out.append(bar(x, v, color))
        x += BAR_W + BAR_GAP
    return "".join(out)


# ---- 数据读取（容错：缺字段返回 None，不造数） ----

def rate_from(rec):
    """{'num','den','rate'} / 数值 / 平铺 → rate%（无据 → None）。"""
    if isinstance(rec, (int, float)) and not isinstance(rec, bool):
        return float(rec)
    if not isinstance(rec, dict):
        return None
    r = rec.get("rate")
    if isinstance(r, (int, float)) and not isinstance(r, bool):
        return float(r)
    num, den = rec.get("num"), rec.get("den")
    if (isinstance(num, (int, float)) and isinstance(den, (int, float))
            and not isinstance(num, bool) and den):
        return num * 100.0 / den
    return None


def get_block(s, m, line):
    """v1.1 终版 metrics.<m>.<line> 优先；兼容平铺 <m>_<line>（含 v1.0 E5_H/E5_N）。"""
    metrics = s.get("metrics")
    if isinstance(metrics, dict):
        b = (metrics.get(m) or {}).get(line)
        if b is not None:
            return b
    return s.get("%s_%s" % (m, line))


def block_pending(b):
    return isinstance(b, dict) and bool(b.get("pending") or b.get("pending_semantic_review"))


def arm_rate(m, b, arm):
    """M1–M5 主判率（%）：b[arm] 直挂 rate/num-den → MAIN_KEYS 嵌套 → 平铺 <arm>_rate。"""
    if not isinstance(b, dict):
        return None
    rec = b.get(arm)
    if isinstance(rec, dict):
        r = rate_from(rec)
        if r is not None:
            return r
        for k in MAIN_KEYS[m]:
            r = rate_from(rec.get(k))
            if r is not None:
                return r
            r = rate_from(rec.get("%s_rate" % k))
            if r is not None:
                return r
    elif isinstance(rec, (int, float)) and not isinstance(rec, bool):
        return float(rec)
    for k in ("%s_rate" % arm, "%s_pass_rate" % arm):
        v = b.get(k)
        if isinstance(v, (int, float)) and not isinstance(v, bool):
            return float(v)
    return None


def arm_nd(m, b, arm):
    """分子/分母字符串（在册者），否则 None。"""
    if not isinstance(b, dict):
        return None
    rec = b.get(arm)
    if isinstance(rec, dict):
        if (isinstance(rec.get("num"), (int, float)) and isinstance(rec.get("den"), (int, float))):
            return "%g/%g" % (rec["num"], rec["den"])
        for k in MAIN_KEYS[m]:
            r = rec.get(k)
            if isinstance(r, dict) and isinstance(r.get("num"), (int, float)) \
                    and isinstance(r.get("den"), (int, float)):
                return "%g/%g" % (r["num"], r["den"])
    return None


def e5_parts(b):
    """E5 → (L 胜率%, B1 胜率%, 平局率% 或 None)；全部缺 → None。
    E5-N v2 臂名 L2（npair2 地图 L2/B1）与 E5-H 臂名 L 同角色，L 键缺时回退 L2 键。"""
    if not isinstance(b, dict):
        return None
    lw, bw = b.get("L_wins"), b.get("B1_wins")
    if not isinstance(lw, (int, float)):
        lw = b.get("L2_wins")   # E5-N v2 胜数键
    den_ok = (isinstance(lw, (int, float)) and isinstance(bw, (int, float))
              and (lw + bw) > 0)
    lr = b.get("L_win_rate")
    if not isinstance(lr, (int, float)):
        lr = b.get("L2_win_rate")
    if not isinstance(lr, (int, float)) and den_ok:
        lr = lw * 100.0 / (lw + bw)
    br = b.get("B1_win_rate")
    if not isinstance(br, (int, float)) and den_ok:
        br = bw * 100.0 / (lw + bw)
    ties, n = b.get("ties"), b.get("n")
    tr = b.get("tie_rate")
    if not isinstance(tr, (int, float)) and isinstance(ties, (int, float)) \
            and isinstance(n, (int, float)) and n > 0:
        tr = ties * 100.0 / n
    if lr is None and br is None and tr is None:
        return None
    return (lr, br, tr)


# ---- 底注组装（动态，只报在册数值） ----

def wrap_cap(s, limit=68):
    # 断行点 = ｜（分段符）与 ；，（句读），标点一律保留在片段尾；行内按原字符序拼接（不改原文），
    # 仅当累计超 limit 时在断点处换行。大字号底注（24px）超 112 字符行宽的回填文案必需。
    if len(s) <= limit:
        return [s]
    parts, buf = [], ""
    for ch in s:
        buf += ch
        if ch in "｜；，":
            parts.append(buf)
            buf = ""
    if buf:
        parts.append(buf)
    lines, cur = [], ""
    for p in parts:
        cand = cur + p
        if len(cand) > limit and cur:
            lines.append(cur)
            cur = p
        else:
            cur = cand
    if cur:
        lines.append(cur)
    return lines


def e5_single_caption(s):
    segs = []
    for line in ("H", "N"):
        b = get_block(s, "E5", line)
        if not isinstance(b, dict):
            continue

        def fmt(k, _b=b):
            v = _b.get(k)
            return ("%g" % v) if isinstance(v, (int, float)) and not isinstance(v, bool) else "未登记"

        bits = ["平局 %s" % fmt("ties"), "双不合格 %s" % fmt("both_unqualified"),
                "信息不足 %s" % fmt("insufficient_info")]
        n = b.get("n")
        if isinstance(n, (int, float)) and not isinstance(n, bool):
            bits.append("n=%g" % n)
        segs.append("%s 线 %s" % (line, "，".join(bits)))
    if not segs:
        return None
    return "E5 非胜负单列（不计入胜率分母）：" + " ｜ ".join(segs)


def e5_aux_caption(s):
    segs = []
    for line in ("H", "N"):
        b = get_block(s, "E5", line)
        if not isinstance(b, dict):
            continue
        nat = b.get("naturalness_mean_by_arm")
        if not isinstance(nat, dict):
            continue
        la = "L" if isinstance(nat.get("L"), (int, float)) else "L2"  # E5-N v2 臂名
        nl, nb = nat.get(la), nat.get("B1")
        if not (isinstance(nl, (int, float)) and isinstance(nb, (int, float))):
            continue
        bits = ["自然度均值(1–5) %s %g / B1 %g" % (la, nl, nb)]
        send = b.get("sendability_ge2_rate_by_arm")
        if isinstance(send, dict) and isinstance(send.get(la), (int, float)) \
                and isinstance(send.get("B1"), (int, float)):
            bits.append("可发送性≥2率 %s %g%% / B1 %g%%" % (la, send[la], send["B1"]))
        snd = b.get("sendability_mean_by_arm")
        if isinstance(snd, dict) and isinstance(snd.get(la), (int, float)) \
                and isinstance(snd.get("B1"), (int, float)):
            bits.append("可发送性均值(0–3) %s %g / B1 %g" % (la, snd[la], snd["B1"]))
        segs.append("%s 线 %s" % (line, "，".join(bits)))
    if not segs:
        return None
    return "E5 辅助（代理评，1–5 自然度与 0–3 可发送性独立口径）：" + " ｜ ".join(segs)


def m4_dims_caption(s):
    b = get_block(s, "M4", "N")
    if not isinstance(b, dict):
        return None
    segs = []
    for dim, cn in (("intent", "意图"), ("boundary", "分寸"), ("naturalness", "自然度")):
        parts = []
        for arm in ("L", "B1"):
            rec = b.get(arm)
            if not isinstance(rec, dict):
                continue
            d = rec.get("dims") or rec.get("dimensions") or {}
            dd = d.get(dim) if isinstance(d, dict) else None
            r = rate_from(dd.get("excellent_rate") if isinstance(dd, dict) else None)
            if r is not None:
                parts.append("%s %s" % (arm, fmt_pct(r)))
        if parts:
            segs.append("%s %s" % (cn, "/".join(parts)))
    if not segs:
        return None
    return "M4 三维优秀率分列（N 线，各维 0–3 中 3 分占比）：" + " ｜ ".join(segs)


def m5_caption(s):
    segs = []
    for line in ("H", "N"):
        b = get_block(s, "M5", line)
        if not isinstance(b, dict):
            continue
        bits = []
        one = []
        for arm in ("L", "B1"):
            rec = b.get(arm)
            r = rate_from(rec.get("at_least_one_usable")) if isinstance(rec, dict) else None
            if r is not None:
                one.append("%s %s" % (arm, fmt_pct(r)))
        if one:
            bits.append("至少一条可用率（子指标2）%s" % "/".join(one))
        cov = []
        for arm in ("L", "B1"):
            rec = b.get(arm)
            if not isinstance(rec, dict):
                continue
            r = None
            for k in ("direction_coverage", "valid_direction_coverage"):
                r = rate_from(rec.get(k))
                if r is not None:
                    break
            if r is not None:
                cov.append("%s %s" % (arm, fmt_pct(r)))
        if cov:
            bits.append("有效方向覆盖（子指标3）%s" % "/".join(cov))
        gp = b.get("group_pref")
        if isinstance(gp, dict):
            lw, bw = gp.get("L_group_wins"), gp.get("B1_group_wins")
            r = gp.get("L_group_pref_rate")
            if not isinstance(r, (int, float)) and isinstance(lw, (int, float)) \
                    and isinstance(bw, (int, float)) and (lw + bw) > 0:
                r = lw * 100.0 / (lw + bw)
            if isinstance(r, (int, float)):
                gbits = ["机制组偏好 %s" % fmt_pct(r)]
                for k, cn in (("tie", "平局"), ("both_unqualified", "双不合格"),
                              ("insufficient_info", "信息不足")):
                    v = gp.get(k)
                    if isinstance(v, (int, float)) and not isinstance(v, bool):
                        gbits.append("%s %g" % (cn, v))
                bits.append("组级盲评偏好（子指标6）%s" % "，".join(gbits))
        if bits:
            segs.append("%s 线 %s" % (line, "；".join(bits)))
    if not segs:
        return None
    return "M5 其余子指标（子指标1 首选可用率见图；子指标4/5 越低越好）：" + " ｜ ".join(segs)


def counts_caption(s):
    segs = []
    for m in ("M1", "M2", "M3", "M4", "M5"):
        line_bits = []
        for line in ("H", "N"):
            b = get_block(s, m, line)
            if not isinstance(b, dict):
                continue
            bits = []
            for arm in ("L", "B1"):
                nd, r = arm_nd(m, b, arm), arm_rate(m, b, arm)
                if nd and r is not None:
                    bits.append("%s %s（%s）" % (arm, nd, fmt_pct(r)))
            if bits:
                line_bits.append("%s %s" % (line, "，".join(bits)))
        if line_bits:
            segs.append("%s %s" % (m, " ｜ ".join(line_bits)))
    if not segs:
        return None
    return "分子/分母（在册者）：" + "；".join(segs)


def main():
    final = os.path.join(AGG, "summary.json")
    using_final = os.path.exists(final)
    src = final if using_final else os.path.join(AGG, "summary-partial.json")
    with open(src, encoding="utf-8") as f:
        s = json.load(f)

    hpend_legacy = bool((s.get("H_line_E1_E3") or {}).get("pending_semantic_review"))

    HTOK = "@@H@@"  # 高度占位（底注行数确定后统一替换）
    svg = ['<?xml version="1.0" encoding="UTF-8"?>',
           '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%s" viewBox="0 0 %d %s">'
           % (W, HTOK, W, HTOK),
           '<rect width="%d" height="%s" fill="#FFFFFF"/>' % (W, HTOK),
           text(W / 2.0, 44, "User-Value v1.1：L vs B1 分指标结果（M1–M5 + E5）", 34, "bold"),
           text(W / 2.0, 76, "N 线 = 原生子代理 ｜ H 线 = deepseek-flash-2 历史复评"
                             "（CURRENT/DIRECT 已归一化为 L/B1）", 25, "bold"),
           text(W / 2.0, 104, "E5 胜率分母＝非平局对数（tie/双不合格/信息不足单列不计分母）；"
                              "M4 仅 N 线、主判＝三维全合格率；M5 主条＝子指标1 首选可用率", 24, fill="#666666")]

    # 网格与 y 轴刻度
    for g in range(0, 101, 25):
        yy = yv(g)
        svg.append('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="%s" stroke-width="1"/>'
                   % (X0, yy, X1, yy, C_GRID))
        svg.append(text(X0 - 12, yy + 5, "%d%%" % g, 28, anchor="end"))
    svg.append('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="%s" stroke-width="2"/>'
               % (X0, Y1, X1, Y1, C_TEXT))

    # 组标题（两线模型身份）
    svg.append(text(X0 + 3 * SLOT, 140, "H 线 · deepseek-flash-2 历史复评", 28, "bold"))
    svg.append(text(X0 + 9 * SLOT, 140, "N 线 · 原生子代理", 28, "bold"))

    # 逐指标槽
    for gi, line in enumerate(("H", "N")):
        for mi, m in enumerate(METRICS):
            si = gi * 6 + mi
            cx = X0 + si * SLOT + SLOT / 2.0
            svg.append(text(cx, Y1 + 30, m, 24, "bold"))
            svg.append(text(cx, Y1 + 52, METRIC_SUB[m], 17))
            b = get_block(s, m, line)
            if m == "E5":
                parts = e5_parts(b)
                if parts is None:
                    svg.append(placeholder(cx, "待真实数值", "（E5 数值未落盘）"))
                    continue
                lr, br, tr = parts
                vals = [(lr, C_L), (br, C_B1)]
                if tr is not None:
                    vals.append((tr, C_TIE))   # 平局灰条单列
                svg.append(slot_bars(si, vals))
                continue
            if m == "M4" and line == "H" and b is None:
                svg.append(placeholder(cx, "本轮不适用", "（M4 仅 N 线）"))
                continue
            lr = arm_rate(m, b, "L")
            br = arm_rate(m, b, "B1")
            if lr is None and br is None:
                if block_pending(b) or (line == "H" and m in ("M1", "M2", "M3") and hpend_legacy):
                    svg.append(placeholder(cx, "待语义复核", "（v1.1 重评未落盘）"))
                else:
                    svg.append(placeholder(cx, "待真实数值", "（v1.1 重评未落盘）"))
                continue
            vals = [(v, c) for v, c in ((lr, C_L), (br, C_B1)) if v is not None]
            svg.append(slot_bars(si, vals))

    # 组分隔竖线
    svg.append('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="%s" stroke-width="1"/>'
               % (X0 + 6 * SLOT, Y0 - 15, X0 + 6 * SLOT, Y1, C_PEND))

    # 图例（28px 大字号放不下右上角，移至底注上方独立一行，居中排布）
    lx, ly = 613, 672
    for i, (c, name) in enumerate([(C_L, "L 臂"), (C_B1, "B1 臂"),
                                   (C_TIE, "非胜负单列（仅 E5）")]):
        x = lx + i * 110
        svg.append('<rect x="%.1f" y="%.1f" width="24" height="24" fill="%s"/>' % (x, ly - 22, c))
        svg.append(text(x + 30, ly, name, 28, anchor="start"))

    # 底注（无路径；动态只报在册数值）
    m_metrics = ("M1", "M2", "M3", "M4", "M5")
    have_pairs = [(m, ln) for m in m_metrics for ln in ("H", "N")
                  if arm_rate(m, get_block(s, m, ln), "L") is not None
                  or arm_rate(m, get_block(s, m, ln), "B1") is not None]
    have_m = []
    for m, _ln in have_pairs:
        if m not in have_m:
            have_m.append(m)
    complete = all((m, ln) in have_pairs for m in ("M1", "M2", "M3", "M5") for ln in ("H", "N")) \
        and ("M4", "N") in have_pairs   # M4 仅 N 线，H 槽位按设计不适用
    if complete:
        st = "状态：M1–M5 全部为 v1.1 对称重评数值（M4 仅 N 线）。"
    elif have_m:
        st = ("状态：%s 为 v1.1 对称重评数值，其余指标待落盘（占位「待真实数值」）。"
              % "、".join(have_m))
    else:
        st = "状态：M1–M5 主判数值待 v1.1 对称重评落盘（占位「待真实数值」，不画假数据）。"
    if not using_final:
        st += "E5 数值沿用既有部分汇总（v1.0 判分口径，按 addendum §3 对称重评后更新）。"

    caps = ["口径：全部比率为 L vs B1 同尺对称口径；新尺（v1.1）与旧尺（v1.0）禁止纵向相减。",
            e5_single_caption(s), e5_aux_caption(s), m4_dims_caption(s),
            m5_caption(s), counts_caption(s), st,
            "声明：E5 全部结果为原生子代理盲评代理评分，不是真人用户发送率。"]
    cap_lines = []
    for c in caps:
        if c:
            cap_lines.extend(wrap_cap(c))
    y = 716
    for ln in cap_lines:
        svg.append(text(W / 2.0, y, ln, 24, fill="#666666"))
        y += 38

    height = max(860, y + 14)

    svg.append("</svg>")   # S71 修复：v1.0（plot_results.py:152）有根元素闭合标签，v1.1 升级时遗漏致 XML premature end of data

    out_path = os.path.join(AGG, "user-value-results.svg")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(svg).replace(HTOK, str(height)))
    print("SVG written:", out_path, "| source:", os.path.basename(src),
          "| v1.1 schema metrics with data:", len(have_pairs), "/10")
    return 0


if __name__ == "__main__":
    sys.exit(main())
