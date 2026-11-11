"""Independent, offline review. Writes only beside this script; no model requests."""
import hashlib
import importlib.util
import json
import sys
from collections import Counter
from pathlib import Path
from unittest.mock import patch

ROOT = Path('D:/lovebrain-arena')
OUT = Path(__file__).parent
sys.path[:0] = [str(ROOT/'arena'), str(ROOT/'integration_preview')]
import lb_gateway
from lb_stream_timing import StreamTimer
from engine_adapter import OutputConsumer, Event

def load(p):
    return json.loads(Path(p).read_text(encoding='utf-8'))

def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()

def lines(p):
    return [json.loads(s) for s in Path(p).read_text(encoding='utf-8').splitlines() if s.strip()]

r = {'real_model_calls': 0}
r['raw_faults'] = []
for batch, arm in [('v5_ctx_live_20261025','candidate'), ('r2_ctx_live_20261024','parent')]:
    p = ROOT/'logs'/batch/(arm+'_E007')/'out/turn_08_E007_T08/reply_raw.txt'
    obj = load(p)
    consumed = OutputConsumer.parse(p.read_text(encoding='utf-8'))
    r['raw_faults'].append({'file': str(p), 'sha256': sha(p),
        'response_keys_ascii': [ascii(k) for k in obj['response']],
        'directions_length': len(obj['directions']),
        'consumed_status': consumed['status'],
        'normalizations': consumed['key_normalization_audit']})

tab = load(ROOT/'evaluation/DELIVERY_AND_COST_COLUMNS_V1.json')
r['cost_denominators'] = {}
for batch, arms in tab['batches'].items():
    for arm, v in arms.items():
        total = v['tokens']['total']
        r['cost_denominators'][batch+'/'+arm] = {
            'total_tokens': total, 'P': v['P'], 'C': v['C'], 'S_no_veto': v['S'],
            'per_consumable_primary': total/v['C'] if v['C'] else None,
            'per_no_veto_primary': total/v['S'] if v['S'] else None,
            'per_semantically_passed_or_user_willing': None}

planp=ROOT/'refs/experiment_protocol_20261023/v5_next_batch_20261025/INTERLEAVE_PLAN.json'
plan=load(planp)
expected=dict(plan['frozen']['config_files'])
for c in plan['frozen']['identity_closure'].values():
    expected.update(c['files'])
r['historical_freeze']={'plan_sha256':sha(planp), 'checked':len(expected), 'mismatch':[
    {'path':p, 'expected':h, 'current':sha(ROOT/p) if (ROOT/p).is_file() else None}
    for p,h in expected.items() if not (ROOT/p).is_file() or sha(ROOT/p)!=h]}
budget=ROOT/'logs/auth_budget/AUTH-HARNESS-PERF-PHASE1-20261025-01.jsonl'
ev=lines(budget)
counts=Counter(x.get('kind',x.get('event')) for x in ev)
r['phase_budget']={'counts':dict(counts), 'cap':256,
    'started':counts['attempt_started'], 'remaining':256-counts['attempt_started']}

# The actual OpenAI-compatible adapter path, with HTTP completely replaced in-process.
class Response:
    def __enter__(self): return self
    def __exit__(self,*args): return False
    def read(self):
        return json.dumps({'choices':[{'message':{'content':'{}'},'finish_reason':'stop'}],
                           'usage':{'prompt_tokens':1,'completion_tokens':1}}).encode()
captured=[]
def fake_open(req, **kw):
    captured.append(json.loads(req.data.decode()))
    return Response()
with patch('urllib.request.urlopen', fake_open):
    adapter=object.__new__(lb_gateway.OpenAICompatAdapter)
    adapter.base='http://offline.invalid/v1'; adapter.model='dsf'; adapter.key=''
    actual=adapter.generate('s','u',{'model':'dsf','temperature':0.7,'stream':True})
r['adapter_timing_probe']={'network':'replaced; zero real dispatch',
    'request_stream':captured[0].get('stream'), 'returned_timing':actual.get('timing'),
    'request_keys':sorted(captured[0])}

# Demonstrate why timestamping a preview before validating it is not time-to-usable.
timer=StreamTimer(turn_start_perf=10.0)
now=[10.0]
timer._t=lambda:now[0]
timer.start_request()
now[0]=11.0
timer.on_delta('{"response":{"recommended":"hello"}')
now[0]=20.0
timer.finish()
now[0]=21.0
timer.seal_usable(lambda text:True)
r['late_validator_backdating']={'validated_at_request_relative_ms':11000,
                                'reported':timer.as_dict()}
invalid=StreamTimer()
invalid.start_request(); invalid.on_delta('not-json'); invalid.finish(); invalid.seal_usable(None)
r['invalid_payload_usable_fallback']=invalid.as_dict()

# Independent sample/count checks, without overwriting the worker's IRR outputs.
packet=ROOT/'evaluation/CALIBRATION_PACKET_V1'
nodes=lines(packet/'nodes.jsonl')
subset=lines(packet/'IRR_SUBSET_NORMAL.jsonl')
ratings={k:{v['node_id'].rstrip('S'):v for v in lines(packet/('RATINGS_'+k+'.jsonl'))}
         for k in ['R1','R2','R3']}
same_ties=decisive=flips=0
labels=['甲','乙']
for n in subset:
    nid=n['node_id']; p1=ratings['R1'][nid]['pref']; p3=ratings['R3'][nid]['pref']
    p3={'甲':'乙','乙':'甲'}.get(p3,p3)
    if p1 in labels and p3 in labels:
        decisive+=1; flips+=(p1!=p3)
    elif p1==p3: same_ties+=1
r['calibration_audit']={'all_nodes':len(nodes), 'selected_nodes':len(subset),
    'selected_story_counts':dict(Counter(n['story'] for n in subset)),
    'true_both_decisive_pairs_R1_R3':decisive,'flips':flips,
    'equal_non_decisive_labels_counted_as_decisive_in_worker_code':same_ties,
    'all_nodes_given_blanket_card_defect_note':sum(bool(n['evidence'].get('card_defect_note'))
                                                  for n in nodes if n['provenance']=='model_paid'),
    'model_paid_nodes':sum(n['provenance']=='model_paid' for n in nodes),
    'control_C03':next(n for n in nodes if n['position']=='C03')['answers']}
r['integration_direct_probes']={}
for val in ['[]','{"response": []}', '{"response":{"bad_bo\\u200by":"x","recommended":"hi"}}']:
    parsed=OutputConsumer.parse(val)
    r['integration_direct_probes'][val]={'status':parsed['status'], 'slots':parsed['slots'],
                                       'audit':parsed['key_normalization_audit']}
try:
    Event('ADVISOR_NOTE','PRIVATE','').to_msg()
    note_rejected=False
except ValueError:
    note_rejected=True
r['integration_direct_probes']['note_to_chat_rejected']=note_rejected
(OUT/'INDEPENDENT_RESULTS.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(r,ensure_ascii=False,indent=2))
