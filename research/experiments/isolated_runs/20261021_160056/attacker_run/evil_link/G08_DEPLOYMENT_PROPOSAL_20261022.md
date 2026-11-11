# G08 部署方案 v2（**WITHDRAWN：已撤回，不再是试考前置于**）

> **2026-10-22 20:10 状态：作废（不再待批、不得执行）。**
> 依据：`refs/harness_api_mainline_20261022/HARNESS_API_MAINLINE_GUIDE_20261022.md` §3
> 与 `refs/arena_v1/SCOPE_DECISION_API_CANDIDATE_20261022.md`（v2）。
> 撤回理由是**判错了对象**：参赛对象是 harness（由本项目编写），本批要防的不是
> "恶意第三方本地代码"，因此不需要 OS 账户/防火墙/主仓 ACL。
> `tools/g08_deploy.py --apply` 已硬拒绝（`WITHDRAWN_BY_SCOPE`，exit 3）。
> 旧隔离矩阵的 FAIL 事实**原样保留**，不改写成 PASS，也不声称完成了抗恶意代码隔离。
> 下文仅作历史方案留档。

日期：2026-10-22。取代同日 v1 方案。依据：复核 v3 执行书 §2B。
性质：**方案文档。未创建账户、未改防火墙、未改任何 DACL、未复用 Codex 内部账户。**

## 0. v1 方案的错误前提（如实撤回）

v1 称"新账户不加入 Users 组 → 对主仓天然不可读，主仓零 DACL 变更"。
**该依据错误**：独立复核实测（`refs/arena_closeout_review_v3_20261022/
evidence_154158_455873/ROOT_ACL_READONLY.json`）——`D:\lovebrain-arena`
根目录存在继承自卷的 **Authenticated Users = Modify** ACE。任何本地账户
都自动持有 Authenticated Users 身份（由系统赋予，与是否加入本地 Users 组
无关，微软文档：Special identity groups）。新账户对主仓默认**可读可写**，
v1 的拒绝依据不成立，不能照原文批准实施。

## 1. 修正后的机制：显式 DENY + 沙箱专用世界

拒绝依据从"无组成员"改为**对该账户的显式 DENY ACE**（DENY 优先于
Allow，可实测验证）；依赖授予全部落在新目录，原运行时零 ACE 变更。

### 存量修改（如实计入，非"零接触"）

| # | 对象 | 变更 | 快照 | 回滚 |
|---|---|---|---|---|
| M1 | `D:\lovebrain-arena` 根 | 追加 1 条 DENY ACE：`ArenaHostSandbox:(OI)(CI)Full` | 部署前 `icacls /save` 全树 SDDL 留底（audit 目录） | `icacls /remove` 该账户全部 ACE（对照快照核验条数） |
| M2 | 本地安全策略 | `ArenaHostSandbox` 加入 SeDenyInteractiveLogonRight / SeDenyRemoteInteractiveLogonRight（禁止交互/远程登录，仅服务式拉起） | `secedit /export` 留底 | 导出项还原 |

除上表两处外，**无其他存量修改**。原 Python 运行时、防火墙现状、其他
账户、其他目录均不动。

### 新建对象

| # | 对象 | 说明 |
|---|---|---|
| N1 | 本地账户 `ArenaHostSandbox` | 随机密码（SecureString，不落命令行/环境变量）；无组成员声明不作为安全依据（见 §0），安全依据是 M1 DENY |
| N2 | `D:\arena-sandbox\` | `icacls /inheritance:r` 切断卷继承，显式 `grant ArenaHostSandbox:(OI)(CI)M` + `grant Administrators:F`；Auth enticated Users 无条目 |
| N3 | `D:\arena-sandbox\runtime\` | 受管 Python 的**完整拷贝**（robocopy；非共享原运行时）——防火墙按此唯一二进制路径限定，不触碰 abyss 侧 python.exe |
| N4 | 防火墙出站阻断 2 条 | `New-NetFirewallRule -DisplayName ArenaSandbox-Block-Outbound -Direction Outbound -Action Block -Program "D:\arena-sandbox\runtime\python.exe" -RemoteAddress Any`（含回环；SDDL 与完整参数部署时落档）；第二条 `ArenaSandbox-Block-DNS` 同程序 UDP 53。**不放行任何端口**——IPC 走 stdio 管道，回环也不需要 |
| N5 | 每 run staged 树 | `D:\arena-sandbox\staged_<run_id>\`（控制器拷贝生成，账户只读）+ `sandbox\`（唯一可写区） |

### 媒体路径修正（v1 未兑现项）

媒体授权登记表现指向**主仓内原文件 realpath**——主仓对账户 DENY 后，
合法媒体也会不可读。修正：`stage()` 时把题面登记的媒体文件**拷贝进
staged 树**（控制器侧算 sha 绑定，登记表指向 staged 副本）；未登记
媒体对账户本就不可读（DENY+无条目），双层成立。此为控制器代码变更
（trusted_controller._turn_media_entries / stage），随本方案一并实施。

### 凭据 / 账本 / 评分

不变：control_dir、out_dir、attempt_ledger、授权单不授予账户任何权限
（在主仓内，受 M1 DENY 保护）；宿主不注入任何密钥；模型调用全部经
控制器代理（stdio）。

### 跨用户拉起

`.NET ProcessStartInfo{UserName, Password-in-SecureString}` 拉起
`D:\arena-sandbox\runtime\python.exe -I -B _candidate_host.py` 并接管
stdio 管道。**部署第一步先做连通性冒烟**（管道握手/hello 帧），
冒烟不过即停止，不进入验收、不追加变更。

## 2. 验收（唯一生产入口 iso_run.py；宿主以 ArenaHostSandbox 运行）

| 检查 | 判据（只认 OS 拒绝，不认 FileNotFoundError/超时/协议错） |
|---|---|
| A2_sibling_state | **BLOCKED:PermissionError** + 零 canary |
| A3_traversal_out_of_staged | **BLOCKED:PermissionError** + 零读取 |
| A4b_abs_module_exec | **BLOCKED:PermissionError** + 零 canary 落盘 |
| MEDIA 私有/data URL | 保持拒绝 |
| 回环旁路 | 沙箱账户 python 直连 `127.0.0.1:<控制器端口>` → **阻断证据**（SocketError，且防火墙拦截计数 +1） |
| 直连外网 | 沙箱账户 python 访问任意外部地址 → **阻断证据** |
| 正常功能 | mock/echo 题完整跑通；评分/账本/媒体授权（staged 副本）不回归 |
| 控制器侧 | abyss 用户一切操作不受 M1/M2 影响（DENY 仅限沙箱账户） |

不达标即回滚（§0 两项存量 + §1 新建对象全部复原，对照快照核验）并保持
G08 FAIL/NOT_READY，不降格残差 PASS。

## 3. 停止条件

- 连通性冒烟失败（跨用户 stdio 管道接不通）→ 停，报告现场，不试错；
- 验收任一攻击路径未获 OS 级拒绝 → 停，回滚，G08 保持 FAIL；
- 部署中发现需要超出 §0/§1 清单的任何系统变更 → 停，先补充审批；
- `runs/_atk_legit` 等历史 DENY 残骸不清（纪律不变）。

## 4. 审批请求（一次收集）

批准范围 = §0 两项存量修改 + §1 五项新建对象 + §3 验收与回滚授权。
批准后一次执行完成（账户/目录/运行时拷贝/防火墙/M1 ACE/冒烟/验收），
交付拒绝证据矩阵与快照回滚包。未批准则 G08 保持诚实 FAIL。
