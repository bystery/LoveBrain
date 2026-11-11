# W5 — Release 构建与签名链路验证（2026-11-06）

分支：`closeout-20261106`（含输入框修复 + 批量导入第一批未提交改动，未改任何源码/配置）。
环境：JAVA_HOME = Eclipse Adoptium jdk-21.0.3.9-hotspot；SDK = D:\Android\Sdk（local.properties 已指向）；apksigner = build-tools/37.0.0。

## 结论

**Release 可出包，签名通过，与发布基线证书一致。无阻断项。**

## 1. 构建结果

```
./gradlew assembleRelease  →  BUILD SUCCESSFUL in 2m 32s
50 tasks: 25 executed, 25 up-to-date
```

- 关键任务实跑（非 up-to-date，覆盖了本批新代码）：`compileReleaseKotlin`、`minifyReleaseWithR8`、`shrinkReleaseRes`、`optimizeReleaseResources`、`lintVitalRelease`（vital lint 通过）。
- 仅 Kotlin 警告（unused parameter / safe call 等，无错误）；新文件 `BatchTranscriptParser.kt:120` 有一条 `nextLineNo` 未使用警告，可在收口时顺手清，不阻断。

## 2. 产物

| 项 | 值 |
|---|---|
| APK | `D:/LoveBrain/app/build/outputs/apk/release/app-release.apk` |
| 大小 | 2,946,578 B（≈2.81 MB） |
| versionName / versionCode | 1.4.2 / 11（build.gradle.kts L32-33） |
| applicationId | com.lovebrain.app |

对比 debug：`app-debug.apk` 14,528,311 B（≈13.9 MB，2026-11-06 22:42）→ R8 + 资源裁剪后体积缩至约 1/5。

## 3. 签名验证（apksigner verify --print-certs --verbose）

- **Verifies**（整体通过）。
- v1 (JAR)：false；**v2：true**；v3/v3.1/v3.2/v4/SourceStamp：false。
  - v1 缺失可接受：minSdk 26 > 23，Android 7+ 只需 v2。
- 签名者：CN=LoveBrain Release, OU/O=LoveBrain, L=Luoyang, ST=Henan, C=CN；RSA 2048。
- 证书 SHA-256 = `98e10b2808ba0ab6c047c2cfff15fcedc5fd14de0f4da98d1914a5490530dbaf`，与 `scripts/signing-baseline.txt` 钉的 `cert_sha256` **逐字一致**（v1.4.0 起现行钥匙，非 2026-10-07 已销毁的历史钥匙）。

签名链路结构（app/build.gradle.kts）：
- `keystore.properties`（仓库根，已 gitignore）提供 storeFile/storePassword/keyAlias/keyPassword **四字段均存在**；storeFile 指向中央工作区 keystore 目录下的 release 签名库（路径属内部治理信息，公开副本脱敏）。
- release 与 debug buildType 在该文件存在时都挂 `signingConfigs.release`；不存在时不指定 signingConfig——release 产 unsigned APK，debug 回 AGP 默认 key，绝不静默回退 debug 签名冒充发布（设计正确，本次路径未触发）。
- 注：仓库根 `keystore/` 目录为空，真实 keystore 不在仓库内。

## 4. Release 与 Debug 行为差异

| 项 | release | debug |
|---|---|---|
| isMinifyEnabled | **true**（本次实跑通过） | false |
| isShrinkResources | **true** | false |
| proguard | `proguard-android-optimize.txt` + `proguard-rules.pro`（34 行：kotlinx.serialization/OkHttp/Koin/Compose LocalLifecycleOwner keep，Tink dontwarn） | 无 |
| lintVital | 跑且通过 | 不跑 |
| BUILD_TYPE BuildConfig | "release" | "debug" |
| 签名 | release key | 有 keystore.properties 时同为 release key |

## 5. 版本号建议（只建议，不改）

现值 1.4.2 / versionCode 11（2026-10-17，阅读修正批）。本收口批含输入框修复（修 bug）+ 批量导入（新功能，新增 composer/BatchImportDraft、BatchTranscriptParser、BatchImportSheet 及测试）。

- **建议 versionName 1.5.0 / versionCode 12**：批量导入是用户可见新功能，按本仓惯例（功能上 minor：1.4.0→1.4.1→1.4.2 均为小步修正才走 patch）应升 minor。
- 若收口定调为"纯修复批"，1.4.3 / versionCode 12 也可接受；覆盖升级只要求 versionCode 递增，签名同钥匙无障碍（1.4.2 同 key 可直接覆盖）。

## 6. 还差什么

1. **versionName/versionCode 未随收口批更新**（仍 1.4.2/11）——提交前需定号并改 build.gradle.kts（本任务未改）。
2. 本批源码改动尚未提交；release APK 是含未提交改动的产物，提交/打 tag 后需按 §9 版本分离重出包并记录 APK SHA-256。
3. 可选：跑 `scripts/verify_signing_continuity.sh` 做门禁脚本侧复核（本次已用 apksigner 直验通过，非阻断）。
4. 小清理（非阻断）：`BatchTranscriptParser.kt` 的 unused 参数警告。
