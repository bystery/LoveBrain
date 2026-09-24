#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
scripts/suggest_cost_baseline.py
================================

今日锦囊（SUGGEST）**真实 Provider** 成本基准的 runner。

它存在的理由（复核报告 2026-09-23 §5.2 / §8 第 1 步第 8 项）：

  > `SuggestCostBaselineTest` 明确"不发起真实网络请求"，usage/cost 是手填数据，
  > 不能证明真实请求花费。
  > 没有固定输入下的真实 Provider A/B 证据：旧版 token、当前 token、有效建议数、
  > 重复率、空泛率、单条平均成本。

JVM 单测只能证明 usage 绑定与校验逻辑，永远证明不了"一次真实请求花多少钱"。
本脚本做后者：对**冻结的** KB 夹具 + **仓库内的** prompt 资产，把同一条请求
打 N 次到用户配置的 Provider，逐条输出 machine-readable JSON。

本脚本**不会**自己编造任何 usage/费用数字：拿不到 usage 就写 null，
网络失败就记 failed 并让整体退出码非 0。`--dry-run` / `--self-test` 只构造请求、
不发流量，且输出的 usage/cost 字段一律为 null。

复刻的生产链路（逐项来源，改代码时请同步改这里）：
  system  = PromptBuilder.buildSuggestSystemPrompt()
            (app/src/main/java/com/lovebrain/app/domain/PromptBuilder.kt:102) —— suggest.md 全文
  user    = PromptBuilder.buildSuggestUserPrompt()
            (PromptBuilder.kt:806-850) —— 关系阶段 / 温度摘要(300) / 与今天相关的事项(800) /
            表达偏好(300) / 需要避开的经验(lastH1Blocks 1, 400) / 时间戳垫底，再过 3500 预算
  body    = DeepSeekRepository.buildRequestBodyWithConfig(..., AppConfig.TEMPERATURE_MAIN, stream=true)
            (app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:900-963)
  usage   = 流式最后一个带 usage 的 chunk（stream_options.include_usage=true）
  cost    = util/UsagePricer.kt（三元组计价 + 北京高峰判定 + deepseek.com 双条件）
  校验    = domain/SuggestValidator.kt（6-8 条、action 去重、ID 去重、分类白名单、<6 判 partial）

用法（真实请求，需要用户自备 Key）：
  LOVEBRAIN_BASELINE_API_KEY=sk-xxx bash scripts/suggest_cost_baseline.sh --requests 5

离线自检（不发任何请求，可在无 Key/无设备机器上跑）：
  python scripts/suggest_cost_baseline.py --self-test
  python scripts/suggest_cost_baseline.py --dry-run --out dist/suggest-baseline.dry.jsonl

退出码：0 全部请求成功；1 有请求失败或指标不可用；2 用法/环境缺失（未验证，不是通过）。
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
KB_DIR_DEFAULT = os.path.join(ROOT, "benchmarks", "suggest-baseline", "kb")
FIXTURE_LOCK_DEFAULT = os.path.join(ROOT, "benchmarks", "suggest-baseline", "FIXTURE.lock")
SUGGEST_ASSET = os.path.join(ROOT, "app", "src", "main", "assets", "engine", "suggest.md")

# AppConfig.kt —— 与生产常量逐字对齐
SUGGEST_BUDGET = 3500          # AppConfig.kt:64
TEMPERATURE_MAIN = 0.7         # AppConfig.kt:19
SUGGEST_TIMEOUT_MS = 45_000    # AppConfig.kt:15
DEFAULT_MODEL = "deepseek-v4-flash"  # AppConfig.kt:21
DEFAULT_BASE_URL = "https://api.deepseek.com"

# UsagePricer.kt:26-30 —— 元/百万 tokens，Triple(缓存命中, 缓存未命中, 输出)
PRICE_TABLE = {
    ("flash", False): (0.05, 1.5, 4.5),
    ("flash", True): (0.10, 3.0, 9.0),
    ("pro", False): (0.15, 4.5, 13.5),
    ("pro", True): (0.30, 9.0, 27.0),
}

VALID_CATEGORIES = ("现在可用", "今天可准备", "有机会再做")

# 空泛判定：生产侧没有可机读的"空泛"定义（SuggestValidator 只看 action 非空/去重/条数），
# 所以这里是**本基准自己声明的启发式**，规则全部来自 suggest.md 的「禁止」与「核心原则」
# 小节（app/src/main/assets/engine/suggest.md:「不给伪确定预测」「通用鸡汤不结合实际关系状态」
# 「每条 action 必须简短可直接理解」「不需要等对方先说特定台词」）。
# 它不是产品合同；报告里必须连同本定义一起引用。
VAGUE_PHRASES = (
    "多沟通", "多交流", "真诚一点", "真心对待", "表达在乎", "表达关心", "多陪陪", "多陪她",
    "找话题", "主动一点", "耐心一点", "理解她", "体谅", "给她空间", "保持联系", "制造惊喜",
    "注意身体", "多喝热水", "照顾好自己", "注意安全", "早点休息", "用心", "仪式感",
)
PSEUDO_CERTAINTY = ("一定会", "肯定会", "保证", "必定", "她一定", "必然")
FABRICATION_HINTS = ("我刚拍", "我拍了", "我路过看到", "我给你点了", "已经买了")


class HarnessError(Exception):
    """环境/夹具问题：属于"没能验证"，退出码 2。"""


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


CR, LF = bytes([13]), bytes([10])


def canon_lf(data: bytes) -> bytes:
    """按 LF 归一：换行不是内容（CRLF 工作树与 LF 检出必须得到同一个指纹）"""
    return data.replace(CR + LF, LF)


