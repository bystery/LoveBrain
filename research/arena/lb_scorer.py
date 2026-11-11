# lb_scorer.py — LoveBrain 斗兽场确定性评分器 v0.4
# 宪法地位：唯一计分器。只读产物逐条对照 gold，绝不模拟、绝不执行候选代码。
# 三层架构的层 1（程序规则）；层 2（judge 评审团）在 judge_panel.py，永不在此进红线。
# 评分公式：score10 = 通过检查点权重 / 总权重 × 10；veto 命中 → 0。
# v0.2：新增 C10 意图契约检查点（仅带 input.intent 的题启用）。
# v0.3（rubric v0.3）：①C0 输出契约按候选声明校验（contract 参数，默认 legacy）；
#   ②新增 C11 旧事重提检查点（gold.stale_fact_must_not，记忆污染探针）。
# v0.4（蜂刺加固，2026-10-17 用户令"加上蜂刺"）：
#   ①新增 C12 物理可行性禁区（gold.geo_impossible_must_not，源自用户真实事故 F6.1：
#     异地设定下回复"那我等你下课一起去吃饭"——地理盲区）；
#   ②新增 C13 风格趋同检查（F4.3：非空话术两两 LCS 子串比 > 阈值扣分）。
# v0.4.1：C10 intent 字段兼容 dict/str 两种形态（装配层 dict、题库 str 均可用）。
# v0.5（Phase 0，refs/REFACTOR_PLAN_PHASE0.md §4）：
#   ①契约新增 may_decline：silent 轮合法输出 = decline_reason 存在（response 可全空）；
#     gold.expect_decline 断言"应不建议发送"；
#   ②gold 新增 soft_keywords_any：命中只进 evidence 不计分（关键词分级——
#     硬关键词只许锚定客观事实，闲聊风格词迁移到 soft，堵"复述得分"的人机化激励）。
# v0.7（2026-10-18 第五波判决 §2：no_reply 评分绕过修复，P0）：
#   动作三分——合法性（veto 级）/ 恰当性（计分检查）/ 任务完成（正向检查天然约束）：
#   ①越权不回：契约/轮级均未允许 may_decline 时输出 action=no_reply（或非空 decline_reason）
#     → veto=unauthorized_no_reply（旧版 schema 分支无条件放行 = 全部不回得满分漏洞）；
#   ②矛盾动作：不回动作与非空可发送正文并存 → veto=decline_with_text；
#     action=reply 与非空 decline_reason 并存 → 同 veto；
#   ③非法 action 值（非 reply/no_reply）→ veto=invalid_action；
#   ④合法不回必须显式进入不回分支：只免风格/正文类检查，另设 decline_appropriateness
#     计分检查（gold.expect_decline 或本轮 may_decline 才得分，否则"恰当性未证实"计 0），
#     堵"空白自动获得质量满分"；
#   ⑤正向文本检查（must_kw 等）在合法不回时照常判 fail——任务完成度不因不回豁免。
# v0.8（2026-10-19 第六波判决 §4：动作规范化与类型安全，P0/P1）：
#   ①先类型后语义：response/directions/analysis/reason/decline_reason 出现即验类型，
#     坏类型 → veto=json_schema_broken（evidence 记字段名），不再 AttributeError 崩掉整轮审计；
#   ②显式动作优先：action=reply 与非空 decline_reason 并存 = veto=action_conflict
#     （正文为空也不能"转换动作"走 legacy decline 分支——第六波判决反例 1）；
#   ③未知顶层字段政策（写入契约口径）：协议顶层键 = action/response/directions/
#     analysis/decline_reason/reason；未知键值为非空字符串 → veto=unknown_payload_field
#     （防夹带待发正文），其他未知键登记 schema_anomalies（消费侧不得当作协议字段）；
#   ④reason 非字符串 = 类型违规；合法 no_reply + 字符串 reason 保留为正例。
# v0.9（2026-10-19 第七波判决 §3/§5：完整输出结构校验 + 开放契约收口，P1）：
#   ①嵌套结构校验：directions 各项、response 已声明风格键、analysis.scene_facts
#     （dict；text=字符串、source_ids=字符串列表）、analysis.ongoing（dict；item/status=字符串）、
#     topic_status/topic_label=字符串——坏嵌套 → veto=json_schema_broken（此前 scene_facts[0].text
#     为对象仍得 10 分，随后机制写回 .strip() 崩溃、第二轮中止）；
#   ②评分与记忆写回共用同一份合格结构：analysis_type_errors() 是唯一权威校验器，
#     机制写回前必须过同一道门（格式失败留证，不提交非法记忆；生命周期时钟照常前进）；
#   ③开放契约收口：候选契约 extra_text_fields 显式声明的顶层扩展字段并入协议键
#     （此前被 unknown_payload_field 误杀 = 禁掉了 rubric 既有扩展能力）；
#     未声明字段的非空字符串仍拒绝。
# v0.9.1（2026-10-20 对照执行期初审发现，协议错误即修）：
#   directions[i] 显式 null 曾因 "is not None" 短路漏拦（A1-t5 null 项仍得 8.75）——
#   与第八波 scene_facts.text null 同款漏洞。directions 为必填可发送槽位
#   （契约 directions=N，rubric"破坏即 0 分"），null/非字符串一律判结构错误。
#   可选扩展字段（extra_text_fields）维持 null=缺省放行（已登记语义）。
#   影响面（离线重评）：候选臂 A1-t5/A2-t5、父臂 4 处、pilot_r3 6 处——两臂同病，
#   系模型输出随机缺陷，此前校验器不可见。
# v0.9.2（2026-10-20 第九波判决 §3 契约统一，P1）：
#   v0.9.1 判决被独立复核推翻：实际生成提示（format.md §directions +
#   HARNESS_IO_SPEC）明确允许"不适合本轮的方向输出 null，不凑满四个"——
#   null 判零属评分器单方面契约变更，与出站提示冲突，不能把遵循 null 指令的
#   输出归为生成缺陷。撤销 v0.9.1 的 null 判零，契约统一为 string|null：
#   ①directions[i]：str 或 None 合法（null=该方向本轮不交付）；非字符串拒绝；
#   ②可发送文本采集与动作判定统一以 all_reply_texts 为唯一权威
#     （修复 v0.9.1 从顶层取 style 键导致"一个 null 被杀、四个 null 满分"的自相矛盾）；
#   ③min_nonempty 继续约束 response 风格键非空数（主路径 schema 检查）；
#   ④scene_facts[].text 的 null 判定不变（写回侧消费，null 事实不可入记忆，
#     与 directions 的展示缺省语义不同层）。
#   历史输出一律按当时契约（string|null）解释，v0.9.1 重评结论作废重算。
# v0.9.3（2026-11-03，GOLD_SIDECAR 接入，benchmark/stories_v2/GOLD_SCHEMA.md v1.0）：
#   新增两个 gold 消费检查点（仅当 gold 键存在时实例化；老批次 gold={} → 行为与
#   v0.9.2 逐字节一致，回归见 tools/test_gold_sidecar_scoring.py + 两批次 dry-run 对照）：
#   ①C14 assertion_*：sidecar 原子断言 status ∈ {cancelled,invalid,unknown} 或
#     negation=true 时不得坐实（信号=gold.affirm_markers 词组；无 markers 不实例化，
#     confirmed 原子只作证据不强迫提及，rubric §10）；
#   ②C15 unknown_*：gold.unknowns 未决话题不得坐实（不确定不当确定，同词组信号）。
#   映射/加载适配器在 arena/gold_sidecar.py（C3/C6/C8/C10/C11/C12 全部复用既有检查器）。

