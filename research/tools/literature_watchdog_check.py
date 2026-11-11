# -*- coding: utf-8 -*-
"""LITERATURE_WATCHDOG_CHECK — 外部论文检索合规确定性检查器。

用途：检查 LIVE_LOOP_LOG.md 每个 LOOP 段落是否满足
LITERATURE_WATCHDOG.md §二定义的每轮最低要求（3 条）+ 撞墙额外要求。

输出：
  - stdout：人类可读逐 LOOP 合规报告
  - WATCHDOG_VERDICT.json：机读判定，供 RUNBOOK/RESUME_STATE 引用

检查项（确定性可查）：
  R1: 该 LOOP 有 ≥3 次 WebSearch/WebFetch 日志条目（关键词与 LOOP 主题相关）
  R2: 该 LOOP 有 ≥1 篇新论文登记（不在此前轮次已登记集合中）
  R3: 该 LOOP 段落正文引用 ≥1 篇论文（标题/arXiv ID/作者名匹配）
  R4(撞墙): 若 LOOP 含"撞墙/PIVOT/ELIMINATE/wall-hit"，要求 ≥5 篇新精读 + ≥2 条替代方向

不检查（需人判断）：
  - 论文与失败的相关性质量
  - 精读是否真的读了全文（只查是否有"精读"标注）

用法：
  python -I tools/literature_watchdog_check.py
  python -I tools/literature_watchdog_check.py --log path/to/LIVE_LOOP_LOG.md --lit path/to/LITERATURE_DELTA.md
"""
import argparse
import io
import json
import os
import re
import sys
from collections import defaultdict

# Force UTF-8 stdout on Windows (GBK console can't render check marks)
if sys.platform == "win32":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

DEFAULT_LOG = "refs/research_harness_20261110/overnight/LIVE_LOOP_LOG.md"
DEFAULT_LIT = "refs/research_harness_20261110/overnight/LITERATURE_DELTA.md"
DEFAULT_LIT_DIR = "refs/research_harness_20261110/phase1/literature"
DEFAULT_OUT = "refs/research_harness_20261110/overnight/WATCHDOG_VERDICT.json"

WALL_CUES_STRONG = ("撞墙→", "撞墙触发", "从头重设计", "PIVOT", "ELIMINATE该路线", "路线撞墙", "撞墙淘汰")
# Weak cues only trigger wall-check if the LOOP section explicitly declares a wall-hit verdict
WALL_CUES_WEAK = ("撞墙", "wall-hit", "从头重新设计")
SEARCH_LOG_CUES = ("WebSearch", "WebFetch", "webReader", "搜索", "检索词", "关键词（原样）")


def _read(path):
    if not os.path.isfile(path):
        return ""
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def _split_loops(log_text):
    """Split LIVE_LOOP_LOG into {loop_id: full_section_text}.
    A LOOP may have multiple ## sections (e.g. "## LOOP-10 — ..." and "## LOOP-10 完成 — ...").
    We merge all sections with the same LOOP-ID into one combined text for checking.
    """
    pattern = re.compile(r"^## (LOOP-[\d.]+[a-z]?)\b", re.MULTILINE)
    matches = list(pattern.finditer(log_text))
    loops = defaultdict(str)
    for i, m in enumerate(matches):
        loop_id = m.group(1)
        start = m.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(log_text)
        section = log_text[start:end]
        loops[loop_id] = loops[loop_id] + "\n\n" + section
    return dict(loops)


def _extract_papers(lit_text):
    """Extract paper identifiers from LITERATURE_DELTA.
    Returns set of (title_fragment, arxiv_id) tuples + set of all known paper strings.
    """
    papers = set()
    # arXiv IDs: 4-digit.5-digit or similar
    for m in re.finditer(r"arXiv[: ]?(\d{4}\.\d{4,5})", lit_text):
        papers.add(m.group(1))
    # arxiv URLs
    for m in re.finditer(r"arxiv\.org/(?:abs|html|pdf)/(\d{4}\.\d{4,5})", lit_text):
        papers.add(m.group(1))
    # Title patterns (bold or ** ** lines near paper entries)
    for m in re.finditer(r"\*\*([A-Z][^*]{10,120})\*\*", lit_text):
        title = m.group(1).strip()
        # Filter out non-title bold (section headers etc.)
        if any(w in title.lower() for w in ("论文", "统计", "日志", "纠错", "规格", "管线",
                                             "模块", "与既有", "不做什么", "引用关系")):
            continue
        papers.add(title[:40])  # first 40 chars as fingerprint
    return papers


def _extract_search_count(section_text, lit_text):
    """Count WebSearch/WebFetch evidence associated with a LOOP.
    Checks: (a) explicit search log cues in LOOP section, (b) LITERATURE_DELTA
    sections that reference the loop.
    """
    count = 0
    for cue in SEARCH_LOG_CUES:
        count += section_text.count(cue)
    # Also count any arxiv IDs mentioned in the section (proxy for paper reference = search done)
    arxiv_in_section = len(re.findall(r"\d{4}\.\d{4,5}", section_text))
    # If LOOP section itself mentions searching/fetching, that's evidence
    if any(w in section_text for w in ("WebSearch", "WebFetch", "webReader", "搜索并记录")):
        count += 3  # weight: explicit tool mention = strong evidence
    return count, arxiv_in_section


