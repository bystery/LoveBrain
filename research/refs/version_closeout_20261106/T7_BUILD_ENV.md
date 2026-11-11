# T7 — D:/LoveBrain 本机构建环境打通报告（2026-11-06）

分支：`closeout-20261106`（已确认 `git branch --show-current` 输出）。
性质：只运行构建命令，未修改 D:/LoveBrain 任何源码、配置文件。

## 1. 环境现状（探测结果）

| 项 | 实测值 | 结论 |
|---|---|---|
| PATH 上 `java -version` | 11.0.26（Oracle JDK 11） | **不能用于 AGP 8.6** |
| `JAVA_HOME` 环境变量 | `C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot` | JDK 21 Temurin 21.0.3+9，满足 AGP 8.6（需 17+）与 Gradle 8.7 |
| Android Studio 自带 jbr | `D:\Android\AndroidStudio\jbr\bin\java.exe`（JDK 21） | 备用 JDK，同样可用 |
| `ANDROID_SDK_ROOT` | `D:\Android\Sdk` | 存在且有效 |
| SDK 内容 | build-tools / cmdline-tools / emulator / licenses / platform-tools / platforms / sources | 完整可用 |
| `D:/LoveBrain/local.properties` | `sdk.dir=D:/Android/Sdk` | 与实际 SDK 一致 |
| Gradle wrapper | `gradle-8.7-bin.zip`，且 `~/.gradle/wrapper/dists` 已缓存 | 离线即可启动 |
| AGP / Kotlin | AGP 8.6.0、Kotlin 1.9.24、kotlinx.serialization 1.9.24、roborazzi 1.30.0 | 与盘点记录一致 |

结论：此前盘点（PRODUCT_WIRING_INVENTORY_V1.md）中"Gradle 8.7 / AGP 8.6 / Kotlin 1.9.24 / JDK17 可能缺失、ANDROID_SDK_ROOT 待解析"的担忧**全部不成立**——JDK 位置在 Adoptium JDK21（非 Android Studio jbr），Gradle 发行包与依赖缓存均已就绪。

## 2. 构建步骤逐项结果

| # | 命令 | 结果 | 要点 |
|---|---|---|---|
| 1 | `./gradlew help --offline`（JAVA_HOME=JDK21） | **成功**，34s | 离线即通过，说明插件与依赖全在本地缓存，无需走代理 |
| 2 | `./gradlew compileDebugKotlin` | **成功**，23s | 16 tasks（3 executed, 13 up-to-date），无编译错误 |
| 3 | `./gradlew assembleDebug` | **成功**，8s | 36 tasks（5 executed, 31 up-to-date），debug 签名 validateSigningDebug 通过，无需 keystore |

代理（HTTPS_PROXY=127.0.0.1:10808）**未启用、未需要**。

## 3. 产物

- APK：`D:\LoveBrain\app\build\outputs\apk\debug\app-debug.apk`
  - 大小 13,908,602 字节（约 13.9 MB）
  - 时间 2026-11-06 22:08（本次构建产出）
  - 附带 `output-metadata.json`

## 4. 复现方式（关键：PATH 上的 java 11 不可用）

在 Git Bash 中执行前必须覆盖 JAVA_HOME：

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.3.9-hotspot"
cd /d/LoveBrain && ./gradlew assembleDebug
```

等价备选 JDK：`D:\Android\AndroidStudio\jbr`（同样为 JDK 21）。

## 5. 解锁清单

- 无阻断项。不需要安装任何软件、不需要联网下载依赖、不需要签名配置（debug 构建自动用调试签名）。
- 唯一注意点：默认 shell PATH 的 JDK11 会导致 AGP 报版本不符；按 §4 设 JAVA_HOME 即可。
- 后续 release 构建才需要正式 keystore（本次范围外，未尝试）。
