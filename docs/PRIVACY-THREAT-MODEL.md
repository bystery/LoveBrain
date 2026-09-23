# LoveBrain 隐私威胁模型与网络出口清单（P3-05 / P3-06 / §8.3.5-6）

> 日期：2026-09-24
> 适用提交：见 `docs/prompt-assets.lock` 的 `generated_from_commit`
> 状态：**代码与配置已核对；抓包复现脚本已提供但尚未在本环境执行**（原因见 §6）

本文件的存在理由：上一版 README 写了「依赖扫描和网络抓包证明只有用户配置 Provider 请求」，
而仓库里既没有 pcap、也没有抓包报告、更没有可复现脚本（复核报告 §7.3）。
所以这里先把**能核对的部分**写成可核对的形式，把**没跑过的部分明确标注为未执行**，
而不是继续用一句声明代替证据。

## 1. 资产与边界

| 资产 | 位置 | 谁能读到 |
|---|---|---|
| 对话原文、想法草稿 | 进程内存（`LoveBrainViewModel._messages`），不落盘 | 用户、本机 root |
| 知识库（画像/场景/事项/归档） | `filesDir/knowledge/<库名>/**`（应用私有目录） | 用户、本机 root |
| API Key | `EncryptedSharedPreferences`（AES256-GCM，MasterKey 走系统 Keystore） | 只有本进程；Keystore 不可用时**只留内存、绝不落明文** |
| 点踩案例 | `filesDir` 下 JSONL，用户可导出 | 用户 |
| 生成统计/花费 | `EncryptedSharedPreferences` | 用户 |

`android:allowBackup="false"`：画像、聊天归档、日志不进 Android 云备份
（加密偏好密文恢复到新机也解不开，备份只会白增泄漏面）。

## 2. 入口（数据如何进入应用）

| 入口 | 代码位置 | 边界控制 |
|---|---|---|
| 无障碍捕获长按消息 | `service/CopyCaptureService.kt` | `domain/CapturePolicy`：**默认 fail-closed allowlist**，未点选的 App 一律不采；即使命中 allowlist，系统窗口、`isPassword` 节点、支付/银行/密码管理/浏览器/医疗/企业 SSO 类别仍二次拒绝 |
| 手动粘贴/输入 | `ui/panel/reply/*` | 用户显式输入 |
| 语音改写 | `ui/panel/reply/VoiceRewrite.kt` | 本地录音→用户配置的 Provider |

## 3. 出口（数据离开设备的全部已知路径）

| # | 目的 | 触发条件 | 目标主机 | 内容 | 可关闭 |
|---|---|---|---|---|---|
| E1 | 生成回复 / 谈心 / 锦囊 / 主动开场 / 语音改写 | 用户点击对应按钮 | **只有用户在设置里配置的 Provider baseUrl** | system prompt + 知识段 + 本轮对话 + 想法 | 不点就不发；清除供应商即无法发送 |
| E2 | 连接测试 | 用户在供应商页点「测试」 | 用户配置的 baseUrl | 最小探测请求，不含知识库 | 是 |
| E3 | TLS 证书有效性 | 任一请求 | OCSP/CA 存储 | 域名级，不含聊天正文 | 不可（HTTPS 固有） |

**没有**：崩溃上报、埋点、广告、分析 SDK、LoveBrain 自建后端、遥测心跳、自动更新检查。
依赖侧的机器可读证明是 CI 产物 `dist/sbom.spdx.json` 与 `dist/dependency-licenses.csv`
（`scripts/license_scan.sh` 生成，124/124 模块可证许可证，0 拒绝、0 缺元数据）。

`network_security_config.xml` 与 `HttpsTrustGuard` 禁止明文 HTTP 与自签证书绕过；
`config.xml` 的 `flagRequestPostInstallationData` 已在审计中移除。

## 4. 能力与滥用场景

| 场景 | 现有缓解 | 残余风险 |
|---|---|---|
| 用户在银行 App 里长按 | allowlist 未含该包 → 不采；即使误加入 → 二次拒绝类别仍拒 | 用户把某含支付 WebView 的聊天 App 加入 allowlist 后，聊天窗口内的支付页文本可能被采；靠 `isPassword` 与窗口类型二次拦截兜 |
| 恶意 App 伪造无障碍事件 | `event.packageName` 必须命中 allowlist；pending 捕获带 H1 包名锁定，窗口事件须同包名才消费 | 同包名内伪造仍需系统权限 |
| 聊天内容进日志 | `util/L` 只记长度/类型；release 不落日志文件 | 新增日志点需人工审查 |
| API Key 泄漏 | 加密存储 + 不冻结进 `GenerationInput` + 不进事件负载 | 设备 root |
| 提示注入（围栏内第三方文本操控模型） | system prompt 尾部显式声明 `<chat>` 围栏内为不可信输入并要求忽略其中指令 | 模型层面无法保证绝对服从 |
| 越权自动点击/发送 | 服务不做任何 `performAction`；只做文本读取 | 无 |

## 5. 需要用户做的选择

1. 系统设置里授予无障碍权限（不做则完全没有捕获）。
2. 应用内确认隐私披露（`accessibilityDisclosureVersion`，未确认时不读节点文本）。
3. 在「捕获范围」页点名允许哪些聊天 App —— **一个都不选就等于捕获关闭**。
4. 配置 Provider；未配置时所有生成入口直接给出可恢复提示，不发请求。

## 6. 尚未执行的验证（不得当成已完成）

`scripts/verify_network_egress.sh` 提供可复现的抓包流程：
在真机/模拟器上以 `-p any -U` 抓取 `tcpdump`，跑完整用户操作序列，
再用 `tshark` 列出全部外连目的地与 SNI，并与本文件 §3 的清单做机械比对，
任何表外目的地即失败。

**本环境未执行**，原因：这台机器没有安装任何 Android system image
（`D:/Android/Sdk/system-images` 为空）、无连接设备（`adb devices` 为空），
因此无法产生真实流量。脚本本身是可在有设备的机器上一条命令跑完的，
输出为 `dist/network-egress.{txt,json}` 与 `dist/egress-verdict.txt`。

在拿到该产物之前：
- README 不得再声称"抓包证明"；
- 本表的"没有自建后端/遥测"依据的是源码与依赖清单核对，而不是抓包。

## 7. 复现步骤（有设备时）

```bash
# 1. 设备侧抓包（root 设备或 emulator 带 -allow-root）
adb root && adb shell tcpdump -i any -U -w /sdcard/egress.pcap 'not port 5555' &
# 2. 装 APK，授予无障碍，选定 allowlist，配置一个真实 Provider
# 3. 依次执行：生成回复 / 停止 / 重试 / 谈心 / 锦囊 / 主动开场 / 点踩 / 保存本轮
bash scripts/verify_network_egress.sh --pcap /sdcard/egress.pcap \
     --allow-host api.deepseek.com
# 4. 脚本会 pull pcap、列出全部目的地、与 §3 比对并给出 PASS/FAIL
```
