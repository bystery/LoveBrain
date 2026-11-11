# -*- coding: utf-8 -*-
"""tools/test_uc_supp_rules.py — W18 组合候选纯离线单测（零模型调用、零网络）。

覆盖（W18_UNC_SUPPRESS_DESIGN_20261102.md §2.3/§2.5）：
  - R-W18-1 口径锚点门正反例（含设计点名的 4 个反例句）；
  - R-W18-2 同锚点取最新（严格更旧才丢，同轮都保留）；
  - R-W18-3 T 标注用真实 turn；
  - R-W18-4 / U / 总预算 240 / 120 / 360 截断；
  - 跨段去重（标点归一化包含比较）与无信号空块；
  - 纯函数不改入参；
  - a/b/c 行格式逐字；
  - U 段渲染与 uncertain_rules.build_block 逐字节同构；
  - LRE146（S 全程为空）candidate 与父本逐字节相同。

用法：python tools/test_uc_supp_rules.py
"""
import copy
import importlib.util
import json
import os
import sys
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CAND = os.path.join(ROOT, "candidates", "window_expand_b_norecap_uc_supp")
PARENT = os.path.join(ROOT, "candidates", "window_expand_b_norecap_uc")


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


sup = _load("w18_supp_rules_test", os.path.join(CAND, "mechanism", "suppression_rules.py"))
unc = _load("w18_unc_local_test", os.path.join(CAND, "mechanism", "uncertain_rules.py"))


def ev(uid, turn, text, role="HER", ts="2026-11-02 20:00"):
    return {"uid": uid, "role": role, "text": text, "ts": ts, "turn": turn}


def u16(s):
    return len((s or "").encode("utf-16-le")) // 2


class TestStrongValuesGate(unittest.TestCase):
    def test_positive_examples(self):
        cases = {
            "我八十": {"80"},
            "五十五那页翻过去哈": {"55"},
            "我那个九点半的号只能取消了": {"9"},
            "我果断把12月5号的号取消了": {"12", "5"},
            "队长把和新群的友谊赛定在1月18日周日": {"1", "18"},
            "下午四点档": {"4"},            # TOD 兜底：(NUM)点
        }
        for clause, want in cases.items():
            self.assertEqual(sup.strong_values(clause), want, clause)

    def test_negative_examples_named_in_design(self):
        # 设计 §2.3 R-W18-1 点名：变回一个人 / 反手连练两周 / 六人满编 / 顺一点
        for clause in ("就我又变回一个人了", "反手连练两周", "六人满编",
                       "顺一点就慢点开", "回到后场就是胜利"):
            self.assertEqual(sup.strong_values(clause), set(), clause)

    def test_c_line_gate_blocks_non_anchor_cancel(self):
        # "反手连练两周" 所在的 revert 消息：clause 无口径值 → 不入段
        events = [ev(0, 7, "反手连练两周，今天实战终于能顶回去了！虽然能把球回到后场就是胜利")]
        block, cu, n = sup.suppression_block(events)
        self.assertEqual((block, cu, n), ("", 0, 0))

    def test_uncertain_cue_excluded(self):
        events = [ev(0, 3, "费用要是五十五那页翻过去哈")]
        block, _cu, _n = sup.suppression_block(events)
        self.assertEqual(block, "")


