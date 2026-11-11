# Phase 1A 第八波交付报告（第七波判决 §9-1~4 离线收口）

> 日期：2026-10-19 深夜 ｜ 撰写：Worker ｜ 状态：等独立复审
> 本报告只写真实发生过的事；凡未做的，明确写"未做"。**未做任何真实模型调用；未动历史日志与封存内容。**

---

## 〇、三句话摘要

1. 第七波判决 §9 执行令 **1–4 项全部离线落地**，六套自带检查 + 时间校验器全 PASS（本报告 §二逐项对判）。
2. 判决点名的三类反例全部实测复现并拦截：**嵌套坏字段不再 10 分**（评分与写回共用同一校验器）、**六轮旧冻结源实测拒绝**（rejection.json 落盘）、**R2 第 8 组改回当时真实上下文**（"B 的高配"）。
3. §9-5 有限真实复跑**仍暂缓**：16 请求方案（`--pairs 4 --order AB,BA,BA,AB`）已实现并离线验证，等你授权。

## 一、判决 §9 逐项落点

### 1. 补输出结构与写回边界（判决 §3 / §5）

- **lb_scorer v0.9**：新增 `output_structure_errors` / `analysis_type_errors`（唯一权威嵌套校验器）——directions 各项、response 已声明风格键（及未声明非空文本字段）、analysis.scene_facts（dict；text=字符串、source_ids=字符串列表）、ongoing（item/status=字符串）、topic_status/topic_label。坏嵌套 → `veto=json_schema_broken`（evidence 标 `nested_type_violation`）。
- **写回共用同一份合格结构**：基线机制 `update_state` v0.2 与 β_single 写回都先过 `analysis_type_errors`——不合格不消费、不提交非法记忆、不抛异常不中止回放；跳过原因留证（基线 `state_after.json` 的 `last_writeback_skipped`、β 的 mechanism_notes `writeback_skipped`）。
- **生命周期时钟明确规定**：`last_clock` 每轮照常前进（生命周期状态），analysis 派生记忆内容（topic/facts/ongoing）只在合格时提交。
- **真正机制错误仍独立记录**：runner v1 的 try/except 与 `mechanism_error` 字段保留，未用宽泛吞异常掩盖实现错误。
- **测试**（`tools/test_output_structure.py`，全部离线）：scene_facts[0].text=对象 → 格式失败；directions 含对象 → 格式失败；基线/β 两轮回放——t01 格式失败、无非法记忆、t02 照常执行、合法正例照常写回（修一处不破一处）。

### 2. 冻结源防错 + 新后测身份（判决 §4）

- **冻结源校验**（`_validate_freeze_source`）：`checkpoint.json` 存在且 `turns_done == 4`（本实验规定冻结阶段）、episode_id/candidate 与本实验一致、前史 t1–t4 manifest 齐全；任一不满足 → **在任何真实调用前**拒绝，`rejection.json` 落盘。**实测**：用六轮旧 `A_full` 目录作 `--freeze-from` → `turns_done=6 ≠ 4` 拒绝（logs/l3b_fork/l3bfork_20261019_233447_pid844/rejection.json）。
- **版本锚如实声明**：复用旧前史时记录其 `episode_manifest.integrity`（旧代码散列），并明确"前史执行于旧代码版本，与本 fork 评价代码散列不同属预期，不冒称同版本"。
- **后测身份锁定**：分支 runner 以 `--run-id <fork_run_id>/<branch>` 绑定本次实验；汇总前逐节点验证 manifest.run_id 属本次 + gateway attempt 事件（started/completed 配对，每分支 4 条）存在——复制来的旧后测无法通过（旧产物 run_id 不匹配）。
- **--memory-off 硬检查**：runner 对未实现 `set_memory_access` 的候选明确拒绝（实测：baseline → RuntimeError "拒绝静默降级为全记忆运行"）。
- **交错编排就绪**：`--pairs N --order AB,BA,...`（每对从同一冻结点新目录恢复）。实测 `--pairs 2 --order AB,BA`：p1 A→B、p2 **B→A** 执行次序正确。16 请求方案 = `--pairs 4 --order AB,BA,BA,AB`，**未跑真实对**。

### 3. 生成源 + 历史展示 + 输入 schema（判决 §5 / §6）

