#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""source_id_wash.py — analysis.source_ids 的守卫侧通用清洗（W19-02，零模型调用）。

背景（W17c 批证据）：候选偶尔把合法历史消息引用写成 canonical 短格式 mN
（如 E164_T04 的 "m2"），而机制渲染的历史标签是 m_src[i]（i 从 0 计、评估侧
原题真实消息计数，见 trusted_controller.prior_real_hist_n / prior_hist_id_map）。
评分器（lb_scorer source_ids_exist 检查）只认合法引用集内的精确 id，短格式
被误判 illegal_source_id veto。隔离路径合法集已修复（FK-SCORE-01）；本模块
做守卫侧清洗：把 m2 类短格式按证据确认的语义改写为 m_src[n]，**不改回复正文**。

id 语义判定依据（W17c E164_T04 实证）：
  - analysis.scene_facts 里 "m0" 挂在"今天09:30前台称双十一办卡送…"上 = 本轮
    T04 自己的消息（canonical_id_map → {"m0": "HER"}）；
  - "m2" 挂在"昨天21:05她说纯次卡1450、单次72.5"上 = T03 的历史消息
    （2026-11-10 21:05，prior 历史第 2 条，0 计）→ 机制渲染格式即 m_src[2]。
  即：模型在同一个 analysis 块里混用了本轮 canonical 格式（m0）与历史引用的
  短格式（m2）；mN 指历史第 n 条（n 从 0 计），对应 m_src[n]。

清洗规则（通用，按格式匹配，不对任何题号特判；每条编辑都登记，不猜）：
  1) token 已是 m_src[n] 且 n < prior_hist_n → 保留（合法历史引用）；
     越界 → 移除并登记（不猜指向）。
  2) token 是短格式 mN：
     a. N 在本轮 canonical 合法集内 → 保留原样（mN 正是本轮 canonical 渲染
        格式，按字面解读最忠实；改写成历史反而是猜测）；
     b. 否则 N < prior_hist_n → 改写为 m_src[N]（历史第 N 条）；
     c. 否则 → 移除并登记（越界，不猜）。
  3) 其余 token（如 "1,1" 噪声、非字符串）→ 移除并登记。
  4) 首尾空白 token 先 strip 再判；strip 后合法则用 strip 后的值并登记。
  5) 清空后的 source_ids 保留为 []（评分器对空列表天然通过；不伪造引用）。

正文保证：只重写 "source_ids" 键下的列表；response/directions 等其它键逐字节
不变（对象未变化时整段文本逐字节原样返回，不做任何 re-dump）。

复用方式（下一版候选，含 uc_guard 谱系）：在机制守卫 repair 结果上调用
  washed_text, meta = wash_reply_text(text, canonical_ids, prior_hist_n)
