# Wave16：v13 复核退修整改报告（修复差异 + 全部验收 + 最终 52 槽清单）

日期：2026-10-21。判决：`refs/WAVE15_V13_INDEPENDENT_REVIEW_20261021/`（20 项矩阵
v13 基线 5/20）。本报告按 §8-3 一次交齐：修复差异、全部验收、最终 52 槽清单。
**零真实模型调用；52 次授权仍待用户明确给出。**

## 1. 执行令 1：候选核心四族（candidates/phase3_facts/mechanism/mechanism.py 第 2 稿）

| 缺陷（复核 §） | 修复差异 | 验收锚点 |
|---|---|---|
| 来源错号（§2） | `_src_lookup` 重写：与 `lb_assembly.render_chat` 同构——仅 HER/ME 按序占号 m0..，IDEA 不占号不进映射；speaker 直接取角色不再默认 ME | 矩阵"IDEA不能冒充m0真实来源/IDEA不占编号且HER正常入库"PASS |
| 纠正互杀（§3） | 纠正按轮处理：`prev_facts` 冻结快照 → `_detect_correction_event` 从**真实消息**抽方向（`不是X`=旧值、`改签成/现在坐X`=新值，允许空格标点）→ `_apply_correction_to_prev` 只对上一轮库做有方向 superseded → 之后才统一提交本轮新事实（同轮永不互杀）。含历史时间标记（去年/曾经/之前…）的记录不因共享编号作废；旧值未点名 → 不抹库，只记事件并保守警示。更正事件独立落盘 `kv_corrections.jsonl`（独立于摘要是否被接收，snapshot/restore 含该文件） | "同轮纠正不作废新值×2 / 不同事件历史不因共享编号作废 / 真实更正不随摘要拒绝而丢失" PASS |
| 状态误标 + ongoing 绕过（§4） | `_derive_status` 优先级重排：已发生（过去/完成标记，最高优先，不被问句降级）→ confirmed（ME 真实消息在场且含接受标记）→ offer（约定词+提议/问句证据）→ intent → event。ongoing 写回走同一推导：offer 一律改存"提议未确认（不可当已约定）"并随存储持久化、随注入渲染——作为事实消费的记忆通道全部覆盖 | "状态区分 offered/confirmed/happened / ongoing不绕过邀请未确认约束" PASS |
| 英数门误放误拒（§5） | 类型化证据门：复合时刻 HH:MM 必须逐字出现在所引原文或时间戳（日期组件不可重拼）；其余英数 token 只能来自所引原文池（含中文数词等价归一 两/二→2…），**时间戳不再进 token 池** | "日期组件不能拼出无出处钟点 / 等价两盒到2盒不丢正确事实" PASS |
| 教学例接触本批失败样例（§5 附注） | facts_evidence.md 全部示例换异题材（快递单号/报告提交/纪录片），不含 G7812 与"正点 到合肥了" | `grep -c "G7812\|正点 到合肥" facts_evidence.md` = 0 |

## 2. 执行令 2：共同工程与审稿守卫

| 项（复核 §） | 修复差异 | 验收 |
|---|---|---|
| 场景同族（§6） | `infer_scene` 改判"当前发言动作"：①"又来"移出指向词表，"你又来"（指向 ME）保留；②礼貌求许可（好吗/行吗/可以吗/好不好/行不行）不否决收尾，索取新信息（什么/几/哪…）才否决；③收尾窗口只看最后一条 ME 之后的 HER 消息（ME 重开→旧晚安失效）；④收尾锚定窗口最后一条 HER（HER 补新事→重开） | 4 项矩阵 PASS；自有套件扩至 16 案例 + 3 实测槽全过（含"你又来了"仍争执的不许过度修正护栏） |
| 冻结缺检索依赖（§7） | `candidates/phase1_retr_idf/mechanism/mechanism.py`（sha `50c095eb…`）纳入 FROZEN_SHA + FREEZE_FILES + 槽级依赖注记；`DEP_NOTE_ARMS=("A3","F")` 两臂均记 `dep_mechanism_sha256` | 矩阵"实际检索依赖纳入冻结和快照"PASS；干净 52 批实测：52/52 槽 dep 注记、两臂覆盖、值指向 phase1 |
| run_id 重复（§7） | run_id 改用完整 episode 名（原 `ep[:3]` 使 4 个 DEV 槽共 2 个 run_id） | 干净 52 批实测：**52 槽 52 个唯一 run_id** |
| CAP 文案（§7） | 拒绝文案"第 67 次"改为 `% (CAP + 1)` 动态生成 | 52 配置下文案为"第 53 次"（矩阵"第 53 次请求前拒绝"PASS） |
| 开发组序（§7） | 保留代码轮换 `[("A3","F"),("F","A3")]` 取模，修正文档（方案 v5 §2）：DEV_MEETING=A3/F、DEV_TWO_BOXES=F/A3、DEV_NEW_STATION=A3/F、DEV_ROOMMATE=F/A3 | 与代码 rotation_log 一致 |
| "逐字节等价"口径（§7） | 方案 v5 §4 改为：核心请求/配对/停批逻辑同源复用；52 矩阵与独立演练通过；旧 66 执行环境封存 | 文档层 |
| 匿名材料绑定（§7） | `gen_anon_groups.py` v3：绑定断言升级 id+speaker+text+ts 四元组（HER→PARTNER/ME→USER）；意图头匹配真实样式【持续意图】并与 episode intent 双向断言；clock/intent 缺失即 FAIL；回复直接从原始 `reply_raw.txt` 解析并与派生 all_recommended 交叉核对。新增 `tools/test_anon_binding_guards.py` 6 项守卫自测（复现复核的说话人/意图改动攻击，均拒绝） | 27 槽绑定断言 PASS + 6 守卫 PASS |
| WAVE14 报告（§7） | 旧正文（12/12、62/66 等失效结论）整体移入 `PHASE1B_WAVE14_REAL66_RESULTS_ARCHIVED_20261021.md`（标不得引用）；当前文件只保留一套有效表（调用/格式/配对签收事实 + 证据清单） | 文档层 |

