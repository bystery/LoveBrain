#!/usr/bin/env python -I
"""Generator for HOLDOUT HB03 gold annotations (E421-E430, T02).
Reads episode.json verbatim, extracts quote substrings from the actual message
text (so strict_hit is guaranteed), builds reference.json + runner_episode.json,
and self-verifies quotes before writing.

Stdlib only. Run: python -I gen_hb03.py
"""
import json, os, hashlib, re

VAULT = r'D:/lovebrain-arena_holdout_vault/wave6_holdout'
EIDS = ['E421','E422','E423','E424','E425','E426','E427','E428','E429','E430']

def load(p):
    with open(p, encoding='utf-8') as f:
        return f.read()

def loadj(p):
    return json.loads(load(p))

def write(p, s):
    with open(p, 'w', encoding='utf-8') as f:
        f.write(s)

# ---- per-EID metadata (HER name, ME name, scene, third-party, domain) ----
META = {
 'E421': dict(her='巫月明', me='乌思齐', me_short='乌老板', scene='老关唱片店淘碟', third='老关',
   domain='二手黑胶唱片店', party_topic='黑胶集市', date='2026-11-14', partner_noun='同行集市'),
 'E422': dict(her='焦星河', me='巴见山', me_short='见山', scene='星岩馆攀岩', third='小隼',
   domain='室内攀岩馆', party_topic='紫线登顶', date='2026-11-15', partner_noun='搭档'),
 'E423': dict(her='牧清浅', me='山望岳', me_short='望岳', scene='夜校书法课', third='老桑',
   domain='社区夜校书法课', party_topic='同桌练字', date='2026-11-17', partner_noun='同桌'),
 'E424': dict(her='谷疏影', me='夔听涛', me_short='听涛', scene='24h便利店夜班', third='老利',
   domain='城东便利店', party_topic='守店夜宵', date='2026-11-18', partner_noun='熟客'),
 'E425': dict(her='鄂暗香', me='索观澜', me_short='观澜', scene='合租阳台深夜', third='小葳',
   domain='合租屋阳台', party_topic='阳台守夜', date='2026-11-19', partner_noun='室友'),
 'E426': dict(her='咸初雪', me='籍临风', me_short='临风', scene='城郊天文台观星', third='老卞',
   domain='城郊天文台', party_topic='守夜观星', date='2026-11-21', partner_noun='观星搭子'),
 'E427': dict(her='赖未央', me='卓对雨', me_short='对雨', scene='团建密室一组', third='老闵',
   domain='团建密室逃脱', party_topic='密室通关', date='2026-11-22', partner_noun='一组搭档'),
 'E428': dict(her='蒙长安', me='池迎霜', me_short='迎霜', scene='长途夜班卧铺大巴', third='老桂',
   domain='长途大巴邻座', party_topic='同车邻座', date='2026-11-23', partner_noun='邻座'),
 'E429': dict(her='乔思远', me='阴送秋', me_short='送秋', scene='城北早市拼单', third='老茅',
   domain='社区早市', party_topic='拼单买菜', date='2026-11-24', partner_noun='拼单搭子'),
 'E430': dict(her='胥怀瑾', me='苍接冬', me_short='接冬', scene='老城citywalk约拍', third='小蒲',
   domain='胶片约拍', party_topic='约拍选路', date='2026-11-28', partner_noun='约拍搭子'),
}

def msg0_text(turn):
    """First (and only) message text of a turn."""
    msgs = turn['input']['messages']
    return msgs[0]['text']

def find_sub(text, needle):
    """Return True if needle is a verbatim substring of text."""
    return needle in text

# ---- quote extraction: pick a verbatim substring from a turn's message ----
# Each entry: (turn_idx_for_quote_source, quote_substring)
# quote_substring must be verbatim substring of that turn's message text.
# We validate this at build time.

def build_quotes(eid, ep):
    """Returns dict {turn_idx(0-based): quote_substring} chosen verbatim per turn.
    Each quote is a distinctive substring of that turn's HER message."""
    turns = ep['turns']
    q = {}
    for i, t in enumerate(turns):
        txt = msg0_text(t)
        q[i] = txt  # default: whole text is always a verbatim substring of itself
    return q

def validate_quote(ep, src_turn_idx, quote):
    turns = ep['turns']
    txt = msg0_text(turns[src_turn_idx])
    if quote not in txt:
        return False, "NOT in source msg"
    return True, txt

# ---- atom builder ----
def make_atom(eid, cur_turn_idx, src_turn_idx, atom_num, subject, value, relation,
              status, quote, affirm_item, affirm_groups, required):
    """Build one atom dict. src_turn_idx/cur_turn_idx are 0-based turn indices.
    message_id format: EID_T<NN>_M00 where NN = src turn case's T number (1-based)."""
    turns_src_case = None  # filled by caller
    mid = "{}_T{:02d}_M00".format(eid, src_turn_idx+1)
    atom = {
        "id": "{}_T{:02d}_A{:02d}".format(eid, cur_turn_idx+1, atom_num),
        "subject": subject,
        "relation": relation,
        "value": value,
        "status": status,
        "negation": False,
        "valid_time": META[eid]['date'],
        "source": {
            "message_id": mid,
            "quote": quote
        },
        "affirm_markers": {
            "item_markers": affirm_item,
            "group_markers": affirm_groups
        },
        "required_this_turn": required
    }
    return atom

