# V3首轮直接执行单：先修共同链路，随后真实pilot，不再等资格通知

这是实施任务，不是再出一份计划。先读`REVIEW_VERDICT.md`和`INDEPENDENT_REVIEW.json`，核对磁盘是否已有更晚修复。旧P06的192生成、旧P07轨迹、旧原判词不改不重做。本单约8小时内进入修后实跑的目标，不以耗满时间验收。

## A｜约0—1h：去掉漏计重试，留真实对账

认领 `tools/run_p06_recovery_review.py` 的新版本和新输出目录；不要继续旧版本派发。

- `Reviewer.__init__(retry=4)`及`run`循环改为一次逻辑计划单元最多一次真实请求；删除同一attempt_id下反复`inner.generate`。如果保留工程重复机制，必须另有预登记单元和新的实际attempt，每次先reserve，默认关闭。
- 区分task_id、measurement_epoch、attempt_id；时间戳nonce不构成重复已消耗单元的许可。已开始但未落结果的单元标unknown_consumed，不能重派。
- 同scope下一批只派尚未开始的独立单元；旧scope实际27对预算18的问题先封存对账，关闭旧恢复单，不把差额9“退回”。追加源事件hash/时间/序号的补充记录，总管理账按27计，不伪造历史事前预留。
- 冻结unit输入、system/schema、模型/温度、SLA和驱动版本；改变SLA是新测量epoch，不在同nonce/outdir下覆盖资格文件。结果按attempt/run分文件保存，存在即拒绝覆盖；已经被覆盖的旧资格摘要只能从原网关事件派生重建，标derived。
- 每次生成保存完整raw，不截1500；展示片段另存。评审item_id完整匹配、profile/维度顺序匹配，0—3只接受真正int（拒bool和float），null给原因。别改旧判词，只新增验证覆盖层。
- 实际发送统一经过一个共用`call_once`薄原语（复用现有可信调用/预算组件）；不要在下一个脚本又手写另一套“预留一次、里面重试”。同进程用alias信号量，多进程用现有协调/轻量lease共享配额；只锁派发槽，不锁整个业务运行期，不建新daemon或平台。
- 一个假网关超时证明实际只发一次、预算记一次；重复调同计划单元被拒；已有成功样本身份不误杀。只验这条常用链路，不全平台重跑。

当前资格和小批已经完成；不要再等待“120s re-qualification后台通知”，不要继续45→120→240阶梯。

## B｜约0—4h，与A交替：修P07输入和消费，不重造runner

修改 `tools/run_p07_pilot.py`，复用现有机制入口和可信协议：

1. 意图由`{"goal":...}`改为公共入口支持的`{"text":...,"status":"ACTIVE","enabled":true}`；目标关闭/调整场景传真实状态。记录完整实际输入，而不是日志只说goal_active。
2. 用`pending_sent_me`/已消费游标将上一轮实际发送的ME消息带回下一次ctx，再接最新HER；每消息有稳定uid和逻辑ts。生成但未发的草稿不提交；重复窗口不重复写入事件。
3. 不回/失败记录control_event，不再往history添加`who=me,text=（未获回复）`。在 `build_b3_simulator_request.py` 增加显式缺席/等待事件入口，不伪装聊天。
4. 模拟输出坏/空时该轨迹标incomplete/environment_failure，不用“…”造HER。支持wait/end，没新HER就不凭空请求候选下一句。
5. 删除错误`classify_reply`语义：空对象不是合法no_reply；no_reply带正文不能先判send；may_decline不是所有轮恒true。可信侧复用当前协议/评分器动作规则。必要时等价抽纯函数，不能复制第三套逻辑。
6. `normalize_reply_contract.py`别名冲突不得丢非空备选；多出方向不隐形丢弃；缺槽补null要报告缺失，不算完整选项。raw、canonical、changes/dropped/unmapped、Praw/C/功能完整度分开。接入新P07的共同消费器版本前，用基线/候选同规则验证；原P06分不改。
7. 场景数据支持明确feedback条件，不靠把I2改名I2__hesitant破坏静态查表。builder可接scenario对象/反馈覆盖；每trajectory_id包含场景/反馈/arm/repeat；沙箱、输出、快照、事件不重名。
8. 每次API关联trajectory_id/turn/role/attempt_id；发送记录message_id；ctx/input/raw hashes入trace。summary按轨迹统计，不从全局累计events数假装该轨迹sim_calls。

## C｜离线一次验证，约1h，不发API

最小矩阵用真实入口捕获/状态读写：
- ACTIVE和ADJUSTED目标进实际user，CLOSED不进；无目标不复活旧邀约。
- 轮1发送指定ME文本，轮2ctx实际有ME原文和其uid/ts；未发送时没有该ME消息。
- 空对象判交付失败；显式合法no_reply在许可轮通过、无许可轮失败；no_reply+正文冲突被拒。
- 模拟失败不造HER；wait/end不额外调用harness；无回复是事件不是ME句子。
- 对两个不同文本的alias冲突不吞掉非空内容；规范回复值保持原样；功能损失透明。
- 发送仍只选首选；不从好备选救坏首选；两臂同消费/输入规则。
- 两反馈条件状态、路径、调用关联不串；快照恢复后能真正再处理一次正确消息。

修复前证据在`audit_current.py`已保存；它是旧行为复现，不要求把其输出“错误仍存在”当新回归通过。修后写新的通过/反例清单。

## D｜通过后直接实跑：最多96次首轮计划

按原场景素材做2场景×2反馈×3决策×2臂=24harness、最多24对方模拟；8个模拟器负对照、最多12轨迹评审，计划68、上限96，目的分账。生成固定dsf；辅助qwen共享alias槽位，不用Gemini/hy4。已有旧pilot的12harness不是这个完整四因子设计，不混表。

先8负对照或其最小代表，排除明显“任何话都接受/不回也算邀请成功”的模拟器失效。再执行真实两臂闭环；合格后扩大，失败只修当前可复现根因，不追旧P06评分全覆盖。

新的CLI/配置若需要`--feedbacks`、`--plan`、`--authorization`，实现后给真实命令；不要把不存在参数写成已经能执行。旧`--aid`默认值属于旧实验，禁止原样重跑。沿持续授权登记新有限scope，不再问用户是否继续。

## E｜等待调用期间并行做，不以资格测试阻塞

- Stage2先实际写16条×12位及参考，Stage1冻结不动；验证器已泛化，不再重复做“加argparse”。
- 依据当前原文启动一份行动/知识改进分支，记录主要假设；不得只有资料和测试没有候选变化。
- P06尾项用已有单次可用facts和必要原文复核继续，task/interaction不稳就转worker证据阅读，明确同一AI来源；不能又挂一张120s“qualification”让全部工作等。

首份交付：实际27/18差额处理；无漏计请求与协议矩阵；有效意图和真实ME回放捕获；修后pilot真实轨迹/账本或精确阻断；Stage2实际内容；下一轮候选修改。之后按主书W03—W12连续推进，不等用户说继续。
