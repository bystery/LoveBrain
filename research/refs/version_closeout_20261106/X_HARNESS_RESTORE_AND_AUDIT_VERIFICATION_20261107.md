# LoveBrain Harness 全景还原与外部审计核查报告（2026-11-07）

> 应对任务（来自用户与外部评审会话）：①完整还原当前产品 Harness（输入哪些信息 → 中间经过什么处理 → 保存了什么 → 输出回复）；②从本地完整证据核查 Arena 的主要判断与外部两份审计报告的事实；③给出"哪些可借鉴、哪些不能直接替换"的处置结论。**不直接替换代码。**
>
> 审计基准：产品仓 `D:/LoveBrain` 分支 `closeout-20261106` HEAD=`e2ebde1`（CI #120 = run 42359394997，本地 `gh` 复核 **success**）；Arena 本地全量证据（含 GitHub 未上传的 2.1GB 逐调用日志、判决书原件、原始答卷）。外部两份报告基于 GitHub 快照（main 40c1f12 + Arena 8d288d5 + closeout e2ebde1），本报告补齐其无法复核的部分并逐条核对。

---

## 〇、一页结论

1. **你的产品现在跑着两条 harness**：原版主链（仍是唯一正式回复引擎，已修 16 项缺陷，5 个提交在 `closeout-20261106`）＋ Arena 冠军引擎（`SHADOW` 影子旁路，读写双侧强制 OFF 收敛，默认关闭、不展示、不写主记忆）。外部报告"实际上是两条 Harness"的判断**属实**。
2. **Arena 的三个核心思想有真实证据**（原始证据优先 / 未确认不坐实 / 确定性代码修输出契约），判决书数字全部核实；但 **"整体替换原版"的证据不足**——Arena 自己的 BASELINE_PARITY.md 写明"整体优于原版仍未证明"，用户愿发盲评未做，B 线封存未消费。
3. **外部两份审计报告可信度高**：引用的全部关键数字（W22 的 severe/JSON/veto/swap、各判决 prompt delta、预算常量、触发间隔等）与本地判决书逐字一致，无捏造；需修正 4 处口径（见 §四）。
4. **审计三轮修复已全部落地**（`d9ff3db`→`6ed7fbc`→`b35a197`→`8cafeec`→`e2ebde1`，CI 绿）；剩余 = 真机验收（只有你能做）＋ 6 项已知代码级小缺陷（§六有最小修复标准）。
5. **处置建议：不整体替换。** 按已铺好的路径走：原版修断点（已做）→ 冠军 S1 shadow 对账 → S2/S3 三阶段 → B 线一次性盲评 → 用户愿发实测，再决定是否晋级。

---

## 一、当前产品 Harness 全景（e2ebde1 实测，全部有 file:line 证据）

### 1.1 总体：两条线并存

```
正式回复（用户看到的）：
聊天输入(HER/ME) + 军师备注(IDEA) + 持续意图 + 知识库
   → Composer 冻结快照 → GenerationInput（请求身份）
   → PromptBuilder（system 资产 + 知识段 + 任务段 + <chat> + 时间戳 + 预算裁剪）
   → GenerationEngine（1 次流式请求，最多 4 attempts）
   → 4 风格 + 4 方向 + analysis → 用户挑选/反馈
   → 用户"结束本轮" → TopicRecorder WAL 事务写 6 投影 → 后台维护(3/5 触发)

Arena 冠军（SHADOW 影子旁路，默认 OFF）：
   同一轮的输入快照 → ChampionPromptAssembler（seed+有效意图+备注+已确认事件3600CU
   +未定事项段+当前窗口整条+时间戳）→ 额外 1 次 fire-and-forget 模型调用
   → ContractGuard 修复审计 → shadow_log.jsonl（对照用，不展示）
   → 用户结束本轮且模式允许 → 按 msgUid 写独立事件库 filesDir/champion/events/
```

### 1.2 输入端：模型能拿到什么（原版主链）

