# G08 部署前提 — 受限执行环境（ISOLATION_ENV_PREREQ）

日期：2026-10-22。依据：ARENA_V14_INDEPENDENT_REVIEW §4.2、CLOSEOUT 执行书 A2。

## 现状（如实）

候选机制代码只在不可信宿主进程（`_candidate_host.py`，`python -I`）内导入执行，
可信控制器已零候选执行（IMP 导入副作用 canary 实证：staged 恰好 1 行、pid=宿主、
仓库夹具零痕迹）。但宿主本身仍是**同用户普通进程**：文件系统不设限、网络不设限。

矩阵因此如实登记 4 项环境硬缺件 FAIL（不降格残差 PASS）：

| 检查 | 实测 | 需要的环境能力 |
|---|---|---|
| A2_sibling_state | 未授权文件可读（32B canary 被读走） | 文件系统白名单挂载 |
| A3_traversal_out_of_staged | 路径跳出 staged 可读仓库文件（8135B） | 同上 + 根目录隔离 |
| A4b_abs_module_exec | 绝对路径模块可被执行（canary 落盘） | 只读代码挂载 + 独立根 |
| RESTRICTED_ENV_ABSENT | 汇总登记 | 以下前提 |

已部署的纵深防御（不构成隔离，仅收窄攻击面）：生产入口自动 deny 当前题面文件
（A1/A6b 确定拒绝 PermissionError 实证）、媒体授权白名单（私有文件/data:URL 拒绝）、
模型/温度可信强制、额度持久账本、started 零派发硬边界。

## 部署前提（满足任意一套即可重跑矩阵）

**方案 1：Windows AppContainer / 受限令牌（最低改动）**
- 宿主进程以 AppContainer 或受限主令牌启动：普通用户 SID 的文件读取被拒，
  仅对 staged 树授予读执行、对 sandbox 目录授予读写。
- 网络默认拒绝；唯放行到可信代理的命名管道/回环 IPC。
- 验收标准：矩阵 A2/A3/A4b 由 FAIL 转 BLOCKED:<拒绝证据>。

**方案 2：容器/微虚拟机（跨平台通用）**
- Linux: bubblewrap/nsjail——只读 bind staged 树 + tmpfs sandbox，无网络命名空间
  （IPC 经 unix socket 传入）。
- Windows: Docker/Windows Sandbox——staged 树只读卷挂载，无外网，宿主目录
  不挂载。

**方案 3：独立低权限 OS 账户**
- 专用运行账户，对仓库/题库/账本目录仅 staged 临时副本可读；icacls 精确授予。
- 可信控制器以原账户运行（评分权威隔离由进程边界保持）。

## 明确不做（纪律）

- 不再对主仓做 deny/reset 权限试验、不搬移题库树、不清理 ACL 残骸换 PASS。
- 不把必要隔离降格为"残差已登记 PASS"。
- 未部署前，G08 保持 FAIL/NOT_READY；考场以"开发诊断模式"运行（见开启 prompt）。
