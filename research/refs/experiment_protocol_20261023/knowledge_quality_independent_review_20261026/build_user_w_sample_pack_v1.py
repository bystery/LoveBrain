# -*- coding: utf-8 -*-
r"""build_user_w_sample_pack_v1.py — 给用户的 8 组愿发（W）匿名可选样本（WORKER_NEXT_MAINLINE §最终交付：
"本轮个人口味仅给用户 8 组信息完整、匿名、易判的可选样本"）。

设计：
- 8 个案例 = 4 故事 × 2，覆盖情绪承接/顺势推进/回扣不编造/事实来源/主体确认/邀约切换/认领用力过猛/订位边界；
- 每案例展示：匿名 persona 摘要 + 真实历史 + 她的最新消息 + 4 条匿名回复（甲/乙/丙/丁 = 四臂首选，种子洗牌）；
- 用户可答：选一条愿发 / 多选 / 都不想发（或想不回）——全部合法；
- **密封**：选项字母→臂 的映射存 `USER_W_SAMPLE_BINDINGS_V1.json`（含种子），公开展示包内零臂名；
- 回收前 W 仍=unknown；本包不阻塞任何其他工作，也不代用户打分。

运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe build_user_w_sample_pack_v1.py
"""
import json
import os
import random

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(HERE))))
OUT_MD = os.path.join(HERE, '..', '..', '..', 'evaluation', 'USER_W_SAMPLE_PACK_V1.md')
OUT_BIND = os.path.join(HERE, '..', '..', '..', 'evaluation', 'USER_W_SAMPLE_BINDINGS_V1.json')

CASES = ['E006_T02', 'E007_T02', 'E007_T03', 'E008_T03', 'E008_T06', 'E012_T03', 'E012_T05', 'E012_T07']
LABELS = ['甲', '乙', '丙', '丁']
SEED = 20261027

PERSONA_BRIEF = {
    'E006': '上海，认识多年的朋友，她最近工作不顺（今天方案被甲方当众批评）',
    'E007': '南京，你对她有好感；她喜欢后摇和现场音乐、讨厌太吵，最近加班多',
    'E008': '武汉，经共同朋友小林牵线认识；她做老师，常提妹妹',
    'E012': '成都，认识两个月、线下见过一次；她做设计，慢热、反感被安排但吃幽默',
}


def visible_dialogue(n):
    lines, seen = [], set()
    items = list(n['evidence']['real_history']) + list(n['evidence']['current_input']['messages'])
    for h in items:
        key = (h.get('role'), h.get('text'), h.get('ts'))
        if key in seen:                      # 当前轮消息会同时在 real_history 与 current_input，去重
            continue
        seen.add(key)
        who = '她' if h['role'] == 'HER' else '你'
        lines.append('- %s：%s' % (who, h.get('text', '')))
    return '\n'.join(lines)


def main():
    nodes = {n['node_id']: n for n in
             (json.loads(l) for l in open(os.path.join(HERE, 'QUALITY_NODES.jsonl'), encoding='utf-8')
              if l.strip())}
    bindings = json.load(open(os.path.join(HERE, 'PRIVATE_BINDINGS.json'), encoding='utf-8'))
    by_case_arm = {}
    for b in bindings:
        by_case_arm.setdefault(b['case_id'], {})[b['arm']] = b['node_id']
    ARMS = ['reference_product_v1', 'v5_purpose_aligned', 'goal_action_v1', 'evidence_memory_v1']

    md = []
    md.append('# USER_W_SAMPLE_PACK_V1 — 愿发率可选样本（8 组，匿名，可选不答）\n')
    md.append('日期：2026-10-27。性质：**个人口味样本，回答完全可选**；未回收时所有版本的 W（愿发）')
    md.append('维度保持 unknown，不阻塞任何其他评估。\n')
    md.append('## 怎么答（30 秒/组也行）\n')
    md.append('每组给一段对话现场和 4 条候选回复（甲/乙/丙/丁，顺序随机）。请按"**你会原样发给对方吗**"')
    md.append('选：一条 / 多条 / 都不想发（或这轮你宁可不回）。不用解释理由，也可以顺口写一句为什么。\n')

    bind = {'kind': 'user_w_sample_bindings_v1', 'seed': SEED, 'generated': '2026-10-27',
            'note': '密封映射：仅供回收后还原臂名；不得与公开展示包一同分发',
            'samples': []}

    for i, case_id in enumerate(CASES):
        story = case_id.split('_')[0]
        arm_nodes = by_case_arm[case_id]
        opts = []
        for arm in ARMS:
            n = nodes[arm_nodes[arm]]
            opts.append({'arm': arm, 'node_id': arm_nodes[arm],
                         'text': (n['answer']['primary'] or '').strip()})
        rnd = random.Random('%s|%s' % (SEED, case_id))
        order = list(range(4))
        rnd.shuffle(order)
        shuffled = [opts[j] for j in order]
        n0 = nodes[arm_nodes[ARMS[0]]]
        md.append('---\n')
        md.append('## 组 %d／8 · %s\n' % (i + 1, story))
        md.append('【关系背景】%s\n' % PERSONA_BRIEF[story])
        md.append('【对话到目前】\n%s\n' % visible_dialogue(n0))
        md.append('【候选回复】')
        letters = []
        for j, o in enumerate(shuffled):
            md.append('- **%s**：%s' % (LABELS[j], o['text'].replace('\n', ' ')))
            letters.append({'label': LABELS[j], 'arm': o['arm'], 'node_id': o['node_id']})
        md.append('\n你的选择：________（例：乙 / 甲丁 / 都不想发）\n')
        bind['samples'].append({'sample_index': i + 1, 'case_id': case_id, 'options': letters})

    assert len(bind['samples']) == 8
    # 包内零臂名自检
    blob = '\n'.join(md)
    for arm in ARMS:
        assert arm not in blob, 'arm name leaked: %s' % arm
    with open(OUT_MD, 'w', encoding='utf-8') as f:
        f.write('\n'.join(md) + '\n')
    with open(OUT_BIND, 'w', encoding='utf-8') as f:
        json.dump(bind, f, ensure_ascii=False, indent=1)
    print('OK: pack (8 cases x 4 anonymized options, zero arm names) -> %s' % OUT_MD)
    print('OK: sealed bindings -> %s' % OUT_BIND)


if __name__ == '__main__':
    main()
