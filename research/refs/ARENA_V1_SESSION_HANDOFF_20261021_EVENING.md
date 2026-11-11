# ARENA_V1 考场修复 · 会话交接文档（2026-10-21 晚）

项目：`D:/lovebrain-arena`。本文是**进度快照 + 下一步行动卡**，不替代三份权威文件：
《独立复核报告》《修复任务书》《收口指导书》。接手会话先读那三份（见 §0），再读本文。

---

## 0. 30 秒上手

**阅读顺序（必须）：**
1. `D:/lovebrain-arena/AGENTS.md`
2. `D:/lovebrain-arena/refs/ARENA_V1_CLOSEOUT_GUIDE_20261021.md`（考场建设总指导书）
3. `D:/lovebrain-arena/refs/ARENA_V1_INDEPENDENT_REVIEW_20261021.md`（**推翻 OFFLINE_READY 的判决书**，六大缺陷）
4. `D:/lovebrain-arena/refs/arena_v1_review_20261021/VERDICT.json`（判决索引）
5. `D:/lovebrain-arena/refs/ARENA_V1_REPAIR_HANDOFF_20261021.md`（**修复任务书，R1/R2/R3 三组要求的权威来源**）

旧 17 波聊天记录**不需要**逐条重读。

**接手后第一件事（已定位、有现成补丁）：**
修 `arena/isolation/host_runtime.py` 里 `_wait_result` 的竞态 bug（`KeyError: 'g1'`），重跑
`runs/_dbg8` 验证 reference_product_v1 隔离全流程。详见 §4——**补丁代码已写好，直接落地**。

**当前总状态：R1 真实纵向链路代码已重写完成、卡在最后一个竞态；R2 完成探测结论部分；R3 未开始。**

---

## 1. 项目定位与纪律（最高优先，违反即返工）

LoveBrain 斗兽场 = 自动考场，用来迭代"微信聊天辅助 AI"的候选机制（candidate mechanism）。
考场工程与候选训练是两条线：**先把考场修到工程可信，再训练考生**。

纪律红线（继承自项目传统，本次会话全程遵守，接手会话必须继续遵守）：

| # | 纪律 | 说明 |
|---|---|---|
| 1 | **机制 >>> prompt** | 修行为靠机制/代码，不靠改提示词碰运气 |
| 2 | **不宣称未验证完成** | 没有实测证据不写 PASS；验收模板全部初始化为待验证，禁止预填 |
| 3 | **真实调用需明确"授权"口令** | 未经用户明说"授权"，零真实模型调用、零 ACL 操作、零对外发布 |
| 4 | **52 次候选训练另列** | 不拿考生修复充当考场建设进度；52 对照不复用本次授权 |
| 5 | 不读 `benchmark/b_line` 内容、不读用户 dataset | 独立选择内容保密 |
| 6 | 不执行 `refs/arena_v1/ISOLATION.md` §3 一键 ACL 清理命令 | 该命令从可变 JSON 取路径递归删，未核验最终路径/副本/退出码 |
| 7 | 不执行 `test_isolation_matrix.py` 的主仓 ACL 修改逻辑 | 目录级 deny 曾造成锁死事故（见 §9） |
| 8 | 不创建无人值守自动进化脚本 | 逐轮候选变异必须人工监督、单独移交 |
| 9 | 分批交付 + 门控 | 每批完成后停下等用户授权，不擅自扩大范围 |

**本会话（被交接方）执行情况：零真实模型调用、零 ACL 操作、零主仓文件修改、419 份原文件未动。**

---

## 2. 判决与任务范围

### 2.1 当前判决：NOT_READY（不是 OFFLINE_READY）

worker 曾宣称 OFFLINE_READY，独立复核**推翻**。六大考场缺陷：