def sha256_file(path: str) -> str:
    """内容指纹（文本输入先按 LF 归一）。

    `.gitattributes` 是 `* text=auto`：同一份夹具/资产在 Windows 工作树里是 CRLF、
    在 Linux 检出里是 LF。跟着原始字节算的锁只能在一台机器上对上——锦囊 self-test 在
    CI 上就是这么红的。换行不是内容，所以摘要前归一。
    """
    with open(path, "rb") as fh:
        return sha256_bytes(canon_lf(fh.read()))


def read_text(path: str, what: str) -> str:
    if not os.path.isfile(path):
        raise HarnessError(f"missing {what}: {path}")
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


# ── 夹具冻结：FIXTURE.lock ───────────────────────────────────────────────────

def fixture_files(kb_dir: str) -> list:
    names = ["kb.json", "understand/warmth.md", "understand/style.md",
             "moment/plan.md", "memory/lessons.md"]
    out = []
    for n in names:
        p = os.path.join(kb_dir, n)
        if not os.path.isfile(p):
            raise HarnessError(f"fixture file missing: {p} — the frozen KB fixture is incomplete")
        out.append((n, p))
    return out


def build_fixture_manifest(kb_dir: str, asset: str) -> str:
    lines = ["# benchmarks/suggest-baseline/FIXTURE.lock —— 冻结输入的内容指纹",
             "# 由 scripts/suggest_cost_baseline.py --write-lock 生成；任何一字节变化都会让基准跑分失去可比性",
             "suggest_md_sha256=%s" % sha256_file(asset)]
    for name, path in fixture_files(kb_dir):
        lines.append("%s=%s" % (name, sha256_file(path)))
    return "\n".join(lines) + "\n"


def verify_fixture_lock(kb_dir: str, asset: str, lock_path: str) -> list:
    if not os.path.isfile(lock_path):
        raise HarnessError(f"fixture lock missing: {lock_path} — run --write-lock once and commit it")
    expected = {}
    with open(lock_path, "r", encoding="utf-8") as fh:
        for raw in fh:
            raw = raw.strip()
            if not raw or raw.startswith("#"):
                continue
            if "=" not in raw:
                raise HarnessError(f"malformed lock line in {lock_path}: {raw}")
            k, v = raw.split("=", 1)
            expected[k.strip()] = v.strip()
    drift = []
    actual = {"suggest_md_sha256": sha256_file(asset)}
    for name, path in fixture_files(kb_dir):
        actual[name] = sha256_file(path)
    for key, want in expected.items():
        got = actual.get(key)
        if got is None:
            drift.append("%s: present in the lock but not in the tree" % key)
        elif got != want:
            drift.append("%s: locked=%s current=%s" % (key, want, got))
    for key, got in actual.items():
        if key not in expected:
            drift.append("%s: in the tree but not pinned in the lock (locked=<absent> current=%s)" % (key, got))
    return drift


# ── prompt 组装（复刻 PromptBuilder） ────────────────────────────────────────

def last_h1_blocks(content: str, count: int) -> str:
    """PromptBuilder.lastH1Blocks (PromptBuilder.kt:1094-1098)。"""
    blocks = [b.strip() for b in re.split(r"(?<=\n)(?=# )", content) if b.strip().startswith("# ")]
    if len(blocks) <= count:
        return "\n\n".join(blocks)
    return "…（更早的已省略）\n\n" + "\n\n".join(blocks[-count:])


def trim_to_entry_boundary(text: str, max_length: int) -> str:
    """PromptBuilder.trimToEntryBoundary (PromptBuilder.kt:901-912)。"""
    if len(text) <= max_length:
        return text
    kept = []
    used = 0
    for line in text.split("\n"):
        add = len(line) + (1 if kept else 0)
        if used + add > max_length:
            break
        kept.append(line)
        used += add
    return "\n".join(kept)


def ongoing_section(plan_md: str, limit: int = 3) -> str:
    """moment/plan.md 的「## 进行中」条目 → 生产注入格式 `name | status | chain`。

    生产侧由 OngoingContextSelector.selectForInjection 挑选（会按本轮消息相关性过滤）；
    锦囊基准没有"本轮消息"，所以这里按 PromptBuilder.buildSuggestUserPrompt 的注释
    「与今天相关的有效事项最多 3 条」取前 limit 条格式正确的行。这是**已知差异**，
    写在 BENCHMARK.md 的基准定义里，不当成完全等价。
    """
    section = plan_md.split("## 已结束")[0]
    items = []
    for line in section.split("\n"):
        line = line.strip()
        if line.count("|") < 2:
            continue
        name, status = line.split("|")[0].strip(), line.split("|")[1].strip()
        if not name or status not in ("新出现", "进行中", "已完成", "已取消"):
            continue
        items.append(line)
        if len(items) >= limit:
            break
    return "\n".join(items)


def timestamp_prompt(now: datetime) -> str:
    """PromptBuilder.buildTimestampPrompt (PromptBuilder.kt:775-778) + TimeFmt PATTERN。"""
    t = now.strftime("%Y-%m-%d %H:%M")
    return ("【当前时间】%s\n所有回复必须基于上述当前时间进行时段判断，禁止臆测。"
            "回复需自然贴合当前时段。时间只认系统给定的当前时间，不凭对话内容或主观感觉推测。" % t)


