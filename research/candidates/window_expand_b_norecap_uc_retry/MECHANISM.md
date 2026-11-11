# MECHANISM — window_expand_b_norecap_uc_retry

四决策（相对父本 window_expand_b_norecap_uc 的单变量增量：只加"批级崩坏轮重试策略"一处，装配零改动）。

## 存储 / 检索 / 意图 / 上下文组织
**全部与父本逐字节相同**：mechanism/mechanism.py、mechanism/uncertain_rules.py、assets/ 均为
window_expand_b_norecap_uc 的原样副本（L0 以逐字节 diff 验证）。uc 注入块、3600 CU 历史预算、
intent 块、回复不写 events——一切照旧。

## 唯一变异点：runner 批级崩坏轮重试（params.collapse_retry=true）
- 触发：`run_max288_batch.py --collapse-retry 1` 开启后，某 attempt 的 reply raw 为空
  （len(strip)==0）或程序判定不可解析（json 解析失败且无法修复）时，该 attempt 以
  **逐字节相同的 prompt** 重调 1 次（attempt_id 加 `-r1` 后缀）。
- 记账：每次重试作为独立 attempt 经 AuthorizationBudget 登记（先登记后派发）；两次 raw 全留档；
  取首个可解析者进入评分，失败保持失败（不删分母、不补跑凑数）。
- 对称性：双臂同策——parent 臂崩坏轮同样重试。重试头寸预注册 ≤20% base attempts，两臂共享。
- 边界：不重试语义不合格（只重试合同破坏）；不改评分口径；重试后仍崩位在收口时逐位登记为
  底模输出契约崩坏族（W21 诊断），并入 G1 B3 亲读范围。

## 为何这是单变量
prompt 构造、机制代码、评分器、判据全同父本；唯一差异 = 崩坏轮是否获得一次同 prompt 重试。
该差异正是本代假设（有效回复率抬升）的全部来源，G3' 直接检验它。

## 边界
零额外 prompt 改动；不读网络新增端点；不改输出契约定义；snapshot/restore 沿用父本字段；
不碰 benchmark/b_line/；不动封板题与既有判决。
