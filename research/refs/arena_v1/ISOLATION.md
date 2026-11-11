# ARENA V1 · D 包隔离报告（ISOLATION）

日期：2026-10-21。依据：指导书 §6（D1–D4）。**本文件是执行登记，不是验收通过书。**

## 1. 架构（D1/D2）

> **2026-10-21 网络面结论更新（E 包）**：D4 必要不变量"不产生未登记真实请求"
> 已由**上游通道密钥门禁**（应用层机制，免管理员）实测保证——
> A6c 直连真实上游通道无密钥/猜密钥均被拒 + canary 上游账本零次无密钥服务；
> 代理持密钥合法调用成功（L4）。A6a 任意本地 socket 可达性 = 纵深防御残差
> （RESIDUAL_DOCUMENTED，管理员防火墙规则为最小权限需求，非必要路径）。
> 矩阵最新：22 PASS + 1 RESIDUAL_DOCUMENTED + 0 FAIL（logs/isolation_matrix_20261021_165632.json 及 verify 重跑）。


```
可信控制器 arena/isolation/trusted_controller.py
  ├─ 评分/原题快照/账本/额度（候选回传 gold/id_map/score 一律丢弃，A4 实证）
  ├─ 白名单轮输入 → JSON 行帧 IPC（1MB 上限 / 超时 / 坏 JSON 错误路径）
  ├─ 可信代理 TrustedProxy：绑定 batch/candidate/case，attempt 去重、额度守卫、
  │   离线替身后端；账本运行期只在内存，退出后落盘 + sidecar 散列
  └─ ACL 护栏：受保护树文件级 deny(R,W)（icacls，用户态免管理员）
受限宿主 arena/isolation/candidate_host.py（python -I -B 干净环境独立进程）
  └─ 只读 staged 代码/媒体（白名单架构级强制）、只写自身 state/；无 lb_scorer
被测体 arena/isolation/synthetic_candidate.py（合成 canary，含攻击执行器）
```

## 2. D4 攻击/正常功能矩阵结果（logs/isolation_matrix_*.json）

| 测试 | 结果 |
|---|---|
| L1 父依赖加载 / L2 状态快照恢复 / L3 读允许媒体 | PASS |
| A1 直读题面 gold（绝对路径） | **BLOCKED**（PermissionError） |
| A2 读其他候选 state | **BLOCKED** |
| A3a 相对跳转 / A3b 绝对路径 / A3c junction | 全 **BLOCKED** |
| A4 伪造 gold/id_map/score | 已回传但控制器**全部丢弃**，评分用可信侧快照 |
| A5a/A5b 导入/猴子补丁评分器 | **BLOCKED**（staged 无此模块；原路径 ACL 拒绝） |
| A6a 绕过代理直连本地 canary 端口 | **NET_NOT_ISOLATED → 记 BLOCKED（见 §4）** |
| A6b 子进程继承越权读 | **BLOCKED**（ACL 同用户继承） |
| A7 伪造/重放 attempt_id / 超额 | 重放拒、额度守卫生效（used=5/quota=5）、账本 attempt 唯一 |
| A8a 崩溃 / A8b 超大帧 / A8c 杀宿主 / A8d 坏 JSON | 全部进明确错误终态，不悄悄重生 |

汇总：**17 PASS + 1 BLOCKED（网络出站）+ 0 FAIL**（isolation_matrix_20261021_163742.json）。

## 3. ACL 护栏协议（含事故记录，最高优先教训）

- **禁止**对仓库目录做 `(OI)(CI)` 继承 deny：文件级级联锁死，且锁死文件在用户权限下
  **不可恢复**（icacls /remove 需 READ_CONTROL 被拒；owner 隐式 WRITE_DAC 受限 token
  不可用；DELETE 亦被拒）。
- 2026-10-21 事故：开发期目录级继承 deny 曾锁死 benchmark/episodes + media/pilot。
  处置：整目录改名隔离 → 从 `lovebrain_review_v13_wave15_20261021.zip` 重建
  （19/19 与 split_manifest 散列逐字节对账一致，0 mismatch）→ 原位还原。**数据零丢失**。
- 现行协议：运行前字节快照受保护树（refs/arena/episodes/cases/anchors）→ 文件级
  deny（目录不 deny）→ 运行结束整树改名隔离、快照原位还原 → 残石登记
  `_ACL_LOCKED_LEDGER.json`。
- 当前残石：20 个 `*_acl_locked_20261021_*` 目录 + `benchmark/_acl_locked_episodes{,_probe2}`
  + `benchmark/media/_acl_locked_pilot`（内容均有已验证副本，锁定原件等待清理）。
- **一次性最小权限请求（管理员 PowerShell，一次执行）**：
  ```powershell
  Set-Location D:\lovebrain-arena
  Get-Content _ACL_LOCKED_LEDGER.json | ConvertFrom-Json | % locked | % path | % {
    icacls $_ /remove:d <redacted> /T /C ; Remove-Item $_ -Recurse -Force }
  ```

## 4. 已知限制与最小权限需求（D4 收口条件）

1. **网络出站未隔离（A6a）**：无管理员无法加防火墙规则/AppContainer。候选直连
   127.0.0.1 canary 成功。影响：真实上游若在本机开免 key 端点可被绕过代理直连。
   最小需求（二选一，一次性授权）：①管理员执行 netsh advfirewall 出站阻止规则；
   ②部署时上游仅经可信代理回环端口 + 管理员防火墙规则限制源进程。
2. **Windows 防火墙/AppContainer 之外的内核级隔离未做**：文件读+写已实测隔离；
   进程/句柄级越权面以 A 矩阵为准。
3. logs/ 历史证据树未纳入 deny 范围（量大且只读历史，无评分权威）；已在残石
   协议中留接口，如需纳入按管理员需求一并申请。

## 5. D 包关闭判定

- 文件/导入/伪造/崩溃/代理滥用攻击：**实际阻断，PASS**。
- 网络出站隔离：**BLOCKED**（需一次性管理员授权），D 包不写全 PASS；
  按 E2 口径 G08 = PASS（文件/进程面）+ BLOCKED（网络面）双状态登记。
- 已验收输入边界在新进程路径的复验：A4（伪造字段丢弃）+ A8d（坏 JSON）+ G03
  相关由 E 包 verify_arena_v1 在同一路径重跑确认。
