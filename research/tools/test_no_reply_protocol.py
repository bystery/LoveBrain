# -*- coding: utf-8 -*-
"""test_no_reply_protocol.py — 判决令 §8 验收 + 第五波判决 §2 验收：
no_reply 协议对两候选一致生效，且"全部不回"不能刷满分。

正夹具两枚（用户明确不发 / 允许可不回）+ echo 替身两种输出形态：
  A. action="no_reply"（新统一动作）
  B. decline_reason="..."（旧式静默）
负对照（第五波判决 §2-4）：
  N1 越权不回：普通问句（无 may_decline）输出 action=no_reply → veto=unauthorized_no_reply
  N2 矛盾动作：no_reply + 非空正文 → veto=decline_with_text
  N3 非法动作：action="block" → veto=invalid_action
  N4 reply+decline_reason+正文 → veto=action_conflict（v0.8 显式动作冲突先于正文检查）
  N5 全部不回扫描：对 PILOT_L1 全部 12 节点提交 {"action":"no_reply"}，
     两候选评分路径均不得全得 10 分（仅 may_decline 轮可得满分）。
断言：baseline / beta_single / phase0_beta 三候选语义一致。零网络调用。"""
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "arena"))
import lb_scorer  # noqa: E402
import lb_runner  # noqa: E402

FIXTURE_ALLOW = {   # 可不回：不回与短告别均合法
    "case_id": "FIX_allow", "turn_type": "may_decline",
    "input": {"clock": "2026-10-24 12:30",
              "messages": [{"role": "HER", "text": "不聊了 我去看剧了", "ts": "2026-10-24 12:28"},
                           {"role": "HER", "text": "拜拜", "ts": "2026-10-24 12:30"}],
              "may_decline": True},
    "gold": {},
}
FIXTURE_REQUIRED = {  # 用户明确不发：必须不建议发送
    "case_id": "FIX_required", "turn_type": "silent",
    "input": {"clock": "2026-10-24 23:58",
              "messages": [{"role": "HER", "text": "别回了 我睡了", "ts": "2026-10-24 23:58"}],
              "may_decline": True},
    "gold": {"expect_decline": True},
}
FIXTURE_NORMAL_Q = {  # N1 负对照：普通问句，无任何不回许可
    "case_id": "FIX_normal_q", "turn_type": "reply",
    "input": {"clock": "2026-10-24 12:30",
              "messages": [{"role": "HER", "text": "几点见？", "ts": "2026-10-24 12:28"}]},
    "gold": {},
}


def out_action():
    return json.dumps({"action": "no_reply", "reason": "对方已收尾"}, ensure_ascii=False)


def out_decline():
    return json.dumps({"response": {"recommended": None, "bad_boy": None,
                                    "playful": None, "warm": None},
                       "decline_reason": "对方已收尾",
                       "directions": [None] * 4,
                       "analysis": {"topic_status": "end", "topic_label": "收尾",
                                    "scene_facts": [], "ongoing": []}},
                      ensure_ascii=False)


def out_normal():
    return json.dumps({"response": {"recommended": "拜拜 下次聊", "bad_boy": "滚去睡吧",
                                    "playful": "晚安戊", "warm": "好梦"},
                       "directions": ["下次聊", "路上慢点", "到家说一声", "明天见"],
                       "analysis": {"topic_status": "end", "topic_label": "道别",
                                    "scene_facts": [], "ongoing": []}},
                      ensure_ascii=False)


def out_contradiction():   # N2：不回动作 + 非空待发正文
    return json.dumps({"action": "no_reply",
                       "response": {"recommended": "拜拜 下次聊", "bad_boy": None,
                                    "playful": None, "warm": None},
                       "directions": [None] * 4,
                       "analysis": {"topic_status": "end", "topic_label": "道别",
                                    "scene_facts": [], "ongoing": []}},
                      ensure_ascii=False)


def out_reply_with_reason():   # N4：action=reply + decline_reason + 正文
    return json.dumps({"action": "reply", "decline_reason": "不该回",
                       "response": {"recommended": "拜拜", "bad_boy": None,
                                    "playful": None, "warm": None},
                       "directions": ["下次聊", "路上慢点", "到家说一声", "明天见"],
                       "analysis": {"topic_status": "end", "topic_label": "道别",
                                    "scene_facts": [], "ongoing": []}},
                      ensure_ascii=False)


