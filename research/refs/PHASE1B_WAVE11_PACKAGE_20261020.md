# L3B Wave11 一包收口报告（v9 复核执行令 · 零真实调用）

日期：2026-10-20。依据：v9 独立复核 §6 一次性执行令——只做三组修正，连续做完，
自证通过后一次性请求真实调用授权。本报告合并收口，不重复 v9/v10 已关闭项。

## 0. 结论

三组收口全部完成并回归验证；**13 套离线回归全 PASS**；66 槽执行器已在同路径
mock 下完整演练（干净 66 槽 + 守卫反例 + 失败停批/显式恢复）。**本批零真实调用**；
66 次调用授权清单见 §4（方案 v2 已冻结散列）。

## 1. 收口一：表达注入 + 统一审计 + 反误杀（复核 §3）

| 项 | 修复 | 验证 |
|---|---|---|
| 表达块重复注入 | 全局永久替换 → **作用域限定注入**：run_turn 调用期间临时替换 `lb_assembly.build_reply_prompts`、finally 恢复；`_expr_orig` 防御捕获真身 | 同进程 run_episode 两次，每请求表达块恰 1 份；父版本臂零污染；全局无永久副作用（test_expr_candidate A5/F 组） |
| 运行时审计缺 F4 | `_load_families()` 补 F4（模式+标签与 tools/expr_audit.py 同源）；同源断言升级为模式+标签全量比对 | 复核问题句"她之前不是自己把猫关门外了…"运行时命中 F4（旧事件回指—主语需人工核对） |
| 三臂同口径 | A1/A2 无 expr_trace 不按零缺陷计——主表以统一审计器四族计数+人工判读列呈现（方案 v2 §4）；检测器计数不充当真人感结论 | 方案 v2 判读表修订 |
| 建议句误杀 | expression.md 规则一改为**收信人一致**判据：允许例"你就跟她说你对猫毛过敏，不方便让猫进屋"（她问怎么应对室友时=自然建议）；反例"她问要不要拦，你就回复她……"（收信视角错位）；删除"我陪你搬"无条件正例 | expr_audit F1 首模式标签改"建议或指令句式—收信人一致性需人工判读"（不凭句式定罪）；A4 断言允许例入提示 |

A3 依赖散列：A3 槽账逐槽记录 `dep_A2_mechanism_sha256`；A2/A3 机制与
expression.md 执行前快照归档入批次 `_frozen/`（复核 §3.1 依赖冻结要求）。

## 2. 收口二：剧本与测量口径（复核 §4）

- **NCB 旅程唯一时间线**（§4.1）：周四 20:25 购票报 G7812（周五湖州上车）→
  周四 21:25 改签 D3145（均在出发前）→ 周五 18:52 正点到合肥 + 定胜糕两盒
  （上车前买好）一盒给 ME → 周六婚礼、晚间"你那盒别放着 早点吃"（写清谁和
  哪一盒）→ 周日返程收尾。仍 5 请求位。`_journey_timeline` 字段固定参照。
- **测评注释出可见输入**（§4.2）：NCA t05 intent 改为"先回组会时间的事 再帮她
  想想新组见面准备什么"，观测说明移入隐藏 meta.obs（runner 白名单不透传）；
  t02 删除"意图较上一轮变化"。防泄漏词表补"观测点/诊断观测/意图较上一轮"，
  校验器与 mock 泄漏扫描同步加固。
- **硬关键词→软证据**（§4.3）：NCA t01/t02/t03/t04 与 NCB t3/t4 的开放闲聊承接
  全部降级 `soft_keywords_any`（不计分仅证据）；保留客观契约——NCB 车次号
  verbatim（G7812/D3145）、NCA t05 组会时间（initial_kb 可答）。"照片发来瞅瞅"
  类自然承接不再因未复述实体被扣分。
- **检索观测位**（§4.4）：NCA t05 明确为"相关事实不在当前窗口、intent/kb 不泄露"
  的库召回观测节点（换项目组事实仅存库存），不扩题库。
