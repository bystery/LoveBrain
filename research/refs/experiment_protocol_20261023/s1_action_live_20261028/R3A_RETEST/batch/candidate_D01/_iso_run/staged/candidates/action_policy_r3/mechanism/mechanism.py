# -*- coding: utf-8 -*-
# candidates/action_policy_r3/mechanism/mechanism.py
#
# action_policy_r3 — FIX-9「不回范围收窄」+「成果/求助必须接住」硬判据。
# 父本=action_policy_r2（运行时闭包引用，不复制代码）；血缘 r3 → r2 → r1 → v6_goal_context。
#
# 实测根因（全部取自已付费真实答卷，非推测；来源见 IDENTITY.md 与
# refs/experiment_protocol_20261023/deadline_quality_20261028/FINAL_VERDICT_20261028.md §6、
# 同目录 SILENCE_MENU_AUDIT_20261028.json）：
#
#   1) 过度沉默（D1 新材料批，action_policy_r2 身份，200 份答卷里真实不回 5 次）：
#      D01_T08「收摊了用你电源一天没跳冰也够用成了 / 知道啦谢了不聊了我躺了」、
#      D02_T08「总算把周日这把补上了 / 娃也接了谢啦不聊了我给他做饭去」
#      ——两格候选交 action=no_reply，而这两张卡的 acceptable_actions 里没有任何"不回"选项，
#      current_action 明确要求"简短认可办成了/替她把这一天收个尾"。协议层 10.0 无 veto，
#      但按卡面语义是决策失误（评分器只按轮级 may_decline 许可，不读卡）。
#      同批 D02_T06 也选了不回——该卡自身矛盾（current_action 写"（可不回）"，
#      private_guidance 写"既不能强推也不能玩消失"），已登记为题卡缺陷，不计候选胜负。
#      对照：AK09_T01（"先睡了 你别等"）、AK12_T01（"先这样吧 结果出来我再说"）
#      同选不回，两卡都把不回列为合法 → 说明问题不是"她会收尾"，而是**没给"不回"划边界**。
#
#   2) 不回菜单泄漏到不允许不回的轮次（同类半成品，S1+D1 实测 3 处）：
#      E024_T10 / E031_T09 / D01_T03——signal_hint=暂缓|忙碌、may_decline=false，
#      机制自报 caps 里仍含"收尾"，而 r1/r2 把"收尾"的语义改成"本轮不发任何消息"。
#      模型那三次都自己选了"承接"才没炸；真选了就是 unlicensed no_reply（评分器 veto）。
#      r1/r2 的剔除条件只盯 hint==收尾 一种，漏掉了 忙碌/暂缓/拒绝 这三条同样含 收尾/停 的菜单分支。
#
#   3) action_taken="收尾" 在不同版本里语义不一致（r2 的 S1 答卷里有 2 处写 收尾 却带正文），
#      说明"同一个动作名既指发一句、又指不发"这件事本身就在污染审计与模型选择。
#
# 修法（FIX-9，flag=no_reply_scope_narrowing_fix；关掉即逐字节回到 r2）：
#   A. 动作语义复位："收尾"恢复成 v6 的含义——发一句不要求应答的短收尾（正文必须非空）；
#      "不回"从此只有"停"这一个动作名，且必须写成 r2/FIX-6 已实测通过的
#      action=no_reply + decline_reason 表达（FIX-6 的机制价值原样保留）。
#   B. "不回"只在三个条件同时成立时才进菜单：本轮 may_decline=true、她最近一条消息里
#      明确给了免回授权或指定下次由她开口（别等/不用回/结果出来我再说/明天再讲…），
#      且本窗口没有刚落地的成果、她最近一条也没有在问我要主意。
#      —— 她只说"不聊了我躺了/我做饭去/困了"是**结束对话**，不是**免回授权**：仍需一句收尾。
#   C. 泛化剔除：任何 hint（收尾/拒绝/忙碌/暂缓/积极/含糊/中性）下，只要 may_decline=false
#      或条件 B 不成立，菜单里就不出现"停"；"收尾"作为发消息动作则一律保留（修掉根因 2）。
#   D. 硬判据显式写给模型：检测到成果/求助时，动作块追加【必须接住】，说明不回等于
#      把成果丢掉、把问题悬着；清单无"停"时【发送合同】直接要求正文非空。
#   E. 保留 r2 的 FIX-7 单 JSON 硬约束与 FIX-8 回扣有据（逐字复用父本常量，不重写）。
#
# 诚实边界：
#   - 关键词表是通用生活语言，无 episode 特判；但它本质是启发式，语义判定权仍交给模型
#     （动作块里明写"以你读出的语义为准"）。表漏判/误判的后果不对称：漏判=重演过度沉默
#     （伤），误判=少给一次合法不回（她仍得到一句收尾，几乎无损）——因此表偏向保守，
#     成果类词表宁可宽一点，免回授权只认她最近一条消息里的明确表达。
#   - 本文件不声称已修好：必须由新身份真实复验批（受影响位点 + 新写同类位点 +
#     AK09/AK12 合法不回对照）回填后才有结论。修复前后数据分列，旧成绩不套用。
#   - 转发型候选：不复制父本机制，运行时加载 r2 模块（闭包引用），对外接口全部转发。
import importlib.util
import json
import os
import re
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)
_ROOT = os.path.dirname(_CAND)
_APR2 = os.path.join(_ROOT, "action_policy_r2")   # staging trigger: candidates/action_policy_r2


