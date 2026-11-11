# 新复验材料出题任务（D 批）——给独立出题者的唯一说明书

## 0. 你的身份与边界（必须先读）

你是**出题者**，不是候选实现者。你写的题目要在候选从未见过的素材上测试它。

硬性禁止：
- 你**不允许**阅读或参考以下任何东西：`candidates/` 目录下任何文件、任何 `logs/**` 答卷、
  任何 `refs/experiment_protocol_20261023/**` 下的评审结论或判词、任何含
  "action_policy"/"reference_product"/"v6_integrated" 字样的机制代码。
- 不允许把任何候选写过的句子、修过的故障、评分器喜欢的表达当成题目的"正确答案"。
  你要写的是**生活里真的会出现的局面**，判分标准来自本说明书 §3。
- 素材必须是**编造但自洽**的成年人在职场景；不得取自真实用户聊天记录，不得取自
  `benchmark/stories_v2/stage1|stage2` 已有的 16 个故事与 `anchors_20261028` 的 12 个锚点
  （它们已被候选练过）。你若要理解格式，只允许读本说明书 §1 的字段清单，
  **不要读那些旧故事的文件内容**。

## 1. 交付物（写在指定目录，别碰别处）

每个故事写 3 个文件，两个故事共 6 个文件，放在
`D:/lovebrain-arena/benchmark/stories_v2/stageD_src/<STORY_ID>/`：

1. `episode.json`
   顶层键**必须恰好是** `episode_id, persona, initial_kb, turns`（多一个少一个都算失败）。
   - `persona`：`{"me": "...", "her": "..."}`，各一句身份（年龄+职业+处境），例如
     `"me": "32岁，三甲医院检验科技师，倒班。"`
   - `initial_kb`：**六个键、每个都是短字符串标量**，不得嵌套、不得加别的键：
     `me`（我的长期情况，含一条以后会被用到的具体事实）、
     `her`（她的长期情况，含一条限制或偏好）、
     `warmth`（两人关系到哪一步、怎么认识的、认识多久）、
     `style`（说话风格约束）、
     `stage`（当前关系阶段的一句话）、
     `lessons`（历史教训/易踩的雷，含一条"她说X时其实是Y"这类误读纠正）。
   - `turns`：**恰好 8 个**请求位，`case_id` 依次为 `<STORY_ID>_T01 … _T08`。
     每个 turn：
     ```
     {"case_id": "...", "turn_type": "reply", "input": {...}, "gold": {}}
     ```
     - `gold` 必须是**空对象** `{}`（金标不进题面）。
     - `input` 允许键：`messages, clock, may_decline, intent, note`（不要出现别的键）。
     - `messages`：本轮新增的对话消息，每条 `{"role": "HER"|"ME", "text": "...", "ts": "YYYY-MM-DD HH:MM"}`，
       1—4 条；`ts` 不得晚于本轮 `clock`，且全故事时间单调递增（`clock` 也不能倒退）；
       ME/HER 交替自然，ME 的话是用户本人已经发出去的，不是草稿。
     - 每轮消息必须**只有本轮新增**（历史由 runner 逐位累积，不要把整段对话塞进每一轮）。
     - `may_decline`：布尔。全故事**恰好 2 轮**为 true，且必须是"她已明确收尾/明确说不必回"的局面。
     - `intent`：用户当前想达成的目标对象
       `{"text": "一句话目标", "status": "ACTIVE|ADJUSTED|CLOSED|COMPLETED|PAUSED", "enabled": true|false}`。
       至少出现 5 轮，且必须演示一次完整生命周期：先 ACTIVE 推进 → 一次 ADJUSTED（她改了时间/条件，
       目标随之改写）→ 一次 CLOSED 或 COMPLETED（目标真的结束）。
       **禁止**在 CLOSED/COMPLETED 之后的轮次里把同一目标重新写成 ACTIVE。
     - `note`（可选，1—3 轮）：给用户看的补充背景（例如"她这周值夜班，白天要补觉"）。
       写进 note 的事实允许被回复引用，但 note 里不能出现评审用语
       （"扣分""合规""红线""评分"等一律不许出现）。

