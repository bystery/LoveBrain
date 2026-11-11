#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""test_retr_idf.py — 检索改进候选（phase1_retr_idf）离线回归（锁定用例，先于任何真实调用）。

构成：
  A. 冻结案例回放（L3B_MINI_FULL t5/t6 窗口 × 冻结点 11 条 facts）：
     t5 过敏事实被取回（父版本 LCS 漏检）且 t6 过敏/猫事实被拒（父版本凭"今天"误取）。
  B. 锁定新用例（本文件即锁定记录，先于对照真实调用落库）：
     同义改述(部分词汇重叠) / 无关日期词 / 跨人物跨主题泛化 / HER-ME 归属限制 / 
     纠正场景 / 过期状态（部分处理，登记） / 高频泛词不误取。
  C. 父版本对照（同 assets/params，写回逐字节一致；唯一差异=检索与追踪）。
  D. 来源追踪正确性（stored/scored/injected 三态在 trace 中可复核；无 gold 泄漏）。
已知限制（登记，不在本候选修）：
  - 纯同义零字面重叠（实体字都不同）召回不了——词面检索的本质限制；
  - HER/ME 说话人归属在检索层不可分（存储无 speaker 字段）；
  - 过期状态靠时间指示词剥离部分缓解，无真正的状态失效机制。
