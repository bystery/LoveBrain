# tools/build_f5_authorization.py — F5 修复复测额度登记（12 次生成）
#
# 语义：F5 只修一个已确认缺陷（N01_T05 协议失败，E3 修复），复测范围限定为
#       **新身份** action_policy_r4_guard_final（F5_CANDIDATE_FREEZE_20261029.json
#       记录的 identity_sha256）重跑 N01 整条 12 轮候选链。
#       不新增总帽、不复活旧单、不重试、不补跑；1 物理请求 = 1 attempt。
# 用法：
#   PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/build_f5_authorization.py
import datetime as dt
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
from isolation.authorization_budget import AuthorizationBudget      # noqa: E402

OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                   "final_mainline_review_20261029")
AID = "AUTH-F5-RECHECK-20261029-01"
FROZEN = json.load(open(os.path.join(OUT, "F5_CANDIDATE_FREEZE_20261029.json"),
                        encoding="utf-8"))


def main():
    doc_path = os.path.join(OUT, "F5_RECHECK_AUTHORIZATION_20261029.json")
    ledger_rel = "logs/auth_budget/%s.jsonl" % AID
    scope = {
        "purpose": "F5 修复复测：新身份重跑 N01 候选链 12 轮（含原失败位 T05），"
                   "验证 E3 修复与无回归",
        "stage": "F5",
        "experiment": "F5_repair_recheck_N01_candidate",
        "candidate": "action_policy_r4_guard_final",
        "identity_sha256": FROZEN["identity_sha256"],
        "supersedes_identity_sha256": FROZEN["supersedes_identity_sha256"],
        "allowed_models": ["dsf"],
        "forbidden_models": ["hy4-preview", "hy3", "qwen3.8-flash", "gemini-3.8-flash"],
        "endpoint": "http://127.0.0.1:17770/v1",
        "positions": 12,
        "total_attempts": 12,
        "fail_fast": True,
        "no_retry": True,
        "no_makeup": True,
        "single_shot_per_attempt": True,
        "closed_history_not_reused": "F1/F3/F4 已关闭授权单不复活、不挪用、不抵扣。",
    }
    doc = {
        "kind": "authorization_registration",
        "authorization_id": AID,
        "authorized": True,
        "status": "ACTIVE",
        "granted_by": "用户2026-10-29持续实验授权（F5 预留：最多 64 次生成 + 32 次评审）；"
                      "本单只取 12 次生成，不新增总帽、不复活旧单。",
        "phase": "F5",
        "budget_ledger": ledger_rel,
        "real_endpoint": {"api_base": "http://127.0.0.1:17770/v1", "model": "dsf",
                          "temperature": 0.7, "max_attempts": 12},
        "api_config": "api_cfg:openai_compat_stream_false_v1；dsf 单一基座；与 F3 正式批同参数。",
        "scope": scope,
        "execution_entry": "tools/run_f5_recheck.py",
        "notes": "一物理请求一 attempt；失败消耗不退款；不重试；额度用尽即停并书面申请。",
        "registered_at": dt.datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
    }
    with open(doc_path, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
        f.write("\n")
    AuthorizationBudget(os.path.join(ROOT, ledger_rel), AID, 12, scope=scope)
    print("WROTE", os.path.relpath(doc_path, ROOT).replace("\\", "/"))
    print("identity", FROZEN["identity_sha256"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
