# -*- coding: utf-8 -*-
"""tools/update_marathon_ledger_quality_first.py — 记账（零真实调用）。

只做三件事，且不改历史数字：
  1. 备份 MARATHON_BUDGET_LEDGER.json；
  2. 追加一条 QUALITY_FIRST_DELIVERY 事件（used=0, tokens=0）；
  3. 在「机制筛选」阶段 note 末尾追加"质量优先裁定后 M2 许可暂停"的现行说明，
     并在「裁判校准+新故事评审」阶段 note 末尾追加"余 84 < 所需 173"的缺口说明。
不新增阶段、不改 cap_total（1280 已满配）。
"""
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LEDG = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "mainline_review_20261026",
                    "MARATHON_BUDGET_LEDGER.json")
BACKUP = os.path.join(ROOT, "backups",
                      "pre_quality_first_ledger_20261026_2250",
                      "MARATHON_BUDGET_LEDGER.json")
TS = "2026-10-26 22:50"


def main():
    doc = json.load(open(LEDG, encoding="utf-8"))
    before = json.dumps(doc, ensure_ascii=False, sort_keys=True)
    os.makedirs(os.path.dirname(BACKUP), exist_ok=True)
    shutil.copy2(LEDG, BACKUP)

    marker = "[质量优先 2026-10-26]"
    for st in doc.get("stages") or []:
        note = st.get("note") or ""
        if marker in note:
            continue
        if st.get("stage") == "机制筛选":
            st["note"] = note + (" %s 用户裁定质量一级、成本二级、产品接入暂停；"
                                 "本段原有『v5 保留并可进 M2 复验』的许可暂停，"
                                 "M2/产品接入须以一级质量成绩为前提（见 evaluation/QUALITY_FIRST_MAIN_REPORT_V1.md）。"
                                 % marker)
        elif st.get("stage") == "裁判校准+新故事评审":
            st["note"] = note + (" %s 质量优先补评后：所需 9 维判定 141 次（含锚例 173，按 192 申请）"
                                 "超过本阶段剩余 84；且 A3/EVAL 两本授权均已 CLOSED，"
                                 "需扩阶段帽或缩 scope 并新建授权文书，未批准前不得开跑。"
                                 % marker)

    ev = {
        "ts": TS,
        "event": "QUALITY_FIRST_DELIVERY",
        "used": 0,
        "tokens": 0,
        "detail": ("质量优先离线补评交付（真实调用 0）：覆盖矩阵 6 批 368 份；知识四段（ref 存储 44/45、"
                   "仅蒸馏 6/45、T08 在场 11/40、S2 有据 62.5%/弱据 34.4%/无据 3.1%、"
                   "【进行中事项】注入 0/32 且根因可复现）；其他批 S3 补评；"
                   "12 维缺测表（S4 与 8 交互维 NR）；冻结提案（卡集 sha bdd258…、"
                   "计划 sha 7f158c57…/1f0d78c6…）；主报告 V1。"
                   "裁判授权缺口：阶段余 84 < 所需 173。")
    }
    if not any(e.get("event") == ev["event"] and e.get("ts") == ev["ts"]
               for e in doc.get("ledger_events") or []):
        doc.setdefault("ledger_events", []).append(ev)

    after = json.loads(json.dumps(doc, ensure_ascii=False, sort_keys=True))
    orig = json.loads(before)
    # 自检：只允许 stages 的 note 变化 + 新增一条事件；used/tokens 不变
    assert after["cap_total"] == orig["cap_total"]
    for a, b in zip(orig["stages"], after["stages"]):
        assert a["stage"] == b["stage"] and a["cap"] == b["cap"] and a["used"] == b["used"]
    assert len(after["ledger_events"]) == len(orig["ledger_events"]) + 1
    json.dump(doc, open(LEDG, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(json.dumps({"ok": True, "events": len(doc["ledger_events"]),
                      "backup": os.path.relpath(BACKUP, ROOT)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
