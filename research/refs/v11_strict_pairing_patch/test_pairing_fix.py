"""Offline acceptance test for the single-function reviewer patch.

Usage: python -B -X utf8 test_pairing_fix.py PATH_TO_EXECUTOR
Loads only the function AST; never imports/runs the executor or calls a model.
"""
import ast
import json
import os
import pathlib
import sys
import tempfile


def main():
    source = pathlib.Path(sys.argv[1]).resolve()
    tree = ast.parse(source.read_text(encoding='utf-8'))
    fn = next(n for n in tree.body if isinstance(n, ast.FunctionDef)
              and n.name == '_verify_slot_attempts')
    namespace = {'os': os, 'json': json}
    exec(compile(ast.Module(body=[fn], type_ignores=[]), str(source), 'exec'), namespace)
    verify = namespace['_verify_slot_attempts']
    start = dict(kind='attempt_started', run_id='R', case_id='C', attempt_id=1)
    end = dict(start, kind='attempt_completed')
    fixtures = {
        'valid': [start, end],
        'wrong_terminal_attempt': [start, dict(end, attempt_id=99)],
        'wrong_terminal_case': [start, dict(end, case_id='other_case')],
        'terminal_before_start': [end, start],
        'missing_ids': [{k:v for k,v in x.items() if k!='attempt_id'} for x in [start,end]],
        'missing_terminal': [start],
        'duplicate_terminal': [start,end,end],
        'gateway_failed': [start,dict(end,kind='attempt_failed')],
        'extra_start': [start,start,end],
        'other_run_ignored': [dict(start,run_id='other'),start,end,dict(end,run_id='other')],
    }
    # Leave the tiny fixture logs under the review artifact directory for inspection.
    target = pathlib.Path(tempfile.mkdtemp(prefix='pairing_test_', dir=pathlib.Path(__file__).resolve().parent))
    results = {}
    for name, events in fixtures.items():
        folder = target/name
        folder.mkdir()
        (folder/'gateway_events.jsonl').write_text(''.join(json.dumps(x)+'\n' for x in events),encoding='utf-8')
        result = verify(str(folder),'R','C')
        results[name] = result['ok'] == (name in ('valid','other_run_ignored'))
    print(json.dumps({'passed':sum(results.values()),'total':len(results),'tests':results},indent=2))
    return 0 if all(results.values()) else 1


if __name__ == '__main__':
    raise SystemExit(main())
