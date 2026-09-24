# LoveBrain 交接：下一窗口开工单（2026-09-25，CI 首跑之后那两格做完）

> 要做的事仍只有一件：**严格按 `LoveBrain_Three_Phase_Reaudit_and_Six_Principles_UI_Architecture_Guide_c0ff0415_2026-09-24.md` 继续**。
> 账本：`LoveBrain_Guide_Item_by_Item_Verification_2026-09-24.md` 末尾「追加：接手自 `3d92488` 的那一轮」
> （§10.0–§10.5，三态标注）· 过程记录：`LoveBrain_Three_Phase_Execution_Log_c0ff041_guide_2026-09-24.md`
> 上一份开工单：`LoveBrain_Handover_Next_Window_2026-09-24.md`（它的 §2.1/§2.2 已由本轮做完，其余仍有效）

## 0. 一句话现状

**本地领先远端一截、一个都没推**（远端仍是 `3d92488`；条数现算：
`git rev-list --count 3d92488..HEAD`——本轮最后一次量是 9 笔，之后还会加文档提交）。
本轮把上一份交接单里"真正卡着的两格"做完了本机能做的部分：

- **§2.1 lint 平台差**：定位清楚并改掉口径。差的**不是 1 条规则而是 2 条**（`GradleDependency` CI=10/本机=1、
  `OldTargetApi` CI=1/本机=0），而且这两条的数**既不随平台稳定也不随时间稳定**，所以既没抬预算到 81、
  也没"按平台各锁一份"，改成不进预算但每次打印条数与理由（降级白名单写死在脚本里，仓库自己的债不许降级）。
  挑出去之后进预算的部分两边**逐条相等：70 条 / 14 规则**（`eb8ac9a` 当时；本轮之后因又还掉一格
  `UnusedResources` 降到 69，见 §1 的现在值——两个数都对，只是量的时刻不同）。
- **§2.2 那 23 条失败**：全部归完类，**12 夹具文案 / 7 夹具点击时机 / 3 夹具 payload / 1 未决**，
  四类各自一笔提交；没有一条是把断言改软。

`verify` 与 `ui-test` 会不会因此转绿，**只有推上去跑同一 SHA 才知道**——本机没有 system image。

## 1. 起手必查（照抄，别凭记忆）

```bash
git fetch origin && git rev-parse --short HEAD && git rev-list --count FETCH_HEAD..HEAD
gh run list --limit 3
# 若已推送，读同一 SHA 的三项与产物：
gh run view <run-id> --json jobs --jq '.jobs[] | .name + " | " + (.conclusion // "?") + " | " + (.steps | map(select(.conclusion=="failure") | .name) | join(" ; "))'
gh run download <run-id> -D _temp/ci-<run-id>
bash scripts/test_check_lint_budget.sh            # 判据自测，本机 27 格
PYTHON=python bash scripts/check_lint_budget.sh   # Windows 上必须给 PYTHON=python
```

本轮实测基线（`6eea6eb`）：**1091 单测 / 137 套件 / 0 失败 / 0 错误 / 0 跳过 / 陈旧 XML 0**；
lint 报告 70 条 / 15 规则，其中**进预算 69 条 / 14 规则**（账本已 `UnusedResources 34→33`）、
advisory 1 条；`:app:compileDebugAndroidTestKotlin` rc=0。
跨层 6 条 / 取消审计 / 工单编号 / prompt 零 diff / 大文件计数 **本轮未重跑**，沿用上轮未复验。

## 2. 上一份交接单里被本轮证伪的四条（别再当依据）

1. 「远端已经是 `3d92488`，没有未推送提交」——`616cfd5`（纯文档）与本轮 6 笔都没推。
2. 「`ui-test` 产物里 `home.png`/`knowledge-base.png` 都产出了」——该 run 的 5 个产物里**一张 PNG 都没有**。
   截图那一格仍是 0%，别当已交付。
3. 「§2.1 二选一：真问题→改代码 ／ 平台产物→按平台各自登记」——两个前提都不成立，见上面 §0。
4. 「本机 lint 71 条 / 15 规则」——现在报的是 70 条 / 15 规则，其中进预算 69 / 14；
   口径变了不是债变多，比较前先看清 `STATS` 那行。

## 3. 只剩"推送 + 读 CI"能闭的（本轮新留下）

- `verify` 是否转绿：本轮之后 lint 那一步两侧同尺了；后面还有 R8/APK 元数据、SBOM、
  费用 dry-run、egress 证据四件**从没跑到过**，第一次跑到可能再爆新问题。
- `ui-test`：改过的 22 格（12+7+3）是否真绿；`lateCallbacks` 那格现在会自己报
  "R1 有没有出门 / R2 累计几次"，红也要红得能读出原因。
- `upgrade-test`：needs `[verify, ui-test]`，前两个不绿它永远 skipped。
- 2 格 Service destroy 仍是 `Assume` 跳过（算 skipped 不算通过）。

## 4. 下一格建议顺序（本机就能做的那批，B 类）

1. **`FloatingService` 那颗输入行的可点节点没有标签**——本轮逐点数入口时量到：
   带 `EditableText`、无文案无 `contentDescription`，读屏念不出这是什么输入框（§6.5 第②栏）。
   改生产码，测试形状现成（`ComposerAddButtonGatingTest` 已经在数这些节点）。
