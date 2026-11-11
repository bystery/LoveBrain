#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""盲评材料生成 v4（第七波判决 §6：历史样本绑定当时冻结题面）。

相对 v3 的修正（第七波判决）：
  - **上下文改用运行时冻结题面快照**（benchmark/episodes/archive/PILOT_L1_v1__pilot_r3.json），
    不再用当前 PILOT_L1 JSON——此前第 8 组展示"星耀12Pro"新上下文、模型实际收到
    "B 的高配"，属新输入+旧输出的错误配对；
  - 生成器内置绑定自检：快照每条消息文本必须命中该轮当时请求（calls.user），
    任一未命中直接拒绝生成（防止题面再改后静默错配）；
  - 材料头标明：run 目录、冻结题面 sha256、评分版本——历史样本按其冻结状态评审，
    跨版本分数变化不得归为单一机制收益；
  - 修文案后的**新题面**审核走 render_pilot_views.py 双视图（新输出生成后另出新盲评），
    本脚本只服务 pilot_r3 历史样本。
保留 v3 修正：合法不回正确展示；可用性以 runner 裁决为准；第 7 组补图片与预算出处。
不重新调用模型——只重渲染既有 logs/pilot_r3 产物。
"""
import glob
import hashlib
import json
import os
import random
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN = "logs/pilot_r3"
SEED = 20261019
SKIP = set()          # v3：媒体轮图片已真实进入请求，不再预剔
OUT = "refs/BLIND_SAMPLES_PILOT_R2.md"
# 第七波判决 §6.2：历史样本绑定运行时冻结题面（非当前题面）
EP_FROZEN = os.path.join("benchmark", "episodes", "archive",
                         "PILOT_L1_v1__pilot_r3.json")
SCORING_VERSION = "v0.9"   # 材料标注用的评分器版本口径


def parse_output(raw: str):
    """返回 (kind, payload)。kind ∈ text / no_reply / unparseable。"""
    m = re.search(r"\{.*\}", raw or "", re.DOTALL)
    if not m:
        return "unparseable", None
    try:
        out = json.loads(m.group(0))
    except json.JSONDecodeError:
        return "unparseable", None
    if out.get("action") == "no_reply":
        reason = out.get("reason") or out.get("decline_reason") or ""
        return "no_reply", {"reason": str(reason)}
    dr = out.get("decline_reason")
    if isinstance(dr, str) and dr.strip():
        return "no_reply", {"reason": dr.strip()}
    try:
        rec = out["response"]["recommended"]
        if isinstance(rec, str) and rec.strip():
            return "text", {"reply": rec.strip()}
    except Exception:
        pass
    return "unparseable", None


def load_run(d):
    turns = {}
    for td in sorted(glob.glob(os.path.join(d, "turn_*"))):
        m = json.load(open(os.path.join(td, "manifest.json"), encoding="utf-8"))
        cid = m["case_id"]
        raw = open(os.path.join(td, "reply_raw.txt"), encoding="utf-8").read().strip()
        kind, payload = parse_output(raw)
        contract_veto = m.get("veto")          # runner 裁决（权威）
        turns[cid] = {"raw": raw, "kind": kind, "payload": payload,
                      "contract_veto": contract_veto,
                      "display_status": ("no_reply" if kind == "no_reply" else
                                         ("text" if kind == "text" else "unparseable")),
                      "clock": m.get("clock_injected"), "score": m.get("score10"),
                      "media": m.get("media_refs") or []}
    return turns


def main():
    ep = json.load(open(EP_FROZEN, encoding="utf-8"))
    ep_sha = hashlib.sha256(open(EP_FROZEN, "rb").read()).hexdigest()
    # 第七波判决 §6.2：绑定自检——冻结题面每条消息必须命中该轮当时请求。
    # 题面再改而不重建快照时，此处会拒绝生成而不是静默错配。
    binding_errors = []
    for run in ("baseline_L1", "beta_L1"):
        for t in ep["turns"]:
            cid = t["case_id"]
            tds = glob.glob(os.path.join(RUN, run, "turn_*_%s" % cid))
            if not tds:
                binding_errors.append("%s/%s: 无运行目录" % (run, cid))
                continue
            calls = json.load(open(os.path.join(tds[0], "calls.json"),
                                   encoding="utf-8"))
            user = calls[0].get("user") or ""
            for m in t["input"].get("messages") or []:
                if m.get("image"):
                    continue
                if m.get("text") and m["text"] not in user:
                    binding_errors.append("%s/%s: 消息未命中当时请求: %r"
                                          % (run, cid, m["text"][:30]))
    if binding_errors:
        raise SystemExit("冻结题面绑定自检失败（题面与运行历史不匹配，禁止生成）:\n"
                         + "\n".join(binding_errors[:8]))
    base = load_run(os.path.join(RUN, "baseline_L1"))
    beta = load_run(os.path.join(RUN, "beta_L1"))
    rng = random.Random(SEED)

    def contract_ok(d):
        return sum(1 for v in d.values() if not v["contract_veto"])

    def count_outputs(d):
        txt = sum(1 for v in d.values() if v["contract_veto"] is None and v["kind"] == "text")
        nr = sum(1 for v in d.values() if v["contract_veto"] is None and v["kind"] == "no_reply")
        return txt, nr

    bt, bn = count_outputs(base)
    yt, yn = count_outputs(beta)
    lines = ["# Pilot L1 真实模型匿名盲评材料 R2 修订版（v4 自动生成，原始回复未润色）\n",
             "> **历史样本绑定（第七波判决 §6.2）**：上下文取自运行时冻结题面快照"
             "（`%s`，sha256=%s…），回复取自 `%s`；评分版本 scorer %s。"
             "修文案后的新题面（星耀12Pro 等）**不属于本次运行输入**，其行为评价"
             "须等新输出生成后另出新盲评；本材料按冻结状态评审，不跨版本归因。"
             % (EP_FROZEN, ep_sha[:16], RUN, SCORING_VERSION),
             "> 本版修正（第五波判决 §3）：合法不回正确展示；可用性以 runner 裁决为准；"
             "第 7 组补图片与既往预算出处。未重新调用模型。",
             "> 固定模型 dsf；同冻结外部历史（候选草稿不入历史）、同预算口径；A/B 身份随机（seed=%d）。" % SEED,
             "> **共同背景（两人都已知）**：" + "；".join(
                 "%s=%s" % (k, v) for k, v in ep.get("persona", {}).items()),
             "> **全请求可用性（runner 裁决，先于质量看）**：基线契约通过 %d/12，β_single %d/12。"
             % (contract_ok(base), contract_ok(beta)),
             "> **有效输出构成**：基线 %d 条文字 + %d 个合法不回动作；β_single %d 条文字 + %d 个合法不回动作。"
             % (bt, bn, yt, yn),
             "> 消息时间戳已注入模型上下文；图片经 image_url 通道进入请求（媒体轮标注）。",
             "> 展示层提取失败会单独标注，**不改写**候选契约状态（v2 曾把合法不回误标为契约失败）。\n"]
    keys = [t["case_id"] for t in ep["turns"] if t["case_id"] not in SKIP]
    keymap = {}
    n = 0
    for cid in keys:
        t = next(x for x in ep["turns"] if x["case_id"] == cid)
        if cid not in base or cid not in beta:
            lines.append("\n## %s ——（缺运行，跳过）\n" % cid)
            continue
        n += 1
        a_is_base = rng.random() < 0.5
        keymap[cid] = "baseline" if a_is_base else "beta_single"
        a, b = (base, beta) if a_is_base else (beta, base)
        lines.append("\n## 组 %d · %s（请求时间 %s）%s\n" % (
            n, cid, t["input"]["clock"],
            " · 媒体轮（图片已进请求）" if t["input"].get("media") else ""))
        lines.append("**聊天上下文**（末条为待回复消息）：\n")
        for m in t["input"]["messages"]:
            sp = "她" if m["role"] == "HER" else "我"
            lines.append("- %s（%s）：%s" % (sp, m["ts"][11:16], m["text"]))
        # 第五波判决 §3：媒体轮补图片本物 + 既往事实出处（同一 episode 内 HER 原话）
        # 第六波判决 §11：链接按文档所在目录（refs/）解析 → 前缀 ../；并自动校验
        # 按文档位置解析后文件真实存在，防止审稿人拿到死链。
        if t["input"].get("media"):
            imgs = (t["input"].get("media") or {}).get("images") or []
            doc_dir = os.path.dirname(os.path.abspath(OUT))
            for img in imgs:
                abs_path = os.path.join(ROOT, img) if not os.path.isabs(img) else img
                if not os.path.isfile(abs_path):
                    raise RuntimeError("图片资产缺失（按文档位置解析会死链）: %s" % abs_path)
                rel_doc = os.path.relpath(abs_path, doc_dir).replace("\\", "/")
                lines.append("\n**本轮图片**（模型收到的原图，相对本文档路径 %s）：\n"
                             % rel_doc)
                lines.append("[%s](%s)\n" % (os.path.basename(img), rel_doc))
            # 前情提要：从早前节点找同话题 HER 原话（不润色、带来源时间）
            if cid.endswith("t07"):
                t05 = next((x for x in ep["turns"] if x["case_id"] == "L1_t05"), None)
                if t05:
                    lines.append("\n**前情提要（既往事实出处，她 2026-10-14 原话）**：")
                    for m in t05["input"]["messages"]:
                        if m["role"] == "HER":
                            lines.append("- %s（t05，%s）" % (m["text"], m["ts"]))
                    lines.append("")
        intent = (t["input"].get("intent") or {}).get("text")
        if intent:
            lines.append("\n**我的意图**：%s" % intent)
        if t["input"].get("may_decline"):
            lines.append("\n**本轮允许不回**（不回与短告别均自然）")
        for label, cand in (("A", a), ("B", b)):
            v = cand[cid]
            if v["contract_veto"]:
                lines.append("\n**匿名回复 %s**：〔runner 裁决契约失败：%s，无可展示文本——失败如实计入可用性，不作质量评价〕"
                             % (label, v["contract_veto"]))
            elif v["kind"] == "no_reply":
                reason = v["payload"]["reason"] if v["payload"] else ""
                lines.append("\n**匿名回复 %s**：〔建议动作：**不回复**（无需发送文本）〕"
                             % label)
                if reason:
                    lines.append("> 模型给出理由（仅备注，**不作为待发正文**）：%s" % reason)
            elif v["kind"] == "text":
                lines.append("\n**匿名回复 %s**\n\n> %s" % (label, v["payload"]["reply"]))
            else:
                lines.append("\n**匿名回复 %s**：〔展示层提取失败——runner 裁决契约通过（score=%s），"
                             "但回复文本无法结构化提取；属展示缺陷，契约状态以 runner 为准〕"
                             % (label, v["score"]))
        lines.append("\n**评价**（A、B 分别勾选其一；再做总选）：\n")
        lines.append("- 回复 A：[ ] 原样可发 [ ] 修改后可发（写怎么改） [ ] 不想发（写为什么）"
                     " [ ] 不回更合适")
        lines.append("- 回复 B：[ ] 原样可发 [ ] 修改后可发（写怎么改） [ ] 不想发（写为什么）"
                     " [ ] 不回更合适")
        lines.append("- 总选：[ ] A 更好 [ ] B 更好 [ ] 都可 [ ] 都不想发"
                     "  附加：[ ] 这轮不回更合适（若勾，写哪句都不自然的原因）\n")
    lines.append("\n---\n\n**评分键（评完再看）**：seed=%d，A 身份映射见 %s/blind_key.json\n" % (SEED, RUN))
    open(OUT, "w", encoding="utf-8").write("\n".join(lines))
    json.dump({"seed": SEED, "a_is_baseline": keymap,
               "run_baseline": os.path.join(RUN, "baseline_L1"),
               "run_beta": os.path.join(RUN, "beta_L1"),
               "frozen_episode": EP_FROZEN, "frozen_episode_sha256": ep_sha,
               "scoring_version": SCORING_VERSION,
               "availability": {"baseline": "%d/12" % contract_ok(base),
                                "beta_single": "%d/12" % contract_ok(beta)}},
              open(os.path.join(RUN, "blind_key.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print("盲评材料 %d 组 → %s（基线 %d/12，β %d/12；文字 %d+%d，不回 %d+%d）"
          % (n, OUT, contract_ok(base), contract_ok(beta), bt, yt, bn, yn))


if __name__ == "__main__":
    main()
