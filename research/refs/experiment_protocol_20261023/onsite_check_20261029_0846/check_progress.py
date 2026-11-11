import collections,json,datetime
from pathlib import Path
root=Path(r'D:\lovebrain-arena')
out=Path(__file__).parent
rows=[]
for scope in ('S1','S2','S2R','S3','D1','R3A','R3B'):
    path=root/'logs'/'auth_budget'/f'AUTH-P08-{scope}-20261028-01.jsonl'
    events=[json.loads(s) for s in path.read_text(encoding='utf-8-sig').splitlines() if s.strip()]
    counts=collections.Counter(e.get('kind',e.get('event')) for e in events)
    card=json.loads((root/'refs/experiment_protocol_20261023/s1_action_live_20261028'/f'{scope}_SCORECARD.json').read_text(encoding='utf-8-sig'))
    rows.append(dict(batch=scope,ledger_counts=dict(counts),ledger_mtime=datetime.datetime.fromtimestamp(path.stat().st_mtime).astimezone().isoformat(),arms=card['arms'],paired=card.get('paired_position_verdicts'),dimensions=card.get('per_dimension_mean'),denominators=card.get('per_dimension_cells'),coverage=card.get('coverage'),five_layer_delivery=card.get('five_layer_delivery')))
record={'observed_at':datetime.datetime.now().astimezone().isoformat(),'batches':rows,'status':'Historical real comparisons finished; final F0-F6 execution has no new on-disk evidence after 2026-10-29 00:42','caution':'Quality scores are worker self-review; binding verification is not independent semantic validation. Known closing/no_reply label conflict remains.'}
(out/'CURRENT_PROGRESS.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
for r in rows:
    print(json.dumps({k:r[k] for k in ('batch','ledger_counts','arms','paired')},ensure_ascii=False))
