# 发版检查清单（Release Checklist）

> **发布模型（2026-09-29 用户书面改需求；2026-09-30 复核 §1 P0-3 / §8 第 3 条之后全仓统一）**
>
> * **GitHub Actions 只做构建与测试**：`.github/workflows/ci.yml` 的 `verify` + `ui-test` 是必过项；
>   `upgrade-test` 只在 `workflow_dispatch` 那一发里跑（push/PR 里它的结论就是 `skipped`，实测 run 36730225255）。
> * **R8 签名 APK、覆盖安装验证、签名连续性全部在本地手动执行**（第 3/4/5 步）。
> * **发布是一条人工命令**（第 9 步）：APK 与证据一起由人 `gh release create` 上传。
> * `.github/workflows/release.yml` **不是发布流水线**：它只在人按下 `workflow_dispatch` 时跑，
>   做的是"发布前机器复核 + 把最后那一米交回给人"，跑完不碰 Release 页，也不读任何签名 secret。
>
> ⚠ 本文件旧版第 3 行写的是"配合 release.yml：tag 推送后 CI 自动跑单测 + 构建 + 发布"。
> 那句话同时与 ci.yml 里已落地的改需求、与 release.yml 当时的行为互相矛盾（复核 §1 P0-3），**2026-10-01 起作废**：
> 现在 **push 任何 `v*` tag 都不会触发任何构建或发布动作**，CI 也不会等签名结果。

下面每一步都给出**可直接粘贴**的命令。命令里出现的脚本全部在 `scripts/` 下，参数名逐条按脚本 `--help`
头的用法写（要核对就跑 `bash scripts/<name>.sh --help`，会打印 usage）。

---

## 1. 一次性准备（每台发布机一次）

- [ ] Android SDK build-tools 提供 `apksigner` / `aapt2`，`adb` 在 PATH 上（第 4/5 步要用）。
- [ ] `gh` 已登录：`gh auth status`。
- [ ] 本地拿得到真钥四件套（**不进 git、不进 CI**）：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
- [ ] `scripts/signing-baseline.txt` 钉的公开证书指纹 == v1.3.1 发布页上写的那一条（签名连续性的唯一参照物）。

## 2. 版本号、变更记录、物料，推到 main

```bash
grep -n 'versionCode\|versionName' app/build.gradle.kts   # versionName 决定 tag，versionCode 必须 +1
```

- [ ] `versionName` 就是这次要发的版本（tag `v1.4.0-rc1` → `versionName "1.4.0-rc1"`），`versionCode` 比上一版大 1。
- [ ] `README.md` / `README_EN.md` 顶部 Version 徽章同步。
- [ ] Release notes 亮点 ≤5 条、每条人话（"长按捕获更快了"，不是"优化 CopyCaptureService 事件分发"）；
      有不兼容/权限/行为变更就在最顶部加粗说明。
- [ ] `docs/demo.gif` 与 `docs/img/*.png` 为最新 UI；GitHub About 描述与 topics 与当前版本一致；Social preview 1280×640 为最新版。
- [ ] 推 main：**只有 main 的 push 触发 ci.yml**，PR 触发但不产生可引用的 push run：

```bash
git push origin main
COMMIT="$(git rev-parse HEAD)"; echo "$COMMIT"
```

## 3. 等 CI 绿，并把"哪一发 CI"钉成一个号

CI 在这一模型里只负责构建 + 测试：push / PR 的必过项（`verify`、`ui-test`）里没有任何签名步骤。
`ci.yml` 唯一会碰签名 secret 的地方是你**手动** `workflow_dispatch` 才跑的 `upgrade-test` job——发布不依赖它，
所以也别为了拿绿去重跑那一发。

```bash
gh run list --workflow ci.yml --limit 5 --json databaseId,runNumber,headSha,event,status,conclusion
# 唯一实现：按 (repo, ci.yml, head_sha, event) 解析出**恰好一个**已完成的 run id（同一条命令也在 release.yml 里跑）
bash scripts/resolve_ci_run.sh --commit "$COMMIT" --workflow ci.yml \
  --require-job verify --require-job ui-test --id-file dist/ci-run-id.txt
```

