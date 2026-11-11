# -*- coding: utf-8 -*-
"""Benchmark V1 Phase 1 · 离线测试套件（指导书 §5.2 清单的实现面）。

运行：`python -m pytest tools/benchmark_v1/tests -q`（全部零网络；不触 B 线正文）。
"""
import importlib.util
import json
import os
import sys

import pytest

_HERE = os.path.dirname(os.path.abspath(__file__))
_BM1 = os.path.dirname(_HERE)
_REPO = os.path.abspath(os.path.join(_BM1, "..", ".."))
sys.path.insert(0, _BM1)
sys.path.insert(0, os.path.join(_REPO, "arena"))

import four_arm_runner as far          # noqa: E402  导入即注册 bm1_mock_light
import lb_gateway                      # noqa: E402
from lb_runner import run_episode      # noqa: E402
import normalize_output as no          # noqa: E402
import check_provider as cp            # noqa: E402
import gold_diag                       # noqa: E402

DIRECT_CAND = far.BASELINE_CAND_DIRS["DIRECT"]
COT_CAND = far.BASELINE_CAND_DIRS["COT"]


# ---------- 夹具：合成 2 轮剧集（不触真实题库写入） ----------

def _make_episode(tmp_path):
    ep = {
        "episode_id": "TSYN1",
        "persona": {"me": "测试用户", "her": "测试对象"},
        "initial_kb": {"note": "synthetic"},
        "turns": [
            {"case_id": "TSYN1_T01", "turn_type": "reply",
             "input": {"messages": [{"role": "HER", "text": "周末去爬山吗？", "ts": "2026-11-07 10:00"}],
                       "clock": "2026-11-07 10:00", "may_decline": False},
             "gold": {}},
            {"case_id": "TSYN1_T02", "turn_type": "reply",
             "input": {"messages": [{"role": "ME", "text": "去啊，几点出发？", "ts": "2026-11-07 10:05"},
                                    {"role": "HER", "text": "七点我校门口", "ts": "2026-11-07 10:06"}],
                       "clock": "2026-11-07 10:06", "may_decline": False},
             "gold": {}},
        ],
    }
    p = tmp_path / "episode.json"
    p.write_text(json.dumps(ep, ensure_ascii=False), encoding="utf-8")
    return str(p)


def _run_arm(arm, ep_path, out_dir):
    cand = DIRECT_CAND if arm == "DIRECT" else COT_CAND
    return run_episode(ep_path, cand, out_dir, "bm1_mock_light",
                       run_id="t_%s" % arm)


@pytest.fixture()
def synthetic_episode(tmp_path):
    return _make_episode(tmp_path)


# ---------- 1/2/5/6 生命周期与输入等价 ----------

def test_light_mock_run_and_normalize(tmp_path, synthetic_episode):
    out = str(tmp_path / "DIRECT" / "TSYN1")
    _run_arm("DIRECT", synthetic_episode, out)
    rows = list(no.iter_run_dir(str(tmp_path), arms=("DIRECT",)))
    assert len(rows) == 2
    for r in rows:
        assert r["parse_ok"] is True and r["contract_ok"] is True
        assert r["action"] == "reply" and r["primary_text"].strip()


def test_direct_cot_input_equivalence(tmp_path, synthetic_episode):
    _run_arm("DIRECT", synthetic_episode, str(tmp_path / "DIRECT" / "TSYN1"))
    _run_arm("COT", synthetic_episode, str(tmp_path / "COT" / "TSYN1"))
    spec = importlib.util.spec_from_file_location(
        "t_cot_mech", os.path.join(COT_CAND, "mechanism", "mechanism.py"))
    cot = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(cot)
    for tdir in sorted(os.listdir(str(tmp_path / "DIRECT" / "TSYN1"))):
        if tdir.startswith("_") or not os.path.isfile(
                os.path.join(tmp_path, "DIRECT", "TSYN1", tdir, "calls.json")):
            continue
        d = json.load(open(os.path.join(tmp_path, "DIRECT", "TSYN1", tdir, "calls.json"),
                           encoding="utf-8"))
        c = json.load(open(os.path.join(tmp_path, "COT", "TSYN1", tdir, "calls.json"),
                           encoding="utf-8"))
        assert d[0]["user"] == c[0]["user"], "user 必须逐字节相等"
        assert c[0]["system"].endswith(cot.COT_INSTRUCTION)
        assert c[0]["system"][: -len(cot.COT_INSTRUCTION) - 2] == d[0]["system"]