全部离线，无任何模型调用。
"""
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for p in ("arena", "tools"):
    sys.path.insert(0, os.path.join(ROOT, p))
os.chdir(ROOT)

import l3b_frozen_fork  # noqa: E402  (仅复用其事件配对常量，不触发其 main)
import importlib.util as _ilu
_spec = _ilu.spec_from_file_location(
    "retr_idf_mech",
    os.path.join(ROOT, "candidates/phase1_retr_idf/mechanism/mechanism.py"))
newmech = _ilu.module_from_spec(_spec)
_spec.loader.exec_module(newmech)

FROZEN_FACTS = [
    {"clock": "2026-08-24 20:12", "text": "她室友今天抱回来一只猫"},
    {"clock": "2026-08-24 20:12", "text": "她今天20:12说对猫毛过敏，不能靠近"},
    {"clock": "2026-08-24 20:20", "text": "程汐把猫关在自己房间门外，让室友自己吸"},
    {"clock": "2026-08-24 20:20", "text": "程汐因为过敏撸不了猫，只能云吸"},
    {"clock": "2026-08-25 12:40", "text": "她周末去徒步了，说山顶风景很好"},
    {"clock": "2026-08-25 12:40", "text": "她说改天发照片给我"},
    {"clock": "2026-10-26 12:00", "text": "程汐在2026-10-26 11:10至11:59之间连续发送大量重复的短语消息"},
    {"clock": "2026-10-26 12:30", "text": "她室友今天12:25提出想把猫放到她房间晒太阳"},
    {"clock": "2026-10-26 12:30", "text": "她今天12:30在问要不要拦这件事"},
    {"clock": "2026-10-26 18:20", "text": "她今天18:15说食堂麻辣香锅排了很长的队"},
    {"clock": "2026-10-26 18:20", "text": "她今天18:20说香锅味道还行"},
]

ep = json.load(open(os.path.join(ROOT, "benchmark/episodes/L3B_MINI_FULL.json"),
                    encoding="utf-8"))
_turns = {t["case_id"]: t for t in ep["turns"]}


def _window(cid):
    return "".join(m.get("text", "") for m in _turns[cid]["input"]["messages"])


def _selected(window, facts):
    return [i for i, _ in newmech._retrieve(window, facts)]


def main():
    fails = []

    # ---- A. 冻结案例回放（真实时点库存；第九波判决 §5.3 修正） ----
    # t5 检索时点库存=前 7 条、t6 时点=前 9 条（食堂事实 9/10 由 t6 对话自身写入，
    # 任何臂在 t6 检索时都不可见——旧夹具用最终 11 条属校准口径错误，已修）。
    sel5 = set(_selected(_window("L3B_t05"), FROZEN_FACTS[:7]))
    sel6 = set(_selected(_window("L3B_t06"), FROZEN_FACTS[:9]))
    if 1 not in sel5:
        fails.append("A1/t5(前7条): 猫毛过敏事实(fact_id=1)未被取回: %s" % sorted(sel5))
    if sel6:
        fails.append("A2/t6(前9条): 任何事实被误取: %s" % sorted(sel6))
    # A2x 最终 11 条库存=额外检索单测（非运行时回放，不冒充时点行为）：
    sel5_full = set(_selected(_window("L3B_t05"), FROZEN_FACTS))
    sel6_full = set(_selected(_window("L3B_t06"), FROZEN_FACTS))
    if not {9, 10} <= sel6_full:
        fails.append("A3/t6(最终11条,额外单测): 食堂事实未召回: %s" % sorted(sel6_full))
    if {4, 5, 6} & sel6_full:
        fails.append("A4/t6(最终11条): 无关事实(徒步/照片/重复短语)被取回: %s" % sorted(sel6_full))

    # ---- B. 锁定新用例（跨人物/主题，先于真实调用冻结） ----
    LOCKED = [
        # (名称, facts, window, 期望 selected 的 fact 下标)
        ("B1/同义改述(实体字重叠)",
         [{"clock": "2026-10-01 10:00", "text": "她报了瑜伽班，每周去三次"},
          {"clock": "2026-10-02 10:00", "text": "她哥哥在深圳做程序员"}],
         "她的瑜伽课怎么样了 会不会太累",
         {0}),
        ("B2/无关日期词不召回",
         [{"clock": "2026-10-01 10:00", "text": "她昨天说想学吉他"},
          {"clock": "2026-10-01 10:00", "text": "她说公司食堂涨薪了"}],
         "今天食堂的番茄炒蛋太咸了",
         {1}),
        ("B3/跨人物跨主题泛化",
         [{"clock": "2026-09-01 10:00", "text": "老王欠我两百块说月底还"},
          {"clock": "2026-09-01 10:00", "text": "表妹高考完想去海边玩"}],
         "老王把钱转我了 终于",
         {0}),
        ("B4/HER-ME归属限制(登记已知限制:检索层不可分)",
         [{"clock": "2026-09-01 10:00", "text": "我想养一只柯基"}],
         "她说她也想养狗了",
         {0}),
        ("B5/纠正场景(更正后事实召回)",
         [{"clock": "2026-09-01 10:00", "text": "她说下周去北京出差"},
          {"clock": "2026-09-02 10:00", "text": "她说北京行程取消了改去成都"}],
         "你成都那边安排好了吗",
         {1}),
        ("B6/过期状态(时间指示词剥离部分缓解,完整失效机制未做)",
         [{"clock": "2026-09-01 10:00", "text": "她这周在出差"}],
         "这周末有空吗 一起吃饭",
         set()),
        ("B7/高频泛词窗口不误取",
         [{"clock": "2026-09-01 10:00", "text": "她哥哥在深圳做程序员"}],
         "今天加班好累 项目的进度太赶了 明天还要开会",
         set()),
        ("B8/纯同义零字面重叠(已知限制:词面检索召回不了,登记)",
         [{"clock": "2026-09-01 10:00", "text": "她对芒果过敏"}],
         "她吃了那份甜品以后嘴唇肿了",
         set()),
    ]
    for name, facts, window, want in LOCKED:
        got = set(_selected(window, facts))
        if got != want:
            fails.append("%s: selected=%s ≠ 期望 %s" % (name, sorted(got), sorted(want)))

    # ---- C. 父版本对照：同输入写回逐字节一致（唯一差异=检索+追踪） ----
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        "parent_mech", os.path.join(ROOT, "candidates/phase0_beta_single/mechanism/mechanism.py"))
    parent = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(parent)

    class StubGW:
        name = "stub"

        def __init__(self, text):
            self.text = text

        def generate(self, system, user, images=None, model=None,
                     temperature=None, timeout_s=120, tag="main"):
            return {"text": self.text, "finish": "stop", "usage": None}

    REPLY = json.dumps({
        "response": {"recommended": "那就改到成都见面", "bad_boy": "行", "playful": "好",
                     "warm": "到了说一声"},
        "directions": ["成都见", None, "注意安全", None],
        "analysis": {"topic_status": "new", "topic_label": "成都行程",
                     "scene_facts": [{"text": "她确认行程改到成都", "source_ids": ["m0"]}],
                     "ongoing": [{"item": "成都行程", "status": "进行中"}]}},
        ensure_ascii=False)

    def run_one(mech, sandbox, window, clock):
        os.makedirs(sandbox, exist_ok=True)
        h = {"assets_root": os.path.join(ROOT, "candidates/phase0_beta_single/assets"),
             "sandbox": sandbox, "turn": 0, "topic": "", "topic_age": 99,
             "persona": {}, "initial_kb": {}, "memory_access": True,
             "last_clock": None, "topic_started_at": None}
        with open(os.path.join(sandbox, "kv_facts.jsonl"), "w", encoding="utf-8") as f:
            for fc in FROZEN_FACTS:
                f.write(json.dumps(fc, ensure_ascii=False) + "\n")
        with open(os.path.join(sandbox, "kv_ongoing.json"), "w", encoding="utf-8") as f:
            json.dump([{"item": "室友养猫与程汐过敏的冲突", "status": "进行中"}], f, ensure_ascii=False)
        ctx = {"case_id": "T", "input": {"clock": clock, "messages": [
            {"role": "HER", "text": window, "ts": clock}]}}
        out = mech.run_turn(h, ctx, StubGW(REPLY))
        files = {}
        for fn in ("kv_facts.jsonl", "kv_ongoing.json"):
            files[fn] = open(os.path.join(sandbox, fn), "rb").read()
        return out, files, mech.snapshot(h)

    tmp = tempfile.mkdtemp(prefix="retr_idf_parity_")
    out_p, files_p, snap_p = run_one(parent, os.path.join(tmp, "sb_parent"),
                                     "你成都那边安排好了吗", "2026-10-27 10:00")
    out_n, files_n, snap_n = run_one(newmech, os.path.join(tmp, "sb_new"),
                                     "你成都那边安排好了吗", "2026-10-27 10:00")
    if files_p["kv_facts.jsonl"] != files_n["kv_facts.jsonl"]:
        fails.append("C1/写回 facts 与父版本不一致")
    if files_p["kv_ongoing.json"] != files_n["kv_ongoing.json"]:
        fails.append("C2/写回 ongoing 与父版本不一致")
    if out_p["reply"] != out_n["reply"] or out_p["id_map"] != out_n["id_map"]:
        fails.append("C3/回复透传或 id_map 与父版本不一致")
    if snap_p != snap_n:
        fails.append("C4/snapshot 字节与父版本不一致（追踪文件不得入快照）")
    if "retr-idf" in out_p["notes"] or not (
            "retr-idf" in out_n["notes"] and "retrieval_trace" in out_n["notes"]):
        fails.append("C5/notes 标识不符")
    if not os.path.isfile(os.path.join(tmp, "sb_new", "retrieval_trace.jsonl")):
        fails.append("C6/新候选未产出 retrieval_trace.jsonl")
    elif os.path.isfile(os.path.join(tmp, "sb_parent", "retrieval_trace.jsonl")):
        fails.append("C7/父版本不应产出追踪文件")

    # ---- D. 来源追踪三态正确性 + 无 gold 泄漏 ----
    tr = [json.loads(ln) for ln in
          open(os.path.join(tmp, "sb_new", "retrieval_trace.jsonl"), encoding="utf-8")][0]
    for key in ("facts_stored", "retrieval", "injected", "writeback", "memory_access"):
        if key not in tr:
            fails.append("D1/trace 缺 %s" % key)
    fs = tr.get("retrieval", {}).get("facts_scored") or []
    if not fs or not all(("selected" in r and "filtered_reason" in r and "score" in r)
                         for r in fs):
        fails.append("D2/trace.facts_scored 三态字段不完整")
    if tr.get("injected", {}).get("ongoing_injection_mode") is None:
        fails.append("D3/trace 未标注 ongoing 注入方式")
    blob = json.dumps(tr, ensure_ascii=False)
    for g in ("gold", "must_keywords", "expect_decline", "目标事实"):
        if g in blob:
            fails.append("D4/trace 含禁注入字段 %s" % g)

    # ---- E. memory-off 行为与父版本一致（不读历史、不产出检索追踪行内容差异） ----
    h_off = {"assets_root": os.path.join(ROOT, "candidates/phase0_beta_single/assets"),
             "sandbox": os.path.join(tmp, "sb_off"), "turn": 0,
             "topic": "", "topic_age": 99, "persona": {}, "initial_kb": {},
             "memory_access": False, "last_clock": None, "topic_started_at": None}
    os.makedirs(h_off["sandbox"], exist_ok=True)
    with open(os.path.join(h_off["sandbox"], "kv_facts.jsonl"), "w", encoding="utf-8") as f:
        for fc in FROZEN_FACTS:
            f.write(json.dumps(fc, ensure_ascii=False) + "\n")
    trr = {}
    old_write = newmech._write_trace
    newmech._write_trace = lambda h, t: trr.update(t)
    try:
        ctx = {"case_id": "T", "input": {"clock": "2026-10-27 10:00", "messages": [
            {"role": "HER", "text": "你成都那边安排好了吗", "ts": "2026-10-27 10:00"}]}}
        newmech.run_turn(h_off, ctx, StubGW(REPLY))
    finally:
        newmech._write_trace = old_write
    if trr.get("facts_stored") not in (None, [],) or trr.get("injected", {}).get("topic") != "":
        fails.append("E1/memory-off 仍读取历史: facts_stored=%r" % trr.get("facts_stored"))

    # ---- F. reviewer 反例归回归集（第九波判决 §5.2/§8-C4；不再是"独立封存验证"） ----
    R13 = [{"text": t} for t in (
        "她室友今天抱回来一只猫", "她今天20:12说对猫毛过敏，不能靠近",
        "程汐把猫关在自己房间门外，让室友自己吸", "程汐因为过敏撸不了猫，只能云吸",
        "她周末去徒步了，说山顶风景很好", "她说改天发照片给我",
        "程汐在2026-10-26 11:10至11:59之间连续发送大量重复的短语消息",
        "她室友今天12:25提出想把猫放到她房间晒太阳", "她今天12:30在问要不要拦这件事",
        "她今天18:15说食堂麻辣香锅排了很长的队", "她今天18:20说香锅味道还行",
        "她用的是iPhone 15 Pro", "她坐MU5123从上海到北京")]
    REVIEWER = [
        ("F1/英数实体-iPhone型号", "iPhone 15 Pro 换电池贵吗", {11}),
        ("F2/英数实体-航班号", "MU5123晚点了", {12}),
        ("F3/单字歧义-猫眼≠猫毛", "猫眼能退票吗", set()),
    ]
    for name, q, want in REVIEWER:
        got = set(_selected(q, R13))
        if got != want:
            fails.append("%s: selected=%s ≠ 期望 %s" % (name, sorted(got), sorted(want)))

    # ---- H. 检索门控故障族（独立复核 §3 + 合并执行令 §6-1；配对回归，权衡显式断言） ----
    ALLERGY = "她对猫毛过敏，不能靠近"
    REFUND_BG = "她说退票规则可以查一下"
    CAT_EYE = "猫眼能退票吗"

    def _sel_trace(window, facts):
        tr = {}
        sel = [i for i, _ in newmech._retrieve(window, facts, trace=tr)]
        return set(sel), tr

    # H1 配对回归：同查询、不同库存背景——单条正常背景记录不得替无关事实放行
    sel_h1a, tr_h1a = _sel_trace(CAT_EYE, [{"text": ALLERGY}])
    sel_h1b, tr_h1b = _sel_trace(CAT_EYE, [{"text": ALLERGY}, {"text": REFUND_BG}])
    if sel_h1a != set():
        fails.append("H1a/猫眼×仅过敏事实: selected=%s ≠ {}" % sorted(sel_h1a))
    if 0 in sel_h1b:
        fails.append("H1b/猫眼×加退票背景: 过敏事实(fact_id=0)仍被取回（跨条目授权未收口）: %s"
                     % sorted(sel_h1b))
    if sel_h1b != {1}:
        fails.append("H1b/猫眼×加退票背景: 退票背景事实应入选且过敏事实应被拒: %s" % sorted(sel_h1b))
    _rows = {r["fact_id"]: r for r in tr_h1b["retrieval"]["facts_scored"]}
    _g0 = _rows[0].get("gate") or ""
    if not _g0.startswith("denied"):
        fails.append("H1c/猫眼×加退票背景: 过敏事实 gate=%r 未标注拒绝归因" % _g0)

    # H2 相关短问（登记权衡：单字证据与"猫眼"歧义字面不可分，语义层缺失，不装已修）
    sel_h2, tr_h2 = _sel_trace("我能抱一下猫吗", [{"text": ALLERGY}])
    if sel_h2 != set():
        fails.append("H2/相关短问'我能抱一下猫吗': selected=%s（权衡登记：单字证据拒，"
                     "v1 能取回属单字歧义放行的代价）" % sorted(sel_h2))
    if tr_h2["retrieval"]["facts_scored"][0].get("gate") != "denied:single_char_only":
        fails.append("H2b/相关短问: gate 归因应为 denied:single_char_only")

    # H3 标点分段：跨标点假 bigram 不得再产生（"退，票"≠"退票"）
    if newmech._cjk_bigrams("退，票") != set():
        fails.append("H3a/_cjk_bigrams('退，票') 跨标点伪造了退票")
    if newmech._cjk_bigrams("猫。眼") != set():
        fails.append("H3b/_cjk_bigrams('猫。眼') 跨标点伪造了猫眼")
    sel_h3, _ = _sel_trace(CAT_EYE, [{"text": ALLERGY}, {"text": "她说退，票要自己申请"}])
    if sel_h3 != {1}:
        fails.append("H3c/猫眼×分段退票背景: 过敏事实应被拒、退票事实凭双字证据入选: %s"
                     % sorted(sel_h3))

    # H4 单字归因分离：G08"不吃辣"首先被字面门拦住（denied），与否定极性理解分开归因
    sel_h4, tr_h4 = _sel_trace("那家火锅很辣 你受得了吗", [{"text": "她不吃辣，点餐要备注"}])
    if sel_h4 != set():
        fails.append("H4/不吃辣: selected=%s" % sorted(sel_h4))
    if tr_h4["retrieval"]["facts_scored"][0].get("gate") != "denied:single_char_only":
        fails.append("H4b/不吃辣: 漏取归因应为检索层单字拒绝（denied），非否定极性")

    # H5 背景不串门：英数查询 + 无关背景记录，背景不得入选、英数召回保持
    sel_h5, _ = _sel_trace("iPhone 15 Pro 换电池贵吗",
                           [{"text": "她用的是iPhone 15 Pro"}, {"text": REFUND_BG}])
    if sel_h5 != {0}:
        fails.append("H5/英数查询×无关背景: selected=%s ≠ {0}（背景串门或英数回归）" % sorted(sel_h5))

    # ---- G. 开发节点（≥12，不同主题；观测登记用，不作为能力通过宣称） ----
    # 依据第九波判决 §8-C3：阈值校准用例与锁定用例分开；本组为开发观测节点，
    # 行为登记于 refs/L3B_RETR_DEV_NODES_20261020.md，只断言"不异常、不崩溃"。
    DEV_NODES = [
        ("G01/英文品牌型号", [{"text": "她换华为Mate60了"}], "mate60 贵吗 她那个"),
        ("G02/数字编号", [{"text": "她们公司在3号楼302开会"}], "你们302会议室在哪栋"),
        ("G03/金额表意", [{"text": "她借了我三百块买书"}], "那300块不用急着还"),
        ("G04/同字歧义-人名", [{"text": "李雪帮她搬家了"}], "下雪了路滑注意安全"),
        ("G05/相关改述", [{"text": "她报了驾驶班学车"}], "你科目二约了没"),
        ("G06/无关歧义-苹果", [{"text": "她昨天买了苹果手机"}], "我想吃苹果了 水果没了"),
        ("G07/状态变化", [{"text": "她住在城东老小区"}], "你搬新家之后通勤远吗"),
        ("G08/否定语义", [{"text": "她不吃辣，点餐要备注"}], "那家火锅很辣 你受得了吗"),
        ("G09/相对时间", [{"text": "她说下周三体检"}], "周三体检别吃早饭"),
        ("G10/中英混合", [{"text": "她的视频网站会员到期了"}], "app会员续了吗 帮我砍一刀"),
        ("G11/地名近似", [{"text": "她老家在山西大同"}], "陕西的面食是不是最正宗"),
        ("G12/缩写词", [{"text": "她赶论文ddl熬了通宵"}], "你ddl什么时候交"),
    ]
    dev_obs = []
    for name, facts, w in DEV_NODES:
        try:
            got = sorted(set(_selected(w, facts)))
            dev_obs.append((name, got))
        except Exception as e:
            fails.append("%s 异常: %s: %s" % (name, type(e).__name__, e))
    json.dump(dev_obs, open(os.path.join(ROOT, "logs", "retr_dev_nodes_obs_20261020.json"),
                            "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    if fails:
        print("FAIL %d 项：" % len(fails))
        for f in fails:
            print("  -", f)
        raise SystemExit(1)
    print("PASS：冻结案例回放(t5过敏取回/t6误取消除) + 锁定用例8组 + 故障族H组"
          "(配对背景/相关短问权衡/标点分段/单字归因/背景不串门) + 父版本写回/快照"
          "逐字节一致 + 追踪三态完整无gold泄漏 + memory-off一致")


if __name__ == "__main__":
    main()
