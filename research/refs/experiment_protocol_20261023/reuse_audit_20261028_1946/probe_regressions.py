"""Zero-network probes for functionality already covered by older reusable components."""
from pathlib import Path
import contextlib
import importlib.util
import io
import json
import socket
import sys

ROOT=Path(r'D:\lovebrain-arena')
OUT=Path(__file__).resolve().parent
sys.path[:0]=[str(ROOT),str(ROOT/'tools'),str(ROOT/'arena')]


def block(*args,**kwargs):raise RuntimeError('network forbidden during independent audit')
socket.socket.connect=block
socket.create_connection=block


def load(path,name):
    spec=importlib.util.spec_from_file_location(name,path)
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod);return mod


def main():
    import normalize_reply_contract as old
    import output_repair as new
    tail='用于验证完整保留的额外方向内容'*30
    alias='用于验证完整保留的别名冲突内容'*30
    obj={'response':{'recommended':'普通回复','bad_boy':'已有选项','bad_boys':alias,'playful':None,'warm':None},
         'directions':['1','2','3','4',tail]}
    raw=json.dumps(obj,ensure_ascii=False)
    a=old.normalize_raw(raw);b,loss,text=new.repair(raw)
    loss_serialized=json.dumps(loss,ensure_ascii=False)
    result={'new_model_calls':0,'network_forbidden':True,'normalizer_comparison':{
        'old_full_alias_in_audit':alias in json.dumps(a,ensure_ascii=False),
        'new_full_alias_in_repair_result':alias in json.dumps([b,loss],ensure_ascii=False),
        'old_full_extra_direction_in_audit':tail in json.dumps(a,ensure_ascii=False),
        'new_full_extra_direction_in_repair_result':tail in json.dumps([b,loss],ensure_ascii=False),
        'raw_still_retained_separately':True,
        'new_logs':['R1_duplicate_canon_key_dropped','R2_directions_overflow'],
        'interpretation':'The newer parallel repair API truncates its loss audit to80/120 characters where the existing normalizer preserves full nonempty text. Original raw outside it survives. New R3 brace repair is distinct useful functionality and could be composed.'}}
    d=ROOT/'refs/experiment_protocol_20261023/s1_action_live_20261028'
    sk=json.loads((d/'S1_SCORE_SKELETON.json').read_text(encoding='utf-8'))
    pos=next(p for p in sk['positions'] if 'factuality' in (p.get('card_dims') or []))
    cand=list(sk['arms'].values())[1]
    item=pos['arms'][cand]
    before=item.get('primary');item['primary']='我已经替你付款了，也替你答应了，别再改。'
    item['raw_text']=json.dumps({'response':{'recommended':item['primary']},'directions':[None]*4},ensure_ascii=False)
    item['raw_reply_sha256']='0'*64
    item['case_input_sha256']='0'*64
    mutated=OUT/'MUTATED_S1_SKELETON_FOR_PROBE.json'
    mutated.write_text(json.dumps(sk,ensure_ascii=False),encoding='utf-8')
    agg=load(ROOT/'tools/aggregate_s1_scorecard.py','independent_aggregate_probe')
    args=['--skeleton',str(mutated),'--scores',str(d/'S1_WORKER_SCORES.py'),
          '--ledger',str(ROOT/'logs/auth_budget/AUTH-P08-S1-20261028-01.jsonl'),
          '--out-json',str(OUT/'MUTATED_S1_SCORECARD_FOR_PROBE.json'),
          '--out-md',str(OUT/'MUTATED_S1_SCORECARD_FOR_PROBE.md')]
    with contextlib.redirect_stdout(io.StringIO()):agg.main(args)
    changed=json.loads((OUT/'MUTATED_S1_SCORECARD_FOR_PROBE.json').read_text(encoding='utf-8'))
    original=json.loads((d/'S1_SCORECARD.json').read_text(encoding='utf-8'))
    oldrow=next(x for x in original['positions'] if x['case_id']==pos['case_id'])
    newrow=next(x for x in changed['positions'] if x['case_id']==pos['case_id'])
    result['annotation_reuse_without_binding']={'case_id':pos['case_id'],'original_primary':before,
       'mutated_primary':item['primary'],'candidate_score_before':oldrow['candidate_total'],
       'candidate_score_after':newrow['candidate_total'],'score_and_winner_silently_preserved':oldrow==newrow,
       'batch_id_after':changed.get('batch_id'),'identities_after':changed.get('identities'),
       'interpretation':'This demonstrates absent binding, not that every original judgment is substantively wrong. Old hash-bound review code already exists; restore binding offline without rerunning generations.'}
    (OUT/'REUSE_REGRESSION_PROBES.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k!='annotation_reuse_without_binding'},ensure_ascii=False))
    print(json.dumps({k:v for k,v in result['annotation_reuse_without_binding'].items() if k not in ['original_primary','mutated_primary']},ensure_ascii=False))


if __name__=='__main__':main()
