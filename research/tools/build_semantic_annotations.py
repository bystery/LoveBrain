# tools/build_semantic_annotations.py — 把 24 位旧答卷的人工初审抽成独立评估侧批注文件。
#
# R1 + B（上下文绑定）收口：批注不仅绑 case_id + slot + raw_reply_sha256 +
# slot_text_sha256，还绑**当时评估侧上下文**：
#   - run_id（= 该批 authorization_id，取自 EXAM_INDEX，不硬编码）
#   - harness_identity_sha256（= 该批答卷生成时的 harness 闭包身份，取自 EXAM_INDEX）
#   - 每条 case 的 input/题面内容散列 case_input_sha256（题面变 → 批注失效）
# 缺任一项 → 分层工具按"上下文未验证"处理，不凭当前活文件补造历史绑定、不跨批静默复用。
#
# B 归属修正：E001_T04、E003_T04 的 case-level "edge" 实际来自 **备选槽**（warm /
# directions[2]），recommended 本身没被独立复核过。旧实现把 legacy 标签无脑赋给
# primary。这里把这两处的 primary 记为 unrated（reviewer 允许的选项之一），
# 并在 provenance_corrections 留痕；不对未独立审过的首选文本冒充定级。
#
# backup 槽：只有 BACKUP_SEVERE_LEGACY 明确点名的记 severe；其余保持 unrated（不落批注），
# 分层工具显示未评，绝不冒充 pass/无问题。
#
# 本文件是**评估侧**批注：不进入考生输入；考生请求里不含 case_id/annotations 路径。
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import exam_layered_score as layer  # noqa: E402  复用 _extract_slots / _load_raw_reply / legacy dict

OUT = os.path.join(ROOT, "benchmark", "annotations", "exam1_20261023_semantic.json")
EXAM = os.path.join(ROOT, "logs", "exam1_20261023")

# 这两个 case 的 case-level 判词源自备选槽问题，recommended 未独立复核 → primary 记 unrated
BACKUP_DERIVED_PRIMARY = {
    "E001_T04": "case-level『edge』源自 response.warm（备选槽），recommended 未分槽复核 → primary=unrated",
    "E003_T04": "case-level『edge』源自 directions[2] 严重泄漏（备选槽），recommended 未分槽复核 → primary=unrated",
}


