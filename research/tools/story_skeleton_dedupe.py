# -*- coding: utf-8 -*-
"""tools/story_skeleton_dedupe.py — W03-06 叙事骨架/语义模板去重普查。

从 stories_v2 manifests 抽取每篇骨架特征（领域×主题集×冲突类型），
Jaccard 聚近重（主题集重叠 ≥0.6 或同域同主题数），输出：
  - 近重簇（哪些故事靠换名字凑数）
  - 覆盖矩阵（域 × 主题 的稀疏表 → 扩展缺口）
  - 分期扩展配额（48/96/200）
接受标准对应：新篇骨架三元组须与全部既有篇至少一元不同。
"""
import json
import os
from itertools import combinations

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFESTS = [("stage2", "STAGE2_MANIFEST.json"), ("stage3", "STAGE3_MANIFEST.json"),
             ("stage4", "STAGE4_MANIFEST.json"), ("wave1", "STAGE1_MANIFEST.json"),
             ("wave2", "STAGE1_MANIFEST.json"), ("wave3", "STAGE1_MANIFEST.json")]
# 领域关键词 → 域标签（骨架第一元）
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


# 二次归一映射（2026-11-02）：13 篇"其他"判读结果旁路引用，题目/manifest 哈希不动
DOMAIN_NORMALIZATION = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                                    "global_harness_program_20261030",
                                    "W03-06_DOMAIN_NORMALIZATION_20261102.json")


def load_domain_map():
    try:
        m = json.load(open(DOMAIN_NORMALIZATION, encoding="utf-8"))
        return {r["story_id"]: r["domain"] for r in m.get("mapping", [])}
    except Exception:
        return {}


def domain_of(title, themes):
    txt = title + "".join(themes)
    for dom, kws in DOMAIN_RULES:
        if any(k in txt for k in kws):
            return dom
    return "其他"


def jac(a, b):
    a, b = set(a), set(b)
    return len(a & b) / max(1, len(a | b))


def load_stories():
    dmap = load_domain_map()
    out = []
    for stage, mf in MANIFESTS:
        fp = os.path.join(ROOT, "benchmark", "stories_v2", stage, mf)
        if not os.path.exists(fp):
            continue
        m = json.load(open(fp, encoding="utf-8"))
        for s in m.get("stories", []):
            title, themes = s.get("title", ""), s.get("themes", [])
            if not title:  # stage2 manifest 空 → 回退 <stage>/<sid>/exposure.json
                ex = os.path.join(ROOT, "benchmark", "stories_v2", stage, s["story_id"], "exposure.json")
                if os.path.isfile(ex):
                    j = json.load(open(ex, encoding="utf-8"))
                    title, themes = j.get("title", ""), j.get("themes", [])
            out.append({"id": s["story_id"], "stage": stage, "title": title,
                        "themes": themes, "turns": s.get("turns"),
                        "domain": s.get("domain") or dmap.get(s["story_id"]) or domain_of(title, themes),
                        "conflict_type": s.get("conflict_type")})
    return out


def main():
    stories = load_stories()
    # 近重簇：主题集 Jaccard ≥0.6 且同域
    clusters = []
    for a, b in combinations(stories, 2):
        if a["domain"] == b["domain"] and jac(a["themes"], b["themes"]) >= 0.6:
            for c in clusters:
                if a["id"] in c["ids"] or b["id"] in c["ids"]:
                    c["ids"] |= {a["id"], b["id"]}
                    break
            else:
                clusters.append({"ids": {a["id"], b["id"]},
                                 "j": round(jac(a["themes"], b["themes"]), 2),
                                 "pair": "%s~%s" % (a["id"], b["id"])})
    # 覆盖矩阵
    domains = {}
    for s in stories:
        domains.setdefault(s["domain"], []).append(s["id"])
    # 主题频次
    theme_freq = {}
    for s in stories:
        for t in s["themes"]:
            theme_freq[t] = theme_freq.get(t, 0) + 1
    res = {"kind": "w0306_skeleton_dedupe", "date": "2026-10-31", "total": len(stories),
           "near_dup_clusters": [{"ids": sorted(c["ids"]), "max_jaccard": c["j"]} for c in clusters],
           "domain_coverage": {k: sorted(v) for k, v in sorted(domains.items(), key=lambda x: -len(x[1]))},
           "theme_frequency": dict(sorted(theme_freq.items(), key=lambda x: -x[1])),
           "phase_plan": {
               "now": len(stories),
               "phase_48": {"add": 48 - len(stories),
                            "rule": "每篇(域,主题集,冲突型)三元组与全部既有篇至少一元不同；优先补域覆盖<2的域"},
               "phase_96": {"add": 96 - len(stories),
                            "rule": "同上 + 每域≥6篇；引入新冲突型（第三方插入/跨故事连续/时间线倒错更正）"},
               "phase_200": {"add": 200 - len(stories),
                             "rule": "语义模板级配额：骨架模板库×变体槽位，模板间 bigram 重叠<0.4；部分故事由 HER 续写脚本驱动"},
           }}
    out = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030",
                       "W03-06_SKELETON_DEDUPE_20261031.json")
    json.dump(res, open(out, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print("written", out)
    print("total=%d near_dup_clusters=%d" % (len(stories), len(clusters)))
    for c in res["near_dup_clusters"]:
        print("  CLUSTER", c["ids"], "j=", c["max_jaccard"])
    print("domains:", {k: len(v) for k, v in res["domain_coverage"].items()})
    thin = {k: len(v) for k, v in res["domain_coverage"].items() if len(v) < 2}
    print("thin domains (<2):", thin)


if __name__ == "__main__":
    main()
