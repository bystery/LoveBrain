from pathlib import Path
import collections
import hashlib
import json

ROOT=Path(r'D:\lovebrain-arena')
OUT=Path(__file__).resolve().parent


def main():
    rows=[]
    for a,b in zip(sorted((ROOT/'runs/g11_real_nca').glob('turn*/calls.json')),
                   sorted((ROOT/'runs/g11_real_nca2').glob('turn*/calls.json'))):
        x=json.loads(a.read_text(encoding='utf-8'))[0];y=json.loads(b.read_text(encoding='utf-8'))[0]
        keys=['system','user','temperature','images','model','resp_model']
        rows.append({'case':a.parent.name,'first':str(a),'second':str(b),
            'same_request_fields':{k:x.get(k)==y.get(k) for k in keys},
            'same_output':x.get('raw_text')==y.get('raw_text')})
    w=ROOT/'logs/p07_w09_20261028_a'
    ev=[json.loads(s) for s in (w/'EVENTS.jsonl').read_text(encoding='utf-8').splitlines() if s.strip()]
    groups=collections.defaultdict(collections.Counter)
    for x in ev:
        tid=x.get('traj') or ''
        if '__fb' not in tid or '__r' not in tid:continue
        cell=tid.rsplit('__r',1)[0]
        groups[cell][x.get('event')]+=1
    findings={'g11_pairs':rows,'g11_same_request_pairs':sum(all(x['same_request_fields'].values()) for x in rows),
        'g11_known_reason':'After a reporting/authorization evidence-path edit changed the frozen hash, six entire real generations were reissued. Source incident record explicitly confirms12/8 exceeded. This is avoidable engineering rerun, not a new harness experiment.',
        'w09_by_cell_events':{k:dict(v) for k,v in groups.items()},
        'w09_current_complete_pairs':0,
        'caution':'W09 errors, planned repeats and known overwrite waste must not be blindly added as one exact wasted-call figure.'}
    (OUT/'KNOWN_REPEAT_CHECKS.json').write_text(json.dumps(findings,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'g11_exact_same_request_pairs':findings['g11_same_request_pairs'],
                       'w09_cells':len(groups),'new_real_model_calls':0}))


if __name__=='__main__':main()
