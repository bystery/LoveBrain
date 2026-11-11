# candidates/action_policy_r2/mechanism/mechanism.py
#
# action_policy_r2 — 截止日故障族修复分支（父本=action_policy_r1，全量继承，不复制后漂移）。
# 运行时依赖闭包：action_policy_r2 → action_policy_r1 →（r1 内部）v6_goal_context。
# v6_integrated 只是 params.json 血统上的祖本，本文件运行时不加载它，因此这里不写它的
# candidates/ 路径——写了会被 staging 扫描当成真实依赖、多拷一棵树，闭包就与父本不可比。
#
# 本分支只做三件事，全部 flag 门控（params.json.mechanism_flags），关掉即逐字节回到 r1：
#
# FIX-6 不回表达（no_reply_expression_fix）
#   实测根因（S2 锚点批，2026-10-28 真实答卷，非推测）：
#     AK09_T01 / AK12_T01（两轮 may_decline=True 的收尾轮）候选输出
#     response 四键全空串 + directions=[null,null,null,null] + analysis.signal=收尾，
#     无 action/decline_reason 顶层键 → 评分器按 legacy 合同判 json_schema_broken → veto 0 分、
#     零可发正文。父本同两轮给短收尾，12/12 干净。
#   r1 的 FIX-1 把机制侧规则改成"最终正文必须完全为空——一个字都不写"（见
#   candidates/action_policy_r1/mechanism/mechanism.py:116-119），而它沿用的输出合同与
#   assets/system_prompt/format.md 从没写过"不回"怎么表达；评分器（arena/lb_scorer.py v0.7/v0.8，
#   第 240-247 行）其实**早已支持** action=no_reply（要求顶层显式声明，正文必须为空）。
#   → 合同错位是 r1 自己造出来的：机制教它"用空正文表示不回"，评分器只认"用 action 键表示不回"。
#   修法：把 CAP_RULES[收尾]/[停] 与【发送合同】改成评分器认识的表达——
#   顶层写 "action":"no_reply" + "decline_reason"，response 四键给空串、directions 给 null，
#   并明确禁止"空正文但不写 action"这种既发不出也评不了分的中间态。
#   不改评分器、不改 rubric、不改题卡：只把候选自己该说的话说全。
#
# FIX-7 单 JSON 结构卫生（protocol_hygiene_fix）
#   实测根因（同批真实答卷）：
#     AK02_T01：模型在 directions 之后提前闭合顶层 }，再补写 analysis →
#       json.JSONDecoder().raw_decode 于 char330 截断（第一段仅 response/directions），
#       整轮按 json_schema_broken 记账，四风格正文其实完好，analysis 记忆写回丢失；
#     S1 E024_T02：style 键名混入零宽字符（bad_bo​y）→ json.loads 成功但缺必需键；
#     S1 E024_T04 / E034_T05：directions 给了 5 条（合同固定 4 条）。
#   父臂也非免疫（E034_T11 json_schema_broken、E027_T06 illegal_source_id）：这是共享的输出
#   协议脆弱性，不是某一臂独占故障族。
#   修法：动作块固定追加三条可核对的硬约束（只输出一个 JSON；顶层右花括号只能在 analysis
#   之后出现一次；style 键名逐字照抄；directions 恰好 4 槽，多余的并入或删除、不给第 5 条）。
#
# FIX-8 共同经历回扣有据（callback_grounding_fix）
#   实测根因：AK07_T01 候选 directions[3]"你上次说的那个考点笔记还在吗"——该锚点的对话记录、
#   persona、initial_kb 六字段与 note 里都没有"考点笔记"（全锚点目录检索无"笔记"二字）。
#   core.md 红线早已禁止编造共同经历，说明"禁止"本身不够：补一条可自检的操作性判据——
#   凡"上次/之前/你说的X"必须能在【对话记录】或【记忆行】里逐字找到，找不到就不提或问一句。
#
# 诚实边界：
#   - 三项修复都只改候选侧提示与合同表达，零额外模型调用，不改评分器/题面/卡/金标。
#   - 修复效果未经真实轨迹验证前，不声称已消除故障族；必须由新身份的真实复验回填。
#   - 本文件是转发型候选：不复制父本机制，运行时加载 r1 模块（闭包引用）。
import importlib.util
import json
import os
import re
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)
_ROOT = os.path.dirname(_CAND)
_APR1 = os.path.join(_ROOT, "action_policy_r1")   # staging trigger: candidates/action_policy_r1


