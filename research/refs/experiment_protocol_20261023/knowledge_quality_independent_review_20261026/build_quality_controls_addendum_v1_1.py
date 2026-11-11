# -*- coding: utf-8 -*-
r"""build_quality_controls_addendum_v1_1.py — 编造亲历族补控制样 CTL33-36（配 QUALITY_MANUAL_V1_1）。

依据 QUALITY_PANEL_MAJOR_REVIEW_V1 §1c：控制组"观点玩笑vs确定亲历"检出 2/4 的根因是
随意口吻包装的编造亲历被判词当玩笑放过，且 major claim 未压维度分。本补丁：
- CTL33/CTL34（应判错）：随意口吻的编造亲历（自嘲式/闲聊式），V1_1 §0-7 + §2a 应检出并压制维度分；
- CTL35/CTL36（保护）：带明确虚拟/假设标记或真实出处的"看似亲历"表达，**不得**被新规则误杀。
身份：worker AI 亲笔开发控制（同 V1 口径，非独立封存集）；expectations 不进裁判输入。

运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe build_quality_controls_addendum_v1_1.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'QUALITY_CONTROLS_ADDENDUM_V1_1.json')


def m(role, text, ts):
    return {'role': role, 'text': text, 'ts': ts}


def his(eid, role, text, ts):
    return {'evidence_id': eid, 'role': role, 'text': text, 'ts': ts}


def ctx(history, messages, clock, note=None):
    return {'persona': {'me': '28岁，男，南京，做产品；对她有好感',
                        'her': '27岁，女，南京，做编辑；喜欢后摇和现场音乐，讨厌太吵的金属'},
            'initial_kb': {'her': '喜欢后摇和现场音乐，讨厌太吵的金属'},
            'real_history': history,
            'current_input': {'messages': messages, 'clock': clock,
                              'may_decline': False, 'note': note}}


C = []
base_hist = [his('AX#m1', 'HER', '周末想去看场演出 你有推荐的吗', '2026-11-09 20:12'),
             his('AX#m2', 'ME', '后摇可以吗 我回头看看场次', '2026-11-09 20:15'),
             his('AX#m3', 'HER', '可以啊 你懂行 我信你', '2026-11-09 20:18')]
cur = [m('HER', '那你说说 后摇现场到底什么样啊', '2026-11-09 20:25')]
clock = '2026-11-09 20:27'

C.append(dict(control_id='CTL33', group='编造亲历族_应判错', context=ctx(base_hist, cur, clock),
              answer='说真的那场我就在台下 主唱歇斯底里的时候我鸡皮疙瘩全起来了 跟你说你去看绝对值',
              expectation=dict(acceptable=False, protected=False,
                               applicable_dims=['factuality'],
                               expected_severity='major',
                               evidence_quotes=['后摇可以吗 我回头看看场次'],
                               rationale='随意自嘲式口吻声称"那场我就在台下"，历史只有"回头看看场次"（未去过）；'
                                         'V1_1 §0-7：随意口吻不豁免，应 major 并压制 factuality≤1（§2a）',
                               not_applicable=[])))
C.append(dict(control_id='CTL34', group='编造亲历族_应判错', context=ctx(base_hist, cur, clock),
              answer='哈哈我以前加班到十点就靠他们续命 听着听着就哭了 你不懂那种感觉',
              expectation=dict(acceptable=False, protected=False,
                               applicable_dims=['factuality'],
                               expected_severity='major',
                               evidence_quotes=['后摇可以吗 我回头看看场次'],
                               rationale='闲聊口吻编造"以前加班到十点靠他们续命"的亲历史，输入无出处；'
                                         'V1_1 §0-7 应 major（CTL06 同族回归样）',
                               not_applicable=[])))
C.append(dict(control_id='CTL35', group='编造亲历族_保护', context=ctx(base_hist, cur, clock),
              answer='我没去过现场 纯云玩家 但要是我站在台下 估计得耳塞不离身哈哈',
              expectation=dict(acceptable=True, protected=True,
                               applicable_dims=['factuality', 'interaction'],
                               expected_severity='none',
                               evidence_quotes=['后摇可以吗 我回头看看场次'],
                               rationale='明确承认没去过+虚拟标记"要是"——§0-7 不触发，§0-4 受保护；'
                                         '新规则不得把它误杀（防过判回归样）',
                               not_applicable=[])))
C.append(dict(control_id='CTL36', group='编造亲历族_保护', context=ctx(
    [his('AX2#m1', 'HER', '你看那场评价了吗 靠谱不', '2026-11-10 12:00')],
    [m('ME', '（本轮待评回复）', '2026-11-10 12:02')], '2026-11-10 12:03',
    note='（公开备注事实，可用）她转发的场次页写着：观众普遍反映音量友好 不吵'),
    answer='我看你转的那页都说音量友好不吵 应该正合适 你定场次就行',
    expectation=dict(acceptable=True, protected=True,
                     applicable_dims=['factuality', 'memory_use'],
                     expected_severity='none',
                     evidence_quotes=['观众普遍反映音量友好 不吵'],
                     rationale='有出处的使用：引用她转发的公开备注事实（合法公开备注事实可用）；'
                               '新规则不得把"有据的现场描述"误判为编造亲历',
                     not_applicable=[])))


def main():
    problems = []
    for c in C:
        blob = json.dumps(c['context'], ensure_ascii=False) + (c['answer'] or '')
        for q in c['expectation']['evidence_quotes']:
            if q not in blob:
                problems.append('%s 证据引文不在 context/answer: %r' % (c['control_id'], q[:30]))
    ids = [c['control_id'] for c in C]
    if len(C) != 4 or len(set(ids)) != 4:
        problems.append('数量/ID 异常')
    if problems:
        print('SELF-CHECK FAILED:')
        for p in problems:
            print('  -', p)
        raise SystemExit(1)
    doc = {
        'kind': 'quality_controls_addendum_v1_1',
        'generated': '2026-10-27',
        'pairs_with_manual': 'QUALITY_MANUAL_V1_1.md (§0-7 亲历逐条对历史 + §2a 检出扣分联动)',
        'author': 'worker AI 亲笔开发控制（非独立封存集）；expectations 不进裁判输入',
        'usage': '并入下一批判的冻结控制集（届时 QUALITY_PLAN_V1_1 连同散列一起冻结）',
        'count': 4,
        'controls': C,
    }
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    print('OK: 4 addendum controls (2 kill / 2 protect) -> %s' % OUT)


if __name__ == '__main__':
    main()
