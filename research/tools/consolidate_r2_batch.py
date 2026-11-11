# -*- coding: utf-8 -*-
"""tools/consolidate_r2_batch.py — BATCH-R2-CTX-20261024 结果收口（离线，零生成、不重跑旧答卷）。

产出 R2_VERDICT.md：本批真实结果判决 + 资源表 + 四层配对计数 + ≤6 组重点公开对照（真实原文/散列）
+ 关闭证据。逐字段完整初审见 r2_real_independent_review_20261025/NODE_REVIEW.md（独立复核会话）；
本文件只做 worker 侧收口索引与公开对照，程序分不冒充质量、不冒称用户愿发或胜率。
数据源：R2_EXTRACTED_OUTPUTS.json（真实输出/散列/veto/tokens，绑 raw_sha）+ 运行时 .copy.json 副本。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REVIEW25 = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "r2_real_independent_review_20261025")
REFS = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "r2_next_batch_20261024")
LIVE = os.path.join(ROOT, "logs", "r2_ctx_live_20261024")
RES = json.load(open(os.path.join(REFS, "R2_INDEPENDENT_RESULTS.json"), encoding="utf-8"))
EO = json.load(open(os.path.join(REFS, "R2_EXTRACTED_OUTPUTS.json"), encoding="utf-8"))
BY = {(r["arm"], r["case_id"]): r for r in EO}

EPS = {}
for ep in ("E007", "E010", "E012"):
    EPS[ep] = json.load(open(os.path.join(LIVE, "parent_%s" % ep, ep + ".copy.json"), encoding="utf-8"))


def turn(case_id):
    ep = case_id.split("_")[0]
    for t in EPS[ep]["turns"]:
        if t["case_id"] == case_id:
            return t


def prior(case_id, n=2):
    ep = case_id.split("_")[0]
    idx = [i for i, t in enumerate(EPS[ep]["turns"]) if t["case_id"] == case_id][0]
    out = []
    for t in EPS[ep]["turns"][max(0, idx - n):idx]:
        out.extend(t["input"]["messages"])
    return out


def lines(msgs):
    return "\n".join("> %s %s：%s" % (m["ts"], m["role"], m["text"]) for m in msgs)


def backups_full(rec):
    out = []
    for x in (rec.get("all_texts") or []):
        s = x.get("slot", "")
        if s == "response.recommended":
            continue
        t = x.get("text") or ""
        if t.strip():
            out.append((s.split("response.")[-1] if s.startswith("response.") else s, t))
    return out


GROUPS = [
    ("E007_T03", "双方给现场作无据保证（内容层，非来源ID）"),
    ("E007_T08", "交付层原版失败 vs r2 稳（首选 vs 契约分层）"),
    ("E010_T03", "r2 有依据地限制自己掌握的路线（正向）"),
    ("E010_T07", "r2 完整装备清单服务明确请求（长≠扣分）"),
    ("E012_T08", "已发生回顾：r2 收尾不复活旧约 vs 原版仍说周六见"),
    ("E007_T02", "备选混入军师指令（整槽不可发）"),
]


def build():
    m = RES["metrics"]
    p, c = m["parent"], m["candidate"]
    d = RES["candidate_vs_parent_percent"]
    md = ["# BATCH-R2-CTX-20261024 收口：真实新架构对照结果与判决（2026-10-25）",
          "",
          "worker 侧收口索引；完整逐字段初审见 `r2_real_independent_review_20261025/NODE_REVIEW.md`，",
          "一手复算见同目录及本目录 `R2_INDEPENDENT_RESULTS.json`/`R2_EXTRACTED_OUTPUTS.json`。",
          "程序分与语义并列、不冒充质量或用户愿发；三故事皆公开开发集，不称泛化/生产冠军。",
          "**依用户 2026-10-24 更正：回复长度不作目标或晋级条件。**",
          "",
          "## 1. 资源（实际 API usage，非估计字符）",
          "",
          "| 每臂24次 | 原版 | r2 | 变化 |",
          "|---|---:|---:|---|",
          "| prompt token | %d | %d | %+.2f%% |" % (p["prompt_tokens"], c["prompt_tokens"], d["prompt_tokens"]),
          "| completion token | %d | %d | %+.2f%%（不单独扣质量分） |" % (p["completion_tokens"], c["completion_tokens"], d["completion_tokens"]),
          "| 总 token | %d | %d | **%+.2f%%** |" % (p["total_tokens"], c["total_tokens"], d["total_tokens"]),
          "| 首选均字数 | %.1f | %.1f | %+.2f%%（仅描述） |" % (p["primary_mean_chars"], c["primary_mean_chars"], d["primary_mean_chars"]),
          "| 等待 p50 | %.1fs | %.1fs | %+.2f%% |" % (p["latency_ms"]["p50"]/1000, c["latency_ms"]["p50"]/1000, d["latency_p50_percent"]),
          "| 等待 p95 | %.1fs | %.1fs | **%+.2f%%（尾部退化）** |" % (p["latency_ms"]["p95"]/1000, c["latency_ms"]["p95"]/1000, d["latency_p95_percent"]),
          "| 单次最大 | %.1fs | %.1fs | — |" % (p["latency_ms"]["max"]/1000, c["latency_ms"]["max"]/1000),
          "| 累计调用等待 | %.1fs | %.1fs | %+.2f%% |" % (p["latency_ms"]["sum"]/1000, c["latency_ms"]["sum"]/1000, d["latency_sum_percent"]),
          "| 程序均分/硬veto | %.2f / %d | %.2f / %d | 不当自然度/总优势 |" % (p["program_mean10"], p["veto_count"], c["program_mean10"], c["veto_count"]),
          "",
          "本批墙钟约 %d 分 %d 秒；金额未知不填零；无首token/解码分段数据，不精确归因时延变化。" % (
              RES["wall_elapsed_seconds_from_event_timestamps"]//60, RES["wall_elapsed_seconds_from_event_timestamps"] % 60),
          "",
          "**资源判断：省 token 成立（总 −56.56%、prompt −59.49%）；提速未成立**（p50≈持平、p95 +45.91%、"
          "累计等待 +20.11%）。不得把省量统称提速，也不以“可能服务波动”抹去尾延迟退化信号。",
          "",
          "## 2. 四层配对计数（首选内容 / 交付·来源契约 / 备选风险 / 目标·反馈动作）",
          "",
          "逐位判断以 NODE_REVIEW 为准，此处仅汇总额度分布；未知/存疑保留，不当通过：",
          "- 首选内容：多数 both（双方基本可用，少数单方相对稳）；r2 在 E012_T08 相对更好、E007_T08 首选相对稳。",
          "- 交付·来源契约：原版 E007_T08 因 directions 长度 3≠4 程序 0 分（合法 JSON，非传输失败）；"
          "r2 两臂本批**无非法来源 ID**（修 verify 抽取位置后独立复算 illegal_source_slots=0/0）。",
          "- 备选风险：双方都有无据现场/路线/装备/亲历、军师指令混入可发槽、关闭后备选再推、困了又加选择题。",
          "- 目标·反馈动作：r2 展示可用能力——目标切换、接受暂缓、调整分工、已发生后收尾；E010_T03/T05/T06 "
          "有依据地限制与商量，未压制合理私人提议。",
          "",
          "## 3. 判决",
          "",
          "**保留精简 system + 真实事件记忆 + 目标/上下文架构作为研发方向；v4_intent_context_r2 冻结作后续参照，"
          "不接入默认生产。** 依据：资源收益明确、部分目标链可用；但真实事实（无据现场/路线/装备/亲历）与备选视角"
          "仍有缺口，且等待未改善（尾部更差）。**并非因回复变长**。原版同样有本批可见错误（E007_T03 补现场、"
          "E012_T08 已发生仍写周六见），不能仅按程序均分判谁更强。",
          "",
          "归因诚实：本批是整套简化架构对原版的比较，不单独宣称三条护栏或意图模块各自的因果贡献；固定 HER 后续与"
          "ME 历史不因候选改变，不能当候选实际推进关系的证据。三护栏进入请求≠三类真实错误已消除；本批未复现旧 E005"
          "改期题、无图片输入，不能称旧日期题/视觉能力已验证通过。",
          "",
          "## 4. 关闭证据",
          "",
          "48 started / 48 completed / 0 failed；6 独立 run、48 响应 ID 唯一、题面与输入散列绑定；"
          "签名单绑定修订后身份 `37076a76…`（非旧 `eaf6d267…`）；账本已追加 `authorization_closed`（额度耗尽，"
          "不得复活/覆盖/补跑）。",
          "",
          "## 5. 重点公开对照（≤6 组；甲=原版 乙=r2，公开非盲评）",
          ""]
    for cid, title in GROUPS:
        p, c = BY[("parent", cid)], BY[("candidate", cid)]
        t = turn(cid)
        inp = t["input"]
        md += ["---", "", "### %s · %s" % (cid, title), "",
               "**请求时间**：%s" % inp["clock"]]
        it = inp.get("intent") or {}
        if it:
            md.append("**当轮意图**：%s（enabled=%s,status=%s）" % (it.get("text") or "（关闭）", it.get("enabled"), it.get("status")))
        if inp.get("note"):
            md.append("**军师备注（私密不外发）**：%s" % inp["note"])
        pr = prior(cid, 2)
        if pr:
            md += ["", "<details><summary>必要旧史（前序真实已发送，不含草稿）</summary>", "", lines(pr), "", "</details>"]
        md += ["", "**本轮真实消息**：", "", lines(inp["messages"]), "",
               "**甲（原版）首选**：%s" % p["primary"], "",
               "**乙（r2）首选**：%s" % c["primary"], "",
               "（甲 raw=%s…｜乙 raw=%s…｜程序 甲%s/乙%s｜veto 甲=%s 乙=%s）" % (
                   p["raw_sha256"][:12], c["raw_sha256"][:12], p["score10"], c["score10"], p["veto"], c["veto"]), ""]
        for lab, rec in (("甲", p), ("乙", c)):
            bs = backups_full(rec)
            if bs:
                md.append("**%s 备选（完整原文）**：" % lab)
                md.append("")
                for k, tx in bs:
                    md.append("- %s：%s" % (k, tx))
                md.append("")
        md.append("用户语气裁断：甲 □愿发 □改后愿发 □不想发；乙 □愿发 □改后愿发 □不想发；总选 □甲 □乙 □都行 □都不发 □不回更合适。")
        md.append("")
    out = os.path.join(REFS, "R2_VERDICT.md")
    open(out, "w", encoding="utf-8").write("\n".join(md))
    return out


if __name__ == "__main__":
    print("wrote r2 收口判决 ->", os.path.relpath(build(), ROOT))
