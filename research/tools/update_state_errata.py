# -*- coding: utf-8 -*-
r"""tools/update_state_errata.py — 把 F1–F5 的终局状态与勘误写进 STATE_AND_ERRATA.json。

只新增/替换本主线相关的键（phases / errata_items / next_actions / cost_index），
不删除历史字段，避免"改账本自证"。所有数字都能从写明的证据文件复核。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/update_state_errata.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                    "final_mainline_review_20261029", "STATE_AND_ERRATA.json")


def main():
    doc = json.load(open(BASE, encoding="utf-8"))

    doc["phases"] = {
        "F0_ledger_errata": {
            "status": "done",
            "outputs": ["ACTION_OUTCOMES_130.jsonl", "JUDGMENT_ERRATA_20261029.json"],
            "headline": "130 对双臂账本；4 对卡面冲突；撤回三格过度沉默与 FIX-9 2→0",
        },
        "F1_blind_review": {
            "status": "done",
            "outputs": ["F1_REPORT.md", "F1_ANALYSIS.json",
                        "logs/f1_blind_review_20261029_094731"],
            "headline": "158 次额度用尽（155 完成 + 3 孤儿不重试）；对照 16/16 全对；"
                        "历史 127 对的候选臂其实是 r1/r2/r3（按批次分列，见 E-F6-A），"
                        "读数 42/24/54/7 只能归给各批身份，不能归给 r4；"
                        "worker 旧标只有 48.15% 一致，不能当金标；顺序一致性 10/12",
            "authorization": "AUTH-F1-BLIND-20261029-01 (closed)",
        },
        "F2_freeze": {
            "status": "done",
            "outputs": ["F2_FREEZE_RECORD_20261029.json",
                        "FINAL_CANDIDATE_FREEZE_20261029.json"],
            "identity_sha256": "9b8f4e1eae4c68c007c29f8f1696eafdc29220354d792ccd0c1f00a9783aafb4",
            "headline": "以 r2 为基新建 action_policy_r4_guard_final（三项确定性改动，无动作策略变更）",
        },
        "F3_new_material": {
            "status": "done",
            "outputs": ["F3_REPORT.md", "F3_ANALYSIS.json", "F3_KNOWLEDGE_CHAIN_SUMMARY.json",
                        "logs/f3_newmat_live_restart_20261029",
                        "logs/f3_judge_20261029_115709"],
            "headline": "4 篇新故事 72 位、144/144 生成（K 链真实 T12 checkpoint 续跑 T13–T24）；"
                        "盲评 72/72：r4 38 / 原版 10 / 平 24，正面信号片 5:2:5；"
                        "交付分列：合同 veto 原版 2（K02_T04、N02_T08）/ 候选 1（N01_T05），"
                        "无法提取正文 原版 0 / 候选 1；major 条目候选 8（5 位点）/ 原版 5（5 位点）；"
                        "同批二级：总 token −10.10%、中位耗时 +17.35%；"
                        "长期召回未证实（9 个必需原子里仅 2 个真出窗、出窗且被投影单独保留 0）",
            "authorizations": ["AUTH-F3-NEWMAT-20261029-01 (closed, 首批 48 次被上游超时与主机内存事故打断，作废不补跑)",
                                "AUTH-F3-RESTART-20261029-01 (closed, 144/144)",
                                "AUTH-F3-JUDGE-20261029-01 (closed, 72/72)"],
        },
        "F4_component_ablation": {
            "status": "done_with_invalid_arm",
            "outputs": ["F4_REPORT.md", "F4_ANALYSIS.json", "logs/f4_component_20261029",
                        "logs/f4_component_20261029_retry", "logs/f4_component_knowfix_20261029",
                        "logs/f4_judge_20261029_120529"],
            "headline": "行动层 6 对有效但无方向：on 1 / off 1 / tie 4；"
                        "知识投影 6 对无效：去掉投影后 5/6 仍能从近期原文拿到答案数字，不构成消融；"
                        "结论是未见稳定组件收益，也不建议据此删组件",
            "authorizations": ["AUTH-F4-LOCAL-20261029-01 (closed, 24 次实调用 + 8 次重复派发被拒 0 调用)",
                                "AUTH-F4-KNOWFIX-20261029-01 (closed, 12/12)",
                                "AUTH-F4-JUDGE-20261029-01 (closed, 12/12)"],
        },
        "F5_repair_recheck": {
            "status": "done",
            "outputs": ["F5_REPORT.md", "F5_ANALYSIS.json", "F5_REPLAY.json",
                        "F5_CANDIDATE_FREEZE_20261029.json",
                        "logs/f5_recheck_live2_20261029_133653",
                        "logs/f5_judge_20261029_134800", "logs/f5_judge_20261029_140906"],
            "headline": "E3 修复 N01_T05 括号缺陷：72 份重放只改这 1 条、260 份历史答卷零回归；"
                        "新身份实跑 N01 12/12 可交付；同臂重抽 24 份评分 frozen 10 / repaired 7 / tie 7"
                        "（两侧本是同一 harness 的两次抽样，不读作质量下降）",
            "new_identity": {
                "candidate": "action_policy_r4_guard_e3",
                "identity_sha256": "17fd2160e5306a23ba7ac52a2c5c716d4a58e345e5781d84890b69465fb76d17",
                "code_provenance_to_live_recheck": "8/8 机制与资产文件逐字节相同",
            },
            "authorizations": ["AUTH-F5-RECHECK-20261029-01 (closed, 12/12)",
                                "AUTH-F5-JUDGE-20261029-01 (closed, 12/12)",
                                "AUTH-F5-JUDGE-SWAP-20261029-01 (closed, 12/12)"],
        },
        "F6_final_verdict": {
            "status": "executed_acceptance_open",
            "outputs": ["F6_FINAL_VERDICT_20261029.md", "F6_FINAL_VERDICT_20261029_REV2.md",
                        "F6_TABLES.json", "F6_COST_INDEX.json",
                        "REFACTOR_NOTES_20261029.md", "F3_RESUME_NOTE.json"],
            "verdict": "REWORK（执行完成、验收未完成；REV2 已撤回初稿的两处事实错误与两处夸大口径）",
            "headline": "r4 的唯一质量证据是 F3 新材料 72 对：38 胜 / 10 负 / 24 平，九维配对增益全为正；"
                        "同批二级代价为总 token −10.10%、完成耗时中位数 +17.35%。"
                        "知识链 Retrieval 未补齐（出窗且被投影单独保留 0/9，Capture/Fidelity/Update=NR）；"
                        "正向能力部分成立（邀约 7/10 胜），转场/反撩/深聊/收尾无单项验收；"
                        "严重退化未被排除（候选 8 条 major/5 位点，集中在无据确定化；原版合同 veto 2 次）",
            "attribution_correction": "F1 历史 127 对属 r1/r2/r3 各批，与 r4 无关（见 E-F6-A）",
            "post_verdict_supplement": "F_BCD_CLOSEOUT_ADDENDUM_20261029.md"
                                       "（B/C/D 实测后追加；本段 headline 里"
                                       "“Retrieval 未补齐/严重退化未排除”两句已被 B 实测与 C 重判更新，"
                                       "以补充件与本 phases.B/C 为准，原判词文件不回改）",
            "minimal_remaining_items": [
                "B：真实跨窗口检索与更新（先零调用四前置，通过后 ≤24 生成）",
                "C：无据确定化（一个通用假设，冻结后 ≤24 生成三领域正反对照）",
                "D：≤12 对人工匿名评分（W 未知不代填）"],
        },
        "B_knowledge_probe": {
            "status": "done",
            "outputs": ["F_B_PROBE_REGISTRATION.json", "F_B_PRECHECK.json",
                        "KB_PROBE_AUTHORIZATION_20261029.json",
                        "F_B_KNOWLEDGE_RESULT.json", "F_B_KNOWLEDGE_REPORT.md",
                        "F_BCD_CLOSEOUT_ADDENDUM_20261029.md",
                        "logs/kp_precheck_20261029", "logs/kb_probe_live_20261029_164332"],
            "tools": ["tools/knowledge_probe_precheck.py", "tools/build_kb_probe_authorization.py",
                      "tools/run_knowledge_probe_batch.py",
                      "tools/analyze_knowledge_probe_batch.py",
                      "tools/annotate_knowledge_probe_grades.py"],
            "identity_used": "17fd2160e5306a23ba7ac52a2c5c716d4a58e345e5781d84890b69465fb76d17",
            "headline": "首个“值确已离开 1400CU 原文窗口”的真实生成实测：8 个候选探针零调用前置全过、"
                        "按写死配额取 6（归属2/更正2/时间2，3 个生活领域）；"
                        "夹具 76 轮零网络不计费；每格派发前逐字节断言实拼 prompt==冻结 on_prompt，"
                        "off=同 prompt 精确删三段投影，24/24 sha 匹配、0 失败、0 unknown_usage。"
                        "结果：Retrieval 12/12；Use on 11/12 vs off 0/12；Update 3/4；Attribution 4/4；"
                        "污染 1/24（KP-C2 on:r1 把 f_0055 的事件日期接成“28号”，on:r2 给对 11月8号，"
                        "可复现性 1/2，且含素材缺陷 E-MAT-B）；off 侧 12/12 如实弃权未编造。"
                        "资源：on 83,869 token / off 79,686，投影层每次请求约 +401 prompt token（CU 差 519–819），"
                        "每次成功跨窗使用≈7,624 on token，金额 null",
            "ablation_method": "off＝同一 on prompt 删除三段声明投影；此后投影层消融一律用此口径"
                               "（F4 的 memory_off/evidence_memory=false 会同时清历史或改计划块表头，无效）",
            "authorization": "AUTH-KB-PROBE-20261029-01 (closed, 24/24 消耗，不复活不补跑)",
        },
        "C_major_reclass_hypothesis": {
            "status": "analysis_done_dispatch_not_registered",
            "outputs": ["F_C_MAJOR_RECLASS_20261029.json", "F_C_MAJOR_RECLASS_20261029.md",
                        "F_C_HYPOTHESIS_FREEZE_20261029.json",
                        "F_C_HYPOTHESIS_FREEZE_20261029.md"],
            "tools": ["tools/build_c_severe_reclass.py", "tools/build_c_hypothesis_freeze.py"],
            "headline": "按完整前史逐条重判 13 条 major：候选 8 条/5 位点收敛为一个错误族"
                        "（无据确定化 3 位点：N02_T03/K01_T19/K01_T20），任务遗漏 0、编造事件 0；"
                        "拒收判词 5/8（2 条整份交付已条件化、1 条判词理由错、2 条卡面前提在原文查不到）；"
                        "原版 5 条/5 位点全部成立（放弃召回/算术 3、单方变更 1、替她决定 1）。"
                        "三条错误率分列不合并。唯一通用假设 assertion_tiering 已 FROZEN"
                        "（EVIDENCED/PROPOSAL/NEEDS_CHECK + 分支表达，禁题材特判、禁全量 hedge），"
                        "实现与新身份未开工、对照批 ≤24 生成未登记未派发",
            "ruler_proposal": "RULER-C-01（K02_T16 卡面把未敲定的减两千当已生效）— 提案未生效，待用户裁定",
            "self_risk": "重判方向对候选有利，故每条拒收都附逐字前史证据；分歧项登记为尺子提案而非自行改判",
        },
        "D_human_rating": {
            "status": "material_ready_unrated",
            "outputs": ["F_D_HUMAN_PAIRS_WORKSHEET.md", "F_D_HUMAN_PAIRS_BINDING.json"],
            "tools": ["tools/build_d_human_pairs.py"],
            "headline": "12 对匿名甲/乙（9 个 major 位点 + 按 unit_id 字典序补 3 个两臂无 major 位点），"
                        "只判愿发/需改/不愿发，不核事实、不出现臂名与成本；甲乙顺序由 seed 决定且只写入绑定件。"
                        "尚无人工评分 → W 仍为未知，AI 不代填",
        },
    }

    doc.setdefault("errata", {})
    doc["errata"]["items_20261029_phases"] = [
        {"id": "E-F5-A", "kind": "process_error_reverted",
         "what": "F5 先把 E3 写进已冻结的 action_policy_r4_guard_final 目录，违反“旧身份不覆盖”纪律",
         "fix": "按 FINAL_CANDIDATE_FREEZE 逐字节还原（重算身份一致），E3 另注册为 action_policy_r4_guard_e3；"
                "F5 那 12 次复测证据通过 staged 副本逐字节比对归属新身份，未为此重复付费"},
        {"id": "E-F4-A", "kind": "invalid_measurement",
         "what": "F4 知识投影消融无效：首轮问句自带答案数字，去数字重跑后 5/6 仍在近期原文里能找到答案",
         "consequence": "不宣称知识通道有增益，也不据此删除记忆投影段"},
        {"id": "E-F3-A", "kind": "design_limit",
         "what": "长期召回未被验证：1400 CU 预算下早期事实大多仍在原文窗口内，9 个必需原子只有 2 个真出窗",
         "consequence": "K 链优势解释为近期上下文使用与协议合规，不写成“长期记忆赢”",
         "superseded_by": "phases.B_knowledge_probe：用夹具把来源句挤出实际出站窗口后实测 6 探针，"
                          "Use on 11/12 vs off 0/12；原记录保留不回改"},
        {"id": "E-MAT-B", "kind": "material_defect",
         "what": "K02 素材内部日期冲突：消息时间戳在 2026-05/06（uid60『瓷砖到了 但色差有点大』=2026-05-28），"
                 "而内容日期写『瓷砖改到11月8号到』（uid15）；记忆库如实写回两条（f_0011 与 f_0055），"
                 "投影同时给出",
         "consequence": "B 批 KP-C2 on:r1 把事件日期接成“28号就到了”（on:r2 给对 11月8号，可复现性 1/2）。"
                        "素材缺陷单列不计候选胜负，但该格无据断言仍计入污染计数；"
                        "修素材属题库版本化改动，不为改素材重跑既有生成"},
        {"id": "E-TOOLS-B", "kind": "tool_reporting_defect",
         "what": "tools/run_knowledge_probe_batch.py 首版把 AuthorizationBudget.close() 的 None 返回值"
                 "当作“未关单”打印 closed=False，而账本末事件实为 authorization_closed",
         "consequence": "数据未受影响（24/24 完成且已关单）；工具已改为回读 closed 标志与账本末事件"},
        {"id": "E-F3-B", "kind": "aborted_batch",
         "what": "首批 F3 生成被上游超时与主机内存事故打断：48 started / 42 completed / 5 failed",
         "consequence": "整批作废、不补跑、不退款；内存修复后以新授权单重跑 144/144"},
        {"id": "E-F1-A", "kind": "orphan_attempts",
         "what": "F1 续跑前有 3 个在途 attempt 被 kill 留下孤儿记录",
         "consequence": "按不重试原则排除（EXCLUDED_ORPHANS.json），额度 158/158 用尽关闭"},
        {"id": "E-F3-C", "kind": "budget_bookkeeping",
         "what": "编排器对 K02 候选续跑按整批 24 预检，剩余额度只有 12，触发 1 次 batch_rejected（0 调用）",
         "consequence": "手工以 --expect-attempts 12 续跑 T13–T24；编排器已改为可传 expect"},
        {"id": "E-TOOLS-A", "kind": "tooling_defect",
         "what": "run_f3_new_material.py 结尾关单代码在仓库根目录执行时 ModuleNotFoundError: arena",
         "consequence": "该批授权单当时手工关闭，账本口径不受影响；缺陷记入 REFACTOR_NOTES"},
        {"id": "E-MAT-A", "kind": "path_inconsistency_benign",
         "what": "盲材生成读 benchmark/episodes/*.json，而实跑读 benchmark/stories_v2/final_20261029/*/episode.json",
         "verification": "四篇故事的两种路径按 sort_keys JSON 规范化后散列逐一相等（内容同源，仅缩进/换行不同），"
                         "裁判上下文与实跑输入语义一致",
         "consequence": "记为工程整洁问题（应单一来源），不改数据、不影响结论"},
        {"id": "E-JUDGE-A", "kind": "judge_limit",
         "what": "全部盲评由 hy3 自动裁判完成，无人类 taste 校准；顺序复测一致性 F1 10/12、F5 9/12",
         "consequence": "自动分只作复核线索，胜负结论要求两次顺序同向或标注为噪声"},
        {"id": "E-F6-A", "kind": "factual_error_corrected",
         "what": "F6 初稿把 F1 历史 127 对（42:24:54）记在 r4 名下",
         "truth": "ACTION_OUTCOMES_130.jsonl 的 candidate_arm 实测：S1/S2/S3=action_policy_r1、"
                  "S2R/D1=action_policy_r2、R3A/R3B=action_policy_r3；r4 从未跑过历史批次，"
                  "R3A 的父本还是 action_policy_r2。混合历史均分不能给 r4 做非劣证明。",
         "correction_file": "F6_FINAL_VERDICT_20261029_REV2.md",
         "correct_scope": "r4 的成绩只有 F3 新材料 72 对 + F4 消融 6 对 + F5 复测 12 轮"},
        {"id": "E-F6-B", "kind": "factual_error_corrected",
         "what": "F3/F6 初稿把交付缺陷只统计成‘候选 1 例不可交付、对照 0 例’",
         "truth": "144 份 scoring.json 的 veto 字段实测：对照原版 2 次合同 veto"
                  "（K02_T04 json_schema_broken、N02_T08 illegal_source_id:t7_0,t7_1），候选 1 次"
                  "（N01_T05 json_schema_broken）；‘无法提取正文’才是原版 0 / 候选 1。"
                  "错因：只统计 action_class，未把合同 veto 分列。",
         "extra": "major 也按条目与位点分列：候选 8 条/5 位点，原版 5 条/5 位点，重叠仅 K02_T16"},
        {"id": "E-F6-C", "kind": "overclaim_withdrawn",
         "what": "撤回两条结论：‘无待推进项/执行完成≈验收完成’与‘未见系统性退化（≈退化已排除）’",
         "truth": "候选 8 条 major 集中在把未知/暂定说成确定；4 篇故事内部相关；作者出卡前看过答卷；"
                  "8 道控制题×双顺序不等于 16 个独立校准样本；裁判无人工校准。"
                  "正确口径：本批未见质量下降，但退化未被排除，总目标仍 REWORK。"},
        {"id": "E-F6-D", "kind": "metric_basis_fixed",
         "what": "二级指标必须同批双臂重算；旧 v5 的‘省 57%’不得算给当前版本",
         "truth": "F3 同批 72:72：总 token 564,069→507,100（−10.10%，prompt −12.08%、completion +34.12%）；"
                  "完成耗时中位数 20.06s→23.54s（+17.35%），p95 59.46s→76.24s（+28.22%），合计 +17.20%；金额 null。"},
        {"id": "E-F6-E", "kind": "scope_reduced",
         "what": "撤回‘为补身份需全量重跑 e3（130+72）’这项待办",
         "truth": "外部独立离线等价重放（submission_review_20261029_1510/E3_INDEPENDENT_REPLAY.json）确认："
                  "72 份原文 71 份对象不变，仅 N01_T05 由不可解析变可解析；加上已有 12 轮真实复跑，证据已足够。"},
        {"id": "E-F6-F", "kind": "ledger_completeness",
         "what": "补全孤儿与用量口径",
         "truth": "孤儿共 4：F1 三个在途（F1H:S1:E027_T07/T08/T09）+ F3 作废批一个"
                  "（iso_20261029_101657_7fd288_g0005）。F3/F5 生成 token 取答卷 calls.json，"
                  "裁判 token 取账本 usage，两桶不重叠；均无 cache 命中信息；作废批 48/42/5 花钱不进结论。"},
    ]

    doc["cost_index"] = {
        "file": "F6_COST_INDEX.json",
        "totals": {"attempt_started": 530, "attempt_completed": 521,
                    "attempt_failed": 5, "dispatch_or_batch_refused": 9,
                    "note": "含收口 B 探针批 24 started / 24 completed（AUTH-KB-PROBE-20261029-01，已关单）"},
        "tokens": {"from_ledger_usage": 1078663, "from_answer_files": 1153468,
                    "from_kb_probe_ledger": 163555,
                    "basis": "账本 usage 覆盖全部裁判与 F4 生成；F3/F5 生成的 token 由答卷 calls.json 去重汇总；"
                             "B 探针批 24 格 token 取自授权账本 attempt_completed 事件（0 格 unknown_usage）"},
        "orphan_attempts": {"count": 4,
                             "f1": ["judge:F1:F1H:S1:E027_T07:18", "judge:F1:F1H:S1:E027_T08:19",
                                      "judge:F1:F1H:S1:E027_T09:20"],
                             "f3_aborted": ["iso_20261029_101657_7fd288_g0005"]},
        "secondary_metrics_same_batch_f3": {
            "parent": {"prompt": 539902, "completion": 24167, "total": 564069,
                        "latency_median_ms": 20062, "latency_p95_nearest_rank_ms": 59460},
            "candidate": {"prompt": 474687, "completion": 32413, "total": 507100,
                           "latency_median_ms": 23543, "latency_p95_nearest_rank_ms": 76241},
            "delta": {"total_tokens_pct": -10.10, "prompt_pct": -12.08, "completion_pct": 34.12,
                       "latency_median_pct": 17.35, "latency_p95_pct": 28.22,
                       "latency_sum_pct": 17.20},
            "money": None,
            "note": "旧 v5 的‘省 57%’不得算给当前身份；本批唯一合法口径是这张同批对照表。",
        },
    }

    doc["next"] = ("F0–F6 执行完成、验收未完成，判决 REWORK；口径修订见 F6_FINAL_VERDICT_20261029_REV2.md，"
                   "收口 B/C/D 实测见 F_BCD_CLOSEOUT_ADDENDUM_20261029.md。"
                   "B 已把唯一未测核心补上：值确已离开实际出站窗口时，on 11/12 用到、off 0/12，"
                   "并暴露 1 格投影内跨行接日期（KP-C2 on:r1，含素材缺陷 E-MAT-B）。"
                   "C 把候选真实短板收敛为单一族（无据确定化 3 位点）并冻结了唯一通用假设 assertion_tiering，"
                   "实现与对照批未开工。D 材料已出、尚无人评，W 保持未知。"
                   "身份防漂移：tools/check_identity_drift.py（两个身份当前 identical=true）。")
    doc["next_actions"] = [
        "C-实施（下一个最高价值项）：按 F_C_HYPOTHESIS_FREEZE_20261029 从 e3 另建新身份"
        "（只改行动块分层文本），先 tools/check_identity_drift.py 复算并另写冻结件，"
        "再登记新有限授权，跑 3 领域各 ≥4 项正反对照（≤24 生成）；"
        "护栏任一回退即判假设被否证，不得用全量 hedge 压错误率。",
        "D-人工评分：把 F_D_HUMAN_PAIRS_WORKSHEET.md 交用户/导师逐对标注，回收后与自动分对表；"
        "未回收前 W=未知，禁止 AI 代投或用自动分冒充。",
        "B-扩展（可选，价值次于 C-实施）：若要把 11/12 的单批小样本做成结论，需新授权跑更多探针"
        "（同夹具、同 off=删三段投影口径），并补 time-window 更长的探针验证 Update 的复现性 1/2。",
        "素材版本化：E-MAT-B（K02 时间戳与内容日期冲突）与 4 个卡面冲突按题库版本化改，"
        "不为改素材重跑既有生成；尺子提案 RULER-C-01 待用户裁定，生效走离线重评。",
        "提交口径：今天可交“固定基础模型下的聊天 Harness 重构原型 + 开发集对照实验”"
        "（题目建议《固定基础模型下的聊天Harness重构原型及开发集对照实验》），"
        "B/C/D 作带时间戳的补充附录；禁止复活已关闭账本、禁止为 e3 全量重抽、"
        "工程整洁不排在实测前面。",
    ]

    json.dump(doc, open(BASE, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("UPDATED", os.path.relpath(BASE, ROOT).replace("\\", "/"))
    print("phases:", list(doc["phases"]))
    print("errata items:", len(doc["errata"]["items_20261029_phases"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
