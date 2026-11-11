# -*- coding: utf-8 -*-
"""tools/benchmark_v1/inventory.py — Benchmark V1 Phase 1 数据集全面盘点。

任务：对当代故事库（benchmark/stories_v2 的 208 集库）做逐篇盘点、十大专题启发式分类、
谱系隔离审计，产出：
  1. refs/benchmark_v1_20261107/phase1/DATASET_INVENTORY.csv   （208 行 + 表头）
  2. refs/benchmark_v1_20261107/phase1/TOPIC_COVERAGE_MATRIX.csv（T01–T10 十行 + 表头）
  3. stdout 摘要（--json 机器可读全量 / --markdown 报告用表格）

纪律与边界：
  - 纯标准库、零模型调用、只读输入；不改任何既有文件。
  - benchmark/b_line/ 只读 B_LINE_MANIFEST.json 元数据（source_path/sha/stage）；
    B_LINE_README.md 仅用于登记其 v1 口径未同步问题。绝不读取任何 episode 正文。
  - 所有数字以本脚本实测为准；勘误清单来自 refs/dataset_review_20261107/
    A_COMPOSITION_AND_PROVENANCE.md §3.3 与 C_GOLD_AND_SCORING.md §4.1 的勘误台账
    （在下方 ERRATA 常量中逐条注明出处）。
  - primary_topic 是启发式分类（用户指导书 §2.3），每行如实标注 annotation_basis 与
    annotation_confidence，不声称"已完成独立语义审查"。

运行：python -I tools/benchmark_v1/inventory.py [--json] [--markdown]
"""
import argparse
import csv
import hashlib
import json
import os
import sys
from collections import Counter, OrderedDict, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SV2 = os.path.join(ROOT, "benchmark", "stories_v2")
OUT_DIR = os.path.join(ROOT, "refs", "benchmark_v1_20261107", "phase1")
CUSTODY_FP = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                          "global_harness_program_20261030", "STORY_CUSTODY_LEDGER.json")
DOMAIN_NORM_FP = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                              "global_harness_program_20261030", "W03-06_DOMAIN_NORMALIZATION_20261102.json")
GOLD_DIR = os.path.join(SV2, "GOLD_SIDECAR")
GOLD_INDEX_FP = os.path.join(GOLD_DIR, "GOLD_INDEX.json")
B_LINE_MANIFEST_FP = os.path.join(ROOT, "benchmark", "b_line", "B_LINE_MANIFEST.json")
B_LINE_README_FP = os.path.join(ROOT, "benchmark", "b_line", "B_LINE_README.md")
ANCHORS_MANIFEST_FP = os.path.join(SV2, "anchors_20261028", "ANCHORS_MANIFEST.json")

LIB_STAGES = OrderedDict([
    ("stage2", 12), ("stage3", 8), ("stage4", 24),
    ("wave1", 12), ("wave2", 12), ("wave3", 12), ("wave3_ext", None),
])
# 各波 manifest 与 stories 键（与 tools/story_skeleton_dedupe.py 同源口径）
STAGE_MANIFESTS = {
    "stage2": "STAGE2_MANIFEST.json",
    "stage3": "STAGE3_MANIFEST.json",
    "stage4": "STAGE4_MANIFEST.json",
    "wave1": "STAGE1_MANIFEST.json",
    "wave2": "STAGE1_MANIFEST.json",
    "wave3": "STAGE1_MANIFEST.json",
}
GOLD_WAVE_FILE = {s: "GOLD_%s.json" % s for s in LIB_STAGES}

INVENTORY_COLUMNS = [
    "episode_id", "source_path", "stage", "turn_count", "domain", "primary_topic",
    "secondary_topics", "length_class", "relationship_stage", "gold_available",
    "gold_conflict_count", "source_conflict_flag", "known_errata", "exposure_status",
    "b_line_related", "lineage_group_id", "annotation_basis", "annotation_confidence",
    "review_status", "eligible_for_dev", "eligible_for_selection",
    "eligible_for_holdout", "eligible_for_reuse", "reuse_block_reason", "episode_sha256",
]
MATRIX_COLUMNS = [
    "topic", "episodes", "turns", "with_assertions_turns", "sealed_n", "exposed_n",
    "b_line_related_n", "known_errata_n", "high_confidence_n",
]

TOPICS = ["T01", "T02", "T03", "T04", "T05", "T06", "T07", "T08", "T09", "T10"]
TOPIC_NAMES = {
    "T01": "日常聊天/自然接话/幽默", "T02": "调情/暧昧/反撩/拉扯",
    "T03": "深聊/价值观/思想交流", "T04": "主动推进/邀约/关系发展",
    "T05": "情绪理解/关心/安慰", "T06": "用户意图/口吻/改写/多候选",
    "T07": "事实/人物归属/不确定性", "T08": "记忆/知识库/相关信息提取",
    "T09": "长程聊天/状态变化/纠错取消", "T10": "反事实/边界/抗干扰/状态污染",
}

# ---------------------------------------------------------------------------
# 勘误台账（出处：refs/dataset_review_20261107/C_GOLD_AND_SCORING.md §4.1、
# A_COMPOSITION_AND_PROVENANCE.md §3.3；blocking=该瑕疵使整篇的评分使用被阻断）
# 每条: (位置, 内容, 处置, blocking)
# ---------------------------------------------------------------------------
ERRATA = {
    "E021": [("E021_T07", "时钟(周三11-11)与卡面'今晚有球'设计矛盾", "W16-05 v1 修时钟(T05/T06 前移周一晚)；FS1 E3 severe 记素材缺陷位剔除", False),
             ("E021_T11/T12", "'下季度忙完'与'五月起'季候衔接略松", "登记保留不阻塞(11月时钟下年审季=Q1 可自洽)", False)],
    "E022": [("E022_T12", "收尾轮 ME 提'表哥介绍的练习册'为轻量背景补注", "登记保留不阻塞(HER 台词自带承接；两臂同等可见)", False)],
    "E029": [("E029_T07", "12月时钟下 HER 问'九月假期'——时间视野跳变", "后备池待修，尚未修(诚实保留在池)", True)],
    "E062": [("E062_F06", "快门数挂在镜头上=品类错误(属机身)", "T24 探针作废不计失败；stories v2 下一版修复(当前库仍带此瑕疵)", True)],
    "E065": [("E065_T21", "剧本'到账15200'与'3600x3+3800=14600'自相矛盾", "已裁决：算术挑战不计胜负；候选 T20 当场抓错记 factuality 正向", False)],
    "E149": [("E149_T05", "CF 孪生保留源篇'明天/后天'措辞与 +1 天时间戳不一致", "登记，不影响 CF1 判决方向", False)],
    "E164": [("E164_T05", "账目陷阱：HER 报'省60/三十七块七'与自报 24 次除数不符", "有意设计改造(LRE117 先例)：HER 报错数、卡面引导指出、gold 不背书错数——属设计非缺陷", False)],
    "LRE117": [("LRE117_T04/T05", "部分原子 asserted 应为 stale(GT 瑕疵)", "登记回素材维护轨，待修(影响 gold 侧判分口径)", True)],
    "LRE125": [("LRE125_A07", "原子重复登记(GT 瑕疵，LD1 批发现)", "登记回素材维护轨", False)],
    "LRE128": [("LRE128_T35", "账目陷阱：HER 报'省60'实为超 60", "有意设计改造(同 E164)——属设计非缺陷", False)],
}
ERRATA_SOURCES = ("出处：refs/dataset_review_20261107/C_GOLD_AND_SCORING.md §4.1 素材勘误表；"
                  "refs/dataset_review_20261107/A_COMPOSITION_AND_PROVENANCE.md §3.3")