| # | 缺陷 | 归属组 |
|---|---|---|
| 1 | 真实链路没接通：隔离宿主固定跑合成候选；参考基线缺资产+机制入口，实跑报错 | R1 |
| 2 | 评分占位：坏 JSON 无真正评分/veto | R1 |
| 3 | 隔离旁路：恢复备份在候选可读位置，宿主读到合成 gold 副本 | R2 |
| 4 | 总验收误报：子测试失败读旧日志仍 PASS；空检查结果也过 | R3 |
| 5 | 评审统计不可靠：篡改展示原文仍过；重复导入致"总12/审13/未审−1" | R3 |
| 6 | 发布包不完整：156 文件自报 PASS，解包首项验收缺 9 文件 | R3 |

**保留的真实成果**（不推倒）：公共协议提取与 re-export、16 条契约矩阵、原进程内评分/输入边界/no_reply/输出结构/失败持久化五套旧检查、26 条参考组件测试、参考辅助函数及测试、开发题曝光声明、人工演练框架。

### 2.2 修复任务书三组

- **R1 真实纵向链路**：同一运行路径按参数实际加载 reference_product_v1 和真实 phase 候选；驱动真实 init/run_turn/snapshot/restore；gateway.generate 同步 IPC，模型原始结果在 run_turn 返回前交给候选；可信侧调 `run_case_scoring_safe` 出真实 score10/checks/veto；attempt_started 发送前落盘、失败不退额度。
- **R2 真实隔离 + 公平基线**：停用主仓 deny+整树搬移；gold/备份/日志/评分移出候选可发现位置；参考基线补资产+机制入口+≥8 对拍；ACL 台账只清点不删；网络面不足则 G08 如实不通过。
- **R3 验收收口**：严格证据绑定+负例自测；review_loop 全材料内容绑定+去重；coverage 时钟单调+message.image；封存做访问控制验证；发布包补全；G11 真实入口；授权清单删 N8。

> 注：`refs/arena_v1/SCOPE_AND_STATUS.md` 里那一大串 PASS/OFFLINE_READY 是**被推翻前的 worker 自评**，R3 修复后必须重写，现在不要引用它作为状态依据。

---

## 3. 本会话已完成工作（文件级清单）

### 3.1 R1 主链路三件套（新建/重写）

| 文件 | 类型 | 作用 |
|---|---|---|
| `arena/isolation/host_runtime.py` | **新建** | 隔离宿主运行时：`load_mechanism`（与 `lb_runner.load_mechanism` 同契约，按候选目录参数加载 v1/v2 机制，**无硬编码合成候选**）；`HostRuntime` 驱动真实 init/run_turn/snapshot/restore；`IpcGateway` = `lb_gateway.ModelGateway` 同签名替换，`generate()` 同步 IPC；`HostServer` 帧循环（主线程 resolve gateway 结果 + 工作线程顺序执行命令，防死锁） |
| `arena/isolation/_candidate_host.py` | **新建** | 宿主进程入口（`python -I -B`）：**stdin/stdout/stderr 三流 reconfigure UTF-8**（修 GBK 误解码的关键补丁） |
| `arena/isolation/trusted_controller.py` | **重写** | 可信控制器：`TrustedProxy`（复用真实 `lb_gateway` 适配器+事件账，attempt_started 发送前落盘、失败不退额度、directive 只在控制器侧合并）；`IsolatedRun`（stage 镜像拷贝候选闭包 → spawn 宿主 → IPC → 可信侧评分/产物/账本）；`request()` 内联分派 `gateway_generate` 帧；回传白名单（候选自报 score/gold 字段不采信）；**不再对主仓 ACL deny/整树搬移** |

### 3.2 参考基线（R1/R2 共用）

