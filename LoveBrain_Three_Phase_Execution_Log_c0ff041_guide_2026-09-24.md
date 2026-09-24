# LoveBrain 三阶段再复核指导书 — 执行记录（2026-09-24）

> 输入指导书：`LoveBrain_Three_Phase_Reaudit_and_Six_Principles_UI_Architecture_Guide_c0ff0415_2026-09-24.md`
> 被审提交 `c0ff0415`；本轮起点 `4795471`（上一窗口的最后一个提交，它已停手并留了
> `LoveBrain_Handover_CI_Evidence_2026-09-24.md`）。
> 本轮 HEAD：`a74cc8a` 之后接本文件的提交（**全部只在本地、未推送**；
> 阶段一的 P0-03/04/05 + 阶段二的 ports/棘轮/死 API 都在里面）。
> 本轮没有改 prompt：`git diff --exit-code 286c9406..HEAD -- app/src/main/assets/engine` → 零差异。

## 0. 一句话状态

阶段一的 P0-03 / P0-04 / P0-05 已落地并本机验证；P0-01 / P0-02 的**真机那一半卡在
19 条 instrumentation 真失败**上，本机没有 system image，只有 CI 能出证据，所以要推送。
阶段二只做完了"包边界棘轮 + 死 API + Koin 唯一 journal + 冻结注释改口"；
feature store 拆分、Repository 按能力拆分、ports + contract test **没做**。
阶段三（设计系统 10 个组件、截图矩阵）**没开始**。

## 1. 逐项进度表

| # | 指导书条目 | 状态 | 提交 | 证据 |
|---|---|---|---|---|
| 1 | P0-01 CI 总红灯 | **部分**：verify 已全绿（上一窗口） | 前置 `2b01f21`/`f5328b7`/`8cb9819` | run 35944822268 的 verify 26 步全 ✓（含 R8/APK metadata/SBOM/费用 dry-run）；本轮新增的 egress 步骤未跑过 CI |
| 2 | P0-01「AndroidTest 缺 import」 | **不成立**（复核误判） | — | `:app:compileDebugAndroidTestKotlin` 本机 + CI 都 exit 0；`assertDoesNotExist`/`onAllNodes` 是成员函数，不需要 import |
| 3 | P0-02 生成不崩溃的 10 格真链路 | **没证成**：夹具竞态修了 + 取证加了，结果待 CI | `30d313e`, `dd5282e` | run 35943906234：40 执行 / 19 失败 / 2 跳过；本轮把 7 条同源于"只推一帧"的夹具改为等条件，并把 29 处可见性断言换成带几何量的诊断 |
| 4 | P0-03 未来 schema 写保护可绕过 | **完成** | `1658356` | 新增 `KnowledgeTx`/`transaction`/`WriteResult` + 唯一落盘出口按路径归属拒绝；`ReadOnlySchemaWriteGateTest` 5 格，24 个公开写入口跑完目录树逐字节不变 |
| 5 | P0-03 读入口不走 safeKbFile | **完成** | `1658356` | 同上文件的越界读测试（`../`、`./../`、`moment/../../`、绝对路径） |
| 6 | P0-04 抓包脚本 `|*) continue` 假阴性 | **完成（本机不可判的两格除外）** | `41d95e9` | 通配兜底删掉；5 个合成 pcap + 六格自测；本机无 tshark → 4 格判不了，脚本 exit 2 并写明 CANNOT-VERIFY |
| 7 | P0-05 WAL 锁内二次检查 + AlreadyCommitted | **完成** | `06cfc4c` | `commit()` 锁内重读、`recover()` 同源；`two coroutines committing the same round write it once` |
| 8 | P0-05 水位失败注入 | **完成** | `06cfc4c` | `an effect that landed but lost its watermark runs again on recovery` 显式要求 scene 的 effect 跑第二次 |
| 9 | P0-05 文档改口 at-least-once | **完成** | `06cfc4c` | 类 KDoc、`Tx.apply` 注释、测试类头、`docs/ARCHITECTURE.md` §2.5.1（该文档按 .gitignore 不入库） |
| 10 | P1-01 18 个 >500 行文件 | **未做**，且 KnowledgeRepository 变长 | — | 实扫：114 个生产 Kotlin 文件 / 32,673 行 / >500 行 18 个 / >800 行 10 个；KnowledgeRepository 1857 → 2000（加写边界与 Tx） |
| 11 | P1-02 用源码 grep 代替触摸边界 | **未做**（要设备） | — | 语义树 `boundsInRoot` ≥48dp 的断言还没写 |
| 12 | P1-03 GenerationActionButton 重复 modifier 链 | **完成** | `dd5282e` | 只剩一条 size→graphics→shadow→clip→background→clickable→padding |
| 13 | P1-04 Koin 双 journal | **完成** | `845330a` | `single { TopicRecorder(get(), get()) }` + 身份断言；改回旧写法 → 该格 RED（实测） |
| 14 | P1-05 文案未收口 | **未做** | — | 见 §3 |
| 15 | §3.3 fingerprint 死 API | **完成** | `845330a` | 删除，全仓 0 调用者；真正在用的只有 `GenerationFingerprints.inputOf` |
| 16 | §3.3 prompt「冻结」说法不实 | **完成（选了"改口"分支）** | `845330a` | Engine 注释改成"hash 只做诊断，本轮用的是现读文本"；`PreparedPrompt` 整体冻结没做 |
| 17 | §3.3 验收包与实现不符的两句话 | **部分** | — | "仍用冻结 prompt"已改口；"全部写路径统一拒绝"现在**变成真的**了，但验收包文档还没补这一节的实测数 |
| 18 | §7 第二步 4：包依赖测试 | **完成** | `a9bb9b1` | 棘轮 + `scripts/package_deps_report.sh`；存量 15 条 / 11 文件已登记；四格反向验证 |
| 19 | §7 第二步 1/2/3（ports、feature store、Repository 拆分） | **未做** | — | 见 §3 |
| 20 | §7 第三步（设计系统 + 截图矩阵） | **未开始** | — | 见 §3 |

