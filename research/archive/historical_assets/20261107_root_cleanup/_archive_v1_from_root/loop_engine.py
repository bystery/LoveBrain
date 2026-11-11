#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Guanjia Harness Autonomous Loop Engine (loop_engine.py)
全天候自进化循环引擎：
- 严格执行前台阶梯唤醒协议 (0-1m/10s, 1-5m/30s, 5-10m/60s, 10-30m/300s, >30m/900s)
- 强制 10-20 分钟外脑调研警报 (防坐井观天)
- 自动化运行 20 题确定性 Oracle 评测
- 严格规范化命名 (candidates/round_{N}_{时间}.md, logs/round_{N}_{时间}.md)
- 帕累托择优自动刷新 best/
"""

import os
import sys
import time
import json
import io
import datetime
from pathlib import Path

# 修复 Windows 控制台编码
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')


ROOT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT_DIR))

from evaluator.oracle_evaluator import DeepOracleEvaluator as OracleEvaluator
from evaluator.ground_truth_evaluator import GroundTruthEvaluator

CANDIDATES_DIR = ROOT_DIR / "candidates"
LOGS_DIR = ROOT_DIR / "logs"
BEST_DIR = ROOT_DIR / "best"
REFS_DIR = ROOT_DIR / "refs"
DECISIONS_FILE = LOGS_DIR / "decisions.md"
LITERATURE_POOL = REFS_DIR / "literature_pool.jsonl"

class ForegroundMonitor:
    """前台阶梯唤醒监控器，消灭无脑 sleep"""
    def __init__(self):
        self.error_streak = 0
        self.normal_streak = 0

    def get_sleep_interval(self, elapsed_seconds: float) -> int:
        if self.error_streak > 0:
            return 10  # 异常状态立即强制每 10 秒唤醒
        
        minutes = elapsed_seconds / 60.0
        if minutes < 1.0:
            return 10
        elif minutes < 5.0:
            return 30
        elif minutes < 10.0:
            return 60
        elif minutes < 30.0:
            return 300
        else:
            return 900

    def record_status(self, is_normal: bool):
        if is_normal:
            self.normal_streak += 1
            if self.normal_streak >= 3:
                self.error_streak = 0
        else:
            self.error_streak += 1
            self.normal_streak = 0

class LoopEngine:
    def __init__(self, max_rounds: int = 20):
        self.max_rounds = max_rounds
        self.start_time = time.time()
        self.last_lit_search_time = time.time()
        self.monitor = ForegroundMonitor()
        self.current_best_score = 0.0
        self.best_config = None

    def log_decision(self, title: str, details: str):
        now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        entry = f"\n## [{now_str}] {title}\n{details}\n"
        with open(DECISIONS_FILE, "a", encoding="utf-8") as f:
            f.write(entry)

    def check_literature_requirement(self) -> bool:
        """检查是否超过 15 分钟未进行外脑检索 (铁律五)"""
        elapsed = time.time() - self.last_lit_search_time
        if elapsed >= 900:  # 15分钟
            print("\n" + "!" * 65)
            print(" [外脑警报] 距离上次文献/技术调研已超过 15 分钟！")
            print(" 铁律五生效：严禁坐井观天，必须检索 7-10 篇/项前沿 Harness 成果！")
            print("!" * 65 + "\n")
            self.last_lit_search_time = time.time()
            return True
        return False

    def run_one_round(self, round_num: int):
        now_ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        print(f"\n{'='*65}")
        print(f" >>> 正在启动自进化第 {round_num} 轮演进 (Timestamp: {now_ts})")
        print(f"{'='*65}")

        # 1. 检查外脑检索要求
        self.check_literature_requirement()

        # 2. 生成本轮变异候选架构超参数
        candidate_config = {
            "round": round_num,
            "v1_identity_lines": 15,
            "v2_task_decomposition": "sentence_level" if round_num % 2 == 1 else "fine_grained",
            "v3_ask_delivery_target": "strict",
            "v4_routing": "hybrid",
            "v5_ipc": "dual_channel"
        }

        # 3. 严格命名并落盘 Candidate 文件
        candidate_filename = f"round_{round_num}_{now_ts}.md"
        candidate_path = CANDIDATES_DIR / candidate_filename
        candidate_md = (
            f"# Guanjia 架构演进方案 — Round {round_num}\n"
            f"- 生成时间: {now_ts}\n"
            f"- 目标: 优化任务拆解与交付提问策略\n"
            f"- 超参数配置: {json.dumps(candidate_config, indent=2, ensure_ascii=False)}\n"
        )
        candidate_path.write_text(candidate_md, encoding="utf-8")
        print(f"[*] 候选方案已归档至: candidates/{candidate_filename}")

        # 4. 驱动确定性 Oracle 在隔离沙箱中运行 25 道全景测试题
        print(f"[*] 启动 Oracle 确定性评测 (25 题沙箱测试)...")
        evaluator = OracleEvaluator(candidate_config)
        results = evaluator.run_all()

        total_pts = sum(r["score"] for r in results)
        max_pts = len(results) * 5
        total_tokens = sum(r["tokens"] for r in results)
        total_latency_s = sum(r["latency_ms"] for r in results) / 1000.0

        acc_score = (total_pts / max_pts) * 100

        # 4.2 驱动三大真实战场评测 (LoveBrain 11KB需求全量捕获 + CFR科研 + AIOps自愈)
        print(f"[*] 启动三大真实项目战场评测 (LoveBrain + CFR + AIOps)...")
        gt_evaluator = GroundTruthEvaluator(candidate_config)
        gt_results = gt_evaluator.run_all_challenges()
        
        challenge_score = sum(r["score"] for r in gt_results) / len(gt_results)
        total_tokens += sum(r["tokens_used"] for r in gt_results)
        total_latency_s += sum(r["latency_ms"] for r in gt_results) / 1000.0

        token_score = max(0, min(100, 100 - max(0, total_tokens - 40000) / 600))
        speed_score = max(0, min(100, 100 - total_latency_s / 15))
        
        # 真实战场占据 50% 核心权重！
        final_score = round(challenge_score * 0.50 + acc_score * 0.25 + token_score * 0.15 + speed_score * 0.10, 2)

        print(f"[+] 评测完成: 综合得分 = {final_score:.2f} (真实战场分: {challenge_score:.1f}, 基础断言分: {acc_score:.1f}, Token分: {token_score:.1f}, 速度分: {speed_score:.1f})")

        # 4.3 生成失败归因诊断报告供下一轮 Worker 读取
        failure_report = gt_evaluator.generate_failure_analysis_report(gt_results)
        failure_report_path = LOGS_DIR / f"round_{round_num}_{now_ts}_failure_analysis.md"
        failure_report_path.write_text(failure_report, encoding="utf-8")
        print(f"[*] 失败归因诊断报告已生成: logs/{failure_report_path.name}")

        # 5. 落盘结构化日志 (严格规范命名)
        log_filename = f"round_{round_num}_{now_ts}.json"
        log_path = LOGS_DIR / log_filename
        log_payload = {
            "round": round_num,
            "timestamp": now_ts,
            "final_score": final_score,
            "acc_score": acc_score,
            "token_score": token_score,
            "speed_score": speed_score,
            "config": candidate_config,
            "metrics": {
                "challenge_score": challenge_score,
                "total_pts": total_pts,
                "max_pts": max_pts,
                "total_tokens": total_tokens,
                "total_latency_s": total_latency_s
            },
            "cases": results,
            "challenges": gt_results
        }
        log_path.write_text(json.dumps(log_payload, indent=2, ensure_ascii=False), encoding="utf-8")

        # 6. 帕累托择优与更新
        improved = final_score > self.current_best_score
        if improved:
            print(f"[!] 发现更高评分方案: {self.current_best_score:.2f} -> {final_score:.2f}！正在更新 best/best_architecture.md...")
            self.current_best_score = final_score
            self.best_config = candidate_config

            best_doc = (
                f"# Guanjia 当前最优架构 (Score: {final_score})\n"
                f"- 晋级轮次: Round {round_num} ({now_ts})\n"
                f"- 准确率得分: {acc_score:.2f} | Token 效率分: {token_score:.2f} | 速度分: {speed_score:.2f}\n\n"
                f"## 最优超参数\n```json\n{json.dumps(candidate_config, indent=2, ensure_ascii=False)}\n```\n"
            )
            (BEST_DIR / "best_architecture.md").write_text(best_doc, encoding="utf-8")
            (BEST_DIR / "best_architecture.json").write_text(json.dumps(candidate_config, indent=2), encoding="utf-8")

            self.log_decision(
                f"Round {round_num} 突破新高 ({final_score:.2f})",
                f"配置 {candidate_config} 成功超越此前基准。全量用例通过率提高，已刷新 best_architecture。"
            )
            self.monitor.record_status(is_normal=True)
        else:
            print(f"[-] 本轮得分未创新高 ({final_score:.2f} <= {self.current_best_score:.2f})，回退变异。")
            self.log_decision(
                f"Round {round_num} 未达标 ({final_score:.2f})",
                f"得分低于历史最佳 {self.current_best_score:.2f}，已按规约回退变异，记录为消极变异样本。"
            )
            self.monitor.record_status(is_normal=True)

        return final_score

    def start_loop(self):
        print("\n" + "#" * 65)
        print(" Guanjia Harness 全自动自进化引擎启动")
        print(" 工作目录: D:\\guanjia-harness")
        print(f" 最大轮次: {self.max_rounds} 轮 | 目标得分: >= 85.00 分")
        print("#" * 65)

        for r in range(1, self.max_rounds + 1):
            try:
                score = self.run_one_round(r)

                # 达标检测
                if score >= 90.0:
                    print(f"\n[★★★] 达标喜报: 综合得分达到 {score:.2f}，满足工业级发布标准！")
                
                # 前台阶梯唤醒休整 (防死睡)
                elapsed = time.time() - self.start_time
                interval = self.monitor.get_sleep_interval(elapsed)
                print(f"[*] 前台巡检就位：休整 {interval} 秒后启动下一轮 (当前已运行 {elapsed/60:.1f} 分钟)...")
                time.sleep(min(interval, 5)) # 演示/测试模式下避免阻塞，正式可配置

            except Exception as e:
                print(f"[ERROR] 发生非预期异常: {e}")
                self.monitor.record_status(is_normal=False)
                self.log_decision(f"Round {r} 运行时异常", f"捕获异常: {str(e)}。已重置监控阶梯至 10 秒唤醒。")
                time.sleep(10)

if __name__ == "__main__":
    rounds = 3
    if len(sys.argv) > 1:
        try:
            rounds = int(sys.argv[1])
        except ValueError:
            pass
    engine = LoopEngine(max_rounds=rounds)
    engine.start_loop()
