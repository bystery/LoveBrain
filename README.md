<div align="center">

<img src="images/demo.gif" alt="LoveBrain Android 悬浮聊天助手真实交互演示" width="360"/>

# LoveBrain

### 不是替你回一句话，而是帮你把这段关系聊明白。

**记住很久以前的细节 · 不把猜测说成事实 · 一次给出真正不同的高情商回复。**

Android 悬浮 AI 聊天助手：贴在聊天界面旁，读懂上下文，帮你构思、对比、修改；**最后由你自己决定发什么，不会自动代聊或发送。**

**[下载安装 APK](https://github.com/bystery/LoveBrain/releases/latest)** · [看真实聊天案例](#一个故事看懂-lovebrain) · [查看实验成绩](#这些优势是怎么测出来的) · [开源代码](app/)

</div>

<img src="images/research-highlights.svg" alt="LoveBrain 核心实验成绩：长程事实保持 100%、严重错误 6 降至 1、新生成整组盲评 11 比 1、历史固定模型整组盲评 11 比 0 另 1 平" width="100%"/>

> **成绩是做出来的，不是写在 Prompt 里的。** 25–32 轮长程事实保持率达到 **100%**（旧滑窗仅 49.3%/50.9%）；未确认事项引发的严重事实错误从 **6 处降至 1 处**；在两组各 12 对完整回复组盲评中，LoveBrain 分别取得 **11:1** 和 **11:0（另 1 平）**。各项的原始判决与评分表都在[证据索引](docs/appendix/EVIDENCE_INDEX.md)。

## 一个故事看懂 LoveBrain

这是项目里真正跑过的**合成聊天测试故事 E340「合唱团低音搭子」**，人物虚构、对话和下列输出均来自仓库现有实验，不是为 README 临时编造的成绩。

霍青崖和顾星晚是认识一年的合唱团搭子。两个人互怼、互相关照，却谁都没有把关系说破。

故事里埋着很多只有认真跟过聊天的人才懂的伏笔：

- **起点**：她让他排练结束买奶茶，三分糖去冰；嘴上嫌他拍子，心里知道他唱得很稳。
- **后来**：她声带不舒服，他默默送了润喉糖；她选中独唱段，让他的低音和声接自己的高音，**由她给舞台手势，他来跟拍**。
- **演出前**：她半开玩笑约夜宵，第二天又怕被团友起哄而收回；随后却再次试探散场吃饭的地点。
- **终于**，演出前一天下午，她发来一句：

> **顾星晚：** 明天演出。我哥高铁到了，我跟他说我有个特别稳的低音搭子，台上从来不抢拍。他问搭子是什么搭子，我没理他。

这句表面上只说“搭子”，背后却是一次克制的关系试探：**她把他介绍给哥哥，又不肯替两人的关系下定义。**

### 普通 Direct 的实际回复

> 明天你只管唱，手势我给你打准，一个拍都不会让你等。你哥三年没回来，这回让他听听定音针什么水平。散场先别急着跟车走。

它也能顺着演出话题回复，但把舞台上**谁给谁打手势**的方向说反了，还把仅供助手参考的哥哥行程背景带入了对外建议。话题接住了，细节和分寸却不够稳。

### LoveBrain 完整机制的实际输出

**首选 · 接住暧昧，不替她表态**

> 没理他就对了，留着明天台上给他答案。你哥坐哪排？我那句和声下去，让他自己判断是什么搭子。

**轻挑 · 试探一下她的意思**

> 什么叫没理他，是还没想好怎么介绍吧。行，明天我自己介绍。

**俏皮 · 把只有你们懂的梗接回来**

> 特别稳的低音搭子，这称呼我收下了，比听你喊我全名顺耳。你哥要是追问，就说这搭子管琴房钥匙。

**温柔 · 让演出本身先稳住**

> 你哥来了，明天你唱的时候心里能踏实点。我就在你侧后方，手势一落我就跟。

还能换一个真正不同的话题方向：

> 散场那顿夜宵，你哥要是在，我就当着他的面问清楚是什么搭子。

这不是把一句话改写成四种同义句。**它给的是不同的关系姿态：含蓄、试探、玩笑、陪伴，以及能继续推进的话题。** 用户按当下的心情挑一句即可。

[阅读 E340 完整故事](research/benchmark/stories_v2/wave5_selection/E340/episode.json) · [LoveBrain 原始输出 JSON](research/experiments/runs/selection_v1/CURRENT/E340/turn_10_E340_T10/reply_raw.txt) · [Direct 原始输出](research/experiments/runs/selection_v1/DIRECT/E340/turn_10_E340_T10/reply_raw.txt) · [这一题逐候选评分理由](research/evaluation/user_value_v1/m5-eval/hpair2-01.json)

## 为什么它不是“套了一段更长的 Prompt”？

普通回复工具的任务是：“根据这段话，写一句回复。”

LoveBrain 要处理的是真实聊天里的连续状态。它有三层协同机制：

| 聊天里真正难的事 | LoveBrain 做了什么 | 最终帮你得到什么 |
| --- | --- | --- |
| **聊太久，早期细节丢了** | 只把确认的真实聊天事件写入长期上下文，按完整消息保留历史证据、选择当下有用的记忆 | 记得旧伏笔，不会把两个人的设定聊丢 |
| **事情改了、没定，却被 AI 说死** | 识别旧约定失效、未确认事项和私人备注；按当前话题组织约束，必要时阻止无据坐实 | 不会轻易替你订票、承诺、确认关系或泄露心里话 |
| **一句回复没错，却不是我想发的** | 分开生成首选、不同风格和可选话题方向；用确定性守卫保证输出结构 | 你有真正不同的沟通策略，而不是只有一条客服式标准答案 |

<img src="images/lovebrain-pipeline.svg" alt="原始聊天和记忆，经事实筛选与上下文装配，再生成多种可编辑回复" width="760"/>

**记忆形成、上下文取舍、状态纠错、未确认事实约束、输出结构校验，是可执行的代码和机制，不是单句“请认真思考”。** 想看具体实现，可以从 [核心机制与代码位置](research/CORE_MECHANISMS.md) 进入。

## 这些优势是怎么测出来的？

为了让“有记忆、更懂分寸、多种选择”可以被证实，我们不是随便找聊天截图打分，而是构造了成体系的合成对话：

| 测试资产 | 已完成规模 |
| --- | ---: |
| 不重复的合成聊天故事 | **317 个** |
| 带事实 Gold 标注的故事 | **299 个** |
| Gold 覆盖的对话轮数 | **4,082 轮** |
| 可逐条核验的细粒度事实断言 | **9,646 条** |

**一条题目是什么样的？** 就像上面的合唱团故事：给系统之前发生过的聊天、当前这句话、关系阶段和允许使用的个人备注，让它提出回复。针对事实型题目，标准答案卡记录哪些事实必须用对、哪些旧信息已经作废、哪些未定事项绝对不能说成既成事实；针对多候选体验题目，则让匿名评委看**完整候选组**，逐条判断事实、可用性、方向差异，并比较哪组真正更值得用户挑选。

不是只比较回复长度，也**不要求 LoveBrain 和普通 AI 生成相同数量的回复**：一次提供多条选择本来就是 LoveBrain 的产品机制。评委需要指出具体哪句做得好、哪句触犯了事实边界，不能只给一个没理由的“感觉不错”。

本轮用户价值测评使用 **60 道冻结案例**；其中多候选偏好对比为 **12 对**。这与上面的 317 个故事总资产是不同统计口径。长程记忆则由专门的 25–32 轮长剧实验测定，不把 E340 这条十几轮的剧情假说成三十轮测试。

### 实验真正测出的提升

| 读者关心的结果 | LoveBrain | 相应基线 | 验证 |
| --- | ---: | ---: | --- |
| **25–32 轮仍保留早期关键事实** | **100%** | 旧滑窗 49.3% / 50.9% | [FLJUDGE](research/refs/experiment_protocol_20261023/long_probe_20261030/FLJUDGE_VERDICT_20261102.md) |
| **换四个不同场景仍保留早期关键事实** | **四域全部 100%** | 旧滑窗 32.3%–62.4% | [LD1](research/refs/experiment_protocol_20261023/long_probe_20261030/LD1_VERDICT_20261102.md) |
| **无据坐实、严重事实错误** | **1 处** | 旧机制 6 处 | [W16](research/refs/experiment_protocol_20261023/long_probe_20261030/W16_UNC_VERDICT_20261102.md) |
| **新生成回复组的盲评选择** | **11 胜** | 普通 B1 1 胜 | [M5-N](research/evaluation/user_value_v1/m5-final-tally.json) |
| **历史固定模型回复组的盲评选择** | **11 胜** | Direct 0 胜、1 平 | [M5-H](research/evaluation/user_value_v1/m5h-final-tally.json) |
| **第一条建议即可用** | **12/12** | 11/12 | [M5-N](research/evaluation/user_value_v1/m5-final-tally.json) · [M5-H](research/evaluation/user_value_v1/m5h-final-tally.json) · [六项总览](research/evaluation/user_value_v1/summary.json) |
| **不同话题方向真正有效** | **44/47（93.6%）** | 普通基线无同构方向槽 | [M5-N](research/evaluation/user_value_v1/m5-final-tally.json) |
| **结构化回复契约违规** | **2/72** | 旧机制 7/72 | [W17B](research/refs/experiment_protocol_20261023/long_probe_20261030/W17B_GUARD_VERDICT_20261103.md) |

换句话说：**我们分别测了“能不能记住”“会不会乱说”“用户有没有真正可挑的回复”**。这些成绩来自不同的对口实验，整合起来才是 LoveBrain 的完整价值，而不是拿一个统一分数抹掉它的长处。

<details>
<summary><strong>查看完整六项评分、Direct/CoT 四臂对照及研究细节</strong></summary>

完整 M5 六项已全部结账，含首选可用率、至少一条可用率、有效方向覆盖、重复候选比例、备选严重错误率和整组盲评偏好。历史与新生成两组数据分别呈现；不好的分项也保留在原始成绩单中。

- [实验成绩与完整证据索引](docs/appendix/EVIDENCE_INDEX.md)
- [60 题用户价值评估报告](research/evaluation/user_value_v1/report.md)
- [逐项评分规则](research/evaluation/user_value_v1/scoring-templates.md)
- [53 份 Arena 历史判决目录](research/refs/VERDICT-INDEX.md)
- [研究论文与完整机制](research/PAPER.md)

</details>

## 现在就试试

1. [下载 Android APK](https://github.com/bystery/LoveBrain/releases/latest)，支持 Android 8.0 及以上。
2. 在 App 中配置你自己的 AI 服务商、模型与 API Key，开启悬浮窗权限。
3. 在任意聊天软件旁呼出 LoveBrain，长按捕获（需自愿开启对应权限）或直接粘贴消息，生成、选择并亲自发送。

**你拥有最后的发送权。** 生成内容是供你编辑的草稿，LoveBrain 不会替你发送；关系记忆和画像保存在本地，调用 AI 时只将本轮必要输入与相关记忆发送给你选择的服务商。

<details>
<summary>安装升级、源码构建、研究分支与许可</summary>

- 正式 APK 以 [Releases](https://github.com/bystery/LoveBrain/releases/latest) 为准。v1.4.0 起发布签名证书已更换，旧签名版本升级前请先在 App 内导出并备份关系记忆。
- 使用 JDK 17 + Android SDK，可执行 `./gradlew assembleDebug` 本地构建。
- 本仓库 `research-migration-20261112` 分支展示完整研究资产；分域冠军的 `LIVE_AUTO` 主链路接线在 [`closeout-20261106` 分支](https://github.com/bystery/LoveBrain/tree/closeout-20261106)。本分支未合并到 `main`，不同实验的机制证据与正式安装包版本分别有据可查。
- [AGPL-3.0](LICENSE) 或 [商业许可](LICENSING.md) 双许可；欢迎提交 [Issue](https://github.com/bystery/LoveBrain/issues)。

</details>
