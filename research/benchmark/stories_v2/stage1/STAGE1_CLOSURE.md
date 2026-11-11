# P02 首批 8 条新故事 — Stage 1 收口

日期：2026-10-27。范围：`benchmark/stories_v2/stage1/`。性质：**合成虚构**，不是用户真实素材；作者 QoderWork worker-AI，独立评审无（同 worker 自检）。本批是 dev 候选，未用于 P06 调参，不能据此声称泛化。

## 1. 交付

| 项 | 实测 |
|---|---:|
| 故事 | 8 |
| 每故事 turns | 8 |
| 固定 input 位 | 64 |
| case_id | E013_T01–E020_T08（无重号） |
| 参考卡 | `GRADING_CARDS_STAGE1.json`，64 张 |
| 参考原子 | 64 条（其中本轮必需 6 条） |
| 意图修订 | 9 处（E014/E016/E017/E019/E020） |
| may_decline 轮 | 4（E018_T03/T04/T07/T08） |
| 机械验证 | 8/8 故事、64/64 case、0 错（`STAGE1_VALIDATION.json`） |
| 零网络 capture | 128/128 started/completed、failed 0、order+bijection、P64/C64、real_model_calls 0、16 residents |

每故事目录：`episode.json`、`reference.json`、`timeline.json`、`exposure.json`、`audit.md`；runner 副本在 `benchmark/episodes/E013–E020.json`（与 canonical episode 逐字节一致，因为现有 executor 的 `load_episode()` 固定读该目录）。canonical 仍是故事目录。

## 2. 覆盖

- 破冰：E013 陶艺课同桌。
- 旧同学重联/工作合作：E014。
- 普通朋友低落、无恋爱目标：E015。
- 玩笑触边界→认真：E016。
- 主动邀约/对方犹豫：E017。
- 疲惫/话题枯竭/允许不回：E018。
- 品味分歧/诚实表达：E019。
- 长期回扣/计划不确定/适时结束：E020。

不是每轮都是考点：多数轮是普通对话；`required_this_turn` 只在必须用旧事实的 6 个位点标 true，其余原子标 optional。

## 3. 验证口径

`tools/validate_story_v2.py` 机械检查：8 turns；case 唯一；HER/ME 时间不晚于 clock；clock 不回退；episode 无 gold/未来消息/“考察/观测/评分”等测评词；`initial_kb` 只含 `me/her/warmth/style/stage/lessons` 且全为标量字符串；reference 字段齐全；每轮 ≥2 个可接受动作；`source.quote` 在对应消息或 initial_kb 中逐字存在；required atom 的 source 必须早于当前轮；protected expression 必须在当前 HER 消息中逐字出现；timeline 消息索引与 episode 一致；exposure hashes 一致；audit 八节齐全；runner copy 与 canonical episode 深等。

## 4. 能力差异证据（零网络 capture）

- 128 次 capture 全部由同一 `execute_plan` 逐 slot 交错执行；`attempt_started=completed=128`；`execution_order_verified=true`、`dispatch_bijection=true`；`real_model_calls=0`。
- `turns_parent=64`、`turns_candidate=64`：两臂都在新故事上真实装配并产出，不是空跑。
- 16 个 `isolation_sentinel.json` 的 `run_id` 去重后 **16/16**（8 故事×2 臂），实际状态隔离成立。
- **遗留指标缺陷**：summary 的 `state_isolated=false` 来自执行器内写死的 `len(set(run_ids)) == len(keys) == 6`（旧 3 故事×2 臂），不是本批真实串状态；本批实际是 16/16 distinct。该字段需在后续修执行器时改为按 `len(keys)` 比较；本批不改共享执行文件，登记为已知缺陷。
- `cross_arm_user_len_diff_max=2251` CU：v6_integrated 的 user 侧装配确实比 reference 长，来源是目标/动作/证据记忆段；不是把 system 换掉。

## 5. 暴露与限制

- 全部 8 条为合成虚构，`real_user_material=false`；无真实个人数据。
- 作者与读者都是同一 worker-AI；`independent_review=false`；不是独立封存集。
- `frozen_at=null`、`experiments_used=[]`：尚未冻结、尚未用于任何正式实验；P06 不使用本批。
- 8 turns/故事，不是最终 12 位/故事；64 自然故事、8 分支、8 长链是后续周期目标，本批不冒充。
- 纯文本；无图片/音频/媒体引用；E018 显式覆盖 no_reply/只回晚安。
- 参考卡不设唯一回话短语；只给动作、事实、未知、保护表达与禁断言。

## 6. 证据索引

- `benchmark/stories_v2/stage1/STAGE1_MANIFEST.json`（文件哈希、case 列表、覆盖）
- `benchmark/stories_v2/stage1/STAGE1_VALIDATION.json`（8/8、64/64、0 错）
- `benchmark/stories_v2/stage1/GRADING_CARDS_STAGE1.json`（64 卡）
- `logs/p02_stage1_capture_e_20261027/BATCH_SUMMARY.json`（128/128、P64/C64、0 真实调用）
- `logs/p02_stage1_capture_20261027/`（早期 N-ID capture，turns=0，已被 E-ID capture 取代；保留作排障证据，不当作正式结果）