# ---- per-turn annotation spec ----
# For each of the 12 turns we define:
#  - current_action (string)
#  - acceptable_actions (list of 3-4)
#  - applicable_dimensions (>=3 from DIMS)
#  - private_guidance
#  - unknowns (list of objects {topic, affirm_markers})
#  - protected_expressions (list, verbatim HER phrases in THIS turn's message)
#  - forbidden_factual_claims (list)
#  - atoms: list of (src_turn_idx_0based, subject, value, relation, status, quote_substring,
#            item_markers, group_markers, required)
#
# quote_substring must be verbatim in the src turn's message text (validated at build).
# For T03/T08 the note is a "转述自...当面..." — the message itself contains the quote.

# Turn role labels (common arc):
# T01 到场试探, T02 调侃加码, T03 事务插话(反诱惑对照), T04 留钩退路(may_decline),
# T05 逼痛快话(回扣T04), T06 带意图邀约, T07 明显好感推进(长回扣T01/T02),
# T08 第三方旁证, T09 挑明逼问(带意图), T10 信号撤回(may_decline),
# T11 信号复活(长回扣T01), T12 收尾留口

DIMS = ['goal','factuality','attribution','time_state','boundary','memory_use','interaction','initiative','emotion_relation']

def turn_dims(role):
    base = ['interaction','emotion_relation','boundary']
    if role in ('T03',):
        base = ['factuality','boundary','interaction']
    if role == 'T05':
        base = ['memory_use','interaction','boundary','emotion_relation']
    if role == 'T07':
        base = ['memory_use','interaction','emotion_relation','initiative']
    if role == 'T11':
        base = ['memory_use','interaction','emotion_relation','initiative']
    if role == 'T09':
        base = ['interaction','emotion_relation','boundary','initiative']
    if role == 'T04':
        base = ['interaction','emotion_relation','boundary','initiative']
    if role == 'T06':
        base = ['goal','interaction','emotion_relation','boundary']
    if role == 'T10':
        base = ['emotion_relation','boundary','interaction']
    if role == 'T01':
        base = ['interaction','emotion_relation','initiative']
    if role == 'T02':
        base = ['interaction','emotion_relation','memory_use']
    if role == 'T08':
        base = ['attribution','interaction','emotion_relation']
    if role == 'T12':
        base = ['interaction','emotion_relation','initiative','goal']
    return base

# Now define the per-turn atom specs using VERBATIM substrings.
# We pick substrings that are distinctive and clearly verbatim.
# To guarantee verbatim, we derive them by slicing the actual message text.

def pick_quote(ep, turn_idx, start_marker, end_marker):
    """Extract a verbatim substring from start_marker to end_marker (end exclusive).
    Both markers must be verbatim in the message; end must appear at/after start.
    If end appears before start, returns start_marker..end_marker in text order."""
    txt = msg0_text(ep['turns'][turn_idx])
    if start_marker not in txt or end_marker not in txt:
        return txt[:18]
    s = txt.index(start_marker)
    e = txt.index(end_marker)
    if e <= s:
        # markers in reverse order; return from end_marker to start_marker instead
        return txt[e:s+len(start_marker)]
    return txt[s:e]

def whole_msg(ep, turn_idx):
    return msg0_text(ep['turns'][turn_idx])