class TestLineFormats(unittest.TestCase):
    def test_c_cancel_and_revert_format_with_real_turn(self):
        events = [
            ev(0, 7, "那油费还按咱俩原来的规矩，我八十，五十五那页翻过去哈"),
            ev(1, 8, "九点半的号取消了"),
        ]
        block, cu, n = sup.suppression_block(events)
        self.assertIn("# 【口径基线：以下旧说法已作废，勿再沿用】", block)
        self.assertIn("- 现行口径以「我八十」（T7）为准", block)
        self.assertIn("- 已声明作废：「五十五那页翻过去哈」（T7）", block)
        self.assertIn("- 已声明作废：「九点半的号取消了」（T8）", block)
        self.assertEqual(n, 3)
        self.assertLessEqual(cu, 240)

    def test_supersede_pair_format(self):
        events = [
            ev(0, 1, "周六出发油费一人八十"),
            ev(1, 2, "周六出发油费改成一人五十五"),
        ]
        block, _cu, _n = sup.suppression_block(events)
        self.assertIn("- 「周六出发油费一人八十」已作废（T1改）→ "
                      "现行：「周六出发油费改成一人五十五」", block)

    def test_cancelled_pair_format_when_old_value_not_口径(self):
        # a/b 行不受 R-W18-1 限制（其判定由 update_pass 给出）；旧值无强口径仍出 b 行
        events = [
            ev(0, 1, "九点半到了社区门口碰头"),
            ev(1, 2, "社区门口碰头取消了"),
        ]
        block, _cu, _n = sup.suppression_block(events)
        self.assertIn("- 「九点半到了社区门口碰头」已作废（T1改）→ 现行：（已取消）", block)


class TestAnchorDedup(unittest.TestCase):
    def test_newer_replaces_older_same_value(self):
        events = [
            ev(0, 25, "费用她说还按之前说定的一人五十五"),
            ev(1, 26, "那个五十五就当彻底没存在过"),
        ]
        block, _cu, _n = sup.suppression_block(events)
        self.assertIn("- 已声明作废：「那个五十五就当彻底没存在过」（T26）", block)
        self.assertNotIn("现行口径以「费用她说还按", block)

    def test_same_turn_both_kept(self):
        events = [ev(0, 5, "五十五那页翻过去哈"),
                  ev(1, 5, "费用还按一人五十五，别再改了")]
        block, _cu, n = sup.suppression_block(events)
        self.assertEqual(n, 2)
        self.assertIn("已声明作废：「五十五那页翻过去哈」（T5）", block)
        self.assertIn("现行口径以「费用还按一人五十五」（T5）为准", block)

    def test_disjoint_anchors_both_kept(self):
        events = [ev(0, 4, "九点半的号取消了"),
                  ev(1, 6, "12月5号聚餐作废")]
        block, _cu, n = sup.suppression_block(events)
        self.assertEqual(n, 2)
        self.assertIn("（T4）", block)
        self.assertIn("（T6）", block)

    def test_real_turn_label_not_uid(self):
        # uid=0..3 但真实轮次是 5；标注必须 T5（b2 的 uid+1 会写成 T4）
        events = [ev(0, 5, "九点半的号取消了"), ev(1, 5, "一句废话"),
                  ev(2, 5, "又一句废话"), ev(3, 5, "还是废话")]
        block, _cu, _n = sup.suppression_block(events)
        self.assertIn("（T5）", block)
        self.assertNotIn("（T4）", block)


