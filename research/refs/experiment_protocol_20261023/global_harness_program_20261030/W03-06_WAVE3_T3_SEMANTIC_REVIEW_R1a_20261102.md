# W03-06 WAVE3 T3 语义审查 R1a — LRE117（2026-11-02）

## 判决：PASS（无需修复，零哈希级联）

- ①T1–T12 逐字继承：程序化深度断言 12/12 全等（仅 case_id 改名），persona/initial_kb 亦相等；runner_episode==episode。
- ②续写质量：T13 兑现 T10 单号钩子，六处新冲突（T16/17/19/24/28/33）均有原子承载；HER 台词全口语、零评审术语，媒体均转述。
- ③出窗探针：T21 四点半↔T11、T31 滨江路↔T09、T25 五十五↔T07、T29 八点↔T06/T02，引用数字日期核对无误。
- ④旧事复活陷阱三处（T15/T25/T29）均以 superseded/cancelled+unknowns 标注，卡面 acceptable_actions 覆盖"重新确认"与"按八点整"两类正确处置。
- ⑤时间线：clocks 单调且与 episode 全等，message_index 63 条全匹配；2026-11-01=周日核实，11-07/14/21/28、12-05 均为周六。
- ⑥gold：40 轮多选 ≥2、may_decline 与卡面一致，无教 AI 说错话。

## 登记项（不改动）

1. T16 HER"比上次那五十五还便宜十块"：按人差 5、两人合计差 10，口语歧义；卡面未引导复述该数，不构成金标错误。
2. T36 HER"一共收三百二"实为 80+160+160=400：疑似有意账目陷阱（卡面含"发现差一笔如实指出"合法动作、原子 status=uncertain）；建议裁判指南注明不得把 HER 报数当真值。

## 校验

validate_story_v2 --stage wave3_ext --turns 40 --count 1 --manifest-name MANIFEST_LRE117.json：stories=1 passed=1 errors=0。MANIFEST/exposure 哈希与落盘一致。
