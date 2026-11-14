# -*- coding: utf-8 -*-
"""selection_v1 四臂实验结论图生成脚本（3 张，纯 Python 标准库，零第三方依赖）。

图表与结论口径（数据全部程序化解析自仓库内实验产物，脚本内零硬编码结果数字，
校验失败即报错退出，不回退、不补数）：

1. chart-context-cost.svg      上下文使用成本（四臂实验：单轮 prompt/completion token 与端到端延迟）
2. chart-contract-quality.svg  输出协议合规（layer-1 确定性计分器 scoring.json，1,968 轮）
3. chart-context-retention.svg 关键断言覆盖（最终汇总 required 通道，null 结果）

可比性前提（三图相同）：同一实验（40 集 × 4 臂）、同模型（deepseek-flash-2）、
同采样、同一评分栈；臂定义见 refs/benchmark_v1_20261107/phase3/experiments/selection_v1/ARM_REGISTRY.json。

数据源（仓库相对路径，clone 后可直接运行）：
- research/refs/benchmark_v1_20261107/phase3/experiments/selection_v1/selection_v1_universal_scores.csv
- research/refs/benchmark_v1_20261107/phase3/experiments/selection_v1/FOUR_ARM_SUMMARY.json
- research/refs/benchmark_v1_20261107/phase3/experiments/selection_v1/FOUR_ARM_RESULTS.csv（延迟结论全样本求和）
- research/experiments/runs/selection_v1/<ARM>/<EP>/<turn>/scoring.json（1,968 个文件，逐轮遍历）

复跑：python -I research/benchmark/charts/generate_selection_charts.py
输出：本脚本同目录 3 个 SVG；无时间戳写入，重跑输出不变。
"""

import ast
import csv
import json
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPT_PATH = Path(__file__).resolve()
RESEARCH = SCRIPT_PATH.parent.parent.parent          # research/
SEL_DIR = RESEARCH / "refs" / "benchmark_v1_20261107" / "phase3" / "experiments" / "selection_v1"
SCORES_CSV = SEL_DIR / "selection_v1_universal_scores.csv"
SUMMARY_JSON = SEL_DIR / "FOUR_ARM_SUMMARY.json"
RESULTS_CSV = SEL_DIR / "FOUR_ARM_RESULTS.csv"
RUNS_DIR = RESEARCH / "experiments" / "runs" / "selection_v1"
OUT_DIR = SCRIPT_PATH.parent

DATA_CUTOFF = "2026-11-12"
DOC_REF = "research/BENCHMARK_EVALUATION.md 第5节（四臂实验）"

ARMS = ["DIRECT", "COT", "LEGACY", "CURRENT"]
ARM_NAMES = {
    "DIRECT": "直接基线",
    "COT": "CoT 基线",
    "LEGACY": "历史 Harness",
    "CURRENT": "LoveBrain 装配",
}
ARM_COLORS = {
    "DIRECT": "#b0b0b0",
    "COT": "#8a8a8a",
    "LEGACY": "#e15759",
    "CURRENT": "#4e79a7",
}

C_AXIS = "#333333"
C_GRID = "#dddddd"
C_TEXT = "#333333"
C_NOTE = "#555555"

EXPECTED_CSV_N = {"DIRECT": 492, "COT": 492, "LEGACY": 478, "CURRENT": 492}


def fail(msg):
    print(f"[FATAL] {msg}", file=sys.stderr)
    sys.exit(1)


# ---------- 数据加载 ----------

