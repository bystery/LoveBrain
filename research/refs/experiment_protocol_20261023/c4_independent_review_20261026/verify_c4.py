"""Independent C4 audit. Writes only in this review directory; no network/model calls."""
import collections
import datetime
import hashlib
import importlib.util
import json
import re
import sys
import subprocess
from pathlib import Path

R=Path('D:/lovebrain-arena'); O=Path(__file__).parent
sys.path.insert(0,str(R/'arena'))
def read(p): return json.loads(Path(p).read_text(encoding='utf-8'))
def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def module(name,p):
    spec=importlib.util.spec_from_file_location(name,p)
    m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m); return m

specs=[('reference_product_v1','c4_sweep_a_orig_vs_goal_live_20261026','parent'),
       ('goal_action_v1','c4_sweep_a_orig_vs_goal_live_20261026','candidate'),
       ('v5_purpose_aligned','c4_sweep_b_v5_vs_mem_live_20261026','parent'),
       ('evidence_memory_v1','c4_sweep_b_v5_vs_mem_live_20261026','candidate')]
stats={}; outputs=[]; seen_ids=[]; case_copies=collections.defaultdict(list)
for name,batch,arm in specs:
    rows=[]
    for cp in sorted((R/'logs'/batch).glob(arm+'_*/E*.copy.json')):
        e=read(cp)
        case_copies[e['episode_id']].append((name,hashlib.sha256(
            json.dumps(e,ensure_ascii=False,sort_keys=True).encode()).hexdigest()))
    for p in sorted((R/'logs'/batch).glob(arm+'_*/out/*/reply_raw.txt')):
        d=p.parent; raw=p.read_text(encoding='utf-8'); obj=json.loads(raw)
        calls=read(d/'calls.json'); mf=read(d/'manifest.json')
        analysis=obj.get('analysis') or {}
        primary=(obj.get('response') or {}).get('recommended')
        backup=[{'slot':'response.'+k,'text':v} for k,v in (obj.get('response') or {}).items()
                if k!='recommended' and isinstance(v,str) and v.strip()]
        backup += [{'slot':'directions[%d]'%i,'text':v}
                   for i,v in enumerate(obj.get('directions') or []) if isinstance(v,str) and v.strip()]
        user=calls[0].get('user',''); notes=(d/'mechanism_notes.txt').read_text(encoding='utf-8')
        store=re.search(r'facts_store=(\d+)',notes); proj=re.search(r'facts_proj=(\d+)',notes)
        cid=d.name.split('_',2)[-1]
        row={'arm':name,'case_id':cid,'dir':str(d),'raw_sha256':sha(p),
             'primary':primary,'backups':backup,'analysis':analysis,
             'prompt_tokens':sum(c['usage']['prompt'] for c in calls),
             'completion_tokens':sum(c['usage']['completion'] for c in calls),
             'veto':mf.get('veto'),'call_count':len(calls),
             'first_usable_ms':(calls[0].get('timing') or {}).get('first_usable_ms'),
             'facts_store':int(store[1]) if store else None,'facts_projection':int(proj[1]) if proj else None,
             'warmth_fact_in_actual_prompt':'共同朋友小林是当初牵线认识的那位' in user,
             'action_rule_in_prompt':'【目标-动作自评与选择】' in user,
             'analysis_empty_required_by_system':'固定输出空对象' in calls[0].get('system','')}
        seen_ids += [c.get('resp_id') for c in calls]
        rows.append(row); outputs.append(row)
    stats[name]={'n':len(rows),'real_calls':sum(x['call_count'] for x in rows),
        'tokens':sum(x['prompt_tokens']+x['completion_tokens'] for x in rows),
        'veto_count':sum(bool(x['veto']) for x in rows),
        'nonempty_backups':sum(len(x['backups']) for x in rows),
        'empty_analysis':sum(not x['analysis'] for x in rows),
        'analysis_key_counts':dict(collections.Counter(','.join(sorted(x['analysis'])) for x in rows)),
        'action_rule_positions':sum(x['action_rule_in_prompt'] for x in rows),
        'facts_reported':sum(len(x['analysis'].get('facts',[])) for x in rows),
        'tentatives_reported':sum(len(x['analysis'].get('tentatives',[])) for x in rows),
        'store_nonempty_positions':sum(bool(x['facts_store']) for x in rows),
        'projection_nonempty_positions':sum(bool(x['facts_projection']) for x in rows),
        'missing_first_usable':sum(x['first_usable_ms'] is None for x in rows)}