2. **§5.3 document 一格**（+ catalog 写侧）：形状照 `5a21ec2` / `df1e802`；
   不 new Mutex、不搬 CoroutineScope（`SingleOwnerContractTest` 会拦）。别拿
   `applyProfileUpdateAtomically` 开第一刀。
3. **§6.3 知识库页接四态**：范例已有两份（反馈页 `e359930`、供应商页 `d902514`→`80bc78e`）。
4. **§5.1 `core/testing` 归位**：本轮新增 `app/src/androidTest/…/testing/UiText.kt`，
   于是同一判据的夹具文本在两个测试源集各存一份（`ReplyPayloadShapeForUiFixtureTest` ↔
   `ResultAreaInteractionTest`），改一处必须改两处——这就是 §5.1 那张目录图要解决的。
5. §6.1 剩 9 颗 `Lb*` 组件 + token 从 `ui.theme` 迁进 `core/designsystem`。
6. §6.5 截图工具仍**故意没接**：理由未变（§6.1–§6.4 铺开前拍的 baseline 会整批作废）。

## 5. 别重复劳动：本轮做的 6 笔

| 提交 | 内容 |
|---|---|
| `eb8ac9a` | lint 账本分「进预算 / advisory」两类；输出自报这把尺；27→28 格判据自测 + 两份真产物夹具；接进 verify |
| `1be17b8` | `ComposerAddButtonGatingTest`：把"点了没反应"的机理钉成 JVM 合同（含变异检查） |
| `47bbc13` | ResultArea 夹具改用 `parseProviderText`；helper 改名 `successWithOnlyRecommendedReply`；生产解析器正反两格实测 |
| `f76f64a` | 判据自测改为**从账本现读条数**（还一条债不再引发 5 格假红）；夹具断言换成"两侧逐条相等 + CI 独有规则全在 advisory" |
| `555ca48` | `UiText`（当前配置 / `inTag` / 由模板拼停止棒正则）；12 处写死中文的 finder 改资源驱动；中英文两侧各一条 parity 断言（设备 + JVM）；`UnusedResources 34→33` |
| `6eea6eb` | `awaitAddEntryActionable`：推帧推到 ➕ 真带点击语义再点；R1/R2 那句 `requestCount` 断言修对（旧的 `>=1` 从来没断过 R2） |

可复用的新零件：`UiText.current(id, vararg)`（设备当前配置下生产会渲染的那句）、
`UiText.inTag("zh"|"en", id)`（盯回落）、`UiText.generatingBarPattern()`（生成中停止棒整串匹配）、
`GENERATE_STOP_TEST_TAG`（生产留的锚点，"文字会变，tag 不会"）。
**新用例取文案一律走这些，别再抄一份中文字面量。**

## 6. 坑表（本轮新增 5 条，接上一份的 15 条之后）

16. **`python -` 读 heredoc 按 ANSI 码页解码**：正则里的中文自己先坏（"unterminated character set"）。
    写成文件再执行，或用 `\u` 转义；`PYTHONUTF8=1` 救不了这条。
17. **`sed` 的模式里带 `\n` 永远不命中而退出码仍是 0**：我差点把一份没动过手脚的夹具当成
    "变异检查通过"。做变异前先断言"手脚确实动了"（`assert head in t`）。
18. **历史产物夹具不要断言"退出码 0 / 不许出现 OVER"**：账本合法变小之后，旧快照必然"超"。
    要钉的是不变量（两边逐条相等），不是当时的数。
19. **`UnusedResources` 把 androidTest 的引用也算"已使用"**：改测试就能让这条尺少一格。
    它证明不了文案在生产路径可达。
20. **`git status` 显示 ` M` 而 `git diff` 为空**：本轮 `values-en/strings.xml` 被编辑器写成 CRLF，
    还原后 `git rev-parse HEAD:<f>` 与 `git hash-object <f>` 同 hash——纯 stat 脏，别为此"清理"工作树。
21. **`package_deps_report.sh` 缺 python 可用性探针**：Windows 不带 `PYTHON=python` 直接 exit 49，
    看着像"判失败了"其实是根本没跑。`check_lint_budget.sh` 有那个探针（缺解释器给 CANNOT-VERIFY=2），
    同族脚本里这是缺口，下次动 scripts/ 时补上。

## 7. 硬约束（一条没变）

不许改 prompt 内容（`git diff --exit-code 286c9406..HEAD -- app/src/main/assets/engine` 必须零差异）；
一个提交一个验收目标、message 写用户行为；先写会红的回归测试，且新断言必须被坏实现打破过；
不许用源码 grep 顶替 UI/触摸/无障碍测试；不许用"脚本存在"代替产物；不许新增 `|| true` / `continue-on-error`；
**不许为了让 CI 绿而调软断言或抬预算**（本轮 lint 那格走的是"分类 + 白名单 + 理由必填"，
`check_lint_budget.sh` 里 `ADVISORY-FORBIDDEN` 就是防有人借这条路给真债免检）；
文档里的数一律来自当次命令输出；不为行数机械拆文件；**发布要独立复核签字，worker 不自签**；
**推送需要用户明确说「推送」**。

## 8. 发布判定

**NO-GO。** 判据是指导书 §10：新 SHA 的三项 required checks 全绿且 artifacts 齐全。
本轮结束时的最后一笔代码提交从没上过 CI（笔笔现算：`git log --oneline 3d92488..HEAD`），
`ui-test` 那 23 条的判决仍来自 `3d92488`。
本窗口不签 PASS，下一窗口在拿到同一 SHA 的三项结果之前也不签。
