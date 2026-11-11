# -*- coding: utf-8 -*-
"""tools/consolidate_v3_deletion.py — BATCH-V3-DELETION-20261024 收口整合器（离线，零真实生成）。

r2 复核后修订（VERDICT_AND_EXECUTION §3 A 包）：
  A1 归属更正 + 三层分列：旧版手填 PAIRING 有两处**标签与判词写反**（E004_T08、E006_T02）
     与一处措辞归属错（E004_T07 "排班"实为 v3）。不再给单一"supports"计数冒充胜率；改为每
     请求位分三层核对：**首选内容 / 交付·来源契约 / 备选风险**，各层独立统计。全部标注为
     worker 初审（含已更正归属），非独立胜率、非用户偏好、非盲评。
  A2 展示完整：改名"重点公开对照（非盲评）"；备选恢复**完整原文**（不再 [:40] 无省略号）；
     日期组等**补必要旧史**（从当时冻结运行时副本 .copy.json 取已进入请求的相关历史/画像/意图）。

两份产物直接取自**当时付费证据**，不手写背景、不改原始答卷、不重跑：
  1) V3_KEY_PUBLIC_GROUPS.md  —— 6 组重点公开对照（原版甲 / v3 乙）。
  2) PAIRED_CONCLUSION.json   —— 24 位三层初审结论 + 逐层计数 + 整合判决。

结论大方向不变：总 token −60.79% 的删除方向签收；v3 质量/交付未达生产替换门槛；字数与输出
token 增加仅描述、不单独扣分。依用户 2026-10-24 更正：不以回复更短/更利落为条件。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REVIEW = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                      "real_v3_independent_review_20261024")
LIVE = os.path.join(ROOT, "logs", "v3_deletion_live_20261024")
EO = json.load(open(os.path.join(REVIEW, "EXTRACTED_OUTPUTS.json"), encoding="utf-8"))
BY = {(r["arm"], r["case_id"]): r for r in EO}

# A2：题面/历史取**当时冻结运行时副本**（绑到本批），不再回读工作树 episode。
# 两臂各一份副本；本次散列仍一致，取 parent 副本为题面源，candidate 副本备查。
EPS = {}
for ep in ("E004", "E005", "E006"):
    EPS[ep] = json.load(open(os.path.join(LIVE, "parent_%s" % ep, ep + ".copy.json"),
                             encoding="utf-8"))
    cand_copy = json.load(open(os.path.join(LIVE, "candidate_%s" % ep, ep + ".copy.json"),
                               encoding="utf-8"))
    assert [t["case_id"] for t in EPS[ep]["turns"]] == [t["case_id"] for t in cand_copy["turns"]], \
        "两臂副本 case_id 不一致"


def turn_idx(case_id):
    ep = case_id.split("_")[0]
    for i, t in enumerate(EPS[ep]["turns"]):
        if t["case_id"] == case_id:
            return ep, i
    raise KeyError(case_id)


def turn_of(case_id):
    ep, i = turn_idx(case_id)
    return EPS[ep]["turns"][i]


def prior_messages(case_id, n=2):
    """同故事该位之前 n 轮的真实已发送消息（进过请求的历史；不含模型草稿）。"""
    ep, i = turn_idx(case_id)
    out = []
    for t in EPS[ep]["turns"][max(0, i - n):i]:
        out.extend(t["input"]["messages"])
    return out


def _msg_lines(msgs):
    return "\n".join("> %s %s：%s" % (m["ts"], m["role"], m["text"]) for m in msgs)


def backups_full(rec):
    """非空备选**完整原文**；'null' 字符串单列为不可用文本，不当合法备选。"""
    out = []
    for slot in (rec.get("all_texts") or []):
        s = slot.get("slot", "")
        if s == "response.recommended":
            continue
        txt = (slot.get("text") or "")
        if txt.strip() == "":
            continue
        key = s.split("response.")[-1] if s.startswith("response.") else s
        out.append((key, txt))
    return out


# ───────────────────────── 三层初审（24 位；worker 初审，含 r2 复核更正）─────────────────────────
# pc: 首选内容 supports ∈ candidate/original/both/undecided（只按正文，不含契约/备选）
# ct: 交付·来源契约 ∈ both_ok / candidate_fail / original_fail（取自真实程序 veto/score 与非法来源）
# bk: 备选风险 ∈ none / candidate / original / both（有字段原文支持的备选问题）
L3 = {
    "E004_T01": ("both", "both_ok", "none", "承接后劲并发问 / 承接问剧名，均有下一回合入口"),
    "E004_T02": ("both", "both_ok", "none", "原版承认没猜准回问；v3 加'最没存在感'猜测依据属未核经历但未捏角色名——存疑,不判硬伤"),
    "E004_T03": ("both", "both_ok", "none", "完整偏好双方均收到;口味表达均可"),
    "E004_T04": ("both", "both_ok", "none", "低信息主动,简短应答成立"),
    "E004_T05": ("both", "both_ok", "none", "拥挤预测双方均未验证;去查票是新提议非已查"),
    "E004_T06": ("both", "both_ok", "none", "接受并商量;主动提时间/同行合法"),
    "E004_T07": ("both", "both_ok", "candidate", "时间有准信再说;措辞'等我把排班弄清楚'来自 v3(她做运营非排班岗),属可讨论非硬事实;v3 备选'加场是晚上场'无出处场次 且 directions[3]='null' 字符串不可用"),
    "E004_T08": ("candidate", "both_ok", "both", "首选:v3'图收到了…哪个部分最值得'未评价未见画面;原版'照片看着挺有意思'无图评照片(images=[])→首选内容候选相对支持(仍有解释空间)。备选:原版'照片看着不错'、v3'这几张构图可以/会拍'两臂均假评画面→另列 both"),
    "E005_T01": ("both", "both_ok", "none", "接住互损;v3 更快进安排,优劣须用户口味,不代投"),
    "E005_T02": ("both", "both_ok", "none", "顺势提吃饭 / 加价深蹲续玩笑,两条行动均可;均有暧昧备选"),
    "E005_T03": ("both", "both_ok", "none", "选先练再吃/先练并商量日期均为新共识推进"),
    "E005_T04": ("undecided", "both_ok", "none", "目标=接招/反撩;两者偏安排/解释,互动层观察,不因长度扣分,非硬 veto"),
    "E005_T05": ("both", "both_ok", "none", "留待明天/记个半死接梗未锁死;暂定与确定区分"),
    "E005_T06": ("original", "both_ok", "candidate", "原版接受后天等她定时可;v3 把 11-08(周日)所说'下周二(11-10)三点前有空'当成本轮 11-11 三点前都空=日期作用域迁移,无来源(见 T05 旧史)"),
    "E005_T07": ("both", "both_ok", "none", "双方真实确认后'后天见';原版'我去订'是未来行动非已订"),
    "E005_T08": ("both", "both_ok", "both", "接吐槽选清淡均可;备选:原版 bad_boy'肿三天还照练不误'、v3'肿了三天还去带课'由教练职业编造亲历→both"),
    "E006_T01": ("both", "both_ok", "none", "关注挫追问原因 / 接情绪问刺点;首轮回一句即可"),
    "E006_T02": ("both", "candidate_fail", "candidate", "首选正文双方基本可(遵守不分析);但 v3 整份答卷 analysis.scene_facts[0].source_ids=['t1_0'](本轮合法 ID=m0/m1)→程序 0 分 veto=illegal_source_id;t1_0。交付/来源契约层原版无 veto 占优。v3 备选'今晚别聊工作'忽视 HER 当前想骂几句→备选风险 candidate"),
    "E006_T03": ("both", "both_ok", "none", "抓住当众否定,跟住新信息"),
    "E006_T04": ("both", "both_ok", "none", "转米线并提新机会,'改天去蹭'非虚构去过"),
    "E006_T05": ("both", "both_ok", "both", "均给实质观点并反问;v3'半小时想明白'可能泛化修辞,不据此硬判;备选:原版'拖了半月最后选最初想法'、v3'朋友都听烦了'无出处亲历→both"),
    "E006_T06": ("both", "both_ok", "original", "主动求看法可有条件给倾向;原版备选 directions[0] 用第三人'她'向用户问策略=军师视角混入可发备选→备选风险 original"),
    "E006_T07": ("both", "both_ok", "none", "没经历但有感觉,观察/共情不冒充亲历"),
    "E006_T08": ("both", "both_ok", "none", "留待她来找;'那就这么定了'可指等她联系,不硬判已约;v3'随时有空'承诺偏满=软观察非失败"),
}

GROUPS = ["E004_T08", "E005_T02", "E005_T04", "E005_T06", "E006_T05", "E006_T08"]
GROUP_TITLE = {
    "E004_T08": "媒体/无图装看图", "E005_T02": "目标启用·主动/反撩",
    "E005_T04": "目标切换后的推进", "E005_T06": "日期作用域迁移（含必要旧史）",
    "E006_T05": "深聊给实质观点", "E006_T08": "收尾是否自然停下",
}


def build_public_md():
    md = ["# BATCH-V3-DELETION-20261024 收口：6 组重点公开对照（原版甲 / v3 乙）",
          "",
          "> 命名说明（A2）：本表标题已直接标注甲=原版、乙=v3，属**公开对照**，不是盲评，",
          "不冒充无偏胜率样本。保留用户语气裁断区，但不为改名另造盲评平台。",
          "",
          "生成：tools/consolidate_v3_deletion.py（离线，零真实生成）。题面/历史取当时冻结运行时副本",
          "`logs/v3_deletion_live_20261024/parent_E00x/E00x.copy.json`（已绑定本批，非工作树 episode）；",
          "首选/备选原文取 EXTRACTED_OUTPUTS.json（当时 reply_raw.txt 解析并绑 raw_sha256 +",
          "case_input_sha256），备选恢复**完整原文**。正文存在且程序契约通过 ≠ 用户愿发。",
          "**本批不要求回复更短/更利落**；字数与输出 token 只描述。",
          ""]
    for cid in GROUPS:
        p, c = BY[("parent", cid)], BY[("candidate", cid)]
        t = turn_of(cid)
        inp = t["input"]
        pc, ct, bk, ev = L3[cid]
        md.append("---")
        md.append("")
        md.append("## %s · %s" % (cid, GROUP_TITLE[cid]))
        md.append("")
        md.append("**实际请求时间**：%s" % inp["clock"])
        it = inp.get("intent") or {}
        if it:
            md.append("**当轮意图**：%s（enabled=%s, status=%s）" % (
                it.get("text") or "（关闭清空）", it.get("enabled"), it.get("status")))
        md.append("**允许不回**：%s" % bool(inp.get("may_decline")))
        prior = prior_messages(cid, 2)
        if prior:
            md.append("")
            md.append("<details><summary>展开必要旧史（同故事前序真实已发送消息，不含模型草稿）</summary>")
            md.append("")
            md.append(_msg_lines(prior))
            md.append("")
            md.append("</details>")
        md.append("")
        md.append("**本轮真实消息**：")
        md.append("")
        md.append(_msg_lines(inp["messages"]))
        md.append("")
        if inp.get("note"):
            md.append("**军师备注（私密，不外发）**：%s" % inp["note"])
            md.append("")
        md.append("**甲（原版）首选**：%s" % p["primary"])
        md.append("")
        md.append("**乙（v3）首选**：%s" % c["primary"])
        md.append("")
        md.append("（甲 raw_sha=%s…｜乙 raw_sha=%s…｜images=%s｜程序 甲%s/乙%s｜veto 甲=%s 乙=%s）" % (
            p["raw_sha256"][:12], c["raw_sha256"][:12],
            (p.get("images") or c.get("images") or []),
            p["score10"], c["score10"], p["veto"], c["veto"]))
        md.append("")
        for lab, rec in (("甲", p), ("乙", c)):
            bs = backups_full(rec)
            if bs:
                md.append("**%s 备选（完整原文）**：" % lab)
                md.append("")
                for key, txt in bs:
                    md.append("- %s：%s" % (key, txt))
                md.append("")
        md.append("**三层初审（worker，非愿发）**：首选内容→%s；交付·来源契约→%s；备选风险→%s。%s"
                  % (pc, ct, bk, ev))
        md.append("")
        md.append("用户语气裁断：甲 □原样愿发 □改后愿发 □不想发；乙 □原样愿发 □改后愿发 □不想发。"
                  "总选 □甲 □乙 □都行 □都不发 □不回更合适。")
        md.append("")
    out = os.path.join(REVIEW, "V3_KEY_PUBLIC_GROUPS.md")
    open(out, "w", encoding="utf-8").write("\n".join(md))
    return out, len(GROUPS)


def _count(idx):
    from collections import Counter
    return dict(Counter(v[idx] for v in L3.values()))


def build_paired_json():
    rows = []
    for cid, (pc, ct, bk, ev) in L3.items():
        p, c = BY[("parent", cid)], BY[("candidate", cid)]
        rows.append({
            "case_id": cid,
            "layer1_primary_content": pc,
            "layer2_delivery_contract": ct,
            "layer3_backup_risk": bk,
            "rationale": ev,
            "parent_score10": p["score10"], "parent_veto": p["veto"],
            "candidate_score10": c["score10"], "candidate_veto": c["veto"],
            "parent_invalid_source_ids": p.get("invalid_fact_source_ids"),
            "candidate_invalid_source_ids": c.get("invalid_fact_source_ids"),
            "parent_literal_null_slots": p.get("literal_null_slots"),
            "candidate_literal_null_slots": c.get("literal_null_slots"),
            "parent_raw_sha256": p["raw_sha256"],
            "candidate_raw_sha256": c["raw_sha256"],
            "case_input_sha256": p["case_input_sha256"],
            "parent_primary_chars": p["primary_chars"],
            "candidate_primary_chars": c["primary_chars"],
        })
    doc = {
        "batch_id": "BATCH-V3-DELETION-20261024",
        "authorization_id": "AUTH-V3-DELETION-20261024-01",
        "arm_mapping": {"parent": "reference_product_v1", "candidate": "v3_system_slim"},
        "revision": "r2 复核更正（VERDICT_AND_EXECUTION §3 A 包）",
        "review_status": "worker_initial_audit_not_user_rated_not_blind_review_not_winrate",
        "attribution_corrections": [
            "E004_T08：旧版 supports=original 与判词（原版首选无图评照片）矛盾；更正首选内容=候选相对支持，"
            "备选两臂假评画面另列 both。",
            "E006_T02：旧版 supports=candidate 却把 illegal_source_id:t1_0/0分 挂给原版；实为 v3 契约失败，"
            "原版无 veto。更正：首选内容 both、交付契约层原版占优（candidate_fail 记在 v3）。",
            "E004_T07：旧版称原版说'排班'；'等我把排班弄清楚'实际来自 v3。更正归属，不新增硬失败。",
        ],
        "provenance": "NODE_REVIEW.md 逐字段初审 + EXTRACTED_OUTPUTS（当时真实输出/散列/程序 veto）三层形式化；"
                      "程序分不冒充质量。",
        "optimization_objective_note":
            "用户 2026-10-24 更正：不以回复更短/更利落为目标或晋级条件；质量相当而总资源实质下降即支持效率型保留。",
        "layer_counts": {
            "primary_content": _count(0),
            "delivery_contract": _count(1),
            "backup_risk": _count(2),
        },
        "old_single_supports_counts_superseded": {
            "note": "上一版单一 supports 计数（candidate1/original2/both16/borderline4/undecided1）含两处归属写反，"
                    "不作为独立胜率引用；改由下面逐层计数呈现。",
            "values": {"candidate": 1, "original": 2, "both": 16, "borderline": 4, "undecided": 1},
        },
        "rows": rows,
        "verdict": {
            "resource": "输入 token −63.93%，总 token −60.79%（182,785→71,667，实际 API usage，签收）；"
                        "输出 token +19.15%、首选字数 +64.74% 仅描述不扣分；单次耗时本批 p50 相近、p95 较短，"
                        "不升级为长期保证；金额未核不填零。",
            "quality_delivery": "首选内容层：除 E004_T08（v3 相对未犯错）外双方基本可用；交付契约层仅 v3 "
                                "E006_T02 来源契约失败(0 分)；备选风险层两臂均有事实问题（无图评画面、日期迁移、"
                                "职业/无出处亲历、军师视角混入、无出处场次、'null'字符串槽）。严重错误不被省 token 抵消。",
            "decision": "删除大量 system 复杂度有真实资源收益、方向保留；v3 因 E006_T02 契约失败 + 多处事实/备选"
                        "问题，**尚未达生产替换门槛 → 不晋级冠军**；reference_product_v1 与 v3_system_slim 身份冻结作对照。",
            "closure": "48 started / 48 completed / 0 failed；账本已追加 authorization_closed（额度耗尽，不得复活/覆盖/补跑）。",
            "not_claimed": "本表非独立胜率、非用户愿发、非泛化；96 公开位不等于 96 条独立故事。",
        },
    }
    out = os.path.join(REVIEW, "PAIRED_CONCLUSION.json")
    open(out, "w", encoding="utf-8").write(json.dumps(doc, ensure_ascii=False, indent=1))
    return out, doc["layer_counts"]


if __name__ == "__main__":
    a, n = build_public_md()
    b, counts = build_paired_json()
    print("wrote重点公开对照 (%d 组) -> %s" % (n, os.path.relpath(a, ROOT)))
    print("wrote三层配对结论 -> %s" % os.path.relpath(b, ROOT))
    for k, v in counts.items():
        print("  %-20s %s" % (k, v))