def _load_apr2():
    spec = importlib.util.spec_from_file_location(
        "apr2_mechanism_for_action_policy_r3",
        os.path.join(_APR2, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


apr2 = _load_apr2()
apr1 = apr2.apr1
v6g = apr2.v6g                                     # 同一份 v6_goal_context 模块对象

_FLAGS = {}
_p = os.path.join(_CAND, "params.json")
try:
    _FLAGS = (json.load(open(_p, encoding="utf-8")) or {}).get("mechanism_flags") or {}
except Exception:                                      # noqa: BLE001
    _FLAGS = {}

_NARROW = bool(_FLAGS.get("no_reply_scope_narrowing_fix", True))

# ============================================================================
# FIX-9-B 判据词典（通用生活语言，禁止 episode 特判）
# ============================================================================
# 免回授权：她明确把"回不回"交给我，或指定下次由她开口。只看她最近一条消息。
_WAIVER = ("别等", "不用回", "不需回", "不用回复", "别回复", "免回", "你先忙", "你忙你的",
           "不用管我", "不用接", "我再说", "我再讲", "我再跟她说", "我再跟你说", "再说吧",
           "再说", "到时候", "结果出来", "回头我", "下次再", "明天再", "明早再", "晚点再",
           "改天再", "有空再", "回聊", "不着急回")
# 成果/进展落地：扫整窗口她最近三条（成果常常在收尾句之前一两条落地）。
_ACHIEVE = ("成了", "办成", "办妥", "搞定", "弄好", "做好", "补上", "接到", "接了", "拿到",
            "考上", "考完", "通过", "约上", "定下", "批下来", "到账", "退烧", "好多了",
            "没跳", "不跳", "够用", "顶住", "破纪录", "签成", "顺利", "收工", "赢了", "过了")
# 求助/待答问题：只看她最近一条消息。
_ASK = ("？", "?", "怎么办", "怎么弄", "咋办", "你说", "帮我", "能不能", "行不行",
        "要不要", "该不该", "给个主意", "你觉得", "我该")

_HER_ROLES = ("HER", "PARTNER")


def _hit(text, words):
    return any(w in text for w in words)


def _her_texts(cur):
    out = []
    for m in (cur or []):
        if (m.get("role") or "").upper() in _HER_ROLES:
            t = (m.get("text") or "").strip()
            if t:
                out.append(t)
    return out


_r3_orig_signal = v6g._signal
_R3_SIGNAL_MISS = []


def _r3_signal(cur, may_decline):
    sig = _r3_orig_signal(cur, may_decline)
    if not isinstance(sig, dict):                     # 父本返回类型漂移 → 记账，按原样交给上层
        _R3_SIGNAL_MISS.append("signal_not_dict")
        return sig
    her = _her_texts(cur)
    last = her[-1] if her else ""
    window = " ".join(her[-3:])
    # 免回授权的两句窗口：她最后一条若是"困了 先这样"这类短收尾（≤12 字），
    # 授权常落在紧挨着的上一条（如"结果出来我再跟你说"）。上一条是实质内容时不回看——
    # 否则"周末那两天再说吧"这种**话题**推后会冒充成**免回**授权。
    waiver_span = last
    if len(last) <= 12 and len(her) >= 2:
        waiver_span = her[-2] + " " + last
    sig["waiver"] = _hit(waiver_span, _WAIVER)
    sig["result_report"] = _hit(window, _ACHIEVE)
    sig["open_ask"] = _hit(last, _ASK)
    sig["silence_ok"] = (bool(sig.get("may_decline")) and sig["waiver"]
                         and not sig["result_report"] and not sig["open_ask"])
    return sig


# ============================================================================
# FIX-9-A/C 菜单复位：不回只有"停"，且需三项条件同时成立；"收尾"=发一句
# ============================================================================
_V6_CAPS = getattr(apr1, "_apr_orig_caps", None)      # v6 原始菜单函数（未含 r1/r2 的剔除分支）
_R3_CAPS_MISS = []
if _V6_CAPS is None:
    _R3_CAPS_MISS.append("v6_caps_handle_missing")
    _V6_CAPS = v6g._capabilities

_r3_orig_caps = v6g._capabilities                     # r2 当前生效的菜单函数


def _r3_capabilities(sig, goal_effective):
    if _NARROW and getattr(apr1, "_apr_orig_caps", None) is not None:
        caps = _V6_CAPS(sig, goal_effective)          # 复位到 v6 菜单（收尾=发消息，一直在）
    else:                                             # flag 关闭 → 逐字节回到 r2
        return _r3_orig_caps(sig, goal_effective)
    if "停" in caps and not sig.get("silence_ok"):
        caps = [a for a in caps if a != "停"]          # 泛化剔除：任何 hint 下不回都要三条件
    if "收尾" in caps and sig.get("hint") == "收尾" and not sig.get("may_decline"):
        pass                                          # r3: 收尾是发消息动作，may_decline=false 也保留
    return caps


# ============================================================================
# FIX-9-A/D 动作块：替换 r1 的"正文必须为空"合同，改写 r2 的【不回怎么写】触发条件
# ============================================================================
_R1_CLOSING_LINE = ("她已在收尾：按下方发送合同执行——允许不回时正文为空安静收住；"
                    "不允许不回时给一条自然短消息。")
_V6_CLOSING_LINE = "她已在收尾：自然收住；可留一句下次的话头，不硬留、不加问号。"
_R3_CLOSING_LINE = ("她已在收尾：本轮只有清单里列出'停'时才允许不回；没列'停'就发一句"
                    "不要求应答的短收尾（正文非空、末尾不加问号），先把她刚给的成果或"
                    "问事接住再放她去忙。")

_NR = getattr(apr2, "_NR_RULE", "")                   # FIX-6 已实测通过的不回写法（原样复用）
_HYGIENE = getattr(apr2, "_HYGIENE", "")              # FIX-7（原样复用）
_CALLBACK = getattr(apr2, "_CALLBACK", "")            # FIX-8（原样复用）

_BASE_BLOCK = getattr(apr2, "_r2_orig_block", None)   # r1 最外层动作块（含目标让位/钩子/自我状态）
_R3_CONTRACT_MISS = []
if _BASE_BLOCK is None:
    _R3_CONTRACT_MISS.append("r1_block_handle_missing")
    _BASE_BLOCK = v6g._action_block

_CONTRACT_SEG = re.compile(r"\n\n【发送合同】.*?(?=\n\n【|\Z)", re.S)


def _r3_contract(sig, caps):
    parts = []
    if "停" in caps:
        parts.append("【发送合同（本轮可以不回，但只此一种写法）】只有选 '停' 才允许不回，"
                     "写法：" + _NR + " 选 '收尾' 不算不回——它是要发出去的一句话。"
                     "想说话就正常写正文，别把正文清空却不写 action。")
    else:
        parts.append("【发送合同（本轮必须回）】本轮能力清单里没有 '停'：不允许不回。"
                     "response.recommended 必须是一句能直接发出去的话；"
                     "把四个风格键清空会被判格式损坏或无据不回，等于本轮什么都发不出去。"
                     "清单里的 '收尾' 指发一句不要求应答的短消息（末尾不加问号、不摊新任务），"
                     "不是'不发'。")
    if _NARROW and (sig.get("result_report") or sig.get("open_ask")):
        parts.append("【必须接住】她这几条里有刚落地的成果（办成/补上/拿到/够用这类）"
                     "或正在问你要主意：本轮正文必须先把它明确接住——认可一句或答一句，"
                     "再谈收尾。这时候不回不是'尊重'，是把她的成果丢掉、把她的问题悬着。")
    return "\n\n".join(parts)


_r3_prev_block = v6g._action_block


def _r3_action_block(sig, caps, gs, contract_extra=""):
    if not _NARROW:
        return _r3_prev_block(sig, caps, gs, contract_extra)     # flag 关闭=回到 r2 行为
    block = _BASE_BLOCK(sig, caps, gs, contract_extra)
    # ① 收尾说明行：先认 r1 改写后的文案，再认 v6 原文；都没有就记账并补一句
    if _R1_CLOSING_LINE in block:
        block = block.replace(_R1_CLOSING_LINE, _R3_CLOSING_LINE)
    elif _V6_CLOSING_LINE in block:
        block = block.replace(_V6_CLOSING_LINE, _R3_CLOSING_LINE)
    elif sig.get("hint") == "收尾":
        # 只有"她已在收尾"的轮次父本才写这一行；非收尾轮没有属正常
        _R3_CONTRACT_MISS.append("closing_line_not_found@hint=%s" % sig.get("hint"))
        block += "\n" + _R3_CLOSING_LINE
    # ② 摘掉父本追加的【发送合同】段（r1 的"正文必须为空"或 r2 的【不回怎么写】变体）
    expect_contract = ("停" in caps) or (sig.get("hint") == "收尾" and not sig.get("may_decline"))
    n = len(_CONTRACT_SEG.findall(block))
    if n == 1:
        block = _CONTRACT_SEG.sub("", block, count=1)
    elif n > 1:
        _R3_CONTRACT_MISS.append("send_contract_segment_x%d@caps=%s" % (n, ",".join(caps)))
        block = _CONTRACT_SEG.sub("", block)
    elif expect_contract:
        _R3_CONTRACT_MISS.append("send_contract_segment_not_found@caps=%s" % ",".join(caps))
    if "必须完全为空" in block or "不发任何消息" in block:
        _R3_CONTRACT_MISS.append("legacy_silence_text_still_present")
    # ③ 换上 r3 的合同 + 保留 r2 的 FIX-7/FIX-8 常量
    block = block + "\n\n" + _r3_contract(sig, caps)
    if getattr(apr2, "_FLAGS", {}).get("protocol_hygiene_fix", True) and _HYGIENE:
        block += _HYGIENE
    if getattr(apr2, "_FLAGS", {}).get("callback_grounding_fix", True) and _CALLBACK:
        block += _CALLBACK
    return block


# ============================================================================
# 装配：CAP_RULES 语义复位 + 三个钩子重绑
# ============================================================================
if _NARROW:
    v6g.CAP_RULES = dict(v6g.CAP_RULES)
    v6g.CAP_RULES["收尾"] = ("发一句不要求应答的短收尾：先把她刚给的成果或话头接住/认可，"
                             "再放她去忙；正文不能为空，末尾不加问号，不摊新任务、不追问时间。")
    v6g.CAP_RULES["停"] = ("本轮不回（唯一的不回动作）：只有她明确说不用回/别等/指定下次"
                           "由她开口，且这几条里没有新成果或求助要接住时才选；写法见下方发送合同。")
if _NARROW:
    v6g._signal = _r3_signal
v6g._capabilities = _r3_capabilities
v6g._action_block = _r3_action_block


# ============================================================================
# 对外接口：全部转发 r2（机制其余部分零改动）
# ============================================================================
init = apr2.init
run_turn = apr2.run_turn
snapshot = apr2.snapshot
restore = apr2.restore
set_memory_access = apr2.set_memory_access
build_system_prompt = apr2.build_system_prompt
base = apr2.base
v5 = apr2.v5
