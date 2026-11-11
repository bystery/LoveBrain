#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""test_review_loop_guards.py — R3#4 review_loop 加固反例自测。

反例矩阵（独立复核 §8 / 修复任务书 R3#4）：
  T1 材料、表单、映射在位 → 正常接受；
  T2 篡改展示原文（materials/MAT_xx.json 改字，保留旧 binding hash）→ ingest 必拒
     （"材料内容与映射绑定不一致"）；汇总期同拒；
  T3 同 material_id+同 reviewer 重复表单、revision 不递增 → 拒；
  T4 重复表单带 revision 递增 → 显式替代旧表（superseded），不重复计票；
  T5 统计分母分离：票数/已审材料数/未审材料数分列，已审材料数 ≤ 总材料数
     （杜绝"总12/审13/未审−1"）；
  T6 episode 顶层 persona/initial_kb 进入材料（不得丢）。
零网络；全部在临时目录（LB_REVIEW_WORK 覆盖），不触碰真实发布区。
"""
import json
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))
sys.path.insert(0, HERE)

CASE = {
    "case_id": "T_RL1", "turn_type": "reply",
    "persona": {"name": "小周", "city": "杭州"},          # episode 顶层 persona
    "initial_kb": [{"topic": "良渚秋游", "fact": "上周约了去良渚"}],  # 顶层 initial_kb
    "input": {"clock": "2026-10-21 10:00",
              "messages": [
                  {"role": "HER", "text": "良渚的秋天好看吗", "ts": "2026-10-21 09:58"},
                  {"role": "HER", "text": "周末一起去？", "ts": "2026-10-21 10:00"}]},
    "gold": {},
}


def main():
    fails = []
    tmp = tempfile.mkdtemp(prefix="lb_rloop_")
    case_path = os.path.join(tmp, "T_RL1.json")
    json.dump(CASE, open(case_path, "w", encoding="utf-8"), ensure_ascii=False)
    spec = {"seed": 7, "materials": [
        {"case_ref": case_path, "state": "正常",
         "arm_x": {"reply": "良渚秋天确实好看，周六上午人少"},
         "arm_y": {"reply": "可以呀，带相机拍银杏"}},
        {"case_ref": case_path, "state": "正常",
         "arm_x": {"reply": "好啊，我查下天气"},
         "arm_y": {"reply": "周末我可能有事，下周呢"}},
    ]}
    spec_path = os.path.join(tmp, "spec.json")
    json.dump(spec, open(spec_path, "w", encoding="utf-8"), ensure_ascii=False)

    work = os.path.join(tmp, "work")
    os.environ["LB_REVIEW_WORK"] = work
    import review_loop as rl

    # --- gen + T6 顶层 persona/initial_kb ---
    rl.cmd_gen(spec_path)
    mat1 = json.load(open(os.path.join(work, "materials", "MAT_01.json"),
                          encoding="utf-8"))
    if mat1.get("共同背景", {}).get("name") != "小周":
        fails.append("T6: episode 顶层 persona 丢失（共同背景=%r)"
                     % mat1.get("共同背景"))
    if not mat1.get("初始知识库"):
        fails.append("T6: episode 顶层 initial_kb 丢失")
    if mat1.get("原样输出") != {"甲": spec["materials"][0]["arm_x"]["reply"],
                               "乙": spec["materials"][0]["arm_y"]["reply"]} \
            and mat1.get("原样输出") != {"甲": spec["materials"][0]["arm_y"]["reply"],
                                        "乙": spec["materials"][0]["arm_x"]["reply"]}:
        fails.append("gen: 原样输出与 arms 不符")

    def fill(dst_name, mid, reviewer, compare, rev=1):
        src = "entry_01.json" if mid == "MAT_01" else "entry_02.json"
        f = json.load(open(os.path.join(work, "forms", src),
                           encoding="utf-8"))
        f["material_id"] = mid
        f["reviewer"] = reviewer
        f["compare"] = compare
        f["revision"] = rev
        json.dump(f, open(os.path.join(entries, dst_name), "w",
                          encoding="utf-8"), ensure_ascii=False)

    entries = os.path.join(work, "entries")
    os.makedirs(entries, exist_ok=True)
    fill("e1.json", "MAT_01", "r1", "甲")

    # --- T1 正常接受 ---
    rl.cmd_ingest(entries)
    ing = json.load(open(os.path.join(work, "ingest_result.json"),
                         encoding="utf-8"))
    if len(ing["accepted"]) != 1:
        fails.append("T1: 正常表单未接受 accepted=%d" % len(ing["accepted"]))

    # --- T2 篡改展示原文（保留旧 binding hash）→ 必拒 ---
    mp = json.load(open(os.path.join(work, "mapping_secret.json"),
                        encoding="utf-8"))
    old_binding = mp["mapping"]["MAT_01"]["binding_sha256_16"]
    m = json.load(open(os.path.join(work, "materials", "MAT_01.json"),
                       encoding="utf-8"))
    anon = m["臂顺序"][0]
    m["原样输出"][anon] = m["原样输出"][anon] + "（被篡改）"
    json.dump(m, open(os.path.join(work, "materials", "MAT_01.json"), "w",
                      encoding="utf-8"), ensure_ascii=False, indent=1)
    # 篡改者同时把 mapping 的 binding 一并保留（不做 gen）→ mapping 未变
    rl.cmd_ingest(entries)
    ing = json.load(open(os.path.join(work, "ingest_result.json"),
                         encoding="utf-8"))
    if ing["accepted"] or not ing["rejected"]:
        fails.append("T2: 篡改展示原文未被拒")
    elif not any("展示原文被篡改" in "; ".join(r["why"]) for r in ing["rejected"]):
        fails.append("T2: 拒因未指明内容绑定不一致: %r"
                     % [r["why"] for r in ing["rejected"]])
    # 还原材料
    m["原样输出"][anon] = m["原样输出"][anon].replace("（被篡改）", "")
    json.dump(m, open(os.path.join(work, "materials", "MAT_01.json"), "w",
                      encoding="utf-8"), ensure_ascii=False, indent=1)

    # --- T3 重复表单（同 material+reviewer，revision 不递增）→ 拒 ---
    fill(os.path.join(entries, "e1_dup.json"), "MAT_01", "r1", "乙", rev=1)
    rl.cmd_ingest(entries)
    ing = json.load(open(os.path.join(work, "ingest_result.json"),
                         encoding="utf-8"))
    if ing["accepted"]:
        # e1.json 先序在前被接受，dup 被拒
        if not any("重复表单" in "; ".join(r["why"]) for r in ing["rejected"]):
            fails.append("T3: 重复表单拒因缺失: %r"
                         % [r["why"] for r in ing["rejected"]])
    else:
        fails.append("T3: 首份表单也应被接受 accepted=%d" % len(ing["accepted"]))
    # 记录 e1 被接受后清理 dup，准备 T4
    os.remove(os.path.join(entries, "e1_dup.json"))

    # --- T4 revision 递增 → 显式替代 ---
    fill(os.path.join(entries, "e1_rev2.json"), "MAT_01", "r1", "相当", rev=2)
    rl.cmd_ingest(entries)
    ing = json.load(open(os.path.join(work, "ingest_result.json"),
                         encoding="utf-8"))
    if len(ing.get("superseded") or []) != 1:
        fails.append("T4: 旧表未被显式替代 superseded=%r"
                     % ing.get("superseded"))
    acc_entries = [a["entry"] for a in ing["accepted"]]
    if acc_entries != ["e1_rev2.json"]:
        fails.append("T4: 替代后接受集应为 [e1_rev2]，实际 %r" % acc_entries)

    # --- T5 汇总分母分离 ---
    rl.cmd_aggregate()
    s = json.load(open(os.path.join(work, "review_summary.json"),
                       encoding="utf-8"))
    ml = s["manual_layers"]
    if ml["已审材料数"] != 1 or ml["总材料数"] != 2 or ml["未审材料数"] != 1:
        fails.append("T5: 分母不符 %r" % {k: ml[k] for k in
                                          ("总材料数", "已审材料数", "未审材料数")})
    if ml["票数_compare"] != 1:
        fails.append("T5: 票数应为 1（替代后仅 1 票），实际 %s"
                     % ml["票数_compare"])
    if ml.get("_INVARIANT_VIOLATION"):
        fails.append("T5: 分母不变式违约 %s" % ml["_INVARIANT_VIOLATION"])
    if "已审" in ml and "未审" in ml:
        fails.append("T5: 旧统计口径未移除（须分列票数/已审材料数/未审材料数）")

    shutil.rmtree(tmp, ignore_errors=True)
    if fails:
        print("REVIEW LOOP GUARDS FAIL")
        for x in fails:
            print(" -", x)
        sys.exit(1)
    print("REVIEW LOOP GUARDS PASS：篡改展示原文必拒；重复表单拒/显式替代；"
          "票数/已审材料数/未审材料数分列且已审≤总；episode 顶层 persona/"
          "initial_kb 入材料。")


if __name__ == "__main__":
    main()