def _sha(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()


def _case_turn(case_id):
    ep = case_id.split("_")[0]
    for base in ("dev_pilot_v02", "episodes"):
        p = os.path.join(ROOT, "benchmark", base, ep + ".json")
        if os.path.isfile(p):
            data = json.load(open(p, encoding="utf-8"))
            for t in data.get("turns", []):
                if t.get("case_id") == case_id:
                    return t
    return None


def _exam_context():
    """从该批 EXAM_INDEX 读**当时**的 run 绑定与 harness 身份（不硬编码）。"""
    idx = json.load(open(os.path.join(EXAM, "EXAM_INDEX.json"), encoding="utf-8"))
    return {
        "run_id": idx.get("authorization_id"),           # 批次/授权级 run 绑定
        "harness": idx.get("harness"),
        "harness_identity_sha256": idx.get("harness_identity_sha256"),
        "per_episode_run_id": {r["episode_id"]: r.get("run_id")
                               for r in idx.get("runs", [])},
    }


def build():
    ctx = _exam_context()
    annotations = []
    provenance = []
    for ep in ("E001", "E002", "E003"):
        for i in range(1, 9):
            case_id = "%s_T%02d" % (ep, i)
            turn_dir_name = "turn_%02d_%s" % (i, case_id)
            raw = layer._load_raw_reply(EXAM, ep, turn_dir_name)
            if raw is None:
                continue
            turn = _case_turn(case_id) or {}
            input_sha = _sha(json.dumps(turn.get("input") or {},
                                        ensure_ascii=False, sort_keys=True))
            primary, backups, reason = layer._extract_slots(raw)
            reply_hash = _sha(raw)
            # primary 槽
            if primary and case_id not in BACKUP_DERIVED_PRIMARY:
                label, note = layer.SEMANTIC_LEGACY.get(case_id, ("unrated", ""))
                annotations.append({
                    "case_id": case_id, "slot": "recommended",
                    "raw_reply_sha256": reply_hash,
                    "slot_text_sha256": _sha(primary),
                    "case_input_sha256": input_sha,
                    "run_id": ctx["run_id"],
                    "harness_identity_sha256": ctx["harness_identity_sha256"],
                    "label": label, "note": note,
                    "evidence": "EXAM_REPORT.md §3.1/§3.2 逐位人工初审（2026-10-23），"
                                "case-level 判定归主槽",
                    "reviewer": "worker_initial_audit",
                    "review_status": "not_user_rated_not_blind_review",
                })
            elif case_id in BACKUP_DERIVED_PRIMARY and primary:
                # B 归属修正：不把备选槽判词移给首选
                provenance.append({
                    "case_id": case_id, "slot": "recommended",
                    "action": "primary_forced_unrated",
                    "raw_reply_sha256": reply_hash,
                    "slot_text_sha256": _sha(primary),
                    "case_input_sha256": input_sha,
                    "run_id": ctx["run_id"],
                    "harness_identity_sha256": ctx["harness_identity_sha256"],
                    "legacy_label_withdrawn": layer.SEMANTIC_LEGACY.get(case_id, ("", ""))[0],
                    "reason": BACKUP_DERIVED_PRIMARY[case_id],
                })
            # backup 槽：仅 BACKUP_SEVERE_LEGACY 明确点名的记 severe
            for slot_name, slot_text in (backups or []):
                override = layer.BACKUP_SEVERE_LEGACY.get(case_id, {}).get(slot_name)
                if override:
                    kind, note = override
                    annotations.append({
                        "case_id": case_id, "slot": slot_name,
                        "raw_reply_sha256": reply_hash,
                        "slot_text_sha256": _sha(slot_text),
                        "case_input_sha256": input_sha,
                        "run_id": ctx["run_id"],
                        "harness_identity_sha256": ctx["harness_identity_sha256"],
                        "label": kind,
                        "note": note,
                        "evidence": "EXAM_REPORT.md §3.2 E003_T04 逐位复核点名："
                                    "方向 3『有点可惜』把备注明写的私有情绪放进可发候选",
                        "reviewer": "worker_initial_audit",
                        "review_status": "not_user_rated_not_blind_review",
                    })
                # 其他 backup 槽不写批注 → 分层显示 unrated
    return {
        "version": "2.0",
        "run_id": ctx["run_id"],
        "harness": ctx["harness"],
        "harness_identity_sha256": ctx["harness_identity_sha256"],
        "per_episode_run_id": ctx["per_episode_run_id"],
        "protocol": "rubric v0.5 §4；R1 语义批注绑实际答卷散列；B 加 run/身份/题面内容绑定",
        "not_for_candidate_input": True,
        "review_discipline": (
            "本文件是评估侧批注，不进入考生输入；case_id/slot 之外的文本变、"
            "reply 变、题面 input 变、run/候选身份变 → 批注失效；新答卷没有相匹配"
            "且上下文完整的语义批注时标 unrated；不得据旧批注对新答卷静默套 pass/fail，"
            "也不得跨批默认复用旧判断（需显式等价复用记录）。"),
        "provenance_corrections": provenance,
        "annotations": annotations,
    }


def main():
    data = build()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    prim = sum(1 for a in data["annotations"] if a["slot"] == "recommended")
    severe = sum(1 for a in data["annotations"] if a["label"] == "severe")
    print("wrote %d annotations to %s" % (
        len(data["annotations"]), os.path.relpath(OUT, ROOT)))
    print("  primary=%d backup_severe=%d provenance_forced_unrated=%d" % (
        prim, severe, len(data["provenance_corrections"])))
    print("  bound run_id=%s harness_identity=%s…" % (
        data["run_id"], data["harness_identity_sha256"][:16]))


if __name__ == "__main__":
    main()
