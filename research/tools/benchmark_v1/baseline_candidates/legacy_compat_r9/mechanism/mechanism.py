# -*- coding: utf-8 -*-
# tools/benchmark_v1/baseline_candidates/legacy_compat_r9/mechanism/mechanism.py
#
# LEGACY_COMPAT_R9 — r9_slim 的最小输入边界适配（Benchmark V1 Phase 2 §9，2026-11-08）。
#
# 被适配对象（原候选，一个字节不改）：candidates/action_policy_r9_slim
#   运行时闭包 r9_slim → r2 → r1 → v6_goal_context → v5 → reference_product_v1。
#   该闭包在 wave 系（wave1+/wave2/wave3/wave3_ext）dict 型 persona 上崩溃：
#   action_policy_r1/mechanism/mechanism.py:270 `_seed_facts` 对 persona.me/her
#   调 `.strip()`（同文件 `_seed_block` L319 同型消费），dict 无该属性 →
#   AttributeError，init 即崩（lb_runner run_episode L540 无捕获，直接上抛）。
#
# 适配面（仅此一处）：init 前把 ep_meta.persona 中 **dict 型** 的 me/her 单元
#   确定性降为旧版闭包期望的单字符串（见 persona_struct_to_str 注释与 Phase1
#   全库盘点字段域）。其余一切透传：
#   - str 型 persona.me/her 原样透传（零改动，逐字节同 r9_slim 行为）；
#   - persona 其它键（如 relationship_stage）、initial_kb、ep_meta 其余键透传；
#   - init/run_turn/snapshot/restore/set_memory_access/build_system_prompt 全部
#     转发原机制模块对象；不复制、不改写、不重排任何原候选文件。
#
# 资产转发：本目录 assets/system_prompt/*.md 仅为满足 lb_runner.
#   require_candidate_assets 的入口完整性检查（fail-fast）的占位指针文件；
#   mechanism.init 把 assets_root 一律转发到 candidates/action_policy_r9_slim/assets
#   （真实资产唯一来源）。原闭包只在 init 与 restore 时经 assets_root 构建
#   system prompt（v5_purpose_aligned L97-106 / reference_product_v1 L375-394、
#   L489-501），run_turn 不重读 ctx.assets_root，故转发点只需 init 一处；
#   restore 读快照内存下的（已转发的）真实资产路径，同样正确。
#
# 边界：本模块零模型调用、零网络、只读原候选；输出契约语义与 r9_slim 的
#   params.json output_contract=null 完全一致（见本目录 params.json）。
import importlib.util
import json
import os

_HERE = os.path.dirname(os.path.abspath(__file__))          # .../legacy_compat_r9/mechanism
_REPO = os.path.abspath(os.path.join(_HERE, "..", "..", "..", "..", ".."))  # D:\lovebrain-arena
_R9_CAND = os.path.join(_REPO, "candidates", "action_policy_r9_slim")
_R9_MECH = os.path.join(_R9_CAND, "mechanism", "mechanism.py")
_R9_ASSETS = os.path.join(_R9_CAND, "assets")

# r1 闭包只对这两个 persona 键做字符串消费（_seed_facts L270 / _seed_block L319）。
# 其它 persona 键（如 relationship_stage: str）旧闭包本就按 dict 整体透传，不触碰。
PERSONA_STR_KEYS = ("me", "her")


def _load_r9():
    """按绝对路径加载原候选机制模块（importlib，不进 sys.modules 缓存；
    原闭包内部按各自 __file__ 相对路径级联加载 r2/r1/v6/v5/base，全部落在
    candidates/ 原位，因此复用的是原候选的完整运行时闭包，非副本）。"""
    spec = importlib.util.spec_from_file_location(
        "legacy_compat_r9_target_action_policy_r9_slim", _R9_MECH)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


_r9 = _load_r9()


