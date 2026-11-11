# 考场收口报告 V3（复核 v3 执行书 §3 路径 · 第一阶段交付）

日期：2026-10-22 17:00。对象：`refs/arena_closeout_review_v3_20261022/REVIEW_AND_CLOSEOUT_EXECUTION.md`。
本轮真实模型调用 **0 次**；系统权限/防火墙/账户/DACL **0 变更**。
全部结论为 **worker 自验**，待独立签收。

## 1. 第一阶段完成口径逐项对照（执行书 §4）

### A. 授权闭环（§2A 七个同族绕过，一次修完）

| 复核反例 | 修复 | 反例证据（tools/test_authorization_budget.py，17/17） |
|---|---|---|
| CLI 省略 --authorization 仍创建 | 非离线适配器 CLI 强制 --authorization（iso_run.py 入口拒绝，exit 2，不建目录） | `v3_cli_requires_authorization` |
| 无预算代理仍派发 | TrustedProxy 非离线适配器无 budget 一律拒派发（dispatch_refused_no_budget 入账） | `v3_proxy_without_budget_refused` |
| 离线口误伤/被扩宽 | 允许清单 `OFFLINE_ADAPTERS={mock,echo}` 控制器与 CLI 同源；清单外适配器（含新增）默认必须挂预算（fail-closed） | `offline_allowlist_boundary` |
| 关闭后旧实例继续派发 | reserve 在**同一把锁内**重读权威账本核关闭态（不信实例缓存） | `v3_closed_checked_in_lock` |
| 跨实例重复 attempt_id | 唯一性锁内按权威账本全量核对 | `v3_duplicate_attempt_across_instances` |
| 重建对象放大额度 | max_attempts 首次使用即持久绑定（authorization_scope 事件）；重建不符构造即拒 | `v3_max_attempts_immutable` |
| 坏账本行静默清零 | 坏 JSON 行 → LedgerCorrupt **停批**（不清零、不当未消费） | `v3_corrupt_ledger_stops_batch` |
| 陈旧实例预检放行 | ensure_batch 锁内重读权威账 | `v3_ensure_batch_rereads_authority` |

接线修复（复核两项）：①授权单严格校验（缺 ID/非布尔 authorized/未知
status/非法额度/端点/model/temperature 与出站配置不符 → 全部拒绝，
`v3_authorization_doc_validation`）；②episode 结束**不再关闭整个授权**
（授权覆盖批准的运行清单，由可信批次 `close_authorization()` 显式收口；
失败按批准策略终止，不复活不补跑）。失败/完成配对入授权账本。
整批预检批需由调用方声明（`--expect-attempts`），辅助调用须按实际路径重算。

**G04 回归与修法（如实登记，本阶段唯一一次门禁自伤）**：上一版把强制面写成
`adapter != "mock"`，把离线确定性替身 `echo`（零上游、零消耗）也拦在预算口外，
导致 11 轮生命周期对拍首轮 `HOST_EXCEPTION` 失败；G10 冒烟因门禁画像不一致
连带 FAIL（一因一果，非两个缺陷）。改为允许清单判定后 G04 12/0、G10 PASS，
真实上游强制面未放宽（原 16 项反例全绿）。教训：安全边界按"是否出网"划分，
不按"是否叫 mock"划分。

锁机制教训（如实登记）：O_EXCL+remove 锁在 Windows 有删除竞态（AV/沙箱
扫描器短暂持有句柄 → delete-pending → 锁文件名被占死）。已换字节范围锁
（msvcrt.locking/flock），OS 随进程死亡自动释放，无删除竞态。真实 10 进程
占额（4/8 边界恰 4 接受 6 拒绝）签收口径保留并升级为默认测试。

### B. 24 题接入与评审（§2C，不再等待出题方）

1. **内容安装**：v0.2 九文件对独立签收散列逐字入库 `benchmark/dev_pilot_v02/`
   （9/9 一致，源 zip sha256 63c89dfa…），三份题面正式安装 `benchmark/episodes/`。
   内容版本登记 `DEV_EXPOSED_MANIFEST.json`（原件散列+导入记录+版本绑定；
   IMPORT_OK 标注为已执行证据、非生产安装凭证）。
2. **导入器 schema 边界**（通用修复，零题号特判）：嵌套 initial_kb、非法
   日历日期（11-99）、窗口消息乱序、缺 case_id、turn 级 gold 错型、字符串
   消息项崩溃——合成正反例 7/7（`logs/importer_selftest` 记录）。
3. **卡片齐全/绑定**：`--cards` 检查 24 卡数量/唯一 case_id/双向绑定/
   episode 归属/split=dev_exposed；真实 24 题+24 卡实测 **IMPORT_OK**
   （`logs/pilot_v02_import_fixed.json`）。
4. **评审接线**：`tools/review_wiring_drill.py`——三组自造正反回复
   （守卡 vs 违卡、合法 null vs 硬填、事实守约 vs 违约）走**真实**
   gen→ingest→aggregate→verify_workdir 全链路：3/3 材料通过独立复核、
   判据引用评审卡 failure_conditions 留判断依据、合法 null 不硬填、
   候选/运行/case/输出槽位绑定落 review_wiring.json sidecar、
   fixture 标签不伪造用户投票。生产路径 `spec_from_run()`（真实答卷
   逐位读对应卡，首选/非空备选分审）就绪，待首场答卷启用。

