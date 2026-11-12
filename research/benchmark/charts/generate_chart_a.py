# -*- coding: utf-8 -*-
"""图表 A 生成脚本：D1-D7 方向覆盖条图（枚举去重回填口径）。

- 数据全部来自 dataset-final-reconciliation.csv（仓库内 research/benchmark/dataset-final-reconciliation.csv，
  nine_steps/8_d1_d7 行 + d1_d7 节），由本脚本程序化解析，脚本内零硬编码数字；
  解析失败即报错退出，不回退、不补数。
- Y 轴从 0 起（不截断坐标轴）；D4 程序化覆盖为 0，以基线标记 + 0 标签画出。
- 纯 Python 标准库，零第三方依赖。

复跑：python -I generate_chart_a.py
输出：本脚本同目录 chart-A-d1d7.svg
"""

import csv
import re
import sys
from pathlib import Path

SCRIPT_PATH = Path(__file__).resolve()
CSV_PATH = SCRIPT_PATH.parent.parent / "dataset-final-reconciliation.csv"
OUT_PATH = SCRIPT_PATH.parent / "chart-A-d1d7.svg"
DATA_CUTOFF = "2026-11-12"


def fail(msg):
    print(f"[FATAL] {msg}", file=sys.stderr)
    sys.exit(1)


def read_csv():
    if not CSV_PATH.is_file():
        fail(f"源文件不存在: {CSV_PATH}")
    with open(CSV_PATH, encoding="utf-8") as f:
        return list(csv.DictReader(f))


def parse_nine_steps(rows):
    """从 nine_steps/8_d1_d7 行解析各方向去重回填数字（唯一权威来源）。"""
    val = None
    for r in rows:
        if r["section"] == "nine_steps" and r["item"] == "8_d1_d7":
            # 紧凑回填数字位于该行的 version_label 列（value 列为 PASS），两列合并搜索
            val = f"{r['value']} {r['version_label']}"
            break
    if val is None:
        fail("未找到 nine_steps/8_d1_d7 行")
    pat = {
        "D1": r"D1 枚举 (\d+)",
        "D2": r"D2 (\d+)",
        "D3": r"D3 C14 (\d+)\+C8 (\d+)",
        "D4": r"D4 程序化 (\d+)",
        "D5": r"D5 (\d+)",
        "D6": r"D6 C10 (\d+)",
        "D7": r"D7 (\d+)",
    }
    out = {}
    for k, p in pat.items():
        m = re.search(p, val)
        if not m:
            fail(f"nine_steps/8_d1_d7 解析失败: {k} / 模式 {p}")
        out[k] = tuple(int(x) for x in m.groups())
    return out


def parse_d1_d7_rows(rows):
    """从 d1_d7 节逐方向解析 claimed 与去重回填，用于交叉核对与注释。"""
    res = {}
    for r in rows:
        if r["section"] != "d1_d7":
            continue
        m = re.match(r"(D[1-7]) ", r["item"])
        if not m:
            continue
        key = m.group(1)
        val = r["value"]
        claimed_m = re.match(r"claimed=([^|]+)\|", val)
        backfill_m = re.search(r"去重回填=(.+)$", val)
        if not claimed_m or not backfill_m:
            fail(f"d1_d7 行解析失败: {r['item']}")
        res[key] = {"claimed_raw": claimed_m.group(1).strip(),
                    "backfill_raw": backfill_m.group(1).strip(),
                    "full": val}
    if set(res) != {"D1", "D2", "D3", "D4", "D5", "D6", "D7"}:
        fail(f"d1_d7 节方向不齐: {sorted(res)}")
    return res


