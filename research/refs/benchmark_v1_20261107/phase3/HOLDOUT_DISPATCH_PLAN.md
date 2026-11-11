# HOLDOUT_DISPATCH_PLAN · 120 篇 HOLDOUT 生成派发计划（2026-11-10）

> 前置门：SELECTION 审计 C1-C4 全 PASS（或 FAIL 修复复验 PASS）后启动。
> 并发限制：后台代理同时 ≤2（主会话占 1 槽）。

## 派发策略

### 阶段 A：作者 H01-H06（6 批 × 10 篇 = 60 篇）
分 3 轮，每轮 2 个并发代理：
- 轮 1：H01(E401-E410 T01/T02) + H02(E411-E420 T02)
- 轮 2：H03(E421-E430 T02) + H04(E431-E440 T03)
- 轮 3：H05(E441-E450 T03) + H06(E451-E460 T04)

### 阶段 B：作者 H07-H12（6 批 × 10 篇 = 60 篇）
- 轮 4：H07(E461-E470 T05) + H08(E471-E480 T06)
- 轮 5：H09(E481-E490 T07) + H10(E491-E500 T08)
- 轮 6：H11(E501-E510 T09 含24/32轮) + H12(E511-E520 T09/T10 含4组孪生)

### 阶段 C：标注 HB01-HB12（12 批 × 10 篇）
标注在作者完成后接力，分 6 轮：
- 每轮 2 个并发代理，每个代理标注 10 篇

### 阶段 D：审计 HC1-HC4（4 批 × 30 篇）
- 4 个独立审计代理，每个覆盖 30 篇

## 关键约束
- H 段会话禁读 DEV/SELECTION 全部正文
- 直写金库 D:/lovebrain-arena_holdout_vault/wave6_holdout/<EID>/
- 名字从 NAME_POOL_V1.json holdout_assignment 取（120/120 唯一）
- 孪生组 E513-E520 全在 H12（同会话保证同构）
- T09 长剧 E505-E512 在 H11（24/32 轮状态机）
- validate_story_v2 --root D:/lovebrain-arena_holdout_vault 验证金库篇

## 验证链（每批完成后）
build_wave_manifest --root D:/lovebrain-arena_holdout_vault --wave wave6_holdout --turns 12/24/32
→ validate_story_v2 --root D:/lovebrain-arena_holdout_vault ×3
→ build_gold_sidecar（金库篇 hash 进 manifest）
→ dedupe_wave --isolated
→ check_name_uniqueness --root D:/lovebrain-arena_holdout_vault
