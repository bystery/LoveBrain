# tools/exam_interleave_plan.py — A 收口：把 48 槽交错计划接回**正式执行链**
#
# 旧版 rehearsal 直接调 mod.run_turn + 自定义 MockGateway，绕开了预算权威、真实网关、
# attempt 账本、可信侧评分与输入边界——reviewer 判它"只证明调度循环，未证明实跑路径"，
# 且两处守卫失灵：把 T02 改成重复 T01 仍报 order_ok；计划填错误冻结身份仍报 closure_ok。
#
# 本版**不另造模拟调度器**，复用现有 IsolatedRun（宿主+IPC+TrustedProxy+ModelGateway
# 账本+可信侧 lb_scorer+泄漏边界），mock/capture/live 共用同一 execute_plan 入口，
# 只替换适配器：
#   - capture（默认，零网络）：adapter=openai_compat + 进程内替身 urlopen + 测试授权/预算账本
#       → 走满"预算权威 + 网关 + attempt 账本 + 评分 + 出站白名单 + 题面泄漏检查"；
#   - mock：adapter=mock（离线替身，无预算面）→ 只验调度/守卫/账本，最省；
#   - live：需真实授权单，本工具不自动发（无授权拒绝）。
#
# 派发前硬守卫（任一不过 → 零派发即拒，不构造任何 IsolatedRun）：
#   1) validate_plan：48 槽 seq 唯一、每 (arm,episode) 覆盖 T01..T08 无重复无缺、
#      case_id 与 episode/turn 对应、每个 pair 两臂齐全、每 episode 先手顺序符合 EVIDENCE_ORDER；
#   2) verify_frozen_identity：当下重算两臂 harness_identity 闭包 + 本批实际用到的
#      题面/评分卡/评分器/执行器/工具/配置全文件散列，与计划声明值逐字节比对；
#      漂移（含"计划填了错误/全 0 冻结身份"）→ 拒。
#
# fail-fast：capture 用替身 urlopen 在第 N 次派发注入传输错 →
#   账本记 started N / completed N-1 / failed 1 并停批，**不补另一臂凑配对**、不重试。
#
# 隔离与最终请求证据：每 (arm,episode) 独立 run_id + 独立 out/control 目录，
# 落 sentinel 证明两臂/故事互不串；从每轮 calls.json 读**真实出站 prompt**测新增段，
# 不靠截短 notes。
#
# 用法：
#   python tools/exam_interleave_plan.py --emit-plan
#   python tools/exam_interleave_plan.py --execute-capture
#   python tools/exam_interleave_plan.py --execute-mock
#   python tools/exam_interleave_plan.py --fail-fast [--fault-at 5]
#   python tools/exam_interleave_plan.py --reject-duplicate
#   python tools/exam_interleave_plan.py --reject-bad-freeze
#   python tools/exam_interleave_plan.py --all
import argparse
import datetime as _dt
import hashlib
import importlib.util
import json
import os
import re
import shutil
import sys
import tempfile
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "arena"))

# ── 批配置概念 ──────────────────────────────────────────────────────────────
# reviewer §3：arm 标签统一 parent/candidate，候选名/故事顺序/卡路径来自本批配置；
# 计划生成、冻结、校验、执行、报告、工作表【消费同一配置】，不各自藏一份全局旧常量。
# 默认配置 = 旧 v2 回忆批次（E001–E003 / candidate=v2_recall），保证已签收检查不回退。
# 首选 v3 资产删减批次由 --batch-config 提供（E004–E006 / candidate=v3_system_slim），
# 其【配置随计划文档一起冻结】：execute_plan 从文档 batch/arm_mapping/episode_order
# 重建 cfg，绝不在调用点偷偷换全局变量。
EVIDENCE_ORDER = [("E001", "parent_first"), ("E002", "candidate_first"),
                  ("E003", "parent_first")]
ARM_MAP = {"parent": "reference_product_v1", "candidate": "v2_recall"}
ARM_ORDER = {"parent_first": ("parent", "candidate"),
             "candidate_first": ("candidate", "parent")}
PINNED_ENDPOINT = "http://127.0.0.1:17770/v1"
PINNED_MODEL = "dsf"
PINNED_TEMPERATURE = 0.7
TURNS_PER_EPISODE = 8   # legacy：仅供旧引用；真实位数一律 turns_of_episode() 按题面推导

# 跨批复用、必须一并冻结的 harness/评分/执行器/工具文件（与题目无关的稳定依赖）
SHARED_FROZEN_FILES = [
    "benchmark/rubric.md",
    "benchmark/annotations/exam1_20261023_semantic.json",
    "arena/lb_scorer.py", "arena/protocol_public.py", "arena/lb_gateway.py",
    "arena/lb_runner.py", "arena/lb_assembly.py", "arena/api_candidate.py",
    "arena/isolation/trusted_controller.py", "arena/isolation/host_runtime.py",
    "arena/isolation/_candidate_host.py", "arena/isolation/authorization_budget.py",
    "tools/api_trial.py", "tools/exam_layered_score.py",
    "tools/build_semantic_annotations.py", "tools/freeze_identity.py",
    "tools/exam_interleave_plan.py", "tools/rescore_offline.py",
]


class PlanError(Exception):
    pass


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def _load_api_trial():
    os.environ.setdefault("DSF_API_KEY", "")
    return load(os.path.join(ROOT, "tools", "api_trial.py"), "api_trial_ice")


