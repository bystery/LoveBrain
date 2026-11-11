# -*- coding: utf-8 -*-
"""tools/test_controlled_branches_p1.py — B5 受控分支的离线验证（零网络、确定性）。

验证对象：benchmark/positive_capability_20261025/03_CONTROLLED_BRANCHES_P1.json
执行书 B5 口径：对方有自己的兴趣/顾虑/时间安排，依候选实际行为改变反馈，含
'不感兴趣/需要缓一缓'路径；与固定回放分开标；先离线验证分支与证据；
动态模型模拟只能称'模拟效度'。
"""
import json
import os
import re
import sys
from datetime import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(ROOT, 'benchmark', 'positive_capability_20261025')
EPS = os.path.join(D, '01_EPISODES_P1.json')
CARDS = os.path.join(D, '02_GRADING_CARDS_P1.json')
BR = os.path.join(D, '03_CONTROLLED_BRANCHES_P1.json')

RESULTS = []


def check(name, cond, detail=''):
    RESULTS.append((name, bool(cond), detail))
    print('%s %s %s' % ('PASS' if cond else 'FAIL', name, detail))


def ts(s):
    return datetime.strptime(s, '%Y-%m-%d %H:%M')


# ---------- 谓词求值器（与分支规范同款语义） ----------
def count_q(s):
    return len(re.findall(r'[？?]', s or ''))


def eval_pred(p, text):
    t = p['type']
    if t == 'question_count_ge':
        return count_q(text) >= p['n']
    if t == 'question_count_eq':
        return count_q(text) == p['n']
    if t == 'len_gt':
        return len(text or '') > p['n']
    if t == 'len_le':
        return len(text or '') <= p['n']
    if t == 'contains_any':
        return any(k in (text or '') for k in p['items'])
    raise ValueError('unknown predicate type: %s' % t)


def eval_when(when, text):
    if 'any' in when:
        return any(eval_pred(p, text) for p in when['any'])
    return all(eval_pred(p, text) for p in when.get('all', []))


def route(branch, text):
    for pr in sorted(branch['predicates'], key=lambda x: x['priority']):
        if eval_when(pr['when'], text):
            return pr['outcome_id']
    return branch['default_outcome']