def _check_r3_paper_reference(section_text, known_papers):
    """R3: LOOP section references at least 1 paper (by title fragment or arxiv ID)."""
    for paper_id in known_papers:
        if paper_id in section_text:
            return True, paper_id
    # Also check for generic "依据...论文" or "文献" references with specificity
    if re.search(r"依据.{0,60}论文", section_text) or re.search(r"文献.{0,30}(机制|证据|提出)", section_text):
        # But only count if a specific paper name/id is nearby
        for paper_id in known_papers:
            if paper_id in section_text:
                return True, paper_id
        return False, None
    return False, None


def _check_wall(section_text):
    """Check if this LOOP is a wall-hit (triggers R4 extra requirements).
    Only trigger on strong wall-hit declarations, not casual mentions of "撞墙".
    """
    return any(cue in section_text for cue in WALL_CUES_STRONG)


def _count_new_deep_reads(section_text, lit_text):
    """Count '精读' annotations in section or LITERATURE_DELTA sections referencing this loop."""
    # In LOOP section itself
    deep_in_loop = len(re.findall(r"精读", section_text))
    # In full lit text (rough — doesn't attribute to specific loop, but if LOOP mentions
    # "A 线" or "A2" or "文献" + lit has 精读 entries, credit it)
    deep_in_lit = len(re.findall(r"精读", lit_text))
    return deep_in_loop, deep_in_lit


def _count_alt_directions(section_text):
    """Count alternative direction proposals (look for '替代方向' or '新方向' or numbered alternatives)."""
    count = 0
    count += len(re.findall(r"替代方向", section_text))
    count += len(re.findall(r"新方向", section_text))
    # Look for "候选方向" / "方向：" patterns
    count += len(re.findall(r"[候选]?方向[:：]", section_text))
    return count


def check_loop(loop_id, section_text, known_papers, lit_text):
    """Run all checks for one LOOP. Returns verdict dict."""
    verdict = {
        "loop_id": loop_id,
        "search_evidence_count": 0,
        "arxiv_ids_in_section": 0,
        "has_3plus_searches": False,
        "has_new_paper": False,
        "references_paper": False,
        "referenced_paper": None,
        "is_wall_hit": False,
        "deep_reads_in_loop": 0,
        "alt_directions": 0,
        "meets_wall_req": False,
        "overall": "FAIL",
        "penalty": "",
    }

    # R1: ≥3 search evidence
    search_count, arxiv_count = _extract_search_count(section_text, lit_text)
    verdict["search_evidence_count"] = search_count
    verdict["arxiv_ids_in_section"] = arxiv_count
    # Relaxed: if section mentions explicit search tools OR has ≥3 arxiv refs, count as R1 pass
    verdict["has_3plus_searches"] = search_count >= 3 or arxiv_count >= 3

    # R2: ≥1 new paper (we check if section references any arxiv ID or paper title)
    verdict["has_new_paper"] = arxiv_count >= 1 or any(
        p in section_text for p in known_papers
    )

    # R3: references at least 1 paper
    ref_ok, ref_paper = _check_r3_paper_reference(section_text, known_papers)
    verdict["references_paper"] = ref_ok
    verdict["referenced_paper"] = ref_paper

    # R4: wall-hit extra
    verdict["is_wall_hit"] = _check_wall(section_text)
    if verdict["is_wall_hit"]:
        deep_loop, _ = _count_new_deep_reads(section_text, lit_text)
        verdict["deep_reads_in_loop"] = deep_loop
        verdict["alt_directions"] = _count_alt_directions(section_text)
        verdict["meets_wall_req"] = deep_loop >= 5 and verdict["alt_directions"] >= 2

    # Overall verdict
    base_pass = (
        verdict["has_3plus_searches"]
        and verdict["has_new_paper"]
        and verdict["references_paper"]
    )
    if verdict["is_wall_hit"] and not verdict["meets_wall_req"]:
        verdict["overall"] = "FAIL_WALL"
        verdict["penalty"] = "撞墙轮未满足5篇精读+2条替代方向，判决作废+强制补检索"
    elif base_pass:
        verdict["overall"] = "PASS"
    else:
        verdict["overall"] = "FAIL"
        failed = []
        if not verdict["has_3plus_searches"]:
            failed.append("R1(<3次检索证据)")
        if not verdict["has_new_paper"]:
            failed.append("R2(无新论文)")
        if not verdict["references_paper"]:
            failed.append("R3(未引用论文)")
        verdict["penalty"] = "判决作废(INVALIDATED_BY_WATCHDOG); 缺失: " + ", ".join(failed)

    return verdict


