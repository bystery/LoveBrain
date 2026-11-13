# lb_runner.py — LoveBrain 斗兽场考场运行时 v2（Phase 0 改造，refs/REFACTOR_PLAN_PHASE0.md）
# 职责：case/episode → Harness 生命周期驱动（v2 开放接口 / v1 兼容回退）→ 网关记账 → 评分 → manifest
# v2 变更（2026-10-17 Phase 0）：
#   - Harness 生命周期 v2：候选可暴露 init/run_turn/snapshot/restore，持有 ModelGateway 自主
#     多阶段调模型（多阶段推理成为一等能力，审计能力 3 缺口关闭）；state 可为任意对象，
#     快照为任意 bytes（文件型/sqlite 存储合法，审计能力 1 缺口关闭）。
#   - 消毒边界：交给机制/网关的 turn_ctx 剥掉 gold/mock_response/echo（审计能力 6 敞口关闭）；
#     适配器统一经 lb_gateway，不再接收原始 case。
#   - checkpoint/resume：每轮末写 checkpoint（轮指针 + 快照 bytes），--resume 断点续跑，
#     --stop-after 模拟中断（审计能力 4 缺口关闭）。
#   - 轮型协议 turn_type：reply（默认）/ rewrite（改草稿）/ silent（不建议发送，配 may_decline 契约）。
#   - v1 三函数接口继续可用（round_0_baseline 对拍豁免需要它）。
# 宪法纪律不变：本工具只做确定性搬运；变异与归因归 Worker；每轮产物全部落盘可复核。

import argparse
import copy
import datetime
import hashlib
import importlib.util
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lb_assembly
import lb_mechanism
import lb_scorer
from lb_gateway import ADAPTERS, ModelGateway

# 候选可见边界（2026-10-18 第四波判决 §2.1：黑名单 → 白名单）
# 旧 SANITIZE_DROP 黑名单只删 gold/mock_response/echo，漏掉 meta（布景备注/正确策略/隐藏信息）
# 与 episode 顶层 gaps（未来时间+隐藏原因）——接口违规。现改允许字段清单，白名单之外一律不给候选。
TURN_VISIBLE_KEYS = ("case_id", "input", "turn_type", "turn_index", "assets_root")
INPUT_VISIBLE_KEYS = ("messages", "clock", "intent", "may_decline", "note", "kb")
EP_META_VISIBLE_KEYS = ("episode_id", "persona", "initial_kb")
# 第五波判决 §7：容器内部不许藏作者元数据——消息/意图/kb 按允许字段重建 + 深拷贝，
# 既剥未知字段，也切断"候选改消息对象 = 改到评估侧原对象"的别名共享。
MESSAGE_VISIBLE_KEYS = ("role", "text", "ts", "image")
INTENT_VISIBLE_KEYS = ("enabled", "text", "status", "expiry", "now")
# kb 是冻结输入字典；仅允许装配层/机制层实际消费的键（未知键 = 疑似藏元数据）
KB_VISIBLE_KEYS = ("me", "her", "warmth", "style", "stage", "topic", "topic_age_hours",
                   "hours_since_last_exchange", "lessons", "recent", "scene_chain",
                   "ongoing_plan")


# ---------- token 记账（确定性评分账） ----------

def token_count(text: str) -> int:
    """评分账口径 v0：UTF-16 code unit 数（与 Kotlin 长度一致，确定性）。"""
    return lb_assembly.kotlin_len(text)


# ---------- 候选加载 ----------

def resolve_candidate_dir(candidate: str) -> str:
    """2026-10-18 修复：裸候选名静默失败防御。传 'round_0_baseline' 时
    assets_root='round_0_baseline/assets' 不存在，read_asset 吞 OSError 返回空串，
    导致 system prompt 整体为空（模型散文作答、契约 veto）——此类失败必须 fail-fast。"""
    if os.path.isdir(candidate):
        return candidate
    alt = os.path.join("candidates", candidate)
    if os.path.isdir(alt):
        return alt
    raise RuntimeError("候选目录不存在: %s（也尝试了 %s）" % (candidate, alt))


def load_mechanism(candidate_dir: str):
    """返回 (module, origin_str, version)。version: "v2"（有 init+run_turn）或 "v1"。
    v2 可选实现 snapshot/restore（无则 checkpoint 降级为 JSON 尝试）。
    v1 必须有 init_state/build_prompts/update_state（原契约，见 lb_mechanism.py 头注释）。"""
    mech_path = os.path.join(candidate_dir, "mechanism", "mechanism.py")
    if os.path.isfile(mech_path):
        cand = os.path.basename(candidate_dir.rstrip("/\\"))
        mod_name = "lb_candidate_mechanism_%s_%d" % (cand, id(candidate_dir))
        spec = importlib.util.spec_from_file_location(mod_name, mech_path)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        if callable(getattr(mod, "run_turn", None)) and callable(getattr(mod, "init", None)):
            return mod, "candidate:" + os.path.relpath(mech_path, candidate_dir), "v2"
        for fn in ("init_state", "build_prompts", "update_state"):
            if not callable(getattr(mod, fn, None)):
                raise RuntimeError("候选 mechanism 缺少接口函数 %s（v1/v2 契约见 arena/lb_mechanism.py）" % fn)
        return mod, "candidate:" + os.path.relpath(mech_path, candidate_dir), "v1"
    return lb_mechanism, "default:arena/lb_mechanism.py", "v1"