# ---------------------------------------------------------------------------
# 域派生：与 tools/story_skeleton_dedupe.py 同一套 DOMAIN_RULES + 归一映射
# ---------------------------------------------------------------------------
DOMAIN_RULES = [("医疗健康", ["甲状腺", "随访", "腰", "膝盖", "体检", "医嘱", "复健", "偏方", "过敏", "牙"]),
                ("运动健身", ["球", "私教", "健身", "跑", "瑜伽", "泳", "减重", "拉伤"]),
                ("家居装修", ["瓷砖", "地板", "量房", "装修", "预算", "铺装", "家具", "租"]),
                ("出行拼车", ["拼车", "高速口", "航班", "高铁", "接站", "自驾", "加油"]),
                ("餐饮聚会", ["轻食", "桌游", "火锅", "烧烤", "聚餐", "咖啡", "甜品", "生日"]),
                ("工作职场", ["后端", "年审", "加班", "排班", "项目", "汇报", "出差", "物流"]),
                ("宠物", ["猫", "狗", "挠", "猫粮", "宠物"]),
                ("数码消费", ["拍", "拍子", "手机", "电脑", "耳机", "快递", "退换"]),
                ("家庭事务", ["妈", "爸", "家人", "亲戚", "过年", "红包", "搬家"]),
                ("学习成长", ["课", "考试", "报名", "教练", "书", "课表"])]

# ---------------------------------------------------------------------------
# 十大专题启发式关键词表（权重）。指导书 §2.3：标准篇主分类只落 T01–T05/T08，
# T06 走意图/合法不回密集规则；T07/T09/T10 对标准篇只作 secondary。
# 关键词命中在 title+themes+conflict_type 拼接文本上做子串匹配。
# ---------------------------------------------------------------------------
TOPIC_KEYWORDS = {
    "T01": {"破冰": 3, "接梗": 3, "吐槽": 2, "互怼": 3, "玩笑": 2, "幽默": 3, "段子": 3,
            "梗": 1, "投缘": 2, "闲聊": 3, "冲突修复": 2, "关系修复": 2, "日常": 2, "唠嗑": 3},
    "T02": {"渐暧昧": 4, "暧昧": 4, "调情": 4, "撩": 2, "心动": 3, "好感": 3, "捅破": 3,
            "表白": 3, "试探": 2, "拉扯": 3, "反撩": 3, "约会": 2, "博弈": 1},
    "T03": {"价值观": 4, "深聊": 4, "迷茫": 3, "人生": 2, "爱情观": 4, "婚姻观": 4, "观念": 3,
            "思想": 3, "争论": 2, "代际共识": 2, "决策": 1, "抉择": 1, "参谋": 1, "智识": 2},
    "T04": {"邀约": 4, "约见": 4, "约展": 3, "约饭": 2, "落成": 3, "跃迁": 4, "推进": 3,
            "见面": 3, "补约": 2, "再邀约": 4, "固定局": 2, "拼局": 2, "约定": 1, "赴约": 3},
    "T05": {"安慰": 4, "失落": 4, "低落": 3, "难过": 3, "关心": 3, "支持": 2, "焦虑": 3,
            "压力": 2, "陪伴": 3, "陪检": 2, "陪诊": 2, "陪跑": 2, "陪护": 2, "陪": 1,
            "情绪": 3, "安抚": 4, "应激": 2, "好胜心": 2},
    "T06": {"意图": 3, "口吻": 3, "改写": 3, "多候选": 3, "候选": 2},
    "T07": {"归属": 3, "不确定": 2, "口径不一": 2, "口径": 1, "传闻": 2, "认定": 2,
            "张冠李戴": 4, "误认": 2, "主体": 2, "争议": 1},
    "T08": {"记忆": 4, "对账": 3, "旧事": 3, "回归回扣": 3, "回扣": 1, "回声": 3, "复盘": 2,
            "纠错": 3, "旧账": 2, "提取": 3, "知识库": 4, "线索": 2, "时间线": 2, "经验": 1},
    "T09": {"改期": 2, "更正": 2, "取消": 2, "改约": 2, "启停": 2, "暂停": 2, "重启": 2,
            "目标启停": 3, "连环改期": 3, "改签": 2, "降档": 2, "死线": 1, "状态": 2},
    "T10": {"边界": 3, "外借": 2, "拒绝": 2, "越界": 3, "抗干扰": 3, "污染": 3, "反事实": 4,
            "红线": 2, "分寸": 2, "禁忌": 2},
}
# 标准篇允许的主分类集合（指导书 §2.3）
STD_PRIMARY_ALLOWED = {"T01", "T02", "T03", "T04", "T05", "T06", "T08"}
# 判定置信档阈值
HIGH_TOP, HIGH_MARGIN = 6, 3
MED_TOP = 3
# T06 规则阈值：意图契约轮数 ≥3，或 may_decline 轮数 ≥4（参照 AK01-12 锚点设计口径）
INTENT_DENSE, MAY_DECLINE_DENSE = 3, 4


def log(msg):
    print(msg, flush=True)


def load_json(fp):
    with open(fp, encoding="utf-8") as f:
        return json.load(f)


