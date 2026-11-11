# -*- coding: utf-8 -*-
"""arena.isolation — D 包进程隔离与可信调用代理（G08/G09）。

结构（指导书 §6-D1）：
  可信控制器（snapshot/白名单/评分/审计/代理/ACL 护栏）
      ↓ IPC（stdin/stdout JSON 行帧，1MB 上限，超时）
  独立候选宿主进程（-I -B 干净环境，只读 staged 代码/资产，只写自身 state）
隔离手段（当前环境可实测）：
  - 进程隔离 + 最小环境变量 + -I 隔离解释器路径
  - icacls Deny ACL（用户态，无管理员）对受保护目录（refs/episodes/cases/anchors/
    arena/logs）读写拒绝——攻击者同用户身份也无法读取 gold/scorer 源码/其他候选状态
  - 账本只在候选退出后落盘 + sidecar 散列（运行期无可篡改对象）
  - 评分只在可信侧；候选回传的 gold/id_map/score 一律忽略
  - 代理绑定 batch/candidate/case 身份 + attempt 去重 + 额度守卫；离线后端=本地替身
限制（如实登记，见 ISOLATION.md）：
  - 网络出站隔离（禁直连本地上游端口）需要管理员防火墙规则或 AppContainer，
    当前环境实测为 BLOCKED → 一次性最小权限需求已登记。
"""
