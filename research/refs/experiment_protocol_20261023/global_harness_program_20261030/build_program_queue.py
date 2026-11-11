"""Compile and validate 96 concrete tasks from the single master instruction."""
import datetime,hashlib,json,re,zipfile
from pathlib import Path
HERE=Path(__file__).parent
text=(HERE/'MASTER_CONTINUOUS_HARNESS_PROGRAM.md').read_text(encoding='utf-8')
names=dict(re.findall(r'^### (W\d{2})｜(.+)$',text,re.M))
base_deps={
 'W01':[], 'W02':['W01-02'], 'W03':['W01-01'], 'W04':['W01-03'],
 'W05':['W01-02'], 'W06':['W01-02'], 'W07':['W01-02'], 'W08':['W01-03'],
 'W09':['W01-02'], 'W10':['W01-06','W02-03','W03-03','W04-03','W09-03'],
 'W11':['W10-02'], 'W12':['W03-04','W05-02'], 'W13':['W10-04','W03-05'],
 'W14':['W10-02'], 'W15':['W10-02'], 'W16':['W01-01'],
}
tracks={'W01':'X/R','W02':'H/R','W03':'E','W04':'E/R','W05':'H/E','W06':'H/E','W07':'H/X','W08':'H/E','W09':'H/R','W10':'X/E/R','W11':'H/X/R','W12':'X/H/E','W13':'E/R/X','W14':'X/H','W15':'R/X','W16':'X/integrator'}
reuse={'W02-01','W02-04','W06-01','W14-01','W16-03'}
tasks=[]
for line in text.splitlines():
 m=re.match(r'^\| (W\d{2}-\d{2}) \| (.+?) \| (.+?) \|$',line)
 if not m:continue
 tid,action,accept=m.groups();pkg=tid[:3];num=int(tid[-2:])
 deps=list(base_deps[pkg])
 # Not a global serial order; just logical within-package evidence prerequisites.
 if pkg=='W01' and num==6:deps+=['W01-01','W01-05']
 if pkg in ['W02','W03','W04','W05','W06','W07','W08','W09']:
  if num==2:deps+=[pkg+'-01']
  if num==3:deps+=[pkg+'-01']
  if num==4:deps+=[pkg+'-02']
  if num==5:deps+=[pkg+'-03']
  if num==6:deps+=[pkg+'-03',pkg+'-04',pkg+'-05']
 if pkg in ['W10','W11','W12','W13','W14','W15']:
  if num>=2:deps+=[pkg+'-%02d'%(num-1)]
 if pkg=='W16' and num in [3,4,5,6]:deps+=['W01-06']
 if tid=='W02-03':deps+=['W02-02']
 if tid=='W03-03':deps+=['W03-02']
 tasks.append({'id':tid,'package':pkg,'package_name':names[pkg], 'track':tracks[pkg],
 'action':action,'acceptance':accept,'state':'READY' if not deps else 'PLANNED',
 'depends_on':list(dict.fromkeys(deps)),
 'reuse_first':True,'known_existing_work_to_verify':pkg=='W01' or tid in reuse,
 'evidence':[],'owner':None,'started_at':None,'completed_at':None,
 'actual_engineering_hours':None,'actual_model_attempts':0,
 'estimate_engineering_hours':0.25 if pkg=='W01' else 2.5,
 'estimate_note':'Planning only; reuse verified work directly and do not manufacture hours',
 'completion_gate':['implementation_or_reused_artifact','required_validation','scoped_decision','recovery_entry']})
assert len(tasks)==96,len(tasks)
ids={t['id'] for t in tasks};assert len(ids)==96
assert all(len([t for t in tasks if t['package']==p])==6 for p in names)
graph={t['id']:t['depends_on'] for t in tasks}
assert all(d in ids and d!=k for k,v in graph.items() for d in v)
def visit(k,path):
 if k in path:raise AssertionError('cycle '+str(path+[k]))
 for d in graph[k]:visit(d,path+[k])