class TestBudgets(unittest.TestCase):
    def test_s_budget_240_keeps_newest(self):
        # 每轮一个题材不同的作废句（避免 update_pass 互相 cancel 出 a/b 行）
        texts = ["我把3号羽毛球馆的场地预订取消了", "我把5号火锅店的包间预订取消了",
                 "我把7号电影院的晚场票取消了", "我把9号理发店的洗剪吹预约取消了",
                 "我把11号体检中心的空腹检查取消了", "我把14号航班的选座服务取消了",
                 "我把17号面试的会议室预订取消了", "我把20号火车票的靠窗座位取消了",
                 "我把23号讲座的入场名额取消了", "我把26号演出的前排座位取消了",
                 "我把29号维修单的上门时间取消了", "我把32号保洁的深度清洁取消了"]
        events = [ev(i, i + 1, t) for i, t in enumerate(texts)]
        block, cu, n = sup.suppression_block(events)
        self.assertLessEqual(cu, 240)
        self.assertGreater(n, 0)
        self.assertLess(n, len(texts))                # 确实发生截断
        self.assertIn("（T12）", block)               # 最新保留
        self.assertNotIn("（T1）", block)              # 最旧被丢
        lines = block.split("\n")[1:]
        self.assertEqual(len(lines), n)
        turns = [int(x.split("（T", 1)[1].split("）")[0].rstrip("改")) for x in lines]
        self.assertEqual(turns, sorted(turns))       # 展示旧→新

    def test_u_budget_120(self):
        items = [{"text": "这是第%d条很长的未定事项描述文字用来占预算" % i, "where": "本轮"}
                 for i in range(1, 9)]
        block, kept, cu = sup.build_u_block(
            items, unc.UNCERTAIN_TITLE, unc.UNCERTAIN_FOOTER, unc.UNCERTAIN_BUDGET_CU)
        self.assertLessEqual(cu, 120)
        self.assertLess(len(kept), len(items))
        self.assertGreater(len(kept), 0)
        self.assertIn(unc.UNCERTAIN_TITLE, block)
        self.assertIn(unc.UNCERTAIN_FOOTER, block)

    def test_total_budget_360_trims_oldest_s_lines(self):
        lines = ["- 现行口径以「第%d号八十」（T%d）为准" % (i, i) for i in range(1, 9)]
        s_cu = sup._used_of(lines)
        self.assertLessEqual(s_cu, 240)
        uc_cu = 200                                   # 构造 S+U=~440 > 360
        kept, kept_cu, overflow = sup.enforce_total_budget(lines, uc_cu)
        self.assertEqual(overflow, 1)
        self.assertLessEqual(kept_cu + uc_cu, 360)
        self.assertNotIn(lines[0], kept)              # 最旧被丢
        self.assertIn(lines[-1], kept)                # 最新保留
        # 未超限时不动：S≤240 + U≤120 构造性成立
        kept2, cu2, ov2 = sup.enforce_total_budget(lines, 120)
        self.assertEqual((kept2, cu2, ov2), (lines, s_cu, 0))

    def test_total_budget_construction_invariant(self):
        self.assertLessEqual(sup.SUPPRESSION_BUDGET_CU + unc.UNCERTAIN_BUDGET_CU,
                             sup.TOTAL_BUDGET_CU)


class TestCrossDedupAndEmpty(unittest.TestCase):
    def test_cross_dedup_keeps_s_drops_u(self):
        s_block = "# 【口径基线：以下旧说法已作废，勿再沿用】\n- 已声明作废：「五十五那页翻过去哈」（T7）"
        items = [{"text": "五十五那页翻过去哈", "where": "T7"},
                 {"text": "电池到底换不换", "where": "本轮"}]
        kept, xdedup = sup.cross_dedup(s_block, items)
        self.assertEqual(xdedup, 1)
        self.assertEqual([x["text"] for x in kept], ["电池到底换不换"])

    def test_cross_dedup_punctuation_normalized(self):
        s_block = "# 【口径基线：以下旧说法已作废，勿再沿用】\n- 已声明作废：「五十五，那页翻过去哈！」（T7）"
        items = [{"text": "五十五那页翻过去哈", "where": "T7"}]
        kept, xdedup = sup.cross_dedup(s_block, items)
        self.assertEqual((kept, xdedup), ([], 1))

    def test_cross_dedup_no_false_positive(self):
        s_block = "# 【口径基线：以下旧说法已作废，勿再沿用】\n- 已声明作废：「九点半的号取消了」（T4）"
        items = [{"text": "九点半的号还作数吗", "where": "本轮"}]
        kept, xdedup = sup.cross_dedup(s_block, items)
        self.assertEqual(xdedup, 0)
        self.assertEqual(len(kept), 1)

    def test_empty_signals(self):
        self.assertEqual(sup.suppression_block([]), ("", 0, 0))
        self.assertEqual(sup.suppression_block([ev(0, 1, "今天天气不错")]), ("", 0, 0))
        self.assertEqual(sup.cross_dedup("", [{"text": "x", "where": "本轮"}])[1], 0)
        self.assertEqual(sup.build_u_block([], unc.UNCERTAIN_TITLE,
                                           unc.UNCERTAIN_FOOTER,
                                           unc.UNCERTAIN_BUDGET_CU), ("", [], 0))

    def test_u_block_matches_uncertain_rules_verbatim(self):
        prior = [ev(0, 1, "他说电池好像有问题，得换")]
        cur = [{"uid": 1, "role": "HER", "text": "他说电池好像有问题，到底换不换？",
                "ts": "2026-11-02 21:00"}]
        ref_block, ref_items, ref_cu = unc.build_block(prior, cur, 2)
        self.assertNotEqual(ref_block, "")
        items = unc.find_uncertain(prior, cur, 2)
        block, kept, cu = sup.build_u_block(items, unc.UNCERTAIN_TITLE,
                                            unc.UNCERTAIN_FOOTER, unc.UNCERTAIN_BUDGET_CU)
        self.assertEqual((block, kept, cu), (ref_block, ref_items, ref_cu))