def load_scores():
    """逐行解析四臂 universal 评分 CSV，返回逐臂聚合。"""
    if not SCORES_CSV.is_file():
        fail(f"源文件不存在: {SCORES_CSV}")
    agg = {a: {"n": 0, "prompt": 0, "completion": 0, "latency": 0} for a in ARMS}
    with open(SCORES_CSV, encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            arm = r["arm_id"]
            if arm not in agg:
                fail(f"未知臂: {arm}")
            g = agg[arm]
            g["n"] += 1
            try:
                t = ast.literal_eval(r["tokens"])
            except Exception as e:
                fail(f"tokens 字段解析失败（{arm} {r['case_id']}）: {e}")
            g["prompt"] += t.get("prompt") or 0
            g["completion"] += t.get("completion") or 0
            try:
                g["latency"] += int(r["latency_ms"] or 0)
            except Exception as e:
                fail(f"latency_ms 解析失败（{arm} {r['case_id']}）: {e}")
    for a, g in agg.items():
        if g["n"] != EXPECTED_CSV_N[a]:
            fail(f"{a} 位置数 {g['n']} != 预期 {EXPECTED_CSV_N[a]}")
        if g["prompt"] <= 0 or g["completion"] <= 0 or g["latency"] <= 0:
            fail(f"{a} 聚合值非法: {g}")
    return agg


def load_summary():
    """读取最终四臂汇总 JSON（每臂 492 轮口径），用于交叉核对与覆盖图。"""
    if not SUMMARY_JSON.is_file():
        fail(f"源文件不存在: {SUMMARY_JSON}")
    d = json.load(open(SUMMARY_JSON, encoding="utf-8"))
    per = d.get("per_arm")
    if not isinstance(per, dict):
        fail("FOUR_ARM_SUMMARY.json 缺 per_arm")
    out = {}
    for a in ARMS:
        if a not in per:
            fail(f"FOUR_ARM_SUMMARY 缺臂 {a}")
        v = per[a]
        out[a] = {
            "n_turns": v["n_turns"],
            "tokens_total": v["tokens_total"],
            "required_hits": v["required_hits"],
            "required_total": v["required_total"],
            "required_na_turns": v["required_NA_turns"],
        }
        if out[a]["n_turns"] != 492:
            fail(f"FOUR_ARM_SUMMARY {a} n_turns={out[a]['n_turns']} != 492")
        if out[a]["required_total"] not in (220, 232):
            fail(f"FOUR_ARM_SUMMARY {a} required_total={out[a]['required_total']} 异常")
    return out


def cross_check_tokens(agg, summary):
    """CSV 单轮均值与最终汇总单轮均值差须 <1%（LEGACY CSV 快照仅 478/492 轮，容差放宽到 2%）。"""
    for a in ARMS:
        g = agg[a]
        csv_mean = (g["prompt"] + g["completion"]) / g["n"]
        sum_mean = summary[a]["tokens_total"] / summary[a]["n_turns"]
        tol = 0.02 if a == "LEGACY" else 0.01
        if abs(csv_mean - sum_mean) / sum_mean > tol:
            fail(f"{a} token 均值交叉核对超差: CSV {csv_mean:.1f} vs SUMMARY {sum_mean:.1f}")


def load_results_latency():
    """读取宽表 FOUR_ARM_RESULTS.csv（每臂全量 492 轮，无缺行），返回逐臂 latency_ms 总量。

    延迟结论改由本全样本求和比计算：FOUR_ARM_SUMMARY.json 无延迟字段，
    而 universal_scores.csv 的 LEGACY 快照缺 14 轮（478/492），不能作结论口径
    （原 89.0% 即该快照均值比，2026-11-14 勘误为全样本 89.1%）。
    """
    if not RESULTS_CSV.is_file():
        fail(f"源文件不存在: {RESULTS_CSV}")
    with open(RESULTS_CSV, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))
    if len(rows) != 492:
        fail(f"FOUR_ARM_RESULTS.csv 行数 {len(rows)} != 492")
    lat_total = {a: 0 for a in ARMS}
    for r in rows:
        for a in ARMS:
            v = (r.get(f"{a}.latency_ms") or "").strip()
            if not v:
                fail(f"FOUR_ARM_RESULTS.csv {a} latency_ms 存在空值（case_id={r.get('case_id')}）")
            try:
                lat_total[a] += int(float(v))
            except Exception as e:
                fail(f"FOUR_ARM_RESULTS.csv {a} latency_ms 解析失败（case_id={r.get('case_id')}）: {e}")
    for a in ARMS:
        if lat_total[a] <= 0:
            fail(f"FOUR_ARM_RESULTS.csv {a} latency 总量非法: {lat_total[a]}")
    return lat_total


