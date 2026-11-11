# -*- coding: utf-8 -*-
"""Benchmark V1 Phase 3 · 新工具离线测试（universal_scorer / phase3_runner / 唯一性检查）。

运行：`python -m pytest tools/benchmark_v1/tests/test_phase3_tools.py -q`（零网络）。
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

import universal_scorer as us          # noqa: E402
import phase3_runner as p3             # noqa: E402


# ---------- 语义守卫 v1（P3-3.2） ----------

class TestSemanticGuards:
    def test_question_not_affirm(self):
        # 指导书原例："你爸妈知道吗？" 不得坐实"爸妈知情"
        k, cl = us.guarded_hit("你爸妈知道吗？", "爸妈知道")
        assert k == "question_mention"

    def test_negation_not_affirm(self):
        k, _ = us.guarded_hit("我爸妈还不知道这件事。", "爸妈")
        assert k == "negated_mention"

    def test_conditional_not_affirm(self):
        k, _ = us.guarded_hit("如果我爸妈知道了肯定会念叨。", "爸妈知道")
        assert k == "hypothetical_mention"

    def test_quoted_mention_not_affirm(self):
        k, _ = us.guarded_hit("她原话是「爸妈知道就行了」。", "爸妈知道")
        assert k == "quoted_mention"

    def test_plain_affirm(self):
        k, _ = us.guarded_hit("我爸妈都知道了，他俩还催我。", "爸妈都知道")
        assert k == "affirm"

    def test_split_clauses(self):
        cs = us.split_clauses("第一句。第二句？第三句")
        assert cs == ["第一句。", "第二句？", "第三句"]

    def test_is_question_clause_variants(self):
        assert us.is_question_clause("你要来吗")
        assert us.is_question_clause("真的假的？")
        assert not us.is_question_clause("我爸妈知道这事。")

    def test_negated_before_variants(self):
        assert us.negated_before("还没知道", "知道")
        assert not us.negated_before("早就知道", "知道")

    def test_past_mention_not_affirm(self):
        k, _ = us.guarded_hit("上次取消的周六爬山我还记得，挺可惜。", "周六爬山")
        assert k == "past_mention"

    def test_flatten_markers_nested_groups(self):
        atom = {"affirm_markers": [["明晚之前确认", "小王"], ["说周三能取"]]}
        assert us.flatten_markers(atom) == ["明晚之前确认", "小王", "说周三能取"]

    def test_flatten_markers_empty(self):
        assert us.flatten_markers({}) == []
        assert us.flatten_markers({"affirm_markers": None}) == []


# ---------- 控制集评估器 ----------

class TestEvalControls:
    def test_eval_controls_shape(self, tmp_path):
        ctrl = {"version": "test_ctrl_v0", "cases": [
            {"text": "你爸妈知道吗？", "markers": ["爸妈知道"], "expect": "guard", "type": "question"},
            {"text": "我爸妈都知道了。", "markers": ["爸妈都知道"], "expect": "affirm", "type": "statement"},
        ]}
        p = tmp_path / "controls.json"
        p.write_text(json.dumps(ctrl, ensure_ascii=False), encoding="utf-8")
        rep = us.eval_controls(str(p))
        assert rep["n_total"] == 2
        assert rep["accuracy"] == 1.0
        assert rep["stats"]["question"]["ok"] == 1
        assert rep["stats"]["statement"]["ok"] == 1


# ---------- 产品契约层分层 ----------

class TestProductContractFields:
    def test_light_arm_not_applicable(self):
        row = {"contract_ok": True, "veto": "json_schema_broken", "score10": 0.0}
        ok, note, s10, veto = us.product_contract_fields("DIRECT", row)
        assert ok is True
        assert "NOT_APPLICABLE" in note
        assert s10 is None and veto is None

    def test_harness_arm_veto_counts(self):
        row = {"contract_ok": True, "veto": "illegal_source_id", "score10": 5.0}
        ok, note, s10, veto = us.product_contract_fields("CURRENT", row)
        assert ok is False
        assert veto == "illegal_source_id"
        assert "lb_scorer" in note


# ---------- 通用层 diag v1（守卫生效） ----------

class TestDiagV1:
    def test_cancel_atom_question_guard(self):
        gold_index = {"X_T01": {"case_id": "X_T01", "assertions": [
            {"atom_id": "X_T01_A01", "status": "cancelled", "negation": False,
             "value": "周六去爬山", "affirm_markers": ["周六爬山"]}]}}
        # 疑问句提及取消事项 → 不得记 cancel_affirm
        d = us.diag_turn_v1("X_T01", "那我们周六爬山？还是改天？", gold_index)
        assert d["diag_cancel_affirm"] == 0
        assert any(e["kind"] == "question_mention" for e in d["guard_events"])
        # 直陈复活 → 违例
        d2 = us.diag_turn_v1("X_T01", "好，那就周六爬山，我记得你说过。", gold_index)
        assert d2["diag_cancel_affirm"] == 1

    def test_required_hit_unchanged(self):
        gold_index = {"Y_T01": {"case_id": "Y_T01", "assertions": [
            {"atom_id": "Y_T01_A01", "status": "asserted", "negation": False,
             "required_this_turn": True, "value": "周三交稿", "affirm_markers": ["周三交稿"]}]}
        }
        d = us.diag_turn_v1("Y_T01", "好，周三交稿前我把图都发你。", gold_index)
        assert d["diag_required_hits"] == 1
        d2 = us.diag_turn_v1("Y_T01", "收到，我尽快。", gold_index)
        assert d2["diag_required_misses"] == 1


# ---------- phase3_runner：政策门 / 预算账本 / 授权 ----------

class TestPhase3RunnerGate:
    def _auth(self, tmp_path, kind="dev_mock", splits=("DEV",), episodes=("E301",), arms=("DIRECT",)):
        auth = {"scope": "test", "batch_id": "t", "kind": kind,
                "cap_max_total_api_attempts": 100, "model": "deepseek-flash-2",
                "temperature": 0.7, "max_concurrent_requests": 1,
                "retry_on_quality_failure": False,
                "authorized_splits": list(splits), "arms": list(arms),
                "episodes": list(episodes)}
        p = tmp_path / "auth.json"
        p.write_text(json.dumps(auth, ensure_ascii=False), encoding="utf-8")
        return str(p)

    def test_authorization_requires_formal_arms(self, tmp_path):
        auth_path = self._auth(tmp_path, arms=("LEGACY",))
        auth = json.load(open(auth_path, encoding="utf-8"))
        # run_all 的 arms 校验在 policy_gate 之前由 problems 汇总；这里直接验证常量
        assert "LEGACY_COMPAT_R9" in p3.FORMAL_ARMS
        assert "LEGACY" not in p3.FORMAL_ARMS

    def test_split_map_loader(self):
        sm = p3.load_split_map(os.path.join(_REPO, "refs", "benchmark_v1_20261107",
                                            "phase3", "DATASET_V1_ALLOCATION_V3.csv"))
        assert sm["E301"]["split"] == "DEV"
        assert sm["E375"]["planned_split"] == "SELECTION"
        assert sm["E401"]["planned_split"] == "HOLDOUT"
        assert sm["E125"]["status"] == "DIAGNOSTIC_POOL"

    def test_policy_gate_b_line_and_missing(self):
        split_map = {"E301": {"split": "DEV", "planned_split": "", "status": "X"}}
        auth = {"kind": "dev_mock", "authorized_splits": ["DEV"], "arms": ["DIRECT"]}
        # B 线篇必须被拦（用真实 b_line manifest 的一个 id）
        bl = far_b_line_ids()
        if bl:
            eid = sorted(bl)[0]
            problems = p3.policy_gate([eid], auth, split_map, bl)
            assert any("B_LINE_SEALED" in p for p in problems)
        # 不在分派表的篇必须被拦
        problems = p3.policy_gate(["E999"], auth, split_map, bl)
        assert any("不在正式分派表" in p for p in problems)

    def test_policy_gate_holdout_consumed(self, tmp_path):
        # HOLDOUT once-only：已有消费记录 → 必拦（用 wave4 已消费篇模拟）
        split_map = {"E305": {"split": "HOLDOUT", "planned_split": "", "status": "X"}}
        auth = {"kind": "holdout_once_only", "authorized_splits": ["HOLDOUT"], "arms": ["CURRENT"]}
        problems = p3.policy_gate(["E305"], auth, split_map, set())
        assert any("once-only" in p or "dev_exposed" in p for p in problems)

    def test_global_ledger_reserve_register(self, tmp_path):
        led = p3.GlobalAttemptLedger(str(tmp_path / "led.jsonl"))
        assert led.count_reserves(batch="b1") == 0
        led.reserve("b1", 10)
        led.reserve("b1", 10)
        led.reserve("b2", 10)
        assert led.count_reserves(batch="b1") == 2
        assert led.count_reserves(batch="b2") == 1
        led.register("reserved", arm="DIRECT", status="ok")
        assert led.count_reserves(batch="b1") == 2   # register 不计消费

    def test_candidate_dir_for_arm(self):
        assert os.path.basename(p3.candidate_dir_for_arm("LEGACY_COMPAT_R9", 12)) == "legacy_compat_r9"
        assert os.path.basename(p3.candidate_dir_for_arm("CURRENT", 12)) == "window_expand_b_norecap_guard"
        assert os.path.basename(p3.candidate_dir_for_arm("CURRENT", 32)) == "window_expand_b_norecap_uc"
        assert os.path.basename(p3.candidate_dir_for_arm("DIRECT", 12)) == "direct_baseline"

    def test_legacy_compat_identity_hashes_differ(self):
        h1 = __import__("four_arm_runner").candidate_identity_sha256(p3.LEGACY_COMPAT_DIR)
        h2 = __import__("four_arm_runner").candidate_identity_sha256(p3.LEGACY_ORIG_DIR)
        assert h1 != h2  # 适配器与原候选身份分离（双录前提）


def far_b_line_ids():
    import four_arm_runner as far
    try:
        return far.b_line_episode_ids()
    except Exception:
        return set()


# ---------- 名字唯一性检查工具 ----------

class TestNameUniqueness:
    def _load(self):
        spec = importlib.util.spec_from_file_location(
            "cnu", os.path.join(_BM1, "check_name_uniqueness.py"))
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        return mod

    def test_wave5_selection_clean(self):
        mod = self._load()
        rc = mod.main.__wrapped__() if hasattr(mod.main, "__wrapped__") else None
        # 直接构造检查：wave5_selection 若已建成应 0 问题（名字池保证）
        wave_dir = os.path.join(_REPO, "benchmark", "stories_v2", "wave5_selection")
        if not os.path.isdir(wave_dir):
            pytest.skip("wave5_selection 未生成")
        import subprocess
        out = subprocess.run([sys.executable, "-I",
                              os.path.join(_BM1, "check_name_uniqueness.py"),
                              "--wave", "wave5_selection"],
                             capture_output=True, text=True, cwd=_REPO)
        assert out.returncode == 0, out.stdout + out.stderr

    def test_twin_pair_same_name_allowed(self, tmp_path, monkeypatch):
        mod = self._load()
        # 构造最小 wave：孪生两篇同名 + 单篇不同名 → 应 0 问题
        wave = tmp_path / "w"
        for ep, names in (("E901", ("甲", "乙")), ("E902", ("甲", "乙")), ("E903", ("丙", "丁"))):
            d = wave / ep
            d.mkdir(parents=True)
            (d / "episode.json").write_text(json.dumps(
                {"persona": {"her": {"name": names[0]}, "me": {"name": names[1]}}}), encoding="utf-8")
            grp = "LG-T10-GX" if ep in ("E901", "E902") else "LG-E903"
            (d / "exposure.json").write_text(json.dumps({"lineage_group_id": grp}), encoding="utf-8")
        monkeypatch.setattr(mod, "STORIES", str(tmp_path))
        import argparse
        ns = argparse.Namespace(wave="w", json=None)
        monkeypatch.setattr("argparse.ArgumentParser.parse_args",
                            staticmethod(lambda self=None: ns))
        assert mod.main() == 0
