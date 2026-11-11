# -*- coding: utf-8 -*-
"""iso_run.py — 隔离运行 CLI（同一命令，仅切换 --candidate；唯一可信入口）。

python tools/iso_run.py --episode benchmark/episodes/X.json \
    --candidate phase3_facts --out runs/iso_x \
    [--adapter mock] [--quota N] [--stop-after N] [--resume]

new/resume 分离（V14 执行书 B1）：
  - new（默认）：out 目录已有历史证据（检查点/轮目录/manifest）→ 拒绝启动；
  - --resume：不删除 staged/out/control，校验 run_identity + 检查点身份
    （候选/题面）后从检查点恢复；额度从持久 attempt_started 账本重建，
    attempt_id 延续原 run_id（不重置）。

复用 arena/isolation/trusted_controller.IsolatedRun：候选机制只在不可信
宿主进程导入（控制器零候选执行）、同步代理生成、可信侧评分、attempt 账本落盘。
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__))), "arena"))
from isolation.trusted_controller import IsolatedRun  # noqa: E402
from lb_gateway import OFFLINE_ADAPTERS  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--episode", required=True)
    ap.add_argument("--candidate", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--adapter", default="mock")
    ap.add_argument("--quota", type=int, default=8)
    ap.add_argument("--stop-after", type=int, default=None)
    ap.add_argument("--resume", action="store_true")
    ap.add_argument("--run-dir", default=None,
                    help="staging 目录（默认 out 的兄弟 _run）")
    ap.add_argument("--require-assets", default="1")
    ap.add_argument("--authorization", default=None,
                    help="授权单 JSON（含 authorization_id/real_endpoint；"
                         "非 mock 适配器必填——复核 v3 §2A：入口强制预算权威。"
                         "预算权威在授权层账本 logs/auth_budget/<id>.jsonl，"
                         "跨 run/进程/重启不重置；episode 结束不关闭授权，"
                         "批准的运行清单全部结束后用 --close-authorization 收口）")
    ap.add_argument("--expect-attempts", type=int, default=None,
                    help="整批预检声明的实际批需次数（默认=剩余轮次；"
                         "存在辅助调用时必须按实际路径重算后显式声明，"
                         "不能把题位数当请求次数）")
    ap.add_argument("--close-authorization", action="store_true",
                    help="运行结束后由可信批次收口关闭整个授权"
                         "（仅当本 run 是批准运行清单的最后一项）")
    args = ap.parse_args()

    ep = os.path.join(ROOT, args.episode) if not os.path.isabs(args.episode) \
        else args.episode
    out = args.out if os.path.isabs(args.out) else os.path.join(ROOT, args.out)
    run_dir = args.run_dir or os.path.join(
        os.path.dirname(out.rstrip("\\/")) or ROOT,
        "_iso_run_" + os.path.basename(out.rstrip("\\/")))
    control = os.path.join(out, "_control")

    # 复核 v3 §2A：入口强制——非离线适配器（真实上游）必须提供授权单。
    # 离线替身（mock/echo）零真实调用，故与控制器同一份允许清单。
    if args.adapter not in OFFLINE_ADAPTERS and not args.authorization:
        print(json.dumps({"error":
                          "non-offline adapter requires --authorization "
                          "(budget authority is mandatory at the entry)",
                          "adapter": args.adapter}, ensure_ascii=False))
        return 2

    authorization = None
    if args.authorization:
        from isolation.authorization_budget import (
            validate_authorization_doc, BudgetRefused)
        auth_p = args.authorization if os.path.isabs(args.authorization) \
            else os.path.join(ROOT, args.authorization)
        try:
            aj = json.load(open(auth_p, encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as e:
            print(json.dumps({"error": "authorization file unreadable: %s"
                              % e}, ensure_ascii=False))
            return 2
        env_api = os.environ.get("LB_API_BASE", "") or None
        env_model = os.environ.get("LB_MODEL", "") or None
        try:
            norm = validate_authorization_doc(
                aj, expected_endpoint=env_api, expected_model=env_model)
        except BudgetRefused as e:
            print(json.dumps({"error": "authorization invalid: %s" % e},
                             ensure_ascii=False))
            return 2
        authorization = {
            "authorization_id": norm["authorization_id"],
            "max_attempts": norm["max_attempts"],
            "api_base": norm["api_base"], "model": norm["model"],
            "temperature": norm["temperature"], "status": norm["status"],
            "budget_ledger": os.path.join(
                ROOT, "logs", "auth_budget",
                "%s.jsonl" % norm["authorization_id"]),
            "batch_need": args.expect_attempts,
        }

    run = IsolatedRun(run_dir, out, control, args.candidate,
                      adapter=args.adapter, quota=args.quota,
                      resume=args.resume,
                      authorization=authorization).stage()
    try:
        run.start()
        m, proxy = run.run_episode(
            ep, stop_after=args.stop_after, resume=args.resume,
            require_assets=args.require_assets == "1")
        print(json.dumps({"episode_id": m["episode_id"], "turns": m["turns"],
                          "complete": m["complete"],
                          "mean_score10": m["mean_score10"],
                          "veto_turns": m["veto_turns"],
                          "mechanism": m["mechanism"],
                          "lifecycle": m["lifecycle"],
                          "adapter": m["adapter"],
                          "settlement_pending_turns":
                              m.get("settlement_pending_turns"),
                          "budget": m.get("report_metadata", {}).get("budget"),
                          "run_id": m["run_id"]}, ensure_ascii=False))
        if args.close_authorization:
            cs = run.close_authorization(
                "trusted batch close: approved run list complete "
                "(final run %s)" % m["run_id"])
            print(json.dumps({"authorization_closed": cs}, ensure_ascii=False))
        return 0
    finally:
        run.close()


if __name__ == "__main__":
    sys.exit(main())
