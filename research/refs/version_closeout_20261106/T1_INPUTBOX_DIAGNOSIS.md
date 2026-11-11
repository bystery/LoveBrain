# T1 输入框复制/粘贴/选择/全选不稳定 — 根因诊断与修复（closeout-20261106）

状态：**代码修复已落，待真机验证**。本报告所有"已修复"均指"代码层已实施并通过编译"，未在任何真机上复测，验收以第六节清单为准。执行轨：T1（2026-11-06）。

## 一、改动文件

| 文件 | 对应根因 |
|---|---|
| `D:/LoveBrain/app/src/main/java/com/lovebrain/app/ui/common/OverlayTextToolbar.kt` | H1（+H2 配套 isShowing） |
| `D:/LoveBrain/app/src/main/java/com/lovebrain/app/service/OverlayPanelWindow.kt` | H2、H4 |
| `D:/LoveBrain/app/src/main/java/com/lovebrain/app/service/FloatingService.kt` | H2（探针注册） |
| `D:/LoveBrain/app/src/main/java/com/lovebrain/app/ui/panel/reply/ReplyInput.kt` | H3 |
| `D:/LoveBrain/app/src/main/java/com/lovebrain/app/ui/panel/counseling/CounselingPanel.kt` | H3（主输入+追问两处） |

未动整体 UI 结构、未动评分/考场；无任何"加延迟掩盖"式改动。

## 二、根因假设（按可能性排序，带 file:line 证据）

主结构背景：输入框是悬浮窗（TYPE_APPLICATION_OVERLAY）里的 Compose `BasicTextField`；长按菜单是自绘 `Popup`（`OverlayTextToolbar.kt:98`）。**Compose Popup 是挂在面板窗口 token 下的独立子窗口**——下面 H1/H2 都源于这个事实。

### H1（最高）菜单弹出后立刻消失：Popup 外点收起在 overlay 场景不可靠
- 证据：原 `OverlayTextToolbar.kt:103-109` `dismissOnClickOutside = true`。该实现依赖菜单子窗口收到 `ACTION_OUTSIDE`；但打开菜单的那次长按手势**还没结束**（手指仍按在面板窗口上），部分机型/dispatcher 版本会把这次手势的后续事件判为菜单窗口之外 → 菜单**当场**收到 OUTSIDE 收起 → 用户看到"闪一下就没了"。
- 这一条与症状"长按菜单闪一下消失"逐字吻合，列为首位。

### H2（高）菜单/选区手柄伸到面板 frame 之外 → 面板收到 ACTION_OUTSIDE → releaseInput 拆掉选区
- 证据：菜单固定抬高 44dp（`OverlayTextToolbar.kt:100` `rect.top - liftPx`），选区手柄贴输入框两端；面板只有 260–350dp 宽（`AppConfig.PANEL_MIN/DEFAULT/MAX_W`），输入框常在面板上/左/右边缘，菜单与手柄的子窗口必然部分越出面板窗口 frame。
- 原 `OverlayPanelWindow.kt:162-171`：面板窗口（`FLAG_WATCH_OUTSIDE_TOUCH`）收到 `ACTION_OUTSIDE` 即无条件 `releaseInput` → 清 Compose 焦点 → 选区清空 → 菜单收起。于是：触碰越界部分的菜单/手柄 = **当场拆掉正在操作的对象** → "菜单闪一下消失""手柄拖不动""点复制没反应"（click 的 DOWN 触发 OUTSIDE，UP 到达时选区已空，onCopy 空转）。

### H3（中）输入意图 level-triggered：手势中反复翻窗口 flags，打断手势流
- 证据：原 `ReplyInput.kt:278-287`、`CounselingPanel.kt:89-98` 与 `351-360` 都是 `while(true){ if (any { pressed }) onInputIntent() }`——**每个 pressed 事件**（含 MOVE）都上报；`onInputIntent → PanelInputFocusOwner.setMode → applyFlags → host.updateLayout`（`PanelInputFocusOwner.kt:106-119`、`OverlayPanelWindow.kt:122-127`）。窗口 flags（NOT_FOCUSABLE↔可聚焦）在手势进行中反复 `updateViewLayout`，在部分 ROM 上手势流会被 ACTION_CANCEL 打断 → 长按/选区"有时无法操作"、首次点击偶发不灵。首次按下那次翻转（PASSIVE→EDITING）是必须的，但不应逐事件重复。

### H4（中低）临时隐藏不释放输入 → 菜单 Popup 孤儿残留 + EDITING 泄漏
- 证据：原 `OverlayPanelWindow.hideInstantly`（TEMP_HIDDEN 路径）只 `visibility = GONE`，无 releaseInput（对比 `fadeOut:249` 有"兜底焦点清理"）。菜单 Popup 是**独立子窗口**，`GONE` 收不掉它 → 长按菜单开着时临时隐藏，一枚孤儿菜单留在宿主 App 上；同时 EDITING 状态泄漏到下次打开。这也是"闪退"假设（H6）的最大来源之一。