def load_contract(candidate_dir: str):
    """读候选输出契约。第五波判决 §2：baseline params 的顶层 may_decline 此前只写未读
    （load_contract 只认 output_contract），t12 通过主要靠轮级标记——现接线：
    无 output_contract 但声明顶层 may_decline=true → 视为 {"may_decline": true} 契约。"""
    pj = os.path.join(candidate_dir, "params.json")
    if os.path.isfile(pj):
        with open(pj, "r", encoding="utf-8") as f:
            p = json.load(f)
        c = p.get("output_contract")
        if c is None and p.get("may_decline"):
            return {"may_decline": True}
        return c
    return None


# ---------- 边界与工具 ----------

def _validate_message(m, where):
    """第六波判决 §5 / 第七波判决 §5：消息字段类型校验。对象出现在标量位 = 拒绝
    （fail-fast），正常中文自由文本不受影响（只查类型不查内容）。"""
    role = m.get("role")
    if role not in ("HER", "ME"):
        raise ValueError("%s: message.role 必须是 HER/ME，得到 %r" % (where, role))
    for k in MESSAGE_VISIBLE_KEYS:
        v = m.get(k)
        if v is None:
            continue
        if k in ("role", "text", "ts", "image") and not isinstance(v, str):
            raise ValueError("%s: message.%s 应为字符串，得到 %s"
                             % (where, k, type(v).__name__))


def _validate_intent(it, where):
    if not isinstance(it, dict):
        raise ValueError("%s: intent 应为对象" % where)
    if it.get("enabled") is not None and not isinstance(it["enabled"], bool):
        raise ValueError("%s: intent.enabled 应为布尔" % where)
    for k in ("text", "status", "expiry", "now"):
        if it.get(k) is not None and not isinstance(it[k], str):
            raise ValueError("%s: intent.%s 应为字符串，得到 %s"
                             % (where, k, type(it[k]).__name__))


def _validate_input_types(inp: dict, where: str):
    """第七波判决 §5：输入入口完整 schema（此前只验 dict 内部，容器本身错型被放行）。
    版本协议：intent 允许字符串（legacy，题库直书）或对象（装配层）；其余容器错型拒绝。
    may_decline 必须布尔——字符串真值不得产生动作许可。"""
    msgs = inp.get("messages")
    if msgs is not None:
        if not isinstance(msgs, list):
            raise ValueError("%s: messages 应为列表，得到 %s" % (where, type(msgs).__name__))
        for j, m in enumerate(msgs):
            if not isinstance(m, dict):
                raise ValueError("%s: messages[%d] 应为对象，得到 %s"
                                 % (where, j, type(m).__name__))
            _validate_message(m, "%s.messages[%d]" % (where, j))
    intent = inp.get("intent")
    if intent is not None and not isinstance(intent, (str, dict)):
        raise ValueError("%s: intent 应为字符串（legacy）或对象，得到 %s"
                         % (where, type(intent).__name__))
    clock = inp.get("clock")
    if clock is not None and not isinstance(clock, str):
        raise ValueError("%s: clock 应为字符串，得到 %s" % (where, type(clock).__name__))
    md = inp.get("may_decline")
    if md is not None and not isinstance(md, bool):
        raise ValueError("%s: may_decline 应为布尔（字符串真值不产生动作许可），得到 %s"
                         % (where, type(md).__name__))
    note = inp.get("note")
    if note is not None and not isinstance(note, str):
        raise ValueError("%s: note 应为字符串，得到 %s" % (where, type(note).__name__))
    kb = inp.get("kb")
    if kb is not None and not isinstance(kb, dict):
        raise ValueError("%s: kb 应为对象，得到 %s" % (where, type(kb).__name__))
    if isinstance(intent, dict):
        _validate_intent(intent, "%s.intent" % where)


