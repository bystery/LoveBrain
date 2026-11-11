# -*- coding: utf-8 -*-
r"""tools/build_f4_probes.py — 生成 F4 组件诊断的分支题面（零调用）。

知识通道：K01/K02 在真实 T24 checkpoint 之后追加 T25 探针（早先事实已离开
原始历史窗口）；行动层：N01/N02 在真实 T12 checkpoint 之后追加 T13 探针
（正向/含糊/忙碌各一条）。每题只真实生成被追加的那一轮，前史由 checkpoint
恢复，不重烧。episode_id 沿用原故事以匹配 checkpoint 身份。
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAT = os.path.join(ROOT, 'benchmark', 'stories_v2', 'final_20261029')
OUT = os.path.join(MAT, 'f4_probes')

KNOW = [
    {'probe_id': 'K01_P1_leave', 'story': 'K01', 'clock': '2026-11-07 20:10',
     'message': '我年假最后到几号来着 17号要上班吗',
     'current_action': '她问年假最后到几号。已确认年假到16号，17号要值班上班；回答应给16号、17号上班。',
     'acceptable_actions': ['说年假到16号，17号上班', '确认16号结束，17号值班'],
     'atoms': [{'atom_id': 'K01_P1_a1', 'subject': 'her', 'relation': 'leave_final',
                'value': '年假到16号，17号要上班', 'valid_time': '2026-11-16',
                'status': 'active', 'negation': False,
                'source': {'kind': 'message', 'case_id': 'K01_T15', 'message_id': 'm1',
                           'quote': '批了13到16 之前说15是记错了'}, 'required_this_turn': True}],
     'protected': ['批了13到16 之前说15是记错了'],
     'forbidden': ['说年假到17号', '说17号不用上班'],
     'guidance': '正确回指16号结束、17号上班；不要复活更早的13到17。'},
    {'probe_id': 'K01_P2_refund', 'story': 'K01', 'clock': '2026-11-07 20:12',
     'message': '客栈退款截止还是10号吧',
     'current_action': '她问客栈退款截止。已确认改为11月10号，旧8号作废；回答应确认10号。',
     'acceptable_actions': ['确认10号，8号是旧版本', '说10号前可退，房型仍三晚'],
     'atoms': [{'atom_id': 'K01_P2_a1', 'subject': 'her', 'relation': 'refund_deadline',
                'value': '退款截止11月10号，旧8号作废', 'valid_time': '2026-11-10',
                'status': 'active', 'negation': False,
                'source': {'kind': 'message', 'case_id': 'K01_T09', 'message_id': 'm1',
                           'quote': '退款截止改成11月10号'}, 'required_this_turn': True}],
     'protected': ['退款截止改成11月10号'],
     'forbidden': ['说8号前可退是现行', '说房型变了'],
     'guidance': '正确回指10号；8号是旧版本。'},
    {'probe_id': 'K01_P3_rail', 'story': 'K01', 'clock': '2026-11-07 20:14',
     'message': '高铁是1号开售的对吧 D字头',
     'current_action': '她确认高铁开售日与车次。已确认11月1号开售，想坐D字头、时间别太早；回答应确认1号和D字头。',
     'acceptable_actions': ['确认1号开售，D字头', '说1号就开了，按D字头挑不太早的'],
     'atoms': [{'atom_id': 'K01_P3_a1', 'subject': 'her', 'relation': 'ticket_sale',
                'value': '高铁票11月1号开售，想坐D字头，时间别太早', 'valid_time': '2026-11-01',
                'status': 'active', 'negation': False,
                'source': {'kind': 'message', 'case_id': 'K01_T07', 'message_id': 'm0',
                           'quote': '高铁票11月1号开售'}, 'required_this_turn': True}],
     'protected': ['高铁票11月1号开售'],
     'forbidden': ['说错开售日期', '把D字头说成G字头'],
     'guidance': '正确回指1号开售和D字头偏好。'},
    {'probe_id': 'K02_P1_budget', 'story': 'K02', 'clock': '2026-05-23 20:10',
     'message': '八万加三千又减两千 现在到底多少',
     'current_action': '她问预算加减后的当前总价。已确认八万加三千减两千，当前八万一千；回答应给81000。',
     'acceptable_actions': ['说现在八万一千', '八万加三千减两千，合计八万一'],
     'atoms': [{'atom_id': 'K02_P1_a1', 'subject': 'her', 'relation': 'budget_current',
                'value': '八万加三千减两千，当前八万一千', 'valid_time': '2026-05-19',
                'status': 'active', 'negation': False,
                'source': {'kind': 'message', 'case_id': 'K02_T11', 'message_id': 'm1',
                           'quote': '说减两千 工期补回来'}, 'required_this_turn': True}],
     'protected': ['说减两千 工期补回来'],
     'forbidden': ['说还是八万', '漏掉减两千或加三千'],
     'guidance': '正确回指当前81000。'},
    {'probe_id': 'K02_P2_tile', 'story': 'K02', 'clock': '2026-05-23 20:12',
     'message': '瓷砖是8号到的吧 不是5号',
     'current_action': '她确认瓷砖到货日期。已确认改到11月8号，5号来不及；回答应确认8号。',
     'acceptable_actions': ['确认8号，5号来不及', '说8号到，旧5号作废'],
     'atoms': [{'atom_id': 'K02_P2_a1', 'subject': 'her', 'relation': 'tile_date',
                'value': '瓷砖改到11月8号到，5号来不及', 'valid_time': '2026-11-08',
                'status': 'active', 'negation': False,
                'source': {'kind': 'message', 'case_id': 'K02_T06', 'message_id': 'm0',
                           'quote': '瓷砖改到11月8号到'}, 'required_this_turn': True}],
     'protected': ['瓷砖改到11月8号到'],
     'forbidden': ['说5号到是现行', '说日期没定'],
     'guidance': '正确回指8号；5号是旧版本。'},
    {'probe_id': 'K02_P3_measure', 'story': 'K02', 'clock': '2026-05-23 20:14',
     'message': '橱柜测量是11号吧 9号取消了',
     'current_action': '她确认橱柜测量日期。已确认从11月9号取消，改到11月11号；回答应确认11号。',
     'acceptable_actions': ['确认11号，9号取消', '说改到11号，老周会来'],
     'atoms': [{'atom_id': 'K02_P3_a1', 'subject': 'her', 'relation': 'measure_date',
                'value': '测量从11月9号取消，改到11月11号', 'valid_time': '2026-11-11',
                'status': 'active', 'negation': False,
                'source': {'kind': 'message', 'case_id': 'K02_T09', 'message_id': 'm1',
                           'quote': '改到11月11号'}, 'required_this_turn': True}],
     'protected': ['改到11月11号'],
     'forbidden': ['说9号是现行', '说测量取消了不来了'],
     'guidance': '正确回指11号；9号已取消。'},
]

ACTION = [
    {'probe_id': 'N01_A1_positive', 'story': 'N01', 'clock': '2026-03-10 20:00',
     'message': '今天药房提前下班 心情挺好 想出去走走',
     'current_action': '她给出积极信号，想低负担地出去走走。应接住情绪并给具体轻提议，不把提议说成已定。',
     'acceptable_actions': ['接住开心，提一个近处轻活动并给选择', '调侃一句再问她想走路还是坐坐'],
     'guidance': '积极信号：可以推进一小步，但保留她的选择。', 'may_decline': False},
    {'probe_id': 'N01_A2_ambiguous', 'story': 'N01', 'clock': '2026-03-10 20:02',
     'message': '周末可能空 也可能要加班 还没定',
     'current_action': '她给出含糊/可能信号。应不逼定，给轻选择或留口子。',
     'acceptable_actions': ['不逼定，等她确认再安排', '给两个轻选项，让她有空再挑'],
     'guidance': '含糊信号不得当已答应；不催。', 'may_decline': True},
    {'probe_id': 'N01_A3_busy', 'story': 'N01', 'clock': '2026-03-10 20:04',
     'message': '这周排班满 回得慢 你别介意',
     'current_action': '她明确忙碌、回得慢。应轻接、不催不追问；短回或安静收住都可以。',
     'acceptable_actions': ['说没事，等她忙完再聊', '短接一句，不追加问题'],
     'guidance': '忙碌信号：不推进、不索取回复。', 'may_decline': True},
    {'probe_id': 'N02_A1_positive', 'story': 'N02', 'clock': '2026-04-20 20:00',
     'message': '年糕新主人发照片来了 今天心情挺好',
     'current_action': '她分享年糕的好消息、情绪积极。应接住并轻回，可问一句照片细节。',
     'acceptable_actions': ['接住开心，问一句年糕近况', '调侃一句，表达替她高兴'],
     'guidance': '积极分享：接情绪、给轻钩子。', 'may_decline': False},
    {'probe_id': 'N02_A2_ambiguous', 'story': 'N02', 'clock': '2026-04-20 20:02',
     'message': '周六也许能见 但要看学校安排',
     'current_action': '她给出含糊的见面可能。应不逼定，给轻选择或等确认。',
     'acceptable_actions': ['不逼定，等学校安排出来再说', '给两个轻选项，让她方便时挑'],
     'guidance': '可能不等于答应；保留空间。', 'may_decline': True},
    {'probe_id': 'N02_A3_busy', 'story': 'N02', 'clock': '2026-04-20 20:04',
     'message': '这周改作业多 回得慢',
     'current_action': '她明确忙碌、回得慢。应轻接、不催；短回或安静收住都可以。',
     'acceptable_actions': ['说没事，先忙', '短接一句，不追加问题'],
     'guidance': '忙碌信号：不索取回复。', 'may_decline': True},
]


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def dump(p, obj):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write('\n')


def main():
    if os.path.isdir(OUT):
        raise SystemExit('refuse to overwrite existing probes: %s' % OUT)
    probes = []
    for channel, specs, prefix in (('knowledge', KNOW, 24), ('action', ACTION, 12)):
        for s in specs:
            ep = load(os.path.join(ROOT, 'benchmark', 'episodes', '%s.json' % s['story']))
            if len(ep['turns']) < prefix:
                raise SystemExit('%s has < %d turns' % (s['story'], prefix))
            turns = [json.loads(json.dumps(t, ensure_ascii=False))
                     for t in ep['turns'][:prefix]]
            clock = s['clock']
            msg = {'role': 'HER', 'text': s['message'], 'ts': clock}
            inp = {'clock': clock, 'may_decline': bool(s.get('may_decline', False)),
                   'intent': {'text': s['current_action'], 'status': 'ACTIVE',
                              'enabled': True},
                   'messages': [msg]}
            probe_turn = {'case_id': s['probe_id'], 'turn_type': 'reply',
                          'input': inp, 'gold': {}}
            turns.append(probe_turn)
            ep2 = {'episode_id': s['story'], 'persona': ep['persona'],
                   'initial_kb': ep['initial_kb'], 'turns': turns}
            d = os.path.join(OUT, s['probe_id'])
            dump(os.path.join(d, 'episode.json'), ep2)
            atom = s.get('atoms') or []
            card = {'case_id': s['probe_id'], 'current_action': s['current_action'],
                    'acceptable_actions': s['acceptable_actions'],
                    'applicable_dimensions': ['goal', 'factuality', 'attribution',
                                              'memory_use', 'interaction']
                    if channel == 'knowledge' else
                    ['goal', 'interaction', 'initiative', 'emotion_relation',
                     'boundary'],
                    'required_memory_atoms': atom,
                    'private_guidance': s['guidance'],
                    'unknowns': [], 'protected_expressions': s.get('protected') or [],
                    'forbidden_factual_claims': s.get('forbidden') or [],
                    'may_decline': bool(s.get('may_decline', False)),
                    'intent': inp['intent']}
            dump(os.path.join(d, 'reference.json'),
                 {'story_id': s['story'], 'turns': {s['probe_id']: card}})
            source_out = ('logs/f3_newmat_live_restart_20261029/candidate_%s_resume'
                          % s['story'] if channel == 'knowledge'
                          else 'logs/f3_newmat_live_restart_20261029/candidate_%s'
                          % s['story'])
            source_run = ('logs/f3_newmat_live_restart_20261029/_iso_run_candidate_%s_resume'
                          % s['story'] if channel == 'knowledge'
                          else 'logs/f3_newmat_live_restart_20261029/_iso_run_candidate_%s'
                          % s['story'])
            probes.append({'probe_id': s['probe_id'], 'story': s['story'],
                           'channel': channel, 'clock': clock,
                           'message': s['message'], 'prefix_turns': prefix,
                           'source_out': source_out, 'source_run': source_run,
                           'episode': os.path.relpath(os.path.join(d, 'episode.json'),
                                                      ROOT).replace('\\', '/')})
    dump(os.path.join(OUT, 'PROBE_MANIFEST.json'),
         {'kind': 'f4_single_turn_probes', 'count': len(probes),
          'knowledge': sum(1 for p in probes if p['channel'] == 'knowledge'),
          'action': sum(1 for p in probes if p['channel'] == 'action'),
          'probes': probes,
          'note': ('诊断材料，不进入 F3 72 对主表；知识探针在 T24 真实 checkpoint 后追加 T25，'
                   '行动探针在 T12 checkpoint 后追加 T13，episode_id 沿用原故事。')})
    print('probes=%d knowledge=%d action=%d' % (
        len(probes), sum(1 for p in probes if p['channel'] == 'knowledge'),
        sum(1 for p in probes if p['channel'] == 'action')))
    return 0


if __name__ == '__main__':
    sys.exit(main())
