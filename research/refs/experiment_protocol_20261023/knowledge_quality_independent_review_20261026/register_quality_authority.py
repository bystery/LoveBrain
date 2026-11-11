"""Register a bounded evaluation batch under the user's standing authorization.
No network and no candidate generations. Closed ledgers are never reused.
"""
import json
import sys
from pathlib import Path

ROOT = Path('D:/lovebrain-arena')
OUT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
from arena.isolation.authorization_budget import AuthorizationBudget, validate_authorization_doc

AID = 'AUTH-QUALITY-PANEL-20261027-01'
LEDGER = ROOT/'logs/auth_budget'/f'{AID}.jsonl'
SCOPE = {
    'scope_id': 'quality_panel_semantic_review_20261027_v1',
    'purpose': 'Existing C4 128 answer sets plus 13 fixed repeats and 32 authored development controls; advisory nine-dimensional review, NOT personal willingness prediction.',
    'endpoint': 'http://127.0.0.1:17770/v1', 'model_alias': 'dsf', 'temperature': 0.7,
    'automatic_judge_generations': True, 'automatic_candidate_generations': False,
    'failure_policy': 'Failure consumes quota. No retries or automatic refill; stop on transport/audit failure. Invalid semantic response is preserved as unassessed, not regenerated.',
    'gate_before_dispatch': 'Freeze reference cards, review manual, schema, controls and full dispatch plan. Existing packet is ready; missing references are work to finish, not missing human approval.',
}


def main():
    auth = {
        'authorization_id': AID, 'authorized': True, 'status': 'ACTIVE',
        'authorization_basis': {
            'kind': 'existing_explicit_user_standing_authorization',
            'user_quote': '我额度很多！随便用！！！ 现在重点就是修复好bug之后推进主线！',
            'interpretation': 'Create this new bounded judge batch, preserve closed old grants; no new human signature or per-batch approval claimed.'},
        'real_endpoint': {'api_base': SCOPE['endpoint'], 'model': 'dsf',
                          'temperature': 0.7, 'max_attempts':192},
        'budget_ledger': str(LEDGER), 'scope': SCOPE,
        'planned_attempts':173,
        'unused_capacity_policy': '19 remaining is not retry permission; not a spending target.',
        'human_willingness': 'unknown; model forbidden to fill user labels',
    }
    validate_authorization_doc(auth, expected_endpoint=SCOPE['endpoint'],
                               expected_model='dsf', expected_temperature=0.7)
    p = OUT/'AUTHORIZATION_QUALITY_PANEL.json'
    if p.exists():
        assert json.loads(p.read_text(encoding='utf-8')) == auth, 'existing authorization differs; do not overwrite'
    else:
        p.write_text(json.dumps(auth,ensure_ascii=False,indent=2),encoding='utf-8')
    b = AuthorizationBudget(str(LEDGER), AID, 192, scope=SCOPE)
    # This records immutable scope and checks capacity; it reserves no attempts.
    # On re-run, consumed/closed state remains authoritative and can refuse.
    b.ensure_batch(173,purpose='preflight_only_no_dispatch')
    supplement = {'kind':'standing_authorization_bounded_supplement',
                  'authorization_id':AID,'additional_stage_cap':192,
                  'old_marathon_registry_modified':False,
                  'history_policy':'Old cap/use/closed records preserved. This is an explicit new supplement, not silent revival or use of performance-only judge=false scope.',
                  'scope':SCOPE,'ledger_summary':b.summary(),
                  'real_model_calls_this_registration':0}
    (OUT/'BUDGET_SUPPLEMENT.json').write_text(json.dumps(supplement,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(supplement['ledger_summary'],ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()
