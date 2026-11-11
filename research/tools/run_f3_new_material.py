# -*- coding: utf-8 -*-
r"""tools/run_f3_new_material.py — F3 新材料两臂真实生成编排（4 故事 × 2 臂）。

题面：benchmark/stories_v2/final_20261029/{N01,N02,K01,K02}（已冻结、已校验）。
两臂：reference_product_v1（原版） vs action_policy_r4_guard_final（最终候选）。
调用：dsf / 0.7 / http://127.0.0.1:17770/v1；每轮一次物理请求；失败消耗不退款、不补跑。
长期故事（24 位）在 T12 保存真实 checkpoint，复制到新恢复目录后由新进程
--resume 继续 T13–T24（不重跑前史）。

用法：
  set PYTHONIOENCODING=utf-8
  D:\anaconda\python.exe tools/run_f3_new_material.py --mode mock
  D:\anaconda\python.exe tools/run_f3_new_material.py --mode live --authorization refs/.../F3_AUTHORIZATION_20261029.json
"""
import argparse
import concurrent.futures
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import sys
import threading

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAT = os.path.join(ROOT, 'benchmark', 'stories_v2', 'final_20261029')
STORY_TURNS = {'N01': 12, 'N02': 12, 'K01': 24, 'K02': 24}
ARMS = [('parent', 'reference_product_v1'),
        ('candidate', 'action_policy_r4_guard_final')]
PINNED_ENDPOINT = 'http://127.0.0.1:17770/v1'
PINNED_MODEL = 'dsf'
PINNED_TEMP = 0.7
AID = 'AUTH-F3-NEWMAT-20261029-01'
FREEZE = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                      'final_mainline_review_20261029',
                      'FINAL_CANDIDATE_FREEZE_20261029.json')


def sha_file(p):
    h = hashlib.sha256()
    with open(p, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def dump(p, obj):
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write('\n')


def identity(name):
    sys.path.insert(0, os.path.join(ROOT, 'tools'))
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        'api_trial_f3', os.path.join(ROOT, 'tools', 'api_trial.py'))
    mod = importlib.util.module_from_spec(spec)
    os.environ.setdefault('DSF_API_KEY', '')
    spec.loader.exec_module(mod)
    info = mod.harness_identity(name)
    return info['identity_sha256'], info


def run_proc(ep, cand, out, run_dir, turns, mode, auth_path, log_path,
             stop_after=None, resume=False, expect=None):
    argv = [sys.executable, os.path.join(ROOT, 'tools', 'iso_run.py'),
            '--episode', ep, '--candidate', cand, '--out', out,
            '--run-dir', run_dir,
            '--adapter', 'mock' if mode == 'mock' else 'openai_compat',
            '--quota', str(turns)]
    if mode == 'live':
        argv += ['--authorization', auth_path, '--expect-attempts', str(expect or turns)]
        if stop_after:
            argv += ['--stop-after', str(stop_after)]
    else:
        if stop_after:
            argv += ['--stop-after', str(stop_after)]
    if resume:
        argv += ['--resume']
    env = dict(os.environ)
    env['PYTHONIOENCODING'] = 'utf-8'
    t0 = datetime.datetime.now()
    p = subprocess.run(argv, cwd=ROOT, env=env, capture_output=True,
                       text=True, encoding='utf-8', errors='replace',
                       timeout=turns * 240 + 600)
    with open(log_path, 'a', encoding='utf-8') as f:
        f.write('$ %s\n' % ' '.join(argv))
        f.write('rc=%s started=%s finished=%s\n'
                % (p.returncode, t0.isoformat(timespec='seconds'),
                   datetime.datetime.now().isoformat(timespec='seconds')))
        f.write('--- stdout ---\n%s\n--- stderr ---\n%s\n\n' % (p.stdout, p.stderr))
    data = None
    for line in reversed((p.stdout or '').strip().splitlines()):
        line = line.strip()
        if line.startswith('{') and line.endswith('}'):
            try:
                data = json.loads(line)
                break
            except json.JSONDecodeError:
                continue
    return {'argv': argv, 'rc': p.returncode, 'result': data,
            'log': os.path.relpath(log_path, ROOT).replace('\\', '/')}