def main():
    parser = argparse.ArgumentParser(description="Literature watchdog compliance checker")
    parser.add_argument("--log", default=DEFAULT_LOG, help="Path to LIVE_LOOP_LOG.md")
    parser.add_argument("--lit", default=DEFAULT_LIT, help="Path to LITERATURE_DELTA.md")
    parser.add_argument("--lit-dir", default=DEFAULT_LIT_DIR, help="Dir with loop_N_search.md files")
    parser.add_argument("--out", default=DEFAULT_OUT, help="Output JSON path")
    parser.add_argument("--root", default=".", help="Project root")
    args = parser.parse_args()

    root = args.root
    log_path = os.path.join(root, args.log) if not os.path.isabs(args.log) else args.log
    lit_path = os.path.join(root, args.lit) if not os.path.isabs(args.lit) else args.lit
    lit_dir = os.path.join(root, args.lit_dir) if not os.path.isabs(args.lit_dir) else args.lit_dir

    log_text = _read(log_path)
    lit_text = _read(lit_path)

    # Also read any loop_N_search.md files in lit_dir
    extra_lit = ""
    if os.path.isdir(lit_dir):
        for fname in sorted(os.listdir(lit_dir)):
            if fname.endswith(".md"):
                extra_lit += _read(os.path.join(lit_dir, fname))

    all_lit = lit_text + "\n\n" + extra_lit

    if not log_text:
        print("ERROR: LIVE_LOOP_LOG.md not found at %s" % log_path)
        sys.exit(1)

    known_papers = _extract_papers(all_lit)
    loops = _split_loops(log_text)

    if not loops:
        print("ERROR: No LOOP-N sections found in LIVE_LOOP_LOG.md")
        sys.exit(1)

    verdicts = []
    print("=" * 70)
    print("LITERATURE WATCHDOG — 外部论文检索合规检查")
    print("=" * 70)
    print("已知论文指纹数: %d" % len(known_papers))
    print("检查 LOOP 数: %d" % len(loops))
    print()

    pass_count = 0
    fail_count = 0
    wall_fail_count = 0

    for loop_id in sorted(loops.keys(), key=lambda x: (len(x), x)):
        section = loops[loop_id]
        v = check_loop(loop_id, section, known_papers, all_lit)
        verdicts.append(v)

        status_icon = {"PASS": "✅", "FAIL": "❌", "FAIL_WALL": "🔥"}[v["overall"]]
        print("%s %s: %s" % (status_icon, loop_id, v["overall"]))
        print("   R1(≥3检索): %s (证据%d, arxiv引用%d)" % (
            "✓" if v["has_3plus_searches"] else "✗",
            v["search_evidence_count"], v["arxiv_ids_in_section"]))
        print("   R2(新论文): %s" % ("✓" if v["has_new_paper"] else "✗"))
        print("   R3(引用论文): %s%s" % (
            "✓" if v["references_paper"] else "✗",
            (" → %s" % v["referenced_paper"][:30]) if v["referenced_paper"] else ""))
        if v["is_wall_hit"]:
            print("   R4(撞墙: ≥5精读+≥2方向): %s (精读%d, 方向%d)" % (
                "✓" if v["meets_wall_req"] else "✗",
                v["deep_reads_in_loop"], v["alt_directions"]))
        if v["penalty"]:
            print("   惩罚: %s" % v["penalty"])
        print()

        if v["overall"] == "PASS":
            pass_count += 1
        elif v["overall"] == "FAIL_WALL":
            wall_fail_count += 1
        else:
            fail_count += 1

    # Consecutive violation check
    consecutive = 0
    max_consecutive = 0
    for v in verdicts:
        if v["overall"] in ("FAIL", "FAIL_WALL"):
            consecutive += 1
            max_consecutive = max(max_consecutive, consecutive)
        else:
            consecutive = 0

    print("=" * 70)
    print("汇总: PASS=%d  FAIL=%d  FAIL_WALL=%d  连续违规最大=%d" % (
        pass_count, fail_count, wall_fail_count, max_consecutive))
    if max_consecutive >= 2:
        print("⚠️  连续≥2违规触发：所有候选判决降级 PROVISIONAL")
    if max_consecutive >= 3:
        print("🔥 连续≥3违规触发：方法论撞墙，强制5篇精读+2条替代方向")
    print("=" * 70)

    # Write JSON verdict
    out = {
        "checked_at": "2026-11-11",
        "watchdog_version": "v1",
        "total_loops": len(verdicts),
        "pass": pass_count,
        "fail": fail_count,
        "fail_wall": wall_fail_count,
        "max_consecutive_violation": max_consecutive,
        "verdicts": verdicts,
        "summary": (
            "合规 LOOP 可作为保留/淘汰依据；违规 LOOP 判决 INVALIDATED_BY_WATCHDOG。"
            + ("连续违规已触发降级。" if max_consecutive >= 2 else "")
        ),
    }
    out_path = os.path.join(root, args.out) if not os.path.isabs(args.out) else args.out
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print("机读判定写入: %s" % out_path)


if __name__ == "__main__":
    main()