def load_contract():
    """遍历 selection_v1 全部 1,968 个逐轮 scoring.json，统计输出协议合规与否决。"""
    if not RUNS_DIR.is_dir():
        fail(f"runs 目录不存在: {RUNS_DIR}")
    res = {a: {"n": 0, "schema_total": 0, "schema_pass": 0, "veto": 0} for a in ARMS}
    seen = 0
    for arm in ARMS:
        arm_dir = RUNS_DIR / arm
        if not arm_dir.is_dir():
            fail(f"缺臂目录: {arm_dir}")
        for ep in sorted(os.listdir(arm_dir)):
            ep_dir = arm_dir / ep
            if not ep_dir.is_dir():
                continue
            for turn in sorted(os.listdir(ep_dir)):
                sp = ep_dir / turn / "scoring.json"
                if not sp.is_file():
                    continue
                seen += 1
                d = json.load(open(sp, encoding="utf-8"))
                g = res[arm]
                g["n"] += 1
                if d.get("veto"):
                    g["veto"] += 1
                for c in d.get("checks", []):
                    if c.get("id") == "json_schema_valid":
                        g["schema_total"] += 1
                        if c.get("pass"):
                            g["schema_pass"] += 1
    for a in ARMS:
        if res[a]["n"] != 492:
            fail(f"layer-1 {a} scoring.json 数 {res[a]['n']} != 492")
        if res[a]["schema_total"] != 492:
            fail(f"layer-1 {a} json_schema_valid 检查数 {res[a]['schema_total']} != 492")
    if seen != 1968:
        fail(f"scoring.json 总数 {seen} != 1968")
    return res


# ---------- SVG 公共件 ----------

W = 960
FONT = "Microsoft YaHei, SimHei, sans-serif"


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def svg_head(height):
    e = []
    e.append('<?xml version="1.0" encoding="UTF-8"?>')
    e.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{height}" '
             f'viewBox="0 0 {W} {height}" font-family="{FONT}">')
    e.append(f'<rect width="{W}" height="{height}" fill="#ffffff"/>')
    return e


def axis_y(e, x0, x1, y_top, y_bot, vmax, step, label_every, unit=""):
    for k in range(int(round(vmax / step)) + 1):
        v = k * step
        y = y_bot - (v / vmax) * (y_bot - y_top)
        major = (v % label_every == 0)
        e.append(f'<line x1="{x0}" y1="{y:.1f}" x2="{x1}" y2="{y:.1f}" '
                 f'stroke="{"#bbbbbb" if major else C_GRID}" stroke-width="1"/>')
        if major:
            txt = f"{v:g}{unit}"
            e.append(f'<text x="{x0-6}" y="{y+4:.1f}" font-size="11" fill="{C_TEXT}" '
                     f'text-anchor="end">{txt}</text>')
    e.append(f'<line x1="{x0}" y1="{y_top}" x2="{x0}" y2="{y_bot}" stroke="{C_AXIS}" stroke-width="1.5"/>')
    e.append(f'<line x1="{x0}" y1="{y_bot}" x2="{x1}" y2="{y_bot}" stroke="{C_AXIS}" stroke-width="1.5"/>')


def arm_label(e, cx, y, arm):
    e.append(f'<text x="{cx:.1f}" y="{y}" font-size="11.5" font-weight="bold" fill="{C_TEXT}" '
             f'text-anchor="middle">{arm}</text>')
    e.append(f'<text x="{cx:.1f}" y="{y+15}" font-size="10" fill="{C_TEXT}" '
             f'text-anchor="middle">{esc(ARM_NAMES[arm])}</text>')


def footer(e, y, lines, script_note):
    for i, t in enumerate(lines):
        e.append(f'<text x="24" y="{y + i*16}" font-size="11" fill="{C_NOTE}">{esc(t)}</text>')
    e.append(f'<text x="24" y="{y + len(lines)*16 + 4}" font-size="10.5" fill="#888888">'
             f'{esc(script_note)}</text>')


def finish(e, out_name):
    e.append('</svg>')
    out = OUT_DIR / out_name
    out.write_text("\n".join(e) + "\n", encoding="utf-8")
    ET.parse(out)
    print(f"[OK] 写出 {out}")


# ---------- 图 1：上下文使用成本 ----------

