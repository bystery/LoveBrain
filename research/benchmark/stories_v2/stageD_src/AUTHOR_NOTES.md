# AUTHOR_NOTES —— D 批复验材料出题自查汇报

## 交付物（均为新建，未改动仓库任何已有文件）
- `benchmark/stories_v2/stageD_src/D01/` episode.json / reference.json / audit.md
- `benchmark/stories_v2/stageD_src/D02/` episode.json / reference.json / audit.md
- `benchmark/stories_v2/stageD_src/AUTHOR_NOTES.md`（本文件）

素材来源纪律：未阅读 `candidates/`、`logs/`、`refs/experiment_protocol_20261023/`（除本说明书）、
`benchmark/stories_v2/stage1|stage2`、`anchors_20261028` 的任何内容；未运行
`tools/validate_story_v2.py` 或 `tools/build_stage2_grading_cards.py`；无任何联网/调用模型命令。
两题材均为编造：D01 后备箱燕麦拿铁咖啡摊（出摊位置+供电+制冰机返修），
D02 离异带娃的她学 C2 + 接娃串线（驾校时段+科目二延考）。均为成年人、在职、有真实生活负担。

## §4 自检（真跑，用 D:\anaconda\python.exe 的临时脚本，脚本在 workspace 未进仓库）
说明书给的一行计数命令输出：
```
benchmark/stories_v2/stageD_src\D01\episode.json 8
benchmark/stories_v2/stageD_src\D02\episode.json 8
```
逐条自查脚本结论：`ALL CHECKS PASSED`（exit 0）。分项覆盖：
- 顶层键恰为 episode_id/persona/initial_kb/turns；persona=me/her 各一句；initial_kb 六键皆短标量。 PASS
- 每故事 turns 恰 8；case_id 连续 T01..T08；clock 单调不减；每条 message ts ≤ 本轮 clock；每轮消息 1–4 条且只有本轮新增。 PASS
- input 键仅取 messages/clock/may_decline/intent/note；gold 全为 `{}`。 PASS
- may_decline=true 恰 2 轮：D01=T06,T08；D02=T06,T08，且都是她明确收尾/喊困喊睡的局面。 PASS
- intent ≥5 轮且生命周期 ACTIVE→ADJUSTED→COMPLETED 各故事一次完整；COMPLETED 之后无再写 ACTIVE。 PASS
- 卡集合与题面 case 集合完全相等；每卡 may_decline/intent 与题面一致。 PASS
- 每条 atom 的 quote 逐字存在于所引消息/initial_kb；无引用未来轮；required_this_turn=true 的原子的来源都不是本轮。 PASS
- 各卡 protected_expressions 逐字出现在该轮 HER 消息里。 PASS
- initial_kb 原子：D01=5、D02=6（阈值 ≥4）；更早轮次原子：D01=3、D02=4（阈值 ≥3）。 PASS
- forbidden_factual_claims 非空卡数：D01=8、D02=8（阈值 ≥4）。 PASS
- applicable_dimensions 全部落在九维内且每卡 ≥3。 PASS
- audit.md 八个固定二级标题齐全（两份均通过）。 PASS

## §3 质量点自查
- 目标真在动：两故事都有一次被她的反馈改写（D01 周六→周日北门 T03；D02 周六→周日下午 T03），
  并以一次 COMPLETED 真正结束（D01 收摊办成 T08；D02 周日练车补上+娃接到 T08）。 PASS
- ≥3 轮给主动推进窗口：D01 T01/T02/T04/T07；D02 T01/T03/T04/T07；退让与不回各只占 2 个收尾轮（T06/T08）。 PASS
- ≥2 处须用早期轮次或 initial_kb 才算答对：D01 T07（600W电源+机器已延下周）、T05（作废周三取）；
  D02 T07（延考新事实+周日时段+皮卡载娃）、T04（一紧张就熄火）。 PASS
- correction 陷阱：D01 制冰机"周三能取"→T05 更正"主板烧了最快下周三"，T07 若用旧版即错；
  D02 "周三考"→T05 退考延考，T07 若仍说周三即错。 PASS
- 第三人归属陷阱：D01 供货老陈补豆(T04)、管理小王改名额；D02 教练换时段/主动加练/劝买车(T03,T04,T06,T07)。 PASS
- 时态/状态陷阱：D01 制冰机(没好/退)、借电(没成/已用)；D02 考试(约→退→延到下下周)、时段(被抢→留回)、妈妈(发烧→退烧)。 PASS
- 含糊不是同意：D01 T06"再说吧/也可能用你电源/先别抬"；D02 T06"再说吧/也可能照常去练"。 PASS
- 情绪让位不是沉默：D01 T05(位置也没了心累)、D02 T05(带娃带妈自责)，卡里均在 private_guidance 说明此时推进目标为何错。 PASS
- 不复刻旧题材、对话像微信短句：两题材非婚礼/盲盒/建筑/看展/开放麦/考证/猫手术/快走复健；句子短、口语、有省略。 PASS

## 未能自证 / 坦白项（题目弱点）
1. 语义正确性未自动核验：脚本只验证 quote 逐字存在、引用不越界等结构约束，无法判断"最合适的这一轮动作"本身对不对，
   这些是主观判分，复核者可能与我不同判（详见各自 audit.md §5）。
2. correction / 跨轮记忆依赖 harness 是否有长期记忆；若 runner 不做跨轮累积，D01_T07、D02_T07 会大面积答错，难度可能被高估。
3. D01_T07、D02_T07 的"最优解"实际有多种（确认方案 / 再问一句冲突），评分器若咬死单一动作可能误判。
4. 第三人归属陷阱（老陈、教练）单轮信号偏弱：一贯谨慎的模型可能整题都不犯错，导致该位区分度不足。
5. "情绪让位"(T05) 与"含糊"(T06) 的判定偏软，宽严不一时最容易飘。
6. 未做独立人类复核，未用真实用户材料——两故事 audit.md §0/§7 均已如实声明。
