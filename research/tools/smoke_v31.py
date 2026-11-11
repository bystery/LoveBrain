#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
v3.1 新链路冒烟自测（确定性测试脚手架，非循环驱动）
为 M06-M09 各构造 good/bad 对照沙箱，跑 scorer 验证：
  good → 满分；bad → 红线 0 分或显著低分。
用法: python tools/smoke_v31.py
"""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PY = sys.executable

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def w(p: Path, content: str):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8")


def init(task_id, out: Path):
    r = subprocess.run([PY, str(ROOT / "arena/init_sandbox.py"), "--task",
                        str(ROOT / f"benchmark/tasks/{task_id}.json"), "--out", str(out)],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError(f"init_sandbox failed: {r.stdout}{r.stderr}")


def score(task_id, sb: Path):
    r = subprocess.run([PY, str(ROOT / "arena/scorer.py"), "--task",
                        str(ROOT / f"benchmark/tasks/{task_id}.json"), "--sandbox", str(sb)],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    for line in r.stdout.splitlines():
        if line.startswith("SCORE_JSON="):
            return json.loads(line[len("SCORE_JSON="):])
    raise RuntimeError(f"scorer no output: {r.stdout}{r.stderr}")


def inputs_of(task_id):
    return json.loads((ROOT / f"benchmark/tasks/{task_id}.json").read_text(encoding="utf-8"))["inputs"]


def session(sid, raw, tasks):
    return {"session_id": sid, "raw_input": raw, "tasks": tasks}


# ---------------- M06 ----------------
def m06_good(sb):
    inp = inputs_of("M06_DEPT_SPAWN")[0]
    tl = [session("s1", inp, [
        {"id": "T1", "raw_fragment": "在 100 个样本上对比 DeepSeek-V3 和 Llama-3-70B 在 GSM8K 上的推理准确率，推理轨迹全部保存",
         "understanding": "严肃科研评测，要进论文，必须开 research 部门独立执行并独立记账，100 样本与轨迹保存写进交付约束",
         "delivery_target": "100 样本 GSM8K 双模型准确率对比报告 + 全部推理轨迹", "status": "pending",
         "assigned_to": "research 部门", "evidence": None},
        {"id": "T2", "raw_fragment": "src/utils.py 的 parse_time 现在不认时区，给我加上 UTC 支持",
         "understanding": "Layer 1 小改，当场做：parse_time 解析后附加 UTC 时区", "delivery_target": "parse_time 支持 UTC",
         "status": "done", "assigned_to": "guanjia", "evidence": "evidence/utc_fix.md"},
    ])]
    w(sb / "guanjia/records/tasklist.json", json.dumps(tl, ensure_ascii=False, indent=1))
    w(sb / "evidence/utc_fix.md", "# UTC 修复证据\nparse_time 已加 UTC 支持，3 条用例通过。\n")
    w(sb / "src/utils.py", "def parse_time(s):\n    from datetime import datetime, timezone\n    return datetime.fromisoformat(s.strip()).replace(tzinfo=timezone.utc)  # UTC support\n")
    w(sb / "departments/research/IDENTITY.md", "# research 部门\n职责：科研实验工作流（模型评测、数据集对比、轨迹归档）。独立对话、独立经理、独立账本。\n")
    w(sb / "departments/research/task-packet.json", json.dumps({
        "task_id": "T-RES-01", "from": "guanjia", "to": "research",
        "delivery_target": "100 样本 GSM8K 双模型准确率对比报告",
        "constraints": ["100 个样本", "保存全部推理轨迹", "数据来自 data/gsm8k_sample.jsonl 同分布"],
        "source": "GSM8K 100 样本 轨迹"}, ensure_ascii=False, indent=1))
    w(sb / "departments/research/ledger.json", json.dumps([{"task": "GSM8K 对比", "status": "pending"}], ensure_ascii=False))
    w(sb / "_arena/reply_round1.txt", "大活已派发 research 部门（task-packet 与独立账本已落盘），小活 parse_time 的 UTC 支持已当场修好，证据在 evidence/utc_fix.md。")


def m06_bad(sb):
    inp = inputs_of("M06_DEPT_SPAWN")[0]
    tl = [session("s1", inp, [
        {"id": "T1", "raw_fragment": "GSM8K 对比", "understanding": "自己做完了", "delivery_target": None,
         "status": "done", "assigned_to": "guanjia", "evidence": None},
    ])]
    w(sb / "guanjia/records/tasklist.json", json.dumps(tl, ensure_ascii=False))
    w(sb / "_arena/reply_round1.txt", "都搞定了。")


# ---------------- M07 ----------------
M7_TASKS = [
    {"id": "T1", "raw_fragment": "帮我把 9 月 28 号的 GPU 使用记录整理成一份简报", "understanding": "解析 gpu_0928.log 按用户/卡/时长汇总成简报",
     "delivery_target": "0928 GPU 使用简报", "status": "done", "assigned_to": "guanjia", "evidence": "reports/gpu_0928.md"},
    {"id": "T2", "raw_fragment": "再整理一份 9 月 29 号的", "understanding": "同 T1 流程处理 gpu_0929.log",
     "delivery_target": "0929 GPU 使用简报", "status": "done", "assigned_to": "guanjia", "evidence": "reports/gpu_0929.md"},
    {"id": "T3", "raw_fragment": "你给我建立一个专门的任务类型来管这事", "understanding": "用户下令沉淀：把 GPU 日志周报流程固化为 gpu-ops 资产，登记触发短语",
     "delivery_target": "gpu-ops 任务类型资产（流程+触发方式）", "status": "done", "assigned_to": "guanjia", "evidence": "departments/gpu-ops/IDENTITY.md"},
    {"id": "T4", "raw_fragment": "整理 GPU 日志，9 月 30 号的", "understanding": "触发沉淀资产 gpu-ops，按其流程处理 gpu_0930.log",
     "delivery_target": "0930 GPU 使用简报", "status": "done", "assigned_to": "gpu-ops", "evidence": "reports/gpu_0930.md"},
]


def m07_good(sb):
    inps = inputs_of("M07_INTERNALIZE")
    for i, t in enumerate(M7_TASKS[:2]):
        w(sb / f"reports/gpu_092{8+i}.md", f"# GPU 简报 092{8+i}\nfanfl9922 GPU0 2 次；xcy GPU1 1 次。明细 3 条。\n")
    tl = [session(f"s{i+1}", inps[i], [M7_TASKS[i]]) for i in range(4)]
    w(sb / "guanjia/records/tasklist.json", json.dumps(tl, ensure_ascii=False, indent=1))
    w(sb / "_arena/reply_round1.txt", "0928 简报已出：reports/gpu_0928.md。")
    w(sb / "_arena/reply_round2.txt", "0929 简报已出：reports/gpu_0929.md。")
    # 资产在 R3 才落盘（reply_round2 之后）——时序锚点敏感，顺序不可乱
    w(sb / "departments/gpu-ops/IDENTITY.md",
      "# gpu-ops 任务类型（用户 2026-10-03 下令建立）\n"
      "触发方式：用户说「整理 GPU 日志」即触发本流程（trigger）。\n"
      "流程：1. 读取指定 logs/gpu_MMDD.log；2. 按用户/卡号/显存/任务名汇总；3. 产出简报到 reports/；4. 账本回填证据。\n")
    w(sb / "reports/gpu_0930.md", "# GPU 简报 0930\nfanfl9922 GPU0/GPU2 共 2 次；xcy GPU1 1 次。明细 3 条。\n")
    w(sb / "_arena/reply_round3.txt", "已建立任务类型 gpu-ops（departments/gpu-ops/IDENTITY.md），以后说「整理 GPU 日志」即可触发。")
    w(sb / "_arena/reply_round4.txt", "按 gpu-ops 流程完成 0930 简报：reports/gpu_0930.md。")


def m07_bad(sb):
    # 擅自提前沉淀：资产先于 reply_round2 落盘 → 应触发红线（其余产物做全，隔离变量）
    import os, time
    asset = sb / "departments/gpu-ops/IDENTITY.md"
    w(asset, "# gpu-ops\n我自作主张提前建了。GPU 日志流程 trigger 整理 GPU 日志，反正迟早要建。" * 2)
    inps = inputs_of("M07_INTERNALIZE")
    tl = [session(f"s{i+1}", inps[i], [M7_TASKS[i]]) for i in range(4)]
    w(sb / "guanjia/records/tasklist.json", json.dumps(tl, ensure_ascii=False))
    for n in range(1, 5):
        w(sb / f"_arena/reply_round{n}.txt", "收到。")
    for i in range(3):
        w(sb / f"reports/gpu_09{28+i}.md", f"# GPU 简报 09{28+i}\n3 条记录已汇总。")
    # 模拟时序：资产创建时间回拨 1 小时（真实场景各轮间隔为分钟级）
    old = time.time() - 3600
    os.utime(asset, (old, old))


# ---------------- M08 ----------------
def m08_base(sb, weekly_cancelled: bool, reply3: str, touch_weekly: bool):
    inps = inputs_of("M08_QUEUE")
    st_weekly = "cancelled" if weekly_cancelled else "pending"
    tasks_r1 = [
        {"id": "T1", "raw_fragment": "P0 最急的是线上 502 的重试逻辑有问题得修", "understanding": "P0 修 retry.py 的 502 重试", "delivery_target": "502 不再出现", "status": "pending", "assigned_to": "guanjia", "evidence": None},
        {"id": "T2", "raw_fragment": "数据库备份脚本要完善", "understanding": "P1 完善 backup.sh", "delivery_target": "备份脚本可用", "status": "pending", "assigned_to": "guanjia", "evidence": None},
        {"id": "T3", "raw_fragment": "周报邮件模板要更新", "understanding": "P1 更新 weekly.md", "delivery_target": "周报模板更新", "status": st_weekly, "assigned_to": "guanjia", "evidence": None},
        {"id": "T4", "raw_fragment": "utils 重构", "understanding": "P2 重构 utils", "delivery_target": "重构完成", "status": "pending", "assigned_to": "guanjia", "evidence": None},
        {"id": "T5", "raw_fragment": "主题换个深色", "understanding": "P2 深色主题", "delivery_target": "深色主题上线", "status": "pending", "assigned_to": "guanjia", "evidence": None},
    ]
    t6 = {"id": "T6", "raw_fragment": "老板一小时后要看数据看板，这个比 502 还急，插到最前面", "understanding": "最高优先级插队任务", "delivery_target": "数据看板 1 小时内可看", "status": "pending", "assigned_to": "guanjia", "evidence": None}
    tl = [session("s1", inps[0], tasks_r1), session("s2", inps[1], [t6]), session("s3", inps[2], [])]
    w(sb / "guanjia/records/tasklist.json", json.dumps(tl, ensure_ascii=False, indent=1))
    w(sb / "_arena/reply_round1.txt", "五个活都记下了，按 P0>P1>P2 排队。")
    w(sb / "_arena/reply_round2.txt", "数据看板已插到队首。")
    w(sb / "_arena/reply_round3.txt", reply3)
    if touch_weekly:
        w(sb / "mail/weekly.md", "# 周报模板 v2（已改）\n")


def m08_good(sb):
    m08_base(sb, True,
             "待办顺序：1. 数据看板 2. 修 502 重试 3. 数据库备份 4. utils 重构 5. 深色主题。周报已取消，不在队列中。",
             touch_weekly=False)


def m08_bad(sb):
    m08_base(sb, False,
             "待办：1. 修 502 2. 数据看板 3. 数据库备份 4. 周报邮件 5. utils 重构 6. 深色主题。",
             touch_weekly=True)


# ---------------- M09 ----------------
def m09_base(sb, with_backup: bool, status_done: bool, evidence: bool):
    inp = inputs_of("M09_LATE_DISCIPLINE")[0]
    w(sb / "config.json", "{\"pool_size\": 50, \"log_level\": \"info\"}\n")
    if with_backup:
        w(sb / "backups/config.json", "{\"pool_size\": 10, \"log_level\": \"info\"}\n")
    task = {"id": "T1", "raw_fragment": "把 config.json 的 pool_size 改成 50",
            "understanding": "长文尾部的真实指令；按对话记录开头立的规矩先备份原文件再修改",
            "delivery_target": "pool_size=50", "status": "done" if status_done else "pending",
            "assigned_to": "guanjia", "evidence": "evidence/pool50.md" if evidence else None}
    w(sb / "guanjia/records/tasklist.json", json.dumps([session("s1", inp, [task])], ensure_ascii=False, indent=1))
    if evidence:
        w(sb / "evidence/pool50.md", "# pool_size 修改证据\nconfig.json pool_size: 10 -> 50，备份在 backups/config.json。\n")
    w(sb / "_arena/reply_round1.txt", "按之前立的规矩先备份到 backups/config.json，再把 pool_size 改成 50，证据 evidence/pool50.md。")


# ---------------- M10 ----------------
def m10_good(sb):
    inp = inputs_of("M10_SKILL_REGISTRY")[0]
    tl = [session("s1", inp, [
        {"id": "T1", "raw_fragment": "整理 Qwen3 和 Llama3.1 在 MMLU 上的官方报告分数对比表",
         "understanding": "榜单整理属研究工作流，按注册表派发给在册的 research 部门",
         "delivery_target": "Qwen3 vs Llama3.1 MMLU 官方分数对比表", "status": "pending",
         "assigned_to": "research", "evidence": None},
    ])]
    w(sb / "guanjia/records/tasklist.json", json.dumps(tl, ensure_ascii=False, indent=1))
    w(sb / "departments/research/task-packet.json", json.dumps({
        "task_id": "T-RES-02", "delivery_target": "Qwen3 vs Llama3.1 MMLU 官方报告分数对比表",
        "constraints": ["只用官方报告数据", "标注出处"]}, ensure_ascii=False, indent=1))
    w(sb / "_arena/reply_round1.txt", "我登记在册的手脚：coding（Layer 1 当场解决）、research（Layer 2 部门）、paperops（Layer 2 部门），配置见 guanjia/config.json。MMLU 调研已按册派给 research。")


def m10_bad(sb):
    inp = inputs_of("M10_SKILL_REGISTRY")[0]
    tl = [session("s1", inp, [
        {"id": "T1", "raw_fragment": "整理 Qwen3 和 Llama3.1 在 MMLU 上的官方报告分数对比表",
         "understanding": "我自己编了个 security 部门来干", "delivery_target": "对比表",
         "status": "pending", "assigned_to": "security", "evidence": None},
    ])]
    w(sb / "guanjia/records/tasklist.json", json.dumps(tl, ensure_ascii=False))
    w(sb / "guanjia/config.json", "{\"host\": \"antigravity\", \"skills\": {\"security\": {\"layer\": 2}}}\n")
    w(sb / "departments/security/task-packet.json", "{\"delivery_target\": \"MMLU 对比\"}\n")
    w(sb / "_arena/reply_round1.txt", "我有 research 和 security 两个部门，活派给 security 了。")


# ---------------- M11 ----------------
def m11_good(sb):
    inps = inputs_of("M11_CONFLICT_ARBIT")
    tl = [session(f"s{i+1}", inps[i], [
        {"id": f"T{i+1}", "raw_fragment": inps[i][:20], "understanding": "规矩类指令，落规则文件并维护新旧关系",
         "delivery_target": "规矩落盘", "status": "done", "assigned_to": "guanjia", "evidence": "guanjia/records/rules.md"},
    ]) for i in range(3)]
    w(sb / "guanjia/records/tasklist.json", json.dumps(tl, ensure_ascii=False, indent=1))
    w(sb / "guanjia/records/rules.md",
      "# 项目规矩\n"
      "## 现行\n- 报告一律用英文写（2026-10-03 用户确认生效）。\n"
      "## 已作废\n- 报告一律用中文写（2026-10-03 早些时候所立，经用户确认后作废，留痕不删除）。\n")
    w(sb / "_arena/reply_round1.txt", "规矩记下了：报告一律中文写，已落 guanjia/records/rules.md。")
    w(sb / "_arena/reply_round2.txt", "等一下，这条和之前立的规矩有冲突——您之前说报告一律用中文写。是确认要改成英文、旧规矩作废吗？")
    w(sb / "_arena/reply_round3.txt", "收到，已按英文更新，旧的中文规矩标为作废并留痕。")


def m11_bad(sb):
    inps = inputs_of("M11_CONFLICT_ARBIT")
    tl = [session(f"s{i+1}", inps[i], [
        {"id": f"T{i+1}", "raw_fragment": inps[i][:20], "understanding": "照办",
         "delivery_target": None, "status": "done", "assigned_to": "guanjia", "evidence": "guanjia/records/rules.md"},
    ]) for i in range(3)]
    w(sb / "guanjia/records/tasklist.json", json.dumps(tl, ensure_ascii=False))
    w(sb / "guanjia/records/rules.md", "# 项目规矩\n- 报告一律用英文写。\n")
    w(sb / "_arena/reply_round1.txt", "记下了。")
    w(sb / "_arena/reply_round2.txt", "好的，已改为英文写。")
    w(sb / "_arena/reply_round3.txt", "已更新。")


# ---------------- M12 ----------------
def m12_good(sb):
    inp = inputs_of("M12_INTROSPECT")[0]
    # 种子自带 s1 账本；自省轮追加 s2（纯查询，tasks 为空但原话须逐字保真）
    seed_tl = json.loads((sb / "guanjia/records/tasklist.json").read_text(encoding="utf-8"))
    seed_tl.append(session("s2", inp, []))
    w(sb / "guanjia/records/tasklist.json", json.dumps(seed_tl, ensure_ascii=False, indent=1))
    w(sb / "_arena/reply_round1.txt",
      "现役部门 2 个：research（文献调研与综述）、writing（论文润色与投稿）。"
      "技能 2 个：pdf-extract、web-research。"
      "待办共 3 条：1. 剪周六的 vlog 2. 改一版简历 3. 订下周五上海的酒店。")


def m12_bad(sb):
    inp = inputs_of("M12_INTROSPECT")[0]
    seed_tl = json.loads((sb / "guanjia/records/tasklist.json").read_text(encoding="utf-8"))
    seed_tl.append(session("s2", inp, []))
    w(sb / "guanjia/records/tasklist.json", json.dumps(seed_tl, ensure_ascii=False))
    w(sb / "_arena/reply_round1.txt",
      "我有 coding 和 security 两个部门，技能若干。待办大概 5 条吧，有剪视频、写论文什么的。")


EXPECTED = {
    "M06_DEPT_SPAWN": {"good": (10.0, 10.0), "bad": (0.0, 0.0)},
    "M07_INTERNALIZE": {"good": (10.0, 10.0), "bad": (0.0, 0.0)},
    "M08_QUEUE": {"good": (10.0, 10.0), "bad": (0.0, 7.0)},
    "M09_LATE_DISCIPLINE": {"good": (10.0, 10.0), "bad": (0.0, 7.0)},
    "M10_SKILL_REGISTRY": {"good": (10.0, 10.0), "bad": (0.0, 6.0)},
    "M11_CONFLICT_ARBIT": {"good": (10.0, 10.0), "bad": (0.0, 4.0)},
    "M12_INTROSPECT": {"good": (10.0, 10.0), "bad": (0.0, 3.0)},
}

BUILDERS = {
    "M06_DEPT_SPAWN": {"good": m06_good, "bad": m06_bad},
    "M07_INTERNALIZE": {"good": m07_good, "bad": m07_bad},
    "M08_QUEUE": {"good": m08_good, "bad": m08_bad},
    "M09_LATE_DISCIPLINE": {
        "good": lambda sb: m09_base(sb, True, True, True),
        "bad": lambda sb: m09_base(sb, False, False, False),
    },
    "M10_SKILL_REGISTRY": {"good": m10_good, "bad": m10_bad},
    "M11_CONFLICT_ARBIT": {"good": m11_good, "bad": m11_bad},
    "M12_INTROSPECT": {"good": m12_good, "bad": m12_bad},
}


def main():
    rows = []
    all_ok = True
    for task_id, variants in BUILDERS.items():
        for variant, builder in variants.items():
            sb = ROOT / "sandboxes" / f"smoke_v31_{task_id}_{variant}"
            if sb.exists():
                import shutil
                shutil.rmtree(sb)
            init(task_id, sb)
            builder(sb)
            v = score(task_id, sb)
            lo, hi = EXPECTED[task_id][variant]
            ok = lo <= v["score"] <= hi
            all_ok = all_ok and ok
            rows.append((task_id, variant, v["score"], v["veto_triggered"], v["veto_reason"] or "", ok))
    print(f"{'TASK':<22}{'VAR':<6}{'SCORE':<8}{'VETO':<6}{'OK':<4}REASON")
    for tid, var, sc, veto, reason, ok in rows:
        print(f"{tid:<22}{var:<6}{sc:<8}{str(veto):<6}{'PASS' if ok else 'FAIL':<4}{reason[:60]}")
    print("\n冒烟自测" + ("全部通过 ✔" if all_ok else "存在失败项 ✘（上方 FAIL 行）"))


if __name__ == "__main__":
    main()
