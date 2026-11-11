"""Primary-first paired follow-up, per-dimension rater strata. Zero model calls.
Incomplete Gemini pairs plus one fully observed bridge per batch/story are kept
as a NEW cohort. Never stitch replacement scores into the old Gemini average.
"""
import json
from pathlib import Path

HERE=Path(__file__).resolve().parent
P=HERE/'CLEAN_PAIR_DATA'

def read(p):return json.loads(p.read_text(encoding='utf-8'))
def lines(p):return [json.loads(l) for l in p.read_text(encoding='utf-8').splitlines() if l.strip()]
def write(p,d):p.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')

def main():
    pairs=lines(P/'PAIR_NODES.jsonl');keys={b['pair_id']:b for b in read(P/'PRIVATE_PAIR_BINDINGS.json')}
    ratings=[]
    for f in (HERE/'LATEST_GEMINI_REPAIRED').glob('RATINGS_*.jsonl'):ratings+=lines(f)
    assessed={r['item_id'] for r in ratings if r.get('kind')=='primary' and r.get('transport_ok') and r.get('parse_ok') and r.get('schema_ok')}
    incomplete=[];bridges={};delivery_only=[]
    for pair in pairs:
        key=keys[pair['pair_id']]
        if any(a.get('unavailable_reason') for a in pair['answers'].values()):
            delivery_only.append(pair['pair_id']);continue
        if any(key[s]['legacy_semantic_node'] not in assessed for s in ('left','right')):
            incomplete.append(pair['pair_id'])
        else:
            block=(key['left']['batch'],key['left']['case_id'].split('_T')[0])
            bridges.setdefault(block,pair['pair_id'])
    selected=set(incomplete)|set(bridges.values())
    profiles={
      'facts':{'dimensions':['factuality','attribution','time_state','boundary'],'model':'dsf',
               'instruction':'只检查两个首选的确定事实/主体/时序状态/私密信息边界。新提议、观点、疑问、玩笑不当已有事实。给每侧四个0-3或null；错误给逐字证据和严重性。'},
      'task':{'dimensions':['goal','memory_use'],'model':'qwen3.8-flash',
              'instruction':'只检查两个首选是否完成本轮交流动作、是否正确用到本轮需要的旧事实。不给无关旧事加分，不因未复述事实扣分。每侧两个0-3或null并给简短证据。'},
      'interaction':{'dimensions':['interaction','initiative','emotion_relation'],'model':'qwen3.8-flash',
                     'instruction':'只检查两个首选的自然互动、适用时的钩子主动性、依据当前情绪/关系/反馈调整。收尾不要求钩子，普通朋友不强迫调情，不预测用户愿发。每侧三个0-3或null并给简短证据。'}
    }
    jobs=[];private=[];backups=[]
    for pair in pairs:
        pid=pair['pair_id'];key=keys[pid]
        backups.append({'item_id':pid+'-backup','evidence':pair['evidence'],'reference_card':pair['reference_card'],
                        'answers':{s:a.get('backups',[]) for s,a in pair['answers'].items()},
                        'task':'只列有证据的严重事实/主体/边界/军师指令问题，每条含side/slot/quote/evidence/type/severity。无问题列表为空。不返回九维均分；不把新提议当既成事实。'})
        if pid not in selected:continue
        for name,profile in profiles.items():
            uid=pid+'-'+name
            jobs.append({'item_id':uid,'profile':name,'model':profile['model'],
                         'dimensions':profile['dimensions'],'instruction':profile['instruction'],
                         'evidence':pair['evidence'],'reference_card':pair['reference_card'],
                         'answers':{s:a['primary'] for s,a in pair['answers'].items()},
                         'output_contract':{'item_id':'原样','left':'按dimensions顺序的0-3整数/null数组','right':'相同长度数组','issues':'逐字引用+来源+维度+严重性+说明；正常可空','na':'null维度及其理由'}})
            private.append({'item_id':uid,'pair_id':pid,'cohort':'missing_pair' if pid in incomplete else 'bridge',
                            'bindings':key})
    controls=read(P/'CONTROLS_PUBLIC.json');expected=read(P/'CONTROLS_PRIVATE.json');exp={x['node_id']:x for x in expected}
    control_jobs=[]
    for i,c in enumerate(controls):
        profile_name=list(profiles)[i%3];profile=profiles[profile_name]
        control_jobs.append({'item_id':c['node_id']+'-'+profile_name,'model':profile['model'],
                             'profile':profile_name,'dimensions':profile['dimensions'],
                             'instruction':profile['instruction'],'evidence':c['evidence'],'answer':c['answer']})
    # These are execution/format controls only where the source expectation does
    # not label this profile. Never pretend 12 controls qualify every dimension.
    (HERE/'RECOVERY_UNITS.jsonl').write_text(''.join(json.dumps(j,ensure_ascii=False)+'\n' for j in jobs),encoding='utf-8')
    (HERE/'BACKUP_RISK_UNITS.jsonl').write_text(''.join(json.dumps(j,ensure_ascii=False)+'\n' for j in backups),encoding='utf-8')
    write(HERE/'RECOVERY_PRIVATE_BINDINGS.json',private)
    write(HERE/'RECOVERY_CONTROLS_PUBLIC.json',control_jobs)
    write(HERE/'RECOVERY_CONTROLS_PRIVATE.json',expected)
    plan={'kind':'new_stratified_paired_cohort_not_pooled_gemini_patch','incomplete_pairs_with_both_texts':len(incomplete),
          'bridge_pairs':len(bridges),'delivery_failure_only_pairs':delivery_only,'new_pair_units':len(selected),
          'primary_dimension_jobs':len(jobs),'execution_controls':len(control_jobs),
          'primary_phase_planned':len(jobs)+len(control_jobs),'optional_backup_risk_jobs':len(backups),
          'max_combined_jobs':len(jobs)+len(control_jobs)+len(backups),
          'per_alias_cap':3,'global_initial_cap':3,'global_ramp_cap':6,'timeout_s':45,
          'model_policy':{'gemini-3.8-flash':0,'hy4-preview':'forbidden','hy3':'noncritical authoring initially1; large load timed out'},
          'qualification':'Profiles without directly applicable reference controls remain advisory; evidence review required. Same-model repeated roles are not independent human review.',
          'if_service_slow':'Do not burn full queue: test a small profile unit first, stop that model after consecutive failures and continue P07/candidate work. No third all-dimensional judge rerun.',
          'no_new_authorization_required_from_user':True,'new_per_scope_authorization_documents_required':True,
          'actual_dispatch_started':False}
    write(HERE/'RECOVERY_PLAN.json',plan)
    print(json.dumps(plan,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