def chain(sid, arm_label, cand, out_base, mode, auth_path):
    turns = STORY_TURNS[sid]
    tag = '%s_%s' % (arm_label, sid)
    # 控制器在 run 期间对题面文件做 ACL deny；两臂并发读同一文件会互相拒绝。
    # 用内容逐字一致（sha256 相同）的 per-chain 副本，题面身份不变、并发安全。
    ep_dir = os.path.join(out_base, '_episodes')
    os.makedirs(ep_dir, exist_ok=True)
    ep_src = os.path.join(ROOT, 'benchmark', 'episodes', '%s.json' % sid)
    ep = os.path.join(ep_dir, '%s_%s.json' % (sid, arm_label))
    shutil.copyfile(ep_src, ep)
    ep_sha = sha_file(ep)
    if ep_sha != sha_file(ep_src):
        raise RuntimeError('题面副本哈希不符: %s' % ep)
    out = os.path.join(out_base, tag)
    run_dir = os.path.join(out_base, '_iso_run_' + tag)
    log = os.path.join(out_base, 'CHAIN_%s.log' % tag)
    res = {'story': sid, 'arm': arm_label, 'candidate': cand,
           'turns': turns, 'episode': os.path.relpath(ep, ROOT).replace('\\', '/'),
           'episode_sha256': ep_sha,
           'out': os.path.relpath(out, ROOT).replace('\\', '/'),
           'run_dir': os.path.relpath(run_dir, ROOT).replace('\\', '/'),
           'processes': []}
    if turns > 12:
        # 上半段：T1–T12，真实 checkpoint
        p1 = run_proc(ep, cand, out, run_dir, turns, mode, auth_path, log,
                      stop_after=12)
        res['processes'].append(p1)
        ck = os.path.join(out, 'checkpoint.json')
        if p1['rc'] != 0 or not os.path.isfile(ck):
            res['status'] = 'failed_checkpoint_missing'
            return res
        ckd = load(ck)
        if int(ckd.get('turns_done') or 0) != 12:
            res['status'] = 'failed_checkpoint_turn_mismatch'
            res['checkpoint'] = ckd
            return res
        out_r = out + '_resume'
        run_r = run_dir + '_resume'
        shutil.copytree(out, out_r)
        shutil.copytree(run_dir, run_r)
        res['checkpoint'] = {'turns_done': 12, 'copied_to':
                             os.path.relpath(out_r, ROOT).replace('\\', '/')}
        p2 = run_proc(ep, cand, out_r, run_r, turns, mode, auth_path, log,
                      resume=True, expect=12)
        res['processes'].append(p2)
        res['out_final'] = os.path.relpath(out_r, ROOT).replace('\\', '/')
        res['status'] = 'ok' if (p2['rc'] == 0 and p2['result']
                                 and p2['result'].get('complete')) else 'failed_resume'
        return res
    p = run_proc(ep, cand, out, run_dir, turns, mode, auth_path, log)
    res['processes'].append(p)
    res['out_final'] = res['out']
    res['status'] = 'ok' if (p['rc'] == 0 and p['result']
                             and p['result'].get('complete')) else 'failed_run'
    return res


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--mode', default='mock', choices=('mock', 'live'))
    ap.add_argument('--stories', default='N01,N02,K01,K02')
    ap.add_argument('--arms', default='parent,candidate',
                    help='parent/candidate 子集')
    ap.add_argument('--tag', default=None)
    ap.add_argument('--concurrency', type=int, default=3)
    ap.add_argument('--authorization', default=None)
    ap.add_argument('--aid', default=AID,
                    help='授权 ID（默认 F3 首单；重启批传新单 ID）')
    ap.add_argument('--out-base', default=None)
    a = ap.parse_args(argv)
    aid = a.aid
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    tag = a.tag or datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
    if a.out_base:
        out_base = os.path.abspath(a.out_base)
    else:
        out_base = os.path.join(ROOT, 'logs', 'f3_newmat_%s_%s' % (a.mode, tag))
    if os.path.isdir(out_base) and os.listdir(out_base):
        raise SystemExit('拒绝启动：输出目录已有内容 %s（新批请换 tag）' % out_base)
    os.makedirs(out_base, exist_ok=True)

    sids = [s.strip() for s in a.stories.split(',') if s.strip()]
    arms = [x.strip() for x in a.arms.split(',') if x.strip()]
    arm_map = dict(ARMS)
    unknown = [x for x in arms if x not in arm_map]
    if unknown:
        raise SystemExit('未知 arm: %s' % unknown)
    missing = [s for s in sids if s not in STORY_TURNS]
    if missing:
        raise SystemExit('未知故事: %s' % missing)

    # ---- 冻结绑定与身份断言（派发前硬门） ----
    frozen = load(FREEZE)
    id_parent, info_parent = identity('reference_product_v1')
    id_cand, info_cand = identity('action_policy_r4_guard_final')
    expect = frozen.get('identities') or frozen
    exp_parent = (expect.get('reference_product_v1') or {}).get('identity_sha256') \
        if isinstance(expect, dict) else None
    exp_cand = (expect.get('action_policy_r4_guard_final') or {}).get('identity_sha256') \
        if isinstance(expect, dict) else None
    # 兼容冻结文件结构
    if exp_parent is None:
        exp_parent = ((frozen.get('candidates') or {}).get('reference_product_v1')
                      or {}).get('identity_sha256')
    if exp_cand is None:
        exp_cand = ((frozen.get('candidates') or {}).get('action_policy_r4_guard_final')
                    or {}).get('identity_sha256')
    if exp_parent and id_parent != exp_parent:
        raise SystemExit('原版身份不符：%s != 冻结 %s' % (id_parent, exp_parent))
    if exp_cand and id_cand != exp_cand:
        raise SystemExit('候选身份不符：%s != 冻结 %s' % (id_cand, exp_cand))

    auth_path = None
    auth_doc = None
    if a.mode == 'live':
        if not a.authorization:
            raise SystemExit('live 需要 --authorization')
        auth_path = os.path.abspath(a.authorization)
        auth_doc = load(auth_path)
        if auth_doc.get('authorization_id') != aid:
            raise SystemExit('授权 ID 不符: %r' % auth_doc.get('authorization_id'))
        if os.environ.get('LB_API_BASE', '').rstrip('/') != PINNED_ENDPOINT:
            raise SystemExit('LB_API_BASE 必须是 %s' % PINNED_ENDPOINT)
        if os.environ.get('LB_MODEL') != PINNED_MODEL:
            raise SystemExit('LB_MODEL 必须是 %s' % PINNED_MODEL)
        os.environ.setdefault('LB_API_KEY', 'local')
    else:
        os.environ.setdefault('LB_API_BASE', PINNED_ENDPOINT)
        os.environ.setdefault('LB_MODEL', PINNED_MODEL)

    plan = {
        'kind': 'f3_new_material_generation_plan',
        'mode': a.mode, 'tag': tag,
        'stories': sids, 'turns': {s: STORY_TURNS[s] for s in sids},
        'arms': {x: arm_map[x] for x in arms},
        'positions': sum(STORY_TURNS[s] for s in sids) * len(arms),
        'attempts_expected': sum(STORY_TURNS[s] for s in sids) * len(arms),
        'checkpoint': {s: 12 for s in sids if STORY_TURNS[s] > 12},
        'endpoint': PINNED_ENDPOINT, 'model': PINNED_MODEL,
        'temperature': PINNED_TEMP,
        'api_config_identity': 'api_cfg:openai_compat_stream_false_v1',
        'authorization_id': aid if a.mode == 'live' else None,
        'authorization_file': os.path.relpath(auth_path, ROOT).replace('\\', '/')
        if auth_path else None,
        'identity': {'reference_product_v1': id_parent,
                     'action_policy_r4_guard_final': id_cand},
        'materials_sha256': {
            s: {n: sha_file(os.path.join(MAT, s, n))
                for n in ('episode.json', 'reference.json', 'timeline.json', 'audit.md')}
            for s in sids},
        'cards_sha256': sha_file(os.path.join(
            MAT, 'GRADING_CARDS_N01_N02_K01_K02.json')),
        'created_at': datetime.datetime.now().isoformat(timespec='seconds'),
        'discipline': ('一物理请求一 attempt；失败消耗不退款；不重试、不补跑；'
                       '长期故事 T12 checkpoint 后复制目录，由新进程 resume 生成后段'),
    }
    dump(os.path.join(out_base, 'DISPATCH_PLAN.json'), plan)
    print('PLAN positions=%d arms=%s stories=%s' %
          (plan['positions'], arms, sids))

    results, lock = [], threading.Lock()

    def job(sid, label):
        try:
            r = chain(sid, label, arm_map[label], out_base, a.mode, auth_path)
        except Exception as e:                              # noqa: BLE001
            r = {'story': sid, 'arm': label, 'candidate': arm_map[label],
                 'status': 'exception', 'error': '%s: %s' % (type(e).__name__, e)}
        with lock:
            results.append(r)
            print('CHAIN %s %s -> %s' % (sid, label, r.get('status')))
        return r

    jobs = [(s, l) for s in sids for l in arms]
    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, a.concurrency)) as ex:
        futs = [ex.submit(job, s, l) for s, l in jobs]
        concurrent.futures.wait(futs)

    # ---- 汇总：逐 run 读取 manifest 与轮级产物 ----
    runs = []
    for r in sorted(results, key=lambda x: (x.get('story', ''), x.get('arm', ''))):
        final_out = r.get('out_final')
        summary = {'story': r.get('story'), 'arm': r.get('arm'),
                   'candidate': r.get('candidate'), 'status': r.get('status'),
                   'out': final_out, 'processes': r.get('processes'),
                   'checkpoint': r.get('checkpoint')}
        if final_out:
            mp = os.path.join(ROOT, final_out, 'episode_manifest.json')
            if os.path.isfile(mp):
                m = load(mp)
                summary.update({
                    'complete': m.get('complete'), 'turns': m.get('turns'),
                    'turns_total': m.get('turns_total'),
                    'resumed_from': m.get('resumed_from'),
                    'mean_score10': m.get('mean_score10'),
                    'veto_turns': m.get('veto_turns'),
                    'tokens_total': m.get('tokens_total'),
                    'latency_ms': m.get('latency_ms'),
                    'settlement_pending_turns': m.get('settlement_pending_turns'),
                    'execution_fingerprint': m.get('execution_fingerprint')})
            turn_dirs = sorted(d for d in os.listdir(os.path.join(ROOT, final_out))
                               if d.startswith('turn_'))
            summary['turn_dirs'] = len(turn_dirs)
        runs.append(summary)

    counts = {}
    for r in runs:
        counts[r['status']] = counts.get(r['status'], 0) + 1
    result = {'kind': 'f3_generation_result', 'mode': a.mode, 'tag': tag,
              'out_base': os.path.relpath(out_base, ROOT).replace('\\', '/'),
              'plan': plan, 'runs': runs, 'counts': counts,
              'finished_at': datetime.datetime.now().isoformat(timespec='seconds')}
    dump(os.path.join(out_base, 'RESULT.json'), result)
    dump(os.path.join(out_base, 'DONE.json'),
         {'tag': tag, 'mode': a.mode, 'counts': counts,
          'positions': plan['positions'],
          'finished_at': result['finished_at']})

    if a.mode == 'live':
        try:
            from arena.isolation.authorization_budget import AuthorizationBudget
            b = AuthorizationBudget(os.path.join(ROOT, 'logs', 'auth_budget', aid + '.jsonl'),
                                    aid, auth_doc['real_endpoint']['max_attempts'])
            b.close('F3 new-material generation batch finished')
            print('AUTHORIZATION CLOSED', b.summary())
        except Exception as e:                              # noqa: BLE001
            print('CLOSE FAILED:', e)

    print(json.dumps({'counts': counts, 'out_base': result['out_base']},
                     ensure_ascii=False))
    bad = [r for r in runs if r['status'] != 'ok']
    return 0 if not bad else 3


if __name__ == '__main__':
    sys.exit(main())