def sanitize_turn(turn: dict) -> dict:
    """消毒：候选可见的 turn_ctx 按白名单重组（TURN_VISIBLE_KEYS ⊂ 键，
    input 内层再按 INPUT_VISIBLE_KEYS 过滤）。作者 meta / gold / 探针标注 / 正确策略
    一律留在评估侧，不因"没在黑名单"而泄漏。
    第五波判决 §7：messages / intent / kb 嵌套结构按各自允许字段重建 + 深拷贝——
    剥掉藏在容器内部的作者元数据，且不再与评估侧共享可变对象。
    第六波判决 §5：允许字段的值也做类型校验（对象型标量反例拒绝）并深拷贝。
    第七波判决 §5：容器本身错型（messages 字符串/列表内字符串/intent 列表/
    clock 对象/may_decline 字符串）在入口一律拒绝；intent 字符串为版本协议
    明确保留的 legacy 形态（IO_CONTRACT_PILOT §七）。"""
    ctx = {k: copy.deepcopy(turn[k]) for k in TURN_VISIBLE_KEYS if k in turn}
    if isinstance(ctx.get("input"), dict):
        inp = ctx["input"]
        where = turn.get("case_id", "?")
        _validate_input_types(inp, where)
        new_inp = {k: inp[k] for k in INPUT_VISIBLE_KEYS if k in inp}
        if isinstance(new_inp.get("messages"), list):
            new_inp["messages"] = [
                {k: copy.deepcopy(m[k]) for k in MESSAGE_VISIBLE_KEYS if k in m}
                for m in new_inp["messages"]]
        if isinstance(new_inp.get("intent"), dict):
            it = new_inp["intent"]
            new_inp["intent"] = {k: copy.deepcopy(it[k])
                                 for k in INTENT_VISIBLE_KEYS if k in it}
        if isinstance(new_inp.get("kb"), dict):
            kb = new_inp["kb"]
            new_inp["kb"] = {k: copy.deepcopy(kb[k]) for k in KB_VISIBLE_KEYS if k in kb}
        ctx["input"] = new_inp
    return ctx


def validate_media_mapping(case_input: dict, case_id: str = ""):
    """第五波判决 §10：媒体双向映射校验（fail-fast）。
    ①消息 image 引用必须登记进 input.media.images；②登记项必须被消息引用；
    ③引用/登记的资产文件必须存在。单向登记（登记了但消息没带）= 图片静默丢失，禁止。"""
    msgs = case_input.get("messages") or []
    msg_imgs = [m["image"] for m in msgs
                if isinstance(m, dict) and m.get("image")]
    reg = list((case_input.get("media") or {}).get("images") or [])
    problems = []
    for r in reg:
        if r not in msg_imgs:
            problems.append("登记未引用:%s" % r)
    for r in msg_imgs:
        if r not in reg:
            problems.append("引用未登记:%s" % r)
    for r in set(reg) | set(msg_imgs):
        if not os.path.isfile(r):
            problems.append("资产缺失:%s" % r)
    if problems:
        raise ValueError("媒体映射校验失败 [%s]: %s" % (case_id, "; ".join(problems)))


def require_candidate_assets(candidate_dir: str):
    """第五波判决 §8：候选资产入口完整性（fail-fast）。空目录 / 缺 core / 缺 format
    此前返回 148 字符空框架继续跑（system prompt 残缺 = 假运行），现一律拒绝。"""
    assets = os.path.join(candidate_dir, "assets")
    if not os.path.isdir(assets):
        raise RuntimeError("候选资产目录不存在: %s" % assets)
    required = ("system_prompt/core.md", "system_prompt/format.md",
                "system_prompt/redline.md", "system_prompt/naturalness_check.md")
    missing = [r for r in required
               if not os.path.isfile(os.path.join(assets, *r.split("/")))
               or os.path.getsize(os.path.join(assets, *r.split("/"))) == 0]
    if missing:
        raise RuntimeError("候选资产不完整（%s）: 缺 %s"
                           % (candidate_dir, ", ".join(missing)))


# ---------- 完整性散列（第五波判决 §9：每次运行记输入/代码/资产版本） ----------

def _sha_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def integrity_manifest(episode_path: str, candidate_dir: str) -> dict:
    """运行完整性：episode 文件、候选资产树、核心代码文件的 sha256（可复核版本锚）。"""
    code_files = ("lb_runner.py", "lb_gateway.py", "lb_assembly.py",
                  "lb_scorer.py", "lb_mechanism.py")
    out = {"episode_sha256": _sha_file(episode_path), "code": {}, "candidate_tree": {}}
    arena = os.path.dirname(os.path.abspath(__file__))
    for fn in code_files:
        p = os.path.join(arena, fn)
        if os.path.isfile(p):
            out["code"][fn] = _sha_file(p)
    for root, _, files in os.walk(candidate_dir):
        for fn in sorted(files):
            p = os.path.join(root, fn)
            rel = os.path.relpath(p, candidate_dir).replace("\\", "/")
            out["candidate_tree"][rel] = _sha_file(p)
    return out


def _turn_directive(turn: dict) -> dict:
    d = {}
    if "mock_response" in turn:
        d["mock_response"] = turn["mock_response"]
    if isinstance(turn.get("echo"), dict):
        d.update(turn["echo"])
    return d


def canonical_id_map(messages) -> dict:
    """runner 独立计算的权威 id_map（HER/ME 顺序编号；不信任候选回报）。"""
    id_map = {}
    n = 0
    for m in messages or []:
        if isinstance(m, dict) and m.get("role") in ("HER", "ME"):
            id_map["m%d" % n] = m["role"]
            n += 1
    return id_map


def _turn_dir(out_dir: str, i: int, turn: dict) -> str:
    return os.path.join(out_dir, "turn_%02d_%s" % (i + 1, turn.get("case_id", "case")))


def _w(out_dir: str, name: str, content: str):
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, name), "w", encoding="utf-8", newline="") as f:
        f.write(content)


def _wb(out_dir: str, name: str, content: bytes):
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, name), "wb") as f:
        f.write(content)