| 输入 | 来源 | 何时进模型 | 是否长期保存 |
|---|---|---|---|
| 她的话（HER） | 无障碍复制捕获/手动录入 | 本轮 `<chat>` JSON 行 | 结束本轮提交后进 recent/归档 |
| 我的台词（ME） | 手动录入/捕获后指定角色 | 同上 | 同上 |
| 军师备注（IDEA） | 用户填写 | 转独立 ReplyDirective，不进 `<chat>` | 不作为真实聊天保存 |
| 持续意图 | 知识库意图配置 | 冻结快照进 IntentBlock | 单独存储跨轮 |
| 画像/偏好/阶段/经验 | understand/、lessons.md | 知识段（普通回复模式） | 持久化，确认制更新 |
| 近期聊天+场景 | recent.md / scene.md | 知识段 | recent 只留 **2 轮**（AppConfig.kt:60 MAX_TOPIC_TURNS=2），溢出进 raw_chat.md |
| 进行中事项 | plan.md | 经 OngoingContextSelector 三信号筛选+冷却 | 持续追踪 |
| 当前时间 | 系统时钟 | 时间戳段 | 不保存 |
| 仅看本轮 | 用户设置 | 开启则不注入画像/长期记忆/意图 | 不是记忆事实 |

关键实现事实：IDEA 在**类型层**已不是消息角色（`Dialogue.kt:11-27`），生成时被过滤并折进 ReplyDirective（`GenerationInput.kt:151-179`、`ComposerStore.kt:390-403`）——"军师的话被当成她的话"这一族（F1.1）在类型上不可能发生。

### 1.3 中间处理：装配与生成

- **装配顺序**（PromptBuilder.kt:252-262）：system = core/naturalness/redline/format + 安全声明；user = 知识段→场景→持续意图→军师备注→`<chat>`（每条带来源 ID）→时间戳。
- **预算**：TOTAL_BUDGET=9000 **字符**（非 token，AppConfig.kt:67）；对话最多 60 条（REPLY_MAX_MESSAGES，ChatTranscriptBlock.kt:46）。**已知边界：byBlocks 末尾无成品总长复检**（PromptBudget.kt:83），超长备注/粘贴可能越限——未修，登记在案。
- **冻结身份**：requestId、KB 身份、意图快照、纠正记录、Provider 身份（ticketId/hostHash/configHash）、promptAssetHash（GenerationInput.kt:24-44）。资产指纹漂移**只警告不阻止**（GenerationEngine.kt:200-203）；Provider 漂移才阻止（190-194）。
- **意图/备注接线**：原版 main 上确有缺口（`splitMessages` 冻结路径 directive 恒为 null，`git show 40c1f12` 实证），**已在 6ed7fbc 修复**：`selectOngoingForInjection(kb.name, messages, frozenDirective, intentConfig)`（PromptBuilder.kt:321-328）。外部报告 1 §1.3 的发现是真问题，且已按其建议修复。
- **生成**：成功主回复 = **1 次流式请求**；重试上限 GENERATE_MAX_ATTEMPTS=4（AppConfig.kt:15）；SHADOW 开启时额外 1 次 fire-and-forget（GenerationEngine.kt:336-364）。
- **解析**：4 风格+4 方向+analysis。**已知缺陷（未修）**：注释称"风格全空降级用 directions"，实际非空检查只看四风格位，方向有正文而四风格全空时判解析失败（DeepSeekRepository.kt:319-323 vs Models.kt:236-237）。

### 1.4 保存端：什么时刻真正改知识库

| 事件 | 实际行为 | 不是 |
|---|---|---|
| 点"生成回复" | 发请求、展示候选 | 未写长期记忆 |
| 复制/点赞/点踩 | 复制统计 / 偏好入库 / 负反馈案例 | 不代表已发送、不自动改画像 |
| 结束/保存本轮 | TopicRecorder 按 WAL 事务写 6 投影（话题归档/标签/recent/scene/plan/轮次计数，TopicRecorder.kt:251-310） | 默认把 AI 候选当真实 ME 消息的语义保留 |
| 后台维护 | 每 3 次话题转换向量重估、每 5 次经验提取+画像更新（KnowledgeTriggerCoordinator.kt:83-94，AppConfig.kt:63/84-87） | 不是每轮重写画像 |