def sha256_file(fp):
    h = hashlib.sha256()
    with open(fp, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def repo_rel(fp):
    return os.path.relpath(fp, ROOT).replace("\\", "/")


def coarse_relationship(raw):
    """把 persona.relationship_stage 自由文本归到前缀粗类（取括号前主干）。"""
    if not raw:
        return "unknown"
    for sep in ("（", "("):
        if sep in raw:
            raw = raw.split(sep)[0]
    return raw.strip() or "unknown"


def length_class(turns):
    if turns <= 8:
        return "short"
    if turns == 12:
        return "standard"
    if turns == 24:
        return "deep"
    if turns in (32, 40):
        return "long"
    return "other%d" % turns


def keyword_scores(text):
    text = text or ""
    hits = {}
    for topic, table in TOPIC_KEYWORDS.items():
        matched = [(kw, w) for kw, w in table.items() if kw in text]
        # 去除被更长命中词包含的短词，避免同一次出现重复计分（如"渐暧昧"不再同时计"暧昧"）
        matched.sort(key=lambda x: (-len(x[0]), -x[1]))
        kept = []
        for kw, w in matched:
            if not any(kw in k2 for k2, _ in kept):
                kept.append((kw, w))
        if kept:
            hits[topic] = kept
    return hits


def classify(ep):
    """返回 (primary, secondary_str, basis, confidence)。启发式，依据如实记录。"""
    sid, stage = ep["episode_id"], ep["stage"]
    text = (ep.get("title") or "") + "".join(ep.get("themes") or []) + (ep.get("conflict_type") or "")
    hits = keyword_scores(text)
    sec_parts = []
    for t in sorted(hits, key=lambda x: (-sum(w for _, w in hits[x]), x)):
        if len(sec_parts) >= 3:
            break
        kws = "/".join(kw for kw, _ in sorted(hits[t], key=lambda x: -x[1])[:3])
        sec_parts.append("%s(%s)" % (t, kws))

    # 1) 显式阶段规则（指导书 §2.3）
    if ep.get("is_cf"):
        sec = ";".join(p for p in sec_parts if not p.startswith("T10")) or "无"
        return "T10", sec, "stage_rule:CF反事实孪生(manifest_CF.source_story=%s,perturbation=%s)→T10" % (
            ep.get("mother"), ep.get("cf_perturbation")), "high"
    if stage == "wave3_ext":
        sec = ";".join(p for p in sec_parts if not p.startswith("T09")) or "无"
        return "T09", sec, "stage_rule:长剧原位续写(%d轮,母本%s)→T09" % (ep["turn_count"], ep.get("mother")), "high"
    if sid in ("E061", "E062"):
        return "T07", ";".join(p for p in sec_parts if not p.startswith("T07")) or "无", \
            "stage_rule:stage4无据确定化双杀设计(E061随访/E062交易unknown双杀位)→T07", "high"
    if sid in ("E063", "E064", "E065", "E066", "E067", "E068"):
        return "T09", ";".join(p for p in sec_parts if not p.startswith("T09")) or "无", \
            "stage_rule:stage4更正链/账目24轮设计→T09", "high"

    # 2) 意图/合法不回密集 → T06（intent 契约 ≥3 轮或 may_decline ≥4 轮）
    if ep["n_intent"] >= INTENT_DENSE:
        return "T06", ";".join(p for p in sec_parts if not p.startswith("T06")) or "无", \
            "rule:intent契约密集(%d/%d轮)→T06" % (ep["n_intent"], ep["turn_count"]), \
            "high" if ep["n_intent"] >= 3 else "medium"
    if ep["n_may_decline"] >= MAY_DECLINE_DENSE:
        return "T06", ";".join(p for p in sec_parts if not p.startswith("T06")) or "无", \
            "rule:may_decline合法不回密集(%d/%d轮,参照AK锚点口径)→T06" % (ep["n_may_decline"], ep["turn_count"]), "medium"

    # 3) 关键词映射（仅 T01–T05/T08 可为主分类）
    scores = {t: sum(w for _, w in kw_hits) for t, kw_hits in hits.items()
              if t in STD_PRIMARY_ALLOWED and t != "T06"}
    sec_str = ";".join(sec_parts) if sec_parts else "无"
    ranked = sorted(scores.items(), key=lambda x: (-x[1], x[0]))
    if not scores or ranked[0][1] < 2:
        # 无命中或仅弱单词命中（权重1）→ 不制造主分类，诚实兜底 T01 并把弱信号降为 secondary
        return "T01", sec_str, "fallback:themes/title无≥2分主分类信号,默认兜底T01(最低承诺类别)", "low"
    top, top_score = ranked[0]
    margin = top_score - (ranked[1][1] if len(ranked) > 1 else 0)
    matched = ";".join("%s:%s" % (t, "+".join("%sx%d" % (kw, 1) for kw, _ in sorted(hits[t], key=lambda x: -x[1])[:4]))
                       for t, _ in ranked[:2])
    basis = "themes/title关键词[%s]→%s" % (matched, top)
    if top_score >= HIGH_TOP and margin >= HIGH_MARGIN:
        conf = "high"
    elif top_score >= MED_TOP:
        conf = "medium"
    else:
        conf = "low"
    return top, sec_str, basis, conf


def domain_basis(ep, dmap):
    if ep.get("domain_source") == "manifest":
        return "domain=manifest显式"
    if ep.get("domain_source") == "normalization_map":
        return "domain=W03-06归一映射"
    if ep.get("domain_source") == "exposure":
        return "domain=exposure字段"
    if ep.get("domain_source") == "inherited_mother":
        return "domain=承母本%s(长剧原位续写)" % ep.get("mother")
    return "domain=DOMAIN_RULES关键词派生(同dedupe工具)"


def domain_of(title, themes):
    """与 tools/story_skeleton_dedupe.py 完全同口径的关键词派生（首个命中规则生效）。"""
    txt = (title or "") + "".join(themes or [])
    for dom, kws in DOMAIN_RULES:
        if any(k in txt for k in kws):
            return dom
    return "其他"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true", help="stdout 输出机器可读全量摘要")
    ap.add_argument("--markdown", action="store_true", help="stdout 输出报告用 markdown 表格")
    ap.add_argument("--no-write", action="store_true", help="只算不写 CSV（自检用）")
    args = ap.parse_args()

    warnings = []

    # ---------------- manifests & episode files ----------------
    episodes = OrderedDict()
    for stage in LIB_STAGES:
        sdir = os.path.join(SV2, stage)
        if stage == "wave3_ext":
            ids = sorted(d for d in os.listdir(sdir)
                         if os.path.isdir(os.path.join(sdir, d)) and os.path.isfile(os.path.join(sdir, d, "episode.json")))
            for sid in ids:
                mf = load_json(os.path.join(sdir, "MANIFEST_%s.json" % sid))
                st = mf["stories"][0]
                episodes[sid] = {
                    "episode_id": sid, "stage": stage,
                    "dir": os.path.join(sdir, sid),
                    "title": st.get("title") or "", "themes": st.get("themes") or [],
                    "conflict_type": "", "domain": "", "domain_source": "",
                    "manifest_turns": st.get("turns") or mf.get("turn_per_story"),
                    "mother": st.get("source") or ("E" + sid[3:]),
                    "is_cf": False,
                }
            continue
        mf = load_json(os.path.join(sdir, STAGE_MANIFESTS[stage]))
        for st in mf["stories"]:
            sid = st["story_id"]
            rec = {
                "episode_id": sid, "stage": stage,
                "dir": os.path.join(sdir, sid),
                "title": st.get("title") or "", "themes": st.get("themes") or [],
                "conflict_type": st.get("conflict_type") or "",
                "domain": st.get("domain") or "", "domain_source": "manifest" if st.get("domain") else "",
                "manifest_turns": st.get("turns"),
                "mother": "", "is_cf": False,
            }
            episodes[sid] = rec
    # CF 追加（wave3 STAGE1_MANIFEST_CF.json）
    cf_mf = load_json(os.path.join(SV2, "wave3", "STAGE1_MANIFEST_CF.json"))
    for st in cf_mf["stories"]:
        sid = st["story_id"]
        episodes[sid] = {
            "episode_id": sid, "stage": "wave3",
            "dir": os.path.join(SV2, "wave3", sid),
            "title": st.get("title") or "", "themes": st.get("themes") or [],
            "conflict_type": st.get("conflict_type") or "",
            "domain": st.get("domain") or "", "domain_source": "manifest" if st.get("domain") else "",
            "manifest_turns": st.get("turns"),
            "mother": st.get("source_story") or "", "is_cf": True,
            "cf_perturbation": st.get("perturbation") or "",
        }

    assert len(episodes) == 208, "库篇数应为 208，实测 %d" % len(episodes)

    # stage2 manifest 无 title/themes → exposure.json 兜底；波内 manifest 无 domain 的再走归一/关键词
    dmap_rows = load_json(DOMAIN_NORM_FP)
    dmap = {r["story_id"]: r["domain"] for r in dmap_rows.get("mapping", [])}
    for sid, ep in episodes.items():
        if not ep["title"] or not ep["themes"]:
            ex_fp = os.path.join(ep["dir"], "exposure.json")
            if os.path.isfile(ex_fp):
                ex = load_json(ex_fp)
                if not ep["title"]:
                    ep["title"] = ex.get("title") or ""
                if not ep["themes"]:
                    ep["themes"] = ex.get("themes") or []
                if not ep["domain"] and ex.get("domain"):
                    ep["domain"], ep["domain_source"] = ex["domain"], "exposure"
        if not ep["domain"] and sid in dmap:
            ep["domain"], ep["domain_source"] = dmap[sid], "normalization_map"
        if not ep["domain"]:
            ep["domain"], ep["domain_source"] = domain_of(ep["title"], ep["themes"]), "keyword_rules"
    # 长剧域承母本（exposure 有域的 LRE117/LRE128 已在上面走 exposure 分支）
    for sid, ep in episodes.items():
        if ep["stage"] == "wave3_ext" and not ep["domain"]:
            mother = episodes.get(ep.get("mother") or "")
            if mother and mother["domain"]:
                ep["domain"], ep["domain_source"] = mother["domain"], "inherited_mother"

    # ---------------- episode.json / sha / persona ----------------
    ledger = load_json(CUSTODY_FP)
    stories_ledger = ledger["stories"]
    for sid, ep in episodes.items():
        ep_fp = os.path.join(ep["dir"], "episode.json")
        ep["source_path"] = repo_rel(ep_fp)
        ej = load_json(ep_fp)
        turns = ej.get("turns") or []
        ep["turn_count"] = len(turns)
        if ep.get("manifest_turns") not in (None, ep["turn_count"]):
            warnings.append("%s: manifest turns=%s != episode.json %d" % (sid, ep.get("manifest_turns"), ep["turn_count"]))
        persona = ej.get("persona") or {}
        ep["relationship_stage"] = coarse_relationship(persona.get("relationship_stage"))
        ep["sha256"] = sha256_file(ep_fp)
        led = stories_ledger.get(sid)
        if not led:
            warnings.append("%s: 不在保管账（不应发生）" % sid)
        else:
            if led.get("hashes", {}).get("episode") not in (None, ep["sha256"]):
                warnings.append("%s: episode.json sha256 与保管账登记不一致" % sid)
            ep["lifecycle"] = led.get("lifecycle") or "UNKNOWN"
            ep["consumed_by_n"] = len(led.get("consumed_by") or [])
        # manifest 内登记 sha 对账（stage2-4/wave1-3 有 files.episode.json；LRE/CF 走各自 manifest）
        msha = None
        if ep["stage"] in STAGE_MANIFESTS:
            mf = load_json(os.path.join(SV2, ep["stage"], STAGE_MANIFESTS[ep["stage"]]))
            for st in mf["stories"]:
                if st["story_id"] == sid:
                    msha = (st.get("files") or {}).get("episode.json")
        elif ep["stage"] == "wave3_ext":
            msha = (load_json(os.path.join(ep["dir"], "..", "MANIFEST_%s.json" % sid))["stories"][0]
                    .get("files", {}).get("episode.json"))
        if ep["is_cf"]:
            for st in cf_mf["stories"]:
                if st["story_id"] == sid:
                    msha = (st.get("files") or {}).get("episode.json")
        if msha and msha != ep["sha256"]:
            warnings.append("%s: episode.json sha256 与 manifest 登记不一致" % sid)
        ep["errata"] = ERRATA.get(sid, [])
        ep["blocking_errata"] = any(e[3] for e in ep["errata"])

    # ---------------- gold sidecar 逐轮聚合 ----------------
    gi = load_json(GOLD_INDEX_FP)
    gold_pe = {}   # sid -> dict(turns, with_assertions, empty_gold)
    for wave, fname in GOLD_WAVE_FILE.items():
        gw = load_json(os.path.join(GOLD_DIR, fname))
        for sid, st in gw["per_episode"].items():
            gold_pe[sid] = st
        agg = defaultdict(lambda: Counter())
        for t in gw.get("turns", []):
            sid = t["episode_id"]
            c = agg[sid]
            if t.get("provenance", {}).get("card_conflicts"):
                c["conflict_turns"] += 1
            ic = t.get("intent_contract") or {}
            if ic.get("intent"):
                c["intent"] += 1
            if ic.get("may_decline"):
                c["md"] += 1
            atoms = t.get("assertions") or []
            if any(a.get("affirm_markers") for a in atoms):
                c["affirm"] += 1
            if any(a.get("item_markers") for a in atoms):
                c["item"] += 1
            en = t.get("enrichment") or {}
            if en.get("unknowns_upgraded", 0):
                c["unk"] += 1
            if ic.get("keywords_any"):
                c["intkw"] += 1
            c["hook"] += 1 if (c["affirm"] or c["item"] or c["unk"] or c["intkw"] or c["md"]) else 0
        for sid, c in agg.items():
            if sid in episodes:
                episodes[sid].update({
                    "n_conflict_turns": c["conflict_turns"], "n_intent": c["intent"],
                    "n_may_decline": c["md"], "n_hook_turns": c["hook"],
                })
    for sid, ep in episodes.items():
        st = gold_pe.get(sid)
        if not st:
            warnings.append("%s: gold sidecar 缺该篇" % sid)
            st = {"turns": ep["turn_count"], "with_assertions": 0, "empty_gold": 0}
        if st["turns"] != ep["turn_count"]:
            warnings.append("%s: sidecar turns=%d != episode.json %d" % (sid, st["turns"], ep["turn_count"]))
        ep["with_assertions"] = st["with_assertions"]
        ep["empty_gold"] = st["empty_gold"]
        ratio = (st["with_assertions"] / st["turns"]) if st["turns"] else 0.0
        ep["gold_available"] = "full" if ratio > 2 / 3 else ("partial" if ratio > 0 else "none")
        ep.setdefault("n_conflict_turns", 0)
        ep.setdefault("n_intent", 0)
        ep.setdefault("n_may_decline", 0)
        ep.setdefault("n_hook_turns", 0)
        ep["source_conflict_flag"] = ep["n_conflict_turns"] > 0

    # ---------------- 谱系组 ----------------
    groups = OrderedDict()   # gid -> {"members": [(sid, in_library)], "kind": str, ...}
    for sid, ep in episodes.items():
        if ep["stage"] == "wave3_ext":
            gid = "LIN-" + ep["mother"]
            groups.setdefault(gid, {"members": [], "kind": "长剧原位续写"})
            groups[gid]["members"].append((sid, True))
            groups[gid]["members"].append((ep["mother"], True))
    for st in cf_mf["stories"]:
        gid = "LIN-" + st["source_story"]
        groups.setdefault(gid, {"members": [], "kind": "CF反事实孪生(%s)" % st.get("perturbation")})
        groups[gid]["members"].append((st["story_id"], True))
        groups[gid]["members"].append((st["source_story"], True))
    for sid in ("LRE048", "LRE060"):
        mf = load_json(os.path.join(SV2, "wave1_ext", "MANIFEST_%s.json" % sid))
        st = mf["stories"][0]
        mother = st.get("source") or ("E" + sid[3:])
        gid = "LIN-" + mother
        groups.setdefault(gid, {"members": [], "kind": "长剧原位续写(wave1_ext,库外)"})
        groups[gid]["members"].append((sid, False))
        groups[gid]["members"].append((mother, True))
    for gid, g in groups.items():
        # 去重保序
        seen, members = set(), []
        for sid, inlib in g["members"]:
            if sid not in seen:
                seen.add(sid)
                members.append((sid, inlib))
        g["members"] = members
        g["b_line_members"] = [sid for sid, _ in members if sid in B_LINE_IDS] if False else None  # 占位，稍后填充
    sid2gid = {}
    for gid, g in groups.items():
        for sid, _ in g["members"]:
            sid2gid[sid] = gid

    # ---------------- B 线（仅 manifest 元数据） ----------------
    bl = load_json(B_LINE_MANIFEST_FP)
    B_LINE_IDS = set(bl["episodes"].keys())
    for gid, g in groups.items():
        g["b_line_members"] = [sid for sid, _ in g["members"] if sid in B_LINE_IDS]
    bline_sha_match = 0
    for sid, meta in bl["episodes"].items():
        src_sha = meta.get("source_sha256") or meta.get("copy_sha256")
        if sid in episodes and src_sha and episodes[sid]["sha256"] == src_sha:
            bline_sha_match += 1

    # ---------------- 分类 / 资格 / 行组装 ----------------
    rows = []
    for sid, ep in episodes.items():
        primary, sec, basis, conf = classify(ep)
        ep["primary_topic"], ep["secondary_topics"] = primary, sec
        ep["topic_basis"], ep["topic_confidence"] = basis, conf
        lifecycle = ep.get("lifecycle", "UNREGISTERED")
        exposure_status = lifecycle if lifecycle in ("SEALED", "EXPOSED") else "UNREGISTERED"
        gid = sid2gid.get(sid, "")
        in_bline = sid in B_LINE_IDS
        group_has_b = bool(gid and any(m in B_LINE_IDS for m, _ in groups[gid]["members"] if m != sid))
        brel = ("both" if (in_bline and group_has_b) else
                "self" if in_bline else
                "lineage" if group_has_b else "no")
        reasons = []
        if exposure_status != "EXPOSED":
            reasons.append("未暴露(%s)须保持封存" % exposure_status)
        if brel != "no":
            reasons.append("B线相关(%s)" % brel)
        if ep["blocking_errata"]:
            reasons.append("阻断勘误:%s" % ";".join(e[0] for e in ep["errata"] if e[3]))
        dev_ok = not reasons
        gold_ok = ep["gold_available"] != "none"
        reuse_ok = dev_ok and gold_ok
        if dev_ok and not gold_ok:
            reasons.append("gold=none(无可判断言)")
        rows.append({
            "episode_id": sid,
            "source_path": ep["source_path"],
            "stage": ep["stage"],
            "turn_count": ep["turn_count"],
            "domain": ep["domain"] or "其他",
            "primary_topic": primary,
            "secondary_topics": sec,
            "length_class": length_class(ep["turn_count"]),
            "relationship_stage": ep["relationship_stage"],
            "gold_available": ep["gold_available"],
            "gold_conflict_count": ep["n_conflict_turns"],
            "source_conflict_flag": ep["source_conflict_flag"],
            "known_errata": ";".join("%s|%s|%s|%s" % (e[0], e[1], e[2], "blocking" if e[3] else "registered")
                                     for e in ep["errata"]),
            "exposure_status": exposure_status,
            "b_line_related": brel,
            "lineage_group_id": gid,
            "annotation_basis": basis + ";" + domain_basis(ep, dmap) + ";relationship_stage=episode.persona" +
                                ("" if ep["relationship_stage"] != "unknown" else "(无该字段)"),
            "annotation_confidence": conf,
            "review_status": REVIEW_STATUS.get(stage_bucket(sid), "unknown"),
            "eligible_for_dev": dev_ok,
            "eligible_for_selection": dev_ok,
            "eligible_for_holdout": False,
            "eligible_for_reuse": reuse_ok,
            "reuse_block_reason": "+".join(reasons),
            "episode_sha256": ep["sha256"],
        })
        ep["b_line_related"], ep["lineage_group_id"] = brel, gid
        ep["eligible_for_reuse"] = reuse_ok

    # ---------------- 专题矩阵 ----------------
    def topic_rows():
        out = []
        for t in TOPICS:
            members = [r for r in rows if r["primary_topic"] == t]
            out.append({
                "topic": t,
                "episodes": len(members),
                "turns": sum(r["turn_count"] for r in members),
                "with_assertions_turns": sum(gold_pe.get(r["episode_id"], {}).get("with_assertions", 0) for r in members),
                "sealed_n": sum(1 for r in members if r["exposure_status"] == "SEALED"),
                "exposed_n": sum(1 for r in members if r["exposure_status"] == "EXPOSED"),
                "b_line_related_n": sum(1 for r in members if r["b_line_related"] != "no"),
                "known_errata_n": sum(1 for r in members if r["known_errata"]),
                "high_confidence_n": sum(1 for r in members if r["annotation_confidence"] == "high"),
            })
        return out

    matrix = topic_rows()

    # ---------------- 自检 ----------------
    checks = OrderedDict()
    checks["rows"] = len(rows)
    checks["rows_expected"] = 208
    checks["turns_sum"] = sum(r["turn_count"] for r in rows)
    checks["turns_expected"] = 2944
    required = ["episode_id", "source_path", "stage", "turn_count", "primary_topic", "length_class",
                "gold_available", "exposure_status", "b_line_related", "annotation_basis",
                "annotation_confidence", "review_status", "episode_sha256"]
    empty_required = [(r["episode_id"], c) for r in rows for c in required
                      if r[c] in (None, "", "unknown") and c != "relationship_stage"]
    checks["empty_required"] = empty_required
    checks["stage_counts"] = dict(Counter(r["stage"] for r in rows))
    checks["length_class_counts"] = dict(Counter(r["length_class"] for r in rows))
    checks["gold_available_counts"] = dict(Counter(r["gold_available"] for r in rows))
    checks["exposure_counts"] = dict(Counter(r["exposure_status"] for r in rows))
    checks["b_line_related_counts"] = dict(Counter(r["b_line_related"] for r in rows))
    checks["b_line_self_n"] = sum(1 for r in rows if r["b_line_related"] == "self")
    checks["bline_manifest_n"] = len(B_LINE_IDS)
    checks["bline_source_sha_match"] = bline_sha_match
    checks["lineage_groups"] = len(groups)
    checks["library_members_in_groups"] = sum(1 for r in rows if r["lineage_group_id"])
    checks["topic_episode_sum"] = sum(m["episodes"] for m in matrix)
    checks["warnings"] = warnings

    # ---------------- 写 CSV ----------------
    os.makedirs(OUT_DIR, exist_ok=True)
    inv_fp = os.path.join(OUT_DIR, "DATASET_INVENTORY.csv")
    mat_fp = os.path.join(OUT_DIR, "TOPIC_COVERAGE_MATRIX.csv")
    if not args.no_write:
        with open(inv_fp, "w", encoding="utf-8", newline="") as f:
            w = csv.DictWriter(f, fieldnames=INVENTORY_COLUMNS)
            w.writeheader()
            for r in rows:
                w.writerow({k: ("true" if r[k] is True else "false" if r[k] is False else r[k])
                            for k in INVENTORY_COLUMNS})
        with open(mat_fp, "w", encoding="utf-8", newline="") as f:
            w = csv.DictWriter(f, fieldnames=MATRIX_COLUMNS)
            w.writeheader()
            for m in matrix:
                w.writerow(m)
        log("written: %s" % repo_rel(inv_fp))
        log("written: %s" % repo_rel(mat_fp))

    # ---------------- 摘要 ----------------
    reusable = [r for r in rows if r["eligible_for_reuse"]]
    reuse_by_topic = Counter(r["primary_topic"] for r in reusable)
    block_counter = Counter()
    for r in rows:
        if not r["eligible_for_reuse"]:
            for part in r["reuse_block_reason"].split("+"):
                block_counter[part.split(":")[0].split("(")[0]] += 1
    sec_only = Counter()
    for r in rows:
        for part in (r["secondary_topics"] or "").split(";"):
            t = part.split("(")[0].strip()
            if t in TOPICS and r["primary_topic"] != t:
                sec_only[t] += 1
    reuse_by_topic_sec = Counter()
    for r in rows:
        if r["eligible_for_reuse"]:
            for part in (r["secondary_topics"] or "").split(";"):
                t = part.split("(")[0].strip()
                if t in TOPICS:
                    reuse_by_topic_sec[t] += 1

    summary = OrderedDict()
    summary["totals"] = {
        "episodes": len(rows), "turns": checks["turns_sum"],
        "with_assertions_turns": sum(gold_pe.get(r["episode_id"], {}).get("with_assertions", 0) for r in rows),
        "by_stage": {s: sum(1 for r in rows if r["stage"] == s) for s in LIB_STAGES},
        "by_length": checks["length_class_counts"],
        "by_gold_available": checks["gold_available_counts"],
        "by_exposure": checks["exposure_counts"],
        "by_b_line_related": checks["b_line_related_counts"],
        "review_status_counts": dict(Counter(r["review_status"] for r in rows)),
        "gold_none_episodes": [r["episode_id"] for r in rows if r["gold_available"] == "none"],
        "gold_conflict_episodes": {r["episode_id"]: r["gold_conflict_count"] for r in rows if r["gold_conflict_count"]},
        "domain_counts": dict(Counter(r["domain"] for r in rows).most_common()),
    }
    summary["topic_matrix"] = matrix
    summary["topic_secondary_only"] = dict(sec_only)
    # 确定性判分钩子：affirm/item markers、unknowns 升级、intent keywords、may_decline 至少其一的篇
    hook_eps = set()
    for wave, fname in GOLD_WAVE_FILE.items():
        gw = load_json(os.path.join(GOLD_DIR, fname))
        for t in gw.get("turns", []):
            sid = t["episode_id"]
            ic = t.get("intent_contract") or {}
            atoms = t.get("assertions") or []
            en = t.get("enrichment") or {}
            if (any(a.get("affirm_markers") for a in atoms) or any(a.get("item_markers") for a in atoms)
                    or en.get("unknowns_upgraded", 0) or ic.get("keywords_any") or ic.get("may_decline")):
                hook_eps.add(sid)
    summary["topic_hook_episodes"] = {
        t: sum(1 for r in rows if r["primary_topic"] == t and r["episode_id"] in hook_eps) for t in TOPICS}
    summary["topic_exposed_gold_pos"] = {
        t: sum(1 for r in rows if r["primary_topic"] == t and r["exposure_status"] == "EXPOSED"
               and r["gold_available"] != "none") for t in TOPICS}
    summary["topic_reusable"] = dict(reuse_by_topic)
    summary["topic_reusable_secondary"] = dict(reuse_by_topic_sec)
    summary["topic_reusable_ids"] = {t: [r["episode_id"] for r in reusable if r["primary_topic"] == t]
                                     for t in TOPICS if any(r["primary_topic"] == t for r in reusable)}
    summary["topic_errata_ids"] = {t: [r["episode_id"] for r in rows
                                       if r["primary_topic"] == t and r["known_errata"]]
                                   for t in TOPICS if any(r["primary_topic"] == t and r["known_errata"] for r in rows)}
    basis_kind = {}
    for r in rows:
        b = r["annotation_basis"]
        if b.startswith("stage_rule:CF"):
            k = "stage_rule_CF"
        elif b.startswith("stage_rule:长剧"):
            k = "stage_rule_long"
        elif b.startswith("stage_rule:stage4"):
            k = "stage_rule_stage4"
        elif b.startswith("rule:intent"):
            k = "rule_intent_dense"
        elif b.startswith("rule:may_decline"):
            k = "rule_may_decline_dense"
        elif b.startswith("fallback:"):
            k = "keyword_fallback_T01"
        elif b.startswith("themes/title"):
            k = "keyword_themes_title"
        else:
            k = "other"
        basis_kind[r["episode_id"]] = k
    summary["primary_basis_counts"] = dict(Counter(basis_kind.values()))
    summary["topic_primary_basis"] = {
        t: dict(Counter(basis_kind[r["episode_id"]] for r in rows if r["primary_topic"] == t))
        for t in TOPICS}
    summary["topic_members"] = {t: [r["episode_id"] for r in rows if r["primary_topic"] == t]
                                for t in TOPICS}
    summary["reuse"] = {
        "eligible_for_dev": sum(1 for r in rows if r["eligible_for_dev"]),
        "eligible_for_reuse": len(reusable),
        "reusable_ids": [r["episode_id"] for r in reusable],
        "block_reasons": dict(block_counter),
        "gold_none_n": sum(1 for r in rows if r["gold_available"] == "none"),
        "gold_partial_ids": [r["episode_id"] for r in rows if r["gold_available"] == "partial"],
    }
    summary["lineage"] = {
        "n_groups": len(groups),
        "groups": [{"gid": gid, "kind": g["kind"], "members": [m for m, _ in g["members"]],
                    "outside_library": [m for m, inlib in g["members"] if not inlib],
                    "b_line_members": g["b_line_members"]} for gid, g in groups.items()],
        "library_members_in_groups": checks["library_members_in_groups"],
    }
    summary["errata"] = {sid: [{"loc": e[0], "desc": e[1], "disposition": e[2],
                                "blocking": e[3]} for e in eps]
                         for sid, eps in ERRATA.items()}
    summary["b_line"] = {
        "version": bl.get("version"), "date": bl.get("date"), "count": bl.get("count"),
        "composition": bl.get("composition"),
        "integrity": bl.get("integrity"),
        "ids": sorted(B_LINE_IDS),
        "source_sha_match_vs_library": bline_sha_match,
        "readme_note": "B_LINE_README.md 仍写 b_line_v1 口径（LRE121/LRE146/LRE139/LRE127 + E156），与 manifest v2 实际构成（LRE119/123/139/142 + E155）不一致——以 manifest v2 为准",
    }
    if os.path.isfile(ANCHORS_MANIFEST_FP):
        am = load_json(ANCHORS_MANIFEST_FP)
        summary["anchors"] = {k: am.get(k) for k in
                              ("count", "turn_per_story", "may_decline_count", "intent_all_active",
                               "independence", "built_at")}
        summary["anchors"]["note"] = "AK01-12 不在库 208 分母内（sidecar/保管账均不含）；作为 T06 的库外辅助探针登记"
    summary["checks"] = {k: v for k, v in checks.items()}
    summary["errata_sources"] = ERRATA_SOURCES
    summary["sources"] = {
        "gold": "benchmark/stories_v2/GOLD_SIDECAR/GOLD_INDEX.json + GOLD_<wave>.json（generated=%s）" % gi.get("generated"),
        "custody": "refs/experiment_protocol_20261023/global_harness_program_20261030/STORY_CUSTODY_LEDGER.json（registered=%d, events=%d）" % (len(stories_ledger), len(ledger.get("events", []))),
        "domains": "wave1/2/3 manifest 显式 %d 篇 + W03-06 归一映射 %d 篇 + DOMAIN_RULES 关键词派生（同 tools/story_skeleton_dedupe.py）" % (
            sum(1 for r in rows if r["annotation_basis"].find("manifest显式") >= 0),
            sum(1 for _, ep in episodes.items() if ep.get("domain_source") == "normalization_map")),
        "b_line": "benchmark/b_line/B_LINE_MANIFEST.json（version=%s, 仅元数据，episode 零读取）" % bl.get("version"),
    }

    if args.json:
        print(json.dumps(summary, ensure_ascii=False, indent=1))
    if args.markdown:
        print_markdown(summary)
    if not args.json and not args.markdown:
        print_summary(summary)
    return summary


# ---------------------------------------------------------------------------
# review_status：按各波语义审查批次的实际覆盖登记（出处：
# refs/experiment_protocol_20261023/global_harness_program_20261030/ 审查文档 +
# STORY_CUSTODY_LEDGER.json 审查事件；全部为 AI 审查代理批次，非独立人工评审）
# ---------------------------------------------------------------------------
REVIEW_STATUS = {
    "stage2:subset": "W03-03_subset_reviewed_20261030",
    "stage2:rest": "mech_validation_only",
    "stage3": "mech_validation_only",
    "stage4": "mech_validation_only",
    "wave1": "semantic_review_20261101(11干净4修1登记)",
    "wave2": "semantic_review_20261101(两批48篇PASS)",
    "wave3:T1": "semantic_review_T1_20261102(分片A-D)",
    "wave3:CF": "no_batch_semantic_review_found(仅生成自检+机械校验+CF1/CF2实跑判决)",
    "wave3:T2": "semantic_review_T2_20261102(S1-S4)",
    "wave3:T4": "semantic_review_T4_20261102(S5-S8)",
    "wave3_ext": "semantic_review_T3_20261102(R1a/R1b/R2-R6)",
}
_STAGE2_SUBSET = {"E021", "E022", "E023", "E025", "E026", "E028", "E029", "E030",
                  "E032", "E033", "E035", "E036"}  # W03-03 范围 12 篇（含登记瑕疵的 E029）


def stage_bucket(sid):
    if sid.startswith("LRE"):
        return "wave3_ext"  # LRE048/060（wave1_ext）不在库 208 循环内，不影响
    if not sid.startswith("E"):
        return sid
    n = int(sid[1:])
    if 21 <= n <= 36:
        return "stage2:subset" if sid in _STAGE2_SUBSET else "stage2:rest"
    if 37 <= n <= 44:
        return "stage3"
    if 45 <= n <= 60:
        return "wave1"
    if 61 <= n <= 68:
        return "stage4"
    if 69 <= n <= 116:
        return "wave2"
    if 117 <= n <= 148:
        return "wave3:T1"
    if 149 <= n <= 156:
        return "wave3:CF"
    if 157 <= n <= 180:
        return "wave3:T2"
    if 181 <= n <= 212:
        return "wave3:T4"
    return "unknown"


def print_summary(s):
    log("=== inventory 摘要 ===")
    t = s["totals"]
    log("库: %d 篇 / %d 轮 / 带断言轮 %d" % (t["episodes"], t["turns"], t["with_assertions_turns"]))
    log("stage 分布: %s" % t["by_stage"])
    log("长度分布: %s" % t["by_length"])
    log("gold 可用: %s" % t["by_gold_available"])
    log("暴露: %s" % t["by_exposure"])
    log("B线关联: %s" % t["by_b_line_related"])
    log("")
    log("专题矩阵:")
    for m in s["topic_matrix"]:
        log("  %(topic)s 篇=%(episodes)3d 轮=%(turns)4d 带断言=%(with_assertions_turns)4d "
            "S=%(sealed_n)3d E=%(exposed_n)2d B线关联=%(b_line_related_n)2d 勘误=%(known_errata_n)d "
            "高置信=%(high_confidence_n)3d" % m)
    log("")
    log("reuse: dev 可用=%d / 可复用=%d / 阻断原因=%s" % (
        s["reuse"]["eligible_for_dev"], s["reuse"]["eligible_for_reuse"], s["reuse"]["block_reasons"]))
    log("谱系组: %d 组，库内成员 %d 篇" % (s["lineage"]["n_groups"], s["lineage"]["library_members_in_groups"]))
    log("检查: rows=%s turns=%s 空必填=%s 警告=%d" % (
        s["checks"]["rows"], s["checks"]["turns_sum"], s["checks"]["empty_required"] or "无",
        len(s["checks"]["warnings"])))
    for w in s["checks"]["warnings"]:
        log("  WARN: %s" % w)


def print_markdown(s):
    log("### 专题矩阵（分母=库 208 篇 / %d 轮）" % s["totals"]["turns"])
    log("")
    log("| 专题 | 篇数 | 轮数 | 带断言轮 | SEALED | EXPOSED | B线相关 | 已知勘误篇 | 高置信分类 |")
    log("|---|---|---|---|---|---|---|---|---|")
    for m in s["topic_matrix"]:
        log("| %s %s | %d | %d | %d | %d | %d | %d | %d | %d |" % (
            m["topic"], TOPIC_NAMES[m["topic"]], m["episodes"], m["turns"], m["with_assertions_turns"],
            m["sealed_n"], m["exposed_n"], m["b_line_related_n"], m["known_errata_n"], m["high_confidence_n"]))
    log("")
    log("secondary-only 覆盖（非主分类但次要信号命中）: %s" % s["topic_secondary_only"])
    log("")
    log("有确定性判分钩子的篇数（affirm/item markers、unknown 升级、intent 关键词、may_decline 至少其一）: %s"
        % s["topic_hook_episodes"])
    log("")
    log("EXPOSED 且 gold≠none（可立即详细复核）: %s" % s["topic_exposed_gold_pos"])
    log("")
    log("可复用（eligible_for_reuse=true）按专题: %s（合计 %d）" % (
        s["topic_reusable"], s["reuse"]["eligible_for_reuse"]))
    log("可复用篇 ID 按专题: %s" % json.dumps(s["topic_reusable_ids"], ensure_ascii=False))
    log("已知勘误篇按专题: %s" % json.dumps(s["topic_errata_ids"], ensure_ascii=False))
    log("主分类依据分布: %s" % s["primary_basis_counts"])
    log("各专题主分类依据: %s" % json.dumps(s["topic_primary_basis"], ensure_ascii=False))
    log("gold=partial 篇(%d): %s" % (len(s["reuse"]["gold_partial_ids"]), ",".join(s["reuse"]["gold_partial_ids"])))
    log("")
    log("可复用篇的次级专题分布: %s" % s["topic_reusable_secondary"])
    log("")
    log("阻断原因计数: %s" % s["reuse"]["block_reasons"])
    log("")
    log("gold_available: %s；gold=none 篇: %s" % (
        s["totals"]["by_gold_available"], ",".join(s["totals"]["gold_none_episodes"]) or "无"))
    log("")
    log("gold source_conflicts（篇:轮数）: %s" % s["totals"]["gold_conflict_episodes"])
    log("")
    log("review_status 分布: %s" % s["totals"]["review_status_counts"])
    log("")
    log("域分布: %s" % s["totals"]["domain_counts"])
    log("")
    log("谱系组（%d 组）:" % s["lineage"]["n_groups"])
    for g in s["lineage"]["groups"]:
        log("- %s | %s | 成员=%s | 库外=%s | B线成员=%s" % (
            g["gid"], g["kind"], "/".join(g["members"]), "/".join(g["outside_library"]) or "无",
            "/".join(g["b_line_members"]) or "无"))
    log("")
    log("B线: version=%s date=%s count=%s composition=%s integrity=%s" % (
        s["b_line"]["version"], s["b_line"]["date"], s["b_line"]["count"],
        json.dumps(s["b_line"]["composition"], ensure_ascii=False), json.dumps(s["b_line"]["integrity"], ensure_ascii=False)))
    log("B线 source_sha 与库文件匹配: %d/%d" % (s["b_line"]["source_sha_match_vs_library"], s["b_line"]["count"]))
    log("")
    log("anchors: %s" % json.dumps(s.get("anchors", {}), ensure_ascii=False))
    log("")
    log("勘误台账:")
    for sid, items in s["errata"].items():
        for e in items:
            log("- %s %s | %s | 处置: %s | %s" % (sid, e["loc"], e["desc"], e["disposition"],
                                                  "阻断" if e["blocking"] else "登记"))
    log("")
    log("恒等式检查: %s" % json.dumps({k: v for k, v in s["checks"].items()
                                       if k not in ("warnings", "empty_required")}, ensure_ascii=False))
    log("空必填字段: %s" % (s["checks"]["empty_required"] or "无"))
    log("警告: %s" % (s["checks"]["warnings"] or "无"))
    log("数据来源: %s" % json.dumps(s["sources"], ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