## 3. 执行令 3：同批自验（全部落盘，可复核）

- **审查方 20 项矩阵**：`verify_wave15_closure.py D:/lovebrain-arena` → **20/20 PASS**
  （整改前 5/20；修复过程中三跑：15/20 → 20/20 → 20/20 稳定）；
- **52 槽守卫矩阵** `tools/test_parent_child_executor.py`：16 项全 PASS
  （干净 52 / chaos 13 停批 / 重入阻断 / 恢复禁用 / occupied / checkpoint 三反例 /
  第 53 次拒绝 / 两臂分类镜像 / 配置漂移；冻结清单断言 18→19 项同步更新）；
- **全量回归 15 套**：14 套执行全 PASS + 旧 66 矩阵按封存断言 SKIPPED（SEALED，
  符合 v13 复核 §7 口径）——phase3 14 项 / scene 16+3 / anon 守卫 6 / NCA-NCB 回放 /
  检索冻结回放 / 表达候选 / memory-off / no_reply / 输出结构 / 输入边界 /
  评分权威 / 追踪健壮 / 失败持久化 / 后测配对；
- 冻结散列已同步：方案 v5 表 19 项（含 phase1），执行器 FROZEN_SHA 19 项
  （含方案 v5 自身 sha `513f30d5…`）。

## 4. 最终 52 槽清单（方案 v5，`refs/L3B_PARENT_CHILD_PLAN_20261021.md`）

| 项 | 值 |
|---|---|
| 预算 | 52 硬上限（44 主对照 + 8 开发正反例），最多 52 started，无重试 |
| 臂 | A3=candidates/phase2_expr（父）；F=candidates/phase3_facts（子，唯一主变量=事实出处与纠正状态） |
| 共同工程 | lb_assembly.py v12 §6B + v13 §6 版（sha `e6c4b589…`），两臂同版不计候选收益 |
| 主对照 | NCA(6 轮)+NCB(5 轮) × r1/r2 × 两臂 = 44 槽，轮换 [("A3","F"),("F","A3")] 交错 |
| 开发题 | 4 题 × 两臂各 1 次 = 8 槽，组序 A3/F、F/A3、A3/F、F/A3 |
| 额度权威 | 真实网关 attempt_started；第 53 次请求发出前拒绝（文案随 CAP 生成） |
| 恢复 | 禁用（fail-fast；--resolve-failed=RECOVERY_DISABLED） |
| 冻结 | 19 项 FROZEN_SHA（机制×3、资产×2、params×2、arena×4、expr_audit、题面×6、方案 v5） |
| run_id | `l3b_52_<batch>_<arm>_<episode全名>_<rep>_t<n>`，52 槽唯一 |
| 执行器 | `tools/l3b_parent_child_executor.py`（导入复用 66 槽已验证守卫）；66 v3 配置封存断言生效 |
| 槽级依赖 | 52/52 槽记 `dep_mechanism_sha256` = phase1（`50c095eb…`），两臂覆盖 |

## 5. 边界登记（不装作已修）

- CJK 语义级编造（无中生有的地点/事件）机制层不判——本批靠 facts_evidence.md
  生成侧约束 + 下游审计，52 次实测若仍成族，下一轮升级生成侧解码约束；
- "已确认"判定依赖 ME 接受标记词表（确定性启发），拒绝/复杂答复的细化留待实测；
- 未点名旧值的更正只做保守警示不抹库（拿不准标不确定）——警示可能覆盖早于事件
  的全部带实体事实，属诚实冗余而非精确关联；
- 旧输出离线回放（135/14）不代表新 prompt 真实生成效果（复核 §9 口径沿用）。

## 6. 待授权

方案 v5 + 本报告验收全部满足 §8 准入条件。**52 次真实父子对照调用等待用户明确
"授权"口令**（不复用 66 次授权）；个人语气裁决可并行，不阻塞。