## 2. 本机门禁（一次跑完的实测，全部来自当次命令输出）

```
:app:testDebugUnitTest              941 tests / 113 suites / 0 失败 / 0 跳过 / 0 泄漏异常
:app:lintDebug                      72 issues，Error+Fatal 0
:app:compileDebugAndroidTestKotlin  BUILD SUCCESSFUL
scripts/audit_cancellation.py       站点 167：PROTECTED=53 WAIVED=2 SUSPEND-FREE=112 NEEDS_REVIEW=0 → PASS
scripts/strip_ticket_ids.py         PASS（生产注释/代码/字面量 0 处工单编号）
scripts/asset_hashes.sh --check     OK 6dcde732fab602813559370dd6af3b774ca24fda86ce28f2c0cfd88a8be95831
git diff 286c9406..HEAD -- assets/engine   零差异
scripts/suggest_cost_baseline.sh --self-test  all checks passed
scripts/test_verify_network_egress.sh        exit 2 = CANNOT-VERIFY（本机没有 tshark，见 §3）
```

## 2b. 阶段二第二轮推进（本机验证，提交 `2dd94c0`/`ab7457a`/`a74cc8a`）

| §7 第二步条目 | 状态 | 证据 |
|---|---|---|
| 1 建 ports 与 contract tests，先包住 concrete repositories | **知识侧 + provider 侧都完成** | `KnowledgeReadPort` / `KnowledgeWritePort` / `KnowledgePort` / `AiGateway`；合同测试一份跑两侧：知识 8 格 ×2=16 例、网关 4 格 ×2=8 例。反向验证：把 fake 的只读判定写死 false → 那一格立即红；Koin 里把端口绑定写成 `get()` → 三条图测试当场 StackOverflowError |
| 4 package dependency test | 完成 | 棘轮实测 **15 → 10 → 6** 条越界；数字来源 `scripts/package_deps_report.sh` |
| 5 清死 API / 重复 journal / 重复 modifier / 历史描述性注释 | 前三项完成，注释只清到自己踩到的那处 | `fingerprint()` 删除；`TopicRecorder(get(), get())` + 身份断言；GenerationActionButton 单链；Engine「仍用冻结 prompt」改口 |
| 2 五个 feature store | **没做** | 见 §3 第 4 条 |
| 3 KnowledgeRepository 按能力拆 | **没做** | 见 §3 第 4 条 |

§7 第二步「完成定义」逐条对照，不粉饰：

- 「LoveBrainViewModel 不再持有五条 feature 的内部状态」→ **未达成**，VM 体量没动。
- 「KnowledgeRepository 不再是所有知识能力的唯一入口」→ **部分**：domain 已全部走端口，
  写只有 `transaction`/`KnowledgeTx` 一条路；但仓库对象自身仍是所有能力的唯一实现处，拆类未做。
- 「每个 port 有 production/fake 共用 contract suite」→ `KnowledgePort`、`AiGateway` 达成；
  **`Clock` 端口未建**（复核 §4 的 DIP 目标列了它）。
- 「新增 feature 不修改已有 reducer」→ **无法验证**：reducer 还没抽出来。
- 「新代码无 >500 行文件，现存 >800 行文件数量持续下降」→ 前半达成（本轮新增 8 个文件最大 188 行）；
  **后半未达成**：>500 行仍是 18 个、>800 行仍是 10 个，与被审提交测到的一样，
  而 KnowledgeRepository 因加写边界从 1857 涨到 2004。这一格记未达成，不写「方向正确」。