def _load_apr1():
    spec = importlib.util.spec_from_file_location(
        "apr1_mechanism_for_action_policy_r2",
        os.path.join(_APR1, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


apr1 = _load_apr1()
v6g = apr1.v6g                                     # 同一份 v6_goal_context 模块对象

_FLAGS = {}
_p = os.path.join(_CAND, "params.json")
try:
    _FLAGS = (json.load(open(_p, encoding="utf-8")) or {}).get("mechanism_flags") or {}
except Exception:                                      # noqa: BLE001
    _FLAGS = {}

# ============================================================================
# FIX-6 不回表达
# ============================================================================
_NR_RULE = ("本轮不回：用顶层 \"action\":\"no_reply\" + 一句 \"decline_reason\" 表达；"
            "response 四个风格键都给空串，directions 四条全给 null。"
            "绝对不允许只把正文清空却不写 action——那种输出会被判格式损坏，本轮什么都发不出去。")

if _FLAGS.get("no_reply_expression_fix", True):
    v6g.CAP_RULES = dict(v6g.CAP_RULES)
    v6g.CAP_RULES["收尾"] = ("本轮不发任何消息：" + _NR_RULE +
                             " 安静收住，下次的话头等她再开口时再说。")
    v6g.CAP_RULES["停"] = ("本轮完全静默：不追问、不自证、不推进；" + _NR_RULE +
                            " 等她主动。")

    _r2_orig_caps = v6g._capabilities

    def _r2_capabilities(sig, goal_effective):
        caps = _r2_orig_caps(sig, goal_effective)
        # r1 已在 may_decline=false 时剔除 收尾/停；此处保持一致（不回只在被允许的轮次出现）
        if sig.get("hint") == "收尾" and not sig.get("may_decline"):
            caps = [a for a in caps if a not in ("收尾", "停")]
        return caps

    _r2_orig_block = v6g._action_block

    _R2_CONTRACT_MISS = []      # 自检用：父本【发送合同】文本漂移时在此留证（不静默放行）

    def _r2_action_block(sig, caps, gs, contract_extra=""):
        block = _r2_orig_block(sig, caps, gs, contract_extra)
        nr = [a for a in caps if a in ("收尾", "停")]
        if nr:
            # 覆盖 r1 的旧【发送合同】（"正文必须完全为空"），改为评分器可识别的表达
            new_block = block.replace(
                "：最终正文（response.recommended）必须完全为空——一个字都不写；"
                "写了任何正文都会因“不回+正文”冲突被拒发，等于本轮说错话。"
                "想说话就别选这两个动作，改选上面其他能力。",
                "：按下面【不回怎么写】执行；一旦写了任何正文，'不回+正文'冲突同样被拒发。"
                "想说话就别选这两个动作，改选上面其他能力。\n\n【不回怎么写】" + _NR_RULE)
            if new_block == block:                    # 父本文案漂移 → 记账并兜底追加
                _R2_CONTRACT_MISS.append("send_contract_text_not_found")
                new_block = block + "\n\n【不回怎么写】" + _NR_RULE
            return new_block
        return block

    v6g._capabilities = _r2_capabilities
    v6g._action_block = _r2_action_block
    _R2_HOOKED = True

# ============================================================================
# FIX-7 单 JSON 结构卫生
# ============================================================================
_HYGIENE = ("\n\n【单 JSON 硬约束】① 整条回复只有一个 JSON 对象：右花括号只能在 analysis "
            "写完之后出现一次，严禁在 directions 之后就收尾；② response 的四个键名逐字照抄 "
            "recommended/bad_boy/playful/warm，键名里不得有不可见字符、空格或改写；"
            "③ directions 固定四槽（跟进/展开/表达/转向），每槽一句可发正文或 null，"
            "不多给第五槽——想多说的内容并进对应槽或删除；④ 除 action/decline_reason/"
            "response/directions/analysis 外不写任何顶层键。")

# ============================================================================
# FIX-8 共同经历回扣有据
# ============================================================================
_CALLBACK = ("\n\n【回扣有据】凡提到'上次/之前/你说的X'这类旧事或她给过的东西，X 必须能在"
             "【对话记录】或上方【记忆行】里逐字或近乎逐字找到；找不到就不要提，宁可问一句。"
             "回扣只在找得到的时候才比新话更有分量，找不到还硬写就是编造。")

if _FLAGS.get("protocol_hygiene_fix", True) or _FLAGS.get("callback_grounding_fix", True):
    _r2_prev_block = v6g._action_block

    def _r2_action_block_hy(sig, caps, gs, contract_extra=""):
        block = _r2_prev_block(sig, caps, gs, contract_extra)
        if _FLAGS.get("protocol_hygiene_fix", True):
            block += _HYGIENE
        if _FLAGS.get("callback_grounding_fix", True):
            block += _CALLBACK
        return block

    v6g._action_block = _r2_action_block_hy


# ============================================================================
# 对外接口：全部转发 r1（机制其余部分零改动）
# ============================================================================
init = apr1.init
run_turn = apr1.run_turn
snapshot = apr1.snapshot
restore = apr1.restore
set_memory_access = apr1.set_memory_access
build_system_prompt = apr1.build_system_prompt
base = apr1.base
v5 = apr1.v5
