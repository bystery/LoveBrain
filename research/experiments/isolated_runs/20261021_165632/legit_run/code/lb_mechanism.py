# lb_mechanism.py — 机制层接口契约 v1 + 基线机制实现
# 宪法地位：候选 candidates/round_N/mechanism/mechanism.py 必须暴露与本文件相同的三个函数。
# runner 加载优先级：候选 mechanism/mechanism.py 存在 → 用之；否则用本模块（= lb_assembly 原样包装，
# 即 round_0_baseline 语义，对拍豁免权仅属 round_0_baseline）。
#
# 接口契约（MECHANISM_INTERFACE v1）：
#   init_state(assets_root: str, episode_meta: dict) -> dict
#       episode 开考时调用一次。episode_meta = {"episode_id", "initial_kb", ...}。
#       单题模式不调用（state=None）。
#   build_prompts(assets_root: str, case_input: dict, state: dict|None)
#       -> {"system": str, "user": str, "id_map": dict, "scene": str}
#       装配入口。state 为机制自管的记忆实例（episode 模式跨轮传递；单题为 None）。
#   update_state(state: dict, case_input: dict, parsed_output: dict|None, raw_reply: str) -> dict
#       每轮评分后调用，把本轮输出写回记忆。parsed_output 为模型输出提取的 JSON（解析失败为 None）。
#       必须返回 state（可原地改）。抛出异常 = episode 中止并记 mechanism_error。
#
# 隔离纪律（宪法 §6）：不同候选使用隔离记忆实例——runner 每个 episode 每次运行都调 init_state
# 新建 state，机制不得在模块级/global 变量存跨轮状态。

import copy
import datetime
import json
from typing import Optional

import lb_assembly
import lb_scorer


# ---------- 基线机制 = lb_assembly 原样包装 + 默认写回 v0 ----------

def init_state(assets_root: str, episode_meta: dict) -> dict:
    # 2026-10-18 判决令 §2.2/§2.3：persona 经白名单下发（共同可见入口）；
    # last_clock / topic_started_at 记真实时间戳，替代"每轮归零/固定 +3"的轮数近似。
    return {"kb": copy.deepcopy(episode_meta.get("initial_kb") or {}),
            "persona": copy.deepcopy(episode_meta.get("persona") or {}),
            "last_clock": None, "topic_started_at": None,
            "turn": 0}


def _hours_between(ts_a: str, ts_b: str) -> Optional[float]:
    try:
        fmt = "%Y-%m-%d %H:%M"
        a = datetime.datetime.strptime(ts_a, fmt)
        b = datetime.datetime.strptime(ts_b, fmt)
        return round((b - a).total_seconds() / 3600.0, 2)
    except Exception:
        return None


def build_prompts(assets_root: str, case_input: dict, state: Optional[dict]) -> dict:
    if state is not None:
        # episode 模式：kb 由机制状态供给（turn input 里的 kb 字段被状态覆盖）
        case_input = dict(case_input)
        case_input["kb"] = copy.deepcopy(state.get("kb") or {})
        case_input.setdefault("persona", state.get("persona") or {})
        kb = case_input["kb"]
        clock = case_input.get("clock")
        # 真实时间差（判决令 §2.3：不能用轮数代替小时）
        if clock and state.get("last_clock"):
            h = _hours_between(state["last_clock"], clock)
            if h is not None:
                kb["hours_since_last_exchange"] = max(0.0, h)
        if clock and state.get("topic_started_at"):
            h2 = _hours_between(state["topic_started_at"], clock)
            kb["topic_age_hours"] = max(0.0, h2) if h2 is not None else kb.get("topic_age_hours", 0)
    return lb_assembly.build_reply_prompts(assets_root, case_input)


def update_state(state: dict, case_input: dict, parsed_output: Optional[dict],
                 raw_reply: str) -> dict:
    """默认写回策略 v0.2（2026-10-19 第七波判决 §3：写回与评分共用同一份合格结构）。
    规则：
      - 生命周期时钟（last_clock）每轮更新为当前 clock——时钟推进是生命周期状态，
        不属于记忆内容，格式失败也照常前进（第七波判决 §3-2 明确规定）；
      - analysis 必须先过 lb_scorer.analysis_type_errors（与评分同一权威校验器）：
        不合格 → 本轮不提交任何记忆内容（topic/facts/ongoing 全部跳过），
        跳过原因留证在 state["last_writeback_skipped"]（runner 落 state_after.json 可审计），
        不抛异常、不中止 episode（第二轮可继续审计）；
      - 合格时：topic_label 变更 → kb.topic 更新、topic_started_at = 当前 clock
        （topic_age_hours 由 build_prompts 按真实时间差计算）；
        scene_facts → 追加 scene_chain 行；ongoing[] → 合并进 kb.ongoing_plan；
      - lessons 不自动写（保守：经验教训沉淀归机制变异探索）。
    """
    kb = state.setdefault("kb", {})
    state["turn"] = state.get("turn", 0) + 1
    clock = case_input.get("clock")
    if clock:
        state["last_clock"] = clock
    if not parsed_output:
        return state
    ana = parsed_output.get("analysis")
    if not isinstance(ana, dict):
        return state

    # v0.9 写回门（第七波判决 §3-2）：与评分共用 analysis_type_errors。
    # 坏嵌套（如 scene_facts[0].text 为对象）→ 格式失败已由评分记 veto，
    # 此处不得消费未经检查的 raw JSON，也不得把 AttributeError 转成 episode 中止。
    errs = lb_scorer.analysis_type_errors(ana)
    if errs:
        state["last_writeback_skipped"] = {
            "turn": state["turn"], "clock": clock,
            "reason": "nested_type_violation", "errors": errs[:10],
            "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")}
        return state

    label = (ana.get("topic_label") or "").strip()
    if label and label != kb.get("topic"):
        kb["topic"] = label
        if clock:
            state["topic_started_at"] = clock

    facts = [f.get("text", "").strip() for f in ana.get("scene_facts") or []
             if isinstance(f, dict) and f.get("text", "").strip()]
    if facts:
        clock = case_input.get("clock", "1970-01-01 00:00")
        line = "- [%s] %s" % (clock, "；".join(facts))
        kb["scene_chain"] = ((kb.get("scene_chain") or "") + "\n" + line).strip()

    ongoing = [o for o in ana.get("ongoing") or []
               if isinstance(o, dict) and (o.get("item") or "").strip()]
    if ongoing:
        lines = [ln for ln in (kb.get("ongoing_plan") or "").split("\n") if ln.strip()]
        for o in ongoing:
            item, status = o["item"].strip(), (o.get("status") or "").strip()
            lines = [ln for ln in lines if not ln.split("|")[0].strip() == item]
            lines.append("%s | %s" % (item, status))
        kb["ongoing_plan"] = "\n".join(lines)

    return state