### H5（低，登记未改）Compose 焦点与窗口焦点两本账
- `present()` 强制 PASSIVE 但不清 Compose 焦点（`OverlayPanelWindow.kt:222` 注释即"打开即不抢焦点"）。若上游泄漏（H4）未堵住，打开后字段持 Compose 焦点而窗口 NOT_FOCUSABLE，长按行为未定义。H4 修复后该路径关闭，本轮不另改。

### H6（低，未复现）闪退
- 无崩溃日志。最可能是 Popup 子窗口在面板隐藏/动画/销毁竞态下的 WindowManager 异常（BadTokenException / View not attached）。H4 修复消除了"窗口已 GONE 而菜单子窗口仍挂着"的最大来源；**待真机日志确认**，不宣称已消灭。

## 三、已实施修复（逐条对应根因）

1. **H1** `OverlayTextToolbar.kt`：`dismissOnClickOutside = false`；收起改走两条确定性通道——
   - `OverlayTextToolbarHost` 根 Box 增加**只观察不消费**的按压观察器（`awaitEachGesture + awaitFirstDown`）：面板内任何**新手势**按下且菜单在显示 → `toolbar.hide()`。菜单项/选区手柄的触摸在 Popup 子窗口里，到不了这层，不会误收；打开菜单的那次长按手势只余 MOVE/UP（DOWN 发生在菜单出现前），不会刚开就收。
   - 面板外触摸/失焦 → 输入框失焦后 Compose 自行走 `textToolbar.hide()`（既有焦点链，未改）。
   - 返回键收起保留（`dismissOnBackPress = true`，onDismissRequest 仍在）。
2. **H2** `OverlayPanelWindow.kt`：新增 `registerTextToolbarShowingProbe`；`ACTION_OUTSIDE` 处理改为 `EDITING && !isTextToolbarShowing()` 才 `releaseInput`——菜单显示期间外点不拆选区，菜单收起后恢复原判定。`FloatingService.kt` `PanelContent` 用 `DisposableEffect` 注册/注销探针（事实源 = `overlayToolbar.isShowing`）。已知取舍（有意为之，非掩盖）：菜单开着时触碰宿主 App 不再立即退编辑——面板本就"不关、只退编辑"，菜单生命周期以秒计，风险小于误拆正在操作的选区。
3. **H3** `ReplyInput.kt` / `CounselingPanel.kt`（主输入+追问共三处）：`onInputIntent` 改**边沿触发**——`awaitEachGesture { awaitFirstDown(requireUnconsumed = false); onInputIntent() }`，每次手势只在按下那一跳上报一次。进入编辑一次即够；窗口 flags 不再随每个 pressed 事件抖动。
4. **H4** `OverlayPanelWindow.hideInstantly`：先 `focus.releaseInput("hide_instant")` 再隐藏（与 `fadeOut` 同一道兜底），堵住孤儿菜单与 EDITING 泄漏。

## 四、编译验证

- 验证方式：临时 git worktree（HEAD + 仅本任务 5 个文件的 hunks）`compileDebugKotlin --offline` → **BUILD SUCCESSFUL**（仅存量参数名 warning，非本次引入）。
- 主仓当前整仓编译被 **T2 轨进行中的** `feature/composer/BatchTranscriptParser.kt`（未跟踪新文件，155/159 行语法/类型错误）阻塞——该文件非本任务改动，未触碰；T2 收尾后主仓整仓编译以 T7 为准。

## 五、无法验证项

- 全部真机行为（见下节）：**待真机验证**，不得按"已修复完成"关闭本 P0。
- H6 闪退：需真机崩溃日志（`adb logcat` AndroidRuntime 段）才能定案。

## 六、真机验收清单（用户执行，逐项勾选）

前置：安装 closeout-20261106 分支新构建（含本次 5 文件改动）。回复页与谈心页**两个输入框都要测**（谈心页含主输入与追问输入两颗）。

1. 长按菜单稳定出现：输入框内长按 ≥3 次 ×（回复页 / 谈心页主输入 / 谈心页追问），菜单每次都出现且**松手后不消失**。
2. 拖动手柄：出现选区后拖左右手柄扩/缩选区，包括输入框贴面板左端、右端、顶部三个位置（验证 H2 越界场景）。
3. 复制：选中后点"复制"→ 粘贴到微信输入框，内容正确（含面板边缘位置的菜单）。
4. 剪切：选中后点"剪切"→ 文本移入剪贴板且输入框内被删。
5. 粘贴：复制一段微信文本 → 面板输入框长按 → 粘贴成功。
6. 全选：输入 >1 行文本 → 全选 → 再复制/剪切正确。
7. 点外收起：菜单开着 → 点面板内其他位置，菜单收起且不误触其他控件；再长按可复现菜单。
8. 返回键收起：菜单开着按返回 → 菜单收起，面板不关。
9. 关闭面板后宿主正常：面板收起（含菜单开着时收起）→ 微信输入框点一下可正常打字、键盘正常弹出（焦点/IME 已交回）。
10. 临时隐藏：菜单开着 → 触发临时隐藏（球）→ **屏上无残留菜单** → 恢复面板后输入行为正常（验证 H4）。
11. 设置页往返：输入框获焦 → 开设置页 → 关闭 → 回到输入框长按菜单正常（既有 handOffInput 链不回归）。
12. 若出现闪退：导出 `adb logcat -b crash` 交回，按 H6 复核。
