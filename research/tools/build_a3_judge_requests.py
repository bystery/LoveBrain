# -*- coding: utf-8 -*-
"""tools/build_a3_judge_requests.py — A3 小校准 172 请求计划（零网络零生成）。

结构（AUTH-A3-JUDGE-20261026-01 scope）：
  96 = 32 对 × S1/S2/S3 三份同源无历史会话（同序）
  16 = swap 子集 × SR 会话 同序复评（重测稳定性）
  16 = swap 子集 × SR 会话 真换序（绑定交换：{甲:乙答, 乙:甲答}，judge_swap_v1_2 语义）
  44 = 电池（22 无据/冲突主张 + 22 受保护表达）× SB 会话
裁判盲区硬保证：expected / swap_probe / claims_embedded 的期望标注 **绝不**进入 user payload；
参考三件套（allowed_actions/allowed_goal_score_range/acceptable_wordings）按 MANUAL-V2.0-A3 对裁判可见。
提交顺序固定 seed 洗牌（位置中立），计划与请求哈希冻结后才允许派发。
"""
import hashlib
import json
import os
import random

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PK = os.path.join(ROOT, 'evaluation', 'CALIBRATION_PACKET_A3_20261026')
OUT = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023', 'a3_calibration_20261026')
MANUAL_PATH = os.path.join(ROOT, 'evaluation', 'JUDGE_MANUAL_V2_0_A3.md')

JUDGE_SYSTEM_V2 = """你是聊天回复评审者（A3 校准轮）。只依据给定材料与所附评审手册（MANUAL-V2.0-A3）判断，
不要猜测答案来源、版本或成本，不要引用先后/左右/槽位顺序作为理由。
硬性要求：
1) 每条答案分开给两轨：action_score(0-3) 只评动作（不含事实判断）；fact_risk_flags[] 逐条列出
   无据/冲突主张，每条含四轴(statement_type/evidence_status/stance/severity)与逐字原文 quote、证据片段 evidence_quote。
2) 玩笑内嵌的"已发生/已拥有/已约成"断言逐条入旗；仅明显荒诞到不可能为真的夸张可标 joke_absurd 并必须引用原文。
3) 受保护表达（个人口味/带限定的观点/自身感受/边界表达/自嘲+偏好）不得作为扣分或纠正理由，只记 protected_hits。
4) action_score 落在题目"允许分数区间"内的答案不算行动缺陷，即使你更喜欢另一种说法。
5) 输出严格 JSON，无解释文字、无 markdown 代码块。
输出 schema（一次给全）：
{"node":"...","answers":{"甲":{"action_score":0-3,"fact_risk_flags":[{"quote":"...","statement_type":"...",
 "evidence_status":"...","stance":"...","severity":"关键|次要|无","joke_absurd":false,"evidence_quote":"..."}],
 "protected_hits":["..."]},"乙":{...}},
 "pref":"甲|乙|tie","pref_confidence":"low|mid|high",
 "order_reason_check":true,
 "not_rated":["..."]}
pref 合成规则固定：action 分差>=2 定胜负；分差<=1 时关键级 fact_risk 定方向（无旗方胜）；其余 tie。
"""


def sha(s):
    return hashlib.sha256((s or '').encode('utf-8')).hexdigest()


def load_all():
    nodes = [json.loads(l) for l in open(os.path.join(PK, 'nodes.jsonl'), encoding='utf-8')]
    claims = json.load(open(os.path.join(PK, 'FACT_CLAIMS_BATTERY.json'), encoding='utf-8'))
    prot = json.load(open(os.path.join(PK, 'PROTECTED_EXPRESSIONS.json'), encoding='utf-8'))
    return nodes, claims, prot


def public_evidence(node):
    ev = node['evidence']
    return {
        'persona_me': ev['persona_me'], 'persona_her': ev['persona_her'],
        'public_seed': ev['public_seed'],
        'prior_messages': ev['prior_messages'], 'current_window': ev['current_window'],
        'goal': ev['goal'], 'clock': ev['clock'],
    }


def public_reference(node):
    r = node['reference']
    return {
        'allowed_actions': r['allowed_actions'],
        'allowed_goal_score_range': r['allowed_goal_score_range'],
        'acceptable_wordings': r['acceptable_wordings'],
    }


def pair_user_payload(node, swapped):
    """绑定交换（judge_swap_v1_2 语义）：swapped ⇒ {甲:乙答, 乙:甲答}；expected/swap 标记绝不入载荷。"""
    a, b = node['answers']['甲'], node['answers']['乙']
    left, right = (b, a) if swapped else (a, b)
    return json.dumps({
        'evidence': public_evidence(node),
        'reference': public_reference(node),
        'answers': {'甲': left, '乙': right},
    }, ensure_ascii=False)