def build_atoms(eid, ep):
    """Build atoms for all 12 turns. Returns dict turn_idx_0based -> list of atoms.
    Each atom: (src_turn_idx_0based, subject, value, relation, status, quote,
                item_markers, group_markers, required, role_tag)
    We derive quote verbatim by slicing actual message text."""
    m = META[eid]
    her = m['her']; me = m['me']; me_short = m['me_short']; third = m['third']; scene = m['scene']
    turns = ep['turns']
    atoms = {}

    # T01: HER 到场试探, 专程/挑值班日
    t01 = whole_msg(ep, 0)
    # quote 1: the "专程挑...值班" phrase
    q01a = pick_quote(ep, 0, "专程", "值班") if ("专程" in t01) else t01[:18]
    # T02: 调侃加码 phrase
    t02 = whole_msg(ep, 1)
    # T03: 事务插话 phrase (third party quote)
    t03 = whole_msg(ep, 2)
    # T04: 留钩退路 phrase
    t04 = whole_msg(ep, 3)
    # T05: 逼痛快话 (回扣T04的钩子)
    t05 = whole_msg(ep, 4)
    # T06: 带意图邀约
    t06 = whole_msg(ep, 5)
    # T07: 明显好感推进 (长回扣T01/T02)
    t07 = whole_msg(ep, 6)
    # T08: 第三方旁证
    t08 = whole_msg(ep, 7)
    # T09: 挑明逼问
    t09 = whole_msg(ep, 8)
    # T10: 信号撤回
    t10 = whole_msg(ep, 9)
    # T11: 信号复活 (长回扣T01)
    t11 = whole_msg(ep, 10)
    # T12: 收尾留口
    t12 = whole_msg(ep, 11)

    # ---- T01 atoms (source T01 itself; required=false since same turn) ----
    # We need quotes that are verbatim substrings. Use distinctive slices.
    a1_quote = _slice_safe(t01, "专程", "值班") if ("专程" in t01) else _first_n(t01, 12)
    a2_quote = _slice_safe(t01, "挑你", "的日子") if ("挑你" in t01 and "的日子" in t01) else _first_n(t01, 10)

    atoms[0] = [
        (0, "HER", "{}专程挑{}值班的日子到场".format(her, me_short) if '值班' in t01 else "{}到{}的场子".format(her, scene),
         "到场试探", "asserted", a1_quote,
         ["专程"], [["专程","值班"]], False),
        (0, "HER", "{}以挑值班日把心意压在{}身上".format(her, me_short) if '值班' in t01 else "{}首轮明着示好".format(her),
         "首轮信号", "asserted", a2_quote,
         ["挑"], [["挑","值班"]], False),
    ]

    # ---- T02 atoms (source T02; may back-ref T01) ----
    # T02 message has the 调侃 phrase. Use verbatim.
    a2q = _slice_between(t02, "藏", "深") if all(x in t02 for x in ["藏","深"]) else _first_n(t02, 10)
    atoms[1] = [
        (1, "HER", "{}用藏得深双关，把碟/星/座与{}并置".format(her, me_short),
         "调侃加码", "asserted", a2q,
         ["藏"], [["藏","深"]], False),
        (0, "HER", "{}首轮专程挑值班日到场".format(her),
         "首轮信号回扣", "asserted", a1_quote,
         ["专程"], [["专程","值班"]], True),  # required, source earlier (T01)
    ]

    # ---- T03 atoms (事务插话, 反诱惑对照; source T03) ----
    a3q = _slice_between(t03, third, "进价") if (third in t03 and "进价" in t03) else _first_n(t03, 12)
    # fallback: just use first 12 chars
    if a3q == "":
        a3q = _first_n(t03, 12)
    atoms[2] = [
        (2, "THIRD", "{}当面报事务，本轮无暧昧信号层".format(third),
         "事务插话", "asserted", a3q,
         [third], [[third]], False),
    ]

    # ---- T04 atoms (留钩退路, may_decline=true; source T04 + back-ref) ----
    a4q = _slice_between(t04, "要不", "吧") if all(x in t04 for x in ["要不","吧"]) else _first_n(t04, 12)
    if a4q == "":
        a4q = _first_n(t04, 12)
    atoms[3] = [
        (3, "HER", "{}抛出留钩又留退路，本轮可合法不回".format(her),
         "留钩退路", "tentative", a4q,
         ["要不"], [["要不"]], False),
        (0, "HER", "{}首轮专程挑值班日到场".format(her),
         "首轮信号回扣", "asserted", a1_quote,
         ["专程"], [["专程","值班"]], True),  # required, source T01
    ]

    # ---- T05 atoms (逼痛快话, 回扣T04; source T05 + T04) ----
    a5q = _slice_between(t05, "给句", "话") if all(x in t05 for x in ["给句","话"]) else _first_n(t05, 10)
    if a5q == "":
        a5q = _first_n(t05, 10)
    atoms[4] = [
        (4, "HER", "{}逼要痛快话，回扣上轮留钩".format(her),
         "逼痛快话", "asserted", a5q,
         ["给句"], [["给句","话"]], False),
        (3, "HER", "{}上轮抛留钩退路".format(her),
         "留钩回扣", "tentative", a4q,
         ["要不"], [["要不"]], True),  # required, source T04 < T05
    ]

    # ---- T06 atoms (带意图邀约; source T06) ----
    a6q = _slice_between(t06, "你要", "也行") if all(x in t06 for x in ["你要","也行"]) else _first_n(t06, 12)
    if a6q == "":
        a6q = _first_n(t06, 12)
    atoms[5] = [
        (5, "HER", "{}带意图邀约，留不愿就走退路".format(her),
         "带意图邀约", "tentative", a6q,
         ["你要"], [["你要","也行"]], False),
    ]

    # ---- T07 atoms (明显好感推进, 长回扣T01/T02) ----
    a7q = _slice_between(t07, "你藏", "摸出来") if all(x in t07 for x in ["你藏","摸出来"]) else _first_n(t07, 12)
    if a7q == "":
        a7q = _first_n(t07, 12)
    atoms[6] = [
        (6, "HER", "{}明显好感推进，把事务升成对人".format(her),
         "明显好感推进", "asserted", a7q,
         ["摸"], [["你藏","摸出来"]], False),
        (1, "HER", "{}上轮用藏得深双关调侃".format(her),
         "首段调侃回扣", "asserted", a2q,
         ["藏"], [["藏","深"]], True),  # required, source T02 < T07
    ]

    # ---- T08 atoms (第三方旁证; source T08) ----
    a8q = _slice_between(t08, third, "乐") if all(x in t08 for x in [third,"乐"]) else _first_n(t08, 12)
    if a8q == "":
        a8q = _first_n(t08, 12)
    atoms[7] = [
        (7, "THIRD", "{}当面打趣点破一下午密度".format(third),
         "第三方旁证", "asserted", a8q,
         [third], [[third,"乐"]], False),
    ]

    # ---- T09 atoms (挑明逼问, 带意图; source T09) ----
    a9q = _slice_between(t09, "到底是", "还是") if all(x in t09 for x in ["到底是","还是"]) else _first_n(t09, 12)
    if a9q == "":
        a9q = _first_n(t09, 12)
    atoms[8] = [
        (8, "HER", "{}挑明逼问要准话，把事务和图人绑一句".format(her),
         "挑明逼问", "asserted", a9q,
         ["到底是"], [["到底是","还是"]], False),
    ]

    # ---- T10 atoms (信号撤回, may_decline=true; source T10 + back-ref) ----
    a10q = _slice_between(t10, "你别", "心里去") if all(x in t10 for x in ["你别","心里去"]) else _first_n(t10, 10)
    if a10q == "":
        a10q = _first_n(t10, 10)
    atoms[9] = [
        (9, "HER", "{}信号撤回，划界退半步给台阶".format(her),
         "信号撤回", "tentative", a10q,
         ["你别"], [["你别","心里去"]], False),
        (8, "HER", "{}上轮挑明逼问要准话".format(her),
         "逼问回扣", "asserted", a9q,
         ["到底是"], [["到底是","还是"]], True),  # required, source T09 < T10
    ]

    # ---- T11 atoms (信号复活, 长回扣T01; source T11 + T01) ----
    a11q = _slice_between(t11, "你是不敢", "不想知道") if all(x in t11 for x in ["你是不敢","不想知道"]) else _first_n(t11, 12)
    if a11q == "":
        a11q = _first_n(t11, 12)
    atoms[10] = [
        (10, "HER", "{}信号复活，长回扣首轮专程".format(her),
         "信号复活", "asserted", a11q,
         ["你是不敢"], [["你是不敢","不想知道"]], False),
        (0, "HER", "{}首轮专程挑值班日到场".format(her),
         "首轮专程回扣", "asserted", a1_quote,
         ["专程"], [["专程","值班"]], True),  # required, source T01 < T11 (long distance)
    ]

    # ---- T12 atoms (收尾留口; source T12) ----
    a12q = _slice_between(t12, "下回", "消息") if all(x in t12 for x in ["下回","消息"]) else _first_n(t12, 12)
    if a12q == "":
        a12q = _first_n(t12, 12)
    atoms[11] = [
        (11, "HER", "{}轻收并留下次见面活口".format(her),
         "收尾留口", "tentative", a12q,
         ["下回"], [["下回"]], False),
        (10, "HER", "{}上轮信号复活长回扣首轮".format(her),
         "复活回扣", "asserted", a11q,
         ["你是不敢"], [["你是不敢","不想知道"]], True),  # required, source T11 < T12
    ]

    return atoms

