"""Independent bounded ABC audit. No paid calls or product source edits."""
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import socket
import sys
import urllib.request

ROOT = Path('D:/lovebrain-arena')
OUT = Path(__file__).resolve().parent
sys.path[:0] = [str(ROOT/'tools'), str(ROOT/'arena')]

def offline(*args, **kwargs):
    raise RuntimeError('Independent audit: real network disabled')

urllib.request.urlopen = offline
socket.socket.connect = offline

def load(rel, name):
    spec = importlib.util.spec_from_file_location(name, ROOT/rel)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod

watched = [
    'tools/exam_interleave_plan.py', 'tools/exam_layered_score.py',
    'candidates/v2_recall/mechanism/mechanism.py',
    'candidates/reference_product_v1/mechanism/mechanism.py',
    'arena/lb_scorer.py', 'benchmark/annotations/exam1_20261023_semantic.json']
def hashes():
    return {p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in watched}

report = {'real_model_calls':0, 'before':hashes(), 'checks':[], 'probes':{}}
for rel, name, arg in [
    ('tools/test_annotation_binding.py','annotation_abc',None),
    ('tools/test_v2_recall.py','recall_abc','candidate')]:
    mod=load(rel,name)
    candidate = load('candidates/v2_recall/mechanism/mechanism.py','v2_audit') if arg else None
    for fn in mod.CHECKS:
        try:
            with contextlib.redirect_stdout(io.StringIO()):
                detail = fn(candidate) if arg else fn()
            report['checks'].append({'check':fn.__name__,'pass':True,'detail':detail})
        except Exception as exc:
            report['checks'].append({'check':fn.__name__,'pass':False,'error':repr(exc)})

inter=load('tools/exam_interleave_plan.py','interleave_abc_audit')
plan=inter.emit_plan(str(OUT/'INTERLEAVE_PLAN.json'))
for label, mutate in [
    ('out_of_order_predecessor', lambda p:p['attempts'].__setitem__(slice(2,6,2),
        [p['attempts'][4],p['attempts'][2]])),
    ('incorrect_pair_key', lambda p:p['attempts'][2].update(pair_key='E001_T01')),
    ('missing_case_input_hash', None)]:
    if mutate is None:
        layer=load('tools/exam_layered_score.py','layer_abc_probe')
        sha=lambda s:hashlib.sha256(s.encode()).hexdigest()
        ann={'case_id':'X','slot':'recommended','raw_reply_sha256':sha('raw'),
             'slot_text_sha256':sha('text'),'label':'pass','run_id':'RUN',
             'harness_identity_sha256':'H','case_input_sha256':'INPUT'}
        doc={'_index':{('X','recommended'):[ann]}}
        hit=layer._match_annotation(doc,'X','recommended',sha('raw'),sha('text'),
            context={'run_id':'RUN','harness_identity_sha256':'H','case_input_sha256':None})
        report['probes'][label]={'accepted':hit is not None}
        continue
    changed=copy.deepcopy(plan)
    mutate(changed)
    try:
        inter.validate_plan(changed['attempts'])
        report['probes'][label]={'accepted':True}
    except Exception as exc:
        report['probes'][label]={'accepted':False,'error':repr(exc)}

try:
    inter.execute_plan(plan,mode='live')
    report['probes']['live_entry']={'rejected':False}
except Exception as exc:
    report['probes']['live_entry']={'rejected':True,'error':repr(exc),
        'authorization_parameter_present':
            'authorization' in __import__('inspect').signature(inter.execute_plan).parameters}

from isolation.trusted_controller import IsolatedRun
original=IsolatedRun.run_episode
invocations=[]
def observe(self,episode_path,*args,**kwargs):
    invocations.append({'candidate':self.candidate,'episode':Path(episode_path).stem,
                        'stop_after':kwargs.get('stop_after'),
                        'resume':kwargs.get('resume')})
    return original(self,episode_path,*args,**kwargs)
IsolatedRun.run_episode=observe
try:
    report['capture']=inter.execute_plan(plan,mode='capture',
        out_base=str(OUT/'capture'),keep_tmp=True)
finally:
    IsolatedRun.run_episode=original
report['actual_run_episode_invocations']=invocations
actual=[]
for call in invocations:
    arm='parent' if call['candidate']=='reference_product_v1' else 'candidate'
    for n in range(1,9):
        actual.append([arm,f"{call['episode']}_T{n:02d}"])
expected=[[r['arm'],r['case_id']] for r in plan['attempts']]
report['planned_first_10']=expected[:10]
report['actual_first_10']=actual[:10]
report['planned_order_matches_execution']=actual==expected
report['after']=hashes()
report['watched_source_unchanged']=report['before']==report['after']
report['checks_passed']=sum(c['pass'] for c in report['checks'])
report['checks_total']=len(report['checks'])
(OUT/'CHECKS.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'checks':[report['checks_passed'],report['checks_total']],
    'ledger':report['capture']['ledger_kinds'],
    'planned_order_matches_execution':report['planned_order_matches_execution'],
    'probes':report['probes'],'watched_source_unchanged':report['watched_source_unchanged']},ensure_ascii=False))