def build_suggest_user_prompt(kb_dir: str, now: datetime) -> str:
    """复刻 PromptBuilder.buildSuggestUserPrompt（PromptBuilder.kt:806-850）。"""
    kb = json.loads(read_text(os.path.join(kb_dir, "kb.json"), "KB metadata"))
    stage = (kb.get("stage") or "").strip()
    warmth = read_text(os.path.join(kb_dir, "understand", "warmth.md"), "warmth fixture")
    style = read_text(os.path.join(kb_dir, "understand", "style.md"), "style fixture")
    plan = read_text(os.path.join(kb_dir, "moment", "plan.md"), "plan fixture")
    lessons = read_text(os.path.join(kb_dir, "memory", "lessons.md"), "lessons fixture")

    parts = []
    if stage and stage not in ("待确定", "阶段未确定"):
        parts.append("## 关系阶段\n%s\n\n" % stage)
    if warmth.strip():
        parts.append("## 温度摘要\n%s\n\n" % warmth.strip()[:300])
    plan_section = ongoing_section(plan)
    if plan_section.strip():
        parts.append("## 与今天相关的事项\n%s\n\n" % plan_section[:800])
    if style.strip():
        parts.append("## 表达偏好\n%s\n\n" % style.strip()[:300])
    lessons_tail = last_h1_blocks(lessons, 1)
    if lessons_tail.strip():
        parts.append("## 需要避开的经验\n%s\n\n" % lessons_tail.strip()[:400])
    raw = "".join(parts) + timestamp_prompt(now)
    return trim_suggest_budget(raw)


def trim_suggest_budget(text: str) -> str:
    """PromptBuilder.trimSuggestToBudget（PromptBuilder.kt:855-898），预算 3500。"""
    if len(text) <= SUGGEST_BUDGET:
        return text
    sections = re.split(r"(?=^## )", text, flags=re.MULTILINE)
    start = 1 if sections and not sections[0].startswith("## ") else 0
    prefix = sections[0] if start == 1 else ""
    remaining = SUGGEST_BUDGET - len(prefix)
    body = sections[start:]
    high = ("## 与今天相关的事项", "## 需要避开的经验")
    low = ("## 温度摘要", "## 表达偏好", "## 关系阶段")
    out = [prefix] if prefix else []
    for prio in (True, False, None):
        for sec in body:
            is_high = sec.startswith(high)
            is_low = sec.startswith(low)
            if prio is True and not is_high:
                continue
            if prio is False and not is_low:
                continue
            if prio is None and (is_high or is_low):
                continue
            if remaining <= 0:
                break
            add = trim_to_entry_boundary(sec, remaining)
            out.append(add)
            remaining -= len(add)
    return "".join(out)


# ── 计价（复刻 UsagePricer） ─────────────────────────────────────────────────

def price_tier(model: str) -> str:
    s = (model or "").lower()
    if "flash" in s:
        return "flash"
    if "pro" in s:
        return "pro"
    return "unknown"


def is_peak_beijing(dt_utc: datetime) -> bool:
    """UsagePricer.isPeakHourBeijing：北京周一~周五 [9,12) ∪ [14,18)。"""
    beijing = dt_utc.astimezone(timezone(timedelta(hours=8)))
    if beijing.weekday() >= 5:
        return False
    minutes = beijing.hour * 60 + beijing.minute
    return (9 * 60 <= minutes < 12 * 60) or (14 * 60 <= minutes < 18 * 60)


def compute_cost(cache_hit, cache_miss, output, model, base_url, dt_utc, overrides=None):
    """UsagePricer.costYuan + shouldBill（util/UsagePricer.kt:53-80）。

    返回 (cost|None, billed:bool, reason:str)。非 deepseek 域名或不带缓存字段时
    生产侧不计费（返回 0），本基准不把 0 当成"免费"，而是标 billed=False，
    并要求操作者用 --price-hit/--price-miss/--price-out 显式给出该 Provider 的价表。
    """
    tier = price_tier(model)
    has_cache_fields = cache_hit is not None and cache_miss is not None
    if overrides:
        hit, miss, out = overrides
        if None in (hit, miss, out):
            return None, False, "partial price override"
        if None in (cache_hit, cache_miss) or output is None:
            return None, False, "usage missing cache/output tokens"
        return (cache_hit * hit + cache_miss * miss + output * out) / 1e6, True, "operator price table"
    if "deepseek.com" not in (base_url or ""):
        return None, False, "base url is not deepseek.com — pass --price-hit/--price-miss/--price-out"
    if not has_cache_fields:
        return None, False, "usage carries no prompt_cache_hit/miss fields"
    if tier == "unknown":
        return 0.0, False, "model matches neither flash nor pro (UsagePricer: UNKNOWN tier is not billed)"
    hit, miss, out = PRICE_TABLE[(tier, is_peak_beijing(dt_utc))]
    return (cache_hit * hit + (cache_miss if cache_miss is not None else 0) * miss +
            (output or 0) * out) / 1e6, True, "UsagePricer table (%s, peak=%s)" % (
        tier, is_peak_beijing(dt_utc))


# ── 响应指标（复刻 SuggestValidator + 本基准声明的 valid/dup/vague 口径） ─────

def extract_json_block(raw: str):
    """util/Jsons.extractJsonBlock 的等价实现：```json 围栏优先，其次首个 {...}。"""
    m = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", raw, re.DOTALL)
    if m:
        return m.group(1)
    start = raw.find("{")
    if start < 0:
        return None
    depth = 0
    for i in range(start, len(raw)):
        if raw[i] == "{":
            depth += 1
        elif raw[i] == "}":
            depth -= 1
            if depth == 0:
                return raw[start:i + 1]
    return None


