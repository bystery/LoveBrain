# R6 — 解析器冒号行误拆回归：复现与修复（closeout-20261106）

日期：2026-11-07 ｜ 基线：D:/LoveBrain `closeout-20261106` @ d9ff3db ｜ 范围：仅 `BatchTranscriptParser.kt` + `BatchTranscriptParserTest.kt`（BatchImportSheet/ReplyInput 等 R1 并行轨文件未触碰）

## 1. 复现结论：审计属实

审计案例（冒号行格式）：

```
小美：周末见面
地址：西湖边
我：好
```

修复前 JVM 单测实测（worktree closeout-r6-tmp，`BatchTranscriptParserTest`）：
`auditCaseColonFormatAddressLineIsContinuationNotNewSpeaker` 失败，
`expected:<2> but was:<3>`——"地址：西湖边"被当成发言人叫"地址"的新条目。

根因：`step()` 的冒号行分支里 `allowNewSpeaker = !openExists || openOrigin==COLON/NUMBERED`，
一旦当前条是冒号/编号格式开的，**任何**冒号行都视为记录分隔符。T2/W8 的"未知昵称归并进当前条"
保护只对 `openOrigin==BLOCK` 生效，冒号格式多行正文里的字段行（地址/电话/备注…）落在保护缝隙外。
编号转发里 `1. 小美：周末见面` 后接 `地址：西湖边` 同样误拆（同根因，已一并覆盖）。

## 2. 修法（宁可不误拆 + 标注，T2 原则）

冒号行分支新增续行启发式，满足以下全部条件时不拆条、并入当前条并写 `Result.warnings`
（warning 由 BatchImportSheet 预览界面原样展示给用户裁决，未改 UI）：

1. 已有进行中条目（`openExists`）且是冒号/编号格式开的；
2. 冒号前缀 ∈ `CONTENT_LABELS` 常见字段词表（地址/地点/时间/日期/电话/手机/座机/备注/
   附/附言/邮箱/邮编/网址/链接/姓名/性别/年龄/坐标/QQ/微信）；
3. 该名字从未作为发言人出现（`name !in seenSpeakers`）——昵称真叫"地址/时间"且已出现过时
   照常换条（两难时优先不误拆 + 标注，不吞真实发言人）；
4. 冒号后有非空内容（"地址："空内容维持原路径，交给"有发言人无正文"警告）。

warning 文案注明"疑似上一条正文的续行…未拆为新发言人；如确是新发言人，请在预览中手动处理"。

## 3. 新增夹具（8 个，类内正确插入；31→39 全绿，0 失败）

- 审计案例本体（2 条 + 1 警告）；连续字段词行（电话+备注并入）；字段词接 URL（`链接：https://…` 并入、URL 原样保留）；编号转发 `1. 小美：…` + 字段词续行；
- 反例：真实短昵称"她/他"仍换条；昵称撞字段词（首行"时间：…"登记后，再次出现照常换条）；
- 边界：首行即字段词（无可并入上一条，维持原行为当新记录）；块格式字段词行既有保护回归。

## 4. 交付与验证

- worktree D:/wt-r6（分支 closeout-r6-tmp）编译 + `BatchTranscriptParserTest` 39/39 全绿；
- 已复制回主仓（未 commit，与 R1/R2/R5 并行轨未提交改动共存，互不重叠）；
- worktree 与临时分支已清理（`git worktree list` 已无 wt-r6）。

## 5. 仍需真实样本项（真机验收 T2 清单补充）

1. 真实微信合并转发复制里，多行正文第二行为"字段词：内容"的实际频率与写法（全角/半角冒号、有无空格）；
2. 真实联系人昵称叫"地址/时间/备注/链接/微信"等字段词的样本（验证 seenSpeakers 优先路径）；
3. 一条消息内既有正文冒号句又有字段行（如"今晚时间：8点"整句）——当前会整行并入，需确认无误并；
4. CONTENT_LABELS 词表按真实样本增删（当前为封闭枚举，不做开放猜测以防误吞真实昵称）。