def _sandbox_paths(d):
    out = []
    for root, _, files in os.walk(d):
        for fn in sorted(files):
            out.append(os.path.relpath(os.path.join(root, fn), d).replace("\\", "/"))
    return sorted(out)


def _snapshot_bytes(version: str, mech, handle) -> bytes:
    if version == "v2":
        if callable(getattr(mech, "snapshot", None)):
            snap = mech.snapshot(handle)
            return snap if isinstance(snap, bytes) else json.dumps(
                snap, ensure_ascii=False, default=str).encode("utf-8")
        return json.dumps(handle, ensure_ascii=False, default=str).encode("utf-8")
    return json.dumps(handle, ensure_ascii=False, default=str).encode("utf-8")


def _restore_handle(version: str, mech, sandbox: str, snap: bytes):
    """v2 restore(sandbox, snap) -> harness（文件型存储在 sandbox 重建自身文件）；
    v1/无 restore：JSON 反序列化。"""
    if version == "v2" and callable(getattr(mech, "restore", None)):
        return mech.restore(sandbox, snap)
    return json.loads(snap.decode("utf-8"))


# ---------- 单轮执行 ----------

def _exec_turn_v2(turn, ctx, candidate_dir, turn_dir, gateway, mech, handle, contract, prior_real_n=0) -> dict:
    # 第六波判决 §3（P0）：turn 此处 = 评估侧原题快照（候选从未接触的 deepcopy）。
    # 来源表/时钟/媒体引用/评分全部只读该快照——候选对 ctx.messages 的任何增删改
    # 都不能影响"真实来源"的定义（伪造 m3 反例必须稳定 veto）。
    t0 = datetime.datetime.now()
    out = mech.run_turn(handle, ctx, gateway)
    reply = out["reply"]
    latency_ms = int((datetime.datetime.now() - t0).total_seconds() * 1000)
    usage = gateway.turn_usage()
    id_map = canonical_id_map((turn.get("input") or {}).get("messages"))
    # W12-02 判决：机制渲染的历史标签 m_src[i] 是模型实际可见的引用面；合法引用集
    # 扩到 i < prior_real_n（runner 从评估侧 episode 真实计数，不信任候选回报）。
    for _i in range(prior_real_n):
        id_map.setdefault("m_src[%d]" % _i, "HIST")
    scoring = lb_scorer.run_case_scoring_safe(turn, reply, id_map, contract)

    _w(turn_dir, "reply_raw.txt", reply)
    _w(turn_dir, "scoring.json", json.dumps(scoring, ensure_ascii=False, indent=2))
    _w(turn_dir, "calls.json", json.dumps(gateway.calls, ensure_ascii=False, indent=2))
    notes = out.get("notes", "")
    if notes:
        _w(turn_dir, "mechanism_notes.txt", notes)
    prior_real_n += sum(
        1 for m in (turn.get("input") or {}).get("messages") or []
        if isinstance(m, dict) and m.get("role") in ("HER", "ME"))

    manifest = {
        "case_id": turn.get("case_id"), "turn_type": turn.get("turn_type", "reply"),
        "candidate": os.path.basename(candidate_dir.rstrip("/\\")),
        "adapter": gateway.adapter_name,
        "model": os.environ.get("LB_MODEL", "") or "mock",
        "lifecycle": "v2",
        "run_id": gateway.run_id, "runner_pid": os.getpid(),
        "audit_errors": gateway.event_log_errors,
        "clock_injected": (turn.get("input") or {}).get("clock"),
        "media_refs": _collect_images(turn.get("input") or {}),
        "tokens": usage, "gateway_calls": usage.get("n_calls", 0),
        "latency_ms": latency_ms,
        "score10": scoring["score10"], "veto": scoring["veto"],
        "mechanism_error": None,
        "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "prompt_chars": {"prompt_sum": usage.get("prompt"), "reply_sum": usage.get("completion")},
    }
    _w(turn_dir, "manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
    return manifest


def _collect_images(case_input: dict) -> list:
    """收集本轮消息携带的图片资产引用（判决令 §2：资产存在 ≠ 进入请求，须显式传网关）。"""
    return [m["image"] for m in (case_input.get("messages") or [])
            if isinstance(m, dict) and m.get("image")]


def _exec_turn_v1(turn, ctx, candidate_dir, turn_dir, gateway, mech, state, contract) -> dict:
    assets_root = os.path.join(candidate_dir, "assets")
    t0 = datetime.datetime.now()
    assembled = mech.build_prompts(assets_root, ctx["input"], state)
    images = _collect_images(ctx["input"])
    gen = gateway.generate(assembled["system"], assembled["user"],
                           images=images or None,
                           model=turn.get("model"), temperature=turn.get("temperature"),
                           timeout_s=turn.get("timeout_s", 120), tag="main")
    latency_ms = int((datetime.datetime.now() - t0).total_seconds() * 1000)
    usage = gateway.turn_usage()
    # 第六波判决 §3：v1 的 assembled.id_map 同样不作评分权威——来源表只从
    # 评估侧原题快照（turn = 候选未接触的 deepcopy）独立计算。
    id_map = canonical_id_map((turn.get("input") or {}).get("messages"))
    scoring = lb_scorer.run_case_scoring_safe(turn, gen["text"], id_map, contract)

    mech_error = None
    if state is not None:
        parsed = lb_scorer.extract_json_block(gen["text"])
        try:
            mech.update_state(state, ctx["input"], parsed, gen["text"])
        except Exception as e:
            mech_error = "%s: %s" % (type(e).__name__, e)

    _w(turn_dir, "prompt_system.txt", assembled["system"])
    _w(turn_dir, "prompt_user.txt", assembled["user"])
    _w(turn_dir, "reply_raw.txt", gen["text"])
    _w(turn_dir, "scoring.json", json.dumps(scoring, ensure_ascii=False, indent=2))
    _w(turn_dir, "calls.json", json.dumps(gateway.calls, ensure_ascii=False, indent=2))
    if state is not None:
        _w(turn_dir, "state_after.json",
           json.dumps(state, ensure_ascii=False, indent=2, default=str))

    manifest = {
        "case_id": turn.get("case_id"), "turn_type": turn.get("turn_type", "reply"),
        "candidate": os.path.basename(candidate_dir.rstrip("/\\")),
        "adapter": gateway.adapter_name,
        "model": turn.get("model") or os.environ.get("LB_MODEL", "mock"),
        "lifecycle": "v1",
        "run_id": gateway.run_id, "runner_pid": os.getpid(),
        "audit_errors": gateway.event_log_errors,
        "clock_injected": (turn.get("input") or {}).get("clock"),
        "scene_inferred": assembled.get("scene"),
        "media_refs": _collect_images(turn.get("input") or {}),
        "tokens": usage, "gateway_calls": usage.get("n_calls", 0),
        "latency_ms": latency_ms,
        "score10": scoring["score10"], "veto": scoring["veto"],
        "mechanism_error": mech_error,
        "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "prompt_chars": {"system": lb_assembly.kotlin_len(assembled["system"]),
                         "user": lb_assembly.kotlin_len(assembled["user"])},
    }
    _w(turn_dir, "manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
    if mech_error:
        raise RuntimeError("mechanism_error at case %s: %s" % (turn.get("case_id"), mech_error))
    return manifest


# ---------- 单题模式 ----------

def run_case(case_path: str, candidate_dir: str, out_dir: str, adapter_name: str) -> dict:
    with open(case_path, "r", encoding="utf-8") as f:
        case = json.load(f)
    require_candidate_assets(candidate_dir)          # 第五波判决 §8：资产完整性 fail-fast
    validate_media_mapping(case.get("input") or {}, case.get("case_id", ""))
    mech, origin, version = load_mechanism(candidate_dir)
    contract = load_contract(candidate_dir)
    run_id = "%s_case_%s" % (datetime.datetime.now().strftime("%Y%m%d_%H%M%S"),
                             os.getpid())
    gateway = ModelGateway(ADAPTERS[adapter_name](),
                           event_log_path=os.path.join(out_dir, "gateway_events.jsonl"))
    gateway.run_id = run_id
    gateway.set_run_context(run_id, case.get("case_id", ""))
    gateway.directive = _turn_directive(case)
    # 第六波判决 §3：评估侧原题快照（候选未接触）先于候选调用固定
    case_eval = copy.deepcopy(case)
    ctx = sanitize_turn(case)
    os.makedirs(out_dir, exist_ok=True)
    # 单题模式 ep_meta：initial_kb 是"语义上已发生且允许候选知道"的显式可见入口
    # （与 run_episode 的 EP_META_VISIBLE_KEYS 同源）。单题 case 无 episode 级 initial_kb，
    # 从 case input.kb 的冻结快照按 KB_VISIBLE_KEYS 白名单提取，修复单题 KB 注入缺口。
    case_meta = {"mode": "single"}
    kb_src = (case.get("input") or {}).get("kb")
    if isinstance(kb_src, dict):
        case_meta["initial_kb"] = {k: copy.deepcopy(kb_src[k])
                                   for k in KB_VISIBLE_KEYS if k in kb_src}
    m = None
    err = None
    try:
        if version == "v2":
            sandbox = os.path.join(out_dir, "_sandbox")
            os.makedirs(sandbox, exist_ok=True)
            handle = mech.init(os.path.join(candidate_dir, "assets"), case_meta, gateway, sandbox)
            ctx["assets_root"] = os.path.join(candidate_dir, "assets")
            ctx["turn_type"] = case.get("turn_type", "reply")
            ctx["turn_index"] = 1
            m = _exec_turn_v2(case_eval, ctx, candidate_dir, out_dir, gateway, mech, handle, contract)
        else:
            state = mech.init_state(os.path.join(candidate_dir, "assets"), case_meta)
            m = _exec_turn_v1(case_eval, ctx, candidate_dir, out_dir, gateway, mech, state, contract)
        m["mechanism"] = origin
        m["contract"] = contract or "legacy"
    except Exception as e:
        # 第六波判决 §6.3：单题路径与 episode 共用异常持久化（此前只有 gateway_events）
        err = "%s: %s" % (type(e).__name__, e)
        _w(out_dir, "error.json", json.dumps({
            "case_id": case.get("case_id"), "error": err,
            "adapter": gateway.adapter_name, "run_id": run_id,
            "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")},
            ensure_ascii=False, indent=2))
        raise
    finally:
        _w(out_dir, "calls.json",
           json.dumps(gateway.calls, ensure_ascii=False, indent=2))
    _w(out_dir, "manifest.json", json.dumps(m, ensure_ascii=False, indent=2))
    return m