out={'snapshot':datetime.datetime.now().isoformat(),'real_model_calls':0,'arm_stats':stats,
     'response_ids_nonempty':sum(bool(x) for x in seen_ids),'response_ids_unique':len(set(seen_ids)),
     'same_episode_inputs_across_arms':{e:len(set(h for _,h in rows))==1 for e,rows in case_copies.items()},
     'E008_T03':[{k:x[k] for k in ['arm','primary','backups','warmth_fact_in_actual_prompt']}
                for x in outputs if x['case_id']=='E008_T03']}
out['budgets']={}
for name in ['AUTH-C4-SCREEN-20261026-01','AUTH-NEWSTORY-96-20261026-01']:
    es=[json.loads(x) for x in (R/'logs/auth_budget'/f'{name}.jsonl').read_text(encoding='utf-8').splitlines() if x.strip()]
    counts=collections.Counter(x['kind'] for x in es)
    starts=[x['attempt_id'] for x in es if x['kind']=='attempt_started']
    out['budgets'][name]={'events':dict(counts),'unique_started':len(set(starts))}

out['frozen_checks']={}
for fn in ['sweep_a_orig_vs_goal.INTERLEAVE_PLAN.json','sweep_b_v5_vs_mem.INTERLEAVE_PLAN.json']:
    d=read(R/'refs/experiment_protocol_20261023/c4_screen_20261026'/fn)
    ex=dict(d['frozen']['config_files'])
    for a in d['frozen']['identity_closure'].values(): ex.update(a['files'])
    out['frozen_checks'][fn]={'checked':len(ex),'mismatches':[
        k for k,v in ex.items() if not (R/k).is_file() or sha(R/k)!=v]}

# Counterexample to the advertised provenance gate, not a new stress-test suite.
m=module('review_c3',R/'candidates/evidence_memory_v1/mechanism/mechanism.py')
out['C3_source_gate_probes']=[]
for src in [[],[999],['m0']]:
    h={'mem':{'facts':{},'tentatives':{}}}
    added=m._merge_reported(h,{'facts':[{'text':'她已订好明晚的包间','src_ids':src}]},
                           {1},'2026-10-26 12:00',{'m0':1})
    out['C3_source_gate_probes'].append({'src':src,'added':added,'stored':h['mem']['facts']})

pack=read(R/'evaluation/C4_FOUR_ARM_REVIEW_PACK_V1.json')
out['worker_pack_backups_n']=dict(collections.Counter(
    str(x.get('backups_n')) for l in pack['loci'] for x in l['arms'].values()))
product=Path('D:/LoveBrain-arena-e')
def git(*args): return subprocess.check_output(['git','-C',str(product),*args],text=True,encoding='utf-8').strip()
out['product']={'worktree_head':git('rev-parse','HEAD'),
    'changed_files':git('diff','--name-only','176e521..HEAD').splitlines(),
    'apk_exists':(product/'app/build/outputs/apk/debug/app-debug.apk').is_file(),
    'apk_bytes':(product/'app/build/outputs/apk/debug/app-debug.apk').stat().st_size,
    'main_head':subprocess.check_output(['git','-C','D:/LoveBrain','rev-parse','HEAD'],text=True).strip()}
(O/'INDEPENDENT_RESULTS.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
(O/'RAW_REVIEW_ROWS.json').write_text(json.dumps(outputs,ensure_ascii=False,indent=2),encoding='utf-8')
parts=['# 四臂首选：来自原始答卷；未知不填判词','']
for cid in sorted(set(x['case_id'] for x in outputs)):
    parts+=['## '+cid]+['- '+x['arm']+': '+str(x['primary']) for x in outputs if x['case_id']==cid]+['']
(O/'PRIMARIES.md').write_text('\n'.join(parts),encoding='utf-8')
print(json.dumps({k:out[k] for k in ['snapshot','arm_stats','same_episode_inputs_across_arms',
    'budgets','worker_pack_backups_n','frozen_checks','C3_source_gate_probes','product']},ensure_ascii=False,indent=2))