- [ ] 上面这条命令 exit 0，并印出 `run id`。它的失败出口是**有意**的，别绕：
      命中 0 发 → 红（tag push 不触发 ci.yml，就是这个原因）；命中 >1 发 → 红并列出候选 id，
      要求人显式指定（`--run-id <id>`；在 workflow 里就是 `ci_run` 输入）——**不替你挑"最新一发"**。
- [ ] `verify`、`ui-test` 两个 job 均 `success`；`upgrade-test` 结论是 `skipped` 属**预期**。
- [ ] `ui-test` 产物里 45 格插桩结果非空（CI 已经用 `scripts/assert_artifacts.sh` 挡过；红的时候它会自己红）。

## 4. 本地出签名 + R8 的 APK（CI 内不做）

```bash
KEYSTORE_BASE64=… KEYSTORE_PASSWORD=… KEY_ALIAS=… KEY_PASSWORD=… \
  bash scripts/prepare_release_keystore.sh --baseline scripts/signing-baseline.txt
./gradlew :app:assembleRelease --no-daemon
bash scripts/resolve_release_apk.sh --require-signed --path-file fixtures/candidate-apk-path.txt
APK="$(cat fixtures/candidate-apk-path.txt)"; echo "$APK"
```

- [ ] `prepare_release_keystore.sh` 在任何 Gradle 任务之前就把证书指纹跟 baseline 比过；它红 ⇒ 停下，别换 key 硬发。
- [ ] `resolve_release_apk.sh --require-signed` 交出的是**签名**产物路径；它拒绝 `app-release-unsigned.apk`。
- [ ] keystore 材料没被提交：`git status --porcelain keystore keystore.properties` 无待提交项。

## 5. 本地核对版本身份与签名连续性（产出 CI 稍后要复核的那两份记录）

```bash
TAG=v1.4.0-rc1; VERSION="${TAG#v}"
bash scripts/check_apk_metadata.sh "$APK" \
  --expected-package com.lovebrain.app \
  --expected-version-name "$VERSION" \
  --min-version-code 1 \
  --expect-release \
  --r8-mapping app/build/outputs/mapping/release/mapping.txt \
  --properties dist/apk-metadata.txt \
  --report dist/apk-metadata.md

bash scripts/verify_signing_continuity.sh "$APK" \
  --baseline scripts/signing-baseline.txt \
  --properties dist/signing.properties \
  --report dist/signing.md

mkdir -p "docs/release-evidence/$TAG"
sha256sum "$APK" > "docs/release-evidence/$TAG/SHA256.txt"
cp dist/apk-metadata.txt  "docs/release-evidence/$TAG/apk-metadata.txt"
cp dist/signing.properties "docs/release-evidence/$TAG/signing.properties"
```

- [ ] 两条命令都 exit 0；`signing.properties` 里 `signing_continuity=PASS`。
- [ ] mapping.txt 留在本地备用（崩溃还原要它）：`app/build/outputs/mapping/release/mapping.txt`。

## 6. 覆盖安装验证（本地那一跑；CI 不再等它）

```bash
bash scripts/download_release_apk.sh v1.3.1 fixtures --path-file fixtures/old-apk-path.txt
# 脚本自带三件核验：SHA-256 == scripts/signing-baseline.txt 钉的值、APK 内 versionName == v1.3.1、证书指纹连续。
# 需要登录态或仓库改私有：GH_TOKEN="$(gh auth token)" 加在前面重跑即可。

bash scripts/run_upgrade_test.sh \
  --old-apk "$(cat fixtures/old-apk-path.txt)" \
  --candidate-apk "$APK" \
  --out-dir fixtures

cp fixtures/upgrade-evidence.md fixtures/upgrade-fixture-manifest.txt "docs/release-evidence/$TAG/"
```

- [ ] 装老包 → 写真实夹具 → `-r` 覆盖装候选 → 断言旧数据读得回 / schema 已迁移 / 主页面起得来 / logcat 无致命，全绿。
- [ ] 两个会当场红的前提，**不是脚本挑剔**：
      ① 老/新包必须由同一把 key 签名（否则系统直接拒 `-r`）；
      ② 候选是 non-debuggable 的 release 包 ⇒ 写夹具需要 `adb root`（CI 用的是 google_apis 那台模拟器；
      普通用户机上 `run-as` 进不去 `/data/data/com.lovebrain.app`，这是仪器要求不是产品缺陷）。