def test_future_invisibility_and_history_accumulation(tmp_path, synthetic_episode):
    _run_arm("DIRECT", synthetic_episode, str(tmp_path / "DIRECT" / "TSYN1"))
    calls2 = json.load(open(os.path.join(tmp_path, "DIRECT", "TSYN1", "turn_02_TSYN1_T02",
                                         "calls.json"), encoding="utf-8"))
    user2 = calls2[0]["user"]
    assert "周末去爬山吗？" in user2          # T01 可见历史累积
    assert "七点我校门口" in user2            # T02 本轮消息
    # 未来轮不可见在真实题库上由 capture_and_validate 批级保证（见下 integration）


# ---------- 3 CURRENT 双域分流 ----------

def test_current_dual_domain_routing():
    assert far.resolve_current_candidate(8) == far.CURRENT_GUARD
    assert far.resolve_current_candidate(12) == far.CURRENT_GUARD
    assert far.resolve_current_candidate(24) == far.CURRENT_GUARD
    assert far.resolve_current_candidate(25) == far.CURRENT_UC
    assert far.resolve_current_candidate(40) == far.CURRENT_UC


# ---------- 4 归一化：不挑备选 / 全部状态 ----------

def test_normalize_no_cherry_pick():
    raw = json.dumps({
        "response": {"recommended": "首选", "bad_boy": "更长的备选文本文本", "playful": None,
                     "warm": "温柔的备选"},
        "directions": ["跟进", None, None, None],
        "analysis": {"topic_status": "same", "topic_label": "x",
                     "scene_facts": [], "ongoing": []}}, ensure_ascii=False)
    r = no.normalize_harness(raw)
    assert r["primary_text"] == "首选"          # 固定取 recommended，绝不挑最长/最优
    assert r["primary_source"] == "response.recommended"
    assert r["secondary_channels"] == 3         # 只计数，不参与主比较


def test_normalize_state_matrix():
    assert no.normalize_light("")["error_type"] == "empty"
    assert no.normalize_light("不是JSON")["error_type"] == "parse"
    good = no.normalize_light('{"action":"reply","recommended":"好"}')
    assert good["action"] == "reply" and good["contract_ok"]
    nr = no.normalize_light('{"action":"no_reply","recommended":null}')
    assert nr["action"] == "no_reply" and nr["contract_ok"] and nr["primary_text"] is None
    bad = no.normalize_light('{"action":"no_reply","recommended":"文本"}')
    assert bad["error_type"] == "contract"
    dec = no.normalize_harness('{"response":{"recommended":null},"decline_reason":"对方已下线"}')
    assert dec["action"] == "no_reply" and dec["contract_ok"]
    conflict = no.normalize_harness('{"response":{"recommended":"正文"},"decline_reason":"x"}')
    assert conflict["error_type"] == "contract"


# ---------- 7 政策门：B 线与 SEALED 阻断 ----------

def test_policy_blocks_b_line_and_sealed():
    assert any("B_LINE_SEALED" == p for p in far.pilot_policy_check("E045"))
    ledger = json.load(open(os.path.join(
        _REPO, "refs", "experiment_protocol_20261023", "global_harness_program_20261030",
        "STORY_CUSTODY_LEDGER.json"), encoding="utf-8"))
    sealed = [k for k, v in ledger["stories"].items()
              if v.get("lifecycle") == "SEALED" and k not in far.b_line_episode_ids()]
    assert sealed, "账本中应存在非 B 线的 SEALED 故事"
    probs = far.pilot_policy_check(sealed[0])
    assert any("需 EXPOSED" in p for p in probs)


