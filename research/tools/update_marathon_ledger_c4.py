# -*- coding: utf-8 -*-
"""tools/update_marathon_ledger_c4.py — 把 C4 机制筛选的真实消费写回马拉松总账（离线，零调用）。

只做三件事，且带断言（锚点找不到就报错退出，绝不静默改）：
  1) stages[机制筛选].used = 账本真实 attempt_started（128），note 换成收口事实与证据路径；
  2) ledger_events 追加一条 C4_SCREEN_CLOSED（不覆盖历史事件）；
  3) 写前先备份原文件到 backups/，改完自比：除这两处外其余键必须逐字不变。
"""
import datetime
import hashlib
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LEDG = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "mainline_review_20261026",
                    "MARATHON_BUDGET_LEDGER.json")
AUTH_LEDGER = os.path.join(ROOT, "logs", "auth_budget", "AUTH-C4-SCREEN-20261026-01.jsonl")
BACKUP_DIR = os.path.join(ROOT, "backups", "marathon_ledger_" +
                          datetime.datetime.now().strftime("%Y%m%d_%H%M%S"))

doc = json.load(open(LEDG, encoding="utf-8"))
before = json.dumps(doc, ensure_ascii=False, sort_keys=True)

# 真实消费从账本重算，不手抄
kinds = {}
for line in open(AUTH_LEDGER, encoding="utf-8"):
    line = line.strip()
    if not line:
        continue
    e = json.loads(line)
    kinds[e["kind"]] = kinds.get(e["kind"], 0) + 1
used = kinds.get("attempt_started", 0)
completed = kinds.get("attempt_completed", 0)
failed = kinds.get("attempt_failed", 0)
assert kinds.get("authorization_closed") == 1, "C4 账本未 CLOSED，不该写总账"
assert used == 128 and completed == 128 and failed == 0, \
    "账本口径异常 started=%s completed=%s failed=%s" % (used, completed, failed)

os.makedirs(BACKUP_DIR, exist_ok=True)
shutil.copy2(LEDG, os.path.join(BACKUP_DIR, "MARATHON_BUDGET_LEDGER.json"))

stage = None
for s in doc["stages"]:
    if s["stage"] == "机制筛选":
        stage = s
        break
assert stage is not None, "总账里找不到『机制筛选』阶段"
assert stage["cap"] == 128, "阶段帽不是 128（%s）——先核对再写" % stage["cap"]

stage["used"] = used
stage["note"] = (
    "C4 机制筛选已实跑收口：两批 64+64=128（原版 vs goal_action_v1、v5 vs evidence_memory_v1，"
    "同一 4 故事×8 位=32 位点四臂各一次），0 失败、执行顺序与派发双射验证通过，"
    "账本 AUTH-C4-SCREEN-20261026-01 CLOSED 128/128（不得复活）。"
    "撤销侧线先前登记的『v5 为锚、原版臂缺席』偏差——执行书原式无需偏差即可凑满 128。"
    "判决：v5 保留并可进 M2 复验；C2 保留开发但须先修无据既成断言与等待尾部；"
    "C3 判『本轮未被真正 exercise』须先修 facts 通道；原版保留为基线。"
    "证据：evaluation/C4_SCREEN_CLOSURE_V1.json、evaluation/C4_FIRST_ROUND_DISPOSITION_V1.md、"
    "evaluation/DELIVERY_AND_COST_COLUMNS_C4_SWEEPS_FINAL.json。"
    "已知设计缺陷：goal_block 真生效仅 7/32（21.9%），M2 新故事须铺开三种意图条件。")

doc["ledger_events"].append({
    "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M"),
    "event": "C4_SCREEN_CLOSED",
    "used": used,
    "tokens": None,
    "detail": ("128/128 completed 0 failed；token 合计按臂：原版 244247 / v5 103751 / "
               "C2 133372 / C3 111239（见 DELIVERY_AND_COST_COLUMNS_C4_SWEEPS_FINAL.json）；"
               "金额 null（无单价）"),
})

after = json.loads(json.dumps(doc, ensure_ascii=False, sort_keys=True))
# 自比：除被允许的两处，其余必须逐字不变
orig = json.loads(before)
diff_keys = [k for k in orig if k not in ("stages", "ledger_events")] \
    and [k for k in orig if k != "stages" and k != "ledger_events" and orig[k] != after[k]]
assert not diff_keys, "非授权改动：%s" % diff_keys
assert orig["cap_total"] == after["cap_total"], "总帽被改动"
assert len(after["ledger_events"]) == len(orig["ledger_events"]) + 1, "事件数不是+1"
for s_old, s_new in zip(orig["stages"], after["stages"]):
    if s_old["stage"] == "机制筛选":
        assert s_old["cap"] == s_new["cap"], "阶段帽被改动"
        continue
    assert s_old == s_new, "阶段 %s 被非授权改动" % s_old["stage"]

json.dump(doc, open(LEDG, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(json.dumps({
    "stage": "机制筛选", "used": used, "cap": stage["cap"],
    "backup": os.path.relpath(BACKUP_DIR, ROOT).replace("\\", "/"),
    "ledger_sha256_new": hashlib.sha256(open(LEDG, "rb").read()).hexdigest()[:16],
    "real_model_calls": 0, "result": "PASS"}, ensure_ascii=False, indent=1))