def out_invalid_action():  # N3：非法动作值
    return json.dumps({"action": "block", "reason": "??"}, ensure_ascii=False)


def main():
    fails = []
    for cand in ("round_0_baseline", "phase0_beta_single", "phase0_beta"):
        contract = lb_runner.load_contract(os.path.join("candidates", cand))
        # 第五波判决 §2：baseline 顶层 may_decline 必须被 load_contract 接线
        if cand == "round_0_baseline" and not (contract or {}).get("may_decline"):
            fails.append("baseline 顶层 may_decline 未被 load_contract 接线: %s" % contract)
        # ---- 正夹具（既有 18 组合） ----
        for case, tag in ((FIXTURE_ALLOW, "allow"), (FIXTURE_REQUIRED, "required")):
            for name, raw in (("action", out_action()), ("decline", out_decline()),
                              ("normal", out_normal())):
                r = lb_scorer.run_case_scoring(case, raw, {}, contract)
                veto, c0 = r["veto"], {x["id"]: x for x in r["checks"]}
                schema_ok = c0["json_schema_valid"]["pass"]
                if name in ("action", "decline"):
                    if veto or not schema_ok:
                        fails.append("%s/%s/%s: 合法不回被 veto=%s schema=%s"
                                     % (cand, tag, name, veto, schema_ok))
                else:  # normal 回复在 allow 夹具合法；在 required 夹具应扣 decline_expected 分
                    if tag == "allow" and (veto or not schema_ok):
                        fails.append("%s/allow/normal: 正常告别被误判 veto=%s" % (cand, veto))
                    if tag == "required":
                        dec = c0.get("decline_expected")
                        if dec is None or dec["pass"]:
                            fails.append("%s/required/normal: 违例未被扣分" % cand)
        # ---- N1 越权不回（legacy 默认契约：契约与轮级均未许可） ----
        # 注：契约级 may_decline=true 时属"许可但恰当性未证实"路径（decline_appropriateness
        # 扣分，得分 <10）；越权 veto 只在真正无许可（legacy 契约 + 无轮级标记）时触发。
        for name, raw in (("action", out_action()), ("decline", out_decline())):
            r = lb_scorer.run_case_scoring(FIXTURE_NORMAL_Q, raw, {}, None)
            if r["veto"] != "unauthorized_no_reply":
                fails.append("%s/N1/%s: 越权不回未被拦（veto=%s, score=%s）"
                             % (cand, name, r["veto"], r["score10"]))
            # 许可契约下不得满分（恰当性未证实扣分）
            r2 = lb_scorer.run_case_scoring(FIXTURE_NORMAL_Q, raw, {}, contract)
            if r2["score10"] >= 10 or r2["veto"]:
                fails.append("%s/N1/%s: 许可契约下普通问句不回应扣恰当性分（score=%s veto=%s）"
                             % (cand, name, r2["score10"], r2["veto"]))
        # ---- N2 矛盾动作 ----
        r = lb_scorer.run_case_scoring(FIXTURE_ALLOW, out_contradiction(), {}, contract)
        if r["veto"] != "decline_with_text":
            fails.append("%s/N2: 不回+正文矛盾未被拦（veto=%s, score=%s）"
                         % (cand, r["veto"], r["score10"]))
        # ---- N3 非法动作 ----
        r = lb_scorer.run_case_scoring(FIXTURE_ALLOW, out_invalid_action(), {}, contract)
        if r["veto"] != "invalid_action":
            fails.append("%s/N3: 非法动作未被拦（veto=%s）" % (cand, r["veto"]))
        # ---- N4 reply+decline_reason+正文（v0.8：显式动作冲突优先，veto=action_conflict） ----
        r = lb_scorer.run_case_scoring(FIXTURE_ALLOW, out_reply_with_reason(), {}, contract)
        if r["veto"] != "action_conflict":
            fails.append("%s/N4: reply+decline_reason+正文未被拦（veto=%s）" % (cand, r["veto"]))
        # ---- N6 reply+decline_reason+空结构（第六波判决反例 1：此前 10 分无 veto） ----
        r = lb_scorer.run_case_scoring(FIXTURE_ALLOW, json.dumps(
            {"action": "reply", "decline_reason": "不必回",
             "response": {}, "directions": ["", "", "", ""], "analysis": {}}), {}, contract)
        if r["veto"] != "action_conflict":
            fails.append("%s/N6: 显式reply+decline冲突（空正文）未被拦（veto=%s, score=%s）"
                         % (cand, r["veto"], r["score10"]))
        # ---- N7 no_reply + response 为字符串（此前 AttributeError 崩溃） ----
        try:
            r = lb_scorer.run_case_scoring(FIXTURE_ALLOW, json.dumps(
                {"action": "no_reply", "response": "我直接发文字"}), {}, contract)
            if r["veto"] != "json_schema_broken":
                fails.append("%s/N7: response 坏类型未被拦（veto=%s）" % (cand, r["veto"]))
        except Exception as e:
            fails.append("%s/N7: response 坏类型使评分器崩溃（%s: %s）"
                         % (cand, type(e).__name__, e))
        # ---- N8 no_reply + analysis 为字符串（此前 AttributeError 崩溃） ----
        try:
            r = lb_scorer.run_case_scoring(FIXTURE_ALLOW, json.dumps(
                {"action": "no_reply", "analysis": "她想睡了"}), {}, contract)
            if r["veto"] != "json_schema_broken":
                fails.append("%s/N8: analysis 坏类型未被拦（veto=%s）" % (cand, r["veto"]))
        except Exception as e:
            fails.append("%s/N8: analysis 坏类型使评分器崩溃（%s: %s）"
                         % (cand, type(e).__name__, e))
        # ---- N9 no_reply + 顶层 text 非空（第六波判决反例 4：此前 10 分无 veto） ----
        r = lb_scorer.run_case_scoring(FIXTURE_ALLOW, json.dumps(
            {"action": "no_reply", "text": "那你早点休息呀"}), {}, contract)
        if r["veto"] != "unknown_payload_field":
            fails.append("%s/N9: 未知顶层字段夹带正文未被拦（veto=%s）" % (cand, r["veto"]))
        # ---- N10 no_reply + reason 为对象（此前 10 分无 veto） ----
        r = lb_scorer.run_case_scoring(FIXTURE_ALLOW, json.dumps(
            {"action": "no_reply", "reason": {"because": "太晚"}}), {}, contract)
        if r["veto"] != "json_schema_broken":
            fails.append("%s/N10: reason 非字符串未被拦（veto=%s）" % (cand, r["veto"]))
        # ---- N11 正例保留：合法 no_reply + 字符串 reason = 满分恰当路径 ----
        r = lb_scorer.run_case_scoring(FIXTURE_ALLOW, json.dumps(
            {"action": "no_reply", "reason": "她说拜拜，短告别，不回自然"}), {}, contract)
        if r["veto"] or r["score10"] < 10:
            fails.append("%s/N11: 合法不回+字符串reason 正例被误伤（veto=%s, score=%s）"
                         % (cand, r["veto"], r["score10"]))
        # ---- N5 全部不回扫描（PILOT_L1 全 12 节点） ----
        ep = json.load(open(os.path.join(
            os.path.dirname(os.path.abspath(__file__)), "..",
            "benchmark", "episodes", "PILOT_L1_v1.json"), encoding="utf-8"))
        scores = []
        for t in ep["turns"]:
            r = lb_scorer.run_case_scoring(t, out_action(), {}, contract)
            scores.append((t["case_id"], r["score10"], r["veto"],
                           bool((t.get("input") or {}).get("may_decline"))))
        for cid, s10, veto, md in scores:
            if s10 >= 10 and not md:
                fails.append("%s/N5: 非可不回节点 %s 被全部不回刷满分（%s）"
                             % (cand, cid, s10))
        if abs(sum(s for _, s, _, _ in scores) - 120.0) < 1e-9:
            fails.append("%s/N5: 全部不回 = 12/12 满分（绕过成立）" % cand)

    if fails:
        print("NO_REPLY PROTOCOL FAIL")
        for x in fails:
            print(" -", x)
        sys.exit(1)
    print("NO_REPLY PROTOCOL PASS：三候选 × {action,decline,normal} × {allow,required} 一致；"
          "越权/矛盾/非法动作全拦截；全部不回无法刷满分。")
    print("NO_REPLY PROTOCOL PASS", file=sys.stderr)


if __name__ == "__main__":
    main()