def cross_check(ns, detail):
    """d1_d7 节回填值与 nine_steps 权威行逐方向核对，任何不一致即失败。"""
    checks = [
        ("D1", r"去重回填=(\d+)", ns["D1"]),
        ("D2", r"去重回填=(\d+)", ns["D2"]),
        ("D3", r"C14 枚举 (\d+) / C8 枚举 (\d+)", ns["D3"]),
        ("D4", r"去重回填=程序化 (\d+)", ns["D4"]),
        ("D5", r"去重回填=(\d+)", ns["D5"]),
        ("D6", r"C10 枚举 (\d+)", ns["D6"]),
        ("D7", r"去重回填=(\d+)", ns["D7"]),
    ]
    for key, pat, expected in checks:
        m = re.search(pat, detail[key]["full"])
        if not m:
            fail(f"交叉核对解析失败: {key} / 模式 {pat}")
        got = tuple(int(x) for x in m.groups())
        if got != expected:
            fail(f"交叉核对不一致: {key} nine_steps={expected} vs d1_d7={got}")


def extract_annotations(detail):
    """从 d1_d7 行提取 claimed 数字用于脚注出入说明（解析失败即退出）。"""
    ann = {}
    m = re.search(r"claimed=(\d+)", detail["D1"]["full"])
    if not m:
        fail("D1 claimed 解析失败")
    ann["D1_claimed"] = int(m.group(1))
    m = re.search(r"claimed=~(\d+)\(C14\)\+(\d+)\(C8\)", detail["D3"]["full"])
    if not m:
        fail("D3 claimed 解析失败")
    ann["D3_claimed_C14"], ann["D3_claimed_C8"] = int(m.group(1)), int(m.group(2))
    m = re.search(r"claimed=0 programmatic / (\d+) judge-only", detail["D4"]["full"])
    if not m:
        fail("D4 claimed(judge-only) 解析失败")
    ann["D4_judge_only"] = int(m.group(1))
    m = re.search(r"C8 claimed (\d+)（枚举 (\d+)", detail["D6"]["backfill_raw"])
    if not m:
        fail("D6 C8 claimed/枚举 解析失败")
    ann["D6_C8_claimed"], ann["D6_C8_enum"] = int(m.group(1)), int(m.group(2))
    return ann


# ---------- SVG 生成 ----------

W, H = 960, 600
ML, MR, MT, MB = 80, 24, 96, 160          # 绘图区边距（MT=96 为标题/副标题/顶部数值标签留出净空）
PLOT_W, PLOT_H = W - ML - MR, H - MT - MB  # 856 x 360
Y_MAX = 200                                # y 轴上限 = 最大数据值 200，从 0 起，不截断
BAR_W = 64

C_MAIN = "#4e79a7"   # 常规条
C_C14 = "#4e79a7"    # D3 底段 C14
C_C8 = "#f28e2b"     # D3 顶段 C8
C_GRID = "#d9d9d9"
C_AXIS = "#333333"
C_TEXT = "#333333"
C_NOTE = "#555555"

DIRS = [
    ("D1", "长程事实保持"),
    ("D2", "记忆提取写入"),
    ("D3", "记忆更新纠错"),
    ("D4", "检索相关性"),
    ("D5", "未知不确定性"),
    ("D6", "进行中事项追踪"),
    ("D7", "上下文预算/输出契约"),
]


def esc(s):
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def y_of(v):
    return MT + PLOT_H - (v / Y_MAX) * PLOT_H