# ---------- episode 连续回放（v2：消毒 + checkpoint + resume） ----------

def run_episode(episode_path: str, candidate_dir: str, out_dir: str, adapter_name: str,
                stop_after=None, resume=False, intent_override=None,
                memory_off=False, run_id=None) -> dict:
    with open(episode_path, "r", encoding="utf-8") as f:
        ep = json.load(f)
    require_candidate_assets(candidate_dir)          # 第五波判决 §8：资产完整性 fail-fast
    mech, origin, version = load_mechanism(candidate_dir)
    contract = load_contract(candidate_dir)
    # 第七波判决 §4：--memory-off 面对未实现 set_memory_access 的候选明确拒绝，
    # 不允许"默默仍开记忆却在汇总写渠道已关"。
    if memory_off and not callable(getattr(mech, "set_memory_access", None)):
        raise RuntimeError("候选 %s 未实现 set_memory_access，--memory-off 不可用"
                           "（拒绝静默降级为全记忆运行）"
                           % os.path.basename(candidate_dir.rstrip("/\\")))
    # 第五波判决 §9：每次调用尝试即刻追加事件日志（成功/失败都落盘，先于 runner 成功路径）
    # 第七波判决 §4：支持外部 run_id 绑定（fork 编排器把分支 run 绑到本次实验 run_id，
    # 使 manifest.run_id 可证明"后测节点属于本次运行"）。
    run_id = run_id or "%s_ep_%s" % (datetime.datetime.now().strftime("%Y%m%d_%H%M%S"),
                                     os.getpid())
    gateway = ModelGateway(ADAPTERS[adapter_name](),
                           event_log_path=os.path.join(out_dir, "gateway_events.jsonl"))
    gateway.run_id = run_id
    os.makedirs(out_dir, exist_ok=True)
    sandbox = os.path.join(out_dir, "_sandbox")
    os.makedirs(sandbox, exist_ok=True)
    assets_root = os.path.join(candidate_dir, "assets")

    # 判决令 §2.1：ep_meta 白名单——gaps（未来时间+隐藏原因）、布景 note 等留在评估侧；
    # persona / initial_kb 是"语义上已发生且允许候选知道"的显式可见入口。
    ep_meta = {k: v for k, v in ep.items() if k in EP_META_VISIBLE_KEYS}
    ck_path = os.path.join(out_dir, "checkpoint.json")
    ck_bin = os.path.join(out_dir, "checkpoint.bin")

    start_idx = 0
    if resume and os.path.isfile(ck_path):
        with open(ck_path, "r", encoding="utf-8") as f:
            ck = json.load(f)
        start_idx = int(ck["turns_done"])
        with open(ck_bin, "rb") as f:
            snap = f.read()
        handle = _restore_handle(version, mech, sandbox, snap)   # v2 文件型存储在此重建
        # 第六波判决 §6.2：恢复完成事件 + 恢复后即刻 sandbox 清单（此前只在后测结束后记）
        _w(out_dir, "sandbox_tree_at_restore.json", json.dumps(
            {p: None for p in sorted(_sandbox_paths(sandbox))},
            ensure_ascii=False, indent=2))
        gateway._log_event({"kind": "restore_completed", "run_id": run_id,
                            "pid": os.getpid(), "resumed_from": start_idx,
                            "checkpoint_sha256": hashlib.sha256(snap).hexdigest(),
                            "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")})
    elif version == "v2":
        handle = mech.init(assets_root, ep_meta, gateway, sandbox)
    else:
        handle = mech.init_state(assets_root, ep_meta)
    # L3B 冻结分叉（第五波判决 §11-4）：eval 侧关闭全部历史信息访问渠道——
    # 由机制实现 set_memory_access；只关读取通道，persona 等共同画像保留。
    if memory_off and callable(getattr(mech, "set_memory_access", None)):
        mech.set_memory_access(handle, False)

    turns_manifest = []
    # Phase 0.5 意图对照：--intent-override 从 resume 点起把每轮 intent 覆盖为指定文本。
    # 配合 --stop-after/--resume = 冻结同一 Harness 状态分叉，仅改意图（严格对照）。
    if intent_override is not None:
        for i in range(start_idx, len(ep["turns"])):
            inp = ep["turns"][i].setdefault("input", {})
            inp["intent"] = {"enabled": True, "text": intent_override}
    for i in range(start_idx):   # 已完成轮：从落盘 manifest 重建汇总
        td = _turn_dir(out_dir, i, ep["turns"][i])
        with open(os.path.join(td, "manifest.json"), "r", encoding="utf-8") as f:
            turns_manifest.append(json.load(f))

    ran = 0
    prior_real_n = sum(
        1 for t in ep["turns"][:start_idx]
        for m in (t.get("input") or {}).get("messages") or []
        if isinstance(m, dict) and m.get("role") in ("HER", "ME"))
    for i in range(start_idx, len(ep["turns"])):
        if stop_after is not None and (i + 1) > stop_after:
            break
        turn = ep["turns"][i]
        # 第六波判决 §3（P0）：评估侧原题快照——候选从未接触的 deepcopy。
        # 评分来源表/时钟/媒体引用只读它，候选对 ctx 的任何修改都不影响评分权威。
        turn_eval = copy.deepcopy(turn)
        turn_dir = _turn_dir(out_dir, i, turn)
        gateway.calls = []
        gateway.directive = _turn_directive(turn)
        gateway.set_run_context(run_id, turn.get("case_id", ""))
        # 第五波判决 §10：媒体双向映射 fail-fast（登记↔引用不一致 = 图片静默丢失风险）
        validate_media_mapping(turn.get("input") or {}, turn.get("case_id", ""))
        ctx = sanitize_turn(turn)
        ctx["assets_root"] = assets_root
        ctx["sandbox"] = sandbox
        ctx["turn_type"] = turn.get("turn_type", "reply")
        ctx["turn_index"] = i + 1
        # 第五波判决 §9：异常路径也要留证——calls.json / error.json 先落盘再向上抛
        #（旧实现只在 generate 成功返回后写 calls.json，网关异常 = 失败账全丢）
        try:
            if version == "v2":
                m = _exec_turn_v2(turn_eval, ctx, candidate_dir, turn_dir, gateway, mech, handle, contract,
                                  prior_real_n=prior_real_n)
            else:
                m = _exec_turn_v1(turn_eval, ctx, candidate_dir, turn_dir, gateway, mech, handle, contract)
        except Exception as e:
            _w(turn_dir, "error.json", json.dumps({
                "case_id": turn.get("case_id"), "turn_index": i + 1,
                "error": "%s: %s" % (type(e).__name__, e),
                "adapter": gateway.adapter_name,
                "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")},
                ensure_ascii=False, indent=2))
            raise
        finally:
            _w(turn_dir, "calls.json",
               json.dumps(gateway.calls, ensure_ascii=False, indent=2))
        turns_manifest.append(m)
        ran += 1
        # checkpoint：每轮末落快照（v2 = 候选自管 bytes；v1 = state JSON）
        _wb(out_dir, "checkpoint.bin", _snapshot_bytes(version, mech, handle))
        _w(out_dir, "checkpoint.json", json.dumps({
            "turns_done": i + 1, "episode_id": ep.get("episode_id"),
            "candidate": os.path.basename(candidate_dir.rstrip("/\\")),
            "lifecycle": version, "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")},
            ensure_ascii=False, indent=2))

    # ---- episode 汇总（含 resume 拼接的全部轮） ----
    n = len(turns_manifest)
    scores = [m["score10"] for m in turns_manifest]
    vetoes = [m for m in turns_manifest if m["veto"]]
    tok_prompt = sum((m["tokens"].get("prompt") or 0) for m in turns_manifest)
    tok_completion = sum((m["tokens"].get("completion") or 0) for m in turns_manifest)
    lat = sorted(m["latency_ms"] for m in turns_manifest)
    # 真分位数（线性插值）；此前 int(n*0.95) 在 n<=20 时恒等于 max，属误标
    _pos = (len(lat) - 1) * 0.95 if lat else 0
    _lo, _hi = (int(_pos), min(int(_pos) + 1, len(lat) - 1)) if lat else (0, 0)
    p95 = int(round(lat[_lo] + (lat[_hi] - lat[_lo]) * (_pos - _lo))) if lat else 0

    stale_cfg = stale_hit = 0
    for i, turn in enumerate(ep["turns"][:len(turns_manifest)]):
        if (turn.get("gold") or {}).get("stale_fact_must_not"):
            stale_cfg += 1
            spath = os.path.join(_turn_dir(out_dir, i, turn), "scoring.json")
            with open(spath, "r", encoding="utf-8") as f:
                sc = json.load(f)
            if any((not c["pass"]) for c in sc["checks"] if c["id"].startswith("stale_kw_")):
                stale_hit += 1

    complete = n == len(ep["turns"])

    # ---- Phase 0.5 v2.1：探针加权汇总 + 探针窗口泄漏自检 ----
    # 天花板效应修复：220 轮均值会把探针淹没（无记忆候选 9.99 实证）。
    # probe turns（gold 含探针键）权重 W_PROBE=10，普通轮权重 1。
    PROBE_KEYS = ("must_keywords_any", "stale_fact_must_not", "intent_keywords_any",
                  "intent_must_not_keywords", "expect_decline", "must_not_keywords",
                  "expect_ongoing", "forbidden_new_facts", "geo_impossible_must_not")
    W_PROBE = 10
    wsum = 0.0
    gotsum = 0.0
    probe_report = []
    for i, turn in enumerate(ep["turns"][:len(turns_manifest)]):
        g = turn.get("gold") or {}
        is_probe = any(g.get(k) for k in PROBE_KEYS)
        sc = json.load(open(os.path.join(_turn_dir(out_dir, i, turn), "scoring.json"),
                            encoding="utf-8"))
        if is_probe:
            w = W_PROBE
            # 窗口泄漏自检：召回探针的关键词若全部出现在当轮 HER 消息里，
            # 该探针测不到记忆（候选看当轮窗口就能命中），标记 invalid。
            leak = False
            if g.get("must_keywords_any"):
                window_text = "".join(
                    m.get("text", "") for m in turn.get("input", {}).get("messages", [])
                    if isinstance(m, dict) and m.get("role") == "HER")
                leak = all(all(opt in window_text for opt in (grp if isinstance(grp, list) else [grp]))
                           for grp in g["must_keywords_any"])
            probe_report.append({
                "turn": i + 1, "case_id": turn.get("case_id"),
                "score10": sc["score10"], "veto": sc["veto"],
                "failed_checks": [c["id"] for c in sc["checks"] if not c["pass"]],
                "window_leak": leak})
        else:
            w = 1
        wsum += w
        gotsum += w * sc["score10"] / 10.0

    probe_weighted_score = round(gotsum / wsum * 10, 2) if wsum else 0.0

    ep_manifest = {
        "episode_id": ep.get("episode_id"),
        "candidate": os.path.basename(candidate_dir.rstrip("/\\")),
        "adapter": adapter_name, "mechanism": origin,
        "lifecycle": version, "contract": contract or "legacy",
        "run_id": run_id, "runner_pid": os.getpid(),
        "turns": n, "turns_total": len(ep["turns"]), "complete": complete,
        "resumed_from": start_idx if start_idx else None,
        "mean_score10": round(sum(scores) / n, 2) if n else 0.0,
        "probe_weighted_score10": probe_weighted_score,
        "probe_report": probe_report,
        "veto_turns": len(vetoes),
        "first_pass_rate": round(sum(1 for m in turns_manifest if not m["veto"]) / n, 4) if n else 0.0,
        "stale_pollution": {"probes": stale_cfg, "hits": stale_hit,
                            "rate": round(stale_hit / stale_cfg, 4) if stale_cfg else None},
        "tokens_total": {"prompt": tok_prompt, "completion": tok_completion},
        "latency_ms_p95": p95,
        "latency_ms_max": lat[-1] if lat else 0,
        "latency_ms_n": len(lat),
        # 第五波判决 §9：运行完整性锚（episode/代码/候选资产 sha256）
        "integrity": integrity_manifest(episode_path, candidate_dir),
        "ts": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    }
    _w(out_dir, "episode_manifest.json", json.dumps(ep_manifest, ensure_ascii=False, indent=2))
    return ep_manifest