- **生成源已修**：`build_pilot_json.py` 三处旧文案（t08"星耀12Pro"、t20 删"不闹了"、t22 自发下单）改正后**原地再生成，产物与交付 JSON 语义一致（True/True）**——"单一数据源"名实相符。
- **R2 历史绑定**：新建运行时冻结题面快照 `benchmark/episodes/archive/PILOT_L1_v1__pilot_r3.json`（sha256=7c26566239f64f6c…，t08 为当时真实文案"B 的高配"）；生成器 v4 改用快照渲染上下文，**内置绑定自检**（快照每条消息必须命中该轮当时 `calls.user`，实测 12 节点 × 2 候选全命中，未命中即拒绝生成）；材料头标明 run 目录 + 冻结题面 sha + 评分版本（v0.9）+ "新题面行为评价须等新输出"；新题面审核走 `render_pilot_views` 双视图（已重生成）。旧 24 条输出未回写、未跨版本归因。
- **输入 schema 补全**：`_validate_input_types` 入口完整校验——messages 必须对象列表（字符串/列表内字符串拒绝）、intent 仅字符串（legacy，版本协议明确保留）或对象（列表拒绝）、clock/note 字符串、may_decline 必须布尔、kb 对象。测试 7a–7f 五反例全拦截 + legacy 字符串 intent 对照不误杀 + 正常中文不误杀。

### 4. 自包含审稿包（判决 §7 / §8）

- **manifest 无自引用**：新流程 = 包内 `REVIEW_PACKAGE_MANIFEST.json` 只列 payload（生成于打包前）→ sidecar（refs/，在包外）记录整包完整 sha256。内置自检断言"包内 manifest 的 package 名 = 本次包名"（旧 manifest 混入会直接失败）。逐文件 sha256 升完整 64 位。
- **补两个依赖**：`benchmark/rubric.md`（评分权威/开放契约）+ `logs/phase05/judge_calib.json`（首轮校准原始结果）已进白名单。
- **漏网项同步**：L3 来源包 t11 标题 21:30→21:31（与末条/台词稿一致）；总报告第 99 行"温度改变行为"→"**相同名义温度条件下观察到判决翻转**（同温度重复不一致不能证明改变温度的因果效果）"，全文该表述零残留。
- **状态表**：总报告顶部"当前状态（唯一口径）"按本判决更新——评分权威/指定反例标"已签收"，schema 标"补全"，复跑标"暂缓"。
- **排除断言维持 PASS**：dataset/_archive_v1/pycache/.git 零成员；封存 B 线与用户 dataset 在白名单机制下天然不在包内。

## 二、本轮自检结果（全部离线，mock 适配器）

| 检查 | 结果 |
|---|---|
| test_output_structure（新） | PASS：嵌套反例格式失败+写回拦截+第二轮可审计+时钟前进+扩展契约正例+未声明拒绝 |
| test_no_reply_protocol | PASS（回归无损） |
| test_input_boundary（扩 7a–7f） | PASS：容器错型入口拒绝、legacy intent 不误杀 |
| test_scoring_authority | PASS（回归无损） |
| test_memory_off | PASS（回归无损） |
| test_failure_persistence | PASS（回归无损） |
| validate_pilot_timeline --selftest + 4 episodes | PASS |
| build_pilot_json 再生成语义一致 | PASS（L1/L2 均 True） |
| fork 冻结源校验（合法/六轮旧源/重复 run_id/无 memory-off 能力） | 四态全符合预期 |
| fork 交错对次序（--pairs 2 --order AB,BA） | PASS（p2 B→A） |
| 审稿包自检 | PASS（1113 文件/3.4MB；包内 manifest 属本次包） |

## 三、诚实清单：未做 / 待授权

- **§9-5 有限真实复跑未执行**（等授权）：方案为复用合格四轮冻结点 + `--pairs 4 --order AB,BA,BA,AB` = 16 次后测请求；语义判据（过敏使用/旧边界/无关回提/推荐正文成功）待在授权时随执行记录落盘；不做显著收益/长期记忆可靠性/真人感通过的证明。
- 旧 24 条输出的 v0.9 重评未跑（判决已独立重评 11/12、均分 9.1667；如需 worker 侧复算可在授权后补，无新调用）。
- 70 条编号填充仍是机械压力样例，未升级自然长期聊天。
- 沙箱/基线保真/Judge 独立验证集/用户可发送性四门禁继续冻结。