### C. 隔离方案修正（§2B，可审批，未执行）

v1"不加入 Users 组→天然不可读"依据**错误**（主仓根目录 Authenticated
Users=Modify 继承 ACE，独立实测确认）——已撤回。修正版
`refs/arena_v1/G08_DEPLOYMENT_PROPOSAL_20261022.md`：
- 拒绝依据改为对该账户的**显式 DENY ACE**（DENY 优先 Allow，可实测）；
- 存量修改如实计入：主仓 1 条 DENY ACE（/save 快照+/remove 回滚）+
  沙箱账户禁交互登录（secedit 留底还原）——不再是"零接触"；
- 依赖全部落新目录 `D:\arena-sandbox\`（inheritance 切断）+ Python 运行时
  **完整拷贝**（防火墙按此唯一二进制限定，不触碰日常 python.exe）；
- 媒体路径修正：授权媒体 stage 时拷贝进 staged 树（sha 绑定），
  解决 DENY 后合法媒体不可读问题；
- 防火墙给实际规则参数（SDDL 部署时落档，含回环阻断），验收加回环旁路
  与直连外网阻断证据；
- 停止条件与回滚对象逐一列明；冒烟不过即停。

### D. 版本与开启文件（§2D）

- `START_PROMPT.md`："每批<50 分批清理"残留**已删除**（执行书 §2D）；
  冷启动只负责考生机制改进，不重建考场。
- 措辞更正：0/6 逐字相同 → **只证明文本不同，行为等价性需语义比对**
  （SCOPE_AND_STATUS / 事故更正 / V2 报告 / 证据 JSON 四处同步）。
- 冻结：authorization_budget.py（arena/ 整目录随包）、
  test_authorization_budget.py、import_pilot_questions.py、
  review_wiring_drill.py、benchmark/dev_pilot_v02/、修正版文档全部入
  release；执行指纹分层（execution_fingerprint vs report_metadata）
  已在前版建立。最终账见 §2。

## 2. 最终账

| 项 | 结果 |
|---|---|
| 授权反例 | **17/17**（含真实 10 进程边界 + 离线允许清单边界） |
| 隔离矩阵 | 30 检查：26 可动项 PASS + 4 项诚实环境 FAIL，unexpected=**0** |
| lifecycle 对拍 | 12/0 |
| 导入器自测 | 7/7（6 类反例精确拦截 + 正例通过） |
| 真实 24 题+24 卡导入 | **IMPORT_OK**（卡齐全/绑定全过） |
| 评审接线演练 | 3/3 材料全链路 verify ok |
| 离线全量 verify | `logs/arena_v1_acceptance/20261022_final_offline/ACCEPTANCE.json`：G01–G05/G06a/G07–G10 全 PASS，G06b NOT_READY，G08 诚实 FAIL |
| 发布包 | PACKAGE PASS（250 文件，unpack_verify OK，smoke full_offline）；pkg 路径见该账 G10 |
| 集成模式 | `logs/arena_v1_acceptance/20261022_final_integration/`：G11_STANDIN PASS（绑定当前 release），G11 NOT_RUN（无新授权） |
| G08 | FAIL（诚实，环境未部署）——修正版方案待批 |
| G11 | 通路 PASS（历史 run）+ 预算 FAIL/EXCEEDED（已关闭授权不复用） |

## 3. 第二阶段需一次收集的批准（执行书 §3.2，未获批不执行）

**审批件已单独成文：`refs/arena_v1/APPROVAL_REQUEST_20261022.md`**（一次提交，
含系统变更范围 A 与首场预算 B，可分别或一起批准）。

1. **系统变更批准（A）**：G08 方案 v2 的 2 项存量修改（主仓 DENY ACE、
   沙箱账户禁交互登录）+ 5 项新建对象（账户/沙箱根/运行时拷贝/2 条出站阻断/
   外置 staged 树）+ 1 项受控代码变更（跨用户拉起宿主，部署时实施并验收）；
   含 SDDL/secedit 快照、逐项回滚、停止条件、只认 OS 级拒绝的验收判据。
   明确**不做**：媒体 staged 副本改造（本批无媒体）、其他 DACL/防火墙/账户变更。
2. **首场试考授权（B）**：运行清单 = `reference_product_v1` × E001/E002/E003
   共 **24 位**；端点 `http://127.0.0.1:17770/v1`、model=dsf、温度 0.7、
   适配器 openai_compat；**总预算 24 attempts**（依据：G11 实测 1 次/轮，
   24 轮=24 次，无辅助调用；实施中若发现辅助调用先停再按实际路径重算）；
   失败策略 = 停批、原始失败保留、禁止自动补跑、不预留补跑额度；额度不继承
   旧 8/12/52/66 次授权。
3. 批准后连续交付：隔离部署与验收 → 重新冻结 v19 + 重跑 G01–G10 →
   首场 24 位真实答卷 → 逐位质量初审（真实卡、语义/风格分层）→
   可复制开启 prompt。

## 4. 边界声明

- 独立选择集、B 集、几千道扩题：不阻塞公开文本开发试考，也不在本轮
  冒认完成；
- 本批无媒体题，首场文本试考不构成视觉集成证据；
- "工程全部完成只缺钥匙"表述继续作废：第一阶段交付以本报告 §1 逐项
  证据为准，第二阶段以实际隔离验收+真实答卷为准。