# ---------- 8 attempt 记账与上限 ----------

def test_attempt_counting(tmp_path):
    d = tmp_path / "a" / "e1"
    d.mkdir(parents=True)
    with open(d / "gateway_events.jsonl", "w", encoding="utf-8") as f:
        for _ in range(3):
            f.write('{"kind": "attempt_started"}\n')
        f.write('{"kind": "attempt_completed"}\n')
    assert far.count_attempts(str(tmp_path)) == 3



def _mc4(base, tmp_path):
    import json as _json
    import os as _os
    b = dict(base)
    b["max_concurrent_requests"] = 4
    p = _os.path.join(str(tmp_path), "ok_mc4.json")
    with open(p, "w", encoding="utf-8") as f:
        _json.dump(b, f, ensure_ascii=False)
    return p


def test_authorization_enforcement(tmp_path):
    auth_path = os.path.join(_REPO, "refs", "benchmark_v1_20261107", "phase1",
                             "PILOT_AUTHORIZATION.json")
    far.load_authorization(auth_path)  # 合法授权应通过
    base = json.load(open(auth_path, encoding="utf-8"))
    # Phase 2（2026-11-08）：并发口径放宽为 int>=1（授权文件声明制，运行器实际串行）；
    # mc=4 由"应拒"改为合法用例，新增 mc=0/非 int 应拒。
    far.load_authorization(_mc4(base, tmp_path))
    for mutate, frag in (
            ({"model": "other-model"}, "deepseek-flash-2"),
            ({"max_concurrent_requests": 0}, "max_concurrent_requests"),
            ({"max_concurrent_requests": "four"}, "max_concurrent_requests"),
            ({"retry_on_quality_failure": True}, "retry")):
        bad = dict(base)
        bad.update(mutate)
        badp = tmp_path / ("bad_%s.json" % frag[:4])
        badp.write_text(json.dumps(bad, ensure_ascii=False), encoding="utf-8")
        with pytest.raises(RuntimeError) as ei:
            far.load_authorization(str(badp))
        assert frag in str(ei.value)


def test_live_precheck_blocks_without_key(monkeypatch, tmp_path):
    monkeypatch.delenv("LB_API_KEY", raising=False)
    auth = json.load(open(os.path.join(_REPO, "refs", "benchmark_v1_20261107", "phase1",
                                       "PILOT_AUTHORIZATION.json"), encoding="utf-8"))
    problems = far.live_precheck(auth, ["E032"], str(tmp_path))
    assert any("LB_API_KEY" in p for p in problems)


# ---------- 9 密钥遮盖 ----------

def test_provider_redact_and_no_key_blocked():
    key = "sk-secret-abc123"
    assert key not in cp.redact("Authorization: Bearer sk-secret-abc123", key)
    res, code = cp.run_check(key="", do_chat=False)
    assert res["verdict"] == "BLOCKED" and code == 3
    assert "LB_API_KEY" in res["reason"]


# ---------- 10 gold 诊断层 ----------

def test_gold_diag_semantics():
    gi = {"X_T01": {"gold_empty": False, "assertions": [
        {"atom_id": "a1", "status": "cancelled", "negation": False,
         "value": "周日一起看电影", "affirm_markers": ["周日看电影"]},
        {"atom_id": "a2", "status": "asserted", "negation": False,
         "required_this_turn": True, "value": "约在周六下午三点",
         "affirm_markers": ["周六下午三点"]}]}}
    r = gold_diag.diag_turn("X_T01", "好呀，那我们周日看电影", gi)
    assert r["diag_status"] == "OK" and r["diag_cancel_affirm"] == 1
    assert r["diag_required_misses"] == 1 and r["diag_required_hits"] == 0
    r2 = gold_diag.diag_turn("X_T01", "好的，周六下午三点见", gi)
    assert r2["diag_cancel_affirm"] == 0 and r2["diag_required_hits"] == 1
    r3 = gold_diag.diag_turn("X_T99", "任意", gi)
    assert r3["diag_status"] == "N/A_no_gold"
    r4 = gold_diag.diag_turn("X_T01", "随便聊聊", {**gi, "X_T01": {"gold_empty": True}})
    assert r4["diag_status"] == "N/A_no_gold"