| 文件 | 说明 |
|---|---|
| `candidates/reference_product_v1/mechanism/mechanism.py` | **新建**。参考基线机制入口（v2：init/run_turn/snapshot/restore）。逐字移植 `PromptBuilder.kt`：buildSystemPrompt（core+naturalness+redline+format+输入安全声明）、byBlocks 顺序（knowledge→scene→intent→note→chat.header→body→timestamp）、无库分支逐字。装配忠实性锚点（Kotlin 源行号）冻结在文件头 |
| `candidates/reference_product_v1/assets/system_prompt/{core,naturalness_check,redline,format}.md` | **新建**。从 `/d/LoveBrain/app/src/main/assets/engine/system_prompt/` 冻结的原产品资产 |
| `candidates/_attack_fixture/{mechanism/mechanism.py,params.json}` | **新建**。隔离矩阵攻击夹具（非生产候选） |

### 3.3 工具与探测

| 文件 | 说明 |
|---|---|
| `tools/iso_run.py` | **新建**。隔离运行 CLI：`--episode/--candidate/--out/--adapter/--quota/--stop-after/--resume`。**同一命令仅切 `--candidate` 即换机制**（R1 验收项 1 的载体） |
| `tools/probe_restricted_token{,2..6}.py` | **新建**。受限令牌探测链 6 轮。**结论：本环境 OS 级受限执行不可用**（窗口站创建 err 5 环境硬拒；0xC0000142=桌面对象 ACL；系统目录授 BUILTIN\Users 非 Everyone；SidsToRestrict=裸 PSID_AND_ATTRIBUTES 数组）。按纪律 G08 如实不通过、保留 DEV_TRUSTED_ONLY，**不降格 PASS** |
| `runs/_dbg7/` | 上一轮全流程验证现场（证据见 §4） |

### 3.4 编码修复史（重要背景，防止重复踩坑）

宿主 `-I` 模式 stdin 默认 GBK，控制器中文帧被误解码 → BAD_JSON 死循环。
**修复 = 双向**：`_candidate_host.py` 三流 `reconfigure(encoding="utf-8")` + 两侧 IPC 帧
`json.dumps(obj, ensure_ascii=True)`。修复后帧通路已验证 OK（_dbg7 的 calls.json 里
prompt 8958 字符完整、mock 回复已产生），剩下的是 §4 的竞态。

---

## 4. 🔴 当前卡点：`_wait_result` 竞态 → `KeyError: 'g1'`（第一行动）

### 4.1 现场证据

`runs/_dbg7_out/turn_01_NCB_t01/error.json`：

```
HOST_EXCEPTION: KeyError: 'g1'
  mechanism.py:262 run_turn → gw.generate(...)
  host_runtime.py:59 generate → res = self._wait(rid)
```

同一目录 `calls.json` 显示：prompt 已正确装配（frozen 资产 8958 字符）、mock 上游已返
回、IPC 双向通路正常。**即：卡点只在"结果交回候选"的最后一跳。**

### 4.2 根因（竞态时序）

`_resolve_result`（主线程）与 `_wait_result`（工作线程）对 `_pending` 的 pop 竞争：

1. 工作线程（候选 run_turn 内）：`_pending['g1'] = {ev, res:None}` → `ev.wait()` 挂起；
2. 主线程读到控制器回帧 → `_resolve_result`：`_pending.pop('g1')`（字典已空）→ `entry["res"]=msg` → `ev.set()`；
3. 工作线程 `ev.wait()` 返回 True → 执行 `self._pending.pop('g1')` → **KeyError: 'g1'**（已被主线程 pop 走）。

控制器响应快于工作线程唤醒时的必现竞态；慢响应时不出现——所以是概率性/时序性故障。

### 4.3 补丁（已写好，直接落地）

改 `arena/isolation/host_runtime.py` 的 `_wait_result`（**保留本地 entry 引用，wait 成功后不再回字典 pop**）：

```python
def _wait_result(self, req_id, timeout=600):
    ev = threading.Event()
    entry = {"ev": ev, "res": None}
    with self._lock:
        self._pending[req_id] = entry
    if not ev.wait(timeout):
        with self._lock:
            if self._pending.get(req_id) is entry:
                del self._pending[req_id]
        raise RuntimeError("gateway result timeout: %s" % req_id)
    return entry["res"]
```

