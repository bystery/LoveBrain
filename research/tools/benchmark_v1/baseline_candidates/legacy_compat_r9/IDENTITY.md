# LEGACY_COMPAT_R9 — 身份卡

- 组别：LEGACY 的输入适配变体（Benchmark V1 Phase 2 §9；**经输入适配的历史版本，非原版**）。
- 被适配对象：`candidates/action_policy_r9_slim`（LEGACY，完整旧 harness 生命周期，
  运行时闭包 r9_slim→r2→r1→v6_goal_context→v5→reference_product_v1）。
  **原候选一个字节未改**（UNTOUCHABLE 测试守护）。
- 崩溃点：wave 系（wave1/wave2/wave3/wave3_ext）episode 的 persona.me/her 为 dict；
  旧闭包 `action_policy_r1/mechanism/mechanism.py:270 _seed_facts` 对其调 `.strip()`
  （`_seed_block` L319 同型消费）→ AttributeError，init 即崩。
- 适配点清单（仅此三处，其余全透传）：
  1. `adapt_episode_meta`：init 前把 **dict 型** persona.me/her 确定性降为旧版
     字符串形式（模板：`name，[age岁，][occupation，]traits 逐项，底线：boundaries
     逐项`；已知字段外任何额外键以 canonical JSON 追加，语义零丢失）；
  2. `init` 的 assets_root 转发：一律使用 `candidates/action_policy_r9_slim/assets`
     （真实资产唯一来源；本目录 `assets/system_prompt/*.md` 仅为满足
     `require_candidate_assets` 入口检查的占位指针文件）；
  3. 其余接口（`run_turn/snapshot/restore/set_memory_access/build_system_prompt`）
     为对原机制模块对象的直接绑定，无任何包装逻辑。
- 与原版差异（诚实清单）：
  - str 型 persona 输入：逐字节等价（persona/initial_kb/资产/模块全部原样，测试
    `test_legacy_compat_str_persona_equivalence` 以 init 状态 + 首轮 calls.json
    system/user 逐字节相等断言）；
  - dict 型 persona 输入：原版崩溃、本变体可跑通——不存在可对拍的"原版 dict 行为"，
    语义保证仅为"persona 全部语义字段无损进入旧闭包的字符串通道"（转换规则写死
    于 `persona_struct_to_str` 注释）；
  - 身份哈希不同（本目录含包装层与占位指针）；机制行为面与 r9_slim 相同模块对象。
- 哈希（候选依赖闭包，`four_arm_runner.candidate_identity_sha256` 口径）：
  - `legacy_compat_r9`: 见 `refs/benchmark_v1_20261107/phase1/ARM_REGISTRY.json`
    LEGACY_COMPAT_R9 条目（写入值以该文件为准）；
  - 被适配对象 `action_policy_r9_slim`: `c57c093608060d740f16867604885daa6eef05d95f15d569fce0e2d04e55c7e8`
    （ARM_REGISTRY 既有 LEGACY 记录，未变）。
- 禁止：改 `candidates/action_policy_r9_slim` 任何文件；把本变体冒充原版 LEGACY；
  在报告与登记中省略"经输入适配"前缀。