- **主表多列口径**：方案 v2 §4 固化——槽位成功率/首选可发送性/任一可用选项/
  事实视角错误(人工判读)/非空交付数/json_parse_error/合法收尾 分列；A3 增多
  null 不构成"缺陷少"表面优势。

## 3. 收口三：66 槽执行器 + 同路径 dry-run（复核 §5）

`tools/l3b_three_arm_executor.py`（回归 `tools/test_three_arm_executor.py`）：

1. **同路径**：每槽经 `lb_runner.run_episode` 真实 runner/gateway/checkpoint/
   sandbox 路径执行恰好一轮（stop_after/resume 语义），非 mock 直调机制。
2. **额度先占后耗**：槽位先登记 occupied 再生成，终态 success/failed 都消耗；
   第 67 次拒绝（CAP_EXCEEDED 反例过）、重复占槽拒绝、未知臂拒绝。
3. **槽位轮换**：组序 A1A2A3→A2A3A1→A3A1A2（平衡顺序，实际顺序逐槽入账；
   只宣称缩短三臂时间间隔，不宣称消除时间漂移）；arm 内 turn 次序各自有序。
4. **失败策略**：坏 JSON=成功终态（原始输出保留、0 分）后继续；传输失败→槽
   failed 留证（error.json）+ 立即停批；**不得自动重跑**——重跑被
   BLOCKED_BY_FAILED_SLOT 阻断，须操作者 `--resolve-failed` 显式处置（作废
   留证不重跑，批次可继续）。演练全程已验证。
5. **散列保护**：执行前核对方案 v2 十项冻结散列（不一致即停）；批次配置固化
   adapter/模型标签/temperature/timeout/无重试政策（后端版本无法验证时只记
   标签，不落密钥）。
6. **注入断言**：逐槽检查 calls.json system——A3 表达块恰 1、A1/A2 恰 0，
   违规=槽 failed 终态+停批。
7. **汇总**：summary.json 按方案 §4 多列口径自动分臂统计。

dry-run 账（零真实调用，mock）：`logs/l3b_66_dryrun_v2/`（66/66 终态配对、
轮换顺序入账、expr 断言零违规、A3 依赖散列逐槽入账、依赖快照归档）+
`logs/l3b_66_drill_v2/`（传输失败注入停批→阻断→显式恢复→完成，66 额度消耗：
65 success + 1 failed-abandoned）。mock 只证明执行链路，不证明生成质量。

## 4. 66 次真实调用授权清单（一次性提交）

- 方案冻结：`refs/L3B_THREE_ARM_PLAN_20261020.md`（v2，散列自锁）。
- 规模：11 请求位 × 3 臂 × 2 重复 = **66 次，硬上限，无重试额度**。
- 执行：`tools/l3b_three_arm_executor.py --adapter <真实适配器>`（配置执行前
  固化入批次账）；坏 JSON 继续、传输失败停批待操作者决定。
- 冻结散列（sha256 前缀）：A1 机制 fc8fdd68… / A2 机制 50c095eb… /
  A3 机制 31a1dd72… + expression.md 70712802… / scorer 678c4e19… /
  assembly a671c23b… / runner 89568033… / gateway b0587e6a… /
  NCA 018826ba… / NCB 8fbb29b5…。
- 授权后交付：原始回复 + §4 多列主表 + 逐臂逐重复事件配对后测 +
  "哪类可发送、哪类仍失败"判读（含负结果）。

## 5. 回归与包

- 13 套全 PASS：retr_idf / trace_robustness / output_structure / no_reply /
  input_boundary / scoring_authority / memory_off / failure_persistence /
  posttest_event_pairs / timeline / expr_candidate / nca_ncb_episodes /
  three_arm_executor。
- 成果包：`lovebrain_review_v10_20261020.zip`（sidecar sha256 见
  refs/REVIEW_PACKAGE_MANIFEST.json；v10 新增 executor/回归/66 槽 dry-run 账/
  修订 episodes/修订 expression.md 与 expr_audit）。
- 正式晋级继续冻结；已登记限制（单字召回、状态失效、JSON 结构保障）不因本批
  宣称解决。