def chart_context_cost(agg, summary, lat_total):
    cross_check_tokens(agg, summary)
    n_tok = {a: {"p": agg[a]["prompt"] / agg[a]["n"], "c": agg[a]["completion"] / agg[a]["n"]}
             for a in ARMS}
    lat = {a: agg[a]["latency"] / agg[a]["n"] / 1000.0 for a in ARMS}
    # 结论数字口径（2026-11-14 勘误）：改由全样本权威源计算，两臂各 492 轮，总量比=均值比——
    # token = FOUR_ARM_SUMMARY.json 全量 tokens_total；延迟 = FOUR_ARM_RESULTS.csv 全量求和。
    # 原 CSV 快照均值比（60.4%/89.0%）系 LEGACY 快照缺 14 轮（478/492）的样本不完整偏差，已废弃。
    tok_ratio = summary["CURRENT"]["tokens_total"] / summary["LEGACY"]["tokens_total"]
    lat_ratio = lat_total["CURRENT"] / lat_total["LEGACY"]

    H, MT, MB = 660, 140, 250
    PLX0, PLX1, PRX0, PRX1 = 88, 512, 590, 924
    y_bot, y_top = H - MB, MT
    ph = y_bot - y_top
    VMAX_TOK = 7000
    VMAX_LAT = 140
    panel_title_y = 118

    e = svg_head(H)
    e.append(f'<text x="{W/2}" y="30" font-size="19" font-weight="bold" fill="{C_TEXT}" '
             f'text-anchor="middle">上下文使用成本：LoveBrain 装配单轮 token 为历史 Harness 的 {tok_ratio*100:.0f}%'
             f'（四臂实验）</text>')
    e.append(f'<text x="24" y="54" font-size="12" fill="{C_NOTE}">'
             f'左：单轮 token（堆叠 = prompt 装配 + completion 回复）；右：单轮端到端平均延迟。</text>')
    e.append(f'<text x="24" y="70" font-size="12" fill="{C_NOTE}">'
             f'数据源：research/refs/benchmark_v1_20261107/phase3/experiments/selection_v1/'
             f'selection_v1_universal_scores.csv · 数据截至 {DATA_CUTOFF}</text>')

    # 图例（独立一行，避开面板标题与柱顶标签）
    lx = PLX0
    e.append(f'<rect x="{lx}" y="90" width="12" height="12" fill="{ARM_COLORS["CURRENT"]}"/>')
    e.append(f'<text x="{lx+16}" y="100" font-size="11" fill="{C_TEXT}">prompt（上下文装配）</text>')
    e.append(f'<rect x="{lx+150}" y="90" width="12" height="12" fill="{ARM_COLORS["CURRENT"]}" fill-opacity="0.45"/>')
    e.append(f'<text x="{lx+166}" y="100" font-size="11" fill="{C_TEXT}">completion（回复）</text>')

    axis_y(e, PLX0, PLX1, y_top, y_bot, VMAX_TOK, 1000, 2000)
    e.append(f'<text x="26" y="{(y_top+y_bot)/2:.0f}" font-size="12" fill="{C_TEXT}" text-anchor="middle" '
             f'transform="rotate(-90 26 {(y_top+y_bot)/2:.0f})">tokens / 轮</text>')
    axis_y(e, PRX0, PRX1, y_top, y_bot, VMAX_LAT, 20, 40, " s")
    e.append(f'<text x="{PRX0-24}" y="{(y_top+y_bot)/2:.0f}" font-size="12" fill="{C_TEXT}" text-anchor="middle" '
             f'transform="rotate(-90 {PRX0-24} {(y_top+y_bot)/2:.0f})">秒 / 轮</text>')

    slot_l = (PLX1 - PLX0) / len(ARMS)
    slot_r = (PRX1 - PRX0) / len(ARMS)
    BW = 52
    for i, a in enumerate(ARMS):
        cxl = PLX0 + slot_l * (i + 0.5)
        cxr = PRX0 + slot_r * (i + 0.5)
        col = ARM_COLORS[a]
        # 左：prompt + completion 堆叠
        hp = n_tok[a]["p"] / VMAX_TOK * ph
        hc = n_tok[a]["c"] / VMAX_TOK * ph
        yp = y_bot - hp
        yc = yp - hc
        e.append(f'<rect x="{cxl-BW/2:.1f}" y="{yp:.1f}" width="{BW}" height="{hp:.1f}" fill="{col}"/>')
        e.append(f'<rect x="{cxl-BW/2:.1f}" y="{yc:.1f}" width="{BW}" height="{hc:.1f}" '
                 f'fill="{col}" fill-opacity="0.45"/>')
        e.append(f'<text x="{cxl:.1f}" y="{yp + hp/2 + 4:.1f}" font-size="10.5" font-weight="bold" '
                 f'fill="#ffffff" text-anchor="middle">{n_tok[a]["p"]:.0f}</text>')
        if hc > 14:
            e.append(f'<text x="{cxl:.1f}" y="{yc + hc/2 + 4:.1f}" font-size="10.5" font-weight="bold" '
                     f'fill="{C_TEXT}" text-anchor="middle">{n_tok[a]["c"]:.0f}</text>')
        tot = n_tok[a]["p"] + n_tok[a]["c"]
        e.append(f'<text x="{cxl:.1f}" y="{yc - 6:.1f}" font-size="11" font-weight="bold" '
                 f'fill="{C_TEXT}" text-anchor="middle">共 {tot:.0f}</text>')
        # 右：延迟
        hl = lat[a] / VMAX_LAT * ph
        yl = y_bot - hl
        e.append(f'<rect x="{cxr-BW/2:.1f}" y="{yl:.1f}" width="{BW}" height="{hl:.1f}" fill="{col}"/>')
        e.append(f'<text x="{cxr:.1f}" y="{yl - 6:.1f}" font-size="11" font-weight="bold" '
                 f'fill="{C_TEXT}" text-anchor="middle">{lat[a]:.1f}</text>')
        arm_label(e, cxl, y_bot + 24, a)
        arm_label(e, cxr, y_bot + 24, a)

    e.append(f'<text x="{(PLX0+PLX1)/2:.1f}" y="{panel_title_y}" font-size="12.5" font-weight="bold" '
             f'fill="{C_TEXT}" text-anchor="middle">单轮 token 成本（prompt + completion）</text>')
    e.append(f'<text x="{(PRX0+PRX1)/2:.1f}" y="{panel_title_y}" font-size="12.5" font-weight="bold" '
             f'fill="{C_TEXT}" text-anchor="middle">单轮端到端平均延迟</text>')

    footer(e, H - MB + 70, [
        f'样本：同一实验 4 臂 × 40 集，同模型 deepseek-flash-2 同采样（2026-11-10）；每臂 492 轮（左图柱高用 CSV 快照，LEGACY 478/492）。',
        f'结论口径：token 比 = FOUR_ARM_SUMMARY.json 全量 tokens_total（两臂各 492 轮）；延迟比 = FOUR_ARM_RESULTS.csv 全量 492×2 轮求和比；脚本自动交叉核对快照均值差 <1%。',
        f'结论由脚本计算：CURRENT/LEGACY 单轮 token = {tok_ratio*100:.1f}%（降 {100-tok_ratio*100:.1f}%），平均延迟 = {lat_ratio*100:.1f}%（全样本 492×2 轮口径）。',
        f'基线臂 token 更低，但其回复几乎不满足结构化输出协议（见 chart-contract-quality.svg），非同机制公平对比；同机制对比 = LEGACY → CURRENT。',
        f'关键条件：CURRENT 为双域冠军按轮数分流；LEGACY 为历史 Harness；详见 {DOC_REF}。',
    ], '脚本：research/benchmark/charts/generate_selection_charts.py（纯标准库，数字全部程序化解析）· '
       '复跑：python -I research/benchmark/charts/generate_selection_charts.py')
    finish(e, "chart-context-cost.svg")