- [ ] 上一版**没被覆盖安装跑到的 3 条跳过项**，在同一台目标真机上手做一遍（屏幕录制 + `adb logcat` 留存）。
      仪器侧为什么缺：那两条服务生命周期用例在模拟器上被跳过（前台服务与系统环境约束，
      复核 §3.1 明确"不值得为测试环境再造一套服务框架"），所以这段证据只能由真机补。
      按顺序做，每一步都要留一份 logcat：

      1. **起服务**：授予悬浮窗权限后打开总开关 → 悬浮球出现。
      2. **反复起停 5 次**：开关各拨 5 次，每次间隔几秒。
         看两件事：① 每次停止后悬浮球真的消失（不残留）；② 设置页/面板不报"服务已启动"但球不在。
         这一条补的是"repeated start/stop 不泄漏实例"。
      3. **后台切回**：服务运行中把 App 划掉（最近任务里滑走）→ 等 10 秒 → 从桌面图标重新进入。
         悬浮球应当仍在，且长按消息能正常捕获。
      4. **生成中杀掉服务**：长按一条消息 → 点生成 → **在结果出来之前**关掉总开关（或直接停服务）。
         要求：不崩溃、不留下永远转圈的面板；面板关闭后 App 仍可继续操作。
      5. **重开再生成**：接着上一步重新打开总开关 → 再长按一条消息 → 再生成一次 → 必须正常出结果。
         这一条补的是"generation in flight 时 destroy，重开后链路仍可用"。
      6. **覆盖安装**：不卸载，直接 `-r` 装候选包（就是上面那一步的脚本），装完重复 1、4、5 三小步。

      记录落在 `docs/release-evidence/<tag>/device-smoke/`：每步一个 `.logcat.txt` + 关键界面截图，
      外加一份 `notes.md` 写清设备型号、Android 版本、每一步的通过/不通过。
      判据是"有没有留存"，不是"看着顺不顺"——没有留证等于没做。

## 7. 生成证据索引（脚本自己是门，缺输入就红，不会印 "TBD"）

```bash
RUN_ID="$(cat dist/ci-run-id.txt)"
# 注意：gh run download 的位置参数是 **run database id**，不是 commit SHA（旧 release.yml 就写错过这一条）。
gh run download "$RUN_ID" --name sbom-and-licenses --dir dist/ci
gh run download "$RUN_ID" --name test-xml-report --dir dist/ci
# upload-artifact@v4 在压缩包里保留工作区相对路径，所以文件落在 dist/ci/dist/… 与 dist/ci/app/build/…
SBOM_DIR="$(dirname "$(find dist/ci -type f -name sbom.spdx.json | head -1)")"; echo "$SBOM_DIR"

bash scripts/emit_release_evidence.sh \
  --out "docs/release-evidence/$TAG/release-evidence.md" \
  --title "$TAG" \
  --apk "$APK" \
  --metadata "docs/release-evidence/$TAG/apk-metadata.txt" \
  --signing "docs/release-evidence/$TAG/signing.properties" \
  --sbom "$SBOM_DIR/sbom.spdx.json" \
  --sbom-csv "$SBOM_DIR/dependency-licenses.csv" \
  --license-report "$SBOM_DIR/dependency-license-report.md" \
  --upgrade-evidence "docs/release-evidence/$TAG/upgrade-evidence.md" \
  --upgrade-manifest "docs/release-evidence/$TAG/upgrade-fixture-manifest.txt" \
  --ui-xml-dir dist/ci \
  --extra "commit=$COMMIT" --extra "ci-run=$RUN_ID"
```

- [ ] 证据索引里每一行都有出处（APK SHA-256 / versionCode / versionName / 包名 / 证书指纹 / SBOM 条数 / 插桩格数 / 夹具文件数）。
- [ ] 这份 md 就是第 9 步 Release 的正文——不要另写一份口径不同的说明。

## 8. 把证据交回仓库，然后点一次发布前机器复核（不自动）

证据必须在 APK 构建 commit **之后**提交：`release.yml` 会用 `git merge-base --is-ancestor` 实测这条。

