# W5_BATCH_REPORT · wave5_selection 批 2 建设总账（随进度更新，2026-11-09）

## 0. 批次口径注记（先登记，避免审计/复核误判）

1. **feedback 字段约定**：本批存在两种形态——S1/S2/S3 沿用 E117（wave3）结构样例的
   填充形态（作者侧剧情状态注记 {状态,变化}）；S4 按简报模板写 `{}`。机械核查：
   `feedback` 在 arena/（runner/assembly/scorer）、validate_story_v2、build_gold_sidecar
   中**零消费**（grep 0 命中，2026-11-09 实测）——纯惰性元数据，不进运行时、不进评分、
   不影响哈希链（哈希在冻结时按实际内容计算）。登记为波内惰性差异，不作缺陷、不回改
   已冻结篇目。
2. **名字唯一性**：全部 HER/ME 名取自 `phase3/NAME_POOL_V1.json`（selection_assignment，
   320 名与全库 341 已用名零交集、批内零重复）；配角取 third_party_pool（允许跨篇低频复用）。
   机械复核：`python -I tools/benchmark_v1/check_name_uniqueness.py --wave wave5_selection`。
3. **时间跨度**：简报口径"2026-11 月内为主"允许数天/数周跨度，个别篇延伸到 2026-12 上旬
   （跨月回指设计需要），星期-日期一致性由审计逐篇验算。
4. **S4 派发名字更正事件**：协调者派发提示词曾误写 4.5 篇名字（与池冲突），已在代理
   写盘前发 SendMessage 更正（以 NAME_POOL_V1.json 为唯一真值）；已写盘 15 篇 persona
   与池逐字一致（程序核查 0 mismatch）。

## 1. 作者批账本（S1–S8 × 5 篇）

| 会话 | 篇目 | 状态 | 交付报告要点 |
|---|---|---|---|
| AUTHOR-S1 | E339(T01)/E340(T02)/E341(T02)/E347(T03)/E351(T04) | DONE 2026-11-09 | 16 项自检全过；T02 信号梯度+反诱惑位齐 |
| AUTHOR-S2 | E342(T02)/E343(T02)/E348(T03)/E352(T04)/E366(T07) | DONE 2026-11-09 | 禁词 0 命中；E366 含报错数/除数未知/三手转述链 |
| AUTHOR-S3 | E344(T02)/E345(T02)/E349(T03)/E353(T04)/E367(T07) | DONE 2026-11-09 | E367 账目闭环；自查修复 1 处账目冲突后复验过 |
| AUTHOR-S4 | E346(T02)/E350(T03)/E354(T05)/E355(T05)/E368(T07) | DONE 2026-11-09 | 派发名字更正事件：代理写盘前自查池子发现不一致并按池执行（E346 阮星回/关山月、E350 简云溪/龙在野、E354 齐鹿鸣/常青野、E355 蒋星晚/车前子、E368 程苡宁/李慕白），零污染文件 |
| AUTHOR-S5 | E356(T05)/E357(T05)/E360(T06)/E361(T06)/E369(T07) | DONE 2026-11-09 | T06 四类型指令位齐（确认/改写/撤销/代传）；自查修正 1 处 intent 超限；数字全自洽 |
| AUTHOR-S6 | E358(T05)/E359(T05)/E362(T06)/E363(T06)/E370(T07) | DONE 2026-11-09 | T06 两篇四类型全覆盖；E370 初版 10 轮自查补齐 12 轮复验过；feedback={}（S4 口径，登记 §0-1） |
| AUTHOR-S7 | E364(T06)/E365(T06)/E371(T08)/E372(T08)/E374(T09,24轮) | 待派 | |
| AUTHOR-S8 | E373(T08)/E375+E376(T10-G3 目标)/E377+E378(T10-G4 时间) | 待派 | 孪生两对同会话 |

## 2. 标注批账本（B1–B8，随作者批完成后接力）