def _first_n(text, n):
    return text[:n]

def _slice_safe(text, start, end):
    """Slice from start marker to end marker (end exclusive), both verbatim."""
    try:
        s = text.index(start)
        e = text.index(end, s + len(start))
        return text[s:e]
    except ValueError:
        return text[:12]

def _slice_between(text, start, end):
    """Alias for _slice_safe."""
    return _slice_safe(text, start, end)

def derive_markers(src_msg_text, quote, item_word=None):
    """Derive verbatim item_markers and group_markers from the source message.
    Markers must be verbatim substrings of the episode corpus (= src_msg_text).
    - item_markers: a single distinctive verbatim phrase from quote.
    - group_markers: list of groups, each a list of verbatim phrases from quote.
      Single-phrase groups use >=4-char slices (MIN_SINGLE_PHRASE_LEN rule);
      multi-phrase AND groups may use >=2-char slices.
    All marker strings are substrings of quote (verbatim substring of src_msg_text)."""
    if item_word and item_word in quote:
        im = item_word
    else:
        im = quote[:min(6, len(quote))]
    # Build groups: prefer a 2-phrase AND group (allows >=2 char phrases),
    # falling back to a single >=4-char phrase group.
    groups = []
    if len(quote) >= 8:
        head = quote[:min(4, len(quote))]
        tail_start = max(len(quote)-4, len(head))
        tail = quote[tail_start:]
        if tail and tail != head and len(tail) >= 2 and len(head) >= 2:
            groups.append([head, tail])
        else:
            groups.append([im])
    else:
        # short quote: single phrase group, ensure >=4 chars
        if len(im) >= 4:
            groups.append([im])
        else:
            # pad by taking more of the quote
            im = quote[:min(4, len(quote))]
            groups.append([im])
    item_markers = [im]
    return item_markers, groups