def build_svg(ns, ann):
    e = []
    e.append('<?xml version="1.0" encoding="UTF-8"?>')
    e.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
             f'viewBox="0 0 {W} {H}" font-family="Microsoft YaHei, SimHei, sans-serif">')
    e.append(f'<rect width="{W}" height="{H}" fill="#ffffff"/>')

    # 标题 / 副标题（副标题两行左对齐，避免超宽）
    e.append(f'<text x="{W/2:.0f}" y="32" font-size="20" font-weight="bold" '
             f'fill="{C_TEXT}" text-anchor="middle">图表 A：D1–D7 方向覆盖条图（枚举去重回填口径，按 Episode 去重）</text>')
    e.append(f'<text x="24" y="54" font-size="12" fill="{C_NOTE}">'
             f'数据源：research/benchmark/dataset-final-reconciliation.csv（nine_steps·8_d1_d7 与 d1_d7 节）· 数据截至 {DATA_CUTOFF}</text>')
    e.append(f'<text x="24" y="70" font-size="12" fill="{C_NOTE}">'
             f'各方向去重数可重叠、不相加；唯一池 = GOLD 覆盖 299</text>')

    # 图例（D3 分段；置于绘图区上方中部空白带，避开两端 200 柱的数值标签）
    lx = 400
    e.append(f'<rect x="{lx}" y="74" width="12" height="12" fill="{C_C14}"/>')
    e.append(f'<text x="{lx+18}" y="84" font-size="11" fill="{C_TEXT}">D3·C14 枚举 {ns["D3"][0]}</text>')
    e.append(f'<rect x="{lx+130}" y="74" width="12" height="12" fill="{C_C8}"/>')
    e.append(f'<text x="{lx+148}" y="84" font-size="11" fill="{C_TEXT}">D3·C8 枚举 {ns["D3"][1]}</text>')

    # 网格线 + y 刻度（0 起每 25 单位；标签每 50 单位）
    for k in range(Y_MAX // 25 + 1):
        v = k * 25
        y = y_of(v)
        e.append(f'<line x1="{ML}" y1="{y:.1f}" x2="{W-MR}" y2="{y:.1f}" '
                 f'stroke="{"#bbbbbb" if v % 50 == 0 else C_GRID}" stroke-width="1"/>')
        if v % 50 == 0:
            e.append(f'<text x="{ML-8}" y="{y+4:.1f}" font-size="12" fill="{C_TEXT}" text-anchor="end">{v}</text>')

    # y 轴标题（旋转）
    yx, yc = 26, MT + PLOT_H / 2
    e.append(f'<text x="{yx}" y="{yc:.0f}" font-size="12" fill="{C_TEXT}" text-anchor="middle" '
             f'transform="rotate(-90 {yx} {yc:.0f})">覆盖 Episode 数（按 Episode 去重）</text>')

    # 坐标轴线
    e.append(f'<line x1="{ML}" y1="{MT}" x2="{ML}" y2="{MT+PLOT_H}" stroke="{C_AXIS}" stroke-width="1.5"/>')
    e.append(f'<line x1="{ML}" y1="{MT+PLOT_H}" x2="{W-MR}" y2="{MT+PLOT_H}" stroke="{C_AXIS}" stroke-width="1.5"/>')

    # 条形
    slot = PLOT_W / len(DIRS)
    for i, (d, name) in enumerate(DIRS):
        cx = ML + slot * (i + 0.5)
        x = cx - BAR_W / 2
        if d == "D3":
            v14, v8 = ns["D3"]
            h14, h8 = v14 / Y_MAX * PLOT_H, v8 / Y_MAX * PLOT_H
            y14 = MT + PLOT_H - h14
            y8 = y14 - h8
            e.append(f'<rect x="{x:.1f}" y="{y14:.1f}" width="{BAR_W}" height="{h14:.1f}" fill="{C_C14}"/>')
            e.append(f'<rect x="{x:.1f}" y="{y8:.1f}" width="{BAR_W}" height="{h8:.1f}" fill="{C_C8}"/>')
            e.append(f'<text x="{cx:.1f}" y="{y14 + h14/2 + 4:.1f}" font-size="12" font-weight="bold" '
                     f'fill="#ffffff" text-anchor="middle">C14 {v14}</text>')
            e.append(f'<text x="{cx:.1f}" y="{y8 + h8/2 + 4:.1f}" font-size="12" font-weight="bold" '
                     f'fill="#ffffff" text-anchor="middle">C8 {v8}</text>')
        elif ns[d][0] == 0:
            # D4 程序化覆盖为 0：以基线方块标记 + 0 标签画出
            e.append(f'<rect x="{cx-4:.1f}" y="{MT+PLOT_H-4:.1f}" width="8" height="8" fill="{C_AXIS}"/>')
            e.append(f'<text x="{cx:.1f}" y="{MT+PLOT_H-12:.1f}" font-size="13" font-weight="bold" '
                     f'fill="{C_AXIS}" text-anchor="middle">0</text>')
        else:
            v = ns[d][0]
            h = v / Y_MAX * PLOT_H
            y = MT + PLOT_H - h
            e.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{BAR_W}" height="{h:.1f}" fill="{C_MAIN}"/>')
            e.append(f'<text x="{cx:.1f}" y="{y-6:.1f}" font-size="13" font-weight="bold" '
                     f'fill="{C_TEXT}" text-anchor="middle">{v}</text>')

        # x 轴标签（方向号 + 方向名）
        e.append(f'<text x="{cx:.1f}" y="{MT+PLOT_H+22}" font-size="13" font-weight="bold" '
                 f'fill="{C_TEXT}" text-anchor="middle">{d}</text>')
        e.append(f'<text x="{cx:.1f}" y="{MT+PLOT_H+40}" font-size="10.5" '
                 f'fill="{C_TEXT}" text-anchor="middle">{esc(name)}</text>')

    # 脚注（口径与出入登记，全部来自对账 CSV）
    fy = H - MB + 62
    notes = [
        f'① D4 检索相关性：程序化覆盖 0 为最大缺口；judge-only {ann["D4_judge_only"]} 为人工评判口径，单列不计入本图程序化覆盖。',
        f'② 口径出入（见对账 CSV discrepancies 节）：D1 claimed {ann["D1_claimed"]} vs 枚举去重回填 {ns["D1"][0]}（X1）；'
        f'D3 C14 claimed ~{ann["D3_claimed_C14"]} / C8 claimed {ann["D3_claimed_C8"]} vs 枚举 {ns["D3"][0]}/{ns["D3"][1]}（X2/X3）。',
        f'③ D6 的 C8 信号（claimed {ann["D6_C8_claimed"]} / 枚举 {ann["D6_C8_enum"]}）计入 D3，不在 D6 重复计；D7 与 D2 同池（{ns["D7"][0]}），不代表独立新增。',
        f'④ 本图 Y 轴自 0 起；各方向样本可重叠，禁止将柱高相加；唯一池 = GOLD 覆盖 299（STATISTICS.md §三.3）。',
    ]
    for i, t in enumerate(notes):
        e.append(f'<text x="{ML-56}" y="{fy + i*18}" font-size="11" fill="{C_NOTE}">{esc(t)}</text>')

    # 源与脚本信息
    e.append(f'<text x="{ML-56}" y="{fy + 4*18 + 6}" font-size="10.5" fill="#888888">'
             f'脚本：research/benchmark/charts/generate_chart_a.py（纯标准库，可复跑：python -I research/benchmark/charts/generate_chart_a.py）· 数字程序化解析自源 CSV，脚本内零硬编码</text>')
    e.append('</svg>')
    return "\n".join(e) + "\n"


def main():
    rows = read_csv()
    ns = parse_nine_steps(rows)
    detail = parse_d1_d7_rows(rows)
    cross_check(ns, detail)
    ann = extract_annotations(detail)

    svg = build_svg(ns, ann)
    OUT_PATH.write_text(svg, encoding="utf-8")

    # 自检：输出可被 XML 解析器完整读回
    import xml.etree.ElementTree as ET
    ET.parse(OUT_PATH)

    print(f"[OK] D1={ns['D1']} D2={ns['D2']} D3(C14+C8)={ns['D3']} D4={ns['D4']} "
          f"D5={ns['D5']} D6={ns['D6']} D7={ns['D7']}")
    print(f"[OK] 交叉核对通过（nine_steps vs d1_d7 节）")
    print(f"[OK] 写出 {OUT_PATH}")


if __name__ == "__main__":
    main()
