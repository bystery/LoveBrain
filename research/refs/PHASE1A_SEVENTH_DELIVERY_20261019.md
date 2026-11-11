# Phase 1A 第七波交付说明（2026-10-19，对应第六波判决 §12 执行令）

> 状态：执行令第 1–4 项全部离线完成；第 5 项（有限模型复跑）等授权；四类门禁维持冻结。

## 一、§12-1 评分权威与类型安全（P0）

| 判决反例 | 修复 | 验证 |
|---|---|---|
| 候选追加虚构消息引用 m3 → 10 分 | runner 在候选调用前固定评估侧原题 deepcopy（turn_eval）；id_map/时钟/媒体/评分全部只读快照；v1 assembled.id_map 不再作评分权威 | `tools/test_scoring_authority.py`：追加/删除/改角色/伪造 id_map 四类篡改端到端全部 veto=illegal_source_id:m3、score=0，且与原题权威评分逐位一致；干净来源对照组不误杀 |
| action=reply+decline_reason+空结构 → 10 分 | v0.8 显式动作优先：冲突即 veto=action_conflict（先于正文检查，正文为空也不能转换动作） | 测试 N4/N6 |
| no_reply + response/analysis 字符串 → AttributeError | 类型校验前置：坏类型 → veto=json_schema_broken（evidence 记字段名），评分进程不崩溃；另加 run_case_scoring_safe 兜底（意外异常记 scorer_crash，episode 可继续审计） | 测试 N7/N8 |
| 顶层 text 非空 → 10 分 | 未知字段政策写入契约：协议顶层键=action/response/directions/analysis/decline_reason/reason；未知键非空字符串 → veto=unknown_payload_field；其余登记 schema_anomalies | 测试 N9 |
| reason 为对象 → 10 分 | reason 非字符串 = 类型违规 → json_schema_broken | 测试 N10 |
| 合法 no_reply+字符串 reason | 保留正例满分 | 测试 N11 |

## 二、§12-2 输入入口统一 schema（P1）

- sanitize_turn：message.role 必须 HER/ME，text/ts/image 必须字符串；intent.enabled 布尔、text/status/expiry/now 字符串——**对象出现在标量位 = 入口 ValueError 拒绝**（判决两反例）；允许值全部深拷贝（含 messages 逐字段）。
- 正常中文自由文本零影响（只查类型不查内容）。
- 验证：test_input_boundary 扩 6a/6b（对象型标量拒绝）+ 6c（正常文本对照组）。

## 三、§12-3 审计生命周期（P1）

1. **run_case/episode 共用异常持久化**：run_case 异常路径落 calls.json+error.json+gateway_events（测试 V5）。
2. **attempt_started/completed/failed 三态事件**：HTTP 发起前先记 started（带 run_id/case_id/pid/adapter/model/temperature）；写日志失败不再静默（event_log_errors 进 manifest.audit_errors）（测试 V1/V6）。
3. **fork 编排器 v3**：每次运行唯一 run_id 子目录（`logs/l3b_fork/l3bfork_<ts>_pid<n>/`），**目录存在即拒绝覆盖**；全局 INDEX.jsonl 追加式（旧 fork_log 同文件追加导致覆盖证据的根因已废）；`--freeze-from` 复用前史冻结点（复用不重烧）；subprocess.Popen 记录**实际子进程 pid**（fork_log：branch_run_*_start/_child_pid/_child_exit 三事件）。
4. **恢复即刻清单**：runner --resume 时写 sandbox_tree_at_restore.json + restore_completed 事件（含 checkpoint sha256、pid）——不再是后测结束后的补记。
5. **历史缺账如实声明**：10-18 13:46 被覆盖的前史 4 条原始 HTTP 记录无法恢复，不补造（总报告当前状态表）。

离线 mock 复跑（--freeze-from 复用既有冻结点）：A/B 分支、resume=4、prompt 记忆区差异、子 pid 证据全部落盘（logs/l3b_fork/l3bfork_20261019_133952_pid2632/）。

## 四、§12-4 文档与审稿包

| 项 | 修复 |
|---|---|
| R2 图片链接 | 改文档相对路径 `../benchmark/media/pilot/...`；生成器自动校验按文档位置解析后文件存在（防死链）；重生成 12 组 |
| B 视图媒体明细 | 媒体登记（input.media.images）+ 消息图片引用双向对照展示（3 处） |
| L1 t08 | "B 的高配"→"星耀12Pro"（episode JSON + 台词稿同步，不补虚构历史） |
| L2 t20 | 删"哈哈哈哈不闹了"（隔 2.4h 紧接打闹感），直接开买鞋话题（判决建议原话） |
| L2 t22 | "行 那我下单了"→"鞋看着不错 我下单了"（删无承接的"行"，自发口径） |
| L3 全局声明 | 与来源包对齐：S2=最后已知计划（不认定"刚开课"）、S3 精确日期为评估侧参考不进候选可见文本 |
| L3 t11/t12 时间 | 标题统一=请求时间（21:31/21:42），台词稿与来源包一致 |
| L3 黔菜 | 删"前两次你说的那家"（无登记来源，两文档同步） |
| Judge 因果句 | 总报告"温度改变行为"→"相同名义温度条件下观察到判决翻转"；lb_judge_2x2.py 头注释同口径（参数接受度不能由一次一致性判定） |
| 审稿包 | 新 `tools/make_review_package.py`：白名单（arena/candidates/tools/refs/benchmark 子集/关键日志）+ **排除断言 PASS**（dataset/_archive_v1 零成员）+ 逐文件 sha256 manifest（refs/REVIEW_PACKAGE_MANIFEST.json）；**v4 全量包声明不外发，仅留本地** |
| 状态唯一口径 | 总报告顶部加"当前状态（唯一口径）"表 + 时间界限 |

JSON 改动后校验：validate_pilot_timeline L1/L2 全集 PASS。

## 五、§12-5 有限模型诊断（等授权，未执行）

前置已完成。授权后执行方案：复用现有冻结点（--freeze-from，不重烧前史）；固定输入/代码/资产散列与判据；小规模交错、成对重复比较 A/B；逐次完整留证；过敏/边界/相关性/推荐正文/其他选项分开标。

## 六、全量自检（本波实测，全 PASS）

| 自检 | 结果 |
|---|---|
| test_scoring_authority（新增：四类篡改+对照） | PASS |
| test_no_reply_protocol（N1–N11） | PASS |
| test_input_boundary（+对象型标量 6a/6b/6c） | PASS |
| test_failure_persistence（V1–V6，+run_case 路径/attempt 三态） | PASS |
| test_memory_off | PASS |
| validate_pilot_timeline --selftest + L1/L2/L3B | PASS |
| 审稿包排除断言 | PASS |
| fork v3 mock 复跑（子 pid/restore 即刻清单/INDEX） | PASS |