def score_tips(raw_text: str) -> dict:
    parsed_json = extract_json_block(raw_text or "")
    tips = []
    parse_ok = False
    if parsed_json:
        try:
            obj = json.loads(parsed_json)
            tips = obj.get("tips") or []
            parse_ok = isinstance(tips, list)
        except Exception:
            parse_ok = False
    raw_count = len(tips)

    seen_actions, seen_ids, kept = set(), set(), []
    dropped_duplicate = 0
    dropped_blank = 0
    dropped_id_dup = 0
    for idx, tip in enumerate(tips if parse_ok else []):
        if not isinstance(tip, dict):
            continue
        action = (tip.get("action") or "").strip()
        if not action:
            dropped_blank += 1
            continue
        if action in seen_actions:
            dropped_duplicate += 1
            continue
        seen_actions.add(action)
        tid = (tip.get("id") or "").strip()
        if tid and tid in seen_ids:
            dropped_id_dup += 1
            continue
        if not tid:
            tid = "tip-%d" % (len(kept) + 1)
        seen_ids.add(tid)
        kept.append(tip)
        if len(kept) >= 8:
            break

    vague, specific = [], []
    for tip in kept:
        why = vague_reason(tip)
        (vague if why else specific).append((tip, why))

    valid_count = len(specific)
    validated_count = len(kept)
    return {
        "parse_ok": parse_ok,
        "raw_tip_count": raw_count,
        "validated_tip_count": validated_count,
        "valid_tip_count": valid_count,
        "vague_tip_count": len(vague),
        "duplicate_dropped": dropped_duplicate,
        "blank_action_dropped": dropped_blank,
        "duplicate_id_dropped": dropped_id_dup,
        "duplicate_rate": round(dropped_duplicate / raw_count, 4) if raw_count else None,
        "vague_rate": round(len(vague) / validated_count, 4) if validated_count else None,
        "partial": validated_count < 6,
        "category_distribution": {c: sum(1 for t in kept if (t.get("timingCategory") or "") == c)
                                  for c in VALID_CATEGORIES},
        "vague_hits": [{"action": (t.get("action") or "")[:40], "because": why} for t, why in vague],
    }


def vague_reason(tip: dict):
    """本基准声明的"空泛"口径（见文件头 VAGUE_PHRASES 注释）。"""
    action = (tip.get("action") or "").strip()
    example = (tip.get("example") or "").strip()
    timing = (tip.get("timing") or "").strip()
    reason = (tip.get("reason") or "").strip()
    if len(action) < 6:
        return "action 短于 6 字，无法直接执行"
    if not example:
        return "缺 example（没有可直接使用的说法）"
    if not timing:
        return "缺 timing（不知道什么时候做）"
    for p in VAGUE_PHRASES:
        if p in action:
            return "action 命中通用鸡汤词「%s」" % p
    for p in PSEUDO_CERTAINTY:
        if p in reason:
            return "reason 含伪确定预测「%s」" % p
    for p in FABRICATION_HINTS:
        if p in action or p in example:
            return "疑似编造素材「%s」" % p
    return None


# ── 请求 ────────────────────────────────────────────────────────────────────

def endpoint_candidates(raw_input: str) -> list:
    """util/OpenAiChatEndpointResolver.candidates 的等价实现。"""
    base = (raw_input or "").strip().rstrip("/")
    if not base:
        return []
    if base.lower().endswith("/chat/completions"):
        return [base]
    path = base.split("://", 1)[-1]
    path = "/" + path.split("/", 1)[1] if "/" in path else ""
    if path and path.lower().endswith("/v1"):
        return [base + "/chat/completions"]
    return [base + "/chat/completions", base + "/v1/chat/completions", base + "/api/v1/chat/completions"]


def build_request_body(model: str, system: str, user: str, thinking_mode: int, temperature: float) -> str:
    """DeepSeekRepository.buildRequestBodyWithConfig（temperature=TEMPERATURE_MAIN, stream=true）。"""
    body = {
        "model": model,
        "temperature": temperature,
        "stream": True,
        "stream_options": {"include_usage": True},
        "thinking": {"type": "disabled" if thinking_mode == 0 else "enabled"},
        "response_format": {"type": "text"},
        "messages": [{"role": "system", "content": system},
                     {"role": "user", "content": user}],
    }
    if thinking_mode == 1:
        body["thinking"]["reasoning_effort"] = "low"
    return json.dumps(body, ensure_ascii=False)


def stream_request(url: str, api_key: str, body: str, timeout_s: float):
    """一次流式请求；返回 (text, usage|None, first_token_ms, wall_ms, raw_line_count)。"""
    req = urllib.request.Request(
        url,
        data=body.encode("utf-8"),
        headers={
            "Authorization": "Bearer %s" % api_key,
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        },
        method="POST",
    )
    t0 = time.monotonic()
    first_token_at = None
    pieces = []
    usage = None
    lines = 0
    with urllib.request.urlopen(req, timeout=timeout_s) as resp:
        for raw_line in resp:
            line = raw_line.decode("utf-8", "replace").strip()
            if not line:
                continue
            lines += 1
            if not line.startswith("data:"):
                continue
            payload = line[5:].strip()
            if payload == "[DONE]":
                break
            try:
                chunk = json.loads(payload)
            except json.JSONDecodeError:
                continue
            if isinstance(chunk.get("usage"), dict):
                usage = chunk["usage"]
            for choice in chunk.get("choices") or []:
                delta = (choice.get("delta") or {}).get("content")
                if delta:
                    if first_token_at is None:
                        first_token_at = time.monotonic()
                    pieces.append(delta)
    wall = time.monotonic() - t0
    text = "".join(pieces)
    if first_token_at is None and text:
        first_token_at = t0 + wall
    first_ms = int((first_token_at - t0) * 1000) if first_token_at else None
    return text, usage, first_ms, int(wall * 1000), lines


