# LoveBrain

Android 悬浮窗聊天助手，帮你在亲密关系对话里想出下一句。

## 环境要求

- Android 8.0（API 26）及以上
- 自备 AI 服务商的 API key

## 安装

从 [Releases](https://github.com/bystery/LoveBrain/releases) 下载最新的 `app-release.apk` 安装即可。需要授予悬浮窗权限；无障碍权限可选，仅用于长按自动捕获消息内容，不开启的话手动复制粘贴也能用。

## 配置 AI 服务商

打开 App，选择 DeepSeek 或其他 OpenAI 兼容服务商，填入 baseUrl、model 和 API key。key 加密存储在本地。

## 从源码构建

```bash
git clone https://github.com/bystery/LoveBrain.git
cd LoveBrain
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/`。Windows 上项目路径请只用 ASCII 字符，否则构建工具链会报错。

## 隐私

- 没有 LoveBrain 后端，无遥测、无统计、无广告 SDK。
- 聊天记录上下文、知识库、画像等全部是 App 私有目录里的本地文件。
- 唯一的网络出口是用户自己配置的 AI 服务商请求。
- 已关闭云备份（`allowBackup=false`）。
- 知识库导出是明文 zip，分享前请自行检查内容。

## 许可

AGPL-3.0 或商业许可，见 [LICENSE](LICENSE)。闭源商用需获得维护者许可。

## 其他

个人项目。欢迎提 pull request，但没有支持 SLA。