```bash
git add "docs/release-evidence/$TAG"
git commit -m "docs(release): v1.4.0-rc1 local signing + upgrade-install evidence"
git push origin main

gh workflow run release.yml --ref main \
  -f tag="$TAG" -f commit="$COMMIT" -f ref=main
gh run list --workflow release.yml --limit 3
gh run watch <上一步列出的 run id>   # 同一 commit 有多发 CI 时，再加 -f ci_run=<CI run id>
```

- [ ] `release.yml` 跑绿 = 它印出的那张表全部有出处 + 产物 `release-assistant-report`。
      它核的是：tag ↔ versionName ↔ 那个 commit、CI run 唯一且 `verify`/`ui-test` 全绿、
      CI 的插桩/lint 证据非空、本地证据与 CI 记录与 `scripts/signing-baseline.txt` 三者互洽。
- [ ] 它**核不到**、只能由你负责的一条："`$APK` 这些字节确实由 `$COMMIT` 构建"。这条复核里没有、也不该有签名 secret，APK 字节不进复核。
- [ ] `release.yml` 不发布任何东西（permissions 只有 `contents: read`）；它最后一步**印出**第 9 步的命令给你粘。

## 9. 发布：人跑这一条，APK 与证据一起上传

```bash
sha256sum "$APK"                       # 必须等于 docs/release-evidence/$TAG/apk-metadata.txt 里的 sha256
gh release create "$TAG" "$APK" \
  --target "$COMMIT" \
  --title "$TAG" \
  --prerelease \
  --notes-file "docs/release-evidence/$TAG/release-evidence.md"
```

- [ ] 非 RC 的正式版**去掉** `--prerelease`。
- [ ] Release 页确认：APK 文件名带版本号、可下载、正文就是那份证据索引、SHA-256 与自己手里的一致。
- [ ] 从 Release 页**下载回来**装到真机复核一次（每次大版本至少一次）——下载→安装→首启→生成一轮。
- [ ] 按营销日历发社区帖（v2ex / 酷安 / reddit；原草稿已于 2026-09-03 清理，发布前需重写）。

## 10. 签名包的实机自测项（真 release 包，不能用 debug 包代替）

### 首启与配置
- [ ] 全新安装（先卸载）可正常进入引导页
- [ ] API Key 填入 → "测试连接"通过 → 重启 App 后 Key 仍在（加密存储生效）
- [ ] 填错 Key 时的报错文案可读、可操作

### 权限
- [ ] 悬浮窗权限引导跳转正常，授权后悬浮球出现
- [ ] 无障碍服务开启/关闭/被系统回收三种状态，App 均不崩溃且状态展示正确

### 核心链路
- [ ] 任意聊天 App 长按消息 → 悬浮面板弹出、消息入列
- [ ] 生成：四张卡流式渲染完整，点卡片复制成功
- [ ] 谈心模式一轮完整问答
- [ ] 今日锦囊正常出卡
- [ ] 主动发/润色一轮完整生成

### 知识库与沉淀
- [ ] 新建知识库 + AI 画像问卷走通
- [ ] 聊满触发阈值，向量重估/经验提炼/画像更新通知正常出现
- [ ] 导入导出 zip：导出有明文警告、重新导入内容一致
- [ ] 删除知识库有确认对话框

### 降级与异常
- [ ] 断网状态点生成：报错文案友好，恢复后可重试
- [ ] 杀进程重开：消息面板清空（预期行为）、谈心历史保留、今日锦囊当日保留

## 11. 回滚预案

- [ ] 上一版 APK 本地有留存（或上一 Release 可回退下载）：`gh release download v1.3.1 --dir fixtures`
- [ ] 出现 bug 的口径：置顶评论 + Release 页置顶说明 + issue 建档
- [ ] 需要撤回发布时：`gh release edit "$TAG" --draft=true` 先把页面摘下来，再决定修 or 撤（**本仓库约定不删除，只归档**）

## 12. 发布后记账

- [ ] 把 Release URL、APK SHA-256、CI run id、覆盖安装那一跑的结论写回交接单/`docs/release_report_template.md` 那份报告。
- [ ] 关闭已解决 issue；未解决的按复核报告 §6 的口径分流 backlog。