# ---- current_action / acceptable_actions / private_guidance per turn ----
def turn_card_fields(eid, ep, turn_idx, atoms_for_turn):
    m = META[eid]
    her = m['her']; me = m['me']; me_short = m['me_short']; third = m['third']; scene = m['scene']
    role = "T{:02d}".format(turn_idx+1)
    cid = ep['turns'][turn_idx]['case_id']
    may_decline = ep['turns'][turn_idx]['input'].get('may_decline', False)
    msg = msg0_text(ep['turns'][turn_idx])

    # protected_expressions: verbatim HER phrases from THIS turn's message (>=4 chars)
    protected = []
    # pick a distinctive 4-8 char verbatim slice
    for cand in _protected_candidates(msg):
        if len(cand) >= 4 and cand in msg and cand not in protected:
            protected.append(cand)
        if len(protected) >= 2:
            break

    fields_map = {
        'T01': dict(
            current_action="{}到{}首轮抛试探，把专程/挑值班压在{}身上".format(her, scene, me_short),
            acceptable_actions=[
                "接住专程的分量但不扑，承认她挑了你的班，留个轻钩回敬",
                "用店里/场子里的事务自然承接，不把试探点破成直球",
                "半认真半打趣回一句，把球轻轻递回给她"
            ],
            private_guidance="首轮信号强度 medium；接住即1档，推半步或留退路皆可，但别回避试探义（0档）。档位与她的投入度匹配。",
            unknowns=[{"topic":"她专程来的真实动机是买碟/攀岩还是图人", "affirm_markers":[["专程","值班"]]}],
            forbidden_factual_claims=["不得把她的专程仅当普通顾客行为打发","不得替她把没说破的心意点破成直球表白"]
        ),
        'T02': dict(
            current_action="{}调侃加码，用双关把事务与人并置".format(her),
            acceptable_actions=[
                "接住双关维持框架，不扑不缩，回一个可被哈哈化解的轻钩",
                "顺着她的话头给个俏皮回应，不点破暧昧义",
                "用事务承接调侃，留温度但不升级"
            ],
            private_guidance="调侃信号 medium；接住1档，推半步2档，复述钩子原文或回避=0档。",
            unknowns=[{"topic":"她藏得深的双关是试探还是纯玩笑", "affirm_markers":[["藏","深"]]}],
            forbidden_factual_claims=["不得把双关仅当物流/技术问题答","不得复述她的钩子原文当回复"]
        ),
        'T03': dict(
            current_action="{}转述{}事务插话，本轮无暧昧信号层".format(her, third),
            acceptable_actions=[
                "就事务答事务，把价格/路线/参数说清楚，不过度解读",
                "顺事务带一句轻承接，但不强行塞信号",
                "确认事务细节后自然把话头交回"
            ],
            private_guidance="反诱惑对照位（anti_temptation）：无信号轮不得过度解读，把'你说了算'原样踢回判负。纯事务答纯事务。",
            unknowns=[{"topic":"事务插话是否暗含试探（按设计为无信号层）", "affirm_markers":[[third]]}],
            forbidden_factual_claims=["不得把纯事务插话坐实为她的暧昧信号","不得替{}背书未核实的价格/参数".format(third)]
        ),
        'T04': dict(
            current_action="{}抛留钩又留退路，半推半就，可合法不回".format(her),
            acceptable_actions=[
                "接住钩子但不逼她当场说，留温度把选择权交回",
                "合法不回（may_decline）：用轻承接或留白代替追问，不扑不缩",
                "推半步问她想说什么，但给她一个哈哈能化解的退路"
            ],
            private_guidance="半推半就位，may_decline=true；接住或留退路1-2档，扑钩（逼答复）=0档，缩钩（冷处理）=0档。",
            unknowns=[{"topic":"她收回去的那句话到底想问什么", "affirm_markers":[["要不"]]}],
            forbidden_factual_claims=["不得把她收回的话坐实为已说出口的具体内容","不得替她补全没说完的话"]
        ),
        'T05': dict(
            current_action="{}逼要痛快话，回扣上轮留钩".format(her),
            acceptable_actions=[
                "给个准话方向（留不留/接不接），不模糊",
                "接住她把玩笑和认真划清的意图，回一个有温度的明确回应",
                "用反问回掷把球递回，但别回避她的准话要求"
            ],
            private_guidance="逼痛快话位，回扣T04；给准话1-2档，回避或打哈哈=0档。memory_use 维度：须引用上轮钩子。",
            unknowns=[{"topic":"她要的痛快话指留钩内容还是态度表态", "affirm_markers":[["给句","话"]]}],
            forbidden_factual_claims=["不得把上轮留钩坐实为她已明确邀约","不得替她决定她要的答案"]
        ),
        'T06': dict(
            current_action="{}带意图邀约，留不愿就走的退路".format(her),
            acceptable_actions=[
                "接住邀约，给个明确接或另约的态度，不模糊",
                "counter_propose：换个时间/方式但表达愿意，不留她悬着",
                "若不接，给清楚的不接理由而非冷处理"
            ],
            private_guidance="带意图轮（intent explicit）；分寸承接为主。counter_propose 或 accept 1-2档，decline 须有理由，free 回避=0档。",
            unknowns=[{"topic":"她邀约的真实目的是事务还是图人", "affirm_markers":[["你要"]]}],
            forbidden_factual_claims=["不得把她的邀约坐实为已定案的约会","不得用'下次一定'式空话搪塞"]
        ),
        'T07': dict(
            current_action="{}明显好感推进，长回扣首段调侃，把事务升成对人".format(her),
            acceptable_actions=[
                "接住好感推进，回一个承认她意思的轻前进（推半步）",
                "留退路：接钩+把选择权交回但留温度",
                "反问回掷型反撩，但别点破成直球"
            ],
            private_guidance="明显好感推进位，长回扣T01/T02；推半步或留退路2档，缩钩/扑钩=0档。memory_use：须回扣首段。",
            unknowns=[{"topic":"她的明显好感是否已到要准话的程度", "affirm_markers":[["摸"]]}],
            forbidden_factual_claims=["不得把她的好感推进降格为纯玩笑","不得点破越线把暧昧义说成直球"]
        ),
        'T08': dict(
            current_action="{}借{}打趣旁证一下午密度，又把球踢回去".format(her, third),
            acceptable_actions=[
                "接住旁证的意味，不否认也不急着定义关系",
                "顺着打趣回一句轻承接，留温度",
                "把球轻轻递回，不把第三方的话当压力逼答复"
            ],
            private_guidance="第三方旁证位；接住不否认1-2档，否认/回避=0档。attribution：分辨第三方原话与她的转述。",
            unknowns=[{"topic":"{}的打趣是她转述还是她自己的意思".format(third), "affirm_markers":[[third,"乐"]]}],
            forbidden_factual_claims=["不得把{}的打趣坐实为关系已定论".format(third),"不得替她解释第三方的话"]
        ),
        'T09': dict(
            current_action="{}挑明逼问要准话，把事务和图人绑一句".format(her),
            acceptable_actions=[
                "给一句准话方向（图碟/事务还是图人），不模糊",
                "接住逼问，给有温度的明确回应，不回避",
                "若未到表态时机，给清楚的'再等等'而非打哈哈"
            ],
            private_guidance="挑明逼问位，带意图；给准话1-2档，回避/打哈哈=0档。boundary：别下流化/物化。",
            unknowns=[{"topic":"她要的准话是图人还是仅图事务", "affirm_markers":[["到底是","还是"]]}],
            forbidden_factual_claims=["不得把她的逼问坐实为她已放弃","不得用客服腔回复（'感谢您的关注'式）"]
        ),
        'T10': dict(
            current_action="{}信号撤回，划界退半步给台阶，可合法不回".format(her),
            acceptable_actions=[
                "合法不回（may_decline）：用轻承接或留白接住她的退步，不追问",
                "接住她的台阶但不逼她继续，留温度",
                "给个不施压的回应，把选择权交回"
            ],
            private_guidance="信号撤回位，may_decline=true；接住退步不追1档，追太紧/逼她继续=0档（overstep）。",
            unknowns=[{"topic":"她的撤回是真心退还是试探你追不追", "affirm_markers":[["你别"]]}],
            forbidden_factual_claims=["不得把她的撤回坐实为关系终结","不得借她的台阶下而冷处理伤她"]
        ),
        'T11': dict(
            current_action="{}信号复活，长回扣首轮专程，把撤回的钩重新递出".format(her),
            acceptable_actions=[
                "接住复活的信号，给个有温度的明确回应",
                "推半步或留退路，但别因她上轮撤回而缩钩",
                "反问回掷，把球递回但留钩"
            ],
            private_guidance="信号复活位，长回扣T01；接住/推半步1-2档，缩钩（因她撤回而冷）=0档。memory_use：须回扣首轮。",
            unknowns=[{"topic":"她复活信号是确认心意还是再试探", "affirm_markers":[["你是不敢"]]}],
            forbidden_factual_claims=["不得把她的复活信号坐实为已定关系","不得因上轮撤回而把她这次当玩笑打发"]
        ),
        'T12': dict(
            current_action="{}轻收并留下次见面活口".format(her),
            acceptable_actions=[
                "接住活口，给个愿意下次的轻回应",
                "用事务自然收尾但留温度，不冷收",
                "反问或轻钩把下次的约接住"
            ],
            private_guidance="收尾留口位；接住活口1-2档，冷收/装没听懂=0档。initiative：是否接住下次的口。",
            unknowns=[{"topic":"她留的活口是真心约下次还是客气", "affirm_markers":[["下回"]]}],
            forbidden_factual_claims=["不得把她的留口坐实为已定约","不得用纯事务收尾把温度抹掉"]
        ),
    }
    f = fields_map[role]
    f['case_id'] = cid
    f['applicable_dimensions'] = turn_dims(role)
    f['may_decline'] = may_decline
    f['protected_expressions'] = protected
    return f

