# RELEASE_NOTES — ARENA V1 收口发布说明

日期：2026-10-21。基准：v13 审稿包（lovebrain_review_v13_wave15_20261021.zip，
sha256 前 8 位 3e8b1f72）之后至本发布的全部实际改动。

## 1. 与冻结起点的实际改动

| 工作包 | 改动 | 产物 |
|---|---|---|
| A | 协议函数拆出唯一权威库；lb_scorer 改 re-export；候选机制改引公共库；lcs_ratio/AI_FLAVOR_WORDS/STYLE_SIM_MAX 明确留在评分侧 | arena/protocol_public.py、tools/test_protocol_public.py、tools/gen_contract_matrix.py（16/16） |
| B | Kotlin 主链路 Python 移植为比较基准（不参赛）；26 对拍夹具（含 9 多轮）全过 | candidates/reference_product_v1/、tools/test_baseline_parity.py、BASELINE_PARITY.md |
| C | 覆盖矩阵 12 族 36 点 0 缺失；split_manifest 5 家族；封存夹具三重校验；评审闭环 4 命令 12 材料演练 11 收 1 拒 | gen_coverage_matrix / gen_split_manifest / verify_seal_fixture / review_loop |
| D | 隔离三件套（控制器/宿主/代理）+ 攻击矩阵；上游通道密钥门禁（免管理员）；ACL 护栏改"文件级 + 预快照 + 整树隔离还原" | arena/isolation/、tools/test_isolation_matrix.py、ISOLATION.md |
| E | 总验收入口 + 自包含发布包 + 授权清单 | tools/verify_arena_v1.py、tools/build_release_package.py、AUTHORIZATION_REQUEST_INTEGRATION.md |

## 2. 历史重评与不覆盖承诺

- 旧 66 实跑日志与评分**原样保留**（logs/l3b_66_real66_v1/，只读）；
  旧配置因公共装配升级 FROZEN_MISMATCH 封存，验收中按 SKIPPED 单列。
- phase3 的 20/20+10/10 同族测试作为兼容检查保留；不决定 G01–G11。
- 未删除任何失败断言或改良性夹具迎合实现；B-T07 夹具预期修正是
  夹具错误（原实现邀约只看最后一条 HER），非实现迁就。

## 3. 事故记录（必须知情）

开发中曾用**目录级继承 deny** 做隔离实验，级联锁死 benchmark/episodes
与 media/pilot 共 24 个文件；被 deny 文件在用户权限下不可恢复
（icacls /remove 需 READ_CONTROL 被拒、owner 隐式 WRITE_DAC 在受限
token 不可用）。已按"整目录改名隔离 + 从 v13 审稿 zip 重建"恢复，
split_manifest 逐字节散列对账 0 mismatch，**数据零丢失**。约 22 个
锁定残石登记于 `_ACL_LOCKED_LEDGER.json` 待管理员一键清理
（命令见 ISOLATION.md §3）。现行护栏协议因此改为文件级 deny +
预快照 + 整树隔离还原。

## 4. 限制（如实）

- G06b 独立选择未就绪（外部缺件）——最终状态上限见 SCOPE_AND_STATUS §5。
- 网络面纵深残差：候选进程原始 socket 可达本地任意端口
  （A6a RESIDUAL_DOCUMENTED）；必要不变量（不产生未登记真实请求）由
  上游密钥门禁实测保证；管理员防火墙规则为纵深防御最小权限需求。
- Judge 自动裁判未启用（未校准）；真实 B 线未开启；52 次候选对照
  另列未授权。

## 5. 复验命令（自包含包内）

```powershell
Set-Location <解包目录>
python -B -X utf8 tools/test_protocol_public.py
python -B -X utf8 tools/verify_arena_v1.py --mode offline `
  --release refs/arena_v1/release.json --out logs/verify_offline
```