`_resolve_result` 不动（它 pop + 写 res + set ev 的语义正确）。改完**同步改 staged 拷贝**
（`IsolatedRun.stage()` 每次会重新从仓库拷，重跑即生效，无需手动同步）。

### 4.4 修复后重跑（预期全流程 PASS）

```bash
cd D:/lovebrain-arena
<python> tools/iso_run.py --episode benchmark/episodes/NCB_ENTITY_DENSE_v1.json \
    --candidate reference_product_v1 --out runs/_dbg8 --stop-after 2 --adapter mock
```

`<python>` = 上一会话跑 _dbg7 的同一解释器（iso_run 用 `sys.executable` spawn 宿主，
保持一致即可；可用 managed `C:\Users\abyss\.workbuddy\binaries\python\versions\3.13.12\python.exe`）。
**预期**：hello 握手 → INIT_DONE → 2 轮 TURN_DONE（真实 score10/veto 落 scoring.json）
→ episode_manifest complete=true → ledger.json + sidecar 落 `runs/_dbg8/_control/`。

---

## 5. R1 剩余清单（竞态修完后按序做）

| # | 任务 | 验收标准（来自修复任务书 §最少可观察证据） |
|---|---|---|
| 1 | 重跑 _dbg8 参考基线全流程 | §4.4 预期全绿 |
| 2 | **同命令切候选**：`--candidate phase3_facts`（及另一个真实候选）重跑 | 同一命令仅切换 candidate，实际加载不同声明机制（hello 帧 mechanism/origin 不同、staged_hashes 不同）；参考基线可运行 |
| 3 | **真实评分验证**：构造坏 JSON 回复（mock directive 或夹具） | `run_case_scoring_safe` 真判格式失败（score10/checks/veto 有真实值，不是占位说明文字）；候选伪造 score/gold/id_map 字段不改变可信侧评分 |
| 4 | **两轮 + 新进程恢复** mock 端到端 | `--stop-after 2` 后 `--resume` 起新 IsolatedRun 从 checkpoint.bin 恢复，最终 prompt/真实 scorer/状态可反查；全程无常量回复替代模型结果 |
| 5 | **额度纪律**：quota=1 且上游失败 | 失败后不得再发第二次；attempt_started 先落盘；失败/崩溃记录不丢（attempt_ledger.jsonl 可查） |

> 候选分数高低**不是** R1 验收标准；mock adapter 足够完成 R1 全部证据，不烧真实调用。

---

## 6. R2 剩余清单

| # | 任务 | 要点 |
|---|---|---|
| 1 | **control_dir 位置收窄** | 现 `tools/iso_run.py` 把 control 放 `out/_control`，而 staged 根在 `runs/_iso_run_<out>/staged`——同账户候选可经 `../../` 相对路径发现。需把账本/checkpoint/评分移到**不可从 staged 根推导**的位置，并用攻击夹具实测"普通 open 读 control/checkpoint"被阻断（R1 证据链第 3 条的前置） |
| 2 | **停用旧 ACL 协议** | trusted_controller 已不再 deny/整树搬移；`ISOLATION.md` 里"预快照→文件级 deny→结束整树改名隔离+副本还原"旧协议一并停用，只作文物记录 |
| 3 | **修 A6b 假通过** | 子进程 `open(...).read()` 不 print，父进程用"rc=0 且 stdout 非空"判 BLOCKED——rc=0+stdout 空也判 BLOCKED 是假通过。判据改为能证明"读取被阻止"的正向证据 |
| 4 | **≥8 对拍** | 参考基线 vs 真实候选：提交→状态变化→重启→下一轮**完整 prompt** 对拍（不是 JSON 转义类组件测试；26 条组件测试保留为回归） |
| 5 | **ACL 台账只清点** | `_ACL_LOCKED_LEDGER.json`：**161 条路径记录 / 146 条当前存在**（与口头"约 22 个"不符，按台账逐条核验存在性，不推断、不盲删）；管理员善后单独准备、逐目标验证最终绝对路径+副本+散列+退出码 |
| 6 | **G08 网络面如实** | 受限执行环境探针结论=本环境不可用（§3.3）。候选可直连本地任意端口的问题**不降格 RESIDUAL-PASS**；保留 `DEV_TRUSTED_ONLY`，G08 不通过，附最小权限申请（具体配置+可审查），其余离线工作继续 |