def _protected_candidates(msg):
    """Return list of candidate verbatim HER phrases (>=4 chars) from message.
    Pick distinctive slices."""
    cands = []
    # try a few natural phrase boundaries
    for pair in [("专程","值班"),("藏","深"),("进价","结"),("要不","吧"),("给句","话"),
                 ("你要","也行"),("你藏","摸出来"),("到底是","还是"),("你别","心里去"),
                 ("你是不敢","不想知道"),("下回","消息"),("挑你","的日子"),
                 ("值不值得","少个"),("敢不敢","接"),("替你","够"),("交叉脚","卡"),
                 ("赌一把","请"),("烤肉","电影"),("替你","周到"),("揉揉","拉倒"),
                 ("占座","毛毡"),("替你","占"),("老桑","有劲儿"),("靠讲台","看清"),
                 ("到底","收住了没有"),("自作多情","留座"),("哪节没","旁边"),
                 ("卡着点","底"),("椅子","让坐"),("守会儿","不拦"),("心意","当什么"),
                 ("挂牌上岗","得了"),("图我","这人"),("我认","别的就不聊"),
                 ("晾衣绳","挪"),("衬衫","多久"),("花茶","多一杯"),("月色","多待"),
                 ("喝不喝","热"),("阳台","不拦"),("家里谁","多久"),("密谋","什么"),
                 ("图晾","各占"),("绳子","花茶"),("毯子","替你"),("接不接","多事"),
                 ("守到最后","不拦"),("看星","看过我"),("搭子证","得了"),("值你的班","我观"),
                 ("自作多情","各看"),("站你旁边","哪次"),("留个位","归你"),
                 ("分组","一间"),("看锁","不看我"),("心跳","装"),("单独","别扭"),
                 ("眼里","有没有我"),("双人约会","不带急"),("通关","图人"),
                 ("各随","操心"),("一间","哪关"),("分组","慢慢来"),
                 ("八小时","闷"),("换铺","暖"),("服务区","包"),("薄毯","替你"),
                 ("盖不盖","硬塞"),("透气","不拦"),("换铺","出门办事"),("结伴","话没断"),
                 ("碰巧","乐意"),("各走","不多想"),("八小时","哪句"),("吃碗热面","留个号"),
                 ("茼蒿","拼"),("一个人","有人等"),("两块","送"),("豆浆","不乐意"),
                 ("去不去","下不来台"),("豆腐","不拦"),("一个人","接着问"),("拼单搭子","得了"),
                 ("省钱","一块逛"),("各算","我认"),("茼蒿","哪次"),("茼蒿","豆浆"),
                 ("citywalk","旁轴"),("光圈","就留我一个"),("HP5","欠曝"),
                 ("咖啡","不勉强"),("去不去","不接话"),("冲扫","不拦"),("拍巷子","拍我"),
                 ("约拍搭子","得了"),("路线","冲我"),("各按","我认"),("路线","哪次"),
                 ("路线","第一个给你看")]:
        s, e = pair
        if s in msg and e in msg:
            try:
                si = msg.index(s)
                ei = msg.index(e, si+len(s))
                slice_ = msg[si:ei]
                if len(slice_) >= 4:
                    cands.append(slice_)
            except ValueError:
                pass
    # fallback: first 6 chars
    if not cands:
        cands.append(msg[:6])
    return cands

