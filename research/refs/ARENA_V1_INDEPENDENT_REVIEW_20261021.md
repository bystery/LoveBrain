# ARENA v1 独立复核：当前不能确认OFFLINE_READY

日期：2026-10-21。对象：worker提交的20261021_172312验收及当前考场代码。依据：上一轮《考场v1收口执行指导书》，没有追加新的候选质量目标。

**判决：考场尚未建成。worker的OFFLINE_READY应退回为NOT_READY；不能认为只差8次授权、保管身份和残石清理，更不宜现在转入正式候选循环/冠军选优。**

可以换会话，但新会话应先完成本文附带的考场修复交接任务，不能把当前版本当成已验收的可信考场。本文未代用户授权模型调用、管理员操作、创建会话或发送消息。

## 1. 本次实际做了什么，哪些成果保留

我读取六份交付，建立419文件独立副本；在副本运行协议等价、16条契约矩阵、26条参考组件夹具，以及输入边界/评分权威/no_reply/输出结构/失败持久化五套旧检查，全部通过。当前release登记的44份源文件散列也一致。

保留这些真实成果：公共协议提取、已有进程内评分与输入边界、已登记的文件版本、参考辅助函数及测试、开发题曝光声明、人工演练框架。**没有把26条组件测试判成假执行，也没有把fixture演练误说成冒充真实模型输出。**

另外我做了实际宿主进程、纯本地TCP替身、验收负例、材料篡改/重复导入及干净包复验。没有调用真实模型，没有修改D盘原源码/原日志，没有执行ACL变更、目录搬移或清理，没有读取真实B线或dataset。对419份原文件的末尾散列核对无变化。

由于现有完整总验收会对原树施加ACL并整树搬移，我没有原样运行它；对其安全部分和反例分别测试。这是明确限制，不将未跑的ACL矩阵冒认再次通过。

证据：[判决索引](D:/lovebrain-arena/refs/arena_v1_review_20261021/VERDICT.json)、[核心反例](D:/lovebrain-arena/refs/arena_v1_review_20261021/CORE_EVIDENCE.json)、[包与旧回归](D:/lovebrain-arena/refs/arena_v1_review_20261021/PACKAGE_AND_REGRESSION_EVIDENCE.json)、[选择标签与残留元数据](D:/lovebrain-arena/refs/arena_v1_review_20261021/ADDENDUM_EVIDENCE.json)。

## 2. P0：新隔离链路仍是合成测试路径，没有接上真正考场

位置：[宿主加载入口](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/arena/isolation/candidate_host.py:33)、[staging](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/arena/isolation/trusted_controller.py:152)、[回合执行](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/arena/isolation/trusted_controller.py:342)。

- Host固定加载`code/synthetic_candidate.py`，没有按候选目录加载机制，也没有驱动现有init/run_turn/snapshot/restore接口。`IsoRun(candidate='reference_product_v1')`改变的是标签，实际仍复制、运行同一个synthetic文件。独立运行中二者文件散列一致。
- 合成候选先写死reply“收到，正常回复。”，再将`_proxy_calls`一次性回传。控制器随后才调代理，把结果附加为proxy_results；模型结果没有返回到正在运行的真实候选生成过程，不能完成真正多阶段生成。
- 现有`lb_runner.py`没有接入trusted_controller；旧考场仍在本进程加载候选。不能用新合成宿主的检查给旧生产路径背书。
- 新控制器`score()`查询的是不存在的`lb_scorer.score_reply`，实际进入返回说明文字和reply散列的占位分支。独立传入`NOT_JSON`，没有获得真正score10/checks/veto。
- A4测试只要原始帧出现伪造gold字段就记PASS，并未断言新路径的真实评分与原题权威评分逐项相同。原进程内scoring_authority测试通过，不能补上这个缺口。

这不是考生能力没训练好，是指导书要求的“同一生产路径”尚未实现。G03/G08/G09/G11的新链路验收不能签收。

## 3. P0：隔离备份可读，修改主仓ACL的方案还制造持续残留

位置：[恢复快照](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/arena/isolation/trusted_controller.py:184)、[施加拒绝](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/arena/isolation/trusted_controller.py:197)、[整树还原](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/arena/isolation/trusted_controller.py:219)。

