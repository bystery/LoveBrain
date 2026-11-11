#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""test_coverage_guards.py — R3#5 coverage 字段审计反例自测。

反例矩阵（独立复核 §7 / 修复任务书 R3#5）：
  T1 跨轮时钟倒退（NCA 请求时钟改 1900 年的同类反例）→ 必须报 issue；
  T2 轮内请求时钟早于消息 ts → 必须报 issue；
  T3 message.image 媒体通道缺失文件 → 必须报 issue；存在文件 → 不报；
  T4 布尔检查失败（type=may_decline 但 may_decline!=true）→ 进 issues；
  T5 正常 episode → 零 issue（不误报）。
零网络；不触碰真实题库与输出文件（只调用 audit_episode 纯函数）。
"""
import json
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))
sys.path.insert(0, HERE)
import gen_coverage_matrix as gc  # noqa: E402  导入不触发输出（main 已隔离）


def ep(clock_a, clock_b, ts_last="2026-10-21 10:00", image=None,
       may_decline_type=False, may_decline_value=None):
    msgs = [{"role": "HER", "text": "周末去爬山吗", "ts": "2026-10-21 09:58"},
            {"role": "HER", "text": "天气报说周日晴", "ts": ts_last}]
    if image:
        msgs[-1]["image"] = image
    inp = {"clock": clock_b, "messages": msgs}
    if may_decline_type:
        inp["type"] = "may_decline"
        if may_decline_value is not None:
            inp["may_decline"] = may_decline_value
    return {"episode_id": "T_COV", "turns": [
        {"case_id": "t01", "input": {"clock": clock_a, "messages": msgs[:1]},
         "gold": {}},
        {"case_id": "t02", "input": inp, "gold": {}}]}


def issues_for(ep_data, tmp):
    rel = os.path.join(tmp, "fake_rel.json")
    _, issues = gc.audit_episode(rel, ep_data)
    return issues


def main():
    fails = []
    tmp = tempfile.mkdtemp(prefix="lb_cov_")
    # 真实存在的媒体文件（用仓库内现有图片，只读引用）
    real_img = None
    for cand in ("benchmark/media/pilot/L1_t07_phone.png",):
        if os.path.isfile(os.path.join(ROOT, cand)):
            real_img = cand
            break

    # T1 跨轮时钟倒退
    iss = issues_for(ep("2026-10-21 10:00", "1900-01-01 08:00"), tmp)
    if not any("时钟倒退" in i for i in iss):
        fails.append("T1: 跨轮时钟倒退未被抓: %r" % iss)

    # T2 轮内 clock 早于消息 ts
    iss = issues_for(ep("2026-10-21 10:00", "2026-10-21 09:00",
                        ts_last="2026-10-21 09:30"), tmp)
    if not any("早于消息时间戳" in i for i in iss):
        fails.append("T2: 轮内时钟早于 ts 未被抓: %r" % iss)

    # T3 message.image 缺失文件必须报；存在文件不报
    iss = issues_for(ep("2026-10-21 10:00", "2026-10-21 10:05",
                        image="benchmark/media/__no_such__.png"), tmp)
    if not any("媒体缺失" in i and "__no_such__" in i for i in iss):
        fails.append("T3a: message.image 缺失文件未被抓: %r" % iss)
    if real_img:
        iss = issues_for(ep("2026-10-21 10:00", "2026-10-21 10:05",
                            image=real_img), tmp)
        if any("媒体缺失" in i for i in iss):
            fails.append("T3b: 真实存在图片被误报缺失: %r" % iss)
    else:
        fails.append("T3b: 未找到仓库内真实图片可作正例（检查探测路径）")

    # T4 布尔检查失败进 issues
    iss = issues_for(ep("2026-10-21 10:00", "2026-10-21 10:05",
                        may_decline_type=True, may_decline_value=False), tmp)
    if not any("may_decline" in i for i in iss):
        fails.append("T4: 布尔检查失败未进 issues: %r" % iss)

    # T5 正常 episode 零误报（构造时钟/ts 自洽的干净数据）
    clean = {"episode_id": "T_COV", "turns": [
        {"case_id": "t01",
         "input": {"clock": "2026-10-21 09:00",
                   "messages": [{"role": "HER", "text": "早",
                                 "ts": "2026-10-21 08:59"}]},
         "gold": {}},
        {"case_id": "t02",
         "input": {"clock": "2026-10-21 10:00",
                   "messages": [{"role": "HER", "text": "周末去爬山吗",
                                 "ts": "2026-10-21 09:58"}]},
         "gold": {}}]}
    iss = issues_for(clean, tmp)
    if iss:
        fails.append("T5: 正常 episode 误报: %r" % iss)

    shutil.rmtree(tmp, ignore_errors=True)
    if fails:
        print("COVERAGE GUARDS FAIL")
        for x in fails:
            print(" -", x)
        sys.exit(1)
    print("COVERAGE GUARDS PASS：跨轮倒退/轮内早于 ts/message.image 缺失/"
          "布尔失败均被抓；正常 episode 零误报。")


if __name__ == "__main__":
    main()