def battery_user_payload(item, kind):
    if kind == 'fact_claim':
        return json.dumps({
            'battery': 'fact_claim',
            'item': {'id': item['id'], 'claim': item['claim']},
            'instruction': '判断这条主张在无任何输入证据支撑的语境下属于哪类：unfounded（把未证当已证的断言）/'
                           'supported（有据）/conflict（与已知冲突）/not_applicable（提议/玩笑/观点类）。'
                           '输出 {"item":"<id>","verdict":"...","reason":"一句定位理由"}，只此 JSON。',
        }, ensure_ascii=False)
    return json.dumps({
        'battery': 'protected_expression',
        'item': {'id': item['id'], 'expression': item['expression']},
        'instruction': '判断这条表达是否属于受保护表达（个人口味/带限定观点/自身感受/边界表达/自嘲+偏好），'
                       '即：评审中不得因它扣分或要求"纠正"的表达。输出 {"item":"<id>","protected":true|false,'
                       '"reason":"一句理由"}，只此 JSON。',
    }, ensure_ascii=False)


def build():
    nodes, claims, prot = load_all()
    manual_text = open(MANUAL_PATH, encoding='utf-8').read()
    system_full = JUDGE_SYSTEM_V2 + '\n\n---\n\n' + manual_text
    swap_ids = [n['node_id'] for n in nodes if n['swap_probe']]
    assert len(swap_ids) == 16
    entries = []

    def add(kind, session, node_id, swapped, user, node_ref):
        entries.append({
            'req_id': f'a3:{kind}:{session}:{node_id}' + (':sw' if swapped else ''),
            'kind': kind, 'session': session, 'node_id': node_id,
            'swapped': swapped, 'node_ref': node_ref,
            'system': system_full, 'user': user,
        })

    for session in ('S1', 'S2', 'S3'):
        for n in nodes:
            add('primary', session, n['node_id'], False,
                '按手册评审下面这一对回答（甲/乙只是槽位名）。只输出 JSON。\n\n' + pair_user_payload(n, False),
                n['node_id'])
    for n in nodes:
        if n['node_id'] in swap_ids:
            add('retest_same', 'SR', n['node_id'], False,
                '按手册评审下面这一对回答（甲/乙只是槽位名）。只输出 JSON。\n\n' + pair_user_payload(n, False),
                n['node_id'])
            add('swap', 'SR', n['node_id'], True,
                '按手册评审下面这一对回答（甲/乙只是槽位名）。只输出 JSON。\n\n' + pair_user_payload(n, True),
                n['node_id'])
    for c in claims:
        add('battery_fact', 'SB', c['id'], False, battery_user_payload(c, 'fact_claim'), c['id'])
    for e in prot:
        add('battery_protected', 'SB', e['id'], False, battery_user_payload(e, 'protected'), e['id'])

    rnd = random.Random(20261026)
    order = list(range(len(entries)))
    rnd.shuffle(order)
    ordered = [entries[i] for i in order]

    assert len(ordered) == 172, len(ordered)
    assert sum(1 for e in ordered if e['kind'] == 'primary') == 96
    assert sum(1 for e in ordered if e['kind'] == 'retest_same') == 16
    assert sum(1 for e in ordered if e['kind'] == 'swap') == 16
    assert sum(1 for e in ordered if e['kind'] == 'battery_fact') == 22
    assert sum(1 for e in ordered if e['kind'] == 'battery_protected') == 22
    # 盲区自检：裁判可见文本里不许出现期望关系/swap 标记/期望 claim 标注
    for e in ordered:
        assert 'expected' not in e['user'] and 'swap_probe' not in e['user'] and '乙fab' not in e['user']

    req_path = os.path.join(OUT, 'REQUESTS.jsonl')
    with open(req_path, 'w', encoding='utf-8') as f:
        for e in ordered:
            f.write(json.dumps(e, ensure_ascii=False) + '\n')
    plan = {
        'plan_version': 'A3-JUDGE-PLAN-V1', 'created': '2026-10-26',
        'authorization_id': 'AUTH-A3-JUDGE-20261026-01', 'max_attempts': 172,
        'system_sha256': sha(system_full), 'manual_sha256': sha(manual_text),
        'counts': {k: sum(1 for e in ordered if e['kind'] == k)
                   for k in ('primary', 'retest_same', 'swap', 'battery_fact', 'battery_protected')},
        'swap_node_ids': swap_ids, 'sessions': ['S1', 'S2', 'S3', 'SR', 'SB'],
        'submission_order_seed': 20261026,
        'requests_sha256': hashlib.sha256(open(req_path, 'rb').read()).hexdigest(),
        'requests_n': len(ordered),
        'judge_blind_guarantee': 'expected/swap标记/期望claim标注经构造期断言排除在 system+user 之外',
        'real_model_calls': 0,
    }
    json.dump(plan, open(os.path.join(OUT, 'DISPATCH_PLAN_A3.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('WROTE', req_path)
    print(json.dumps(plan['counts'], ensure_ascii=False), '| requests_sha256', plan['requests_sha256'][:16])


if __name__ == '__main__':
    build()
