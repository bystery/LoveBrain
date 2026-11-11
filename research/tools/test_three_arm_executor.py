# -*- coding: utf-8 -*-
"""test_three_arm_executor.py — 66 槽执行器 v2 回归（v10 复核 §5 准入反例矩阵）。

零真实模型调用、零外部生成请求（mock + 网关内部 chaos 注入）。

矩阵（v10 复核 §5 表）：
1. 干净 66 槽：恰 66 个真实 started、逐槽一对一配对、verified_complete、
   expr 块断言、轮换顺序、batch_config 实际配置非空（endpoint_env_name/
   temperature/executor 散列入账）。
2. 网关内部超时（--chaos-attempt N，适配器内部抛错）：真实 attempt_failed
   事件、立即停批、不得重跑或继续；再次启动仍阻断；--resolve-failed 报
   RECOVERY_DISABLED；真实 started 计数不再增长（无隐藏补跑）。
3. 中断 occupied：不跳过、不猜成成功、不自动重发（BATCH_ABORTED_TERMINAL）。
4. 缺失/落后/超前 checkpoint：生成前拒绝，不占槽、不产生真实调用。
5. 第 67 个真实生成请求：请求发出前拒绝（真实事件计数权威，无第 67 条 started）。
6. 坏 JSON / 空白 / 越权不回 / 合法不回：四类分开，前三者不算合法收尾。
7. adapter / 配置漂移：批次重入逐项比对拒绝。
mock 只证明执行链路，不证明生成质量。
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXE = os.path.join(ROOT, "tools", "l3b_three_arm_executor.py")
PY = sys.executable


def run(args, env_extra=None):
    env = dict(os.environ)
    if env_extra:
        env.update(env_extra)
    p = subprocess.run([PY, EXE] + args, capture_output=True, text=True,
                       cwd=ROOT, encoding="utf-8", env=env)
    return p.returncode, p.stdout, p.stderr


def bdir(bd):
    return os.path.join(ROOT, "logs", "l3b_66_%s" % bd)


def load(bd, name):
    return json.load(open(os.path.join(bdir(bd), name), encoding="utf-8"))


def main():
    fails = []
    tmp = tempfile.mkdtemp(prefix="l3b66_test_")
    b1 = "t_" + os.path.basename(tmp) + "_clean"

    # ---- 0. 封存断言（v12 §6B 后新增）：装配层已按共同工程修复升级，v3 冻结
    # 散列必然失配 → 66 槽配置必须拒绝运行（封存机制本身在起作用）。
    # 全矩阵回归转移至 52 槽执行器等价矩阵（tools/test_parent_child_executor.py），
    # 守卫代码同源（导入复用），该矩阵通过即覆盖本文件原 1-7 项。
    import hashlib
    asm_sha = hashlib.sha256(
        open(os.path.join(ROOT, "arena", "lb_assembly.py"), "rb").read()).hexdigest()
    sealed = asm_sha != "a671c23b529bf3f9d25991c04aa920fa533d43b7164b18db1cf980d877096e1b"
    if sealed:
        rc0, out0, err0 = run(["--batch-id", "seal_probe_" + os.path.basename(tmp),
                               "--adapter", "mock"])
        if not (rc0 != 0 and "FROZEN_MISMATCH" in (err0 or "") + out0):
            print("封存断言失败：66 配置在装配层升级后仍可运行（应拒绝）")
            raise SystemExit(1)
        print("PASS 0/66 槽 v3 配置已封存（FROZEN_MISMATCH 拒绝运行；"
              "装配层 v12 §6B 升级生效）。全矩阵回归转移至 52 槽等价矩阵。")
        print("SKIPPED (SEALED): 原 1-7 项针对旧装配层的矩阵不再可运行，"
              "等价守卫矩阵见 test_parent_child_executor.py")
        return

    # ---- 1. 干净 66 槽：恰 66 个真实 started，逐槽一对一 ----
    rc, out, err = run(["--batch-id", b1, "--adapter", "mock"])
    if rc != 0:
        fails.append("1/干净批退出码 %d: %s" % (rc, err[-300:]))
    s = load(b1, "summary.json")
    if s["real_started"] != 66 or s["real_completed"] != 66 or s["real_failed"] != 0:
        fails.append("1/真实网关事件计数不符: started=%d completed=%d failed=%d"
                     % (s["real_started"], s["real_completed"], s["real_failed"]))
    if s["slots_registered"] != 66 or s["terminal_slots"] != 66:
        fails.append("1/槽账不符: registered=%d terminal=%d"
                     % (s["slots_registered"], s["terminal_slots"]))
    if s["pairing_verified"] != 66 or not s["verified_complete"]:
        fails.append("1/配对校验不全: pairing=%d verified_complete=%s"
                     % (s["pairing_verified"], s["verified_complete"]))
    sl = load(b1, "slots.json")
    for x in sl["slots"]:
        if x["status"] != "success":
            fails.append("1/%s 非 success: %s" % (x["slot_key"], x.get("fail_kind")))
            continue
        if not x.get("attempts", {}).get("ok"):
            fails.append("1/%s 配对不符: %s" % (x["slot_key"],
                                                x["attempts"].get("errors")))
        want = 1 if x["arm"] == "A3" else 0
        if x.get("expr_block_counts") != [want]:
            fails.append("1/%s 表达块计数 %s ≠ [%d]" % (x["slot_key"],
                                                        x.get("expr_block_counts"), want))
    # 槽位轮换
    ROT = [("A1", "A2", "A3"), ("A2", "A3", "A1"), ("A3", "A1", "A2")]
    for gi in range(22):
        trip = sl["slots"][gi * 3:gi * 3 + 3]
        if tuple(x["arm"] for x in trip) != ROT[gi % 3]:
            fails.append("1/轮换组 %d 顺序错误" % gi)
        if len({x["episode"] + x["repeat"] + str(x["turn1"]) for x in trip}) != 1:
            fails.append("1/轮换组 %d 非同请求位" % gi)
    # batch_config 实际配置非空（v10 复核 §4.2）
    cfg = load(b1, "batch_config.json")
    if cfg.get("endpoint_env_name") != "LB_API_BASE":
        fails.append("1/batch_config 未记录实际端点源: %r" % cfg.get("endpoint_env_name"))
    if "temperature_effective" not in cfg:
        fails.append("1/batch_config 缺 temperature_effective")
    if not cfg.get("executor_sha256"):
        fails.append("1/batch_config 缺执行器散列")
    if not sl.get("config"):
        fails.append("1/slots.config 仍为空（setdefault bug 未修）")
    if sl.get("config", {}).get("adapter") != "mock":
        fails.append("1/slots.config.adapter 不符: %r" % sl.get("config", {}).get("adapter"))
    # ---- 7. adapter 漂移拒绝（重入同一批换 adapter） ----
    rc, out, err = run(["--batch-id", b1, "--adapter", "echo"])
    if "CONFIG_MISMATCH" not in (out + err):
        fails.append("7/adapter 漂移未被拒绝: %s%s" % (out[-150:], err[-200:]))
    rc, out, err = run(["--batch-id", b1, "--adapter", "openai_compat"],
                       env_extra={"LB_API_BASE": "http://127.0.0.1:9"})
    if "CONFIG_MISMATCH" not in (out + err):
        fails.append("7/端点漂移未被拒绝: %s%s" % (out[-150:], err[-200:]))

    # ---- 2. 网关内部超时：真实 failed 事件、停批、不可恢复、无隐藏补跑 ----
    b2 = "t_" + os.path.basename(tmp) + "_chaos"
    rc, out, err = run(["--batch-id", b2, "--adapter", "mock", "--chaos-attempt", "13"])
    s2 = load(b2, "summary.json")
    if s2["real_started"] != 13 or s2["real_failed"] != 1 or s2["real_completed"] != 12:
        fails.append("2/chaos 真实事件计数不符: started=%d completed=%d failed=%d"
                     % (s2["real_started"], s2["real_completed"], s2["real_failed"]))
    if s2["slots_registered"] != 13:
        fails.append("2/chaos 槽账应 13（12 成功+1 失败）: %d" % s2["slots_registered"])
    sl2 = load(b2, "slots.json")
    f = [x for x in sl2["slots"] if x["status"] == "failed"]
    if len(f) != 1 or f[0].get("fail_kind") != "gateway_or_transport_failure":
        fails.append("2/失败槽留证不符: %s" % [(x["slot_key"], x.get("fail_kind"))
                                               for x in f])
    # 真实 attempt_failed 事件在网关日志
    evp = os.path.join(bdir(b2), "runs", "A2", "NCA_CORR_INTENT_v1", "r1",
                       "gateway_events.jsonl")
    evs = [json.loads(l) for l in open(evp, encoding="utf-8") if l.strip()]
    if not any(e.get("kind") == "attempt_failed" for e in evs):
        fails.append("2/网关日志缺真实 attempt_failed 事件")
    # 再次启动（同配置）仍阻断
    rc, out, err = run(["--batch-id", b2, "--adapter", "mock", "--chaos-attempt", "13"])
    if "BATCH_ABORTED_TERMINAL" not in (out + err):
        fails.append("2/失败批重启未被阻断: %s%s" % (out[-150:], err[-200:]))
    # 恢复路径禁用
    rc, out, err = run(["--batch-id", b2, "--adapter", "mock", "--chaos-attempt", "13",
                        "--resolve-failed", "13", "--resolution-note", "x"])
    if "RECOVERY_DISABLED" not in (out + err):
        fails.append("2/--resolve-failed 未报 RECOVERY_DISABLED: %s%s"
                     % (out[-150:], err[-200:]))
    # 隐藏补跑检查：重启尝试后真实 started 仍为 13
    s2b = load(b2, "summary.json")
    if s2b["real_started"] != 13:
        fails.append("2/重启后真实 started 增长（隐藏补跑）: %d" % s2b["real_started"])

    # ---- 3. 中断 occupied：阻断，不跳过不猜测 ----
    b3 = "t_" + os.path.basename(tmp) + "_occupied"
    os.makedirs(bdir(b3), exist_ok=True)
    json.dump({"batch_id": "l3b_66_" + b3, "config": {"adapter": "mock"},
               "slots": [{"slot_no": 1, "slot_key": "A1|NCA_CORR_INTENT_v1|r1|t1",
                          "arm": "A1", "episode": "NCA_CORR_INTENT_v1",
                          "repeat": "r1", "turn1": 1, "status": "occupied"}],
               "events": []},
              open(os.path.join(bdir(b3), "slots.json"), "w", encoding="utf-8"),
              ensure_ascii=False)
    rc, out, err = run(["--batch-id", b3, "--adapter", "mock"])
    if "BATCH_ABORTED_TERMINAL" not in (out + err):
        fails.append("3/occupied 未决未被阻断: %s%s" % (out[-150:], err[-200:]))
    s3 = load(b3, "summary.json")
    if s3["occupied_slots"] != ["A1|NCA_CORR_INTENT_v1|r1|t1"]:
        fails.append("3/occupied 槽未被如实列示: %s" % s3["occupied_slots"])

    # ---- 4/5. checkpoint 前驱断言 + 真实事件额度权威（进程内直测守卫） ----
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    import l3b_three_arm_executor as exe
    b4 = "t_" + os.path.basename(tmp) + "_ck"
    exe.run_slot(b4, "mock", "A1", "NCA_CORR_INTENT_v1", "r1", 1)   # t1 成功
    st0, _, _ = exe._gateway_event_counts(exe.batch_dir(b4))
    # t3 请求但 checkpoint 停在 1（落后）→ 生成前拒绝
    try:
        exe.run_slot(b4, "mock", "A1", "NCA_CORR_INTENT_v1", "r1", 3)
        fails.append("4/落后 checkpoint 未拒绝（t3 vs turns_done=1）")
    except SystemExit as e:
        if not str(e).startswith("CHECKPOINT_MISMATCH"):
            fails.append("4/落后拒绝报错口径不符: %s" % e)
    # 篡改 checkpoint 超前（turns_done=5）请求 t2 → 拒绝
    ckp = os.path.join(exe.batch_dir(b4), "runs", "A1", "NCA_CORR_INTENT_v1",
                       "r1", "checkpoint.json")
    ck = json.load(open(ckp, encoding="utf-8"))
    ck["turns_done"] = 5
    json.dump(ck, open(ckp, "w", encoding="utf-8"))
    try:
        exe.run_slot(b4, "mock", "A1", "NCA_CORR_INTENT_v1", "r1", 2)
        fails.append("4/超前 checkpoint 未拒绝（t2 vs turns_done=5）")
    except SystemExit as e:
        if not str(e).startswith("CHECKPOINT_MISMATCH"):
            fails.append("4/超前拒绝报错口径不符: %s" % e)
    # 删 checkpoint 请求 t2 → 拒绝
    os.remove(ckp)
    try:
        exe.run_slot(b4, "mock", "A1", "NCA_CORR_INTENT_v1", "r1", 2)
        fails.append("4/缺失 checkpoint 未拒绝")
    except SystemExit as e:
        if not str(e).startswith("CHECKPOINT_MISMATCH"):
            fails.append("4/缺失拒绝报错口径不符: %s" % e)
    # 拒绝不占槽、不产生真实调用
    sl4 = exe.load_slots(exe.batch_dir(b4))
    st1, _, _ = exe._gateway_event_counts(exe.batch_dir(b4))
    if len(sl4["slots"]) != 1 or st1 != st0:
        fails.append("4/拒绝路径占槽或产生了真实调用: slots=%d started=%d→%d"
                     % (len(sl4["slots"]), st0, st1))
    # 第 67 个真实生成请求：仅 66 条真实 started（无槽账）也拒绝
    b5 = "t_" + os.path.basename(tmp) + "_cap"
    rd = os.path.join(exe.batch_dir(b5), "runs", "A1", "NCA_CORR_INTENT_v1", "r1")
    os.makedirs(rd, exist_ok=True)
    with open(os.path.join(rd, "gateway_events.jsonl"), "w", encoding="utf-8") as f:
        for i in range(66):
            f.write(json.dumps({"kind": "attempt_started", "attempt_id": i + 1,
                                "run_id": "ghost_%d" % i, "case_id": "x"}) + "\n")
    try:
        exe.run_slot(b5, "mock", "A2", "NCA_CORR_INTENT_v1", "r1", 1)
        fails.append("5/66 条真实 started 后第 67 请求未被拒绝")
    except SystemExit as e:
        if not str(e).startswith("CAP_EXCEEDED"):
            fails.append("5/超额拒绝报错口径不符: %s" % e)
    sl5 = exe.load_slots(exe.batch_dir(b5))
    if sl5["slots"]:
        fails.append("5/超额拒绝却登记了槽（请求未被真正挡在发出前）")

    # ---- 6. 汇总分类：坏 JSON / 空白 / 越权不回 / 合法不回 四类分开 ----
    legal_nr = json.dumps({"action": "no_reply", "response": None,
                           "directions": [None] * 4,
                           "analysis": {"topic_status": "end", "topic_label": "收尾",
                                        "scene_facts": [], "ongoing": []}},
                          ensure_ascii=False)
    cases = [
        ('{"response":', {"veto": "json_schema_broken"}, "parse_fail_not_json"),
        ("   ", {"veto": None}, "blank"),
        (json.dumps({"response": {"recommended": None, "bad_boy": None,
                                  "playful": None, "warm": None},
                     "directions": [None] * 4, "analysis": {}},
                    ensure_ascii=False),
         {"veto": "unauthorized_no_reply"}, "unauthorized_no_reply"),
        (legal_nr, {"veto": None}, "legal_no_reply"),
        (json.dumps({"response": {"recommended": "在的", "bad_boy": None,
                                  "playful": None, "warm": None},
                     "directions": ["在", None, None, None], "analysis": {}},
                    ensure_ascii=False),
         {"veto": None}, "reply"),
    ]
    for raw, sc, want in cases:
        got, _meta = exe._classify_reply(raw, sc)
        if got != want:
            fails.append("6/分类 %r → %s（期望 %s）" % (raw[:24], got, want))

    for b in (b1, b2, b3, b4, b5):
        shutil.rmtree(bdir(b), ignore_errors=True)
    shutil.rmtree(tmp, ignore_errors=True)

    if fails:
        print("FAIL %d 项：" % len(fails))
        for f_ in fails:
            print("  -", f_)
        raise SystemExit(1)
    print("PASS：干净66槽(恰66真实started/逐槽配对/verified_complete/配置锁定) "
          "+ 网关内部超时(真实failed事件/停批/重启阻断/恢复禁用/无隐藏补跑) "
          "+ occupied阻断 + checkpoint缺失落后超前生成前拒绝 + 第67请求发出前拒绝 "
          "+ 四类不回分列 + adapter/端点漂移拒绝")


if __name__ == "__main__":
    main()
