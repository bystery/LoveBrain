# W03-06 隔离会话生产清单（wave-1 十六篇脚手架）

致隔离生成会话：本清单 + `_templates/` 模板 = 你的全部开工材料。
**开工第一件事**：在 STORY_CUSTODY_LEDGER.json 事件流记录 `generation_isolation_ack`
（声明本会话未读 logs/、未读 candidates/ 失败输出、未读任何评审 JSON）。

## 允许读的输入（白名单，闭合）

1. 本清单 + `benchmark/stories_v2/_templates/`（episode/reference schema 模板）
2. `STAGE2/3/4_MANIFEST.json` 的 **story_id/title/themes 字段**（仅防撞题，不读正文）
3. `W03-06_SKELETON_PLAN_20261031.md` §4 配额清单（域/冲突型配额）
4. `STORY_CUSTODY_LEDGER.json`（仅 lifecycle 字段，防撞已消费主题）
5. 机械校验器：`tools/` 下 STAGE2_VALIDATION 相关脚本（校验器代码不含失败数据）

**禁止读**：logs/ 全部、candidates/ 全部、任何 VERDICT/REVIEW/AUDIT 文档、B3 队列。

## 每篇生产流程（七步）

1. 从 §4 配额取（域, 冲突型）槽位 → 起 story_id（E045 起顺延，勿撞 E045+ 既有号）。
2. 填 `_templates/episode_template.json`：12 位点、persona 四类、HER 台词口语化、
   每轮 HER 反馈变化标注。
3. 填 `_templates/reference_template.json`：每轮 required_memory_atoms 带逐字 quote 与
   case_id（quote 必须是台词逐字子串）；跨轮引用原子 ≥3；明示未定项进 unknowns。
4. 冲突型落实：更正/取消改期/边界/费用/第三方/启停/信息不确定——该轮的原子必须
   有 status 变化或 negation。
5. timeline.json + audit.md（含 author=AI、real_user_material=false 声明）+ exposure.json
   （readers=["author"]）。
6. 机械校验：STAGE2_VALIDATION 同款脚本 IMPORT_OK + quote 逐字核 + 时间线单调。
7. 入库 → `python tools/story_custody.py seed`（自动注册 SEALED）→ 16 篇齐后停，
   语义审查留给主程序会话（W03-03 同款流程）。

## 验收红线

- 新篇 (域, 主题集, 冲突型) 三元组与全部既有篇至少一元不同（W03-06 去重规则）。
- 台词不得出现任何评审术语/错误类型词汇（污染特征）。
- quote 与台词逐字一致——改写即废（机械校验会拦）。
- 不虚构真实人名/电话/住址；全部合成人物。