2. `reference.json`（这是**判分卡**，候选永远看不到它）
   ```
   {"story_id": "<STORY_ID>", "turns": {"<STORY_ID>_T01": {卡}, ...}}
   ```
   卡必须**恰好覆盖**题面的 8 个 case_id，每张卡字段齐全：
   `case_id, current_action, acceptable_actions, applicable_dimensions, required_memory_atoms,
   private_guidance, unknowns, protected_expressions, forbidden_factual_claims, may_decline, intent`
   - `current_action`：一句话说明"这一轮最合适做什么"，必须具体到本轮她说的内容。
   - `acceptable_actions`：**至少 2 条**、互不重复、都真的可行（不要写空话如"表达关心"）。
   - `applicable_dimensions`：从这九个里选**至少 3 个**：
     `goal, factuality, attribution, time_state, boundary, memory_use, interaction,
     initiative, emotion_relation`
   - `required_memory_atoms`：每条含 `atom_id, subject, relation, value, valid_time, status,
     negation, source, required_this_turn`。
     `source` 两种合法形态：
     `{"kind": "message", "case_id": "<本轮或更早的 case_id>", "message_id": "m0", "quote": "逐字原话"}`
     （quote 必须逐字出现在那条消息里；不能引用**未来**轮次；
     若 `required_this_turn=true`，它的 source 轮次**不得等于本轮**——即"本轮必须用上的旧事"
     必须来自更早的轮次或 `initial_kb`）；
     或 `{"kind": "initial_kb", "key": "me|her|warmth|style|stage|lessons 之一", "quote": "逐字片段"}`。
     全故事至少 4 个原子的 source 来自 **initial_kb**（测长期库使用），
     至少 3 个原子的 source 来自**更早轮次**（测跨轮回想，不是本轮原句复读）。
   - `unknowns`：此刻确实不知道的事（至少 1 条），它必须能被回复"自然问一句"而不是编造。
   - `protected_expressions`：她本轮的原话片段，必须逐字出现在该轮 HER 消息里。
   - `forbidden_factual_claims`：这一轮**不能声称**的事（例如替她下结论、把提议当约定、
     把她没说过的话安给她）。至少写 4 张卡的这一项非空。
   - `may_decline` 与题面该轮一致；`intent` 与该轮题面 intent 一致（含 null）。

3. `audit.md`：给后来复核的人看的账目，必须包含以下八个二级标题（一字不差）：
   ```
   ## 0. 性质与暴露
   ## 1. 生活动机
   ## 2. 连续性与回扣
   ## 3. ME 历史与边界
   ## 4. no_reply / 媒体局限
   ## 5. 参考卡质量
   ## 6. 已知风险
   ## 7. 出口检查
   ```
   内容要求：写清这两个人为什么每天真的要说这些事（§1）；哪几轮回扣了更早轮次或长期库、
   回扣的是什么（§2）；ME 已经承诺/做过什么、哪些事 ME 无权替她决定（§3）；
   哪几轮"不回"才合法、为什么（§4）；卡里哪些判断是主观的、复核者可能不同意哪里（§5）；
   该题自身弱点（例如某轮的"最优解"其实有多种）（§6）；
   §0 与 §7 必须如实写：**素材为编造，未做独立人类复核，未使用真实用户材料**。

## 3. 出题质量标准（这才是判你写得好不好的地方）

写两个故事，`D01`、`D02`，各 8 位，两位主角都是成年人、有真实生活负担。要求：

1. **目标是真的在动**：至少有一条贯穿目标（约时间、修东西、把一件事办成），
   它在中间必须被她的反馈**真实改写**一次（时间变了、条件变了、或者她明确说不做了）。
   不许 8 轮都停在"再聊聊看"。
2. **积极互动必须有机会**：至少 3 轮给出可被主动推进的窗口（她抛出兴趣、留下话头、
   或明显期待我提议）。不许把整题写成"她冷淡所以我退让"的合集——退让与不回各只占 2 轮上限。
3. **知识要被用**：至少 2 处，本轮的正确回答**必须**用到早期轮次或 initial_kb 里的具体事实
   （不用就会答错或答空）。同时设计 1 个**陷阱**：早期说过的事实后来被她更正或取消，
   到后面某轮仍然使用旧版本就是错。
