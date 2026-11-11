"""Prepare 96 complete paired units + 12 controls + 12 swaps. Zero model calls.
Keeps all 192 generation slots, including the unparseable generation previously
excluded from the 191-node semantic packet. No Gemini scores are pooled here.
"""
import collections
import copy
import hashlib
import json
import random
from pathlib import Path

ROOT=Path('D:/lovebrain-arena')
OUT=Path(__file__).resolve().parent/'CLEAN_PAIR_DATA'
PK=ROOT/'refs/experiment_protocol_20261023/p06_judge_gem_20261027'

def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sh(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,d):p.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')

def main():
    OUT.mkdir(exist_ok=True)
    all_nodes=[json.loads(l) for l in (PK/'P06_NODES.jsonl').read_text(encoding='utf-8').splitlines() if l.strip()]
    nodes=[n for n in all_nodes if n['_meta']['node_kind']=='primary']
    assert len(nodes)==191
    keyed={(n['_meta']['batch'],n['_meta']['case_id'],n['_meta']['arm']):n for n in nodes}
    cases=sorted({n['_meta']['case_id'] for n in nodes})
    rng=random.Random(271028)
    publics=[];bindings=[]
    for case_index,case in enumerate(cases):
        batches=list('ABC');rng.shuffle(batches)
        for batch in batches:
            available=[keyed[k] for k in [(batch,case,'parent'),(batch,case,'candidate')] if k in keyed]
            assert available
            shared=copy.deepcopy(available[0]['evidence'])
            assert all(n['evidence']==shared for n in available)
            assert all(n['reference_card']==available[0]['reference_card'] for n in available)
            answers={};private={}
            for role in ('parent','candidate'):
                ep=case.split('_T')[0];turn=int(case.split('_T')[1])
                td=ROOT/'logs'/('p06_'+batch+'_live_20261027')/(role+'_'+ep)/'out'/('turn_%02d_%s'%(turn,case))
                mf=read(td/'manifest.json');raw=td/'reply_raw.txt'
                n=keyed.get((batch,case,role))
                if n:
                    assert sh(raw)==n['_meta']['reply_sha256']
                    answers[role]=copy.deepcopy(n['answer'])
                    # Do not hand literal placeholders to the judge as sendable prose.
                    answers[role]['backups']=[b for b in answers[role]['backups'] if not(isinstance(b.get('text'),str) and b['text'].strip().lower() in ('null','none','nil','n/a'))]
                else:
                    try:read(raw)
                    except (ValueError,json.JSONDecodeError):pass
                    else:raise AssertionError('semantic node missing despite parseable raw')
                    answers[role]={'primary':None,'backups':[],'unavailable_reason':'generation_not_parseable; semantic score NA, task delivery failure retained externally'}
                private[role]={'batch':batch,'case_id':case,'arm':role,'candidate':mf['candidate'],
                               'run_id':mf['run_id'],'input_sha256':mf['case_input_sha256'],
                               'raw_sha256':sh(raw),'veto':mf.get('veto'),'answer_dir':str(td),
                               'legacy_semantic_node':n['_meta']['node_id'] if n else None}
            assert private['parent']['input_sha256']==private['candidate']['input_sha256']
            candidate_left=(case_index+ord(batch))%2==0
            left,right=('candidate','parent') if candidate_left else ('parent','candidate')
            pid='FP%03d'%(len(publics)+1)
            publics.append({'pair_id':pid,'evidence':shared,'reference_card':available[0]['reference_card'],
                            'answers':{'left':answers[left],'right':answers[right]}})
            bindings.append({'pair_id':pid,'left':private[left],'right':private[right]})
    assert len(publics)==96 and len(bindings)==96
    controls=read(PK/'P06_CONTROLS.json')['controls']
    selected=[c for c in controls if c['control_id'] in ('CTL33','CTL34','CTL35','CTL36')]
    for acceptable in (False,True):
        have=sum(c['expectation']['acceptable']==acceptable for c in selected)
        candidates=sorted([c for c in controls if c not in selected and c['expectation']['acceptable']==acceptable],key=lambda c:c['control_id'])
        selected+=candidates[:6-have]
    assert len(selected)==12
    control_public=[{'node_id':'FC%02d'%(i+1),'evidence':c['context'],'answer':c['answer']} for i,c in enumerate(selected)]
    control_private=[{'node_id':'FC%02d'%(i+1),'source_control_id':c['control_id'],'expectation':c['expectation']} for i,c in enumerate(selected)]
    swaps=rng.sample([p['pair_id'] for p in publics],12)
    plan={'kind':'clean_pair_source_data_only_not_a_dispatch_plan',
          'source_primary_nodes':191,'source_repeat_nodes_excluded':24,
          'primary_pairs':96,'generation_slots_retained':192,
          'controls':12,'swapped_pair_ids':swaps,
          'execution_plan':'../RECOVERY_PLAN.json',
          'policy':'Data only. Full all-slot qwen study was stopped after feasibility failures. Use the separate primary-first profile plan; no regeneration or naive pooling with Gemini.'}
    (OUT/'PAIR_NODES.jsonl').write_text(''.join(json.dumps(p,ensure_ascii=False)+'\n' for p in publics),encoding='utf-8')
    write(OUT/'PRIVATE_PAIR_BINDINGS.json',bindings)
    write(OUT/'CONTROLS_PUBLIC.json',control_public)
    write(OUT/'CONTROLS_PRIVATE.json',control_private)
    write(OUT/'PLAN.json',plan)
    write(OUT/'MANIFEST.json',{'files':{p.name:sh(p) for p in OUT.iterdir() if p.is_file() and p.name!='MANIFEST.json'},'real_calls':0})
    print(json.dumps({'pairs':96,'retained_generation_slots':192,'single_generation_missing_semantic_content':sum(bool(a.get('unavailable_reason')) for p in publics for a in p['answers'].values()),'control_good':sum(c['expectation']['acceptable'] for c in selected),'control_bad':sum(not c['expectation']['acceptable'] for c in selected),'data_only':True,'calls':0},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