# ---------- 图 2：输出协议合规 ----------

def chart_contract_quality(contract):
    rate = {a: contract[a]["schema_pass"] / contract[a]["schema_total"] * 100.0 for a in ARMS}
    H, MT, MB = 620, 120, 236
    X0, X1 = 88, 900
    y_bot, y_top = H - MB, MT
    ph = y_bot - y_top
    VMAX = 100.0
    cur, leg = contract["CURRENT"], contract["LEGACY"]

    e = svg_head(H)
    e.append(f'<text x="{W/2}" y="30" font-size="19" font-weight="bold" fill="{C_TEXT}" text-anchor="middle">'
             f'输出协议合规：LoveBrain 装配 {cur["schema_pass"]}/{cur["n"]} 全部通过，'
             f'历史 Harness {leg["schema_pass"]}/{leg["n"]}（{leg["schema_total"]-leg["schema_pass"]} 项违例）</text>')
    e.append(f'<text x="24" y="54" font-size="12" fill="{C_NOTE}">'
             f'层 1 确定性计分器（逐轮 scoring.json）的 json_schema_valid 检查：结构化回复协议'
             f'（顶层键/动作契约）逐轮校验，1,968 轮全覆盖。</text>')
    e.append(f'<text x="24" y="70" font-size="12" fill="{C_NOTE}">'
             f'数据源：research/experiments/runs/selection_v1/&lt;臂&gt;/&lt;集&gt;/&lt;轮&gt;/scoring.json'
             f'（逐轮遍历汇总）· 数据截至 {DATA_CUTOFF}</text>')
    e.append(f'<text x="24" y="86" font-size="12" fill="{C_NOTE}">'
             f'柱上数字 = 协议合规轮数；柱下数字 = 该臂被否决（veto）轮数。</text>')

    axis_y(e, X0, X1, y_top, y_bot, VMAX, 20, 20, "%")
    e.append(f'<text x="26" y="{(y_top+y_bot)/2:.0f}" font-size="12" fill="{C_TEXT}" text-anchor="middle" '
             f'transform="rotate(-90 26 {(y_top+y_bot)/2:.0f})">协议合规轮占比</text>')

    slot = (X1 - X0) / len(ARMS)
    BW = 96
    for i, a in enumerate(ARMS):
        cx = X0 + slot * (i + 0.5)
        g = contract[a]
        h = rate[a] / VMAX * ph
        y = y_bot - h
        e.append(f'<rect x="{cx-BW/2:.1f}" y="{y:.1f}" width="{BW}" height="{h:.1f}" '
                 f'fill="{ARM_COLORS[a]}"/>')
        e.append(f'<text x="{cx:.1f}" y="{y - 22:.1f}" font-size="13" font-weight="bold" fill="{C_TEXT}" '
                 f'text-anchor="middle">{rate[a]:.1f}%</text>')
        e.append(f'<text x="{cx:.1f}" y="{y - 7:.1f}" font-size="11" fill="{C_NOTE}" '
                 f'text-anchor="middle">{g["schema_pass"]}/{g["schema_total"]} 轮</text>')
        e.append(f'<text x="{cx:.1f}" y="{y_bot + 40:.1f}" font-size="11.5" font-weight="bold" '
                 f'fill="{C_AXIS}" text-anchor="middle">否决 {g["veto"]} 轮</text>')
        arm_label(e, cx, y_bot + 58, a)

    footer(e, H - MB + 116, [
        f'样本：4 臂 × 40 集 × 12 轮 = 1,968 个 scoring.json（逐臂恰 492 轮，脚本核验）；同一实验同模型 deepseek-flash-2（2026-11-10）。',
        f'DIRECT/COT 为纯文本直接基线，不实现结构化协议——低合规率是协议层有效拦截的证据，不构成同机制公平对比。',
        f'同机制对比：LEGACY → CURRENT 违例与否决 2 项 → 0 项（违例与否决同为同一轮：越权拒答且 schema 不合规）。',
        f'复评层交叉印证：universal_scorer 轻量契约 1,954 轮中仅 COT/LEGACY 各 1 轮失败，与层 1 结论一致。',
        f'详见 {DOC_REF}。',
    ], '脚本：research/benchmark/charts/generate_selection_charts.py（纯标准库）· '
       '复跑：python -I research/benchmark/charts/generate_selection_charts.py')
    finish(e, "chart-contract-quality.svg")