### 3.1 gold的备份成为旁路

`_snapshot_for_restore()`把refs、arena、episodes、cases、anchors复制到当前run_dir的`_restore_copy/`。实际deny只覆盖原树，不覆盖该副本。宿主知道自己的run_dir，与控制器同账户运行；Host.read_within的字符串白名单不能限制任意Python代码使用open。

我只使用合成canary，调用实际快照方法，再让实际宿主用A1读`_restore_copy/refs/gold_canary.json`：**返回VIOLATION_READ_OK，成功读到28字符。** 本测试没有施加原文件ACL，不能说再次测过原文件deny；它直接证明正常生成的恢复副本可被候选读取，而deny实现根本不覆盖该副本。原文件即使被deny，副本仍构成泄题路径。

同理，受信恢复源放在候选有权访问的位置不能当可信还原依据。不能靠prompt白名单证明文件隔离。

### 3.2 目录和进程名称不构成权限隔离

Popen使用同一身份启动Python，`-I`隔离的是Python导入环境，不会创建OS受限身份。当前方案对整个用户的项目文件施加deny，随后把整树改名、用旧快照换回。这会影响其他会话、正常编辑和并发运行；并发变更也可能被旧快照覆盖。它不应成为每次考试的标准操作。

现场台账包含**161条路径记录，146条路径当前存在**；这里只核对存在性，未读被锁内容，也未推断它们全是本轮新建。与口头“约22个”的范围不一致，需按台账逐条核验，不能盲删。

ISOLATION.md的管理员一键命令直接从可变JSON取路径并递归删除，没有在命令内核验最终绝对路径、恢复副本与散列、退出码及清理清单。**当前不建议执行该命令。** 先停用修改主仓ACL/整树替换的考试路径，再单独给出可审查、受路径约束的一次性管理员善后步骤。

### 3.3 攻击矩阵还有假通过条件

A6b子进程执行`open(...).read()`但不print，父进程却用“returncode=0并且stdout非空”判断读取成功。本地对可读合成文件复现：子进程rc=0、stdout空，现有判断仍称BLOCKED。这个PASS不能证明子进程读被阻止。

### 3.4 密钥桩不能替代要求中的网络限制

报告承认候选可直连任意本地socket，又把它降成非必要残差。一次自制有key桩拒绝无key/猜key，只能证明那个桩的认证分支，不能证明候选不能调用其他可达的免key上游、外传题目或绕过审计。既有项目历史端点本就存在免key配置。

本轮原D4要求并未授权自行缩小威胁模型。要么完成能实证的受限执行环境和可信代理边界，要么明确保留`DEV_TRUSTED_ONLY`限制、G08不通过；不能降格后继续宣布必要门禁全绿。

## 4. P0：总验收可以在子测试失败或证据为空时判PASS

位置：[矩阵判定](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/tools/verify_arena_v1.py:258)、[版本检查](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/tools/verify_arena_v1.py:67)。

独立负例全部在副本，不触发ACL：

| 反例 | 应有结果 | 当前实测 |
|---|---|---|
| 隔离子工具返回rc=9、不生成新日志，目录保留上一份成功日志 | 本次G08/G09失败 | 读取按文件名排序的旧日志，两门仍PASS |
| 子工具失败，最新日志为verdicts=[]、ledger_attacker={} | 缺必要检查，失败 | G08/G09都PASS：无FAIL + all([])成立 |
| release清单files={} | 缺必要文件，失败 | G01 PASS，files=0 |
| 独立选择为空、没有保管/独立性证据，只填status=READY | G06b仍未就绪 | G06b PASS |

当前44份散列一致是真事实；但验收器不核查必要集合，因此不是完整发布证明。G03还在本次矩阵运行之前读取旧矩阵的A4标记。证据必须属于本次run、相同release、明确的预期测试集合；rc非零、缺项、NOT_RUN/BLOCKED不能默认通过。

上述问题使现有`ACCEPTANCE.json`只能作为worker的运行记录，不能直接作为独立通过证明；NOT_READY判决不是仅因为G06b外部缺件。

## 5. P1：26条基线组件测试通过，不等于可比较的产品参考基线