def normalise_usage(usage: dict):
    """DeepSeek 的 prompt_cache_hit/miss 与 OpenAI 的 prompt_tokens_details.cached_tokens 都吃。"""
    if not usage:
        return {"prompt_tokens": None, "completion_tokens": None, "cached_tokens": None,
                "cache_miss_tokens": None, "total_tokens": None, "raw": None}
    prompt = usage.get("prompt_tokens")
    cached = usage.get("prompt_cache_hit_tokens")
    if cached is None:
        cached = (usage.get("prompt_tokens_details") or {}).get("cached_tokens")
    miss = usage.get("prompt_cache_miss_tokens")
    if miss is None and prompt is not None and cached is not None:
        miss = prompt - cached
    return {
        "prompt_tokens": prompt,
        "completion_tokens": usage.get("completion_tokens"),
        "cached_tokens": cached,
        "cache_miss_tokens": miss,
        "total_tokens": usage.get("total_tokens"),
        "raw": usage,
    }


# ── 汇总 ────────────────────────────────────────────────────────────────────

def summarise(records: list, meta: dict) -> dict:
    ok = [r for r in records if r["status"] == "ok"]
    dry = [r for r in records if r["status"] == "dry-run"]
    def vals(key):
        return [r[key] for r in ok if isinstance(r.get(key), (int, float))]

    def med(xs):
        return round(statistics.median(xs), 6) if xs else None

    costs = vals("cost_yuan")
    prompts = vals("prompt_tokens")
    completions = vals("completion_tokens")
    cached = vals("cached_tokens")
    valids = vals("valid_tip_count")
    raws = vals("raw_tip_count")
    dup_rates = vals("duplicate_rate")
    vague_rates = vals("vague_rate")
    total_cost = sum(costs) if costs and len(costs) == len(ok) else None
    total_valid = sum(valids) if valids else None
    return {
        "schema_version": 1,
        "generated_at_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "baseline_identity": meta,
        "requests_planned": meta.get("requests"),
        "requests_succeeded": len(ok),
        "requests_dry_run": len(dry),
        "requests_failed": len(records) - len(ok) - len(dry),
        "tokens": {
            "prompt_mean": round(statistics.mean(prompts), 2) if prompts else None,
            "prompt_median": med(prompts),
            "completion_mean": round(statistics.mean(completions), 2) if completions else None,
            "completion_median": med(completions),
            "cached_mean": round(statistics.mean(cached), 2) if cached else None,
            "cache_hit_rate": (round(sum(cached) / sum(prompts), 4)
                               if cached and prompts and sum(prompts) else None),
        },
        "latency_ms": {
            "first_token_mean": round(statistics.mean(vals("first_token_ms")), 1) if vals("first_token_ms") else None,
            "wall_mean": round(statistics.mean(vals("wall_ms")), 1) if vals("wall_ms") else None,
            "wall_max": max(vals("wall_ms")) if vals("wall_ms") else None,
        },
        "quality": {
            "raw_tips_mean": round(statistics.mean(raws), 2) if raws else None,
            "valid_tips_mean": round(statistics.mean(valids), 2) if valids else None,
            "valid_tips_min": min(valids) if valids else None,
            "partial_runs": sum(1 for r in ok if r.get("partial")),
            "duplicate_rate_mean": round(statistics.mean(dup_rates), 4) if dup_rates else None,
            "vague_rate_mean": round(statistics.mean(vague_rates), 4) if vague_rates else None,
        },
        "cost": {
            "currency": "CNY",
            "per_request_mean": round(statistics.mean(costs), 6) if costs else None,
            "per_request_max": max(costs) if costs else None,
            "total": round(total_cost, 6) if total_cost is not None else None,
            "per_valid_tip": (round(total_cost / total_valid, 6)
                              if total_cost is not None and total_valid else None),
            "billed": bool(costs),
        },
        "not_measured": [] if ok else [
            "no request completed — every number above is null because nothing was measured"],
    }


