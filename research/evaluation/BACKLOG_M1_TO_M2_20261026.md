# BACKLOG — M1→M2 期间挂账（低频边角与已知缺陷，不得替代主线）

生成：2026-10-26 14:2x，主线轨（C4 机制筛选收口后）。原则：AGENTS「不为凑时长增活；
低频边角合并 backlog，不得替代下一次真实候选处置」。

## A. M2 派发前必须做（真前置）

1. **`first_usable` 正式接线**：C4 的 128 次里 `first_usable_ms` 仍 128/128 null——普通链路没挂消费回调
   （只有 D5 那 8 次专用链路测到过）。接点=正式可信消费入口，M2 两臂共同测；历史 null 不回填。
   证据：`evaluation/DELIVERY_AND_COST_COLUMNS_C4_SWEEPS_FINAL.json` 的 `wait_by_arm`。
2. **C2 断言边界修订**（故障族 F2）：无出处的"我方经历/具体日期/已订已买"一律改成提议或疑问。
   实锤两例：E007_T03 备选卡「我上次听那支正好是加班到十点」、E012_T03 首选「那家我也一直想再去一次」。
   判据=修订后 F2 命中数（分母 32 全位点 + 7 goal-live），原文引用。
3. **C3 facts 通道扩容**（故障族 F3）：现在只接 `initial_kb.ongoing_plan`+每轮报告，而题面事实在
   `persona.me/her`、`initial_kb.warmth`（E008_T03 它是唯一没用对"小林=牵线人"这条库内事实的臂）。
   扩容后须带 src/ts；判据="该用时是否用对"的位点集 + 长故事窗外召回。
4. **B2 新故事必须铺开三种意图条件**（仅真实聊天 / +一般目标 / +战术备注）：C4 的 goal_block
   真生效只有 **7/32（21.9%）**，E008 全 8 位点无目标 → 目标层永远在少数位点上被测试。
   证据：`evaluation/C4_INTENT_LAYER_COVERAGE_V1.json`。

## B. 工程债（可并行，不阻塞）

5. `tools/test_verify_strict_negative.py` 的桩修好后（原来不接 `env=` 抛 TypeError →
   整组负向门禁静默不跑），现出 1 条真实遗留断言失败：`P1 完整合规日志未通过（误杀）`，
   出现在 G08/G09 隔离矩阵家族——该族按宪法 v1.3④ 已撤回为"非线性前置"，但要修不是删。
6. `tools/test_parent_child_executor.py`：52 槽方案冻结的是旧 phase1/2/3 候选与旧
   lb_scorer/lb_runner/lb_gateway 身份，现按 `test_three_arm_executor.py` 同构改判
   **SKIPPED (SEALED)** 并指明现行等价守卫（verify_frozen_identity + test_execute_plan +
   test_freeze_missing_guard + test_authorization_budget）。若要复活该矩阵，必须重冻成新方案、
   不许改历史冻结表。
7. `tools/exam_batch_configs/*.json`（旧 4 件）没有 `cards_path` 键 → 走默认
   `dev_pilot_v02/03_GRADING_CARDS.json`。C4 的教训是"卡族与故事族错配"会静默把卡字段填空；
   旧配置复跑前必须显式指定卡路径（`state_map_check` 抓不到这一类）。
8. `state_map_check.py` 期望：logs 子目录 188（登记 117，合法增长）；诱饵目录 31→32 已登记
   （两个 `evil_link` 是指向 `refs/arena_v1` 的 NTFS junction，`test_baseline_parity.py`
   重算时新写了可再生清单）。下次增长同样要写清来源而不是只改数字。
9. `STATE_MAP_20261025.md` §4 仍留着"AUTH-STREAM-INT 账本不存在→尚未执行任何调用"的过期内文；
   已在文件头 14:15 段显式更正，历史行不抹。

## C. 额度与资格事实（不得当成可挪用的余额）

10. 机制筛选 **128/128 用满并已 CLOSED**（不可复活、不可挪用）。M2 复验帽 384、
    冻结新题 256、互动 96+96、产品集成 16、命题 48 尚未启动。
11. 裁判阶段 172/256，**余 84 不足以做 128 对终验** → M2 要么缩小终验范围，要么并入下一授权周期；
    不许为"尺子全绿"再烧一轮。
12. 性能阶段账本 `AUTH-HARNESS-PERF-PHASE1-20261025-01` 仍 ACTIVE 4/256，scope 禁 judge；
    C4/裁判/模拟各走自己的桶，不并表。
13. Q 维（语义行动正确性）与 W 维（用户愿发）仍是 unknown：`USER_CALIBRATION_SHEET_V1.md` 16 组未回收。