| 会话 | 篇目 | 状态 |
|---|---|---|
| ANNOTATOR-B1 | E339/E340/E341/E347/E351 | **DONE 2026-11-09**：230 原子 strict 0 失败（曾报 92 处 markers/逐字问题两轮自修复归零，含 1 处真错 E341_T01_A03 误串 E347 素材）；T02 门×2；required 40 处全部来源早于本轮 |
| ANNOTATOR-B2 | E342/E343/E348/E352/E366 | **DONE 2026-11-09**：187 原子 strict 0 失败（12 类硬门自检脚本）；T02 门×2 九项齐；E366 登记方/事实方分立 + 状态机配对；1 处素材瑕疵（E342 周六/周日一日游移）如实登记 audit §6 |
| ANNOTATOR-B3 | E344/E345/E349/E353/E367 | **DONE 2026-11-09**：210 原子 strict 0 失败；T02 门×2（8 键全填）；E367 三类陷阱分立（错挂归属/设计内错账/口径两说）；88 处 markers 格式自修复归零 |
| ANNOTATOR-B4 | E346/E350/E354/E355/E368 | **DONE 2026-11-09**：211 原子 strict 0 失败；E368 四陷阱分立（双计定金 erroneous 原子方向显式）；E354/E355 T05 分档位齐 |
| ANNOTATOR-B5 | E356/E357/E360/E361/E369 | **DONE 2026-11-09**：239 原子 strict 0 失败；E369 三方口径 4 conflict 原子+unknown 双锁；68 处 markers 自修复归零 |
| ANNOTATOR-B6 | E358/E359/E362/E363/E370 | **DONE 2026-11-09**：243 原子 strict 0 失败；E370 130≠140 报错数 erroneous 登记；5%/1% 口径不设 5% 禁令（E333 反向局限规避） |
| ANNOTATOR-B7 | E364/E365/E371/E372/E374 | **DONE 2026-11-10**：176 原子 strict 0 失败（E374=57原子/24轮 T09 跨月状态机，cancel-guard 修复 1 处 required→current turn）；audit.md 由 B7-AUDIT/B7-AUDIT2 代理补写（首派崩溃前 reference.json 已全写完，audit.md 未写） |
| ANNOTATOR-B8 | E373/E375/E376/E377/E378 | **DONE 2026-11-10**：197 原子 strict 0 失败；孪生两对 E375/E376(G3目标) + E377/E378(G4时间) 同会话标注对称验证过 |

## 3. 审计与验证链（全部标注完成后）— **机械链全绿 2026-11-10**

- 标注总计：40 篇 / 1,693 原子 / 274 required / 1,693 affirm_markers（0 缺失）
- 审计进度（2026-11-10）：
  - **C1 (E339-E348) DONE**：8 PASS + 2 FAIL → 修复完成（C1-01 E342 T09 时钟 11-13→11-14 + T10 时钟调整；C1-02 E345 "下月"→"这个月底" 6 处；C1-03 UNCERTAIN 不阻塞）
  - **C2 (E349-E358) DONE**：9 PASS + 1 FAIL → 修复完成（C2-01 E349 跨篇名"封野青"→"龚自明" 3 原子+1 forbidden）
  - **C3 (E359-E368) 进行中**
  - **C4 (E369-E378) 进行中**
  - 修复后重验：validate 39/39 PASS + sidecar 0 conflicts + 64 tests green
- 机械链结果（2026-11-10 实跑）：
  - validate_story_v2 ×2：12T=39/39 PASS + 24T=1/1 PASS = 40/40 **0 错误**
  - dedupe_wave：223 篇全库扫描，0 近重簇
  - lineage_check：26 组，0 违规，0 警告
  - check_name_uniqueness：80 名，0 内重复，0 跨库碰撞
  - custody_split_guard：243 行 / 123 定稿 / 120 PENDING，**0 违规**（40 警告=新题待 seed 账本，不阻塞）
  - build_gold_sidecar：1,693 原子 / 1,693 quotes strict_hit / **0 冲突** / Marker QC **0 violations**
  - 测试套件：64 passed / 0 failed（UNTOUCHABLE 哈希重钉 15 文件 + repin_log）
- 工程修复登记：
  - timeline 格式升级（upgrade_wave5_timeline.py）：wave5 简化格式 → 验证器期望格式（story_id/turn_clocks/message_index/events.source_turn），40 篇全转，原文件 .wave5orig 备份
  - audit.md 标题格式修复：20 篇章节标题缺少点号或用"与"而非"/"，批量修正
  - exposure 补字段：runner_copy/annotator/frozen_at/hashes 40 篇补齐
  - initial_kb source key 字段：11 原子 message_id→key 修复
  - E374_T22_A02 required_this_turn=true+current turn source → 改 false（同轮 cancel-guard 非跨轮回指）
  - 分派表 V3 回写：E339-E378 split=PENDING→SELECTION, status→BUILT_VALIDATED
  - build_gold_sidecar.py：WAVES 加 wave5_selection + sys.path 加 tools 目录（story_custody 导入修复）
- 分派表核销：E339–E378 行已回写 split=SELECTION/status=BUILT_VALIDATED。