# ---------- 11 盲评包确定性 ----------

def test_blind_selection_deterministic():
    import make_blind_review as mbr
    assert mbr.select_positions("E032", 4) == ([3, 5, 8, 10], 12)
    assert mbr.select_positions("E037", 2) == ([3, 6], 8)
    assert mbr.select_positions("E191", 3) == ([3, 7, 10], 12)
    assert mbr.select_positions("LRE117", 3) == ([10, 21, 31], 40)


# ---------- 12 未动文件完整性 ----------

def test_untouched_integrity():
    p = os.path.join(_REPO, "refs", "benchmark_v1_20261107", "phase1",
                     "UNTOUCHABLE_HASHES.json")
    if not os.path.isfile(p):
        pytest.skip("UNTOUCHABLE_HASHES.json 不存在")
    import hashlib
    man = json.load(open(p, encoding="utf-8"))
    drift = []
    for rel, h in man["files"].items():
        fp = os.path.join(_REPO, rel)
        if not os.path.isfile(fp):
            drift.append("MISSING " + rel)
            continue
        cur = hashlib.sha256(open(fp, "rb").read()).hexdigest()
        if cur != h:
            drift.append("CHANGED " + rel)
    assert not drift, drift[:10]


# ===========================================================================
# Phase 2 §8.1（谱系组隔离规则统一）与 §9（LEGACY_COMPAT_R9）追加测试（2026-11-08）
# ===========================================================================

import hashlib          # noqa: E402
import subprocess       # noqa: E402

import lineage_check as lc  # noqa: E402  _BM1 已在 sys.path

COMPAT_CAND = os.path.join(_BM1, "baseline_candidates", "legacy_compat_r9")
R9_CAND = os.path.join(_REPO, "candidates", "action_policy_r9_slim")


