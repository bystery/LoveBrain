# F4 组件消融报告（2026-10-29）

## 1. 目的与设计

F4 回答一个问题：最终候选 `action_policy_r4_guard_final` 里，被声明的两个组件各自是否带来可测增益。

- 行动层（action）：probe 取自 N01/N02 第 13 轮（T12 checkpoint 续跑后新增一题），覆盖 positive / ambiguous / busy 三种信号，共 6 对。
- 知识投影（knowledge）：probe 取自 K01/K02 第 25 轮（T24 checkpoint 续跑后新增一题），共 6 对。

每一对都是同题同人设同历史，只差一处：on 臂走真实 `iso_run --resume`（完整候选，含组件块）；off 臂直接调同一网关（dsf / 0.7 / 17770/v1），system 使用 on 臂同一份快照删去声明的组件块，当前用户消息逐字相同，输出过同一个 `repair_and_guard`。两组都不经过任何额外改写。

行动层删除的块是【本轮可用动作…】（956–1037 字符）；知识层删除【相关已确认事实】【暂定事项】【最近状态变更】三段（约 880–960 字符）。每次删除都记录了块首标记、字符数与 sha256（见 `F4_PRIVATE_BINDINGS.json` 的 `removed_blocks`、`on_prompt_sha256`、`off_prompt_sha256`）。

## 2. 执行与完整性

行动层 on 臂的 checkpoint 显示 `turns_done=13`、`candidate=action_policy_r4_guard_final`、`episode_id=N01/N02`；知识层 on 臂显示 `turns_done=25`。来源 out/run 树分别来自 F3 最终批的 T12/T24 checkpoint 副本；on 臂的 `_control/run_identity.json` 写入了独立 `fork_run_id` 与 `parent_run_id`，使续跑回合的 attempt id 与源批不冲突（首轮 8 个 on 单元因共用源 run_id 被网关拒绝派发，未产生模型调用、未计费，重试后全部 8 个成功）。

`F4_AUTHORIZATION_20261029.json` 账本：24 次 `attempt_started` / 24 次 `attempt_completed`，另有 8 次 `dispatch_refused_duplicate`（零调用）；`F4_KNOWFIX_AUTHORIZATION_20261029.json` 12/12；`F4_JUDGE_AUTHORIZATION_20261029.json` 12/12；三本账均已 `authorization_closed`。F4 实际模型调用合计 48 次（生成 36 + 盲评 12）。

## 3. 行动层结果（有效）

6 对全部完成且 prompt 差异仅限声明块，盲评结果：

| 指标 | 数值 |
|---|---|
| on 胜 | 1（N01_A1_positive） |
| off 胜 | 1（N01_A3_busy） |
| 平局 | 4 |
| major_errors | 0（双方） |
| card_conflict | 0 |

1 比 1、4 平，样本量 6 对，这个分布不能支持“组件有稳定收益”，也不能支持“组件有害”。正确表述是：在本批探针上未见可测增益；不据此删除组件。

## 4. 知识层结果（无效消融）

首轮知识探针的问句直接写了答案数字（例如“13 号上午走”），off 臂可以照抄问句，属于测量污染。已用 `AUTH-F4-KNOWFIX` 重写 6 个问句（不含答案数字）并重跑 12 次，但检查 off 臂 prompt 后发现：近期原文历史仍完整保留答案数字，6 个探针里 5 个的 off 答案数字都能在 off 历史中原样找到（K01_P1 的 13/16/17、K01_P2 的 10/8/9、K01_P3 的 1/13、K02_P2 的 5/8、K02_P3 的 11/9），只有 K02_P1 的 81000 不在历史中。也就是说，删掉【相关已确认事实】等投影段并不能隔离记忆通道，off 仍可从原文历史得到同一事实。

因此知识层 6 对不构成有效消融，不进入收益结论。盲评仅作探索记录：off 胜 1（K01_P1，off 答案引用 13/16/17 且 goal/factuality/memory_use 均 3）、平 5，同样没有方向性结论。

## 5. 结论

行动层消融有效但不确定：on 1 / off 1 / tie 4，不宣称组件增益，也不建议删除。知识层消融无效：操作无法隔离记忆投影，不能据此评价知识组件；若要得到有效结论，需要构造“答案只存在于投影段、不在原文历史”的探针，这是新的测量设计，不在本批授权内。

证据路径：`F4_ANALYSIS.json`、`F4_PRIVATE_BINDINGS.json`、`logs/f4_component_20261029*/`、`logs/f4_component_knowfix_20261029/`、`logs/f4_judge_20261029_120529/F4_RATINGS.jsonl`。
