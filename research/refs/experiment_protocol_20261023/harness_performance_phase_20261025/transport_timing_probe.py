"""Up to 4 real, budgeted synthetic requests, authorized by current human message.

This tests transport/timing capability, not harness quality or performance gains.
Uses existing ModelGateway attempt logging plus the shared phase budget.
"""
import datetime
import json
import pathlib
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = pathlib.Path('D:/lovebrain-arena')
HERE = pathlib.Path(__file__).resolve().parent
sys.path[:0] = [str(ROOT / 'arena')]
from lb_gateway import ModelGateway
from isolation.authorization_budget import AuthorizationBudget


class TimingProbeAdapter:
    name = 'openai_compat_timing_capability_probe'

    def __init__(self, base, stream):
        self.base, self.stream = base, stream
        self.metrics = {}

    def generate(self, system, user, payload):
        body = {'model': 'dsf', 'temperature': .7, 'max_tokens': 16,
                'messages': [{'role': 'system', 'content': system}, {'role': 'user', 'content': user}],
                'stream': self.stream}
        if self.stream:
            body['stream_options'] = {'include_usage': True}
        t0 = time.perf_counter()
        req = urllib.request.Request(self.base + '/chat/completions', data=json.dumps(body).encode('utf-8'), headers={'Content-Type': 'application/json'})
        self.metrics = {'request': body, 'stream_requested': self.stream,
                        'headers_ms': None, 'first_content_ms': None, 'last_content_ms': None,
                        'completion_ms': None, 'timeout_s': 120}
        try:
            with urllib.request.urlopen(req, timeout=120) as response:
                self.metrics['headers_ms'] = (time.perf_counter() - t0) * 1000
                self.metrics['http_status'] = response.status
                self.metrics['content_type'] = response.headers.get('Content-Type')
                if not self.stream:
                    raw = response.read().decode('utf-8')
                    data = json.loads(raw)
                    choice = data['choices'][0]
                    text, finish = choice['message'].get('content', ''), choice.get('finish_reason')
                    usage, model, rid = data.get('usage'), data.get('model'), data.get('id')
                    self.metrics['raw_response'] = data
                    self.metrics['ttft_observable'] = False
                else:
                    text, finish, usage, model, rid, chunks = '', None, None, None, None, []
                    for raw_line in response:
                        line = raw_line.decode('utf-8').strip()
                        if not line or line.startswith(':'):
                            continue
                        if not line.startswith('data:'):
                            raise ValueError('Requested SSE but response line was not data: ' + line[:100])
                        data_line = line[5:].strip()
                        if data_line == '[DONE]':
                            break
                        chunk = json.loads(data_line)
                        chunks.append(chunk)
                        model, rid = chunk.get('model', model), chunk.get('id', rid)
                        if chunk.get('usage'):
                            usage = chunk['usage']
                        for choice in chunk.get('choices', []):
                            content = (choice.get('delta') or {}).get('content')
                            if isinstance(content, str) and content:
                                at = (time.perf_counter() - t0) * 1000
                                if self.metrics['first_content_ms'] is None:
                                    self.metrics['first_content_ms'] = at
                                self.metrics['last_content_ms'] = at
                                text += content
                            if choice.get('finish_reason') is not None:
                                finish = choice['finish_reason']
                    self.metrics['raw_chunks'] = chunks
                    self.metrics['ttft_observable'] = self.metrics['first_content_ms'] is not None
                self.metrics['completion_ms'] = (time.perf_counter() - t0) * 1000
                return {'text': text, 'finish': finish, 'usage': usage, 'resp_model': model, 'resp_id': rid}
        except Exception:
            self.metrics['completion_ms'] = (time.perf_counter() - t0) * 1000
            raise


def main():
    auth = json.loads((HERE / 'AUTHORIZATION_PHASE.json').read_text(encoding='utf-8'))
    assert auth['authorized'] is True and auth['status'] == 'ACTIVE'
    budget = AuthorizationBudget(str(ROOT / auth['budget_ledger']), auth['authorization_id'], auth['real_endpoint']['max_attempts'], status=auth['status'], scope=auth['scope'])
    budget.ensure_batch(4, purpose='four_synthetic_timing_capability_probes')
    run_id = 'timing_cap_' + datetime.datetime.now().strftime('%Y%m%d_%H%M%S') + '_' + uuid.uuid4().hex[:8]
    out = HERE / run_id
    out.mkdir()
    results = []
    for i, streaming in enumerate([False, True, True, False], 1):
        aid = run_id + '_p%d' % i
        adapter = TimingProbeAdapter(auth['real_endpoint']['api_base'], streaming)
        gateway = ModelGateway(adapter, event_log_path=str(out / 'attempts.jsonl'))
        gateway.set_run_context(run_id, 'transport_probe_%d' % i)
        budget.reserve(aid, purpose='synthetic_transport_timing_only')
        try:
            answer = gateway.generate('只回复英文OK两个字母，不要其他内容。', '请回复OK。', model='dsf', temperature=.7, timeout_s=120, attempt_id=aid)
            budget.record('attempt_completed', aid, purpose='synthetic_transport_timing_only', usage=answer.get('usage'))
            result = {'attempt_id': aid, 'ok': True, 'answer': answer, 'metrics': adapter.metrics}
        except Exception as e:
            budget.record('attempt_failed', aid, purpose='synthetic_transport_timing_only', error=type(e).__name__ + ': ' + str(e))
            result = {'attempt_id': aid, 'ok': False, 'error': type(e).__name__ + ': ' + str(e), 'metrics': adapter.metrics}
        result['gateway_calls'] = gateway.calls
        results.append(result)
        (out / 'RESULTS.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'attempt_id': aid, 'stream': streaming, 'ok': result['ok'], 'first_content_ms': adapter.metrics.get('first_content_ms'), 'completion_ms': adapter.metrics.get('completion_ms'), 'usage': (result.get('answer') or {}).get('usage'), 'error': result.get('error')}, ensure_ascii=False), flush=True)
        if not result['ok'] or gateway.pending_settlement or gateway.event_log_errors:
            break
    summary = {'run_id': run_id, 'attempts': len(results), 'real_model_calls': len(results), 'all_ok': all(r['ok'] for r in results), 'streaming_first_content_observed': any(r['ok'] and r['metrics'].get('stream_requested') and r['metrics'].get('ttft_observable') for r in results), 'phase_budget_summary': budget.summary(), 'not_a_harness_performance_comparison': True}
    (out / 'SUMMARY.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    (HERE / 'TIMING_PROBE_LATEST.json').write_text(json.dumps({'path': str(out), **summary}, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