def main():
    ap = argparse.ArgumentParser(description="LoveBrain arena runner v2")
    ap.add_argument("--case", help="单题模式：case JSON 路径")
    ap.add_argument("--episode", help="连续回放模式：episode JSON 路径")
    ap.add_argument("--candidate", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--adapter", default="mock", choices=list(ADAPTERS.keys()))
    ap.add_argument("--stop-after", type=int, default=None,
                    help="跑到第 N 轮后停下（保留 checkpoint），模拟中断")
    ap.add_argument("--resume", action="store_true", help="从最近 checkpoint 续跑")
    ap.add_argument("--intent-override", default=None,
                    help="从 resume 点起覆盖每轮持续意图为该自由文本（冻结状态意图对照用）")
    ap.add_argument("--memory-off", action="store_true",
                    help="L3B 冻结分叉：关闭候选全部历史信息访问渠道（机制须实现 set_memory_access）")
    ap.add_argument("--run-id", default=None,
                    help="外部 run_id 绑定（fork 编排器用：manifest/事件按此 id 归属本次实验）")
    args = ap.parse_args()
    if bool(args.case) == bool(args.episode):
        ap.error("--case 与 --episode 必须且只能给一个")

    cand_dir = resolve_candidate_dir(args.candidate)
    if args.case:
        m = run_case(args.case, cand_dir, args.out, args.adapter)
        print(json.dumps({"case_id": m["case_id"], "score10": m["score10"],
                          "veto": m["veto"], "tokens": m["tokens"],
                          "lifecycle": m["lifecycle"], "mechanism": m["mechanism"]},
                         ensure_ascii=False))
    else:
        m = run_episode(args.episode, cand_dir, args.out, args.adapter,
                        stop_after=args.stop_after, resume=args.resume,
                        intent_override=args.intent_override,
                        memory_off=args.memory_off, run_id=args.run_id)
        print(json.dumps({"episode_id": m["episode_id"], "turns": m["turns"],
                          "turns_total": m["turns_total"], "complete": m["complete"],
                          "mean_score10": m["mean_score10"],
                          "veto_turns": m["veto_turns"],
                          "stale_pollution": m["stale_pollution"],
                          "resumed_from": m["resumed_from"],
                          "lifecycle": m["lifecycle"], "mechanism": m["mechanism"]},
                         ensure_ascii=False))


if __name__ == "__main__":
    main()