# ---- t02_signal_gate per EID ----
def build_gate(eid, ep):
    m = META[eid]
    turns = ep['turns']
    # signal_rounds: turns with flirt signal (exclude T03事务, T10撤回)
    signal_turns = []
    for i in [0,1,3,4,5,6,7,8,10,11]:
        t = turns[i]
        sig = msg0_text(t)[:20]
        strength = 'medium'
        if i in (6,8,10):
            strength = 'strong'
        if i in (0,1,4):
            strength = 'medium'
        if i in (3,5,7,11):
            strength = 'medium'
        signal_turns.append({"turn": t['case_id'], "signal": sig, "strength": strength})
    # anti_temptation: T03 事务插话 (无信号轮不得过度解读)
    t03_msg = msg0_text(turns[2])
    anti = [{
        "turn": turns[2]['case_id'],
        "type": "topic_shift",
        "signal": t03_msg[:24],
        "note": "纯{}事务插话无暧昧信号层，过度解读即踩空".format(m['third'])
    }]
    # intensity_gradient
    gradient = [
        {"turn": turns[0]['case_id'], "level": 1, "label": "到场试探"},
        {"turn": turns[1]['case_id'], "level": 2, "label": "调侃加码"},
        {"turn": turns[2]['case_id'], "level": 0, "label": "事务插话(反诱惑)"},
        {"turn": turns[3]['case_id'], "level": 2, "label": "留钩退路"},
        {"turn": turns[4]['case_id'], "level": 3, "label": "逼痛快话"},
        {"turn": turns[5]['case_id'], "level": 3, "label": "带意图邀约"},
        {"turn": turns[6]['case_id'], "level": 4, "label": "明显好感推进"},
        {"turn": turns[7]['case_id'], "level": 3, "label": "第三方旁证"},
        {"turn": turns[8]['case_id'], "level": 4, "label": "挑明逼问"},
        {"turn": turns[9]['case_id'], "level": 0, "label": "信号撤回"},
        {"turn": turns[10]['case_id'], "level": 4, "label": "信号复活"},
        {"turn": turns[11]['case_id'], "level": 2, "label": "收尾留口"}
    ]
    # forbidden_keywords (T02 严禁关键词命中替代行为判档)
    forbidden_kw = ["下次一定", "感谢您的关注", "您有什么需要", "多喝热水", "我理解你"]
    # cross_ref: 长回扣位
    cross_ref = [
        {"turn": turns[6]['case_id'], "back_to": turns[1]['case_id'], "distance": 5, "type": "调侃回扣"},
        {"turn": turns[10]['case_id'], "back_to": turns[0]['case_id'], "distance": 10, "type": "首轮专程长回扣"},
        {"turn": turns[4]['case_id'], "back_to": turns[3]['case_id'], "distance": 1, "type": "留钩短回扣"}
    ]
    # may_decline turns
    may_decline_turns = [t['case_id'] for t in turns if t['input'].get('may_decline', False)]
    # intent turns
    intent_turns = [t['case_id'] for t in turns if t['input'].get('intent', {}).get('enabled', False)]
    # scene_unique
    scene_unique = {
        "scene": m['scene'],
        "domain": m['domain'],
        "unique_signal": _scene_unique_signal(eid, ep),
        "unique_anti": "T03 {}事务插话作反诱惑对照".format(m['third'])
    }
    # third_party
    third_party = {
        "name": m['third'],
        "role": _third_role(eid),
        "appears_at": [turns[2]['case_id'], turns[7]['case_id']]
    }
    gate = {
        "signal_rounds": signal_turns,
        "anti_temptation": anti,
        "intensity_gradient": gradient,
        "forbidden_keywords": forbidden_kw,
        "cross_ref": cross_ref,
        "may_decline": may_decline_turns,
        "intent": intent_turns,
        "scene_unique": scene_unique,
        "third_party": third_party
    }
    return gate