def _sha_file(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def load_episode(name):
    p = os.path.join(ROOT, "benchmark", "episodes", name + ".json")
    return json.load(open(p, encoding="utf-8"))


def _episodes_of(order):
    return ["benchmark/episodes/%s.json" % e for e, _o in order]


def turns_of_episode(ep_name):
    """该故事的真实请求位 = 题面 turns 数（不写死 8）。

    2026-10-28：Stage2 是 12 位/故事、反馈锚点是 1 位/锚点，8 的硬编码把两者都挡在
    正式执行链外。轮次一律从【已冻结题面本身】推导，配置只能显式核对、不能凭空设定。
    题面缺失/turns 非列表/空 → PlanError（零派发）。
    """
    ep = load_episode(ep_name)
    turns = ep.get("turns")
    if not isinstance(turns, list) or not turns:
        raise PlanError("题面 %s 无有效 turns 列表，无法确定请求位数" % ep_name)
    return len(turns)


def episode_turn_map(cfg):
    """{episode: 请求位数}，按本批配置的故事去重推导。"""
    out = {}
    for ep, _o in cfg["episode_order"]:
        if ep not in out:
            out[ep] = turns_of_episode(ep)
    return out


def default_cfg():
    """默认批配置 = 旧 v2 回忆批次。行为与历史全局常量逐字一致（E001–E003 皆 8 位）。"""
    cards = "benchmark/dev_pilot_v02/03_GRADING_CARDS.json"
    return {
        "batch_id": "V2-RECALL-E001-3",
        "arm_map": dict(ARM_MAP),
        "episode_order": list(EVIDENCE_ORDER),
        "cards_path": cards,
        # 候选是否含 v2 式【回忆】新增段：决定 recall/injection 指标是否【适用】
        "recall_capable": True,
        # None = 由题面推导；给了就必须与题面逐字一致（防止配置与题面悄悄脱钩）
        "turns_per_episode": None,
        "frozen_extra_files": _episodes_of(EVIDENCE_ORDER) + [cards] + SHARED_FROZEN_FILES,
    }


# 兼容旧引用（若有脚本读 ep.FROZEN_EXTRA_FILES / ep.EVIDENCE_ORDER）
FROZEN_EXTRA_FILES = default_cfg()["frozen_extra_files"]


def _norm_cfg(raw):
    """把外部 batch-config JSON（或内置默认）归一为执行链内部 cfg。"""
    cfg = default_cfg()
    if raw is None:
        return cfg
    if isinstance(raw, str):
        raw = json.load(open(raw, encoding="utf-8"))
    if not isinstance(raw, dict):
        raise PlanError("batch-config 必须是对象或 JSON 路径")
    if "batch_id" in raw:
        cfg["batch_id"] = raw["batch_id"]
    am = raw.get("arm_map") or raw.get("arm_mapping")
    if am:
        if "parent" not in am or "candidate" not in am:
            raise PlanError("batch-config arm_map 必须含 parent 与 candidate 两臂")
        cfg["arm_map"] = {"parent": am["parent"], "candidate": am["candidate"]}
    eo = raw.get("episode_order")
    if eo:
        norm = []
        for item in eo:
            if isinstance(item, dict):
                norm.append((item["episode"], item.get("arm_order", "parent_first")))
            else:
                norm.append((item[0], item[1]))
        cfg["episode_order"] = norm
    if raw.get("cards_path"):
        cfg["cards_path"] = raw["cards_path"]
    if "recall_capable" in raw:
        cfg["recall_capable"] = bool(raw["recall_capable"])
    if raw.get("turns_per_episode") is not None:
        cfg["turns_per_episode"] = int(raw["turns_per_episode"])
    # 请求位数一律由题面推导；配置若显式声明必须逐字一致，否则零派发拒绝
    tmap = episode_turn_map(cfg)
    if cfg.get("turns_per_episode") is not None:
        bad = {e: n for e, n in tmap.items() if n != cfg["turns_per_episode"]}
        if bad:
            raise PlanError("配置 turns_per_episode=%s 与题面不符：%s"
                            % (cfg["turns_per_episode"], bad))
    cfg["episode_turns"] = tmap
    # 题面 + 卡 + 共享依赖 → 一并冻结（谁也不能只冻候选身份而漏掉题目/工具版本）
    cfg["frozen_extra_files"] = (_episodes_of(cfg["episode_order"])
                                 + [cfg["cards_path"]] + SHARED_FROZEN_FILES)
    return cfg


def load_batch_config(path):
    return _norm_cfg(path)


def cfg_from_plan(plan):
    """execute_plan/报告从【计划文档本身】重建 cfg——单一配置源，不从全局读旧常量。"""
    bm = plan.get("batch") or {}
    am = plan.get("arm_mapping") or dict(ARM_MAP)
    eo = plan.get("episode_order") or []
    ep_order = [(e["episode"], e.get("arm_order", "parent_first")) for e in eo] \
        if eo else list(EVIDENCE_ORDER)
    raw = {
        "batch_id": bm.get("batch_id"),
        "arm_map": am,
        "episode_order": ep_order,
        "cards_path": bm.get("cards_path"),
        "recall_capable": bm.get("recall_capable"),
    }
    if bm.get("turns_per_episode") is not None:
        raw["turns_per_episode"] = bm["turns_per_episode"]
    raw = {k: v for k, v in raw.items() if v is not None}
    return _norm_cfg(raw or None)


# ───────────────────────── 计划铺排与校验 ─────────────────────────

def _tmap(cfg):
    """取每故事请求位数；兼容外部手工构造、未经 _norm_cfg 的 cfg。"""
    if not cfg.get("episode_turns"):
        cfg["episode_turns"] = episode_turn_map(cfg)
    return cfg["episode_turns"]


def build_plan(cfg=None):
    cfg = cfg or default_cfg()
    tm = _tmap(cfg)
    plan, seq = [], 0
    for ep, order in cfg["episode_order"]:
        for turn_i in range(1, tm[ep] + 1):
            arms = ARM_ORDER[order]
            for arm in arms:
                seq += 1
                plan.append({
                    "seq": seq, "episode": ep, "turn": turn_i,
                    "case_id": "%s_T%02d" % (ep, turn_i), "arm": arm,
                    "pair_key": "%s_T%02d" % (ep, turn_i),
                    "within_pair_pos": 1 if arm == arms[0] else 2,
                    "arm_order_in_ep": order,
                })
    return plan


def validate_plan(plan, cfg=None):
    """派发前守卫：任何不合法计划 → PlanError（调用方据此零派发拒绝）。"""
    cfg = cfg or default_cfg()
    order = cfg["episode_order"]
    tm = _tmap(cfg)
    expect_total = sum(tm[e] for e, _o in order) * 2
    errs = []
    n = len(plan)
    if n != expect_total:
        errs.append("attempts=%d 应为 %d（各故事位数=%s）"
                    % (n, expect_total, tm))
    seqs = [r["seq"] for r in plan]
    if sorted(seqs) != list(range(1, n + 1)):
        errs.append("seq 非 1..N 唯一：%s" % sorted(set(x for x in seqs if seqs.count(x) > 1)))
    # 每 (arm,episode) 必须覆盖该题面全部位点且无重复
    seen = {}
    for r in plan:
        key = (r["arm"], r["episode"])
        seen.setdefault(key, []).append(r["turn"])
    for key, turns in seen.items():
        want = list(range(1, tm[key[1]] + 1))
        if sorted(turns) != want:
            dup = sorted(x for x in set(turns) if turns.count(x) > 1)
            miss = sorted(set(want) - set(turns))
            errs.append("臂/故事 %s 轮次不连续：重复=%s 缺=%s" % (key, dup, miss))
    # case_id ↔ (episode, turn) 对应（据真实题面）
    for r in plan:
        ep = load_episode(r["episode"])
        if r["turn"] - 1 >= len(ep["turns"]) or \
                ep["turns"][r["turn"] - 1].get("case_id") != r["case_id"]:
            errs.append("case_id 与题面不符：seq=%s %s %s_T%02d"
                        % (r["seq"], r["case_id"], r["episode"], r["turn"]))
    # 每个 pair 两臂齐全
    pairs = {}
    for r in plan:
        pairs.setdefault(r["pair_key"], set()).add(r["arm"])
    for pk, arms in pairs.items():
        if arms != {"parent", "candidate"}:
            errs.append("配对缺臂：%s=%s" % (pk, sorted(arms)))
    # 每 episode 先手顺序符合本批配置（首现臂正确）
    for ep, order_kind in order:
        first_arm = next((r["arm"] for r in plan
                          if r["episode"] == ep and r["turn"] == 1), None)
        if first_arm != ARM_ORDER[order_kind][0]:
            errs.append("episode %s 先手臂应为 %s，实为 %s"
                        % (ep, ARM_ORDER[order_kind][0], first_arm))
    if errs:
        raise PlanError("; ".join(errs))


def collect_frozen_files(cfg=None):
    """当下重算：两臂 harness 闭包 + 本批配置/题面/评分/执行器全文件散列。

    2026-10-26 补：文件不存在时除留 null 外，另单列 missing_files——
    过去 verify 只比 declared vs now 两个值，null == null 会静默通过，
    于是"评分卡路径写错=根本没冻结"也能派真金白银的批（C4 派发前核查抓出）。
    """
    cfg = cfg or default_cfg()
    at = _load_api_trial()
    ids = {arm: at.harness_identity(cand) for arm, cand in cfg["arm_map"].items()}
    extra = {}
    for rel in cfg["frozen_extra_files"]:
        p = os.path.join(ROOT, rel.replace("/", os.sep))
        extra[rel] = _sha_file(p) if os.path.isfile(p) else None
    return {"identity_closure": ids, "config_files": extra,
            "missing_files": sorted(rel for rel, sha in extra.items() if sha is None)}


def verify_frozen_identity(plan, cfg=None):
    """计划声明的冻结身份/配置散列 vs 当下重算，逐字节比对。漂移 → PlanError。
    reviewer A 反例（计划填全 0 候选身份仍报通过）在这里被拦下。"""
    cfg = cfg or cfg_from_plan(plan)
    now = collect_frozen_files(cfg)
    declared = plan.get("frozen", {})
    errs = []
    for arm in ("parent", "candidate"):
        d = declared.get("identity_closure", {}).get(arm, {})
        cur = now["identity_closure"][arm]
        if d.get("identity_sha256") != cur["identity_sha256"]:
            errs.append("%s 身份漂移：声明 %s… 实为 %s…" % (
                arm, str(d.get("identity_sha256"))[:16], cur["identity_sha256"][:16]))
        dfiles = d.get("files", {})
        for rel, sha in cur["files"].items():
            if rel in dfiles and dfiles[rel] != sha:
                errs.append("%s 闭包文件漂移：%s" % (arm, rel))
    dcfg = declared.get("config_files", {})
    if now.get("missing_files"):
        errs.append("冻结缺失（参与生产的文件不存在，配置不可核）：%s"
                    % ", ".join(now["missing_files"]))
    for rel, sha in now["config_files"].items():
        if rel not in dcfg:
            errs.append("计划未冻结配置：%s" % rel)
        elif dcfg[rel] != sha:
            errs.append("配置漂移：%s" % rel)
    if errs:
        raise PlanError("; ".join(errs))
    return now


# ───────────────────────── emit plan ─────────────────────────

def emit_plan(out_path, cfg=None):
    cfg = cfg or default_cfg()
    plan = build_plan(cfg)
    validate_plan(plan, cfg)
    frozen = collect_frozen_files(cfg)
    if frozen.get("missing_files"):
        raise PlanError("出计划失败：以下参与生产的文件缺失，绝不写 null 哈希冒充已冻结：%s"
                        % ", ".join(frozen["missing_files"]))
    doc = {
        "generated_at": _dt.datetime.now().isoformat(timespec="seconds"),
        "protocol": "refs/experiment_protocol_20261023/EXPERIMENT_PLAN_20261023.md rev R5.2 §4",
        "arm_mapping": cfg["arm_map"],
        "episode_order": [{"episode": e, "arm_order": o}
                          for e, o in cfg["episode_order"]],
        # 单一配置源：批 id / 卡路径 / 是否含回忆新增段（决定指标适用性）随文档冻结
        "batch": {
            "batch_id": cfg["batch_id"],
            "cards_path": cfg["cards_path"],
            "recall_capable": bool(cfg["recall_capable"]),
            "episode_turns": dict(_tmap(cfg)),
            "turns_per_episode": (list(dict(_tmap(cfg)).values())[0]
                                  if len(set(_tmap(cfg).values())) == 1 else None),
            "positions_total": len(plan),
        },
        "total_attempts": len(plan),
        "no_auto_retry": True, "no_makeup_paired_run": True,
        "fail_fast_on_transmission_error": True,
        "execution_entry": "arena.isolation.trusted_controller.IsolatedRun (mock/capture/live 共用)",
        "frozen": {
            "identity_closure": {
                arm: {"identity_sha256": v["identity_sha256"], "closure": v["closure"],
                      "file_count": len(v["files"]), "files": v["files"]}
                for arm, v in frozen["identity_closure"].items()},
            "config_files": frozen["config_files"],
        },
        "attempts": plan,
    }
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    return doc


# ───────────────── capture 替身（零网络） ─────────────────

_CANNED = json.dumps({
    "response": {"recommended": "（离线替身，不代表质量）", "warm": "（替身）",
                 "playful": "（替身）", "bad_boy": "（替身）"},
    "directions": ["（替身）", None, None, None],
    "analysis": {"topic_status": "same", "topic_label": "rehearsal",
                 "scene_facts": [], "ongoing": []}}, ensure_ascii=False)


class _TransportFault(Exception):
    pass


def make_fake_urlopen(captured, fault_at=None):
    """capture 替身：拦截出站请求体（零网络）；fault_at 次派发抛传输错。
    出站请求体由 api_candidate.build_request_body 构造 → 天然过白名单/BoundaryViolation。"""
    state = {"calls": 0}

    def fake(req, timeout=None):
        body = json.loads(req.data.decode("utf-8"))
        state["calls"] += 1
        captured.append(body)
        if fault_at is not None and state["calls"] == fault_at:
            raise _TransportFault("simulated 5xx at dispatch %d" % state["calls"])

        class _R(object):
            def read(self):
                return json.dumps({"id": "reh-%d" % state["calls"],
                                   "model": PINNED_MODEL,
                                   "choices": [{"message": {"content": _CANNED},
                                                "finish_reason": "stop"}],
                                   "usage": {"prompt_tokens": 1,
                                             "completion_tokens": 1}}).encode("utf-8")

            def __enter__(self):
                return self

            def __exit__(self, *a):
                return False
        return _R()
    fake.state = state
    return fake


# ───────────────── 统一 execute_plan（接回 IsolatedRun） ─────────────────

def _rel_root(p):
    try:
        return os.path.relpath(p, ROOT)
    except ValueError:
        return p        # 跨盘（tmp 在 C:，ROOT 在 D:）→ 用绝对路径


def _ordered_runs(plan):
    """把 48 槽按首次出现铺成 (arm, episode) 运行序；每个 (arm,episode) 一次 run_episode
    （同臂同故事轮次状态天然顺序依赖；两臂状态独立，交错只作调度、绝不互看输出）。"""
    order, seen = [], set()
    for r in plan:
        key = (r["arm"], r["episode"])
        if key not in seen:
            seen.add(key)
            order.append(key)
    return order


def _new_test_authorization(out_base, attempts):
    doc = {"authorization_id": "AUTH-REHEARSAL-%s"
           % _dt.datetime.now().strftime("%Y%m%d%H%M%S"),
           "authorized": True, "status": "ACTIVE",
           "scope": "零网络交错演练（urlopen 被本进程替身接管，无真实调用）",
           "note": "演练授权：不产生真实 API 调用；账本为演练占额证据",
           "real_endpoint": {"api_base": PINNED_ENDPOINT, "model": PINNED_MODEL,
                             "temperature": PINNED_TEMPERATURE,
                             "max_attempts": attempts}}
    p = os.path.join(out_base, "_auth", "AUTH_REHEARSAL.json")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(doc, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    return doc, p


def _tally_ledgers(ctl_dirs):
    kinds = {"attempt_started": 0, "attempt_completed": 0, "attempt_failed": 0}
    detail = []
    for cd in ctl_dirs:
        led = os.path.join(cd, "attempt_ledger.jsonl")
        if not os.path.isfile(led):
            continue
        for line in open(led, encoding="utf-8"):
            line = line.strip()
            if not line:
                continue
            try:
                ev = json.loads(line)
            except json.JSONDecodeError:
                continue
            k = ev.get("kind")
            if k in kinds:
                kinds[k] += 1
            detail.append({"ctl": os.path.basename(cd), "kind": k,
                           "attempt_id": ev.get("attempt_id"),
                           "adapter": ev.get("adapter")})
    return kinds, detail


def _recall_newseg_from_calls(out_dir):
    """从每轮 calls.json 读**真实出站 user prompt**，测 v2 新增段（【回忆】段）UTF-16 码元；
    返回每轮 {case_id, recall_seg_utf16, has_recall}，用于'看最终请求贡献'。"""
    import exam_layered_score  # noqa  仅为同目录导入可用
    spec = load(os.path.join(ROOT, "candidates", "v2_recall", "mechanism",
                             "mechanism.py"), "mech_newseg")
    stops = ("【当前场景】", "【持续意图】", "# 本次对话记录", "【当前时间】",
             "# 【懂得】", "# 【记忆】", "# 【此刻】")
    rows = []
    for td in sorted(d for d in os.listdir(out_dir) if d.startswith("turn_")):
        cf = os.path.join(out_dir, td, "calls.json")
        if not os.path.isfile(cf):
            continue
        calls = json.load(open(cf, encoding="utf-8"))
        main = next((c for c in calls if c.get("tag", "main") == "main"), None)
        if not main:
            continue
        user = main.get("user") or ""
        i = user.find("【回忆】")
        if i < 0:
            rows.append({"turn": td, "has_recall": False, "recall_seg_utf16": 0})
            continue
        ends = [user.find(m, i + 3) for m in stops]
        ends = [e for e in ends if e > i]
        j = min(ends) if ends else len(user)
        rows.append({"turn": td, "has_recall": True,
                     "recall_seg_utf16": _spec_rc(spec).utf16_len(user[i:j].strip())})
    return rows


def _spec_rc(spec):
    """取候选模块的 rc 工具（utf16_len 等纯函数）。

    为什么需要：忠实原版 reference_product_v1 直接导出 rc；转发型候选（action_policy_r2/r3）
    只导出 base，rc 挂在 base.rc 上。两者指向同一份 arena.response_contract 模块，
    utf16_len 是纯函数，取哪个都不改变数值——这里只做句柄解析，不改任何计量口径。
    （R3A 批父臂=r2，首次触发；此前所有批次父臂都是 reference_product_v1，故未暴露。）
    """
    rc = getattr(spec, "rc", None)
    if rc is not None:
        return rc
    base = getattr(spec, "base", None)
    rc = getattr(base, "rc", None)
    if rc is not None:
        return rc
    raise RuntimeError("候选模块无 rc 句柄：%s" % getattr(spec, "__name__", spec))


def _combined_newctx_from_calls(parent_out, cand_out, spec):
    """真实渲染核验：候选相对父本**实际多出**的新上下文段（extras+recall 合计）。

    v2 与父本在同一 case 的出站 user prompt 只差：追加的【回忆】块 + 注入的
    extras 旁路行（其余完全走父本逻辑、逐字相同）。用同一 UTF-16 码元单位取两臂
    同 case 的差值 = 真实新增段，不另造预算单位。仅统计两臂都产出的轮次；
    任一侧缺 main call → 跳过该 case。"""
    def _main_user(out_dir):
        rows = {}
        if not os.path.isdir(out_dir):
            return rows
        for td in sorted(d for d in os.listdir(out_dir) if d.startswith("turn_")):
            cf = os.path.join(out_dir, td, "calls.json")
            if not os.path.isfile(cf):
                continue
            calls = json.load(open(cf, encoding="utf-8"))
            main = next((c for c in calls if c.get("tag", "main") == "main"), None)
            if not main:
                continue
            cid = td.split("_", 2)[-1]              # turn_XX_<case_id>
            rows[cid] = main.get("user") or ""
        return rows
    p_users = _main_user(parent_out)
    c_users = _main_user(cand_out)
    rc = _spec_rc(spec)
    rows = []
    for cid in sorted(set(p_users) & set(c_users)):
        pu, cu = p_users[cid], c_users[cid]
        added = rc.utf16_len(cu) - rc.utf16_len(pu)
        rows.append({"case_id": cid,
                     "cand_utf16": rc.utf16_len(cu),
                     "parent_utf16": rc.utf16_len(pu),
                     "added_utf16": max(0, added),
                     "has_recall": "【回忆】" in cu})
    max_added = max([r["added_utf16"] for r in rows], default=0)
    return rows, max_added


def _attempt_started_ids(control):
    """从该常驻宿主 control/attempt_ledger.jsonl 读真实 attempt_started 的
    attempt_id 序列（网关派发落盘的【事实】，非计划拷贝）。"""
    led = os.path.join(control, "attempt_ledger.jsonl")
    ids = []
    if not os.path.isfile(led):
        return ids
    for line in open(led, encoding="utf-8"):
        line = line.strip()
        if not line:
            continue
        try:
            ev = json.loads(line)
        except json.JSONDecodeError:
            continue
        if ev.get("kind") == "attempt_started":
            ids.append(ev.get("attempt_id"))
    return ids


def _injection_newseg_from_notes(out_dir, cap_default=None):
    """A4：注入预算核验用候选【同一状态、同一次装配】算出的 new_seg_est
    （extras+recall 联合新增段 UTF-16 码元），从每轮 mechanism_notes.txt 读；
    不拿另一臂随机状态反推。返回 (rows, max_seg, cap_seen)。父本/无该机制的
    候选不产 newctx note → rows 空、max=0（不适用，非越界）。"""
    rows, mx, cap_seen = [], 0, None
    if not os.path.isdir(out_dir):
        return rows, mx, cap_default
    for td in sorted(d for d in os.listdir(out_dir) if d.startswith("turn_")):
        nf = os.path.join(out_dir, td, "mechanism_notes.txt")
        if not os.path.isfile(nf):
            continue
        txt = open(nf, encoding="utf-8").read()
        m = re.search(r"new_seg_est=(\d+)", txt)
        if not m:
            continue
        c = re.search(r"cap=(\d+)", txt)
        seg = int(m.group(1))
        if c:
            cap_seen = int(c.group(1))
        rows.append({"case_id": td.split("_", 2)[-1], "new_seg_est_utf16": seg,
                     "cap": int(c.group(1)) if c else None})
        mx = max(mx, seg)
    return rows, mx, (cap_seen if cap_seen is not None else cap_default)


def execute_plan(plan, mode="capture", fault_at=None, out_base=None,
                 keep_tmp=False, do_guards=True, authorization=None,
                 transport=None, workers=None):
    """mock/capture/live/live_stub 共用【逐 slot 交错】入口（A 收口版）。

    派发前守卫（任一不过 → PlanError：零 IsolatedRun 构造、零派发）：
      validate_plan → verify_frozen_identity → 授权对账 →【整批预检 48】。
    通过后按冻结计划 seq 逐 slot 推进：6 常驻宿主、每 slot 只跑它那轮、
    expect_turn 前驱守卫派发前核对；执行序与 attempt 均【从真实网关事件核验】。

    A 收口四项：
      A1 openai_compat 证据默认保留（绝不删目录）；live/live_stub 拒绝覆盖已
         有 turn_* 实跑证据（重生成写新目录，或用 reports_from_batch 写副本）。
      A2 计数来自 control 账本真实 attempt_started：区分 real/stub/mock 派发，
         live 真分支绝不再报 0；逐 slot 附实际 attempt_id（非计划标签拷贝）。
      A3 首发前用同一 AuthorizationBudget 预检【完整批需 total_attempts】，
         不足/已关/账本损坏 → 零派发拒绝；逐 attempt 原子占额仍保留。
      A4 联合注入预算门禁改用候选自身 new_seg_est（mechanism notes），跨臂
         user 总长差只作诊断，不再当 1800 门禁。

    mode：
      mock       adapter=mock（离线替身，无预算面）——验交错/前驱/状态隔离/账本
      capture    openai_compat + 进程内替身 urlopen + 演练授权（零网络）
      live       openai_compat +【真实网络】，必须 authorization；transport 可选
                 注入假 HTTP 以零网络演练真实分支（reviewer 复现口径）
      live_stub  与 live 相同配置/预算/持久接线 + 替身 urlopen（零网络验收）
    """
    if mode not in ("mock", "capture", "live", "live_stub"):
        raise PlanError("未知 mode=%r" % mode)

    # 单一配置源：从计划文档本身重建 cfg（候选名/故事顺序/卡/是否含回忆段），
    # 绝不在调用点偷换全局。默认 v2 批次 cfg 与历史行为逐字一致。
    cfg = cfg_from_plan(plan)
    tm = _tmap(cfg)

    if do_guards:
        validate_plan(plan["attempts"], cfg)
        verify_frozen_identity(plan, cfg)

    from isolation.trusted_controller import IsolatedRun
    import api_candidate
    from isolation.authorization_budget import (
        validate_authorization_doc, BudgetRefused, AuthorizationBudget,
        LedgerCorrupt)

    adapter = "mock" if mode == "mock" else "openai_compat"
    network_fake = mode in ("capture", "live_stub")
    persist = adapter == "openai_compat"          # A1：付费/演练证据默认保留

    tmp = os.path.abspath(out_base or tempfile.mkdtemp(prefix="ep_%s_" % mode))
    # A1：live/live_stub 拒绝覆盖已有派发证据（不删不清，只拒）
    if mode in ("live", "live_stub") and os.path.isdir(tmp):
        for d in os.listdir(tmp):
            od = os.path.join(tmp, d, "out")
            if os.path.isdir(od) and any(
                    x.startswith("turn_") for x in os.listdir(od)
                    if os.path.isdir(os.path.join(od, x))):
                raise PlanError(
                    "拒绝覆盖已有实跑证据：%s/%s — 重生成请写新 out_base "
                    "或用 reports_from_batch 写报告副本（原始付费输出不动）"
                    % (tmp, d))
    os.makedirs(tmp, exist_ok=True)

    # ── 授权对账 + A3 整批预检（构造任何 IsolatedRun 之前） ──
    norm = None
    ledger_path = None
    if adapter == "openai_compat":
        if mode == "capture":
            auth_doc, _ap = _new_test_authorization(tmp, plan["total_attempts"])
        else:
            if authorization is None:
                raise PlanError("%s 无授权：零派发拒绝（绝不自动冒烟真实调用）"
                                % mode)
            auth_doc = authorization
        os.environ["LB_API_BASE"] = PINNED_ENDPOINT
        os.environ["LB_MODEL"] = PINNED_MODEL
        os.environ.setdefault("LB_API_KEY", "local")
        try:
            norm = validate_authorization_doc(
                auth_doc, expected_endpoint=PINNED_ENDPOINT,
                expected_model=PINNED_MODEL,
                expected_temperature=PINNED_TEMPERATURE)
        except BudgetRefused as e:
            raise PlanError("授权无效，零派发拒绝: %s" % str(e)[:200])
        ledger_path = (authorization or {}).get("budget_ledger") or os.path.join(
            ROOT, "logs", "auth_budget_rehearsal",
            "%s.jsonl" % norm["authorization_id"])
        # A3：首发前预检完整批需（不是每 resident 8）；不足/已关/损坏 → 零派发
        try:
            _pre = AuthorizationBudget(
                ledger_path, norm["authorization_id"], norm["max_attempts"],
                status=norm.get("status"), scope=(authorization or {}).get("scope"))
            _pre.ensure_batch(plan["total_attempts"], purpose="full_batch:%s" % mode)
        except (BudgetRefused, LedgerCorrupt) as e:
            raise PlanError(
                "整批预检拒绝（零派发，未构造任何 IsolatedRun）: %s" % str(e)[:200])

    captured = []
    real_urlopen = urllib.request.urlopen
    transport_live = mode == "live" and transport is not None
    try:
        if network_fake:
            urllib.request.urlopen = make_fake_urlopen(captured, fault_at=fault_at)
        elif transport_live:
            urllib.request.urlopen = transport      # 零网络演练 live 真实分支

        attempts = plan["attempts"]
        keys = []
        for r in attempts:
            k = (r["arm"], r["episode"])
            if k not in keys:
                keys.append(k)
        hosts = {}
        exec_events = []
        ctl_dirs, stopped = [], None
        finished = False
        try:
            # 1) 建常驻宿主并 begin_run（init+代理+授权级预算，仍零派发）
            for (arm, ep) in keys:
                cand = cfg["arm_map"][arm]
                base = os.path.join(tmp, "%s_%s" % (arm, ep))
                out = os.path.join(base, "out")
                run_dir = os.path.join(base, "_iso_run")
                control = os.path.join(out, "_control")
                os.makedirs(base, exist_ok=True)
                ep_src = os.path.join(ROOT, "benchmark", "episodes", ep + ".json")
                ep_copy = os.path.join(base, "%s.copy.json" % ep)
                shutil.copyfile(ep_src, ep_copy)
                iso_auth = None
                if norm is not None:
                    iso_auth = {
                        "authorization_id": norm["authorization_id"],
                        "max_attempts": norm["max_attempts"],
                        "api_base": norm["api_base"], "model": norm["model"],
                        "temperature": norm["temperature"], "status": norm["status"],
                        "budget_ledger": ledger_path,
                        "batch_need": tm[ep]}
                iso = IsolatedRun(run_dir, out, control, cand, adapter=adapter,
                                  quota=tm[ep], resume=False,
                                  authorization=iso_auth)
                iso.stage()
                iso.start()
                state = iso.begin_run(ep_copy, resume=False, require_assets=True)
                hosts[(arm, ep)] = {"iso": iso, "state": state, "out": out,
                                    "control": control, "run_id": iso.run_id,
                                    "ep": ep, "arm": arm, "cand": cand}
                ctl_dirs.append(control)
                with open(os.path.join(control, "isolation_sentinel.json"), "w",
                          encoding="utf-8") as f:
                    json.dump({"arm": arm, "episode": ep, "candidate": cand,
                               "run_id": iso.run_id, "out": _rel_root(out)}, f)

            # 2) 按 seq 派发；attempt_id 从真实账本增量取（A2）。
            #    2026-11-02 W15N 提速：宿主级并行——每宿主一个 worker 线程，
            #    宿主内保持 seq 顺序（前驱守卫语义不变），宿主间真并行；
            #    workers 可调上限（None=全部宿主）。exec_events 按 seq 重建保证核验序。
            import threading
            per_host = {}
            for row in attempts:
                per_host.setdefault((row["arm"], row["episode"]), []).append(row)
            results, stop_flag = {}, threading.Event()
            res_lock = threading.Lock()
            stopped_holder = {"stopped": None}

            def _host_worker(hkey, rows):
                h = hosts[hkey]
                iso, state = h["iso"], h["state"]
                for row in rows:
                    if stop_flag.is_set():
                        return
                    pre_ids = _attempt_started_ids(h["control"])
                    try:
                        manifest = iso.step_one_turn(state, expect_turn=row["turn"])
                    except RuntimeError as e:
                        with res_lock:
                            if stopped_holder["stopped"] is None:
                                stopped_holder["stopped"] = {
                                    "reason": "predecessor_or_dispatch_failure",
                                    "seq": row["seq"], "arm": row["arm"],
                                    "episode": row["episode"], "turn": row["turn"],
                                    "detail": str(e)[:200]}
                        stop_flag.set()
                        return
                    post_ids = _attempt_started_ids(h["control"])
                    new_ids = post_ids[len(pre_ids):]
                    ev = {
                        "seq": row["seq"], "arm": row["arm"], "episode": row["episode"],
                        "turn": row["turn"], "case_id": manifest["case_id"],
                        "run_id": iso.run_id, "attempt_ids": new_ids,
                        "dispatched": bool(new_ids),
                        "case_input_sha256_16": (manifest.get("case_input_sha256") or "")[:16],
                        "score10": manifest["score10"], "veto": manifest["veto"]}
                    with res_lock:
                        results[row["seq"]] = ev

            host_threads = []
            wcap = max(1, int(workers)) if workers else len(per_host)
            sem = threading.Semaphore(wcap)

            def _bounded_worker(hkey, rows):
                with sem:
                    _host_worker(hkey, rows)

            for hkey, rows in per_host.items():
                t = threading.Thread(target=_bounded_worker, args=(hkey, rows))
                t.start()
                host_threads.append(t)
            for t in host_threads:
                t.join()
            stopped = stopped_holder["stopped"]
            exec_events.extend(results[r["seq"]] for r in attempts
                               if r["seq"] in results)

            # 3) 全成功 → 收口每完整 8 轮常驻宿主
            if stopped is None:
                for k in keys:
                    m, _p = hosts[k]["iso"].finish_run(hosts[k]["state"])
                    hosts[k]["ep_manifest"] = m
                finished = True
        finally:
            for k in keys:
                try:
                    hosts[k]["iso"].close()
                except Exception:
                    pass
    finally:
        if network_fake or transport_live:
            urllib.request.urlopen = real_urlopen

    # ── 汇总：执行序/计数【从真实事件核验】 ──
    kinds, ledger_detail = _tally_ledgers(ctl_dirs)
    dispatched = kinds["attempt_started"]
    real_dispatched = dispatched if (mode == "live" and transport is None) else 0
    stub_dispatched = dispatched if (network_fake or transport_live) else 0
    mock_dispatched = dispatched if mode == "mock" else 0
    execution_order_verified = (
        stopped is None and len(exec_events) == len(attempts) and
        all(ev["seq"] == row["seq"] and ev["arm"] == row["arm"]
            and ev["episode"] == row["episode"] and ev["case_id"] == row["case_id"]
            and ev["dispatched"] and len(ev["attempt_ids"]) >= 1
            for ev, row in zip(exec_events, attempts)))
    per_key_dispatch = {}
    for ev in exec_events:
        per_key_dispatch[(ev["arm"], ev["episode"])] = \
            per_key_dispatch.get((ev["arm"], ev["episode"]), 0) + 1
    per_key_dispatch = {"%s/%s" % k: v for k, v in per_key_dispatch.items()}
    dispatch_bijection = (sum(1 for e in exec_events if e["dispatched"])
                          == dispatched)
    runs = [{
        "arm": h["arm"], "episode": h["ep"], "candidate": h["cand"],
        "run_id": h["run_id"],
        "status": "DONE" if (finished and h["state"]["next_index"]
                             == tm[h["ep"]]) else "STOPPED",
        "turns_done": h["state"]["next_index"],
        "turns_required": tm[h["ep"]],
    } for h in hosts.values()]
    all_run_ids = [h["run_id"] for h in hosts.values()]
    expect_hosts = 2 * len(set(e for e, _o in cfg["episode_order"]))
    state_isolated = (len(set(all_run_ids)) == len(keys) == expect_hosts)

    # UTF-16 计量用父本机制（两批父本都是 reference_product_v1，暴露 rc）；
    # 跨臂 user prompt 长差是【通用诊断】——v3 资产删减的 token 收益正体现在此。
    parent_spec = load(os.path.join(ROOT, "candidates", cfg["arm_map"]["parent"],
                                    "mechanism", "mechanism.py"), "mech_parent")

    # —— v2 回忆批次专用指标：仅当候选确实含【回忆】新增段时才测；否则如实记 N/A ——
    recall_applicable = bool(cfg["recall_capable"])
    cap = max_recall_seg = inj_max = None
    recall_seg_ok = injection_ok = None
    newseg, inj_rows = [], []
    if recall_applicable:
        cand_path = os.path.join(ROOT, "candidates", cfg["arm_map"]["candidate"],
                                 "mechanism", "mechanism.py")
        cap = load(cand_path, "mech_cap").MAX_NEW_CONTEXT_CHARS
        spec = load(cand_path, "mech_spec")
        for k in keys:
            if k[0] != "candidate":
                continue
            for r in _recall_newseg_from_calls(hosts[k]["out"]):
                r["episode"] = k[1]
                newseg.append(r)
        max_recall_seg = max([r["recall_seg_utf16"] for r in newseg], default=0)
        recall_seg_ok = max_recall_seg <= cap
        # A4 联合注入预算门禁：候选自身 new_seg_est（同状态同装配）
        inj_cap = cap
        for k in keys:
            if k[0] != "candidate":
                continue
            rws, mx, cp = _injection_newseg_from_notes(hosts[k]["out"], cap)
            for r in rws:
                r["episode"] = k[1]
            inj_rows.extend(rws)
            inj_max = max((inj_max or 0), mx)
            inj_cap = cp or inj_cap
        injection_ok = (inj_max or 0) <= (inj_cap or cap)
        injection_cap = inj_cap
    else:
        injection_cap = None

    # 跨臂 user 总长差——通用诊断，不作门禁（两臂状态可不同、共同裁剪会误报）
    diff_rows, max_diff = [], 0
    for ep, _o in cfg["episode_order"]:
        p_out = hosts.get(("parent", ep), {}).get("out")
        c_out = hosts.get(("candidate", ep), {}).get("out")
        if p_out and c_out:
            rws, mx = _combined_newctx_from_calls(p_out, c_out, parent_spec)
            for r in rws:
                r["episode"] = ep
            diff_rows.extend(rws)
            max_diff = max(max_diff, mx)

    boundary_problems = []
    if adapter == "openai_compat" and captured:
        allowed = set(api_candidate.REQUEST_BODY_ALLOWED_KEYS)
        for i, body in enumerate(captured):
            if set(body) - allowed:
                boundary_problems.append("body_keys@%d=%s"
                                         % (i, sorted(set(body) - allowed)))
            if body.get("model") != PINNED_MODEL \
                    or body.get("temperature") != PINNED_TEMPERATURE:
                boundary_problems.append("model/temp@%d" % i)

    network_label = (
        "REAL network (live)" if (mode == "live" and transport is None) else
        "live branch, rehearsal transport (0 real)" if transport_live else
        "disabled (fake urlopen in-process)" if network_fake else
        "disabled (offline mock adapter)")

    result = {
        "mode": mode, "adapter": adapter,
        "execution_entry": "IsolatedRun (begin_run/step_one_turn/finish_run 逐 slot)",
        "guards_applied": bool(do_guards),
        "attempts_planned": plan["total_attempts"],
        "residents": len(keys),
        "dispatched_slots": len(exec_events),
        "runs_done": sum(1 for r in runs if r["status"] == "DONE"),
        "ledger_kinds": kinds,
        "attempt_started": kinds["attempt_started"],
        "attempt_completed": kinds["attempt_completed"],
        "attempt_failed": kinds["attempt_failed"],
        "dispatched_attempts": dispatched,
        "real_dispatched": real_dispatched,
        "stub_dispatched": stub_dispatched,
        "mock_dispatched": mock_dispatched,
        "execution_order_verified": bool(execution_order_verified),
        "dispatch_bijection": bool(dispatch_bijection),
        "per_key_dispatch": per_key_dispatch,
        "state_isolated": bool(state_isolated),
        "distinct_run_ids": sorted(set(all_run_ids)),
        "batch_id": cfg["batch_id"],
        "arm_mapping": cfg["arm_map"],
        "recall_metrics_applicable": bool(recall_applicable),
        "recall_seg_max_utf16": max_recall_seg, "recall_seg_cap": cap,
        "recall_seg_ok": (bool(recall_seg_ok) if recall_applicable else None),
        "injection_newseg_max_utf16": inj_max, "injection_cap": injection_cap,
        "injection_within_budget_ok": (bool(injection_ok) if recall_applicable else None),
        "injection_detail": inj_rows,
        "cross_arm_user_len_diff_max": max_diff,
        "cross_arm_user_len_diff_detail": diff_rows,
        "boundary_problems": boundary_problems,
        "authorization_id": (norm or {}).get("authorization_id"),
        "evidence_persisted": bool(persist),
        "stopped": stopped,
        "runs": runs,
        "exec_events": exec_events,
        "real_model_calls": real_dispatched,
        "network": network_label,
        "out_base": _rel_root(tmp),
    }
    # A1：openai_compat 证据永久保留；只有 mock 且 keep_tmp=False 才清临时
    if not persist and not keep_tmp:
        shutil.rmtree(tmp, ignore_errors=True)
    return result


# ───────────────────────── §3.4 报告接线（两臂产物→分层/配对） ─────────────────────────

def reports_from_batch(result, plan, tmp_base, out_dir=None, annotations_doc=None):
    """§3.4：两臂真实输出目录 + EXAM_INDEX + exam_layered_score 一起接通——
    从同一批次直接生成【逐臂分层】与【配对差异】。

    未提供批注（annotations_doc=None）时，所有语义槽如实标 unrated（绝不冒充
    pass），仅程序层分数/ veto / token / 时延真实可读。逐 slot 交错的执行链
    保证两臂同题、同卡、同采样；这里只做“搬运→分层→配对”，不重跑模型。"""
    import exam_layered_score as layer
    cfg = cfg_from_plan(plan)
    out_dir = out_dir or os.path.join(tmp_base, "_reports")
    os.makedirs(out_dir, exist_ok=True)
    episodes = [e for e, _o in cfg["episode_order"]]
    arm_of = {"parent": cfg["arm_map"]["parent"], "candidate": cfg["arm_map"]["candidate"]}
    run_by_key = {(r["arm"], r["episode"]): r for r in result["runs"]}
    inj_by_case = {c["case_id"]: c for c in result.get("injection_detail", [])}
    diff_by_case = {c["case_id"]: c
                    for c in result.get("cross_arm_user_len_diff_detail", [])}

    layered = {}
    for arm in ("parent", "candidate"):
        exam_root = os.path.join(out_dir, "EXAM_%s" % arm)
        os.makedirs(exam_root, exist_ok=True)
        per_ep_run = {}
        for ep in episodes:
            src_out = os.path.join(tmp_base, "%s_%s" % (arm, ep), "out")
            dst = os.path.join(exam_root, ep)
            if os.path.isdir(src_out):
                if os.path.isdir(dst):
                    shutil.rmtree(dst, ignore_errors=True)
                shutil.copytree(src_out, dst)
                per_ep_run[ep] = (run_by_key.get((arm, ep)) or {}).get("run_id")
        ident = result.get("authorization_id")
        idx = {"started_at": _dt.datetime.now().isoformat(timespec="seconds"),
               "harness": arm_of[arm],
               "harness_identity_sha256": plan["frozen"]["identity_closure"][arm]
               ["identity_sha256"],
               "authorization_id": ident, "batch_id": ident,
               "model": PINNED_MODEL, "temperature": PINNED_TEMPERATURE,
               "status": result.get("mode"), "runs": [
                   {"episode_id": ep, "run_id": per_ep_run.get(ep)}
                   for ep in episodes if ep in per_ep_run]}
        json.dump(idx, open(os.path.join(exam_root, "EXAM_INDEX.json"), "w",
                            encoding="utf-8"), ensure_ascii=False, indent=1)
        res = layer.summarize(exam_root, annotations_doc=annotations_doc,
                              annotations_path=None)
        json.dump(res, open(os.path.join(out_dir, "LAYERED_SCORE_%s.json" % arm),
                            "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        layered[arm] = res

    # 逐臂 token 合计（分层结果按 episode 报 tokens，这里汇总到臂级）
    def _arm_tokens(res):
        tp = sum(e["tokens"]["prompt"] for e in res["episodes"])
        tc = sum(e["tokens"]["completion"] for e in res["episodes"])
        lat = res["total"].get("latency_ms", {})
        return {"prompt": tp, "completion": tc,
                "latency_p50_ms": lat.get("p50"), "latency_p95_ms": lat.get("p95"),
                "latency_max_ms": lat.get("max")}
    tok_by_arm = {arm: _arm_tokens(layered[arm]) for arm in ("parent", "candidate")}

    # 配对差异（逐位）：程序分/veto/token/时延 + 真实新增上下文
    def _by_case(res):
        return {t["case_id"]: t for t in res["turns"]}
    P, C = _by_case(layered["parent"]), _by_case(layered["candidate"])
    paired, win = [], {"parent": 0, "candidate": 0, "tie": 0}
    for case_id in sorted(set(P) | set(C)):
        p, c = P.get(case_id), C.get(case_id)
        if not p or not c:
            paired.append({"case_id": case_id, "status": "partial",
                           "parent": bool(p), "candidate": bool(c)})
            continue
        sp, sc = p["program_score10"], c["program_score10"]
        outcome = "tie" if sp == sc else ("parent" if sp > sc else "candidate")
        win[outcome] += 1
        inje = inj_by_case.get(case_id, {})
        dif = diff_by_case.get(case_id, {})
        paired.append({
            "case_id": case_id, "status": "paired",
            "parent_program10": sp, "candidate_program10": sc,
            "parent_veto": p.get("veto"), "candidate_veto": c.get("veto"),
            "parent_primary_semantic": p["primary_semantic"],
            "candidate_primary_semantic": c["primary_semantic"],
            "program_winner": outcome,
            "injection_new_seg_est_utf16": inje.get("new_seg_est_utf16"),
            "cross_arm_user_len_diff_added_utf16": dif.get("added_utf16"),
            "parent_tokens": p.get("tokens"), "candidate_tokens": c.get("tokens"),
            "parent_latency_ms": p.get("latency_ms"),
            "candidate_latency_ms": c.get("latency_ms"),
        })
    all_semantic_unrated = all(
        t["primary_semantic"] == "unrated"
        for arm in ("parent", "candidate") for t in layered[arm]["turns"])
    doc = {
        "mode": result.get("mode"), "authorization_id": result.get("authorization_id"),
        "batch_id": cfg["batch_id"],
        "arm_mapping": cfg["arm_map"],
        "recall_metrics_applicable": bool(result.get("recall_metrics_applicable")),
        "parent": {"program_mean10": layered["parent"]["total"]["program_mean10"],
                   "semantic": layered["parent"]["total"]["semantic"],
                   "cost": tok_by_arm["parent"]},
        "candidate": {"program_mean10": layered["candidate"]["total"]["program_mean10"],
                      "semantic": layered["candidate"]["total"]["semantic"],
                      "cost": tok_by_arm["candidate"]},
        "paired_program_winner_counts": win,
        "all_semantic_unrated_without_annotations": bool(all_semantic_unrated),
        "turns_parent": len(layered["parent"]["turns"]),
        "turns_candidate": len(layered["candidate"]["turns"]),
        "injection_newseg_max_utf16": result.get("injection_newseg_max_utf16"),
        "injection_cap": result.get("injection_cap"),
        "injection_within_budget_ok": result.get("injection_within_budget_ok"),
        "cross_arm_user_len_diff_max": result.get("cross_arm_user_len_diff_max"),
        "paired": paired,
    }
    json.dump(doc, open(os.path.join(out_dir, "PAIRED_DIFF.json"), "w",
                        encoding="utf-8"), ensure_ascii=False, indent=1)
    return {"out_dir": _rel_root(out_dir), **doc}


# ───────────────────────── 反例与守卫自检 ─────────────────────────

def reject_duplicate():
    """reviewer A 反例：把某臂 E001 T02 改成重复 T01 → validate_plan 必须零派发拒绝。"""
    doc = json.loads(json.dumps(_fresh_plan()))
    for r in doc["attempts"]:
        if r["arm"] == "parent" and r["episode"] == "E001" and r["turn"] == 2:
            r["turn"] = 1
            r["case_id"] = "E001_T01"
            r["pair_key"] = "E001_T01"
            break
    try:
        execute_plan(doc, mode="mock", out_base=tempfile.mkdtemp(prefix="rej_"),
                     keep_tmp=False)
        return False, "未拒绝重复轮"
    except PlanError as e:
        return True, "rejected: %s" % str(e)[:120]


def reject_bad_freeze():
    """reviewer A 反例：计划里把候选冻结身份填成全 0 → verify_frozen_identity 必须拒。"""
    doc = json.loads(json.dumps(_fresh_plan()))
    doc["frozen"]["identity_closure"]["candidate"]["identity_sha256"] = "0" * 64
    try:
        execute_plan(doc, mode="mock", out_base=tempfile.mkdtemp(prefix="rej_"),
                     keep_tmp=False)
        return False, "未拒绝错误冻结身份"
    except PlanError as e:
        return True, "rejected: %s" % str(e)[:120]


def reject_out_of_order():
    """reviewer 交错反例：把 parent E001 的 T02/T03 轮次互换（计划内部仍自洽：
    集合 1..8、case_id↔轮、配对齐全、seq 唯一 → validate_plan 通过），但派发序
    变成 …1,3,2…。execute_plan 必须在【派发前】被 step_one_turn 前驱守卫拦下：
    停在越界 slot、attempt_started 远小于 48、不补跑/不跳轮。"""
    doc = json.loads(json.dumps(_fresh_plan()))
    rows = [r for r in doc["attempts"]
            if r["arm"] == "parent" and r["episode"] == "E001"]
    # rows 按 seq 升序，turns = 1,2,3,...；互换第 2、3 行的 (turn,case_id,pair_key)
    a, b = rows[1], rows[2]
    for k in ("turn", "case_id", "pair_key"):
        a[k], b[k] = b[k], a[k]
    res = execute_plan(doc, mode="mock",
                       out_base=tempfile.mkdtemp(prefix="rej_ooo_"),
                       keep_tmp=False)
    if res["stopped"] is None:
        return False, "未拦截乱序前驱（竟然跑完）"
    if res["attempt_started"] >= doc["total_attempts"]:
        return False, "乱序仍派发满额 started=%d" % res["attempt_started"]
    if res["stopped"]["reason"] != "predecessor_or_dispatch_failure":
        return False, "停止原因非前驱守卫：%s" % res["stopped"]["reason"]
    return True, "rejected: stopped@seq=%s started=%d/%d (前驱守卫派发前拦截)" % (
        res["stopped"]["seq"], res["attempt_started"], doc["total_attempts"])


def _valid_live_auth_doc(out_base):
    """构造与 live 相同的合法授权单（绑定 PINNED 端点/model/temp、额度 48），
    budget_ledger 落在本 out_base 私有目录 → 每次测试用干净账本，不累计。"""
    p = os.path.join(out_base, "_auth", "AUTH_LIVESTUB.jsonl")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return {"authorization_id": "AUTH-LIVESTUB-%s"
            % _dt.datetime.now().strftime("%Y%m%d%H%M%S"),
            "authorized": True, "status": "ACTIVE",
            "real_endpoint": {"api_base": PINNED_ENDPOINT, "model": PINNED_MODEL,
                              "temperature": PINNED_TEMPERATURE,
                              "max_attempts": 48},
            "budget_ledger": p}


def live_no_auth():
    """§3.2 反例：mode=live 不给授权 → 构造任何 IsolatedRun 之前零派发拒绝。"""
    try:
        execute_plan(_fresh_plan(), mode="live", authorization=None,
                     out_base=tempfile.mkdtemp(prefix="live_no_"))
        return False, "无授权竟然未拒绝"
    except PlanError as e:
        return True, "rejected(零派发): %s" % str(e)[:120]


def live_invalid_auth():
    """§3.2 反例：mode=live 给非法授权（未 authorized / 端点不符）→ 授权对账
    在任何构造之前拒绝。"""
    ob = tempfile.mkdtemp(prefix="live_bad_")
    bad = _valid_live_auth_doc(ob)
    bad["authorized"] = False              # 关/未授权 → validate_authorization_doc 抛
    try:
        execute_plan(_fresh_plan(), mode="live", authorization=bad, out_base=ob)
        return False, "无效授权竟然未拒绝"
    except PlanError as e:
        return True, "rejected(零派发): %s" % str(e)[:120]


def live_stub_valid():
    """§3.2 正例：合法授权经 live 相同配置/预算接线，在替身网络下走通 48 slot
    （零真实调用），执行序与逐 slot 交错与 capture 一致。"""
    ob = tempfile.mkdtemp(prefix="live_stub_")
    auth = _valid_live_auth_doc(ob)
    res = execute_plan(_fresh_plan(), mode="live_stub", authorization=auth,
                       out_base=ob, keep_tmp=False)
    ok = (res["attempt_started"] == 48 and res["attempt_completed"] == 48
          and res["attempt_failed"] == 0 and res["execution_order_verified"]
          and res["dispatch_bijection"] and res["state_isolated"]
          and res["recall_seg_ok"] and res["injection_within_budget_ok"]
          and not res["boundary_problems"]
          and res["real_model_calls"] == 0 and res["stub_dispatched"] == 48
          and res["network"] == "disabled (fake urlopen in-process)")
    return ok, ("live_stub started=%d completed=%d order_ok=%s bijection=%s "
                "isolated=%s real_calls=%d" % (
                    res["attempt_started"], res["attempt_completed"],
                    res["execution_order_verified"], res["dispatch_bijection"],
                    res["state_isolated"], res["real_model_calls"]))


def live_branch_persistence_and_counting():
    """A1/A2 零网络演练【真实 live 分支】：注入假 transport（socket 不出网），
    证明 (1) 返回后 out_base 仍在（不删付费证据）；(2) 计数来自真实 attempt
    事件——真实网络=0、替身派发=48；(3) 逐 slot 附真实 attempt_id。"""
    ob = os.path.join(tempfile.mkdtemp(prefix="live_branch_"), "batch")
    captured = []
    transport = make_fake_urlopen(captured)
    auth = _valid_live_auth_doc(ob)
    res = execute_plan(_fresh_plan(), mode="live", authorization=auth,
                       out_base=ob, transport=transport)
    persisted = os.path.isdir(ob) and any(
        os.path.isdir(os.path.join(ob, d)) for d in os.listdir(ob))
    slots_with_ids = all(len(e["attempt_ids"]) >= 1 for e in res["exec_events"])
    ok = (persisted and res["real_model_calls"] == 0
          and res["stub_dispatched"] == 48 and res["real_dispatched"] == 0
          and res["attempt_started"] == 48 and res["execution_order_verified"]
          and slots_with_ids
          and res["network"] == "live branch, rehearsal transport (0 real)")
    return ok, ("live_branch persisted=%s stub=%d real=%d order=%s slots_with_attempt=%s"
                % (persisted, res["stub_dispatched"], res["real_dispatched"],
                   res["execution_order_verified"], slots_with_ids))


def insufficient_batch_preflight():
    """A3：48 计划 + 额度 8 → 首发前整批预检拒绝，零派发（transport 记 0 次）。"""
    ob = tempfile.mkdtemp(prefix="insuf_")
    captured = []
    transport = make_fake_urlopen(captured)
    auth = _valid_live_auth_doc(ob)
    auth["real_endpoint"]["max_attempts"] = 8       # 整批需 48 > 8
    try:
        execute_plan(_fresh_plan(), mode="live", authorization=auth,
                     out_base=ob, transport=transport)
        return False, "额度不足却未首发拒绝（已派发 %d）" % len(captured)
    except PlanError as e:
        zero = len(captured) == 0
        return zero, ("rejected(零派发预检): captured=%d %s"
                      % (len(captured), str(e)[:80])) if zero else \
            (False, "拒绝但仍派发了 %d 次" % len(captured))


def live_refuse_overwrite():
    """A1：同一 live out_base 已有实跑证据 → 二次运行拒绝覆盖（不删不清，只拒）。"""
    ob = os.path.join(tempfile.mkdtemp(prefix="ovr_"), "batch")
    transport = make_fake_urlopen([])
    auth1 = _valid_live_auth_doc(ob)
    execute_plan(_fresh_plan(), mode="live", authorization=auth1,
                 out_base=ob, transport=transport)
    auth2 = _valid_live_auth_doc(ob)
    try:
        execute_plan(_fresh_plan(), mode="live", authorization=auth2,
                     out_base=ob, transport=transport)
        return False, "覆盖重跑未被拒绝"
    except PlanError as e:
        return True, "拒绝覆盖: %s" % str(e)[:90]


def report_wiring():
    """§3.4/§3.5 验收：capture 逐 slot 产物 → 直接生成两臂分层 + 配对差异。
    无批注时语义槽必须如实 unrated（绝不冒充 pass）；程序层分数逐位可读；
    真实渲染联合预算达标。"""
    ob = tempfile.mkdtemp(prefix="repwire_")
    res = execute_plan(_fresh_plan(), mode="capture", out_base=ob, keep_tmp=True)
    rep = reports_from_batch(res, _fresh_plan(), ob)
    ls_cand = json.load(open(os.path.join(ob, "_reports",
                                           "LAYERED_SCORE_candidate.json"),
                             encoding="utf-8"))
    snapshot_bound = all(t.get("case_input_sha_source") == "run_snapshot"
                         for t in ls_cand["turns"])
    ok = (res["execution_order_verified"] and res["injection_within_budget_ok"]
          and rep["turns_parent"] == 24 and rep["turns_candidate"] == 24
          and rep["all_semantic_unrated_without_annotations"] is True
          and rep["parent"]["program_mean10"] is not None
          and sum(rep["paired_program_winner_counts"].values()) == 24
          and snapshot_bound)
    return ok, ("report_wiring turns=P%d/C%d unrated_all=%s snapshot_bound=%s "
                "prog_mean P=%s C=%s winners=%s injection_ok=%s" % (
                    rep["turns_parent"], rep["turns_candidate"],
                    rep["all_semantic_unrated_without_annotations"], snapshot_bound,
                    rep["parent"]["program_mean10"], rep["candidate"]["program_mean10"],
                    rep["paired_program_winner_counts"],
                    rep["injection_within_budget_ok"]))


def _fresh_plan():
    return emit_plan(os.path.join(ROOT, "logs", "_plan_probe", "INTERLEAVE_PLAN.json"))


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--emit-plan", action="store_true")
    ap.add_argument("--execute-capture", action="store_true")
    ap.add_argument("--execute-mock", action="store_true")
    ap.add_argument("--fail-fast", action="store_true")
    ap.add_argument("--fault-at", type=int, default=5)
    ap.add_argument("--reject-duplicate", action="store_true")
    ap.add_argument("--reject-bad-freeze", action="store_true")
    ap.add_argument("--reject-out-of-order", action="store_true")
    ap.add_argument("--live-no-auth", action="store_true")
    ap.add_argument("--live-invalid", action="store_true")
    ap.add_argument("--live-stub", action="store_true")
    ap.add_argument("--live-branch", action="store_true")
    ap.add_argument("--insufficient-batch", action="store_true")
    ap.add_argument("--live-refuse-overwrite", action="store_true")
    ap.add_argument("--report-wiring", action="store_true")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--out", default=None)
    ap.add_argument("--batch-config", default=None,
                    help="批配置 JSON（候选名/故事顺序/卡路径/是否含回忆段）；"
                         "缺省=旧 v2 回忆批次（E001–E003）")
    args = ap.parse_args(argv)

    ts = _dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    out_dir = args.out or os.path.join(ROOT, "logs", "exam2_execute_%s" % ts)
    os.makedirs(out_dir, exist_ok=True)

    cfg = load_batch_config(args.batch_config) if args.batch_config else default_cfg()
    plan = emit_plan(os.path.join(out_dir, "INTERLEAVE_PLAN.json"), cfg)
    print("plan emitted: batch=%s candidate=%s episodes=%s attempts=%d → %s" % (
        cfg["batch_id"], cfg["arm_map"]["candidate"],
        [e for e, _o in cfg["episode_order"]], plan["total_attempts"],
        os.path.join(out_dir, "INTERLEAVE_PLAN.json")))

    if args.batch_config:
        # 首选新批次（如 v3 删减）——作为正式入口跑一次零网络 capture，
        # 生成两臂分层+配对（语义留空待评），验证【跑的确实是本批候选+本批题】。
        n = plan["total_attempts"]
        if not args.execute_capture and not args.execute_mock:
            args.execute_capture = True         # 默认 capture
        mode = "mock" if args.execute_mock and not args.execute_capture else "capture"
        res = execute_plan(plan, mode=mode, out_base=os.path.join(out_dir, "batch"))
        assert res["attempt_started"] == n, "%s started!=%d：%s" % (mode, n, res["attempt_started"])
        assert res["attempt_failed"] == 0 and res["attempt_completed"] == n
        assert res["execution_order_verified"] and res["dispatch_bijection"]
        assert res["real_model_calls"] == 0, "零网络批却出现真实调用"
        assert not res["boundary_problems"], "出站体越界：%s" % res["boundary_problems"][:2]
        rep = reports_from_batch(
            res, plan, os.path.join(out_dir, "batch"),
            out_dir=os.path.join(out_dir, "_reports"))
        summary = {
            "batch_id": cfg["batch_id"], "mode": mode,
            "arm_mapping": cfg["arm_map"],
            "attempts": n, "started": res["attempt_started"],
            "completed": res["attempt_completed"], "failed": res["attempt_failed"],
            "execution_order_verified": res["execution_order_verified"],
            "dispatch_bijection": res["dispatch_bijection"],
            "state_isolated": res["state_isolated"], "residents": res["residents"],
            "real_model_calls": res["real_model_calls"],
            "recall_metrics_applicable": res["recall_metrics_applicable"],
            "cross_arm_user_len_diff_max": res["cross_arm_user_len_diff_max"],
            "turns_parent": rep["turns_parent"],
            "turns_candidate": rep["turns_candidate"],
            "parent_cost": rep["parent"].get("cost"),
            "candidate_cost": rep["candidate"].get("cost"),
            "all_semantic_unrated_without_annotations":
                rep["all_semantic_unrated_without_annotations"],
        }
        json.dump(summary, open(os.path.join(out_dir, "BATCH_SUMMARY.json"), "w",
                                encoding="utf-8"), ensure_ascii=False, indent=1)
        print("BATCH %s %s: started=%d/%d order=%s real_calls=%d recall_applicable=%s "
              "turns=P%d/C%d" % (cfg["batch_id"], mode, res["attempt_started"], n,
                                 res["execution_order_verified"], res["real_model_calls"],
                                 res["recall_metrics_applicable"],
                                 rep["turns_parent"], rep["turns_candidate"]))
        return 0

    any_fail = False
    do_all = args.all

    def run(name, fn, require):
        nonlocal any_fail
        res = fn()
        ok = require(res)
        with open(os.path.join(out_dir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(res if not isinstance(res, tuple) else {"msg": res[1]},
                      f, ensure_ascii=False, indent=1)
        print("%-18s %s" % (name, "PASS" if ok else "FAIL"))
        if not ok:
            any_fail = True
        return res

    if args.execute_capture or do_all:
        r = run("EXECUTE_CAPTURE", lambda: execute_plan(plan, mode="capture"),
                lambda x: (x["attempt_started"] == 48 and x["attempt_completed"] == 48
                           and x["attempt_failed"] == 0 and x["state_isolated"]
                           and x["execution_order_verified"] and x["dispatch_bijection"]
                           and x["recall_seg_ok"] and x["injection_within_budget_ok"]
                           and not x["boundary_problems"]
                           and x["real_model_calls"] == 0))
        print("   capture started=%d completed=%d failed=%d order=%s bijection=%s "
              "recall=%d/%d isolated=%s" % (
                  r["attempt_started"], r["attempt_completed"], r["attempt_failed"],
                  r["execution_order_verified"], r["dispatch_bijection"],
                  r["recall_seg_max_utf16"], r["recall_seg_cap"], r["state_isolated"]))
    if args.execute_mock or do_all:
        run("EXECUTE_MOCK", lambda: execute_plan(plan, mode="mock"),
            lambda x: (x["attempt_started"] == 48 and x["attempt_completed"] == 48
                       and x["execution_order_verified"] and x["dispatch_bijection"]
                       and x["state_isolated"] and x["real_model_calls"] == 0))
    if args.fail_fast or do_all:
        run("EXECUTE_FAILFAST",
            lambda: execute_plan(plan, mode="capture", fault_at=args.fault_at,
                                 do_guards=True),
            lambda x: (x["attempt_started"] == args.fault_at
                       and x["attempt_completed"] == args.fault_at - 1
                       and x["attempt_failed"] == 1 and x["stopped"] is not None
                       and x["real_model_calls"] == 0))
    if args.reject_duplicate or do_all:
        run("REJECT_DUPLICATE",
            lambda: {"rejected": reject_duplicate()[0], "msg": reject_duplicate()[1]},
            lambda x: x["rejected"] is True)
    if args.reject_bad_freeze or do_all:
        run("REJECT_BAD_FREEZE",
            lambda: {"rejected": reject_bad_freeze()[0], "msg": reject_bad_freeze()[1]},
            lambda x: x["rejected"] is True)
    if args.reject_out_of_order or do_all:
        run("REJECT_OUT_OF_ORDER",
            lambda: {"rejected": reject_out_of_order()[0],
                     "msg": reject_out_of_order()[1]},
            lambda x: x["rejected"] is True)
    if args.live_no_auth or do_all:
        run("LIVE_NO_AUTH",
            lambda: {"rejected": live_no_auth()[0], "msg": live_no_auth()[1]},
            lambda x: x["rejected"] is True)
    if args.live_invalid or do_all:
        run("LIVE_INVALID",
            lambda: {"rejected": live_invalid_auth()[0], "msg": live_invalid_auth()[1]},
            lambda x: x["rejected"] is True)
    if args.live_stub or do_all:
        run("LIVE_STUB", lambda: {"ok": live_stub_valid()[0],
                                  "msg": live_stub_valid()[1]},
            lambda x: x["ok"] is True)
    if args.live_branch or do_all:
        run("LIVE_BRANCH",
            lambda: {"ok": live_branch_persistence_and_counting()[0],
                     "msg": live_branch_persistence_and_counting()[1]},
            lambda x: x["ok"] is True)
    if args.insufficient_batch or do_all:
        run("INSUFFICIENT_BATCH",
            lambda: {"ok": insufficient_batch_preflight()[0],
                     "msg": insufficient_batch_preflight()[1]},
            lambda x: x["ok"] is True)
    if args.live_refuse_overwrite or do_all:
        run("LIVE_REFUSE_OVERWRITE",
            lambda: {"ok": live_refuse_overwrite()[0],
                     "msg": live_refuse_overwrite()[1]},
            lambda x: x["ok"] is True)
    if args.report_wiring or do_all:
        run("REPORT_WIRING",
            lambda: {"ok": report_wiring()[0], "msg": report_wiring()[1]},
            lambda x: x["ok"] is True)
    print("EXAM2 EXECUTE: %s" % ("FAIL" if any_fail else "PASS"))
    return 1 if any_fail else 0


if __name__ == "__main__":
    sys.exit(main())
