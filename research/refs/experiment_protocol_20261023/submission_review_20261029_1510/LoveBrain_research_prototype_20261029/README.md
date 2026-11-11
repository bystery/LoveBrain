# LoveBrain 研究原型（2026-10-29）

先读 review/SUBMISSION_REPORT.md。状态：可运行研究原型，完整目标REWORK。
本包不是“全面优于原版”的生产发布；不含原用户聊天导出、API密钥、有效调用授权或巨型历史日志。

## 包含什么

- 冻结r4、差额修复E3和原版的实际候选依赖；保留原目录结构与身份。
- 同一现有runner/网关/预算入口，未另造训练框架。
- 4篇虚构开发故事、72张卡、144份F3实际模型请求/答卷、72份匿名裁判结果。
- 独立重算、错误来源、知识链缺测说明和条件化的E3重放证据。
- 10本关闭预算账本，仅作审计，不可复用调用。

## 在干净目录离线演示

本次实测Windows、Python3.12.4；核心演示不需要第三方Python库。解包进入本目录后：

```
python -X utf8 -B tools/iso_run.py --episode benchmark/episodes/N01.json --candidate action_policy_r4_guard_e3 --adapter mock --quota 12 --out runs/demo_N01
```

结果在runs/demo_N01。mock只证明12轮装配/消费/状态/审计链路，mock分数没有质量意义；不要覆盖已有运行目录。

受控核心功能测试（零网络，使用脚本给定模型输出，非模型能力成绩）：

```
$env:LOVEBRAIN_ROOT=(Get-Location).Path
python -X utf8 -B tools/core_function_probe.py
```

该测试验证真实消息/草稿分离、事实更正、1400CU之外的投影路径、关闭目标与新进程恢复。

## 真实服务

本项目实测服务为OpenAI-compatible的dsf，temperature=0.7。真实调用沿用tools/iso_run.py的openai_compat适配器；必须另提供合法、有限额的授权单，不能使用证据中的已关闭账本。此包不自动发请求、不自动续费。

## 成绩归属

72对F3成绩属于action_policy_r4_guard_final；E3只有局部真实回归和离线等价重放。旧F1混合历史答卷不属于r4对原版。匿名裁判不等于人工验收；故事作者看过候选结果，不是独立未见测试集。

数据为虚构聊天，仍请按导师/学校要求处理公开范围。不要把本包中的prompt或评审卡用于声称独立封存测试。
