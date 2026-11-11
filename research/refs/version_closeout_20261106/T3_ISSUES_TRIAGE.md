# T3 — bystery/LoveBrain Open Issues 对码分诊（2026-11-06）

- 源码：`D:/LoveBrain`，分支 `closeout-20261106`（HEAD 40c1f12，版本 1.4.2），只读。
- 数据源：`gh api repos/bystery/LoveBrain/issues/{1,3,5,6}` 及各自评论（走代理 127.0.0.1:10808）。
- 结论汇总：

| Issue | 主题 | 分类 | 一句话依据 |
|---|---|---|---|
| #1 | AI 连接失败 | **VERIFY_CLOSE** | endpoint 自动探测已实现（DeepSeekRepository.kt:429）；楼主已确认解决 |
| #5 | 自定义超时 | **VERIFY_CLOSE** | GenerationTimeoutTier 60/120/180/300 四档已实现（AppConfig.kt:117） |
| #3 | 表情包识别 + 悬浮窗透明度/大小/卡片排列 | **部分 VERIFY_CLOSE / 表情包 DEFER** | 透明度、球径三档、卡片纵向排列均在码；表情包/视觉链路完全没有 |
| #6 | 历史/画像迁移 + 云盘/WebDAV | **DEFER** | 仅有知识库 zip 导出/导入与本地备份；无跨账号迁移、无云盘、无 WebDAV |

---

## #1 「请问ai连接一直失败换了很多个ai都不行了」（2026-09-04，open）

**根因（评论区已确认）**：旧版要求 Base URL 手动补全 `/v1/chat/completions`，用户填服务商根地址即失败。楼主 os2529271 回复"已解决"；bystery 补充新版已支持 Base URL 自动探测。

**代码证据（当前分支）**：
- `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:429-459` — `testConnectionWithProbe`："带 endpoint 自动探测的连接测试（保存供应商 + 测试连接按钮共用）"，用 `OpenAiChatEndpointResolver.candidates(normalizedBase)` 生成候选 URL 列表逐个探测，成功地址回填 `ProviderTicket.baseUrl`。
- 同文件 :452-453 — `configResolver.normalizeBaseUrl` 含脏数据拦截 + `HttpsTrustGuard.enforce`。
- `data/OpenAiChatWire.kt:143,154` — 连通性探测走极小请求体（"hi"、8 token 上限）。
- 失败归因：`data/CancellableHttpTransport.kt:56` 携带 HTTP 状态码区分 404/405。

**判定**：已实现且用户侧已确认。**VERIFY_CLOSE**：真机用新版重试一次连接即可关单。

## #5 「关于超时」（2026-09-26，open）

**需求**：非官方 API 生成慢，固定超时导致超时报错，希望可自定义。bystery 已回复可选 60/120/180/300 秒。

**代码证据**：
- `app/src/main/java/com/lovebrain/app/AppConfig.kt:117-145` — `enum class GenerationTimeoutTier { SEC_60, SEC_120, SEC_180, SEC_300 }`，注释明言动机即"非官方兼容服务的速度与内容长度都超过固定 120 秒"（:93）。
- :100-104 — 主回复、谈心、主动开场三条生成链路的 `withTimeout` 共吃同一份 `ProviderRequestConfig.generateTimeoutMs`；流式 SSE 读超时 `STREAM_READ_TIMEOUT_SEC` 跟同一档走（防 300s 总档被 OkHttp 120s 掐断）。
- :105-106、:114 — 连接/写超时保持 15s 快速失败；档位挂在每张工单 `ProviderTicket.generateTimeoutSec`（每工单可调，非全局唯一）。
- 任意秒数未支持——与维护者回复口径一致，属已登记的边界而非缺陷。

**判定**：**VERIFY_CLOSE**。真机验证设置页四档切换生效（尤其 300s 档下 SSE 不断流）即可关单。

## #3 「功能需求」（2026-09-25，open）

需求两块 + 评论追加三项：

1. **表情包识别** — **DEFER（本版不做）**。代码核实：`service/CopyCaptureService.kt`（534 行，无障碍长按捕获）整条链路只取 `AccessibilityNodeInfo` 文本/剪贴板文本，全文无 sticker/图片/Bitmap 处理；全仓 `com/lovebrain/app` 无 `image_url`、无视觉模型调用、无多模态请求构造（`OpenAiChatWire.kt` 仅构造文本 chat 请求体）。表情包在微信等 App 里对无障碍常不可读，需要截图+视觉模型的全新链路，属新功能开发，不是缺陷。维护者评论已如实承认"表情包识别暂时还没支持"。
2. **悬浮窗透明度** — **已实现**：`AppConfig.kt:150-235` `PanelBackgroundOpacity`（面板背景不透明度 20%–100%，即最大透明度 80%，设置页滑杆；低档位有刻意的可点性下限）。
3. **悬浮图标大小** — **已实现**：`AppConfig.kt:306+` `BubbleSizeTier`（用户可设三档球径，`SecurePrefs.bubbleSizeDp` 落盘，`FloatingService.kt:312` 档位驱动窗口/球径/角标/吸边）。
4. **评论追加：悬浮窗内关闭、回复卡片上下滚动** — **已实现**：`LoveBrainViewModel.kt:161,589` 回复卡片排列方向（纵向/横向，默认纵向 = 用户要的上下滚动）；`ui/panel/LoveBrainPanelScreen.kt:215,300`、`ui/panel/reply/ResultArea.kt:135,724` 纵向档实装。维护者评论确认上述三项进 1.4.1，当前分支已 1.4.2。

**判定**：透明度/大小/卡片排列 → **VERIFY_CLOSE**（真机过一遍设置页）；表情包识别 → **DEFER**，登记为新功能方向（依赖视觉模型接入与截图捕获链路，工作量独立）。

## #6 「历史与画像迁移/云盘/WebDAV」（2026-10-24，open，无评论）

**需求**：跨账号迁移会话历史与用户画像；云盘存储；WebDAV 导入导出。

**代码核实**：
- 已有的近似能力：`data/KbArchiveTransfer.kt:16`（知识库归档 zip 导出/导入唯一实现）+ `viewmodel/KnowledgeBaseViewModel.kt:430-445`（SAF 选目标导出，用户可手动把 zip 放网盘）；`data/KnowledgeBackupService.kt`（本地备份）。
- 缺失：无 WebDAV 客户端、无云盘直连、无会话历史+画像的打包迁移、无多账号概念（存储单用户）。

**判定**：**DEFER（本版不做，仅登记范围）**。按任务边界不改、不排期；未来若做，KbArchiveTransfer 的 zip 端口是现成挂载点，但会话历史/画像导出格式与账号模型需先设计。

---

## 执行清单（供收口批次）

- VERIFY_CLOSE 动作集中在真机冒烟：① 新版填根地址 Base URL 连接成功；② 超时四档切换 + 300s 长生成不断流；③ 面板透明度滑杆、球径三档、卡片纵向排列生效。验证通过即回帖关 #1/#5，#3 部分关（表情包单独留跟踪）。
- 无 BUG_FIX 项：四个 issue 均未发现当前分支可确认的代码缺陷。
- DEFER 项：#3 表情包（新捕获+视觉链路）、#6 迁移/云盘/WebDAV。
