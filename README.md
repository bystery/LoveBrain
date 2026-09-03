<div align="center">

# 💘 LoveBrain

**面对异性大脑空白？单身久了不会聊天？除了“可以可以”、“牛逼”、“哈哈哈”再无话可说？**

军师 1s 给你 4 种风格的接话示例，用你自己的话，说出最想表达的真心。 

**话是你说的，心是你的——军师只递词，不替你开口。**

[![Version](https://img.shields.io/badge/version-1.1.0-blue)](../../releases/latest)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-green)](../../releases/latest)
[![Privacy](https://img.shields.io/badge/隐私-零遥测·全本地-red)](#-隐私军师嘴很严)
[![License](https://img.shields.io/badge/license-MIT-orange)](LICENSE)

<!-- 🎬 TODO：录好演示 GIF 后放入 docs/demo.gif 并取消下方注释
<br/>
<img src="docs/demo.gif" alt="LoveBrain 演示：长按消息，悬浮窗弹出四种回复" width="360"/>
<br/>
-->

> 🎬 **演示 GIF 占位**：`docs/demo.gif`（待录制）

</div>

---

| | |
|---|---|
| **两步出方案** | 长按消息自动捕获，一键即时生成回复。不跳出聊天界面、不打断聊天节奏，要多快有多快。 |
| **长期关系记忆** | 市面工具“用完即忘”，LoveBrain 让长程与短程记忆协同，持续建模关系阶段与彼此偏好，越用越准——军师，越懂你。 |
| **全本地隐私** | 零遥测、无后端、知识库全在你手机里，可看、可改、可删、可带走。 |

[![⬇ 下载 APK](https://img.shields.io/badge/%E2%AC%87_Download_APK-Latest_Release-2ea44f?style=for-the-badge)](../../releases/latest)

## 😮‍💨 这是不是你

- 对话框打开半天，**打字、删掉、再打字、再删掉**，最后回了句"嗯嗯"
- 她说"我没事"，你真以为没事；她说“刚吃了饭”，你不知道怎么顺着聊，对话框停滞半小时。
- 想主动找她聊，开场白憋了二十分钟，发出去一句"在吗"
- 想表达在乎，怕显得太舔；想保持自我，又怕伤到人

## 💡 军师怎么帮你破局

LoveBrain 是一个**对话脚手架**。AI 替代不了人类的真心，但能帮你打破“开不了口”的第一步。你只需要做两件事：

1. **长按她发来的消息**
2. **点一下生成**
3. **把4种回复当成参考草稿自由组装，融入自己的语气发出去。**

## 🔧 工作原理

![LoveBrain 工作原理总览](docs/img/overview.jpg)

## 🚀 快速开始

### 方式一：直接安装（普通用户）

1. **下载**：在 [Releases 最新版](../../releases/latest) 下载 APK。
2. **配置**：打开 App，填入相关配置文件。
3. **权限**（按需开启）：
   - **悬浮窗权限**：必须开启，用于弹出悬浮球和建议面板。
   - **无障碍权限（可选）**：仅用于长按自动抓取上下文。**非强制开启**，若不开启，手动复制内容依然可以正常使用。

---

### 方式二：源码构建（开发者）

```bash
git clone https://github.com/bystery/LoveBrain.git
cd LoveBrain
./gradlew assembleDebug     # 产物在 app/build/outputs/apk/debug/
```


## 核心特性与机制

### 一条消息，四种思路
同一条消息，军师流式给出 **4 种风格的回复卡**，算好一张就先亮一张，不用干等：

| 方案 | 是什么 |
|---|---|
|  推荐 | 最自然的最优解，不卑不亢，容错率最高 |
|  清醒 | 有自己的想法和底线，不讨好的回法 |
|  俏皮 | 用玩笑接住情绪，不正经但不轻浮 |
|  温柔 | 核心语感是"我在"，把在乎表达出来 |

### 动态长程记忆
别的话术工具是"一次性军师"，没有上下文；LoveBrain 是**全程陪伴的专属军师**。它不只是单次帮你想词，而是通过长程记忆，持续积累你们独有的沟通习惯与默契。

| 维度 | 运作机制 | 实际作用 |
|---|---|---|
| **五维向量** | 亲密 / 信任 / 承诺 / 激情 / 安全 | 每 3 个话题自动重估并附带依据，默契变化看得见 |
| **阶段追踪** | 初识 → 破冰 → 暧昧 → 热恋 → 磨合 → 稳定 → 危机 → 修复 | 研判当下处境，给出该进、该稳还是该退的建议 |
| **经验知识库** | 每 5 个话题自动复盘近 25 轮对话 | 沉淀专属经验进本地知识库，避免在同一问题反复踩坑 |
| **立体画像** | 持续同步更新双方特征与相处温度 | 结合个性特征，让回复建议越来越贴合真实习惯 |

### 更多辅助场景
- **谈心模式**：她emo了、你们闹矛盾了，军师按"六步法"帮你拆局面、提供沟通切入点。
- **今日锦囊**：结合当前相处阶段，每日提供相处策略建议。
- **主动开场**：输入想说的话，军师为你润色出自然、不尴尬的话头。

## 🧪 测试结果

测试数据来自自动化单元测试与真机冒烟测试。耗时与花费由 App 内建计费系统（`UsagePricer` 分时段计价 + `todayCostYuan`/`lastResponseMs`）实时统计，非人工估算。

### 自动化单元测试

| 测试套件 | 测试类数 | 说明 |
|---|---|---|
| 数据层 | 9 | DeepSeek API 错误映射/配置分类、EventBus 捕获、HTTPS 信任、知识库安全/重入/备份、向量标签契约、矢量 pathData 契约 |
| 领域层 | 6 | 知识引擎契约、消息截断、Prompt 组装顺序/阶段契约、阶段目录、问卷自定义输入契约 |
| ViewModel | 10 | 花费展示、草稿持久化、想法提示、R2/R5 回归、画像确认、供应商就绪、工单删除与思考切换、部分 JSON 解析 |
| UI | 1 | 对比度回归 |
| 工具 | 2 | JSON 工具、用量计价 |
| **合计** | **28** | 运行命令：`./gradlew test` |

### 真机冒烟测试

| 场景 | 首字耗时 | 本次花费（元） |
|---|---|---|
| 长按捕获 → 生成 4 方案 | — | — |
| 谈心模式 6 步法 | — | — |
| 今日锦囊生成 | — | — |
| 主动开场润色 | — | — |

> 表中"—"为占位，实际数值由真机运行后填入。详见 [测试报告](docs/test-report.md)（待补充）。

## 🚧 边界声明
LoveBrain 旨在帮助你在关系里**真诚表达、不讨好、不内耗**。军师给的是表达参考，没有"必胜话术"，它帮你开口，真诚靠你自己

### 红线
* **不教操控技巧**：提示词中硬性屏蔽打压贬低（PUA）、故意制造焦虑、冷暴力等行为。
* **不否定对方情绪**：系统绝不出“你想多了”、“太敏感了”等消耗式回复。
* **不伪装虚假人设**：建议目标是“符合你本人的真实表达”，不做欺骗性包装。
* **绝不自动代发**：无自动发送功能，内容必须由你亲自确认、修改并手动发出。

### 使用红线
* ❌ 严禁用于骚扰、纠缠已被明确拒绝的对象。
* ❌ 严禁用于未成年人交往诱导。
* ❌ 严禁用于诈骗、杀猪盘、虚假交友等违法违规行为。
* ❌ 严禁打包转卖或包装为付费情感课程二次牟利。


## ❓ 常见问题

**Q：会读取我的聊天记录吗？**
A：只在你**长按某条消息**时，读取那一条消息——这是生成回复建议的必要输入。不会后台扫描、不监听键盘、不读通知。

**Q：数据会上传吗？**
A：只有你点"生成"时，当次上下文会发给对应的模型服务商。其余全部留在你手机本地：知识库是 App 私有目录里的 Markdown 文件，随时可编辑、导出或删除。

**Q：免费吗？**
A：App 本身开源免费（MIT）。但生成回复需要你自备 API Key。

**Q：iOS 有吗？**
A：暂无。iOS 的无障碍和悬浮窗限制更严，先做好 Android。

**Q：军师会自动帮我发消息吗？**
A：不会。军师只给建议，点卡片是复制到剪贴板，发不发、怎么发永远是你自己动手。

**Q：支持什么模型？**
A：自定义模型，但是我们内部的 花费判断目前只适配了deepseek

**Q：她会知道我用 AI 回她吗？**
A：军师的提示词里专门有"自然度检查"，目标是让建议像人话。但更重要的是：把卡片当成草稿，改成自己的语气再发——它帮你开头，真诚靠你。

## ⚠️ 免责声明

本项目仅供学习交流与娱乐参考。AI 生成的回复不构成任何情感、心理或法律建议；请对自己的言行和感情负责。部分回复风格卡带有博弈色彩的措辞（如推拉、框架类表达），它们只是风格选项而非行为指导——本项目不鼓励任何操控式沟通，真诚才是唯一必杀技。使用本项目即表示你理解并同意：因使用 AI 建议产生的任何后果由使用者自行承担。

## 📄 License

[MIT](LICENSE)

## 🌏 English

**LoveBrain** is a free & open-source Android floating-window assistant that helps you communicate sincerely in your relationship — without people-pleasing, and without overthinking. Long-press any incoming message in any chat app, and it captures the context and streams 4 reply suggestions in a floating panel. Over time, it builds a private, local knowledge base: a 5-dimension relationship vector, an 8-stage relationship tracker, and an experience library — so its advice gets more personal the more you use it. Everything is stored on-device in plain Markdown files (no telemetry, no backend, encrypted API key); the only network request goes to the DeepSeek API you configure with your own key. Requires Android 8.0+.

📖 **Full English README: [README_EN.md](README_EN.md)**

---

<div align="center">

如果军师帮你追到了，回来点个 ⭐ 就行。

</div>