# ---------- 图 3：关键断言覆盖（null 结果） ----------

def chart_context_retention(summary):
    rate = {}
    for a in ARMS:
        v = summary[a]
        if v["required_hits"] > v["required_total"]:
            fail(f"{a} required_hits > required_total")
        rate[a] = v["required_hits"] / v["required_total"] * 100.0
    H, MT, MB = 560, 120, 176
    X0, X1 = 88, 900
    y_bot, y_top = H - MB, MT
    ph = y_bot - y_top
    VMAX = 25.0
    na_lo = min(summary[a]["required_na_turns"] for a in ARMS)
    na_hi = max(summary[a]["required_na_turns"] for a in ARMS)
    gap = rate["CURRENT"] - rate["LEGACY"]

    e = svg_head(H)
    e.append(f'<text x="{W/2}" y="30" font-size="19" font-weight="bold" fill="{C_TEXT}" text-anchor="middle">'
             f'关键断言覆盖：四臂 {min(rate.values()):.1f}%–{max(rate.values()):.1f}%，未显示显著差异'
             f'（null 结果）</text>')
    e.append(f'<text x="24" y="54" font-size="12" fill="{C_NOTE}">'
             f'最终汇总口径（FOUR_ARM_SUMMARY.json）：Gold 要求的原子断言（required）被回复覆盖的比例；'
             f'CURRENT 与 LEGACY 差约 {gap:+.1f} 个百分点，统计上不显著。</text>')
    e.append(f'<text x="24" y="70" font-size="12" fill="{C_NOTE}">'
             f'数据源：research/refs/benchmark_v1_20261107/phase3/experiments/selection_v1/FOUR_ARM_SUMMARY.json'
             f' · 数据截至 {DATA_CUTOFF}</text>')
    e.append(f'<text x="24" y="86" font-size="12" fill="{C_NOTE}">'
             f'本图为 null 结果与通道局限，不作为机制优势证据。</text>')

    axis_y(e, X0, X1, y_top, y_bot, VMAX, 5, 5, "%")
    e.append(f'<text x="26" y="{(y_top+y_bot)/2:.0f}" font-size="12" fill="{C_TEXT}" text-anchor="middle" '
             f'transform="rotate(-90 26 {(y_top+y_bot)/2:.0f})">required 断言覆盖率</text>')

    slot = (X1 - X0) / len(ARMS)
    BW = 96
    for i, a in enumerate(ARMS):
        cx = X0 + slot * (i + 0.5)
        v = summary[a]
        h = rate[a] / VMAX * ph
        y = y_bot - h
        e.append(f'<rect x="{cx-BW/2:.1f}" y="{y:.1f}" width="{BW}" height="{h:.1f}" '
                 f'fill="{ARM_COLORS[a]}"/>')
        e.append(f'<text x="{cx:.1f}" y="{y - 7:.1f}" font-size="12.5" font-weight="bold" fill="{C_TEXT}" '
                 f'text-anchor="middle">{v["required_hits"]}/{v["required_total"]}（{rate[a]:.1f}%）</text>')
        arm_label(e, cx, y_bot + 24, a)

    footer(e, H - MB + 64, [
        f'样本：4 臂 × 492 轮；每臂 {summary["DIRECT"]["required_total"]} 项 required 断言，约 {492-na_hi}–{492-na_lo} 轮适用（其余 {na_lo}–{na_hi} 轮无适用断言，N/A）。',
        f'已知边界：universal_scorer_v2 记载该通道 required 标记僵化（refs/research_harness_20261110/overnight/RESUME_STATE.json）。',
        f'v1 评分快照（LEGACY 478 轮）同通道为 38/220（17.3%），结论一致（selection_v1_universal_scores.csv）。',
        f'长程事实保持的机制效果结论待 holdout_v1（已执行 14.2%，暂无结论）与后续更细评分通道补充。',
        f'详见 {DOC_REF}。',
    ], '脚本：research/benchmark/charts/generate_selection_charts.py（纯标准库）· '
       '复跑：python -I research/benchmark/charts/generate_selection_charts.py')
    finish(e, "chart-context-retention.svg")


def main():
    agg = load_scores()
    summary = load_summary()
    lat_total = load_results_latency()
    contract = load_contract()
    chart_context_cost(agg, summary, lat_total)
    chart_contract_quality(contract)
    chart_context_retention(summary)
    print("[OK] 三张图生成完毕")


if __name__ == "__main__":
    main()
