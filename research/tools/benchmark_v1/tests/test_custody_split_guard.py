# -*- coding: utf-8 -*-
"""custody_split_guard 的合成夹具测试 + 真实数据冒烟（Benchmark V1 Phase 2 §8.1）。

合成夹具：临时目录造 stories_root（manifest + exposure.json）+ 正式分派表 CSV +
custody 账本 JSON，覆盖四条强制片：
  G1 谱系组跨 split 必须报（声明组与 manifest 派生组两条路径）；
  G2 CF 孪生同 split 过 / 跨 split 必须报；
  G3 HOLDOUT 本体或谱系组成员被消费（exposure.json / 账本两路证据）必须报，
     干净的过，未登记账本的 HOLDOUT 本体必须报；
  G4 PENDING 行不参与 G1–G3 且数量核对生效。
真实冒烟：phase2 DATASET_V1_FINAL_ALLOCATION.csv 当前临时分派态（83 定稿 +
157 PENDING）必须 0 违例。

只读工具测试：不写 runs/、不改账本、不读 B 线正文 / GOLD_SIDECAR / logs / candidates。
运行：python -I tools/benchmark_v1/tests/test_custody_split_guard.py
"""
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
GUARD = os.path.abspath(os.path.join(HERE, "..", "custody_split_guard.py"))


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    m = importlib.util.module_from_spec(spec)
    sys.modules[name] = m
    spec.loader.exec_module(m)
    return m


guard = _load("custody_split_guard_under_test", GUARD)

HEADER = ("episode_id,kind,primary_topic,split,lineage_group_id,twin_group,"
          "twin_role,turns,domain,status,allocation_note")


def row(eid, split, lg="", twin="", kind="new", topic="T99", note=""):
    return ",".join([eid, kind, topic, split, lg, twin, "", "12", "测试", "ACCEPT", note])


def write_csv(path, lines):
    """带表头 + # 脚注行 + BOM 的正式分派表形状。"""
    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        f.write(HEADER + "\n")
        for ln in lines:
            f.write(ln + "\n")
        f.write("\n#QUOTA_TOTAL,occupied=0,pending=0,target=0\n")


def make_stories_root(root, manifests, exposures):
    """manifests: {wave: {manifest_file: [story dict]}}
    exposures: {wave: {eid: exposure dict}}"""
    os.makedirs(root, exist_ok=True)
    for wave, files in manifests.items():
        wdir = os.path.join(root, wave)
        os.makedirs(wdir, exist_ok=True)
        for fn, stories in files.items():
            with open(os.path.join(wdir, fn), "w", encoding="utf-8") as f:
                json.dump({"kind": "test_manifest", "stories": stories}, f)
    for wave, eps in exposures.items():
        for eid, x in eps.items():
            edir = os.path.join(root, wave, eid)
            os.makedirs(edir, exist_ok=True)
            with open(os.path.join(edir, "exposure.json"), "w", encoding="utf-8") as f:
                json.dump(x, f)


def make_ledger(path, stories):
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"kind": "story_custody_ledger", "created": "2026-11-08",
                   "stories": stories, "events": []}, f)


def clean_exposure():
    return {"status": "test", "experiments_used": [], "dev_exposed": False}


def vtypes(report):
    return [v["type"] for v in report["violations"]]


class CustodySplitGuardTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="csg_test_")
        self.stories_root = os.path.join(self.tmp, "stories_v2")
        self.csv = os.path.join(self.tmp, "allocation.csv")
        self.ledger = os.path.join(self.tmp, "ledger.json")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def run_guard(self, expected_pending=None):
        return guard.run_guard(self.csv, self.stories_root, self.ledger, expected_pending)

    # ---------- G1 谱系组跨 split ----------

    def test_derived_group_cross_split_reported(self):
        """manifest source 派生组（母本 A ← 续写 B）跨 DEV/HOLDOUT → 必须报；
        分派表 lineage_group_id 全空也必须由派生边兜住。"""
        make_stories_root(
            self.stories_root,
            {"w1": {"MANIFEST_T.json": [{"story_id": "A"}, {"story_id": "B", "source": "A"}]}},
            {"w1": {"A": clean_exposure(), "B": clean_exposure()}})
        write_csv(self.csv, [row("A", "DEV"), row("B", "HOLDOUT")])
        make_ledger(self.ledger, {"A": {"lifecycle": "SEALED", "consumed_by": []},
                                  "B": {"lifecycle": "SEALED", "consumed_by": []}})
        rep = self.run_guard()
        self.assertIn("group_cross_split", vtypes(rep))
        v = [x for x in rep["violations"] if x["type"] == "group_cross_split"][0]
        self.assertEqual(v["group"], "LIN-A")
        self.assertEqual(v["detail"]["assigned"], {"A": "DEV", "B": "HOLDOUT"})

    def test_declared_group_cross_split_reported(self):
        """分派表 lineage_group_id 声明组跨 split → 必须报。"""
        make_stories_root(self.stories_root, {}, {})
        write_csv(self.csv, [row("C", "DEV", lg="LG-X"), row("D", "SELECTION", lg="LG-X")])
        make_ledger(self.ledger, {})
        rep = self.run_guard()
        self.assertIn("group_cross_split", vtypes(rep))
        v = [x for x in rep["violations"] if x["type"] == "group_cross_split"][0]
        self.assertEqual(v["group"], "LG-X")

    def test_same_group_same_split_passes(self):
        """同组同区 → 0 违规（含派生组 LIN-E125 式两成员同 DEV 的真实形状）。"""
        make_stories_root(
            self.stories_root,
            {"w1": {"MANIFEST_T.json": [{"story_id": "E125"}, {"story_id": "LRE125",
                                                          "source": "E125"}]}},
            {"w1": {"E125": clean_exposure(), "LRE125": clean_exposure()}})
        write_csv(self.csv, [row("E125", "DEV", lg="LIN-E125"),
                             row("LRE125", "DEV", lg="LIN-E125", kind="old")])
        make_ledger(self.ledger, {"E125": {"lifecycle": "SEALED", "consumed_by": []},
                                  "LRE125": {"lifecycle": "SEALED", "consumed_by": []}})
        rep = self.run_guard()
        self.assertEqual(rep["violations"], [])

    # ---------- G2 CF 孪生 ----------

    def test_twin_same_split_passes(self):
        """孪生同 split → 过（声明列与 manifest source_story 边一致）。"""
        make_stories_root(
            self.stories_root,
            {"w1": {"MANIFEST_T.json": [{"story_id": "E333"},
                                        {"story_id": "E334", "source_story": "E333"}]}},
            {"w1": {"E333": clean_exposure(), "E334": clean_exposure()}})
        write_csv(self.csv, [row("E333", "DEV", lg="LG-T10-G1", twin="T10-G1"),
                             row("E334", "DEV", lg="LG-T10-G1", twin="T10-G1")])
        make_ledger(self.ledger, {"E333": {"lifecycle": "SEALED", "consumed_by": []},
                                  "E334": {"lifecycle": "SEALED", "consumed_by": []}})
        rep = self.run_guard()
        self.assertEqual(rep["violations"], [])

    def test_twin_cross_split_reported(self):
        """孪生跨 split → twin_cross_split 必须报（同组 G1 也报，属双重证据）。"""
        make_stories_root(self.stories_root, {}, {})
        write_csv(self.csv, [row("E333", "DEV", lg="LG-T10-G1", twin="T10-G1"),
                             row("E334", "SELECTION", lg="LG-T10-G1", twin="T10-G1")])
        make_ledger(self.ledger, {})
        rep = self.run_guard()
        self.assertIn("twin_cross_split", vtypes(rep))
        v = [x for x in rep["violations"] if x["type"] == "twin_cross_split"][0]
        self.assertEqual(v["detail"]["assigned"], {"E333": "DEV", "E334": "SELECTION"})

    # ---------- G3 HOLDOUT 未消费 ----------

    def test_holdout_consumed_exposure_reported(self):
        """HOLDOUT 本体 exposure.json experiments_used 非空 → 必须报。"""
        make_stories_root(
            self.stories_root, {},
            {"w1": {"H": {"status": "test", "experiments_used": ["lr2_batch"],
                          "dev_exposed": False}}})
        write_csv(self.csv, [row("H", "HOLDOUT")])
        make_ledger(self.ledger, {"H": {"lifecycle": "SEALED", "consumed_by": []}})
        rep = self.run_guard()
        self.assertIn("holdout_group_consumed_exposure", vtypes(rep))
        v = [x for x in rep["violations"]
             if x["type"] == "holdout_group_consumed_exposure"][0]
        self.assertEqual(v["episode"], "H")
        self.assertEqual(v["detail"]["member"], "H")

    def test_holdout_dev_exposed_reported(self):
        """HOLDOUT 本体 exposure.json dev_exposed=true → 必须报。"""
        make_stories_root(
            self.stories_root, {},
            {"w1": {"H": {"status": "test", "experiments_used": [],
                          "dev_exposed": True}}})
        write_csv(self.csv, [row("H", "HOLDOUT")])
        make_ledger(self.ledger, {"H": {"lifecycle": "SEALED", "consumed_by": []}})
        rep = self.run_guard()
        self.assertIn("holdout_dev_exposed", vtypes(rep))

    def test_holdout_consumed_ledger_reported(self):
        """HOLDOUT 本体账本 lifecycle=EXPOSED / consumed_by 非空 → 必须报。"""
        make_stories_root(self.stories_root, {}, {"w1": {"H": clean_exposure()}})
        write_csv(self.csv, [row("H", "HOLDOUT")])
        make_ledger(self.ledger, {"H": {"lifecycle": "EXPOSED",
                                        "consumed_by": ["fs1_a_20261030"]}})
        rep = self.run_guard()
        self.assertIn("holdout_group_consumed_ledger", vtypes(rep))

    def test_holdout_group_sibling_consumed_reported(self):
        """资格按组算：HOLDOUT 本体干净，但派生组兄弟篇被消费 → 必须报（两条证据路径）。"""
        for via_exposure in (True, False):
            root = os.path.join(self.tmp, "root_%s" % via_exposure)
            led = os.path.join(self.tmp, "ledger_%s.json" % via_exposure)
            csv_p = os.path.join(self.tmp, "alloc_%s.csv" % via_exposure)
            if via_exposure:
                exposures = {"w1": {"A": {"status": "test",
                                          "experiments_used": ["lr2_x"],
                                          "dev_exposed": False},
                                    "B": clean_exposure()}}
            else:
                exposures = {"w1": {"A": clean_exposure(), "B": clean_exposure()}}
            make_stories_root(
                root,
                {"w1": {"MANIFEST_T.json": [{"story_id": "A"},
                                            {"story_id": "B", "source": "A"}]}},
                exposures)
            write_csv(csv_p, [row("A", "DEV"), row("B", "HOLDOUT")])
            make_ledger(led, {"A": {"lifecycle": "SEALED",
                                    "consumed_by": [] if via_exposure else ["lr2_x"]},
                              "B": {"lifecycle": "SEALED", "consumed_by": []}})
            rep = guard.run_guard(csv_p, root, led)
            self.assertEqual(len([x for x in rep["violations"]
                                  if x["type"].startswith("holdout_group_consumed")]),
                             1, "via_exposure=%s" % via_exposure)
            v = [x for x in rep["violations"]
                 if x["type"].startswith("holdout_group_consumed")][0]
            self.assertEqual(v["episode"], "B", "违规应记在 HOLDOUT 篇名下")
            self.assertEqual(v["detail"]["member"], "A", "消费源是组内兄弟篇")

    def test_holdout_clean_passes(self):
        """HOLDOUT 本体干净（账本 SEALED 且 consumed_by 空 + exposure 干净）→ 过。"""
        make_stories_root(self.stories_root, {}, {"w1": {"H": clean_exposure()}})
        write_csv(self.csv, [row("H", "HOLDOUT")])
        make_ledger(self.ledger, {"H": {"lifecycle": "SEALED", "consumed_by": []}})
        rep = self.run_guard()
        self.assertEqual(rep["violations"], [])
        self.assertEqual(rep["warnings"], [])

    def test_holdout_unregistered_in_ledger_reported(self):
        """HOLDOUT 本体未登记账本 → 无法证明未消费，必须报（定稿前先 seed）。"""
        make_stories_root(self.stories_root, {}, {"w1": {"H": clean_exposure()}})
        write_csv(self.csv, [row("H", "HOLDOUT")])
        make_ledger(self.ledger, {"OTHER": {"lifecycle": "SEALED", "consumed_by": []}})
        rep = self.run_guard()
        self.assertIn("holdout_unregistered_in_custody_ledger", vtypes(rep))

    # ---------- G4 PENDING ----------

    def test_pending_ignored_and_counted(self):
        """PENDING 行不参与组检查（组内 PENDING 兄弟不算跨区）；
        --expected-pending 相等过、不等报 pending_count_mismatch。"""
        make_stories_root(
            self.stories_root,
            {"w1": {"MANIFEST_T.json": [{"story_id": "A"}, {"story_id": "B",
                                                            "source": "A"}]}},
            {"w1": {"A": clean_exposure(), "B": clean_exposure()}})
        write_csv(self.csv, [row("A", "DEV"), row("B", "PENDING")])
        make_ledger(self.ledger, {"A": {"lifecycle": "SEALED", "consumed_by": []}})
        rep = self.run_guard(expected_pending=1)
        self.assertEqual(rep["violations"], [])
        self.assertEqual(rep["summary"]["pending"], 1)
        self.assertNotIn("B", rep["splits"])
        rep2 = self.run_guard(expected_pending=2)
        self.assertIn("pending_count_mismatch", vtypes(rep2))

    # ---------- 输入错误与 CLI ----------

    def test_input_errors(self):
        make_stories_root(self.stories_root, {}, {})
        make_ledger(self.ledger, {})
        # 重复 episode_id
        write_csv(self.csv, [row("A", "DEV"), row("A", "SELECTION")])
        self.assertEqual(guard.main(["--allocation", self.csv,
                                     "--stories-root", self.stories_root,
                                     "--ledger", self.ledger]), 2)
        # split 非法（非 # 脚注行）
        write_csv(self.csv, [row("A", "DEVX")])
        self.assertEqual(guard.main(["--allocation", self.csv,
                                     "--stories-root", self.stories_root,
                                     "--ledger", self.ledger]), 2)
        # 缺必需列
        bad = os.path.join(self.tmp, "bad.csv")
        with open(bad, "w", encoding="utf-8-sig", newline="") as f:
            f.write("episode_id,split\nA,DEV\n")
        self.assertEqual(guard.main(["--allocation", bad,
                                     "--stories-root", self.stories_root,
                                     "--ledger", self.ledger]), 2)
        # 账本不存在
        write_csv(self.csv, [row("A", "DEV")])
        self.assertEqual(guard.main(["--allocation", self.csv,
                                     "--stories-root", self.stories_root,
                                     "--ledger", os.path.join(self.tmp, "nope.json")]), 2)

    def test_cli_exit_codes(self):
        make_stories_root(
            self.stories_root,
            {"w1": {"MANIFEST_T.json": [{"story_id": "A"}, {"story_id": "B",
                                                            "source": "A"}]}},
            {"w1": {"A": clean_exposure(), "B": clean_exposure()}})
        write_csv(self.csv, [row("A", "DEV"), row("B", "HOLDOUT")])
        make_ledger(self.ledger, {"A": {"lifecycle": "SEALED", "consumed_by": []},
                                  "B": {"lifecycle": "SEALED", "consumed_by": []}})
        p = subprocess.run([sys.executable, "-I", GUARD, "--allocation", self.csv,
                            "--stories-root", self.stories_root, "--ledger", self.ledger],
                           capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(p.returncode, 1)
        self.assertIn("VIOLATION [group_cross_split]", p.stdout)
        # --json 机器可读
        p2 = subprocess.run([sys.executable, "-I", GUARD, "--allocation", self.csv,
                             "--stories-root", self.stories_root, "--ledger",
                             self.ledger, "--json"],
                            capture_output=True, text=True, encoding="utf-8")
        rep = json.loads(p2.stdout)
        self.assertEqual(rep["summary"]["violations"], 1)

    # ---------- 真实数据冒烟 ----------

    def test_real_data_smoke_zero_violations(self):
        """phase2 正式分派表当前临时分派态（83 定稿 + 157 PENDING）→ 0 违例；
        PENDING 数量核对 157 亦通过。只读：账本与 stories_v2 元数据。"""
        p = subprocess.run([sys.executable, "-I", GUARD, "--expected-pending", "157",
                            "--json"],
                           capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(p.returncode, 0, "真实数据应 0 违例:\n%s" % p.stdout[-2000:])
        rep = json.loads(p.stdout)
        s = rep["summary"]
        self.assertEqual(s["violations"], 0)
        self.assertEqual(s["rows"], 240)
        self.assertEqual(s["assigned"], 83)
        self.assertEqual(s["pending"], 157)
        self.assertEqual(rep["expected_pending"], 157)
        # v2 分派表（2026-11-08）对 T10 全部 8 组声明 twin_group（含 6 个 PENDING 组），
        # guard 按"行上声明"计数，故 8 而非仅已建 2 组
        self.assertEqual(s["twin_groups"], 8)
        # 38 条警告全部是"新题未 seed"提示，不含未定稿组混排
        self.assertEqual(s["warnings"], s["unregistered_dev_selection"])
        self.assertTrue(all("未登记于 custody 账本" in w for w in rep["warnings"]))
        # 孪生组同区：T10-G1 全 DEV、T10-G2 全 SELECTION
        self.assertEqual(rep["splits"].get("E333"), "DEV")
        self.assertEqual(rep["splits"].get("E334"), "DEV")
        self.assertEqual(rep["splits"].get("E335"), "SELECTION")
        self.assertEqual(rep["splits"].get("E336"), "SELECTION")
        # 派生组兜底：LRE125 与 E125 同组
        self.assertIn("E125", rep["lineage_groups"]["LIN-E125"])
        self.assertIn("LRE125", rep["lineage_groups"]["LIN-E125"])

    def test_real_data_lineage_check_still_green(self):
        """回归：规则统一后 lineage_check 自身（同批真实输入的组派生路径）不受影响，
        两个工具组派生同源。B 线 manifest 元数据键除外（绝不读其正文）。"""
        lc = _load("lineage_check_for_smoke",
                   os.path.abspath(os.path.join(HERE, "..", "lineage_check.py")))
        edges, exposure_map, discovered = lc.scan_stories_root(
            os.path.join(os.path.dirname(HERE), "..", "..", "benchmark", "stories_v2"))
        groups = lc.build_groups(edges)
        self.assertIn("LIN-E125", groups)
        self.assertEqual(groups["LIN-E125"], {"E125", "LRE125"})
        self.assertIn("LIN-E048", groups)  # 泄露事件案例组仍可派生
        self.assertGreater(len(exposure_map), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
