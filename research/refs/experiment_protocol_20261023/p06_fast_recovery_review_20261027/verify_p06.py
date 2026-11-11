"""Read-only independent P06 recomputation; own output only, zero model calls."""
import collections
import copy
import hashlib
import importlib.util
import json
import subprocess
import sys
from pathlib import Path

ROOT=Path('D:/lovebrain-arena')
OUT=Path(__file__).resolve().parent
PK=ROOT/'refs/experiment_protocol_20261023/p06_judge_gem_20261027'
SOURCE=ROOT/'logs/p06_judge_live_gemrun'

def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def lines(p):return [json.loads(l) for l in p.read_text(encoding='utf-8').splitlines() if l.strip()]
def write(p,d):p.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')

def main():
    dest=OUT/'LATEST_GEMINI_REPAIRED'
    dest.mkdir(exist_ok=True)
    spec=importlib.util.spec_from_file_location('null_slot_repair',ROOT/'tools/repair_p06_nullslots.py')
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
    nodes={n['_meta']['node_id']:n for n in lines(PK/'P06_NODES.jsonl')}
    raw=[];repaired=[]
    for kind in ('primary','control','repeat'):
        records=lines(SOURCE/('RATINGS_'+kind+'.jsonl'))
        raw.extend(records)
        rows=[]
        for r in records:
            x=copy.deepcopy(r)
            node=nodes.get(x['item_id'])
            if node and not(x.get('transport_ok') and x.get('parse_ok') and x.get('schema_ok')):
                ok,dropped=mod.tolerant_assess(x,node)
                if ok:
                    x['schema_ok']=True;x['parsed']=json.loads(x['raw'])
                    x['repaired_null_slots']=dropped
                    repaired.append(x['item_id'])
            rows.append(x)
        (dest/('RATINGS_'+kind+'.jsonl')).write_text(''.join(json.dumps(x,ensure_ascii=False)+'\n' for x in rows),encoding='utf-8')
    for name in ['DISPATCH_PLAN.json','JUDGE_BATCH_SUMMARY.json']:
        if (SOURCE/name).exists():(dest/name).write_bytes((SOURCE/name).read_bytes())
    res=subprocess.run([sys.executable,'-X','utf8',str(ROOT/'tools/analyze_p06_judge.py'),
                        '--ratings',str(dest),'--packet',str(PK),'--out',str(OUT/'LATEST_GEMINI_ANALYSIS.json')],
                        cwd=ROOT,capture_output=True,text=True,encoding='utf-8')
    assert res.returncode==0,res.stderr[-1000:]
    result=read(OUT/'LATEST_GEMINI_ANALYSIS.json')
    stats=[];bad=[]
    for batch in 'ABC':
        for role in ('parent','candidate'):
            count=0;veto=[];valid_json=0
            for p in sorted((ROOT/'logs'/('p06_'+batch+'_live_20261027')).glob(role+'_*/out/turn_*/manifest.json')):
                m=read(p);count+=1
                rp=p.parent/'reply_raw.txt'
                try:read(rp);valid_json+=1
                except Exception:pass
                if m.get('veto'):
                    item={'batch':batch,'arm':role,'case_id':m['case_id'],'veto':m['veto'],'path':str(p.parent),
                          'raw_sha256':hashlib.sha256(rp.read_bytes()).hexdigest()}
                    veto.append(item);bad.append(item)
            assert count==32,(batch,role,count)
            stats.append({'batch':batch,'arm':role,'generated':count,'protocol_vetoes':len(veto),'valid_json':valid_json,
                          'veto_rows':veto})
    pooled=[]
    for p in dest.glob('RATINGS_*.jsonl'):pooled.extend(lines(p))
    assessed=[x for x in pooled if x.get('transport_ok') and x.get('parse_ok') and x.get('schema_ok')]
    majors=[]
    for r in assessed:
        if r.get('kind')!='primary':continue
        for slot,part in [('primary',r['parsed'].get('primary',{}))]+[(b.get('slot'),b) for b in r['parsed'].get('backups',[])]:
            for c in part.get('claims',[]):
                if c.get('severity') in ('major','critical'):
                    majors.append({'node_id':r['item_id'],'slot':slot,'claim':c})
    ledgers=[]
    for p in sorted((ROOT/'logs/auth_budget').glob('AUTH-P06*.jsonl')):
        a=lines(p);cnt=collections.Counter(x.get('kind') for x in a)
        started={x['attempt_id'] for x in a if x.get('kind')=='attempt_started'}
        terminal={x['attempt_id'] for x in a if x.get('kind') in ('attempt_completed','attempt_failed')}
        ledgers.append({'name':p.name,'counts':dict(cnt),'unresolved_attempts':sorted(started-terminal),
                        'closed':bool(cnt['authorization_closed'])})
    old=read(ROOT/'evaluation/P06_QUALITY_RESULTS.json')
    summary={'generated_answers':192,'raw_judge_records':len(raw),'assessed_after_existing_null_slot_repair':len(assessed),
             'assessed_by_kind':dict(collections.Counter(x['kind'] for x in assessed)),
             'null_slot_repaired_ids':repaired,
             'published_result_records':old['records'],'published_result_assessed':old['assessed'],
             'raw_primary_coverage_denominator':192,'semantic_packet_nodes_total':len(nodes),
             'semantic_primary_nodes':sum(n['_meta']['node_kind']=='primary' for n in nodes.values()),
             'semantic_repeat_nodes':sum(n['_meta']['node_kind']=='repeat' for n in nodes.values()),
             'one_unparseable_generation_excluded_from_semantic_packet':sum(n['_meta']['node_kind']=='primary' for n in nodes.values())==191,
             'protocol':stats,'major_or_critical_claims_primary':sum(x['slot']=='primary' for x in majors),
             'major_or_critical_claims_backups':sum(x['slot']!='primary' for x in majors),
             'major_details':majors,'decision_flags_as_existing_analyzer':result['decision_flags'],
             'important_analyzer_limit':'strict_usable increments on no claims marked major, WITHOUT requiring protocol acceptance or even a goal/quality pass threshold; do not label task success.',
             'ledger_audit':ledgers,'model_calls_this_audit':0}
    write(OUT/'INDEPENDENT_RESULTS.json',summary)
    write(OUT/'PROTOCOL_FAILURES.json',bad)
    print(json.dumps({k:v for k,v in summary.items() if k in ['generated_answers','raw_judge_records','assessed_after_existing_null_slot_repair','assessed_by_kind','published_result_records','published_result_assessed','major_or_critical_claims_primary','major_or_critical_claims_backups']},ensure_ascii=False,indent=2))
    print('PROTOCOL',[(x['batch'],x['arm'],x['protocol_vetoes']) for x in stats])

if __name__=='__main__':main()