---

## 7. R3 剩余清单（逐条对照复核报告 §4–§9）

| # | 任务 | 验收标准 |
|---|---|---|
| 1 | `tools/verify_arena_v1.py` 严格证据绑定 | 子工具 rc≠0 **立即本门失败**；日志绑定本次 run/release；校验固定预期测试集合/数量/每项终态；旧日志、空 verdicts、空 release files={}、单 status=READY **均不得放行** |
| 2 | 负例自测（复核 §4 五反例全过） | ① rc=9 不生成新日志+目录留旧成功日志 → G08/G09 败；② 最新日志 verdicts=[]/ledger={} → 败；③ release files={} → G01 败；④ 独立选择为空只填 READY → G06b 不就绪 |
| 3 | G03 评分比较真断言 | 真实评分与原题权威评分**逐项相同**（不是"帧里出现过伪造 gold 字段"就算过） |
| 4 | `tools/review_loop.py` 全材料内容绑定+去重 | 篡改展示原文（保留旧 binding hash）必须拒；同 material_id+同 reviewer 重复表单显式版本替代或拒；**票数/已审材料数/未审数分开**（禁"总12/审13/未审−1"）；材料由真实冻结题面/calls/reply_raw 生成；persona/initial_kb 在 episode 顶层时不得丢 |
| 5 | coverage 真实 schema 检查 | 时钟倒退（NCA 请求时钟改 1900 年仍 rc=0 的反例）必须抓；媒体检查覆盖 `message.image`（不只 input.media）；布尔检查失败进 issues |
| 6 | 封存访问控制验证 | 合成封存测试验证"候选不能读/只能授权启封"（不能把"B 目录不存在"称隔离通过）；真实 B 线不打开 |
| 7 | 发布包补全 | 含全部 release 必要文件（现缺 9 个：gen_split_manifest、test_anon_binding_guards、test_failure_persistence、test_input_boundary、test_isolation_matrix、test_no_reply_protocol、test_output_structure、test_posttest_event_pairs、test_scene_assembly）+ split_manifest/review_drill_spec/seal_fixture 的可重建输入与受控数据说明；**干净目录执行同一个完整 offline 入口**（只 import+protocol 测试不算） |
| 8 | G11 真实集成入口 | 未执行时**不得 exit=0**；先用本地替身完整演练（真实 OpenAI 兼容 HTTP、model/温度/图片/完整 usage/原文不截断）；结果绑定同一 release 离线证据 |
| 9 | 授权清单删 N8 | "设施故障诊断调用"与"失败立即停批无补跑"冲突，删去；端点/model/温度与 release、验收记录预先冻结 |
| 10 | 重写 `SCOPE_AND_STATUS.md` | 旧自评已被推翻，R3 完成后按实测重写门禁账 |

---

## 8. 离线收口的最少可观察证据（修复任务书原文口径）

1. 相同命令仅切换 candidate，实际加载的是不同声明机制；参考基线可运行。
2. 两轮并经新进程恢复，最终 prompt、真实 scorer 和状态可反查；没有常量回复替代模型结果。
3. 普通 open 读 gold/恢复副本、跨候选读写、评分篡改、绕代理请求被阻断；允许媒体和状态功能保持。
4. quota=1 时上游失败后不得再发第二次；started 先落盘，失败/崩溃记录不丢。
5. 子测试失败但存在旧成功日志仍判失败；空日志/空清单不能通过。
6. 展示原文篡改、重复表单、倒时钟/缺消息图片均被抓住；统计分母正确。
7. 干净发布包完整离线复验通过；真实 B 线、dataset 和密钥不进包。

