# 现有成果复用清单

此清单用于核销新任务，不替代当前执行书，不要求重新跑历史批次。

| 组件 | 现有入口 | 判定 | 本次应做 | 不应再做 |
|---|---|---|---|---|
| 原版范围/移植 | candidates/reference_product_v1/mechanism/reference_chain.py; refs/arena_v1/BASELINE_PARITY.md | 已有，应继承 | 按已声明范围比较；未覆盖能力单列 | 不重新移植同一前台主链 |
| 模型网关 | arena/lb_gateway.py | 公共可复用 | 继续用现有网关和真实attempt日志 | 不另造计费/派发平台 |
| 授权预算 | arena/isolation/authorization_budget.py | 已实现并有事故修复 | 新scope登记与旧物理更正并列 | 不复活关闭单、不重做27/18修复 |
| 固定故事执行 | tools/exam_interleave_plan.py; arena/isolation/trusted_controller.py | 今天已复用 | 同一入口适配新题卡/候选 | 不因为换窗口再写执行器 |
| 公共输出契约 | arena/protocol_public.py; arena/lb_scorer.py | 已有语义权威 | 实际入口复用或等价调用 | 不再增加第三份近似动作协议 |
| 消费归一保全 | tools/normalize_reply_contract.py | 旧保全可复用 | 保全冲突和超额方向的完整原文 | 不重新截断已解决的审计信息 |
| 新R3闭合修复 | tools/output_repair.py | 有新功能但与旧R1/R2重叠 | 仅增量扩展；当前只是离线工具 | 不宣称正式消费链已全面恢复 |
| 批注/结果绑定 | tools/exam_layered_score.py::_match_annotation; tools/build_review_worksheet.py | 旧保障已实现，新汇总未继承 | 离线绑定现有答案和批注，再派生分表 | 不为修绑定重新跑生成 |
| 九维分表 | tools/aggregate_s1_scorecard.py | 新统计有价值，身份/绑定丢失 | 接回旧真实索引与绑定；修48位模板 | 不把旧case_id分数静默带到新答案 |
| 证据状态结构 | candidates/v6_integrated/mechanism/mechanism.py | 已有结构，W04重复派工被拒 | 研究语义支持、主体和真实使用 | 不为满足任务书复制evidence_state_r1 |
| 行动父子候选 | candidates/action_policy_r1; candidates/action_policy_r2; candidates/action_policy_r3 | 实际继承且有机制差分 | 保留现有闭包与真实试验 | 不将继承文件相同计作从零重做 |
| Stage1/2开发故事 | benchmark/stories_v2/stage1; benchmark/stories_v2/stage2 | 已有可用材料 | 按覆盖缺口、暴露与已跑状态选择 | 不重新写同样一批故事或假称独立 |
| 当前已付费答卷 | refs/experiment_protocol_20261023/s1_action_live_20261028 | S1/S2/S2R/S3/D1已发生 | 回收、绑定、评审与必要新版本复验 | 不因新审计把原始数据归零 |
| 慢模拟器研究 | logs/p07_w09_20261028_a; W09_DISPATCH_INCIDENT_20261028.json | 重复补齐和覆盖事故已有证据 | 独立辅助epoch，不占固定故事关键路径 | 不继续r6/r7补到成功 |
| 当前任务权威 | WORKER_CURRENT_RUNBOOK.json | 已有唯一指针 | 保持一个当前入口，旧书只作沿革 | 不再为审计新建V5总任务书 |
