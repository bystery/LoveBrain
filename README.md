<div align="center">

<img src="https://github.com/bystery/LoveBrain/releases/latest/download/LoveBrain-demo.gif" alt="LoveBrain 悬浮面板演示" width="360"/>

# LoveBrain

**想好下一句，再发出去。**

一个在聊天界面旁边陪你出主意的 Android 悬浮 AI 助手。

它帮你想回复、找话题、整理关系记忆；**不代聊，也不会替你发送消息**。

[下载安装](https://github.com/bystery/LoveBrain/releases/latest) · [反馈问题](https://github.com/bystery/LoveBrain/issues)

</div>

## 聊天时，少一点纠结

对方发来一句不知道怎么回的话？长按捕获，或者自己复制进输入框，LoveBrain 会给出**最多 8 条不同风格和方向的建议**。你可以挑一条、改一改，再亲自发送。

它也可以在你不知道如何开口时，帮你想**主动发消息的切入点**，并提醒你合适的时机，以及什么时候最好先别发。

## 你可以这样用

| 想做什么 | LoveBrain 能帮你什么 |
| --- | --- |
| **想回复** | 给出多种语气和方向；支持复制、评价和进一步调整 |
| **想主动开口** | 建议话题、开场方式、时机与注意事项 |
| **想让建议更懂你** | 建立本地知识库，记录相处阶段、画像和经验 |
| **有一个持续目标** | 写下你希望推进的方向，开启时供后续普通生成参考，关闭后不再使用 |
| **记忆有误** | 查看本轮参考了什么，并对相关记忆进行纠正或暂停引用 |
| **不想挡住聊天** | 调整悬浮球大小和透明度；回复卡片可选横排或竖排 |

## 三步开始

1. 从 [Releases](https://github.com/bystery/LoveBrain/releases/latest) 下载最新 APK 并安装（Android 8.0 及以上）。
2. 在 App 中配置自己的 AI 服务商、模型和 API Key，按引导开启悬浮窗。
3. 在聊天界面打开悬浮球，输入或粘贴消息，点击「生成回复」。

**长按自动捕获是可选功能。** 需要你主动开启无障碍服务并选择允许捕获的聊天应用。不授权也可以手动输入；LoveBrain 不会自动点击、自动发送消息。

支持 DeepSeek 及部分 OpenAI 兼容接口。不同服务商的可用性和费用由对应服务商决定。

## 它是怎么工作的

<img src="https://github.com/bystery/LoveBrain/releases/latest/download/LoveBrain-owerview.jpg" alt="LoveBrain 工作原理：触发捕获 → 检索装配 → 组装提示词 → 流式生成 → 挑选改写 → 后台沉淀 → 闭环回读" width="760"/>

整条链路分四层：悬浮球 UI 负责捕获聊天文本、呈现候选回复（`ui/` + `feature/`，84 个文件）；`viewmodel` 与 `domain/GenerationEngine` 负责组装提示词、调度流式生成、挑选与改写（56 个文件）；`data/` 与 `KnowledgeRepository` 负责本地知识库、加密存储与事件沉淀（31 个文件）；`core/` 与 `model/` 承载基础设施与数据结构（49 个文件）。

提示词本体不是散落在代码里的字符串，而是 `app/src/main/assets/engine/` 下的版本化资产：system_prompt 六件套、流程与知识引擎资产各归其位，并有契约单测锁定结构。深读入口：[assets/engine/README.md](app/src/main/assets/engine/README.md)。

main 分支源码分层实测（236 个 Kotlin 文件）：ui 65 · domain 47 · data 31 · core 27 · model 22 · feature 19 · viewmodel 9 · util 7 · service 6 · di 1，另有根级 App/Config 2 个。

## 验证与质量

[![CI](https://github.com/bystery/LoveBrain/actions/workflows/ci.yml/badge.svg)](https://github.com/bystery/LoveBrain/actions/workflows/ci.yml)

每次推送到 main 或提交 PR，CI 的 `verify` job 会自动跑一遍：单元测试、lint、androidTest 编译检查、协程取消静态护栏。R8 体积检查与模拟器 UI 测试放在手动工作流（`manual.yml`），发布核对在 `release.yml`。

本地验证一行命令：

```bash
./gradlew testDebugUnitTest
```

测试体量：`app/src` 下 604 个 Kotlin 文件中，368 个是测试（358 个单元测试 + 10 个 androidTest）——每一条生成链路改动都有对应的契约与行为测试兜底。

延迟、缓存命中率、单轮成本这类量化读数，连同它们的适用边界，单独放在 [`BENCHMARK.md`](BENCHMARK.md)：数字只代表 2026-09-03 那一轮真实运行（19 场景 / 50 题，deepseek-v4-flash），换模型、换时段就是另一组数，所以不进 README 正文，要看边界说明请点进去。

## 为什么这套方案有效

这套机制不是凭感觉设计的：每一条都在独立考场里，用带逐字标注的合成对话数据验证过。

### 长程事实不再丢失

多轮对话后仍能记住前面约定的具体事实（金额、日期、约定、原话）。滑窗基线的保持率为 38.6–55.7%，LoveBrain 的完整机制在 4 个新域上均为 100%。详见 [评估详情](research/EVALUATION.md)。

### 更省的上下文，更稳的输出

提示用量最多减少 52.2%，输出契约违规从 7/72 降到 2/72。详见 [机制原理](research/MECHANISMS.md)。

### 盲评胜出

两组候选的组级盲评分别为 11:1 与 11:0（另 1 平）；首选建议直接可用 12/12，候选之间零重复。详见 [盲评终账](research/evaluation/user_value_v1/report.md)。

### 建立在可核验的数据之上

317 个合成故事、299 个 Gold episode、4,082 轮对话、9,646 条逐字可核验断言，全部通过 quote 回验。详见 [数据集文档](research/DATASET.md) 与 [统计口径](research/benchmark/STATISTICS.md)。

### 想读完整研究？

从 [research/](research/README.md) 进入：[论文稿](research/PAPER.md) · [研究总览](research/OVERVIEW.md) · [复现指引](research/REPRODUCIBILITY.md)。

## 关于隐私

- **不需要 LoveBrain 账号。** 知识库、关系画像和聊天归档主要保存在设备的 App 私有空间，API Key 使用 Android 加密存储。
- **AI 生成并非离线运行。** 发起生成时，本轮输入及完成回答所需的相关知识库内容会发送给你配置的 AI 服务商；请留意该服务商的隐私政策。
- **不自动代发。** 生成的是供你选择和编辑的建议，发送给聊天对象由你自己决定。
- **备份要留意。** 知识库导出文件可能包含明文聊天或画像信息，不要随意分享；系统云备份已关闭。

## 版本与升级

最新可下载版本以 [Releases 页面](https://github.com/bystery/LoveBrain/releases/latest) 为准。

**重要：** 自 v1.4.0 起，发布签名证书已更换。v1.3.1 及更早旧证书版本无法直接覆盖安装新版；卸载旧 App 会删除其本地数据，操作前请先导出并妥善保存需要的内容。v1.4.0 与后续使用相同签名的正式版本可正常覆盖升级。

## 构建

从源码构建：

```bash
git clone https://github.com/bystery/LoveBrain.git
cd LoveBrain
./gradlew assembleDebug
```

需要 Android SDK 与 JDK 17。编译产物位于 `app/build/outputs/apk/debug/`。

## 开源许可

LoveBrain 以双许可发布，使用者任选其一：**AGPL-3.0**（开源免费，官方全文见 [`LICENSE`](LICENSE)）或**商业许可**（闭源商用，两种选项的完整说明见 [`LICENSING.md`](LICENSING.md)）。

欢迎通过 [Issues](https://github.com/bystery/LoveBrain/issues) 提交建议和问题。