def main():
    eps = {e['episode_id']: e for e in json.load(open(EPS, encoding='utf-8'))}
    cards = json.load(open(CARDS, encoding='utf-8'))
    br = json.load(open(BR, encoding='utf-8'))

    # 1. 分离标注：与固定回放分开
    check('V1 01/02 题面文件不含受控分支标记',
          ('CONTROLLED_BRANCH' not in open(EPS, encoding='utf-8').read()
           and 'CONTROLLED_BRANCH' not in open(CARDS, encoding='utf-8').read()))
    check('V1 分支文件 kind=CONTROLLED_BRANCHES', br['kind'] == 'CONTROLLED_BRANCHES')
    check('V1 每个分支 marked_as=controlled_branch',
          all(b['marked_as'] == 'controlled_branch' for b in br['branches']))
    check('V1 b5_note 声明模拟效度边界', '模拟效度' in br['b5_note'])

    # 2. 结构：trigger 在题面内、outcome 字段齐、分支 ID 唯一
    bids = [b['branch_id'] for b in br['branches']]
    check('V2 branch_id 唯一', len(set(bids)) == len(bids))
    case_ids = {t['case_id'] for e in eps.values() for t in e['turns']}
    ok_trig, ok_out = True, True
    for b in br['branches']:
        if b['trigger_case_id'] not in case_ids:
            ok_trig = False
            print('  缺 trigger:', b['branch_id'], b['trigger_case_id'])
        if not b.get('outcomes') or not b.get('default_outcome'):
            ok_out = False
        for oid, o in (b.get('outcomes') or {}).items():
            for f in ('label', 'path_type', 'her_reply', 'ts', 'state_effect', 'evidence'):
                if not o.get(f):
                    ok_out = False
                    print('  outcome 缺字段:', oid, f)
            if o.get('evidence') and 'P0' not in o['evidence'] and 'MANUAL' not in o['evidence'] and '她人设' not in o['evidence'] and '固定回放' not in o['evidence']:
                ok_out = False
                print('  outcome 证据不含题面/手册锚点:', oid)
    check('V2 trigger_case_id 全部存在于题面', ok_trig)
    check('V2 outcome 字段齐且带证据锚点', ok_out)

    # 3. 必备路径：不感兴趣 + 需要缓一缓
    req = br['required_paths_check']
    for sid in ('P001', 'P002'):
        for k in ('not_interested', 'needs_slow_down'):
            oid = req[sid][k]
            found = any(oid in b['outcomes'] and b['outcomes'][oid]['path_type'] == k
                        for b in br['branches'])
            check('V3 %s 含 %s 路径(%s)' % (sid, k, oid), found)

    # 4. 时间线：outcome.ts > trigger 轮 clock；对齐固定回放者与 01 文件逐字一致
    def turn_clock(eid, case_id):
        for t in eps[eid]['turns']:
            if t['case_id'] == case_id:
                return ts(t['input']['clock']), t
        return None, None

    def her_text_of(case_id):
        for eid in eps:
            for t in eps[eid]['turns']:
                if t['case_id'] == case_id:
                    for m in t['input']['messages']:
                        if m['role'] == 'HER':
                            return m['text'], m['ts']
        return None, None

    ok_t, ok_align = True, True
    for b in br['branches']:
        eid = b['branch_id'].split('-')[0]
        clk, _ = turn_clock(eid, b['trigger_case_id'])
        for oid, o in b['outcomes'].items():
            if ts(o['ts']) <= clk:
                ok_t = False
                print('  时间线不单调:', oid, o['ts'], '<=', clk)
            if o.get('path_type') == 'aligns_fixed_replay':
                ftxt, fts = her_text_of(o['fixed_replay_case_id'])
                if ftxt != o['her_reply'] or fts != o['ts']:
                    ok_align = False
                    print('  与固定回放不一致:', oid)
    check('V4 全部 outcome.ts 严格晚于 trigger clock', ok_t)
    check('V4 aligns_fixed_replay 与 01 文件逐字一致（文本+ts）', ok_align)

    # 5. 谓词路由：代表性候选输出 → 预期路径
    routing = [
        ('P001-BP1', '你周末有空吗？要不要一起骑？还想问你诊所忙不忙？', 'P001-BP1-C1'),
        ('P001-BP1', '顶不住就早点回', 'P001-BP1-C3'),
        ('P001-BP1', '那你之前说想骑车的事儿，还作数吗？', 'P001-BP1-C2'),
        ('P001-BP2', '那就这么定了 周六一起骑 你肯定要来啊', 'P001-BP2-D1'),
        ('P001-BP2', '不急 你看我那天状态 定不了的', 'P001-BP2-D2'),
        ('P002-BP1', '到底去不去嘛 说定了啊', 'P002-BP1-C1'),
        ('P002-BP1', '行 那有合适的再说', 'P002-BP1-C2'),
        ('P002-BP2', '搬什么公司 我来 我还是要去帮你搬', 'P002-BP2-D1'),
        ('P002-BP2', '行吧 算了', 'P002-BP2-D3'),
        ('P002-BP2', '哈哈好 那就等师傅来', 'P002-BP2-D2'),
        ('P004-BP1', '到底给不给看 不发就不理你了', 'P004-BP1-C1'),
        ('P004-BP1', '你这是怕留下笑柄啊 哈哈', 'P004-BP1-C2'),
        ('P004-BP2', '那算了 你是不是也想啊 别嘴硬了嘛', 'P004-BP2-D1'),
        ('P004-BP2', '想多的是你 我又不催你', 'P004-BP2-D2'),
        ('P006-BP1', '对啊我常跟我爸去 你忘了', 'P006-BP1-C1'),
        ('P006-BP1', '我钓鱼其实很少 你记岔啦哈哈', 'P006-BP1-C2'),
        ('P006-BP2', '那我陪你去 就约周六我去接你', 'P006-BP2-D1'),
        ('P006-BP2', '发呆也挺好 不用赶', 'P006-BP2-D2'),
    ]
    bmap = {b['branch_id']: b for b in br['branches']}
    for bid, text, want in routing:
        got = route(bmap[bid], text)
        check('V5 路由 %s: "%s…" → %s' % (bid, text[:12], want), got == want, 'got=%s' % got)

    # 6. 事实守卫：她的回复不得捏造事实/不得替候选承诺/不得出现确定约定
    banned_all = ['已经答应', '说好了你一定', '你就答应了吧']
    story_banned = {
        'P001': ['半马跑完了', '已经约好骑车', '周六就骑'],
        'P002': ['你已经答应帮我搬', '二人局', '单独约会'],
        'P004': ['那我们在一起吧', '你其实喜欢我', '二人约'],
        'P006': ['那就当你答应了', '房我都看好', '我们已经约好去湖边'],
    }
    # 时间窗按故事推导（题面轮次 ts 的最小/最大值 ±1 天）
    story_window = {}
    for eid, e in eps.items():
        all_ts = [m['ts'] for t in e['turns'] for m in t['input']['messages']]
        day = lambda s: s[:10]
        story_window[eid] = (ts(min(all_ts)[:10] + ' 00:00'), ts(max(all_ts)[:10] + ' 23:59'))
    ok_f = True
    for b in br['branches']:
        eid = b['branch_id'].split('-')[0]
        lo, hi = story_window[eid]
        for oid, o in b['outcomes'].items():
            hr = o['her_reply']
            if any(x in hr for x in banned_all + story_banned.get(eid, [])):
                ok_f = False
                print('  事实守卫违规:', oid, hr)
            if not (lo <= ts(o['ts']) <= hi):
                ok_f = False
                print('  时间线出窗:', oid, o['ts'], '窗=', lo, hi)
    check('V6 她的回复无捏造事实/无替候选承诺/时间在故事窗内', ok_f)

    # 7. 谓词覆盖：每个分支的 default 与全部 outcome 都能被路由到达
    ok_cov = True
    for b in br['branches']:
        reached = set()
        for oid, o in b['outcomes'].items():
            # 用 outcome 自身的 her_reply 之类不一定触发；用枚举法：每个 outcome 至少有一条路由用例可达
            for bid2, text, want in routing:
                if want == oid and b['branch_id'] == bid2:
                    reached.add(oid)
        if b['default_outcome'] not in reached:
            # default 可由"全不中"到达：构造空文本验证
            if route(b, '') == b['default_outcome']:
                reached.add(b['default_outcome'])
        missing = set(b['outcomes']) - reached
        if missing:
            ok_cov = False
            print('  不可达 outcome:', b['branch_id'], missing)
    check('V7 每个 outcome 都有可达路由（含 default 由空输入到达）', ok_cov)

    n_fail = sum(1 for _, ok, _ in RESULTS if not ok)
    print('\nSUMMARY: %d checks, %d failed' % (len(RESULTS), n_fail))
    sys.exit(1 if n_fail else 0)


if __name__ == '__main__':
    main()