其中 canonical_ids = 本轮 canonical_id_map(messages) 的键集，
prior_hist_n = prior_real_hist_n(ep, turn_index)。两者都由可信侧传入，
本模块不 import arena、不信任候选自报。
"""
from __future__ import annotations

import json
import re

M_SHORT_RE = re.compile(r"^m(\d+)$")
M_SRC_RE = re.compile(r"^m_src\[(\d+)\]$")

# 动作常量（进 meta["edits"][i]["action"]）
KEEP = "keep"                      # 合法，不动（不进 edits）
STRIP = "whitespace_stripped"      # strip 后合法
M_SHORT_TO_HIST = "m_short_to_hist"  # mN → m_src[N]
REMOVE_OOB_SRC = "removed_m_src_out_of_range"
REMOVE_OOB_SHORT = "removed_m_short_out_of_range"
REMOVE_NOISE = "removed_not_message_id"


def _legal_hist(n, prior_hist_n):
    return 0 <= n < prior_hist_n


def wash_source_id_list(ids, canonical_ids, prior_hist_n):
    """清洗一个 source_ids 列表。

    返回 (new_ids, edits)。edits 每项：
      {"token": 原token, "action": 动作, "reason": 说明, "replacement": 新值或None}
    只有实际发生 strip/改写/移除的 token 才进 edits。
    """
    canonical = set(canonical_ids or ())
    new_ids = []
    edits = []
    for tok in ids or []:
        if not isinstance(tok, str):
            edits.append({"token": tok, "action": REMOVE_NOISE,
                          "reason": "non_string_token", "replacement": None})
            continue
        t = tok.strip()
        if t != tok:
            tok_cmp = t
        else:
            tok_cmp = tok
        m = M_SRC_RE.match(tok_cmp)
        if m:
            n = int(m.group(1))
            if _legal_hist(n, prior_hist_n):
                if tok_cmp != tok:
                    new_ids.append(tok_cmp)
                    edits.append({"token": tok, "action": STRIP,
                                  "reason": "m_src_with_whitespace",
                                  "replacement": tok_cmp})
                else:
                    new_ids.append(tok)
                continue
            edits.append({"token": tok, "action": REMOVE_OOB_SRC,
                          "reason": "m_src[%d] out of range "
                                    "(prior_hist_n=%d)" % (n, prior_hist_n),
                          "replacement": None})
            continue
        m = M_SHORT_RE.match(tok_cmp)
        if m:
            n = int(m.group(1))
            if tok_cmp in canonical:
                if tok_cmp != tok:
                    new_ids.append(tok_cmp)
                    edits.append({"token": tok, "action": STRIP,
                                  "reason": "canonical_with_whitespace",
                                  "replacement": tok_cmp})
                else:
                    new_ids.append(tok)
                continue
            if _legal_hist(n, prior_hist_n):
                repl = "m_src[%d]" % n
                new_ids.append(repl)
                edits.append({"token": tok, "action": M_SHORT_TO_HIST,
                              "reason": "m%d not canonical this turn; "
                                        "evidence: short id refers to prior "
                                        "history message n (0-based)"
                                        % n,
                              "replacement": repl})
                continue
            edits.append({"token": tok, "action": REMOVE_OOB_SHORT,
                          "reason": "m%d not canonical and out of history "
                                    "range (prior_hist_n=%d)"
                                    % (n, prior_hist_n),
                          "replacement": None})
            continue
        edits.append({"token": tok, "action": REMOVE_NOISE,
                      "reason": "not a message id (neither mN nor m_src[n])",
                      "replacement": None})
    return new_ids, edits


def _strip_source_ids(o):
    """深拷贝式归一：所有 source_ids 键置 None，用于正文不变性比对。"""
    if isinstance(o, dict):
        return {k: (None if k == "source_ids" else _strip_source_ids(v))
                for k, v in o.items()}
    if isinstance(o, list):
        return [_strip_source_ids(v) for v in o]
    return o


def body_intact_before_after(obj_before, obj_after):
    """校验除 source_ids 外的对象逐字段相等（response/directions 等正文保证）。"""
    return _strip_source_ids(obj_before) == _strip_source_ids(obj_after)


def wash_reply_text(raw_text, canonical_ids, prior_hist_n):
    """对候选回复文本做 source_ids 清洗。返回 (text, meta)。

    - 不可解析 / 无 source_ids / 无实际编辑 → 逐字节返回原文，meta["changed"]=False；
    - 有编辑 → 只改 source_ids 列表后 json.dumps(ensure_ascii=False) 重写；
      meta["body_intact"]=True 保证除 source_ids 外对象逐字段不变。
    零模型调用；本模块不做评分判断，veto 与否由评分器决定。
    """
    meta = {
        "changed": False,
        "unparseable": False,
        "body_intact": None,
        "edits": [],
        "rules": [],
    }
    if not isinstance(raw_text, str):
        meta["unparseable"] = True
        return raw_text, meta
    try:
        obj = json.loads(raw_text)
    except Exception:                           # noqa: BLE001 不猜不修
        meta["unparseable"] = True
        return raw_text, meta
    if not isinstance(obj, dict):
        meta["unparseable"] = True
        return raw_text, meta

    all_edits = []

    def walk(node, path):
        if isinstance(node, dict):
            for k, v in list(node.items()):
                if k == "source_ids" and isinstance(v, list):
                    new_ids, edits = wash_source_id_list(
                        v, canonical_ids, prior_hist_n)
                    if edits:
                        node[k] = new_ids
                        for e in edits:
                            e["path"] = path + [k]
                        all_edits.extend(edits)
                else:
                    walk(v, path + [k])
        elif isinstance(node, list):
            for i, v in enumerate(node):
                walk(v, path + [i])

    walk(obj, [])
    meta["edits"] = all_edits
    if not all_edits:
        meta["body_intact"] = True
        return raw_text, meta

    actions = {e["action"] for e in all_edits}
    if M_SHORT_TO_HIST in actions:
        meta["rules"].append("m_short_to_hist")
    for a, rule in ((REMOVE_OOB_SRC, "removed_m_src_out_of_range"),
                    (REMOVE_OOB_SHORT, "removed_m_short_out_of_range"),
                    (REMOVE_NOISE, "removed_not_message_id"),
                    (STRIP, "whitespace_stripped")):
        if a in actions:
            meta["rules"].append(rule)

    # 正文不变性自检：除 source_ids 外逐字段必须与原文解析结果一致
    orig_obj = json.loads(raw_text)
    meta["body_intact"] = bool(body_intact_before_after(orig_obj, obj))

    meta["changed"] = True
    return json.dumps(obj, ensure_ascii=False), meta
