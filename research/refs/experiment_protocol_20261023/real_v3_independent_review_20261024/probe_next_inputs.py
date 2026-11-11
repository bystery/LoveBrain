"""Capture actual mechanism assembly with an in-memory fake gateway; no network.

These are input-side probes, not IsolatedRun/live execution or quality evidence.
Only this review directory is written. Existing candidate files remain unchanged.
"""
import importlib.util
import json
import pathlib
import sys

ROOT = pathlib.Path('D:/lovebrain-arena')
HERE = pathlib.Path(__file__).resolve().parent
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'arena')]
spec = importlib.util.spec_from_file_location('review_v4_fixture_helpers', ROOT / 'tools/test_v4_intent_context.py')
fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixtures)
result, captures = [], []
arms = {'reference': fixtures.v4.base, 'v4_draft': fixtures.v4}
names = {'reference': 'reference_product_v1', 'v4_draft': 'v4_intent_context'}
for ep in ('E007', 'E010', 'E012'):
    episode = json.loads((ROOT / 'benchmark/episodes' / (ep + '.json')).read_text(encoding='utf-8'))
    for arm, module in arms.items():
        gw = fixtures.Gw()
        sandbox = HERE / 'next_input_probe' / arm / ep
        h = module.init(str(ROOT / 'candidates' / names[arm] / 'assets'), episode, None, str(sandbox))
        for n, turn in enumerate(episode['turns'], 1):
            ctx = dict(turn, turn_index=n)
            module.run_turn(h, ctx, gw)
            user = gw.calls[-1]['user']
            captures.append({'arm': arm, 'case_id': turn['case_id'], 'user': user})
            kb = episode.get('initial_kb') or {}
            seed_present = {key: isinstance(value, str) and value in user for key, value in kb.items()}
            result.append({'arm': arm, 'case_id': turn['case_id'],
                           'seed_full_values_present': seed_present,
                           'active_goal_block_present': '【当前目标(' in user or '【持续意图' in user,
                           'persistent_private_goal_text_present': '想约她一起去看一场 livehouse 演出' in user})
summary = {'kind': 'actual mechanism input assembly with in-memory fake gateway',
           'real_model_calls': 0, 'not_quality_or_full_executor_evidence': True, 'rows': result}
(HERE / 'NEXT_INPUT_PROBE.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
(HERE / 'NEXT_INPUT_CAPTURE.json').write_text(json.dumps(captures, ensure_ascii=False, indent=2), encoding='utf-8')
for row in result:
    if row['case_id'] in ('E007_T01', 'E007_T04', 'E007_T05', 'E010_T01', 'E012_T01'):
        print(json.dumps(row, ensure_ascii=False))
print('captured_turns=', len(captures), 'real_model_calls=0')