4. **主体归属要能出错**：至少 1 轮出现第三人（同事/家人/客户/医生/宠物兽医等），
   使得"把第三人做的事安到她头上"成为可检测的错误。
5. **时间/状态要能出错**：至少 1 轮涉及"还没发生 vs 已经发生 vs 已取消 vs 已完成"的区分。
6. **她的含糊不等于同意**：至少 1 轮她说"再说吧/看看吧/可能"，
   卡里必须写明：把它当作答应并推进=越权；直接放弃=也没接住。
7. **情绪让位不是沉默**：至少 1 轮她带着疲惫/自责/委屈说话，
   卡里要区分"承接情绪"与"顺着不说话"，并在 `private_guidance` 说清此时推进目标为何是错的。
8. 全故事 16 个消息轮里， HER 的话要像微信：短句、有错别字级口语、不完整句，
   禁止书面腔和解释性独白。ME 的话同样短。
9. **不复刻旧素材**：不要婚礼、盲盒、建筑结构、看展、开放麦、考证、猫手术复健快走这些
   已经用过的题材。选两个彼此不同的领域（例如：倒班与体检复查、店招装修与供货商纠纷、
   学车与接送孩子、露营装备与维修返修、合唱排练与租房换锁——自己定，要新）。
10. 每张卡都要经得起被反驳：`current_action` 写的是"最合适"，不是"唯一正确"。

## 4. 自检（必须真跑，不许口头保证）

写完后必须用下面的命令自查，并把输出贴在你的最终汇报里（零网络，不花任何调用）：

```
cd /d/lovebrain-arena
set PYTHONIOENCODING=utf-8        # 注意：Windows 下用 cmd 的 set，或直接用 bash 环境变量前缀
D:\anaconda\python.exe -c "import json,io,glob; [print(f, len(json.load(io.open(f,encoding='utf-8'))['turns'])) for f in glob.glob('benchmark/stories_v2/stageD_src/*/episode.json')]"
```
逐条自查（用你自己写的临时脚本，或者逐条肉眼核对）：
- 每故事 `turns` 恰为 8；`case_id` 连续；`clock` 单调；消息 `ts ≤ clock`；
- `gold` 全是 `{}`；`may_decline=true` 恰 2 轮；`intent` 至少 5 轮且有一次 ACTIVE→ADJUSTED→CLOSED/COMPLETED；
- 卡与题面 case 集合完全相等；卡的 `may_decline`/`intent` 与题面一致；
- 每条 atom 的 quote 真的逐字存在于所引消息或 initial_kb 里；没有引用未来轮次；
  `required_this_turn=true` 的原子来源不是本轮；
- `protected_expressions` 逐字出现在该轮 HER 消息里；
- 至少 4 个 atom 来自 initial_kb、至少 3 个 atom 来自更早轮次；
- 至少 4 张卡的 `forbidden_factual_claims` 非空；
- `applicable_dimensions` 全部落在九个维度内且每卡 ≥3 个；
- audit.md 八个标题齐全。

**不要**试图运行 `tools/validate_story_v2.py` 或 `tools/build_stage2_grading_cards.py`
（它们依赖清单/暴露文件，那些由实施方在你交付后统一生成）；也不要修改它们或任何已有文件。

## 5. 你只能新建这些文件

允许写入的路径只有：
`benchmark/stories_v2/stageD_src/D01/` 与 `benchmark/stories_v2/stageD_src/D02/` 下的
`episode.json`、`reference.json`、`audit.md`，以及 `stageD_src/AUTHOR_NOTES.md`
（写你的自查结果与你对题目弱点的坦白）。
禁止改动仓库里任何其它文件。禁止删除任何东西。禁止运行任何会调用外部模型/网络的命令。

## 6. 汇报格式

最终汇报只写：两个故事各自一句话前提、每故事的 8 轮一句话概括、
你自查通过的条目清单、你没能自查到的东西、以及你觉得最容易被判错的 3 个位点。
不要写"是否继续"之类的确认请求——写完交付即可。