### 1.5 Champion 影子线（Arena 移植，W23-02 W1/W23-03 R2 落地）

- **模式**：SINGLE_STYLE/FULL 读写双侧强制降 OFF 并记日志（ChampionEngine.kt:78-90、SecurePrefs.kt:364-378）；OFF 时 `commitConfirmedTurn` 零副作用（200-204）。**Champion 现在不是正式回复引擎。**
- **装配输入**（ChampionPromptAssembler.kt:97-160）：persona seed + 有效意图 + 军师备注 + 已确认历史事件（3600 UTF-16 码元预算，EventHistoryAssembler.kt:24-25）+ 未定事项段 + 当前窗口整条消息 + 时间戳。
- **存储**：`filesDir/champion/events/`（每键一文件）；对照日志 `filesDir/champion/shadow_log.jsonl`；守卫审计 `contract_guard_audit.jsonl`。ContractGuard 恒过影子路径（ChampionEngine.kt:258-265）；主路径守卫分支因 OFF **当前不可达**。
- **去重**：按稳定 msgUid（F2 修复后），同分钟同内容两条消息不再被误合并。

---

## 二、Arena 判断逐条核查（本地判决书实证）

| # | Arena 的判断 | 本地证据 | 核查判定 |
|---|---|---|---|
| 1 | 原版近期窗口太小，长链丢事实 | recent=2 轮（AppConfig.kt:60）；FK-LR2-01：滑窗两臂 T25-32 真实保持仅 38.6–55.7% 且随轮下滑（KNOWN_FAILURES.md L51-55） | **问题证实** |
| 2 | 扩大真实消息窗口能救长程保持 | FLJUDGE：T25-32 真实保持 1.0 vs parent 0.493/0.509，prompt −41.9%；LD1 跨四域：1.0 vs 0.32-0.62，终态 425/430，−44.0%；判决链 KEEP→KEEP | **证实（开发集）** |
| 3 | 复杂摘要冗余可删 | W15N01 消融：recap 删除后 keep 双臂 1.000/1.000、九维全正、prompt −0.3%——"无损可删"而非"有害"；W05-04 短故事域 Bmin 等效架构 | **部分证实**（只证明 recap 这一块，不证明"80-90% 没用"） |
| 4 | "原版 80–90% 机制没用" | AGENTS.md 原文：这是**用户待检验的判断**，"通过删除/替换实验检验，不伪称已经测得该比例" | **未证明，Arena 从未宣称已测** |
| 5 | 结构化事实账本更可靠 | W14M02：b2 因 LRE146_T20 把 HER 单方说法坐实+无据归属"老板亲口说的"而触发 H_SUP_null **淘汰** | **不成立（该方向已有反例淘汰记录）** |
| 6 | 未确认事项需显式"不许坐实" | W16：uc 在 >24 轮域 severe 1 vs 6（judge 原始 1 vs 10），KEEP 晋升域冠军 | **局部证实** |
| 7 | 归因锚定可修归属错位 | W22：A 族 cand 0 vs par 2 主假设正面；但 json_schema_broken 11→22、veto 19→29（G3 FAIL）、swap 翻转 7/22（G5 FAIL）→ **KEEP_NO_PROMOTE** | **主假设正面、候选被正确阻断** |
| 8 | "加一小段提示词不会破坏别的" | FK-INJECT-01：追加注入块 → json_schema_broken ≈翻倍（W22FULL REV2 登记） | **被反例证伪——这正是"想当然"的代价样本** |
| 9 | Arena 冠军全面优于真实 LoveBrain | BASELINE_PARITY.md L191-193 原文："候选 vs 基线的'整体优于原版'**仍未证明**"；冠军=候选 vs 直接父本的开发集相对判决；谈心/主动/润色/Android 交互不在基线比较范围；用户愿发未测；B 线封存未消费 | **未证明** |

**给"另一个人"的一句话**：Arena 自己的记录就是最好的示范——W22 想当然多加一段锚定提示词，结果 JSON 失败翻倍、被自己的 G3/G5 门拦下，判决 KEEP_NO_PROMOTE 而非"更强大所以上场"。这套"预注册判据+程序门+亲读复核+自我纠错（REV1→REV2）"的纪律，比它的任何一个候选都更值得借鉴。

