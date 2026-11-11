"""Index repeated recorded inputs; duplicate inputs are candidates for audit, not proof of waste."""
from pathlib import Path
import collections
import hashlib
import json
import os

ROOT=Path(r'D:\lovebrain-arena')
OUT=Path(__file__).resolve().parent
SKIP={'staged','_iso_run','_reports','__pycache__','dist','dataset','b_line','backups',
      'reuse_audit_20261028_1946'}


def compact_hash(obj):
    return hashlib.sha256(json.dumps(obj,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def main():
    observations=[];errors=[];files_read=0;bytes_read=0
    for base in [ROOT/'logs',ROOT/'refs/experiment_protocol_20261023']:
        for directory,dirs,files in os.walk(base,followlinks=False):
            dirs[:]=[n for n in dirs if n not in SKIP and 'acl_locked' not in n.lower()]
            if 'calls.json' not in files:continue
            p=Path(directory)/'calls.json'
            try:
                raw=p.read_bytes();data=json.loads(raw);files_read+=1;bytes_read+=len(raw)
                if isinstance(data,dict):data=data.get('calls',[])
                for x in data:
                    if not isinstance(x,dict) or not isinstance(x.get('system'),str) or not isinstance(x.get('user'),str):continue
                    model=x.get('model') or x.get('resp_model')
                    req=[model,x.get('temperature'),x['system'],x['user'],x.get('images') or []]
                    rid=x.get('resp_id') or x.get('response_id')
                    rawhash=hashlib.sha256(str(x.get('raw_text') or '').encode()).hexdigest()
                    observations.append({'path':str(p),'request_sha256':compact_hash(req),'model':model,
                        'ts':x.get('ts'),'resp_id':rid,'response_sha256':rawhash,
                        'usage':x.get('usage'),'attempt_id':x.get('attempt_id'),
                        'observation_identity':compact_hash([x.get('ts'),rid,rawhash,req])})
            except Exception as exc:errors.append({'path':str(p),'error':str(exc)})
    unique={};copies=collections.Counter()
    for x in observations:
        key=x['observation_identity'];copies[key]+=1
        if key not in unique:unique[key]=x
    groups=collections.defaultdict(list)
    for x in unique.values():groups[x['request_sha256']].append(x)
    repeated=[{'request_sha256':k,'recorded_distinct_responses':len(v),'observations':v}
              for k,v in groups.items() if len(v)>1]
    repeated.sort(key=lambda x:x['recorded_distinct_responses'],reverse=True)
    summaries=[]
    for p in sorted((ROOT/'logs/auth_budget').glob('*.jsonl')):
        data=[]
        try:
            for line in p.read_text(encoding='utf-8').splitlines():
                if line.strip():data.append(json.loads(line))
            counts=dict(collections.Counter(x.get('kind') for x in data))
            scope=next((x for x in data if x.get('kind')=='authorization_scope'),{})
            summaries.append({'path':str(p),'authorization_id':scope.get('authorization_id'),
                'scope':scope.get('scope'),'max_attempts':scope.get('max_attempts'),'events':counts})
        except Exception as exc:errors.append({'path':str(p),'error':str(exc)})
    result={'calls_json_files_read':files_read,'bytes_read':bytes_read,'recorded_response_observations':len(observations),
        'distinct_recorded_observations':len(unique),'copy_observations_removed':len(observations)-len(unique),
        'repeated_exact_input_groups':len(repeated),
        'extra_observations_with_same_input':sum(x['recorded_distinct_responses']-1 for x in repeated),
        'warning':'Not a count of waste or necessarily real dispatches. Planned repeats, new configurations, mock/capture, saved copies and cached responses must be distinguished. No original logs edited.'}
    for name,x in [('REQUEST_INDEX_SUMMARY.json',result),('REPEATED_REQUEST_GROUPS.json',repeated),('BUDGET_SCOPE_INDEX.json',summaries),('REQUEST_READ_ERRORS.json',errors)]:
        (OUT/name).write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False))
    print('top repeated',json.dumps([{'n':x['recorded_distinct_responses'],'paths':[o['path'] for o in x['observations'][:5]]} for x in repeated[:5]],ensure_ascii=False))


if __name__=='__main__':main()