class TestPureFunctions(unittest.TestCase):
    def test_inputs_not_mutated(self):
        events = [
            ev(0, 1, "周六出发油费一人八十"),
            ev(1, 2, "周六出发油费改成一人五十五"),
            ev(2, 3, "九点半的号取消了"),
            ev(3, 4, "反手连练两周，回到后场"),
        ]
        snapshot = copy.deepcopy(events)
        sup.suppression_block(events)
        self.assertEqual(events, snapshot)

        prior = copy.deepcopy(events)
        cur = [{"uid": 9, "role": "HER", "text": "那个五十五还算数吗？", "ts": "2026-11-02 22:00"}]
        cur_snap = copy.deepcopy(cur)
        items = unc.find_uncertain(prior, cur, 5)
        items_snap = copy.deepcopy(items)
        kept, _xd = sup.cross_dedup(sup.suppression_block(prior)[0], items)
        sup.build_u_block(kept, unc.UNCERTAIN_TITLE, unc.UNCERTAIN_FOOTER,
                          unc.UNCERTAIN_BUDGET_CU)
        self.assertEqual(prior, events)
        self.assertEqual(cur, cur_snap)
        self.assertEqual(items, items_snap)


class TestMechanismIntegration(unittest.TestCase):
    class _GW:
        def __init__(self):
            self.prompts = []

        def generate(self, system, prompt, images=None, tag="main"):
            self.prompts.append(prompt)
            return {"text": '{"response": {"recommended": "收到。"}}'}

    def _run(self, cand_name, episode):
        mech = _load("w18_mech_%s" % cand_name,
                     os.path.join(ROOT, "candidates", cand_name,
                                  "mechanism", "mechanism.py"))
        gw = self._GW()
        sandbox = os.path.join(ROOT, "logs", "_test_uc_supp_tmp", cand_name)
        h = mech.init(os.path.join(ROOT, "candidates", cand_name, "assets"),
                      {"persona": episode.get("persona") or {},
                       "initial_kb": episode.get("initial_kb") or {}}, gw, sandbox)
        notes = []
        for i, turn in enumerate(episode.get("turns") or [], 1):
            res = mech.run_turn(h, {"input": turn.get("input") or {}, "turn_index": i}, gw)
            notes.append(res.get("notes") or "")
        return gw.prompts, notes

    def test_lre146_s_empty_prompts_byte_identical_to_parent(self):
        with open(os.path.join(ROOT, "benchmark", "episodes", "LRE146.json"),
                  encoding="utf-8") as f:
            ep = json.load(f)
        a, _na = self._run("window_expand_b_norecap_uc", ep)
        b, _nb = self._run("window_expand_b_norecap_uc_supp", ep)
        self.assertEqual(len(a), len(b))
        self.assertEqual(sum(1 for x, y in zip(a, b) if x == y), len(a))

    def test_no_signal_story_prompt_identical(self):
        ep = {"episode_id": "SYNTH", "persona": {"her": "小棠", "me": "阿远"},
              "initial_kb": {}, "turns": [
                  {"input": {"messages": [{"role": "HER", "ts": "2026-11-02 20:00",
                                           "text": "今天天气不错，出来走走？"}],
                             "clock": "2026-11-02 20:05"}},
                  {"input": {"messages": [{"role": "HER", "ts": "2026-11-02 20:06",
                                           "text": "街角那家店还开着呢。"}],
                             "clock": "2026-11-02 20:10"}},
              ]}
        a, _na = self._run("window_expand_b_norecap_uc", ep)
        b, _nb = self._run("window_expand_b_norecap_uc_supp", ep)
        self.assertEqual(a, b)

    def test_cross_segment_dedup_in_prompt(self):
        ep = {"episode_id": "SYNTH-X", "persona": {"her": "小棠", "me": "阿远"},
              "initial_kb": {}, "turns": [
                  {"input": {"messages": [{"role": "HER", "ts": "2026-11-02 20:00",
                                           "text": "我那个九点半的号只能取消了"}],
                             "clock": "2026-11-02 20:01"}},
                  {"input": {"messages": [{"role": "HER", "ts": "2026-11-02 20:05",
                                           "text": "老板说'九点半的号只能取消了'，那还算不算数？"}],
                             "clock": "2026-11-02 20:06"}},
              ]}
        prompts, notes = self._run("window_expand_b_norecap_uc_supp", ep)
        self.assertIn("xdedup=1", notes[-1])
        self.assertIn(sup.SUPPRESSION_TITLE, prompts[-1])
        self.assertNotIn(unc.UNCERTAIN_TITLE, prompts[-1])

    def test_snapshot_restore_roundtrip_and_same_keys_as_parent(self):
        ep = {"episode_id": "SYNTH-S", "persona": {"her": "小棠", "me": "阿远"},
              "initial_kb": {}, "turns": [
                  {"input": {"messages": [{"role": "HER", "ts": "2026-11-02 20:00",
                                           "text": "周六出发油费一人八十"}],
                             "clock": "2026-11-02 20:01"}},
                  {"input": {"messages": [{"role": "HER", "ts": "2026-11-02 20:05",
                                           "text": "周六出发油费改成一人五十五"}],
                             "clock": "2026-11-02 20:06"}},
                  {"input": {"messages": [{"role": "HER", "ts": "2026-11-02 20:10",
                                           "text": "那就这么定，路上慢点。"}],
                             "clock": "2026-11-02 20:11"}},
              ]}
        snaps = {}
        for cand_name in ("window_expand_b_norecap_uc", "window_expand_b_norecap_uc_supp"):
            mech = _load("w18_snap_%s" % cand_name,
                         os.path.join(ROOT, "candidates", cand_name,
                                      "mechanism", "mechanism.py"))
            gw = self._GW()
            h = mech.init(os.path.join(ROOT, "candidates", cand_name, "assets"),
                          {"persona": ep["persona"], "initial_kb": {}}, gw,
                          os.path.join(ROOT, "logs", "_test_uc_supp_tmp", "snap_" + cand_name))
            for i, turn in enumerate(ep["turns"][:2], 1):
                mech.run_turn(h, {"input": turn["input"], "turn_index": i}, gw)
            snap = mech.snapshot(h)
            snaps[cand_name] = json.loads(snap)
            h2 = mech.restore(os.path.join(ROOT, "logs", "_test_uc_supp_tmp",
                                           "snap_" + cand_name), snap)
            turn = ep["turns"][2]
            mech.run_turn(h, {"input": turn["input"], "turn_index": 3}, gw)
            p1 = gw.prompts[-1]
            mech.run_turn(h2, {"input": turn["input"], "turn_index": 3}, gw)
            p2 = gw.prompts[-1]
            self.assertEqual(p1, p2, cand_name)       # 快照往返同构
        self.assertEqual(sorted(snaps["window_expand_b_norecap_uc"]),
                         sorted(snaps["window_expand_b_norecap_uc_supp"]))


if __name__ == "__main__":
    sys.exit(unittest.main())
