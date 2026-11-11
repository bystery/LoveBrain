"""Incremental zero-model-call review. Original artifacts are read-only."""
from pathlib import Path
import collections
import contextlib
import hashlib
import importlib.util
import io
import json
import os
import socket
import sys

ROOT=Path(r'D:\lovebrain-arena')
OUT=Path(__file__).resolve().parent
BASE=ROOT/'refs/experiment_protocol_20261023/s1_action_live_20261028'
sys.path[:0]=[str(ROOT),str(ROOT/'tools'),str(ROOT/'arena')]
os.environ['PYTHONDONTWRITEBYTECODE']='1'


def module(path,name):
    s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m


def denied(*a,**kw):raise RuntimeError('network disabled during independent review')
socket.socket.connect=denied
socket.create_connection=denied


def main():
    report={'new_model_calls':0,'batches':[],'core_action_cases':[]}
    observed=set();pairs=0;rawfiles=set();aggregate=module(ROOT/'tools/aggregate_s1_scorecard.py','final_delta_aggregate')
    for b in ['S1','S2','S2R','S3','D1','R3A','R3B']:
        sk=json.loads((BASE/f'{b}_SCORE_SKELETON.json').read_text(encoding='utf-8'))
        sc=json.loads((BASE/f'{b}_SCORECARD.json').read_text(encoding='utf-8'))
        led=ROOT/f'logs/auth_budget/AUTH-P08-{b}-20261028-01.jsonl'
        events=[json.loads(x) for x in led.read_text(encoding='utf-8').splitlines() if x.strip()]
        ctr=collections.Counter(x.get('kind') for x in events)
        args=['--skeleton',str(BASE/f'{b}_SCORE_SKELETON.json'),'--scores',str(BASE/f'{b}_WORKER_SCORES.py'),
              '--binding',str(BASE/f'{b}_SCORE_BINDING.json'),'--ledger',str(led),'--label',b,
              '--out-json',str(OUT/f'{b}_INDEPENDENT_AGGREGATE.json'),
              '--out-md',str(OUT/f'{b}_INDEPENDENT_AGGREGATE.md')]
        with contextlib.redirect_stdout(io.StringIO()):rc=aggregate.main(args)
        n=json.loads((OUT/f'{b}_INDEPENDENT_AGGREGATE.json').read_text(encoding='utf-8'))
        report['batches'].append({'batch':b,'ledger':dict(ctr),'aggregate_rc':rc,
            'binding':n.get('answer_binding'),'paired':n['paired_position_verdicts'],
            'paired_same_as_worker':n['paired_position_verdicts']==sc['paired_position_verdicts'],
            'unjudged':n['coverage']['unjudged_positions'],
            'dimension_keys':list(next(iter(n['per_dimension_mean'].values())).keys())})
        pairs+=len(sk['positions'])
        for p in sk['positions']:
            observed.add(p['case_id'])
            for arm,v in p['arms'].items():
                if p['case_id'] in ['D01_T08','D02_T06','D02_T08'] and b in ['D1','R3A']:
                    try:parsed=json.loads(v.get('raw_text') or '{}')
                    except Exception:parsed={}
                    report['core_action_cases'].append({'batch':b,'case':p['case_id'],'arm':arm,
                          'primary':v.get('primary'),'action':parsed.get('action'),'decline_reason':parsed.get('decline_reason')})
    guard=module(ROOT/'tools/test_score_binding_guard.py','final_delta_binding_guard')
    guard.EVIDENCE=str(OUT/'BINDING_GUARD_INDEPENDENT.json')
    with contextlib.redirect_stdout(io.StringIO()):report['binding_guard_rc']=guard.main()
    parity=module(ROOT/'tools/test_output_repair_parity.py','final_delta_repair_parity')
    original_open=io.open
    def redirected(file,*args,**kw):
        target=ROOT/'refs/experiment_protocol_20261023/deadline_quality_20261028/OUTPUT_REPAIR_PARITY_20261028.json'
        try:match=Path(file).resolve()==target.resolve()
        except (ValueError,TypeError):match=False
        return original_open(OUT/'OUTPUT_REPAIR_PARITY_INDEPENDENT.json' if match else file,*args,**kw)
    io.open=redirected
    try:
        with contextlib.redirect_stdout(io.StringIO()):report['output_parity_rc']=parity.main()
    finally:io.open=original_open
    report['output_parity']=json.loads((OUT/'OUTPUT_REPAIR_PARITY_INDEPENDENT.json').read_text(encoding='utf-8'))['summary']
    report['paired_observations']=pairs
    report['unique_case_ids']=len(observed)
    report['scored_pairs']=sum(b['paired']['n'] for b in report['batches'])
    report['excluded_pairs']=sum(len(b['unjudged']) for b in report['batches'])
    report['total_starts']=sum(b['ledger'].get('attempt_started',0) for b in report['batches'])
    report['total_completed']=sum(b['ledger'].get('attempt_completed',0) for b in report['batches'])
    report['sources']={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in
          [ROOT/'tools/aggregate_s1_scorecard.py',ROOT/'tools/output_repair.py',ROOT/'tools/normalize_reply_contract.py',
           ROOT/'candidates/action_policy_r2/mechanism/mechanism.py',ROOT/'candidates/action_policy_r3/mechanism/mechanism.py']}
    (OUT/'INDEPENDENT_RESULTS.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ['new_model_calls','paired_observations','unique_case_ids','scored_pairs',
         'excluded_pairs','total_starts','total_completed','binding_guard_rc','output_parity_rc','output_parity']},ensure_ascii=False))


if __name__=='__main__':main()