def persona_struct_to_str(d):
    """wave 系结构化 persona 单元（me/her 为 dict）→ 旧版闭包期望的确定性单字符串。

    字段域实读（Phase 1 盘点口径，2026-11-08 全库 239 篇 episode.json 扫描；
    样例基准 benchmark/stories_v2/wave3/E191/episode.json）：
      me:  {name: str, traits: [str, ...]}（171 篇）或
           {name, traits, boundaries: [str, ...]}（7 篇）
      her: {name, traits, boundaries}（178 篇）
      顶层另有 relationship_stage: str（本就为字符串，透传不处理）。
      实测库中不存在 age/occupation 字段；模板仍预留（通用性），出现即按序渲染。

    确定性模板（字段序固定，分隔符：段间"，"、列表项间"、"）：
      name，[age岁，][occupation，]traits 逐项[，底线：boundaries 逐项]
    语义信息全保留：
      - traits 全部逐项列出；boundaries 以"底线："前缀整体列出（类别信息保留）；
      - 已知字段（name/age/occupation/traits/boundaries）之外的任何额外键，
        以 canonical JSON（ensure_ascii=False, sort_keys=True, 紧凑分隔符）追加
        在末尾段，保证未知字段零丢失；
      - 空 dict → 空串 → r1 `_seed_facts` 的 `if not t: continue` 分支，与
        "persona 缺该键"行为完全一致。
    本函数不修改入参（只读渲染）。"""
    parts = []
    name = d.get("name")
    if isinstance(name, str) and name.strip():
        parts.append(name.strip())
    age = d.get("age")
    if isinstance(age, bool):
        pass  # bool 是 int 子类，显式排除，防止 True 渲染成 "1岁"
    elif isinstance(age, (int, float)):
        parts.append(("%g岁" % age))
    elif isinstance(age, str) and age.strip():
        parts.append("%s岁" % age.strip())
    occ = d.get("occupation")
    if isinstance(occ, str) and occ.strip():
        parts.append(occ.strip())
    for field in ("traits", "boundaries"):
        vals = d.get(field)
        if isinstance(vals, (list, tuple)) and vals:
            items = [str(v).strip() for v in vals if str(v).strip()]
            if items:
                if field == "boundaries":
                    parts.append("底线：" + "、".join(items))
                else:
                    parts.append("、".join(items))
    known = {"name", "age", "occupation", "traits", "boundaries"}
    extra = {k: d[k] for k in d if k not in known}
    if extra:
        parts.append(json.dumps(extra, ensure_ascii=False, sort_keys=True,
                                separators=(",", ":")))
    return "，".join(parts)


def adapt_episode_meta(episode_meta):
    """返回适配后的 ep_meta 浅拷贝：仅 dict 型 persona.me/her 降为字符串；
    str 型 persona / initial_kb / 其余键原样透传。不改写调用方传入的数据
    （persona 逐键浅拷贝，被替换的只有 me/her 两个值的引用）。"""
    meta = dict(episode_meta or {})
    persona = meta.get("persona")
    if isinstance(persona, dict):
        persona2 = dict(persona)
        changed = False
        for k in PERSONA_STR_KEYS:
            v = persona2.get(k)
            if isinstance(v, dict):
                persona2[k] = persona_struct_to_str(v)
                changed = True
        if changed:
            meta["persona"] = persona2
    return meta


_init_r9 = _r9.init


def init(assets_root, episode_meta, gateway, sandbox):
    """assets_root 一律转发到原候选真实资产（本目录 assets 只是占位指针，
    见文件头注释）；ep_meta 先做 dict→str persona 适配再交原 init。
    返回原机制 handle，后续 run_turn/snapshot/restore 与 r9_slim 完全一致。"""
    return _init_r9(_R9_ASSETS, adapt_episode_meta(episode_meta), gateway, sandbox)


# ---- 其余接口全部转发原机制模块对象（薄包装，无第二处逻辑） ----
run_turn = _r9.run_turn
snapshot = _r9.snapshot
restore = _r9.restore
set_memory_access = _r9.set_memory_access
build_system_prompt = _r9.build_system_prompt