本轮收尾实扫：117 个生产 Kotlin 文件 / 32,807 行；
966 单测 / 117 套件 / 0 失败 0 跳过 / 0 泄漏异常；lint 72 issues 0 error；
androidTest 编译通过；取消审计与工单编号 PASS；prompt 目录零 diff。

一处自伤要认：`ab7457a` 里我用脚本改两个测试文件，把它们的行尾从 LF 翻成 CRLF，
一个加 2 行 import 的提交报了 796 行 diff。`a74cc8a` 已复位
（相对损伤前净差异 3 insertions / 1 deletion），损伤留在历史里没改。

## 3. 明确没做到 / 没法在本机做到的（不混进上面）

1. **19 条真机 instrumentation 失败还在**。本轮只做到：把 7 条同源的夹具竞态改掉、
   给 29 处可见性断言加几何诊断、把重复 modifier 链清掉。是不是转绿要等同一 SHA 的
   `ui-test` 产物；没看到 XML + logcat + 真实 requestCount 之前，
   "点击生成回复不崩溃"仍然不许写成已修复。
2. **egress 六格里有 4 格本机判不了**（没有 tshark）。CI 里 `apt-get install tshark` 之后
   才算真判过；脚本没有"没装就跳过"的分支，装不上就是红。
3. **Service destroy 那两格是 Assume 主动跳过的**（instrumentation 起不了悬浮窗/FGS）。
   报告里算 skipped，不算通过。
4. **阶段二剩下的两块硬骨头没做**：①Reply/Suggest/Proactive/Counseling/Rewrite 五个
   feature store（§5.2 的迁移顺序与统一 store 形状）；②KnowledgeRepository 按
   catalog/document/profile/memory/migration/archive/round 拆（§5.3，且必须共用一个
   `KnowledgeTransactionManager`，不许每个新类各自 new Mutex）。
   端口层已铺好（domain 不再 import data，越界 15→6），所以这两块现在是"往上搬"，
   不必边拆边补依赖。§4 目标结构里的 `Clock` 端口、§5.1 的 `core/designsystem` /
   `core/testing` 目录也都还没建。
   仍然要认的一条：本轮让 KnowledgeRepository 从 1857 涨到 2004 行，
   写边界是必要的，但它同时成了"最大的一次性改动"和"最长文件之一"，拆类必须紧跟。
5. **阶段三完全没开始**：设计 token + `Lb*` 基础组件、Home/Usage/Provider/Feedback 重写、
   Panel/ResultArea 的 modal host、320/360/412/600dp × 1.0/1.3/2.0 字体 × 中英文的截图矩阵。
   截图工具（Roborazzi 或 Paparazzi 二选一）也还没接。
6. P1-05 文案收口没动：本轮实测复现复核的两个数——
   `Text("中文…")` 101 处、`contentDescription = "中文…"` 10 处（`grep -oE` 扫 app/src/main），
   与复核测到的值一致，因为这轮一行文案都没改。§6.1 要求的
   "production composable 禁止新增直接用户可见字面量"目前没有任何门禁在管。

## 4. 脚本索引（本轮新增/改动的可执行件）

| 脚本 | 用途 | 怎么跑 |
|---|---|---|
| `scripts/test_verify_network_egress.sh` | 抓包判据的六格正反自测 | `bash scripts/test_verify_network_egress.sh`（要 tshark+python3；`WORK_DIR=…` 可留产物） |
| `scripts/make_egress_fixtures.py` | 生成 5 个合成 pcap；`--inspect` 自带解析器复述内容 | `python scripts/make_egress_fixtures.py [--out DIR]` |
| `scripts/package_deps_report.sh` | 实测跨层 import，输出可直接贴进棘轮基线 | `PYTHON=python bash scripts/package_deps_report.sh [--count]` |

## 5. 下一步（按依赖顺序，不跳步）

1. 说一声「推送」→ 把这 8 个提交推上去，等同一 SHA 的 `verify` / `ui-test` / `upgrade-test`；
   `ui-test` 若还红，用新加的诊断输出定位那 8 条"not displayed"到底是没测量、被裁还是出窗口。
2. 阶段二第 1 步：`KnowledgeReadPort`/`KnowledgeWritePort`/`AiGateway` + 一套 contract test
   同时跑生产适配器和 fake（复核 §4 的 LSP 验收标准），先吃掉 domain 那 6 处越界。
3. 阶段二第 2 步：`ReplyStore` 起头，按指导书 §5.2 的顺序一个 feature 一个 feature 迁，
   每个 store 一次提交、一次 940+ 单测全绿。
4. P1-02：把触摸区断言从"源码里有常量"改成语义树 `boundsInRoot` ≥48dp（要设备，走 CI）。
5. 阶段三：先接截图工具（Roborazzi 或 Paparazzi 二选一），再谈组件收敛与首页四段结构。
