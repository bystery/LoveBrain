# lint 判据夹具（`scripts/test_check_lint_budget.sh` 的输入）

这两份不是手写样例，是**同一份代码在两台机器上量出来的真报告**。它们要钉住的事实只有一条：
同一件事不该被 lint 量出两个数——如果对不上，先得说清差的是尺还是代码。

| 文件 | 来源 | 量到的数 |
|---|---|---|
| `ci-run-36019334520-lint-results-debug.xml` | GitHub Actions run `36019334520`（SHA `3d92488`）的 `lint-report` 产物，Linux runner | 81 条 issue / 16 条规则 |
| `local-at-3d92488-lint-results-debug.xml` | 本机 `./gradlew :app:lintDebug`，同一份代码（Gradle 把 `lintReportDebug` 判为 UP-TO-DATE，即输入与 `3d92488` 一致） | 71 条 issue / 15 条规则 |

两边 root 元素都是 `by="lint 8.6.0"`，所以差的不是 lint 版本。

## 差的 10 条是谁

按 issue `id` 做集合差，只有两条规则对不上，且全部是"发现来自仓库之外"的检查：

| 规则 | CI | 本机 | 它真正在量什么 |
|---|---:|---:|---|
| `GradleDependency` | 10 | 1 | 这台机器解析到的 Maven 版本清单。同一句 `androidx.test.ext:junit:1.1.5` 声明，CI 报"可升到 1.3.0"，本机报"可升到 1.2.1" |
| `OldTargetApi` | 1 | 0 | 这台机器的 Android SDK 里装了多新的平台（`targetSdk = 35` 两边一样） |

把这两条从预算里挑出去之后，**进预算的部分两台机器完全相同：70 条 / 14 条规则，逐条对得上**。
这就是夹具要断言的东西，也是为什么这里存两份而不是两份合成样例。

## 重取产物

```bash
gh run download 36019334520 -n lint-report -D _temp/ci-36019334520/lint-report
./gradlew :app:lintDebug --no-daemon          # 不要接管道；退出码单独取
```

## 注意

`local-at-3d92488-...xml` 里的 `file="D:\LoveBrain\..."` 是本机绝对路径，原样留着没做加工
——测试不断言路径，只断言规则与条数。哪天这份和当前代码脱节了，`test_check_lint_budget.sh`
的 C1/C2 会红，那时候重录夹具，别改测试。