`reference_product_v1/`只有reference_chain.py、params和说明，没有assets，也没有mechanism入口。实际调用`lb_runner.load_mechanism`得到`default:arena/lb_mechanism.py`；随后用mock执行一轮，直接报**候选资产目录不存在**，没有运行所声明的产品参考链路。

26条测试主要验证转写的常量、围栏、意图、纠正存储和ongoing辅助函数。9个multi_turn标签中包括一次把66条历史截断为60条、对map做snapshot/remove等，并不是9组“提交→状态变化→重启→下一轮完整prompt”的端到端对拍。

这份移植有价值，可以保留；但没有完整system/user装配、主链路提交和参考候选接口，也不能拿“JSON转义测试”充当图片出站/输出解析主链路验收。文档中的原产品散列还多为省略形式，应补可复算的完整冻结依据。

G04需补一个可运行参考适配器，使它与实际候选通过同一隔离runner、同一模型/输入条件比较；最终至少8个检查必须落到完整下一轮prompt及可见状态。原产品本来缺失的能力保留，不为通过题库而强化它。

## 6. P1：人工评审标签能漂移，重复录入会制造错误胜率

位置：[材料生成](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/tools/review_loop.py:69)、[录入](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/tools/review_loop.py:141)、[汇总](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/tools/review_loop.py:188)、[总验收绑定](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/tools/verify_arena_v1.py:188)。

- 独立修改MAT_01的甲原文，保留旧binding字段，G07仍PASS。它重算的是spec和当前题面，不是展示出来的完整材料内容，只比较两个自报散列字符串。
- 把同一material_id、同一reviewer的合法表单以13个文件提交，全部接收。汇总对12份材料报告**已审13、争议或未审−1、甲胜13**。多审查者可以有多张票，但票数与已覆盖材料数必须分开，重复表单也需要明确版本/替代规则。
- 生成器只从spec中的arm_x/arm_y.reply取文案，不从真实calls/reply_raw装载；本批诚实标fixture，所以不是伪造实跑。但工具还不能以此宣布生产评审原文绑定完成。
- 人设/initial_kb在episode顶层时，当前只取turn/input中的persona会丢背景；“真实历史”仅取本窗口msgs[:-1]，未恢复前序故事。意图enabled和有效许可也须对实际请求解析，不从任意字段存在推断生效。
- 汇总的“完成”由state字符串恰为“正常”的数量决定，不是实际完成/解析/动作/首选可用证据；必填标签不完整和未审状态也需单独统计，不能只要填了compare就当完整质量审查。

G07应基于冻结运行产物和材料全部实际字段绑定；导入必须拒绝漂移、错人、缺有效标签和未声明重复，报告分母来自可信运行记录。

## 7. P1：覆盖登记不是题库验收，所谓封存三重检查没有验证访问控制

12族/36点的索引可以保留。实际有5族（F3/F6/F7/F8/F12）没有control标记；F12含两条INFRA_REF，并非36点全是聊天节点。观察点文字也不能替代可执行预期或逐节点自然度/动机/时地审查。

我把NCA第二轮请求时钟改为1900年，并把L1_t07消息image改为不存在的路径，再运行覆盖生成器：仍rc=0，missing=0、field_issues=0。代码没有核对时间单调，媒体仅检查input.media而漏掉实际message.image。部分布尔检查失败也没有进入issues。

封存工具只验payload散列、kind字符串和“真实B目录不存在”。这不是“候选不能读合成封存内容/只能授权启封”的访问测试。G06a的清单/曝光声明可保留，完整封存接口还需在实际宿主路径上验证。

材料自己声明真实B线`NOT_CREATED`、独立选择未就绪，应继续诚实保留；不能以管理员或保管者名字已指定替代题目内容、隔离与冻结手续。本人未为核对这个声明去读真实B线。

## 8. P1：156文件发布包无法按说明完成离线复验

位置：[发布构建与冒烟](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/tools/build_release_package.py:81)。

我用实际构建器在副本重建：它确实返回PASS，copied=156、missing=0、violations=0。其干净目录冒烟只运行公共协议测试和import scorer，并没有执行文档宣称的总离线验收。

对这个干净包实际执行G01，就报**44项清单缺9文件**：gen_split_manifest、test_anon_binding_guards、test_failure_persistence、test_input_boundary、test_isolation_matrix、test_no_reply_protocol、test_output_structure、test_posttest_event_pairs、test_scene_assembly。

