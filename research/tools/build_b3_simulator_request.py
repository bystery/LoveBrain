# -*- coding: utf-8 -*-
"""tools/build_b3_simulator_request.py — B3 对方模拟器请求构造（零网络零生成）。

纪律落点：
- 模拟器盲于臂：system/user 里绝不出现臂名/版本/token/判词——本构造器根本不接收臂参数（类型层面保证）；
- 语义动作驱动：semantic_rules 以"行为方向参考"注入（方向性描述，不是硬谓词——长度/问号奖惩在 scenarios.json
  discipline 里明令禁止，本构造器不含任何长度/关键词计算）；
- 状态输出协议固定（reply/state_delta/internal/end_dialog）；reply 可为空=本轮 wait（不强行产 HER）；
- 每轮请求=全新无历史会话（她不跨轮记忆"系统"，只记对话内容本身——history 全文注入）；
- V3 B3/B7 增补：显式缺席事件通道（absence_notes——"对方没发新消息"是系统事件不是聊天文本，
  绝不伪造成对话行）；scenario_obj 直传（避免静态查表脆弱性）；extra_rules 反馈条件覆盖。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SC = os.path.join(ROOT, 'benchmark', 'interaction_env_20261026', 'scenarios.json')

SYSTEM_TMPL = """你在扮演一位真实的人。你不知道对面是谁、是什么系统、什么版本、花了多少成本——
你只是一个在微信上聊天的人。不要出戏、不要解释自己是模拟器、不要提"测试/评分/系统"。

你的人设与处境：
{persona_block}

行为方向参考（不是死规则，按你的人物逻辑自然执行；回复长度、标点、关键词从来不是你的判据，
你只看对方这条消息**实际做了什么语义动作**）：
{rules_block}

规矩：
1) 你有自己的日程和安排，不会为对方随意改动；约定必须落成双方都确认的具体安排才算成。
2) 你可以拒绝、可以冷、可以主动结束对话；不需要配合对方推进。
3) 对方没发新消息就是没发——你不知道、也不能假装收到过任何不存在的话；你可以等、可以自己开口、也可以不回（reply 留空）。
4) 每轮只输出严格 JSON（无解释无 markdown）：
{{"reply":"你说的话（本轮不想说话就留空字符串）","state_delta":{{"情绪":-2..2,"信任":-2..2,"约定":-1..1,"边界":-2..2}},
 "internal":"一句你的私有想法（对面看不到，用于审计）","end_dialog":false|true}}
"""


def build(scenario_id, history, scenario_obj=None, extra_rules=None, absence_notes=None):
    """history: [{'who':'her'|'me','text':...}, ...]（含开场消息）。
    scenario_obj: 直接传场景对象（跳过静态查表）；extra_rules: 反馈条件覆盖规则（"条件 ⇒ 行为"或整句）；
    absence_notes: 本轮系统事件（如"对方未发送新消息"），以事件区注入，绝不进对话行。"""
    if scenario_obj is not None:
        sc = scenario_obj
        if sc.get('id') != scenario_id:
            raise ValueError('scenario_obj.id(%r) != scenario_id(%r)' % (sc.get('id'), scenario_id))
    else:
        scs = json.load(open(SC, encoding='utf-8'))['scenarios']
        sc = next(s for s in scs if s['id'] == scenario_id)
    her = sc['her']
    persona_block = (
        '身份：%s\n私有动机（你不会主动说出，但影响你的行为）：\n%s\n你的日程安排：%s\n'
        '你的边界：%s' % (
            her['persona'],
            '\n'.join('- ' + m for m in her['private_motives']),
            '；'.join(her['schedule']),
            '；'.join(her.get('boundaries') or ['无特别红线，按人物逻辑'])))
    rules = list(sc['semantic_rules'])
    for r in (extra_rules or []):
        if not r:
            continue
        if '⇒' in r:
            when, then = r.split('⇒', 1)
            rules.append({'when': when.strip(), 'then': then.strip()})
        else:
            rules.append({'when': '（当前反馈条件下）', 'then': r.strip()})
    rules_block = '\n'.join(
        '- 若对方%s ⇒ %s' % (r['when'], r['then']) for r in rules)
    system = SYSTEM_TMPL.format(persona_block=persona_block, rules_block=rules_block)
    # 环境补丁 v2.2（2026-10-28 根因诊断 W09_SIM_HANG_ROOT_CAUSE.json）：上游 qwen3.8-flash
    # 对历史文本中的双破折号"——"存在生成挂死缺陷（位置相关，实测某 44 字消息 150s 无输出，
    # 同消息替换单破折号后 7.3s 正常返回）。仅对嵌入 sim 请求的历史文本做"——"→"—"归一，
    # 不改任何候选产文与评分；双臂同规则。
    transcript = '\n'.join(
        ('她' if h['who'] == 'her' else '对方') + '：' + (h['text'] or '').replace('——', '—')
        for h in history)
    event_lines = ''
    if absence_notes:
        event_lines = ('\n\n本轮系统事件（记录事实，不是任何人的聊天内容）：\n'
                       + '\n'.join('- ' + n for n in absence_notes) + '\n')
    user = ('这是你们的聊天记录（最新在最后）。' + event_lines
            + '现在轮到你。只输出 JSON。\n\n' + transcript + '\n\n对方：')
    # 盲于臂断言：请求两段文本里不出现任何臂/系统标识词
    for bad in ('arm', 'v5', 'reference_product', 'candidate', 'token', 'judge', '改进版'):
        assert bad not in system and bad not in user, '臂泄漏: ' + bad
    return {'scenario': scenario_id, 'system': system, 'user': user,
            'absence_notes': list(absence_notes or []),
            'extra_rules': list(extra_rules or [])}


if __name__ == '__main__':
    req = build('I2', [{'who': 'her', 'text': '夜班第三天，我感觉自己已经半只脚进入贤者模式了，你说句话让我笑一下'},
                       {'who': 'me', 'text': '夜班冠军，你这不叫贤者模式，这叫省电模式'}])
    print('== I2 sample ==')
    print(req['system'][:600])
    print('...\n== user tail ==')
    print(req['user'][-200:])
