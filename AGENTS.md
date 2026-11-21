# 开发说明

LoveBrain 是一个 Android 悬浮助手应用：单模块 `:app`，Kotlin + Jetpack Compose，`minSdk 26` / `compileSdk 35`。

## 构建与测试

- 调试构建：`./gradlew :app:assembleDebug`
- 单元测试（JVM + Robolectric，无需设备）：`./gradlew :app:testDebugUnitTest`
- 发布签名由仓库根目录的 `keystore.properties` 提供（已 gitignore）。本地没有该文件时，release 构建产出
  unsigned APK（仍可验证 R8、资源裁剪与编译），不会回退 debug 签名冒充正式发布。

## 代码风格

- Kotlin 官方风格；Compose 界面优先复用 `core/designsystem` 里的 `Lb*` 组件，不直接调用 Material 的
  `AlertDialog` / 自绘卡底。
- 新增的用户可见文案放进 `app/src/main/res/values/strings.xml`，不在 composable 里另写一份字面量。
- 注释写"为什么"（约束、反例、取舍），不复述代码在做什么。

## 边界

- 不提交密钥、签名材料与 `local.properties`；API Key 只经 `SecurePrefs` 加密存储，明文不落盘。
- 知识库、聊天归档与画像只落在 App 私有目录；导出物是明文文件，涉及真实对话时必须先让用户确认。
- 生成的是供你选择和编辑的建议，应用不代发消息、不自动点击。

## 目录

- `app/src/main/java/com/lovebrain/app/`：`data/`（持久化与网络）、`domain/`（生成与提示词）、`ui/`（界面）、
  `service/`（悬浮窗与消息捕获）
- `app/src/test/java/com/lovebrain/app/`：JVM / Robolectric 测试