import json
import re
from typing import Optional


# ---------- 输出契约与结构校验（A3 拆分：权威实现在 protocol_public） ----------
# ARENA_V1_CLOSEOUT 工作包 A3（2026-10-21）：纯协议函数整体迁至 arena/protocol_public.py
# （唯一权威实现），本模块 re-export 同一函数对象——评分权威不变、历史调用兼容；
# 候选运行包只允许携带 protocol_public，不允许携带本文件（评分权重/gold 不可见）。
# 迁移等价证明：tools/test_protocol_public.py（快照矩阵逐例一致）。
from protocol_public import (  # noqa: E402,F401  纯协议权威 re-export
    LEGACY_CONTRACT,
    normalize_contract,
    extract_json_block,
    all_reply_texts,
    output_structure_errors,
    analysis_type_errors,
)

# ---------- 风格评分专用（评分侧资产，不进 protocol_public） ----------

def _lcs_len(a: str, b: str) -> int:
    if not a or not b:
        return 0
    prev = [0] * (len(b) + 1)
    best = 0
    for i in range(1, len(a) + 1):
        cur = [0] * (len(b) + 1)
        ai = a[i - 1]
        for j in range(1, len(b) + 1):
            if ai == b[j - 1]:
                cur[j] = prev[j - 1] + 1
                if cur[j] > best:
                    best = cur[j]
        prev = cur
    return best