def _load_mech_module(cand_dir):
    """独立加载候选 mechanism（不经 lb_runner，便于直接对拍 init 产物）。"""
    name = "t_mech_%s_%d" % (os.path.basename(cand_dir.rstrip("/\\")), abs(id(cand_dir)) % 10**8)
    spec = importlib.util.spec_from_file_location(
        name, os.path.join(cand_dir, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


# ---------- §8.1 lineage_check：合成谱系树（tmp，零真实题库写入） ----------

def _make_lineage_tree(root):
    """合成 stories_v2 结构：1 个 LRE 长剧组（LIN-EA）、1 个 CF 孪生组（LIN-EB1，
    其中 EB2 为 B 线成员）、1 个独立已消费篇 EC。"""
    def wave(name, manifest_name, manifest):
        w = root / name
        w.mkdir(parents=True)
        (w / manifest_name).write_text(json.dumps(manifest, ensure_ascii=False),
                                       encoding="utf-8")
        return w

    def episode(wdir, eid, used):
        d = wdir / eid
        d.mkdir()
        (d / "exposure.json").write_text(json.dumps(
            {"experiments_used": used, "dev_exposed": bool(used)}, ensure_ascii=False),
            encoding="utf-8")

    wa = wave("waveA", "MANIFEST_LREA.json",
              {"kind": "long_drama_ext",
               "stories": [{"story_id": "LREA", "source": "EA", "files": {}}]})
    episode(wa, "EA", []); episode(wa, "LREA", [])
    wb = wave("waveB", "STAGE1_MANIFEST_CF.json",
              {"kind": "cf",
               "stories": [{"story_id": "EB2", "source_story": "EB1",
                            "perturbation": "time"}]})
    episode(wb, "EB1", []); episode(wb, "EB2", [])
    wc = wave("waveC", "MANIFEST.json", {"kind": "story_v2", "stories": [{"story_id": "EC"}]})
    episode(wc, "EC", ["BATCH-X"])
    (root / "B_LINE_MANIFEST.json").write_text(json.dumps(
        {"kind": "b_line_manifest", "episodes": {"EB2": {"source_path": "synthetic"}}},
        ensure_ascii=False), encoding="utf-8")


def _write_csv(root, rows):
    p = root / "splits.csv"
    p.write_text("episode_id,split\n" + "".join("%s,%s\n" % r for r in rows),
                 encoding="utf-8")
    return str(p)


def test_lineage_check_detects_cross_split_bline_and_holdout_consumption(tmp_path):
    root = tmp_path / "stories"
    root.mkdir()
    _make_lineage_tree(root)
    # 违规布置：EA(DEV) vs LREA(HOLDOUT) 跨分区；B 线关联组 LIN-EB1 的 EB1 进 DEV；
    # 已消费 EC 拿 HOLDOUT。
    csv_bad = _write_csv(tmp_path, [("EA", "DEV"), ("LREA", "HOLDOUT"),
                                    ("EB1", "DEV"), ("EC", "HOLDOUT")])
    rep = lc.run_check(str(root), csv_bad, str(root / "B_LINE_MANIFEST.json"))
    types = {(v["type"], v["group"]) for v in rep["violations"]}
    assert ("cross_split", "LIN-EA") in types, rep["violations"]
    assert ("b_line_related_in_dev_selection", "LIN-EB1") in types, rep["violations"]
    assert any(v["type"] == "holdout_group_consumed" and v["group"] == "self:EC"
               and v["detail"]["consumed_member"] == "EC"
               and v["detail"]["experiments_used"] == ["BATCH-X"]
               for v in rep["violations"]), rep["violations"]
    assert rep["b_line_related_groups"] == ["LIN-EB1"]
    assert rep["summary"]["violations"] == 3


def test_lineage_check_clean_layout_passes(tmp_path):
    root = tmp_path / "stories"
    root.mkdir()
    _make_lineage_tree(root)
    # 干净布置：长剧组同区（同 DEV）；B 线关联组整体 HOLDOUT 且零消费；
    # 已消费 EC 只进 DEV（dev 不要求 unseen）。
    csv_ok = _write_csv(tmp_path, [("EA", "DEV"), ("LREA", "DEV"),
                                   ("EB1", "HOLDOUT"), ("EC", "DEV")])
    rep = lc.run_check(str(root), csv_ok, str(root / "B_LINE_MANIFEST.json"))
    assert rep["violations"] == [], rep["violations"]
    # CLI 可跑：--json 输出可解析且 exit 0（真实命令行入口验证）
    env = dict(os.environ, PYTHONIOENCODING="utf-8", PYTHONUTF8="1")
    proc = subprocess.run(
        [sys.executable, "-I", os.path.join(_BM1, "lineage_check.py"),
         "--stories-root", str(root), "--split-csv", csv_ok,
         "--b-line-manifest", str(root / "B_LINE_MANIFEST.json"), "--json"],
        capture_output=True, env=env, cwd=_REPO)
    assert proc.returncode == 0, proc.stderr.decode("utf-8", "replace")
    out = json.loads(proc.stdout.decode("utf-8"))
    assert out["summary"]["violations"] == 0
    # 违规布置下 CLI exit 1（跨分区检测命中走命令行同样成立）
    csv_bad = _write_csv(tmp_path, [("EA", "DEV"), ("LREA", "HOLDOUT")])
    proc2 = subprocess.run(
        [sys.executable, "-I", os.path.join(_BM1, "lineage_check.py"),
         "--stories-root", str(root), "--split-csv", csv_bad, "--json"],
        capture_output=True, env=env, cwd=_REPO)
    assert proc2.returncode == 1
    out2 = json.loads(proc2.stdout.decode("utf-8"))
    assert any(v["type"] == "cross_split" for v in out2["violations"])


def test_r9_slim_untouchable_hash():
    """硬约束守护：candidates/action_policy_r9_slim 任何文件未被修改。"""
    man = json.load(open(os.path.join(
        _REPO, "refs", "benchmark_v1_20261107", "phase1", "UNTOUCHABLE_HASHES.json"),
        encoding="utf-8"))
    targets = {rel: h for rel, h in man["files"].items()
               if rel.startswith("candidates/action_policy_r9_slim/")}
    assert len(targets) >= 9, "UNTOUCHABLE 清单应覆盖 r9_slim 全部文件"
    drift = []
    for rel, h in targets.items():
        fp = os.path.join(_REPO, rel)
        if not os.path.isfile(fp):
            drift.append("MISSING " + rel)
        elif hashlib.sha256(open(fp, "rb").read()).hexdigest() != h:
            drift.append("CHANGED " + rel)
    assert not drift, drift


def test_lineage_check_real_stories_v2_inventory(tmp_path):
    """真实 stories_v2 只读回归：谱系组发现数与 LINEAGE_LEAKAGE_REPORT 审计口径
    一致（26 组），B 线关联组 = 审计 8 组 + LIN-E048（E048 本体在 B 线 → 其组
    同为 B 关联）。每条谱系边必须带 derivation 溯源（三档派生规则）。"""
    sv = os.path.join(_REPO, "benchmark", "stories_v2")
    if not os.path.isdir(sv):
        pytest.skip("stories_v2 不存在")
    edges, exposure, discovered = lc.scan_stories_root(sv)
    groups = lc.build_groups(edges)
    assert len(groups) == 26, sorted(groups)
    assert all(e.get("derivation") in ("source_field", "title_pattern", "id_pattern")
               for e in edges)
    bl = json.load(open(os.path.join(_REPO, "benchmark", "b_line",
                                     "B_LINE_MANIFEST.json"), encoding="utf-8"))
    bmem = sorted(bl["episodes"].keys())
    ep_group = {m: g for g, ms in groups.items() for m in ms}
    rel = sorted({ep_group[m] for m in bmem if m in ep_group})
    assert rel == ["LIN-E048", "LIN-E072", "LIN-E084", "LIN-E090", "LIN-E105",
                   "LIN-E119", "LIN-E123", "LIN-E139", "LIN-E142"], rel
    # E048 泄露事件复现：E048(DEV) 与其已消费兄弟 LRE048(HOLDOUT) 同列 → 跨分区必报
    csv = tmp_path / "splits_real.csv"
    csv.write_text("episode_id,split\nE048,DEV\nLRE048,HOLDOUT\n", encoding="utf-8")
    rep = lc.run_check(sv, str(csv))
    assert any(v["type"] == "cross_split" and v["group"] == "LIN-E048"
               for v in rep["violations"])


# ---------- §9 LEGACY_COMPAT_R9：str 等价 / dict 跑通 / 登记一致 ----------

def test_legacy_compat_str_persona_equivalence(tmp_path):
    """str persona：LEGACY_COMPAT_R9 与直接加载 r9_slim 的 init 产物语义等价，
    且整集 mock 运行的每轮 system/user prompt 逐字节相等。"""
    compat = _load_mech_module(COMPAT_CAND)
    r9 = _load_mech_module(R9_CAND)
    meta = {"mode": "single",
            "persona": {"me": "测试用户，喜欢爬山", "her": "测试对象，周末常约饭"},
            "initial_kb": {"warmth": "三档", "note": "synthetic"}}
    gw1 = lb_gateway.ModelGateway(lb_gateway.MockAdapter())
    gw2 = lb_gateway.ModelGateway(lb_gateway.MockAdapter())
    h1 = compat.init(os.path.join(COMPAT_CAND, "assets"), meta, gw1,
                     str(tmp_path / "sb1"))
    h2 = r9.init(os.path.join(R9_CAND, "assets"), meta, gw2, str(tmp_path / "sb2"))
    s1 = json.loads(compat.snapshot(h1))
    s2 = json.loads(r9.snapshot(h2))
    assert s1["mem"] == s2["mem"]                    # 种子事实（persona:/kbin:）一致
    assert s1["seed_fids"] == s2["seed_fids"]
    assert s1["plan_items"] == s2["plan_items"]
    assert h1["system_prompt"] == h2["system_prompt"]  # 资产转发后 system 构建一致

    ep = {
        "episode_id": "TSYN1",
        "persona": {"me": "测试用户", "her": "测试对象"},
        "initial_kb": {"note": "synthetic"},
        "turns": [
            {"case_id": "TSYN1_T01", "turn_type": "reply", "gold": {},
             "input": {"messages": [{"role": "HER", "text": "周末去爬山吗？",
                                     "ts": "2026-11-08 10:00"}],
                       "clock": "2026-11-08 10:00", "may_decline": False}},
            {"case_id": "TSYN1_T02", "turn_type": "reply", "gold": {},
             "input": {"messages": [{"role": "ME", "text": "去啊，几点出发？",
                                     "ts": "2026-11-08 10:05"},
                                    {"role": "HER", "text": "七点我校门口",
                                     "ts": "2026-11-08 10:06"}],
                       "clock": "2026-11-08 10:06", "may_decline": False}},
        ],
    }
    ep_path = tmp_path / "episode_str.json"
    ep_path.write_text(json.dumps(ep, ensure_ascii=False), encoding="utf-8")
    m_c = run_episode(str(ep_path), COMPAT_CAND, str(tmp_path / "COMPAT"), "mock",
                      run_id="t_compat_str")
    m_r = run_episode(str(ep_path), R9_CAND, str(tmp_path / "R9"), "mock",
                      run_id="t_r9_str")
    assert m_c["complete"] and m_r["complete"] and m_c["turns"] == m_r["turns"] == 2
    for i, turn in enumerate(ep["turns"]):
        td_c = os.path.join(str(tmp_path / "COMPAT"), "turn_%02d_%s" % (i + 1, turn["case_id"]))
        td_r = os.path.join(str(tmp_path / "R9"), "turn_%02d_%s" % (i + 1, turn["case_id"]))
        c = json.load(open(os.path.join(td_c, "calls.json"), encoding="utf-8"))
        r = json.load(open(os.path.join(td_r, "calls.json"), encoding="utf-8"))
        assert len(c) == len(r) == 1
        assert c[0]["system"] == r[0]["system"], "turn %d system 必须逐字节相等" % (i + 1)
        assert c[0]["user"] == r[0]["user"], "turn %d user 必须逐字节相等" % (i + 1)


def test_legacy_compat_dict_persona_runs_two_turns(tmp_path):
    """dict persona（wave3 真实字段结构，基准 E191）：原 r9_slim init 即崩
    （r1 mechanism.py:270 .strip()），LEGACY_COMPAT_R9 完整跑通 2 轮 mock；
    转换后语义信息（name/traits/boundaries）进入 prompt。"""
    persona = {   # 与 benchmark/stories_v2/wave3/E191/episode.json 实际结构同构
        "her": {"name": "沈一诺",
                "traits": ["桌游局固定组织者", "会做规则速查表", "垫钱不糊涂必当周对账",
                           "对放鸽子先火大后算账"],
                "boundaries": ["费用当周结清不留尾巴", "低消缺口摊进人均要提前说清",
                               "新人入局先学规则再上桌"]},
        "me": {"name": "阿树", "traits": ["德式桌游熟练工", "转账准时记得清",
                                          "提建议给台阶"]},
        "relationship_stage": "渐暧昧（桌游群里的牌友，常坐邻座）",
    }
    ep = {
        "episode_id": "TSYN2",
        "persona": persona,
        "initial_kb": {"warmth": "三档"},
        "turns": [
            {"case_id": "TSYN2_T01", "turn_type": "reply", "gold": {},
             "input": {"messages": [{"role": "HER", "text": "周六桌游局来吗？",
                                     "ts": "2026-11-08 12:00"}],
                       "clock": "2026-11-08 12:00", "may_decline": False}},
            {"case_id": "TSYN2_T02", "turn_type": "reply", "gold": {},
             "input": {"messages": [{"role": "ME", "text": "来，带.rules速查表不",
                                     "ts": "2026-11-08 12:05"},
                                    {"role": "HER", "text": "我带，你带零钱",
                                     "ts": "2026-11-08 12:06"}],
                       "clock": "2026-11-08 12:06", "may_decline": False}},
        ],
    }
    ep_path = tmp_path / "episode_dict.json"
    ep_path.write_text(json.dumps(ep, ensure_ascii=False), encoding="utf-8")
    # 原版直接跑同一合成集：init 崩（证明适配是跑通的必要条件，非恒真断言）
    with pytest.raises(AttributeError) as ei:
        run_episode(str(ep_path), R9_CAND, str(tmp_path / "R9FAIL"), "mock",
                    run_id="t_r9_dict_fail")
    assert "strip" in str(ei.value)
    # 适配候选完整跑通 2 轮
    m = run_episode(str(ep_path), COMPAT_CAND, str(tmp_path / "COMPAT"), "mock",
                    run_id="t_compat_dict")
    assert m["complete"] and m["turns"] == 2
    c1 = json.load(open(os.path.join(str(tmp_path / "COMPAT"),
                                     "turn_01_TSYN2_T01", "calls.json"), encoding="utf-8"))
    u1 = c1[0]["user"]
    assert "沈一诺" in u1 and "阿树" in u1                       # name 保留
    assert "桌游局固定组织者" in u1 and "垫钱不糊涂必当周对账" in u1  # traits 逐项
    assert "底线：费用当周结清不留尾巴" in u1                     # boundaries 带类别前缀
    assert "渐暧昧（桌游群里的牌友，常坐邻座）" in u1             # relationship_stage 透传
    # 调用方传入的 episode 数据未被改写（适配只作用于 init 内部副本）
    assert ep["persona"]["me"] == {"name": "阿树", "traits": ["德式桌游熟练工",
                                                              "转账准时记得清",
                                                              "提建议给台阶"]}
    assert isinstance(ep["persona"]["her"], dict)


def test_legacy_compat_registry_identity_consistent():
    """LEGACY_COMPAT_R9 身份哈希与 ARM_REGISTRY.json 登记值一致；原 LEGACY 记录
    仍指向未改动的 r9_slim。"""
    cur = far.candidate_identity_sha256(COMPAT_CAND)
    reg = json.load(open(os.path.join(_REPO, "refs", "benchmark_v1_20261107",
                                      "phase1", "ARM_REGISTRY.json"), encoding="utf-8"))
    arm = reg["arms"].get("LEGACY_COMPAT_R9")
    assert arm, "ARM_REGISTRY 应含 LEGACY_COMPAT_R9 条目"
    assert arm["identity_sha256"].get("legacy_compat_r9") == cur
    assert "经输入适配" in arm.get("compat_variant", {}).get("note", "") or \
           "经输入适配" in json.dumps(arm, ensure_ascii=False), \
        "登记必须明确'经输入适配的历史版本'"
    # 原 LEGACY 记录未变
    assert reg["arms"]["LEGACY"]["candidate_dir"] == "candidates/action_policy_r9_slim"
    assert reg["arms"]["LEGACY"]["identity_sha256"]["action_policy_r9_slim"] == \
        "c57c093608060d740f16867604885daa6eef05d95f15d569fce0e2d04e55c7e8"
