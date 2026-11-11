"""Reuse current scheduler for slim/new-data capture; no real calls."""
import hashlib
import importlib.util
import json
from pathlib import Path
import socket
import sys
import urllib.request

ROOT=Path('D:/lovebrain-arena')
OUT=Path(__file__).resolve().parent
sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'arena')]
def blocked(*a,**kw): raise RuntimeError('Independent review: network disabled')
socket.socket.connect=blocked
urllib.request.urlopen=blocked
def load(rel,name):
    spec=importlib.util.spec_from_file_location(name,ROOT/rel)
    mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
    return mod

ws=load('tools/build_review_worksheet.py','worksheet_branches')
fixture=OUT/'worksheet_branches'
for i,raw in [(1,'this is not JSON'),(2,'{"action":"no_reply","reason":"明确道别"}')]:
    d=fixture/'E777'/f'turn_{i:02d}_E777_T{i:02d}'
    d.mkdir(parents=True,exist_ok=True)
    mf={'case_id':f'E777_T{i:02d}','run_id':'OFFLINE-FIXTURE','case_input_sha256':'fixture'}
    (d/'manifest.json').write_text(json.dumps(mf),encoding='utf-8')
    (d/'scoring.json').write_text(json.dumps({'score10':0 if i==1 else 10,
        'veto':'json_schema_broken' if i==1 else None,'checks':[]}),encoding='utf-8')
    (d/'calls.json').write_text(json.dumps([{'tag':'main','raw_text':raw}],ensure_ascii=False),encoding='utf-8')
rows,_=ws.worksheet_from_exam(str(fixture),{},'fixture')
supp={'real_model_calls':0,'worksheet_synthetic_branches':{
    'input_turns':2,'rows_returned':len(rows),
    'covered_cases':sorted({r['case_id'] for r in rows}),
    'note':'Malformed reply and legal no_reply both need a visible turn record'}}

ep=load('tools/exam_interleave_plan.py','v3_config_capture')
before={rel:hashlib.sha256((ROOT/rel).read_bytes()).hexdigest() for rel in [
    'tools/exam_interleave_plan.py','tools/build_review_worksheet.py']}
# Controlled in-memory configuration proof, not a production configuration API.
ep.ARM_MAP={'parent':'reference_product_v1','candidate':'v3_system_slim'}
ep.EVIDENCE_ORDER=[('E004','parent_first'),('E005','candidate_first'),('E006','parent_first')]
swap={f'benchmark/episodes/E00{i}.json':f'benchmark/episodes/E00{i+3}.json' for i in (1,2,3)}
swap['benchmark/dev_pilot_v02/03_GRADING_CARDS.json']='benchmark/dev_expansion_v01/03_GRADING_CARDS_E004_E006.json'
ep.FROZEN_EXTRA_FILES=[swap.get(p,p) for p in ep.FROZEN_EXTRA_FILES]
plan=ep.emit_plan(str(OUT/'V3_NORMALIZED_CAPTURE_PLAN.json'))
result=ep.execute_plan(plan,mode='capture',out_base=str(OUT/'v3_config_capture'),keep_tmp=True)
supp['v3_configuration_probe']={k:result[k] for k in [
    'attempt_started','attempt_completed','attempt_failed',
    'execution_order_verified','dispatch_bijection','state_isolated','real_model_calls']}
supp['v3_configuration_probe']['candidate']=ep.ARM_MAP['candidate']
supp['v3_configuration_probe']['episodes']=[e for e,_ in ep.EVIDENCE_ORDER]
supp['v3_configuration_probe']['scope']='In-memory configuration / fake replies only; no semantic result or production-ready CLI claim'
supp['source_unchanged']=all(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==h for p,h in before.items())
(OUT/'FOLLOWUP_CHECKS.json').write_text(json.dumps(supp,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(supp,ensure_ascii=False))
