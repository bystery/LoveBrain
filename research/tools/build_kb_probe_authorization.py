# -*- coding: utf-8 -*-
r"""tools/build_kb_probe_authorization.py — 收口单 B：跨窗口知识探针的有限目的授权单。

语义：
  - 只登记 24 次真实生成（6 个入选探针 × on/off × 2 次重复），模型 dsf/0.7、
    非流式，与 F3/F5 正式批同一份 api 配置身份；
  - 前置闸门（tools/knowledge_probe_precheck.py 的 F_B_PRECHECK.json）必须先为
    dispatch_allowed=true，本单把该文件的 sha256 与入选探针写进 scope，闸门结果
    一旦变化本单即失效；
  - 不新增其它用途额度、不复活任何已关闭账本、不重试、不补跑；
    1 物理请求 = 1 attempt，失败即计消耗；夹具轮（零网络）不入账本，单独登记。
用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/build_kb_probe_authorization.py
"""
import datetime as dt
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
from isolation.authorization_budget import AuthorizationBudget  # noqa: E402

OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                   "final_mainline_review_20261029")
AID = "AUTH-KB-PROBE-20261029-01"
CANDIDATE = "action_policy_r4_guard_e3"
IDENTITY = "17fd2160e5306a23ba7ac52a2c5c716d4a58e345e5781d84890b69465fb76d17"
PRECHECK = os.path.join(OUT, "F_B_PRECHECK.json")
REGISTRATION = os.path.join(OUT, "F_B_PROBE_REGISTRATION.json")
ENDPOINT = "http://127.0.0.1:17770/v1"
MODEL = "dsf"
TEMPERATURE = 0.7


def sha_file(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def main():
    pc = json.load(open(PRECHECK, encoding="utf-8"))
    gate = pc["gate"]
    if not gate.get("dispatch_allowed"):
        raise SystemExit("PRECHECK GATE NOT PASSED: no authorization registered")
    selected = gate["selected"]
    if len(selected) != 6:
        raise SystemExit("expected exactly 6 selected probes, got %d" % len(selected))
    total = len(selected) * 2 * 2
    order = []
    for rep in (1, 2):
        for pid in selected:
            order += ["%s:on:r%d" % (pid, rep), "%s:off:r%d" % (pid, rep)]
    ledger_rel = "logs/auth_budget/%s.jsonl" % AID
    probe_detail = {r["probe_id"]: {"class": r["class"], "story": r["story"],
                                    "fact_id": r["fact_id"], "domain": r["domain"],
                                    "fillers": r["fillers_used"],
                                    "on_prompt_sha256": r["on_prompt_sha256"],
                                    "off_prompt_sha256": r["off_prompt_sha256"],
                                    "probe_clock": r.get("probe_clock")}
                    for r in pc["probes"] if r["probe_id"] in selected}
    scope = {
        "purpose": ("收口单 B：证明“已离开原文窗口的知识”能否被检索并用于行动；"
                    "6 探针 × on/off × 2 重复 = 24 次物理生成"),
        "stage": "B_knowledge_probe",
        "experiment": "KB_probe_cross_window_20261029",
        "candidate": CANDIDATE,
        "identity_sha256": IDENTITY,
        "precheck_file": os.path.relpath(PRECHECK, ROOT).replace("\\", "/"),
        "precheck_sha256": sha_file(PRECHECK),
        "registration_file": os.path.relpath(REGISTRATION, ROOT).replace("\\", "/"),
        "registration_sha256": sha_file(REGISTRATION),
        "selected_probes": selected,
        "probe_detail": probe_detail,
        "frozen_dispatch_order": order,
        "order_rule": ("重复1两轮先跑，探针顺序=闸门选择规则给出的固定顺序，"
                       "同一探针 on/off 相邻以抵消位置漂移；顺序在本单写死，跑批不得临时改。"),
        "arms": {"on": "机制原样出站 prompt", "off": "同一 prompt 精确删除三段投影（逐字节见探针目录）"},
        "allowed_models": [MODEL],
        "forbidden_models": ["hy4-preview", "hy3", "qwen3.8-flash", "gemini-3.8-flash"],
        "endpoint": ENDPOINT,
        "positions": total,
        "total_attempts": total,
        "max_concurrency": 1,
        "fail_fast": True,
        "no_retry": True,
        "no_makeup": True,
        "single_shot_per_attempt": True,
        "fixture_turns_not_billed": ("夹具轮为零网络脚本返回，不产生物理请求，"
                                     "不入本账本；总数记在运行结果的 fixture_turns。"),
        "closed_history_not_reused": "F1–F6 已关闭授权单不复活、不挪用、不抵扣。",
    }
    doc = {
        "kind": "authorization_registration",
        "authorization_id": AID,
        "authorized": True,
        "status": "ACTIVE",
        "granted_by": ("用户2026-10-29收口单 §B 明文额度（最多 24 次真实生成，"
                       "前置全部满足后方可派发）；不新增其它用途额度。"),
        "phase": "B",
        "budget_ledger": ledger_rel,
        "real_endpoint": {"api_base": ENDPOINT, "model": MODEL,
                          "temperature": TEMPERATURE, "max_attempts": total},
        "api_config": "api_cfg:openai_compat_stream_false_v1；dsf 单一基座；与 F3 正式批同参数。",
        "scope": scope,
        "execution_entry": "tools/run_knowledge_probe_batch.py",
        "notes": "一物理请求一 attempt；失败消耗不退款；不重试；额度用尽即停并书面申请。",
        "registered_at": dt.datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
    }
    doc_path = os.path.join(OUT, "KB_PROBE_AUTHORIZATION_20261029.json")
    with open(doc_path, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
        f.write("\n")
    AuthorizationBudget(os.path.join(ROOT, ledger_rel), AID, total, scope=scope)
    print("WROTE", os.path.relpath(doc_path, ROOT).replace("\\", "/"))
    print("attempts=%d precheck_sha=%s" % (total, doc["scope"]["precheck_sha256"][:16]))
    print("order=" + ",".join(order[:6]) + ",...")
    return 0


if __name__ == "__main__":
    sys.exit(main())
