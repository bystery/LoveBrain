# 交接：CI 真机证据与剩余项（2026-09-24）

> 只交**我这边的活**。工作树里 `KnowledgeRepository.kt`、`ReadOnlySchemaWriteGateTest.kt`、
> `scripts/verify_network_egress.sh`、`scripts/test_verify_network_egress.sh`、
> `scripts/make_egress_fixtures.py`、`.github/workflows/ci.yml`、
> `RoundCommitJournal.kt`、`TopicRecorder.kt`、`RoundCommitJournalTest.kt` 的改动
> 不是我做的、我没有提交，也不再触碰。
> 我推送到的提交：`4795471`（本地与远端一致，未打 tag、未建 Release）。

## 1. 一句话状态

CI 第一次在 emulator 上真跑了 instrumentation 套件：**40 执行 / 19 失败 / 2 跳过**，
红得清清楚楚，产物齐（XML/HTML/截图都上传了）。所以"点击生成回复不崩溃"目前
**仍然不成立**——这是新复核报告 P0-02 的判断，我认。

## 2. 逐项进度表

| # | 事项 | 状态 | 证据 / 下一步 |
|---|---|---|---|
| 1 | `ui-test` 脚本在零匹配计数上自杀 | **已修并推送** `2b01f21` | `count_annotations` 容错；本机对照：旧写法 exit=1 零输出，新写法同一棵树 tests=40/ignored=0 |
| 2 | prompt 资产锁跟平台换行走 | **已修并推送** `f5328b7` | 摘要前按 LF 归一；本机(CRLF 树)算出的值 == 直接对 git 索引 blob(LF) 算出的值 `6dcde732…` |
| 3 | 锦囊夹具锁是同一 bug 的第二成员 | **已修并推送** `4795471` | CI 日志自己给的等值：`current=675bfc33…` 就是 LF 归一值；负向：改坏锁 → self-test exit=1 |
| 4 | verify 不编 androidTest；upgrade-test 只依赖 verify | **已推** `8cb9819` | verify 加 `:app:compileDebugAndroidTestKotlin`；`needs: [verify, ui-test]` |
| 5 | 报告 §1.4「缺 import 可能编不过」 | **不成立** | `composeRule.onAllNodes(...)`/`assertDoesNotExist()` 是成员调用；`--rerun-tasks` 强编 exit 0。但"同一 SHA 从没编过"这个遮蔽成立，已按建议补进步骤 4 |
| 6 | 19 条真机失败 | **未开始（下一个活）** | 见 §3 |
| 7 | `LoveBrainViewModel` 继续拆分（2651 行） | 暂停，等树干净 | 剩 改写链路 228、锦囊 160、Trigger 落点 188 三块；动之前先确认 #24 那批落地 |
| 8 | 验收包补 CI 真机那一节（红也要写） | 未写 | `LoveBrain_Rework_Acceptance_2026-09-24.md` §3/§4 现在还是"本地等价门禁"的口径 |

## 3. 19 条失败的分类与取证方法

先取证再动手，别在本地猜：

```bash
gh run download 35943596075 --dir _tmp_ci --name test-xml-report
gh run download 35943596075 --dir _tmp_ci --name instrumentation-test-report
# XML: _tmp_ci/outputs/androidTest-results/connected/debug/TEST-emulator-5554 - 10-_app-.xml
gh run view 35943596075 --log-failed > _tmp_ci/failed.log
```

| 类别 | 条数 | 症状 | 我怀疑的方向（未验证） |
|---|---:|---|---|
| A | 8 | `MessageList 应真收到 1 条消息 expected:<1> but was:<0>`（都在 `OverlayGenerateSmokeTest`） | harness 往 VM 加消息与面板挂载之间有竞态，或 MessageList 的测试标签/角色在真机上不可见 |
| B | 9 | `Assert failed: The component is not displayed!`（`ReplyPrimaryActionsTest` 7、`ResultAreaInteractionTest` 2、空态 1） | API 29 上首屏/滚动位置或组合根挂载方式不同；也可能生产真的把入口藏了（那 48dp 那节会说） |
| C | 1 | `R2 应已向 Provider 发出请求` | fake Provider 的端口/权限或请求未发出——需要 logcat |
| D | 1 | `Failed to inject touch input` | 目标节点不可点击或在滚动外；跟 §7 的 48dp 项可能同源 |

A、B 占 17/19，先各挑一条打通再看是否同根因，别一条条改。
改完必须**同一 SHA** 出 XML + logcat + 真实 requestCount，才算 P0-02 过关。

## 4. 这个仓的验证规矩（我踩过的坑，写下来免得重复）

1. 本机跑 py 门禁用 `python`，**不要用 `python3`**（Windows Store 占位符：exit 49 且零输出，会被当成"没报错"）。
2. 文本内容的指纹/锁一律先按 LF 归一；APK 等二进制必须继续按原始字节。
   同族清单就两处：`asset_hashes.sh`、`suggest_cost_baseline.{sh,py}`。
3. `set -euo pipefail` 里的 `grep -c` 零命中会打死脚本：计数用 `gate_lib.sh` 的
   `count_annotations` / `sha256_of_text` 这类显式容错 helper，fail-closed 留给值判断。
4. 每条新断言都要先被"坏实现"打红一次；三种假绿我已撞过：完成的 Job 不在 `children()` 里、
   `historySize` 对 `key -> []` 恒真、fixture 没量级触发被测分支（先看"确实裁过"再断"怎么裁"）。
5. 用 `sed -i` 会把 CRLF 文件改成 LF（我这次就让 `LoveBrainViewModel.kt` 整体换了行尾），
   批量改行内容优先用小脚本按字节处理 + 断言命中数。
6. 绝不在别的会话改动的文件上叠提交；`git status` 先看清归属再动手。
7. 验收包里的每个数字都要来自当次命令输出（我这一轮写错过 3 个数：167/168 站点、46 行、13 例）。

## 5. 全量门禁怎么跑

```bash
./gradlew --offline :app:testDebugUnitTest :app:lintDebug \
  :app:compileDebugAndroidTestKotlin :app:assembleRelease
bash scripts/assert_artifacts.sh --label "unit" --min-tests auto \
  --xml-dir app/build/test-results/testDebugUnitTest \
  --html-dir app/build/reports/tests/testDebugUnitTest
bash scripts/assert_artifacts.sh --label "lint" \
  --lint-xml app/build/reports/lint-results-debug.xml \
  --lint-html app/build/reports/lint-results-debug.html
bash scripts/asset_hashes.sh --check docs/prompt-assets.lock
bash scripts/suggest_cost_baseline.sh --self-test
python scripts/audit_cancellation.py --check
python scripts/strip_ticket_ids.py --check
```

最近一次全绿（我这边，含 #21/#22 修复）：927 例 / 111 套件 / 0 失败，lint 0 error，
APK 已签名 + R8 mapping 校验通过。emulator 侧只有 CI 能出证据，本机没有 system image。