def lcs_ratio(a: str, b: str) -> float:
    """**旧语义保留**：最长公共子串 / max(len(a),1)。style_diversity 沿用旧分母，
    避免 v0.5 顺手把两条尺子一起改动（协议 §8 变更纪律：一次只动一处，改动单独版本化）。"""
    return _lcs_len(a, b) / max(len(a), 1)


def lcs_ratio_symmetric(a: str, b: str) -> float:
    """v0.5 新增：分母取 max(len(a), len(b))。note_lcs 用这个，避免短候选
    与长备注 2 字巧合即触发旧阈值（EXAM_REPORT §6#1）。"""
    return _lcs_len(a, b) / max(len(a), len(b), 1)


NOTE_LCS_MAX = 0.6      # v0.5：仅"整句照搬"级违规（旧语义保留给诊断对照）
NOTE_LCS_STRICT_MAX = 0.9  # R4：≥0.9 才算字面复读诊断 hit；0.6-0.9 允许"合法引用备注事实"
NOTE_LCS_MIN_LEN = 10   # 短文本（<10 字）不参与 note_lcs 判定


AI_FLAVOR_WORDS = [
    "总而言之", "不可否认", "在这个问题上", "首先，其次", "综上所述",
    "我觉得你可以", "建议你这样回", "作为一个", "值得注意的是",
    "望眼欲穿", "充电", "化解你的疲惫",
    # v0.4 扩充（refs/HUMAN_FLAVOR_SPEC.md，用户钦定《真人感第一》）：
    "能够理解", "听起来", "感觉你", "我理解你", "感同身受",
    # v0.5.1 扩充（Phase 0.5 判别力测试 neg_c 实测漏判后补：客服腔）：
    "感谢您的分享", "很抱歉给您", "请问还有什么", "为您整理", "为您服务",
    "希望我的建议", "随时为您", "可以帮您的吗", "贴心建议",
]

# C13 风格趋同阈值（pilot 封板前可调，调参记录落 logs/）
STYLE_SIM_MAX = 0.6


# ---------- 检查器 ----------

