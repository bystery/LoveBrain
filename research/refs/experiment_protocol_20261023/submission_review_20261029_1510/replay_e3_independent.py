"""Replay the paid F3 model raw through frozen E3; no regenerated answers."""
import importlib.util,json,sys
from pathlib import Path
ROOT=Path(r'D:\lovebrain-arena');OUT=Path(__file__).parent
BASE=ROOT/'refs/experiment_protocol_20261023/final_mainline_review_20261029'
sp=importlib.util.spec_from_file_location('submission_e3_guard',str(ROOT/'candidates/action_policy_r4_guard_e3/mechanism/contract_guard.py'))
guard=importlib.util.module_from_spec(sp);sp.loader.exec_module(guard)
bindings=json.loads((BASE/'F3_PRIVATE_BINDINGS.json').read_text(encoding='utf-8'))
rows=[]
for unit,b in bindings.items():
    td=ROOT/b['turn_dir']['candidate'];call=json.loads((td/'calls.json').read_text(encoding='utf-8'))[0]
    old=(td/'reply_raw.txt').read_text(encoding='utf-8');raw=call['raw_text']
    fixed,meta,obj=guard.repair_and_guard(raw,call['user'])
    try:old_obj=json.loads(old)
    except ValueError:old_obj=None
    row={'unit':unit,'old_parseable':old_obj is not None,'new_parseable':obj is not None,'same_object':old_obj==obj,'same_bytes':old==fixed,'new_primary':(obj or {}).get('response',{}).get('recommended'),'repair_log':meta if old_obj!=obj else None}
    rows.append(row)
changed=[r for r in rows if not r['same_object']]
doc={'pairs':len(rows),'unchanged_objects':len(rows)-len(changed),'changed':changed,'new_unparseable':sum(not r['new_parseable'] for r in rows),'claim':'Offline derived E3 replay on historical model raw; not 72 new live E3 outputs and not new chat quality gains.'}
(OUT/'E3_INDEPENDENT_REPLAY.json').write_text(json.dumps(doc,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in doc.items() if k!='changed'},ensure_ascii=False))
print('changed units', [r['unit'] for r in changed])
assert len(rows)==72 and len(changed)==1 and changed[0]['unit']=='F3:N01_T05'
assert doc['new_unparseable']==0