**交付方式**：一次交齐——代码差异、版本清单、原始证据、完整离线结果、确定的集成授权申请
（≤8 attempt 是待批准上限，不是已授权；离线工程未真通过前不向用户要授权）。

---

## 9. 陷阱与教训清单（血泪，别重踩）

| 陷阱 | 教训 |
|---|---|
| 目录级 `(OI)(CI)` 继承 deny 级联锁死 benchmark/episodes+media/pilot | 被 deny 文件用户级不可恢复；只能整目录改名隔离+从 `lovebrain_review_v13_wave15_20261021.zip` 重建（split_manifest 散列对账 0 丢失）。现行=文件级 deny+残石登记，**再也不用目录级** |
| `icacls` deny 参数必须带括号：`abyss:(R,W)` | 任意 SID 授权需 `*` 前缀 |
| 密钥嗅探模式表自匹配 | 用 regex + 转义规避 |
| `release.json` 冻结时序 | 必须在代码定稿后冻结，否则散列漂移 |
| verify 修 bug 时 G03 漏记录 → 总状态误判 | 改验收器必须跑负例自测 |
| 宿主 stdin 默认 GBK | `-I` 模式三流 reconfigure UTF-8 + `ensure_ascii=True` 双向修复 |
| 控制器误把 gateway_generate 帧当 turn 回包 | `request()` 内联分派（已修） |
| 宿主死锁（_wait_result 主线程内联等） | 帧循环重构：主线程 resolve + 工作线程执行（已修，但留下 §4 竞态） |
| Windows 上多 python 解释器 | iso_run 用 sys.executable spawn 宿主，全程用同一个 |

---

## 10. 关键路径索引

| 内容 | 路径 |
|---|---|
| 项目根 | `D:/lovebrain-arena` |
| 判决书/任务书/指导书 | `refs/ARENA_V1_INDEPENDENT_REVIEW_20261021.md`、`refs/ARENA_V1_REPAIR_HANDOFF_20261021.md`、`refs/ARENA_V1_CLOSEOUT_GUIDE_20261021.md` |
| 复核证据（含 VERDICT.json） | `refs/arena_v1_review_20261021/` |
| R1 三件套 | `arena/isolation/{host_runtime.py,_candidate_host.py,trusted_controller.py}` |
| 隔离运行 CLI | `tools/iso_run.py` |
| 参考基线 | `candidates/reference_product_v1/`（mechanism+assets+reference_chain.py） |
| 攻击夹具 | `candidates/_attack_fixture/` |
| 受限令牌探针 | `tools/probe_restricted_token{,2..6}.py` |
| 总验收器（R3 主战场） | `tools/verify_arena_v1.py` |
| 评审闭环（R3） | `tools/review_loop.py` |
| ACL 残石台账 | `D:/lovebrain-arena/_ACL_LOCKED_LEDGER.json`（161 记录/146 存在，只清点） |
| 旧锁定目录（勿删勿改） | `D:/lovebrain-arena/*_acl_locked_20261021_*` |
| 题库题面 | `benchmark/episodes/*.json`（NCB_ENTITY_DENSE_v1.json 等；**b_line 不读**） |
| 真实上游（授权后才可用） | dsf@127.0.0.1:17770/v1、温度 0.7（66 实跑同款；mock adapter 名=`mock`） |

---

## 11. 与用户的交互约定

- 进度汇报用【刚才/现在/下一步】三句话结构化。
- 每批完成后**停下等"开始"口令**，不自作主张推进下一批。
- 要真实模型调用/ACL 操作/对外发布时，先交具体实现+最小需求，等用户明说"授权"。
- 报告只列真实问题，每条附官方/实测依据；不写"建议·非违规"冗余；不做 AI 写作风格检测。
