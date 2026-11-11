# W03-06 Wave3-T3 语义审查 R2（长剧篇 LRE123/LRE125/LRE126）

日期：2026-11-02 ｜ 审查轨：主程序语义审查 ｜ 范围：`benchmark/stories_v2/wave3_ext/` 中 LRE123(32轮)/LRE125(40轮)/LRE126(32轮)，源篇 `benchmark/stories_v2/wave3/E1xx/`。方法：脚本化深度对比（T1–T12 episode+reference 全字段、原子 source 引用顺序、quote 落位、hash 链）+ 全 104 轮人工通读。

## 一、逐篇判决表

| 篇 | T1–T12 逐字继承 | 续写衔接 | 出窗探针 | 旧事复活陷阱 | 时间线 | gold 合理 | 判决 |
|---|---|---|---|---|---|---|---|
| LRE123 | PASS（台词+参考卡深度相等，仅 case_id/atom_id 改名） | PASS（T13 分工细化接 T12 钩子） | PASS（T13/14/15/19/22/23/25/26/27/28 共 10 轮依赖 T02–T12 原子，引用正确） | PASS（T29 牛肉干、T31 随便喂，均 negation 原子+HER 自纠） | PASS（ts/clock 单调；周五 11-20、周六 11-28/12-05、周一 11-30、2027-01-15 周五均自洽） | PASS（forb/unknowns 齐备，无教错账） | 通过 |
| LRE125 | PASS | PASS（T13 接 T12 AA 钩子；T17 起"一圈年轮"支线承接 T12 邀约） | PASS（T21/T22 88 旧价拦截、T26 128/132、T27 鲈鱼、T29 分工、T30 唱K群规、T37/38 账本） | PASS（88 元旧价三次复活均 superseded+negation，T27 鳜鱼误记 corrected） | PASS（ts/clock 单调；11-28 周六、11-30 周一、12-26 周六、印错海报"11月30日周一"实为周一属剧情设定） | PASS（3520/720/132/1408 算术全对；结余 85 依赖未声明票价，不构成错误） | 通过（附登记项 D1、D3） |
| LRE126 | PASS | PASS（T13 日常回轨接 T08 返程；T14 提前云南菜接 T11 钩子） | PASS（T21 辣度老梗出窗、T22 旧预约复活、T23 跨店清单、T24 云点菜反向、T29 备忘录） | PASS（T22 旧预约单复活：A06/A26 双 cancelled+删除引导；T23 湘菜菜单不跨店） | PASS（ts/clock 单调；11-21 周六、11-23 周一、12-11 周五自洽） | PASS（修复 D2 后无教错账） | 通过（修复 D2 后） |

## 二、缺陷清单与处置

| 编号 | 篇/轮 | 描述 | 级别 | 处置 |
|---|---|---|---|---|
| D1 | LRE125 T11/T12（源 E125 继承） | 聚餐计划全程称"周五"，T11"明晚六点半"（周五晨发）+T12 ts 11-14(周六)"今天的聚餐"形成一日漂移 | 台词/ts 级，T1–T12 逐字保护 | 只登记：源篇即有，LRE125 audit §6 已登记并以"11-14 周六举行"口径承接、续写不复述聚餐日期；建议未来统一在源篇修（T12→11-13 或 T11"明晚"→"今晚"需级联 T13–T16 ts） |
| D2 | LRE126 T29（新轮） | HER"本周五杯结清，累计十五杯"与前两周各 5 杯的累计口径矛盾（累计应为 10 或指总债 15）；reference A34 曾把 15 当中性事实 | gold 教错账风险（reference-only） | 已修：A34 改为"她自报奶茶债累计15杯执行中"，T29 unknowns 增加口径出入说明（以 HER 备忘录为准，不代为澄清纠正）；hash 级联完成 |
| D3 | LRE125/LRE126 全部轮次 | reference.json 缺 may_decline 字段（源 E125/E126 即缺；LRE123 风格带全），no_reply 合法性失去卡面锚 | reference-only | 已修：40+32 轮按 episode 逐轮镜像补 may_decline 布尔；hash 级联完成 |
| D4 | LRE126 T04/T05/T06（源 E126 继承） | T05"明早七点二十出发"（11-07）与 T06 11-06 午"我到杭州了"矛盾 | 台词/ts 级，T1–T12 逐字保护 | 只登记：源篇 reference unknowns 已显式登记漂移并给出"按已到杭理解、不得断言出发时刻"的裁判指引，处置正确 |
| D5 | LRE123 T28 | "新粮全面上岗一周"：12-01 首顿、七日表 12-08 全换，12-18 实为 10–11 天 | 台词口语化粗略值 | 只登记（HER 自述措辞，非 gold 断言事实错误，不修） |
| D6 | LRE125 T07 | "1408 加两瓶大饮料凑低消正好"：1408 已超 1280 低消，"凑低消"表述冗余 | 台词轻微 | 只登记（T13 unknowns 已单列饮料金额未明；不构成算术错误） |
| D7 | LRE123 exposure | 缺 batch 字段（LRE125/126 有），audit 已写批次 | 表面不一致 | 只登记（validator 不要求，不影响绑定） |

## 三、修复与复验记录

- 修改文件：`LRE125/reference.json`、`LRE126/reference.json`（may_decline 镜像 + A34 归因）；哈希级联：两篇 `exposure.json`、`MANIFEST_LRE125.json`、`MANIFEST_LRE126.json`。
- 重跑 `tools/validate_story_v2.py`（逐篇 manifest）：LRE123 32/32、LRE125 40/40、LRE126 32/32 全 PASS，0 error；VALIDATION_LRE123/125/126.json 已更新；may_decline 统计与 episode 完全一致；exposure/manifest 全部哈希复验一致。
- 未触碰任何 episode 台词与 T1–T12 逐字内容；源篇 E1xx 只读未改。

## 四、统计

- 审查轮次 104（32+40+32）；出窗探针轮合计 ≥26，全部真依赖早期原子且引用正确；旧事复活陷阱 8 处（LRE123×2、LRE125×3 价/菜、LRE126×2 预约/菜单 + T21 辣度口径重申），卡面引导全部正确（negation/superseded/cancelled 标注齐备，HER 自纠不外推）。
- HER 台词零评审术语、口语化达标；may_decline/no_reply 轮（12 处）均允许不给准话且其余轮需明确回应。
- 判决：三篇均通过，可进入封存/扩量队列；D1/D4 源篇级漂移移交源篇维护轨，不阻塞本批。