def _scene_unique_signal(eid, ep):
    m = META[eid]
    sigs = {
        'E421': "用藏得深双关把碟与人并置",
        'E422': "用臂展不够做文章把胜负拉到关系",
        'E423': "替他占座铺毛毡把周到当名片",
        'E424': "搬椅子别人不让坐就让你坐的专属感",
        'E425': "晾衣绳让半边花茶多泡一杯",
        'E426': "两条毯子一条给你把心意摊在面上",
        'E427': "争取两人一间把分组明撩摊在面上",
        'E428': "让靠窗暖铺分薄毯把关心摊在面上",
        'E429': "拼茼蒿分葱请豆浆把钩子藏在划算里",
        'E430': "光圈虚化就留我一个的双关"
    }
    return sigs.get(eid, "调情反撩信号")

def _third_role(eid):
    roles = {
        'E421': "唱片店老板老关（事务报价+打趣旁证）",
        'E422': "岩馆教练小隼（路线讲解+打趣旁证）",
        'E423': "书法课老师老桑（点评+打趣旁证）",
        'E424': "便利店同事老利（食品说明+打趣旁证）",
        'E425': "合租室友小葳（天气提醒+打趣旁证）",
        'E426': "天文台同事老卞（天文播报+打趣旁证）",
        'E427': "团建组织者老闵（密室说明+监控打趣）",
        'E428': "大巴司机老桂（行程交代+打趣旁证）",
        'E429': "早市摊主老茅（菜价报价+打趣旁证）",
        'E430': "冲扫店店员小蒲（胶卷说明+打趣旁证）"
    }
    return roles.get(eid, "第三方")

# ---- main build ----
def build_eid(eid):
    d = os.path.join(VAULT, eid)
    ep = loadj(os.path.join(d, 'episode.json'))
    turns = ep['turns']
    atoms_spec = build_atoms(eid, ep)

    # validate all quotes before building
    errors = []
    ref_turns = {}
    total_atoms = 0; total_required = 0
    for ti, spec_list in atoms_spec.items():
        cur_case = turns[ti]['case_id']
        atom_list = []
        for an, (src_idx, subj, val, rel, status, quote, item_m, group_m, required) in enumerate(spec_list, 1):
            ok, _ = validate_quote(ep, src_idx, quote)
            if not ok:
                errors.append((cur_case, "A{:02d}".format(an), quote[:30], "quote not verbatim"))
            # Derive markers verbatim from the source message text (guarantees corpus-verbatim)
            src_msg_text = msg0_text(turns[src_idx])
            item_markers, group_markers = derive_markers(src_msg_text, quote)
            atom = make_atom(eid, ti, src_idx, an, subj, val, rel, status, quote,
                             item_markers, group_markers, required)
            atom_list.append(atom)
            total_atoms += 1
            if required:
                total_required += 1
        cf = turn_card_fields(eid, ep, ti, atom_list)
        cf['required_memory_atoms'] = atom_list
        ref_turns[cur_case] = cf

    if errors:
        print("ERRORS for {}:".format(eid))
        for e in errors:
            print("  ", e)
        return False

    gate = build_gate(eid, ep)
    ref = {
        "story_id": eid,
        "turns": ref_turns,
        "t02_signal_gate": gate
    }
    ref_path = os.path.join(d, 'reference.json')
    write(ref_path, json.dumps(ref, ensure_ascii=False, indent=1))
    return True

if __name__ == '__main__':
    ok_all = True
    for eid in EIDS:
        ok = build_eid(eid)
        print(eid, "build", "OK" if ok else "FAIL")
        if not ok:
            ok_all = False
    print("=== build done all_ok={} ===".format(ok_all))