for k in graph:visit(k,[])
q={'schema':'lovebrain_global_continuous_tasks_v1','date':'2026-10-30',
 'authority':'User latest request: whole-program large continuous iterative optimization, supersedes only-one-local-fix scope',
 'not_a_running_process':True,'master':'MASTER_CONTINUOUS_HARNESS_PROGRAM.md',
 'workload':'96 tasks plus evidence-driven replenishment; estimated effective engineering work, not 288h autonomous uptime promise',
 'planned_engineering_hours_sum':sum(t['estimate_engineering_hours'] for t in tasks),
 'tasks':tasks,
 'wave_attempt_menu_caps':{'A':1536,'B':2048,'C':3072,'D':3072,'E':1536},
 'menu_is_not_spending_target_or_new_global_authorization':True,
 'existing_management_cap':16384,
 'manager_remaining_attempts':None,
 'manager_remaining_note':'Reconcile scope boundaries/open reservations first; inventory file sum is not a canonical cap balance',
 'continuous_replenishment':'After each complete experiment, add highest-value future work to retain 48–72 effective engineering hours READY/PLANNED; never duplicate accepted work',
 'terminal_policy':'Continue authorized nonblocked tracks until scoped acceptance, explicit user pause, or exact resource/external constraint; no infinite paid retries'}
(HERE/'TASK_QUEUE.json').write_text(json.dumps(q,ensure_ascii=False,indent=2),encoding='utf-8')
state={'schema':'lovebrain_global_research_state_v1','created_at':datetime.datetime.now().astimezone().isoformat(),
 'overall':'REWORK_GLOBAL_OPTIMIZATION_CONTINUES','execution_status':'INSTRUCTION_AND_QUEUE_DELIVERED_NOT_STARTED_BY_THIS_PLANNING_TURN',
 'main_incumbent':'action_policy_r4_guard_e3','not_promoted':['action_policy_r5_tier'],
 'baselines':['reference_product_v1','v0_minimal_events (verify comparable scope before using)'],
 'primary_metrics':['goal','factuality','attribution','time_state','boundary','memory_use','interaction','initiative','emotion_relation','positive_interaction_slices','knowledge_six_stage'],
 'secondary_metrics':['actual_api_usage','known_price_cost','first_valid_usable_latency','completion_latency','reliability','complexity'],
 'inherited_evidence':['F3 4 developer-exposed stories/72 pairs 38:10:24; ratings belong to r4','B 24 on/off real calls, worker all-slot grade not primary-only gold','C24+19: tiering not confirmed','F1 mixed older identities, not r4 baseline result','E3 71/72 replay-equivalent, 1 mechanical repair + local live regression'],
 'current_batch':None,'processes_claimed_running_by_this_turn':[],
 'new_real_model_calls_by_this_turn':0,'completed_new_research_tasks_by_this_turn':0,
 'next_actions':['W01 targeted inheritance <=2 effective engineering hours','Open E/H/X/R streams, prepare common candidate screening','Execute a complete quality-led cycle, decide keep/eliminate, replenish next cycle'],
 'product_integration':'PAUSED','historical_submission_deadline':'2026-10-29, not an acceptance override',
 'checkpoint_rule':'When executing, persist actual task statuses/physical attempts/process identity and handoff; planning state is not experimental success'}
(HERE/'RESEARCH_STATE.json').write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding='utf-8')
result={'tasks':96,'packages':16,'all_dependencies_exist':True,'acyclic':True,'menu_attempts':sum(q['wave_attempt_menu_caps'].values()),'new_real_calls':0,'no_autonomous_runtime_claim':True}
(HERE/'QUEUE_VALIDATION.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
payload=['00_READ_ME.md','USER_REQUIREMENTS_TRACEABILITY.md','MASTER_CONTINUOUS_HARNESS_PROGRAM.md','NEW_WINDOW_START_PROMPT.md','TASK_QUEUE.json','RESEARCH_STATE.json','QUEUE_VALIDATION.json','BUDGET_INHERITANCE_INVENTORY.json','build_program_queue.py']
manifest={'kind':'instruction_bundle','not_model_results':True,'files':{n:{'sha256':hashlib.sha256((HERE/n).read_bytes()).hexdigest(),'bytes':(HERE/n).stat().st_size} for n in payload}}
(HERE/'BUNDLE_MANIFEST.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
zipname=HERE/'LoveBrain_GLOBAL_CONTINUOUS_20261030.zip'
with zipfile.ZipFile(zipname,'x',zipfile.ZIP_DEFLATED) as z:
 for n in payload+['BUNDLE_MANIFEST.json']:z.write(HERE/n,arcname=n)
with zipfile.ZipFile(zipname) as z:
 assert z.testzip() is None
 for n in payload:assert hashlib.sha256(z.read(n)).hexdigest()==manifest['files'][n]['sha256']
print(json.dumps({**result,'zip':str(zipname),'zip_bytes':zipname.stat().st_size,'master_chars':len(text),'estimated_task_hours':q['planned_engineering_hours_sum']},ensure_ascii=False))