---

## 三、外部审计报告事实核查结果

### 3.1 数字核对（全部对上，无捏造）

| 外部报告引用 | 本地证据 | 判定 |
|---|---|---|
| W22：归因 severe 2→0（A 族） | W22FULL_VERDICT_REV2 L12/L56：cand 0 vs par 2（REV1 记 par 1，REV2 修正） | ✅ |
| W22：JSON 失败 11→22 | L29/L33：json_schema_broken par 11 / cand 22（n=232/臂） | ✅ |
| W22：程序 veto 19→29 | L21：par 19 / cand 29，契约失败率 4.7%→9.5% | ✅ |
| W22：prompt +1.77% | L72：821,643→836,192 | ✅ |
| W22：swap 翻转 7/22；KEEP_NO_PROMOTE；REV1 漏 G3 | L73/L10/L19 全部一致 | ✅ |
| FLJUDGE/LD1 keep_true 1.0、−41.9%/−44% | RESEARCH_STATE L1306-1307/L1362-1364 | ✅ |
| E3 −18.5%、Bmin −63.6%（vs B0） | RESEARCH_STATE L155/L175 | ✅ |
| gold sidecar 3102 轮/2919 断言 | GOLD_INDEX.json L13-14 | ✅ |
| 外部称无法复算原始答卷 | 本地 `logs/` 2.1GB + 判决书原件在案，本次核对即补上这一环 | ✅（已补） |
| "8 核心文件两仓库 SHA 一致" | example-LoveBrain 快照仅在 GitHub arena 仓，本地无副本 | ⚠️ 未本地复核（不影响结论） |

### 3.2 需修正/补充的 4 处口径

