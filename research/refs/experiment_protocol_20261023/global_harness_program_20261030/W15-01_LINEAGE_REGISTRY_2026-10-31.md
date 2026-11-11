# W15-01 全参赛身份谱系与同量尺成绩册（2026-10-31）

同量尺：dsf@127.0.0.1:17770/v1 temp0.7；九维评审 + severe B3 队列；program_mean10。

| 身份 | 角色 | 状态 | 关键成绩 | 成本画像 | 批次 |
|---|---|---|---|---|---|
| reference_product_v1 | B0 基线（file-based memory） | baseline-active | fs1 program_mean10=9.79；longprobe 九维见各批评审 | fs1 prompt 355,917 字符/48 轮 | fs1 双臂 parent；longprobe 各批 parent |
| v0_minimal_events | 最小事件窗竞争者（Bmin） | frozen-20261031 | fs1 program_mean10=9.79（配对 1胜1负46平）；长程未测（批B 计划） | fs1 prompt 129,629 字符/48 轮（−63.6% vs B0） | fs1_b_live/_live2 candidate |
| action_policy_r4_guard_e3 | 前任冠军（证据记忆竞争者） | lineage-kept | 九维 vs B0 dims-dominant；severe 3/96（修正映射后）；编造族 3.8% | fs1 记忆介质 ~831 字/轮（窗+投影） | fs1_a candidate；C1/D1/E1 parent；F2/F3 parent |
| action_policy_r5_tier | challenger（assertion tiering v1） | archived-not-eliminated | C1 全 9 维压制 B0 真实；D1 vs E3 全平 severe 2/0 | 同 E3 | C1（vs B0） |
| action_policy_r6_tier2 | challenger（tiering v2） | archived-not-eliminated | 九维微幅领先（goal+0.417/init+0.404）severe 仍 2/0 | 同 E3 | E1（vs E3） |
| action_policy_r7_supersede | 现役冠军 | CHAMPION | 九维 7 正 2 微负（F2）/边际可接受（F3 interact −0.208 未破线） | 同 E3（+作废 pass 零额外调用） | F2/F3 candidate（severe 0/96 vs E3 3/96，2/2 批复现）；G1 parent（进行中） |
| action_policy_r9_slim | 共役冠军（瘦体优先，G1 升格） | frozen-screening | 重放范围证明 PASS（状态零漂移/锚全过/−235 字每轮）；行为待 G1 | r7 −235 字/轮 | G1 candidate（进行中） |
| e3_memdump | 测量身份（不参赛） | instrument | F1 fidelity capture 30/31=96.8% | 同 E3 | F1 candidate（96/96） |
| r10_compose | 实验接口身份（不参赛） | experiment-interface | 三门矩阵 goal−83/action−1624/source−131 字每轮 | 按门 | 仅重放（W09-04_COMPOSE_REPLAY） |

各身份当前目录双文件 hash（params+mechanism，漂移检测基线）见 JSON。
身份断言冲突处理：G1 冻结身份（17a26224/2099c12f）为 identity_closure 算法现值；历史记录值（17fd2160=E3、0e2d273a=r7 备案）为各批 prepare 时点值——同算法同时点一致，跨时点差异属闭包内容演进，注册表按批记录时点值。