另外split_manifest、review_drill_spec、review_loop演练数据和seal_fixture也未进包或缺少可重建输入。可选择受控独立数据包，但必须写清装载步骤并可离线重建；不能把需要它们的工具装进去，然后用只import的冒烟称自包含复验通过。

G10退修。包无禁带文件、清单哈希等部分成果保留，不要求加入真实B线或用户dataset。

## 9. P1：G11不是“只等授权”，执行器和协议还没有实现

位置：[integration入口](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/tools/verify_arena_v1.py:336)、[上游调用](<local-user-path-redacted>/.codex/.chatgpt-projects/g-p-6abf72e02e2c819194f242ce5551c8b9/arena_closeout_review_20261021/package/arena/isolation/trusted_controller.py:70)。

我在副本给分支传入明确标记fixture_only的合成授权文件，未触网：入口记G11=NOT_RUN、overall=NOT_READY，却return 0。它没有运行N1–N8，故不能说获批即可执行已准备好的集成。

代理的local_tcp发送的是自制`KEY ...`文本协议，不是现有OpenAI兼容HTTP接口；只接prompt字符串，缺model/温度/图片/完整usage等，返回文本还截到500字符。与本地认证桩联通不能证明能跑真实dsf或vLLM生成。

额度也不符合attempt_started口径：在纯本地拒绝桩中，quota=1却发出了3次请求，最终used=0，因为每次上游失败会退还额度。代理只在内存累计、结束后flush，没有复用原网关的发送前持久化started/完成/失败链。进程死亡及上游不确定失败的审计风险未关闭。

授权清单中的N8“设施故障诊断调用”又与“设施失败立即停批、无补跑”冲突。删去自动故障备用，或在新的、独立批准的诊断计划中处理。端点/model/温度与本次release、验收记录应预先具体冻结，不让用户替未实现的代码补设计，也不要无必要要求迁移A100/vLLM。

**当前不建议批准8次调用：应先把真实集成入口做成可离线验证的具体程序。**

## 10. 修复只围绕一条生产链路，不再开另一套演示考场

本次不是再加一批词表、提高phase3成绩，也不是要求更大候选预算。按附件连续完成三组：

1. **真实纵向链路**：实际candidate/reference加载→生命周期→同步代理生成→可信侧真正scorer→不可覆盖日志/attempt账→下一轮/恢复。公共协议和已验旧工具复用，不另写常量回复替身当实际生成器。
2. **真正隔离与公平基线**：不修改主仓ACL，候选看不到评分/gold/备份/别的状态，不能绕代理；参考组件接成可运行主链路并补最终prompt对拍。
3. **验收/材料/题库/发布一起收口**：严格新证据与必要集合，任何子测试失败即本门失败；真材料内容绑定与去重；时间/媒体/自然故事证据；干净包执行同一完整离线入口。

先同路径mock完成，不要烧真实调用证明这些已知缺陷。现有独立选择外部交付另列；它不阻止这三组离线工程推进。需要系统权限时先交具体实现和可审查配置，一次提出最小需求，其他工作继续。

只有这些离线工程真实通过后，才提交一次≤8个实际attempt的确定集成授权清单。G11通过后可达ENGINEERING_READY；独立选择内容与保管就绪后才可达ARENA_V1_READY_HUMAN。旧52对照不复用授权或冻结版本。

## 11. 对“是否可以换会话训练学生”的明确答复

**现在不应把“考场已搭完”作为新会话前提。** 可以换会话接着做事，但先给它考场修复交接任务；正式逐轮候选研发应在工程验收后另立任务。项目规定的Worker逐轮变异与归因不等于写无人值守自动进化循环脚本。

独立选择、用户个人愿发裁决、未校准Judge都如实保留，不要求用户现在逐题填表，也不把候选答错重新算作考场工程失败。

交接任务书：[给下一会话的执行任务](D:/lovebrain-arena/refs/ARENA_V1_REPAIR_HANDOFF_20261021.md)。复核脚本在当前审核工作区保留；证据JSON记录所有本地输入/结果，外部网络仅连接本地合成TCP桩，真实模型调用0次。