1. **F1–F6 族名**：F4 是"表达缺陷"族（调情缺失只是 F4.1 子项，还有 AI 味/四风格趋同/舔狗化）；F5 是"装配缺陷"族（N=2 截断、Lost-in-the-Middle）。外部报告把 F4/F5 混称为"记忆机制/调情"。
2. **T6 验收矩阵 recap 措辞错误**（外部报告指出，核实属实，**本次已修**）：第 52/53 行写"扩窗+逐字 recap 生效"，但冠军链是 `b_norecap`——recap 已被 W15N01 消融删除，应为"3600 CU 扩窗（无 recap）"。
3. **冒号续行误拆的范围**：仅"冒号/编号"格式下独立成行的正文冒号句（如"我想说：其实…"）会被误拆；块格式安全（有反证测试）。另发现解析器 KDoc 与实现矛盾（BatchTranscriptParser.kt:302-307 的说法仅在块格式成立），且该形态无单测覆盖。
4. **gold sidecar 2919 的性质**：是 **AI 隔离会话出题卡片的逐字搬运**（build_gold_sidecar.py 头注"只读 episode+证据卡，不改一字"），不是人工真值——外部报告 1 其实自己也写对了（"不自动产生人类真值"），此处以本地证据链坐实。另补充一个本地新发现：`benchmark/b_line/README.md` 还停留在 v1 构成（LRE121/146/139/127），与 v2 manifest 实际构成（LRE119/123/139/142，CF=E150/E152/E153/**E155**）不一致，README 未随 v2 更新。

---

## 四、审计三轮修复落地状态（e2ebde1，全部有测试）

| 轮 | 修复 | 状态 |
|---|---|---|
| 批1+2（d9ff3db） | 输入框焦点链/批量导入/冠军影子骨架/记忆修复 | ✅ CI 绿 |
| 审计二轮（6ed7fbc/8cafeec/b35a197） | R1 批量导入接线、R2 冠军持久化/身份/OFF 诚实化、R3 备注+意图接入选项筛选、R4 纠正覆盖、R5 话题门独立性、R6 解析器回归、S1-S4 边界 | ✅ CI 绿 |
| 审计三轮（e2ebde1） | F1 话题门死锁（互斥条件删除，TIME_GAP/SCENE_EMPTY 单独可定案+佐证可组合）、F2-1 msgUid 去重、F2-2 冷却统一生效、F3 批量原子提交+长按➕发现提示 | ✅ CI #120 绿（run 42359394997，本地 gh 核实 success） |
| F4 治理同步 | TeamWorkspace ACTIVE.md 基线 8cafeec→e2ebde1 对账、T6 recap 措辞修正、TASK_QUEUE W23-04 关账 | ✅ 本次完成 |

**仍未解决（代码级，外部报告指认全部属实）**：

| 项 | 证据 | 建议 |
|---|---|---|
| persist() revision 写失败仍返回 true | KnowledgeMemoryStore.kt:152-165 + 测试注释"照抄旧语义" | 最小修：revision 返回值计入 written + 部分写入恢复测试（P1-1） |
| 批量导入原始时间不落库 | Entry.timestampText 仅展示；addMessages 用当前时间（ComposerStore.kt:484） | 等真实微信样本决定是否需要时间保真（P2-1），先登记 |
| 冒号格式续行误拆（"我想说：其实…"） | BatchTranscriptParser.kt:217-247 + 无该形态单测 | 补一条独立行冒号反例测试 + KDoc 修正（P1-3，成本极低） |
| PromptBudget 无成品总长复检 | PromptBudget.kt:83 | 加最终断言/超限降级路径（P2-2） |
| 风格全空×方向有效被判解析失败 | DeepSeekRepository.kt:319-323 | 按注释意图补 directions 降级或改注释+测试钉死现状（P2-2） |
| 资产冻结只警告 | GenerationEngine.kt:200-203 | 保持警告（字节冻结成本高），登记为已知边界（否决理由：真实风险=并发改资产，低频） |

**只有你能做的（P0，worker 不得代报）**：输入框长按/手柄/粘贴/全选/焦点链真机验收；真实微信样本 ≥10 条批量导入闭环；长按➕ vs 轻点回归；v1.4.2 覆盖安装。清单见 T6_ACCEPTANCE_MATRIX.md。

---

## 五、处置结论

1. **借鉴（已吸收）**：证据优先（3600CU 真实事件窗口替代纯摘要）、未确认不坐实（uc 未定事项段）、确定性代码修输出契约（ContractGuard）——三者已在产品线以 SHADOW 形态存在，走 W15-06 三阶段开关与回退。
2. **不替换**：原版的关系画像、表达偏好、确认发送、纠正体系、跨库隔离、WAL 事务、后台提炼，解决的是冠军开发集**没在测**的问题（BASELINE_PARITY 明文列出未覆盖范围）。删除它们必须先有删除实验证据，不能因为"代码变短"。
3. **晋级前置（缺一不可）**：S1 shadow 对账无回退 → S2 单风格 → S3 全量 → B 线一次性盲评（现在封存未消费）→ 同批真实案例上用户亲自选"更愿意发哪条"。Arena 开发集冠军称号不是生产替换凭证。

---

## 六、证据索引

- 产品源码：`D:/LoveBrain`（closeout-20261106@e2ebde1；本报告所有 file:line 为该 HEAD 实测）
- Arena 判决书：`refs/experiment_protocol_20261023/long_probe_20261030/W22FULL_VERDICT_REV2_20261104.md`、`W21FULL_VERDICT_20261104.md`、`W17B/W17C_20261103.md`、`W16_UNC_20261102.md`、`W15N01_20261102.md`、`W14M02_20261102.md`、`FLJUDGE/CF1/LD1_20261102.md`
- 故障与对拍：`refs/KNOWN_FAILURES.md`（FK-LR2-01、FK-INJECT-01、FK-SCORE-01、F1-F6）、`refs/arena_v1/BASELINE_PARITY.md`
- 题库与 gold：`benchmark/stories_v2/GOLD_SIDECAR/GOLD_INDEX.json`、`benchmark/b_line/B_LINE_MANIFEST.json`（封存未消费）
- 原始答卷（GitHub 未上传，本地在案）：`D:/lovebrain-arena/logs/`（约 2.1GB）