def write_outputs(out_path: str, summary_path: str, csv_path: str, records: list, summary: dict):
    if out_path:
        os.makedirs(os.path.dirname(os.path.abspath(out_path)) or ".", exist_ok=True)
        with open(out_path, "w", encoding="utf-8", newline="\n") as fh:
            for r in records:
                fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    if summary_path:
        os.makedirs(os.path.dirname(os.path.abspath(summary_path)) or ".", exist_ok=True)
        with open(summary_path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(summary, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
    if csv_path:
        cols = ["index", "status", "http_status", "prompt_tokens", "completion_tokens",
                "cached_tokens", "cache_miss_tokens", "cost_yuan", "wall_ms", "first_token_ms",
                "raw_tip_count", "validated_tip_count", "valid_tip_count", "duplicate_rate",
                "vague_rate", "partial", "error"]
        os.makedirs(os.path.dirname(os.path.abspath(csv_path)) or ".", exist_ok=True)
        with open(csv_path, "w", encoding="utf-8", newline="") as fh:
            w = csv.writer(fh)
            w.writerow(cols)
            for r in records:
                w.writerow([r.get(c, "") for c in cols])


# ── main ────────────────────────────────────────────────────────────────────

def parse_args(argv):
    p = argparse.ArgumentParser(description="LoveBrain 锦囊真实成本基准 runner")
    p.add_argument("--kb-dir", default=KB_DIR_DEFAULT)
    p.add_argument("--fixture-lock", default=FIXTURE_LOCK_DEFAULT)
    p.add_argument("--write-lock", action="store_true",
                   help="（重新）生成夹具锁文件后退出；只在基准夹具被有意变更时使用")
    p.add_argument("--requests", type=int, default=5)
    p.add_argument("--base-url", default=os.environ.get("LOVEBRAIN_BASELINE_BASE_URL", DEFAULT_BASE_URL))
    p.add_argument("--model", default=os.environ.get("LOVEBRAIN_BASELINE_MODEL", DEFAULT_MODEL))
    p.add_argument("--api-key-env", default="LOVEBRAIN_BASELINE_API_KEY")
    p.add_argument("--temperature", type=float, default=TEMPERATURE_MAIN)
    p.add_argument("--thinking", type=int, choices=(0, 1), default=0)
    p.add_argument("--timeout", type=float, default=SUGGEST_TIMEOUT_MS / 1000.0)
    p.add_argument("--sleep", type=float, default=2.0, help="两次请求之间的间隔秒数，避免打爆限流")
    p.add_argument("--price-hit", type=float, default=None, help="元/百万 tokens，覆盖内置价表")
    p.add_argument("--price-miss", type=float, default=None)
    p.add_argument("--price-out", type=float, default=None)
    p.add_argument("--tag", default="current", help="这一轮的身份标签，用于新旧对比（如 old / current）")
    p.add_argument("--out", default="dist/suggest-baseline.jsonl")
    p.add_argument("--summary", default="dist/suggest-baseline.summary.json")
    p.add_argument("--csv", default="dist/suggest-baseline.csv")
    p.add_argument("--dry-run", action="store_true", help="只构造请求，不发任何网络流量")
    p.add_argument("--self-test", action="store_true", help="离线自检：夹具/组装/计价/指标全部本地验算")
    return p.parse_args(argv)


def self_test() -> int:
    """离线自检：证明这套代码真的能构造请求并计算指标（不依赖网络/Key）。"""
    failures = []

    def check(name, cond, detail=""):
        if cond:
            print("[self-test]   OK   %s" % name)
        else:
            failures.append(name)
            print("[self-test]   FAIL %s %s" % (name, detail))

    sys_p = read_text(SUGGEST_ASSET, "suggest.md asset")
    plan_md = read_text(os.path.join(KB_DIR_DEFAULT, "moment", "plan.md"), "plan fixture")
    user_p = build_suggest_user_prompt(KB_DIR_DEFAULT, datetime(2026, 9, 24, 1, 30))
    check("system prompt is the real suggest.md", len(sys_p) > 500 and "日常行动建议" in sys_p)
    check("user prompt carries the stage section", "## 关系阶段\n暧昧期" in user_p)
    check("user prompt carries warmth/style/plan/lessons",
          all(s in user_p for s in ("## 温度摘要", "## 表达偏好", "## 与今天相关的事项", "## 需要避开的经验")))
    check("user prompt honours the 3500-char budget", len(user_p) <= SUGGEST_BUDGET, "len=%d" % len(user_p))
    check("ongoing section keeps at most 3 items", len(ongoing_section(plan_md).split("\n")) == 3,
          ongoing_section(plan_md)[:120])
    check("timestamp block is frozen in self-test", "【当前时间】2026-09-24 01:30" in user_p)

    body = json.loads(build_request_body("deepseek-v4-flash", sys_p, user_p, 0, TEMPERATURE_MAIN))
    check("request body mirrors production",
          body["temperature"] == 0.7 and body["stream"] is True
          and body["stream_options"]["include_usage"] is True
          and body["thinking"] == {"type": "disabled"}
          and [m["role"] for m in body["messages"]] == ["system", "user"])
    check("endpoint candidates mirror OpenAiChatEndpointResolver",
          endpoint_candidates("https://api.deepseek.com")[-1].endswith("/api/v1/chat/completions")
          and endpoint_candidates("https://x.test/v1") == ["https://x.test/v1/chat/completions"])

    # 计价：与 UsagePricer 的表逐字核对（off-peak flash 1000 hit/1000 miss/1000 out）
    fixed_off = datetime(2026, 9, 19, 3, 0, tzinfo=timezone.utc)   # 北京 11:00 周六 → 非高峰
    cost, billed, _ = compute_cost(1_000_000, 1_000_000, 1_000_000, "deepseek-v4-flash",
                                   "https://api.deepseek.com", fixed_off)
    check("off-peak flash triple = 0.05+1.5+4.5", abs(cost - 6.05) < 1e-9 and billed, "got %s" % cost)
    cost, _, _ = compute_cost(0, 0, 0, "deepseek-reasoner", "https://api.deepseek.com", fixed_off)
    check("unknown tier is not billed", cost == 0.0)
    cost, billed, _ = compute_cost(10, 10, 10, "deepseek-chat", "https://example.org", fixed_off)
    check("non-deepseek host refuses to invent a cost", cost is None and not billed)

    # 指标：6 条干净 / 含重复与空泛两种样本
    good_tips = [
        ("早上七点半给她发一张你早餐的照片", "今天早饭时间", "今天这碗面居然没放香菜", "她昨天说想吃面"),
        ("把她提过两次的摄影展周六下午场链接发给她", "今天下班后", "周六下午那个展你不是想去看吗", "她自己提过两次"),
        ("问她加班这周晚饭想吃什么，替她订一份", "今晚七点前", "这周别点外卖了，我按你口味订", "她说这周连轴转"),
        ("把她随口说想试的咖啡店定位发过去", "今天中午", "你说想试的那家我查到了，就在公司两条街外", "降低她答应的成本"),
        ("晚上十一点前发一句今天早点睡，不追问", "今晚睡前", "今天到这儿，睡够明天再说", "她说过怕被连环追问"),
        ("约见家长那件事给她两个具体时间让她挑", "本周末", "周日下午或者下周三晚上，你哪个方便", "事项一直悬着没定"),
    ]
    good = {"tips": [{"id": "t%d" % (i + 1), "timingCategory": "现在可用",
                      "action": a, "timing": t, "materialNeeded": "",
                      "example": e, "reason": r}
                     for i, (a, t, e, r) in enumerate(good_tips)]}
    m = score_tips("```json\n%s\n```" % json.dumps(good, ensure_ascii=False))
    check("six concrete tips score 6 valid, 0 duplicate, 0 vague",
          m["valid_tip_count"] == 6 and m["duplicate_dropped"] == 0 and m["vague_tip_count"] == 0,
          json.dumps(m, ensure_ascii=False)[:200])
    noisy = {"tips": [
        {"id": "a", "action": "多沟通", "timing": "随时", "example": "在干嘛", "reason": "感情需要经营"},
        {"id": "b", "action": "多沟通", "timing": "随时", "example": "在干嘛", "reason": "x"},
        {"id": "c", "action": "", "timing": "", "example": "", "reason": ""},
        {"id": "d", "action": "把她上周提过的展览链接翻出来约周六", "timing": "今天下班后",
         "example": "周六下午那个展你不是想去看吗", "reason": "她自己提过两次"},
    ]}
    m2 = score_tips(json.dumps(noisy, ensure_ascii=False))
    check("duplicate action dropped", m2["duplicate_dropped"] == 1)
    check("blank action dropped", m2["blank_action_dropped"] == 1)
    check("generic tip flagged vague", m2["vague_tip_count"] == 1, json.dumps(m2["vague_hits"], ensure_ascii=False))
    check("below six is partial", m2["partial"] is True)
    check("unparseable output yields zero valid, not a fake score",
          score_tips("抱歉我不能")["valid_tip_count"] == 0)

    # 夹具冻结
    drift = verify_fixture_lock(KB_DIR_DEFAULT, SUGGEST_ASSET, FIXTURE_LOCK_DEFAULT)
    check("fixture lock matches the tree", not drift, "; ".join(drift))

    # summary 数学
    recs = [{"status": "ok", "prompt_tokens": 1000, "completion_tokens": 100, "cached_tokens": 800,
             "cache_miss_tokens": 200, "cost_yuan": 0.002, "wall_ms": 4000, "first_token_ms": 500,
             "raw_tip_count": 6, "validated_tip_count": 6, "valid_tip_count": 6,
             "duplicate_rate": 0.0, "vague_rate": 0.0, "partial": False},
            {"status": "ok", "prompt_tokens": 1000, "completion_tokens": 100, "cached_tokens": 800,
             "cache_miss_tokens": 200, "cost_yuan": 0.002, "wall_ms": 6000, "first_token_ms": 600,
             "raw_tip_count": 6, "validated_tip_count": 6, "valid_tip_count": 4,
             "duplicate_rate": 0.0, "vague_rate": 0.5, "partial": False},
            {"status": "failed", "error": "timeout"}]
    s = summarise(recs, {"requests": 3})
    check("summary counts successes and failures",
          s["requests_succeeded"] == 2 and s["requests_failed"] == 1)
    check("cost per valid tip uses total/valid", abs(s["cost"]["per_valid_tip"] - 0.004 / 10) < 1e-9,
          str(s["cost"]))
    check("cache hit rate computed from tokens", s["tokens"]["cache_hit_rate"] == 0.8)

    if failures:
        print("[self-test] FAILED %d check(s): %s" % (len(failures), ", ".join(failures)))
        return 1
    print("[self-test] all checks passed (offline, no request sent, no usage invented)")
    return 0


def main(argv=None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])

    if args.self_test:
        return self_test()

    try:
        if not os.path.isfile(SUGGEST_ASSET):
            raise HarnessError("prompt asset missing: %s" % SUGGEST_ASSET)
        if args.write_lock:
            os.makedirs(os.path.dirname(os.path.abspath(args.fixture_lock)), exist_ok=True)
            with open(args.fixture_lock, "w", encoding="utf-8", newline="\n") as fh:
                fh.write(build_fixture_manifest(args.kb_dir, SUGGEST_ASSET))
            print("[suggest-baseline] wrote %s" % args.fixture_lock)
            return 0

        drift = verify_fixture_lock(args.kb_dir, SUGGEST_ASSET, args.fixture_lock)
        if drift:
            raise HarnessError(
                "the frozen KB fixture or suggest.md changed since the lock was written:\n    %s\n"
                "    Either restore the fixture or re-pin it deliberately with "
                "--write-lock and update BENCHMARK.md." % "\n    ".join(drift))

        system = read_text(SUGGEST_ASSET, "suggest.md asset")
        user = build_suggest_user_prompt(args.kb_dir, datetime.now())
    except HarnessError as exc:
        print("[suggest-baseline] CANNOT-VERIFY %s" % exc, file=sys.stderr)
        return 2

    prompt_sha = sha256_bytes((system + "\x00" + user).encode("utf-8"))
    asset_sha = sha256_file(SUGGEST_ASSET)
    meta = {
        "tag": args.tag,
        "requests": args.requests,
        "model": args.model,
        "base_url_host": re.sub(r"^.*://", "", args.base_url).split("/")[0],
        "temperature": args.temperature,
        "thinking_mode": args.thinking,
        "suggest_md_sha256": asset_sha,
        "prompt_sha256": prompt_sha,
        "system_chars": len(system),
        "user_chars": len(user),
        "fixture_kb": os.path.relpath(args.kb_dir, ROOT).replace("\\", "/"),
        "git_commit": os.environ.get("GIT_COMMIT", "unknown"),
        "dry_run": bool(args.dry_run),
    }

    api_key = ""
    if not args.dry_run:
        api_key = os.environ.get(args.api_key_env, "").strip()
        if not api_key:
            print("[suggest-baseline] CANNOT-VERIFY no API key in $%s.\n"
                  "                  Nothing was sent, so nothing was measured. Export a key the operator "
                  "owns and re-run; do NOT copy numbers from a previous run.\n"
                  "                  e.g.  %s=sk-... bash scripts/suggest_cost_baseline.sh --requests 5"
                  % (args.api_key_env, args.api_key_env), file=sys.stderr)
            return 2
        if not args.base_url.lower().startswith("https://"):
            print("[suggest-baseline] CANNOT-VERIFY base url must be https (HttpsTrustGuard forbids cleartext): %s"
                  % args.base_url, file=sys.stderr)
            return 2

    overrides = None
    if args.price_hit is not None or args.price_miss is not None or args.price_out is not None:
        overrides = (args.price_hit, args.price_miss, args.price_out)

    records = []
    urls = endpoint_candidates(args.base_url)
    for i in range(args.requests):
        rec = {
            "index": i + 1,
            "tag": args.tag,
            "requested_at_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "prompt_sha256": prompt_sha,
            "suggest_md_sha256": asset_sha,
            "model": args.model,
            "endpoint": urls[0] if urls else None,
            "status": "ok",
            "http_status": None,
            "error": None,
            "usage_source": None,
        }
        if args.dry_run:
            rec.update({
                "status": "dry-run",
                "prompt_tokens": None, "completion_tokens": None, "cached_tokens": None,
                "cache_miss_tokens": None, "cost_yuan": None, "billed": False,
                "cost_basis": "not sent — dry run",
                "wall_ms": None, "first_token_ms": None, "sse_lines": None,
                "response_chars": None,
                "raw_tip_count": None, "validated_tip_count": None, "valid_tip_count": None,
                "vague_tip_count": None, "duplicate_dropped": None, "duplicate_rate": None,
                "vague_rate": None, "partial": None, "category_distribution": None,
                "vague_hits": None,
            })
            records.append(rec)
            continue
        body = build_request_body(args.model, system, user, args.thinking, args.temperature)
        try:
            text, usage, first_ms, wall_ms, nlines = stream_request(
                urls[0], api_key, body, args.timeout)
            u = normalise_usage(usage)
            cost, billed, basis = compute_cost(
                u["cached_tokens"], u["cache_miss_tokens"], u["completion_tokens"],
                args.model, args.base_url, datetime.now(timezone.utc), overrides)
            score = score_tips(text)
            rec.update(u)
            rec.update(score)
            rec.update({
                "cost_yuan": None if cost is None else round(cost, 8),
                "billed": billed, "cost_basis": basis,
                "wall_ms": wall_ms, "first_token_ms": first_ms, "sse_lines": nlines,
                "response_chars": len(text),
                "usage_source": "provider" if usage else "missing",
            })
            if not usage:
                rec["status"] = "failed"
                rec["error"] = "stream completed without a usage object — cost cannot be inferred"
        except urllib.error.HTTPError as exc:
            rec["status"] = "failed"
            rec["http_status"] = exc.code
            rec["error"] = "HTTP %s: %s" % (exc.code, safe_read(exc))
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            rec["status"] = "failed"
            rec["error"] = "%s: %s" % (type(exc).__name__, exc)
        except Exception as exc:  # noqa: BLE001 - 单条失败不该吞掉整轮
            rec["status"] = "failed"
            rec["error"] = "%s: %s" % (type(exc).__name__, exc)
        records.append(rec)
        print("[suggest-baseline] #%d %s tokens=prompt:%s cached:%s completion:%s cost=%s valid=%s/%s"
              % (i + 1, rec["status"], rec.get("prompt_tokens"), rec.get("cached_tokens"),
                 rec.get("completion_tokens"), rec.get("cost_yuan"),
                 rec.get("valid_tip_count"), rec.get("validated_tip_count")), file=sys.stderr)
        if i + 1 < args.requests:
            time.sleep(max(0.0, args.sleep))

    summary = summarise(records, meta)
    write_outputs(args.out, args.summary, args.csv, records, summary)
    print("[suggest-baseline] wrote %s, %s, %s" % (args.out, args.summary, args.csv), file=sys.stderr)

    if args.dry_run:
        print("[suggest-baseline] DRY RUN: request built (%d chars system + %d chars user, "
              "prompt_sha256=%s) but NOTHING was sent — every usage/cost field is null."
              % (len(system), len(user), prompt_sha[:16]), file=sys.stderr)
        return 0
    if summary["requests_failed"]:
        print("[suggest-baseline] FAIL %d of %d request(s) failed; the emitted numbers are NOT a clean "
              "baseline. Fix the cause and re-run the whole set."
              % (summary["requests_failed"], args.requests), file=sys.stderr)
        return 1
    if not summary["cost"]["billed"]:
        print("[suggest-baseline] FAIL no request produced billable usage — cost evidence is missing.",
              file=sys.stderr)
        return 1
    print("[suggest-baseline] OK %d real request(s) measured (mean ¥%s per request, ¥%s per valid tip)"
          % (summary["requests_succeeded"], summary["cost"]["per_request_mean"],
             summary["cost"]["per_valid_tip"]), file=sys.stderr)
    return 0


def safe_read(exc):
    try:
        return exc.read().decode("utf-8", "replace")[:400]
    except Exception:
        return ""


if __name__ == "__main__":
    sys.exit(main())