def run_case_scoring(case: dict, raw_reply: str, id_map: dict,
                     contract: Optional[dict] = None) -> dict:
    """case: 题目 JSON；raw_reply: 模型原始输出；id_map: {m0: role} 装配期产物；
    contract: 候选输出契约（None = legacy）。
    返回 {score10, veto, checks:[{id,desc,pts,got,pass,evidence}]}"""
    c = normalize_contract(contract)
    gold = case.get("gold", {})
    # 判决令 §8（2026-10-18）：轮级 may_decline（input.may_decline=True = 本轮允许不回）
    # 与契约级 may_decline 取并集——两候选获得一致的"允许不回"语义，不再依赖各自 params。
    if (case.get("input") or {}).get("may_decline"):
        c = dict(c)
        c["may_decline"] = True
    checks = []
    veto = None

    def add(cid, desc, pts, ok, evidence=""):
        checks.append({"id": cid, "desc": desc, "pts": pts,
                       "got": pts if ok else 0, "pass": bool(ok), "evidence": evidence})
        return ok

    out = extract_json_block(raw_reply)

    # ---- v0.8/v0.9 类型安全前置（坏类型 → 格式失败，绝不崩溃） ----
    # 协议顶层键 = 基础协议键 + 候选契约显式声明的扩展文本字段（第七波判决 §5：
    # 已声明扩展能力不得误杀；未声明键的非空字符串仍拒绝，堵夹带正文）。
    PROTOCOL_TOP_KEYS = {"action", "response", "directions", "analysis",
                         "decline_reason", "reason"} | set(c["extra_text_fields"])
    type_errs = []
    schema_anomalies = []
    if out is not None:
        for fname in ("response", "analysis"):
            if out.get(fname) is not None and not isinstance(out[fname], dict):
                type_errs.append("%s:%s" % (fname, type(out[fname]).__name__))
        if out.get("directions") is not None and not isinstance(out["directions"], list):
            type_errs.append("directions:%s" % type(out["directions"]).__name__)
        for fname in ("decline_reason", "reason"):
            if out.get(fname) is not None and not isinstance(out[fname], str):
                type_errs.append("%s:%s" % (fname, type(out[fname]).__name__))
        for k, v in out.items():
            if k not in PROTOCOL_TOP_KEYS:
                if isinstance(v, str) and v.strip():
                    # 第六波判决反例 4：未知键携带非空字符串 = 夹带待发正文嫌疑 → 专用 veto
                    add("json_schema_valid",
                        "输出契约：协议顶层键=%s（未知字段政策见契约）"
                        % ",".join(sorted(PROTOCOL_TOP_KEYS)),
                        2, False, "unknown_payload_field:%s" % k)
                    return _finish(checks, "unknown_payload_field")
                schema_anomalies.append("unknown_field:%s(%r)" % (k, v))
        if type_errs:
            add("json_schema_valid",
                "输出契约：单 JSON，协议顶层键=%s，字段类型校验先于语义"
                % ",".join(sorted(PROTOCOL_TOP_KEYS)),
                2, False, "type_violation:" + ",".join(type_errs))
            return _finish(checks, "json_schema_broken")
        # v0.9 嵌套结构校验（第七波判决 §3）：坏嵌套同样 = 格式失败，
        # 且机制写回共用本判定（不出现"评分满分、写回崩溃"的割裂）。
        nested_errs = output_structure_errors(out, contract)
        if nested_errs:
            add("json_schema_valid",
                "输出契约：单 JSON，协议顶层键=%s，嵌套结构校验先于语义"
                % ",".join(sorted(PROTOCOL_TOP_KEYS)),
                2, False, "nested_type_violation:" + ",".join(nested_errs[:6]))
            return _finish(checks, "json_schema_broken")

    # ---- v0.7/v0.8 动作统一表示与三分判定（显式动作优先，冲突拒绝） ----
    ACTION_REPLY, ACTION_NO_REPLY = "reply", "no_reply"
    action = None            # None=JSON 缺失或非法 action 值
    action_explicit = False
    decline_reason = ""
    if out is not None:
        a = out.get("action")
        dr = out.get("decline_reason")
        decline_reason = dr.strip() if isinstance(dr, str) else ""
        if a is None:
            action = ACTION_NO_REPLY if decline_reason else ACTION_REPLY
        elif a in (ACTION_REPLY, ACTION_NO_REPLY):
            action = a
            action_explicit = True
        # 其他值：action 保持 None → invalid_action

    # 可发送正文是否非空（不回与正文互斥的判定依据；directions 全 None 不算正文）
    def _has_sendable_text(o):
        if o is None:
            return False
        for _, t in all_reply_texts(o, c):
            if isinstance(t, str) and t.strip():
                return True
        return False

    is_decline = False
    veto = None
    schema_ok = False
    if out is None:
        pass    # JSON 不可解析 → C0 fail
    elif action is None:
        veto = "invalid_action"
    elif action_explicit and action == ACTION_REPLY and decline_reason:
        # v0.8（第六波判决反例 1）：显式 reply 与 decline 标志冲突 → 拒绝，
        # 不能因正文为空"转换动作"走 legacy decline 分支拿满分
        veto = "action_conflict"
    elif action == ACTION_NO_REPLY and not c.get("may_decline"):
        veto = "unauthorized_no_reply"          # 越权不回：合约未允许
    elif action == ACTION_NO_REPLY and _has_sendable_text(out):
        veto = "decline_with_text"              # 矛盾动作：说不回又有待发正文
    elif action == ACTION_NO_REPLY:
        # 合法不回：显式进入不回分支（response/directions 可缺省；禁止夹带正文已由上查）
        is_decline = True
        schema_ok = True
    elif decline_reason:
        # 旧式静默（legacy decline 形态，无显式 action）：保留原 schema 要求
        is_decline = True
        resp = out.get("response")
        dirs = out.get("directions")
        schema_ok = (isinstance(resp, dict)
                     and isinstance(dirs, list) and len(dirs) == c["directions"]
                     and isinstance(out.get("analysis"), dict))
    else:
        resp = out.get("response")
        dirs = out.get("directions")
        ana = out.get("analysis")
        schema_ok = (isinstance(resp, dict)
                     and sum(1 for k in c["style_keys"]
                             if isinstance(resp.get(k), str) and resp.get(k).strip())
                     >= c["min_nonempty"]
                     and isinstance(dirs, list) and len(dirs) == c["directions"]
                     and isinstance(ana, dict))
    if veto:
        add("json_schema_valid",
            "输出契约：单 JSON，style_keys 非空≥%d，directions=%d，含 analysis%s"
            % (c["min_nonempty"], c["directions"],
               "（或合法 decline）" if c.get("may_decline") else ""),
            2, False, veto)
        return _finish(checks, veto)
    add("json_schema_valid",
        "输出契约：单 JSON，style_keys 非空≥%d，directions=%d，含 analysis%s"
        % (c["min_nonempty"], c["directions"],
           "（或合法 decline）" if c.get("may_decline") else ""),
        2, schema_ok, ";".join(schema_anomalies))
    if not schema_ok:
        veto = "json_schema_broken"
        return _finish(checks, veto)

    texts = all_reply_texts(out, c)
    ana = out.get("analysis") or {}

    # v0.7 恰当性检查（合法不回专用）：gold.expect_decline 或本轮 may_decline 才可得分；
    # 两者皆无 = 层 1 无法确认"此刻不回合适"，按未证实计 0 分（不当 veto，交给层 2 复核）。
    if is_decline:
        gold_declare = bool(gold.get("expect_decline"))
        turn_declare = bool((case.get("input") or {}).get("may_decline"))
        add("decline_appropriateness",
            "不回恰当性（expect_decline=%s / 本轮 may_decline=%s）"
            % (gold_declare, turn_declare),
            2, gold_declare or turn_declare,
            (decline_reason or (out.get("reason") if isinstance(out.get("reason"), str) else "")
             or "action=no_reply")[:60])

    # C1 source_ids 合法（veto 级：非法引用 = 程序可确认严重错误）
    valid_ids = set(id_map.keys())
    bad_refs = []
    for fact in ana.get("scene_facts") or []:
        for sid in fact.get("source_ids") or []:
            if sid not in valid_ids:
                bad_refs.append(sid)
    add("source_ids_exist", "analysis.scene_facts 的 source_ids 必须指向真实消息 id",
        2, not bad_refs, ",".join(bad_refs))
    if bad_refs:
        veto = "illegal_source_id:" + ",".join(bad_refs)
        return _finish(checks, veto)

    # C2 备注字面复读诊断（v0.5 R4：**权重 0，不入门禁**）。
    # 相似度阈值不能可靠区分"合法照传事实"与"泄漏私有信息"——reviewer 反例：备注
    # "我明天14:55到南门，直接告诉她"，候选照传"我明天14:55到南门"（LCS/max≈0.85）
    # 属合法引用备注授权的事实，旧阈值判 fail 属工具越权。
    # 隐私/事实边界归人工或校准 judge 的语义批注（tools/exam_layered_score.py 绑散列）；
    # 本项只在 ≥0.9（近乎整句照搬）时给 hit 信号，不参与 score10。
    note = (case.get("input", {}).get("note") or "").strip()
    if note:
        eligible = [(nm, t) for nm, t in texts if isinstance(t, str)
                    and len(t) >= NOTE_LCS_MIN_LEN]
        if eligible:
            worst = max((lcs_ratio_symmetric(t, note) for _, t in eligible), default=0.0)
            add("note_lcs_diag",
                "备注字面复读诊断（权重 0，不入门禁）：max(len) 分母、≥%d 字候选、"
                "≥%.2f 才标 hit" % (NOTE_LCS_MIN_LEN, NOTE_LCS_STRICT_MAX),
                0, worst < NOTE_LCS_STRICT_MAX,
                "max_lcs=%.2f pool=%d" % (worst, len(eligible)))
        else:
            add("note_lcs_diag",
                "备注字面复读诊断（权重 0）：本轮所有候选 <%d 字，豁免" % NOTE_LCS_MIN_LEN,
                0, True, "skipped_short(max=%d)" % max((len(t) for _, t in texts), default=0))

    # C3 禁止关键词（must_not_keywords：命中任一可发送文本即失分）
    for i, kw in enumerate(gold.get("must_not_keywords") or []):
        hits = [name for name, t in texts if kw in t]
        add("must_not_kw_%d" % i, "禁止出现：%s" % kw, 1, not hits,
            "hits=" + ",".join(hits) if hits else "")

    # C4 必要关键词（must_keywords_any：任一可发送文本命中其一即得分）
    # Phase 0 分级纪律：must_* 只许锚定客观事实（时间/约定/泄露禁令）；风格承接词用 soft
    for i, group in enumerate(gold.get("must_keywords_any") or []):
        options = group if isinstance(group, list) else [group]
        hit = any(any(opt in t for opt in options) for _, t in texts)
        add("must_kw_%d" % i, "必须承接（任一）：%s" % "/".join(options), 1, hit)

    # C4s 软关键词（v0.5 新增：只进 evidence 不计分——防"复述上下文得分"的人机化激励）
    for i, group in enumerate(gold.get("soft_keywords_any") or []):
        options = group if isinstance(group, list) else [group]
        hit = any(any(opt in t for opt in options) for _, t in texts)
        add("soft_kw_%d" % i, "软关键词（不计分，仅证据）：%s" % "/".join(options), 0, hit,
            "hit" if hit else "miss")

    # C0s 静默断言（v0.5：gold.expect_decline 的 silent 轮，配合 may_decline 契约）
    # v0.6：action="no_reply"（可无 decline_reason）同样满足"不建议发送"
    if gold.get("expect_decline"):
        reason = (out.get("decline_reason") or "").strip() if out else ""
        ok = is_decline and bool(reason or (out or {}).get("action") == "no_reply")
        add("decline_expected", "本轮应不建议发送（decline_reason 或 action=no_reply）", 2, ok,
            (reason or "action=no_reply")[:60] if out else "no_output")

    # C5 话题状态断言
    exp_status = gold.get("expected_topic_status")
    if exp_status:
        ok = ana.get("topic_status") in (exp_status if isinstance(exp_status, list) else [exp_status])
        add("topic_status", "topic_status ∈ %s" % exp_status, 1, ok,
            "got=%s" % ana.get("topic_status"))
    exp_label = gold.get("expected_topic_label_contains")
    if exp_label:
        ok = exp_label in (ana.get("topic_label") or "")
        add("topic_label", "topic_label 含「%s」" % exp_label, 1, ok,
            "got=%s" % ana.get("topic_label"))

    # C6 记忆状态断言：禁止新增事实（forbidden_new_facts 不得出现在 scene_facts）
    for i, ff in enumerate(gold.get("forbidden_new_facts") or []):
        hits = [f.get("text", "") for f in ana.get("scene_facts") or [] if ff in f.get("text", "")]
        add("forbidden_fact_%d" % i, "禁止新增事实：%s" % ff, 2, not hits,
            ";".join(hits))

    # C7 进行中事项断言（ongoing 期望操作；status 允许列表——pilot 期枚举未契约化从宽）
    for i, exp in enumerate(gold.get("expect_ongoing") or []):
        found = None
        for o in ana.get("ongoing") or []:
            if exp.get("item_contains", "") in (o.get("item") or ""):
                found = o
                break
        exp_status = exp.get("status")
        allowed = exp_status if isinstance(exp_status, list) else [exp_status]
        ok = found is not None and found.get("status") in allowed
        add("ongoing_%d" % i, "事项「%s」status=%s" % (exp.get("item_contains"), exp.get("status")),
            2, ok, json.dumps(found, ensure_ascii=False) if found else "not_found")

    # C8 禁止复活已取消事项
    for i, item in enumerate(gold.get("cancelled_items") or []):
        hits = [o for o in ana.get("ongoing") or []
                if item in (o.get("item") or "") and o.get("status") not in ("已取消",)]
        add("cancelled_%d" % i, "已取消事项不得复活：%s" % item, 2, not hits)

    # C9 AI 味词表
    hits = [(name, w) for name, t in texts for w in AI_FLAVOR_WORDS if w in t]
    add("ai_flavor", "AI 味违禁词表零命中", 1, not hits,
        ";".join("%s:%s" % h for h in hits[:5]))

    # C10 意图契约（rubric v0.2：仅带 input.intent 的题启用；无 intent 跳过）
    # v0.4.1 兼容：intent 允许字符串（"调情"）或装配层 dict（{"enabled":true,"text":"调情"}）
    intent = case.get("input", {}).get("intent") or ""
    if isinstance(intent, dict):
        intent = (intent.get("text") or "") if intent.get("enabled", True) else ""
    intent = intent.strip() if isinstance(intent, str) else ""
    if intent:
        for i, group in enumerate(gold.get("intent_keywords_any") or []):
            options = group if isinstance(group, list) else [group]
            hit = any(any(opt in t for opt in options) for _, t in texts)
            add("intent_kw_%d" % i, "意图「%s」信号词（任一命中）：%s" % (intent, "/".join(options)),
                2, hit)
        for i, kw in enumerate(gold.get("intent_must_not_keywords") or []):
            hits2 = [name for name, t in texts if kw in t]
            add("intent_forbid_%d" % i, "意图「%s」禁区词零命中：%s" % (intent, kw), 2, not hits2,
                "hits=" + ",".join(hits2) if hits2 else "")

    # C11 旧事重提（记忆污染探针，rubric v0.3）：不得主动提及的旧事关键词零命中
    for i, kw in enumerate(gold.get("stale_fact_must_not") or []):
        hits = [name for name, t in texts if kw in t]
        add("stale_kw_%d" % i, "旧事重提（记忆污染）禁止提及：%s" % kw, 2, not hits,
            "hits=" + ",".join(hits) if hits else "")

    # C12 物理可行性禁区（蜂刺，rubric v0.4 / F6）：gold.geo_impossible_must_not
    # 列出"本情境下客观上不可能"的动作词（如异地设定下的"一起吃饭""我去接你"）
    for i, kw in enumerate(gold.get("geo_impossible_must_not") or []):
        hits = [name for name, t in texts if kw in t]
        add("geo_must_not_%d" % i, "物理可行性禁区（%s）：%s"
            % (gold.get("geo_reason") or "客观不可能", kw), 2, not hits,
            "hits=" + ",".join(hits) if hits else "")

    # C14 gold 原子断言 must-not-affirm（GOLD_SIDECAR v1.0，benchmark/stories_v2/GOLD_SCHEMA.md）：
    # status ∈ {cancelled, invalid, unknown} 或 negation=true 的原子不得被坐实；
    # 判定信号 = gold 自带 affirm_markers 词组（组内全词同文本出现=坐实）。无 markers 不判分
    # （适配器已记 unrated，见 gold_sidecar.apply_sidecar）。老批次无此键 → 行为不变。
    for i, a in enumerate(gold.get("assertion_checks") or []):
        hit_group = None
        for group in a.get("affirm_markers") or []:
            words = [w for w in (group if isinstance(group, list) else [group]) if w]
            if words and any(all(w in t for w in words) for _, t in texts):
                hit_group = group
                break
        add("assertion_%d" % i,
            "gold 原子不得坐实（status=%s negation=%s）：%s"
            % (a.get("status"), a.get("negation"), a.get("value") or a.get("id")),
            2, hit_group is None,
            "affirm_group=%s" % ",".join(hit_group) if hit_group else "")

    # C15 unknown 处理（不确定不当确定）：gold.unknown_must_not_affirm 列出的
    # 未决话题不得在回复里坐实（同词组信号，与 geo_must_not 同档蜂刺）。
    for i, u in enumerate(gold.get("unknown_must_not_affirm") or []):
        hit_group = None
        for group in u.get("affirm_markers") or []:
            words = [w for w in (group if isinstance(group, list) else [group]) if w]
            if words and any(all(w in t for w in words) for _, t in texts):
                hit_group = group
                break
        add("unknown_%d" % i, "未决话题不得坐实：%s" % (u.get("topic") or "?"),
            2, hit_group is None,
            "affirm_group=%s" % ",".join(hit_group) if hit_group else "")

    # C13 风格趋同（蜂刺，rubric v0.4 / F4.3）：非空话术两两 LCS 子串比 ≤ 0.6
    # 无 gold 依赖，对所有题生效；阈值 pilot 封板前可调
    # 长度门槛 4 字（用户口味为极短话术，中位 19 字；<4 字不参与趋同判定）
    style_texts = [(k, t) for k, t in texts
                   if not k.startswith("directions") and len(t) >= 4]
    worst_pair, worst_sim = None, 0.0
    for a in range(len(style_texts)):
        for b in range(a + 1, len(style_texts)):
            ta, tb = style_texts[a], style_texts[b]
            short, long_ = (ta, tb) if len(ta[1]) <= len(tb[1]) else (tb, ta)
            sim = lcs_ratio(short[1], long_[1])
            if sim > worst_sim:
                worst_sim, worst_pair = sim, (short[0], long_[0])
    add("style_diversity", "风格趋同：候选话术两两 LCS 子串比 ≤ %.2f（F4.3）" % STYLE_SIM_MAX,
        1, worst_sim <= STYLE_SIM_MAX,
        "max=%.2f pair=%s" % (worst_sim, worst_pair) if worst_pair else "")

    return _finish(checks, veto)


def _finish(checks, veto):
    total = sum(c["pts"] for c in checks)
    got = sum(c["got"] for c in checks)
    score10 = 0.0 if veto else (round(got / total * 10, 2) if total else 0.0)
    return {"score10": score10, "veto": veto, "checks": checks,
            "pts_got": got if not veto else 0, "pts_total": total}


def run_case_scoring_safe(case: dict, raw_reply: str, id_map: dict,
                          contract: Optional[dict] = None) -> dict:
    """评分崩溃兜底（第六波判决 §4 末条）：坏字段不应使整条 episode 无法继续审计。
    任何意外异常 → 本轮记格式失败（scorer_crash），评分进程继续。"""
    try:
        return run_case_scoring(case, raw_reply, id_map, contract)
    except Exception as e:
        return {"score10": 0.0, "veto": "scorer_crash:%s: %s" % (type(e).__name__, e),
                "checks": [], "crash": True}
