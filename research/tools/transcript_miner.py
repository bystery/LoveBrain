#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
User Transcript & Experience Miner (tools/transcript_miner.py)
专为自进化执行 AI 设计的本地资产挖掘工具：
从海量（13MB+）本地对话记录与 ZCode CLI 日志中精准提取用户的痛点、调教过程与真实吐槽，
避免大文件加载爆内存或上下文污染，为每轮架构演进提供最真实的「问题 DNA」。
"""

import os
import sys
import json
import glob
from pathlib import Path

# 修复 Windows 控制台 GBK 乱码
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

TRANSCRIPT_TARGETS = {
    "antigravity_usage": r"<LOCAL_USER_DIR>\.gemini\antigravity\brain\9b98c18e-a8f5-4af0-9936-31d3cb74c4b0\.system_generated\logs\transcript.jsonl",
    "antigravity_bottlenecks": r"<LOCAL_USER_DIR>\.gemini\antigravity\brain\d1f99f89-8be7-448d-9e95-806d38b68055\.system_generated\logs\transcript.jsonl",
    "antigravity_research_start": r"<LOCAL_USER_DIR>\.gemini\antigravity\brain\be02c422-69f1-4038-8c9b-9132c647c4ef\.system_generated\logs\transcript.jsonl",
    "zcode_logs_pattern": r"<LOCAL_USER_DIR>\.zcode\cli\log\zcode-*.jsonl"
}

def mine_antigravity_transcript(file_path: str, keyword: str = None, max_results: int = 20):
    if not os.path.exists(file_path):
        return []

    results = []
    line_no = 0
    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            line_no += 1
            if not line.strip():
                continue
            try:
                data = json.loads(line)
            except Exception:
                continue

            msg_type = data.get("type", "")
            content = data.get("content", "")

            # 重点捕获用户原话 (USER_INPUT)
            if msg_type == "USER_INPUT":
                if not keyword or (keyword.lower() in content.lower()):
                    # 清理 metadata 包装
                    clean_content = content.replace("<USER_REQUEST>", "").replace("</USER_REQUEST>", "").strip()
                    results.append({
                        "line": line_no,
                        "timestamp": data.get("created_at", ""),
                        "type": "USER_INPUT",
                        "content": clean_content
                    })
                    if len(results) >= max_results:
                        break
            elif keyword and keyword.lower() in content.lower():
                results.append({
                    "line": line_no,
                    "timestamp": data.get("created_at", ""),
                    "type": msg_type,
                    "content": content[:200] + "..." if len(content) > 200 else content
                })
                if len(results) >= max_results:
                    break

    return results

def mine_zcode_logs(pattern: str, keyword: str = None, max_results: int = 15):
    files = sorted(glob.glob(pattern), reverse=True)
    results = []
    for fpath in files[:3]:
        with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
            for l_idx, line in enumerate(f):
                if not line.strip():
                    continue
                if keyword and (keyword.lower() not in line.lower()):
                    continue
                try:
                    item = json.loads(line)
                    results.append({
                        "file": os.path.basename(fpath),
                        "line": l_idx + 1,
                        "data": item
                    })
                except Exception:
                    results.append({
                        "file": os.path.basename(fpath),
                        "line": l_idx + 1,
                        "text": line[:200]
                    })
                if len(results) >= max_results:
                    return results
    return results

if __name__ == "__main__":
    kw = sys.argv[1] if len(sys.argv) > 1 else None
    print("=" * 65)
    print(f" [TRANSCRIPT MINER] 正在检索本地历史真实对话记录 (Keyword: {kw or 'ALL_USER_REQUESTS'})...")
    print("=" * 65)

    for name, p in TRANSCRIPT_TARGETS.items():
        if "zcode" in name:
            continue
        print(f"\n>>> 对话源: {name} ({p})")
        extracted = mine_antigravity_transcript(p, keyword=kw, max_results=10)
        if not extracted:
            print("  (未找到匹配记录)")
        for item in extracted:
            print(f"  [L{item['line']}] [{item['timestamp']}] {item['content'][:120]}")

    print("\n" + "=" * 65)
    print(" 检索完毕。执行 AI 可将上述真实痛点直接映射为架构变异方案！")
