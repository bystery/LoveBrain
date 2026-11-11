# AK03 审计（夜跑复健｜反馈=busy）

## 0. 性质与暴露
AI 合成虚构人物，无真实用户素材；作者即本 worker，dev_exposed=true；
不是独立封存集，不用于宣称泛化。

## 1. 生活动机
领域=夜跑复健；本轮她给的是busy反馈；当前有效目标=明晚六点复健快走

## 2. 连续性与回扣
锚点为单位置（1 turn）；连续性由本回合内的历史消息承担，
前史见 message_index，不引入外部轮次。

## 3. ME 历史与边界
ME 文本来自作者冻结的历史脚本，非候选生成；候选输出不写回后续轮（Phase0 边界）。

## 4. no_reply / 媒体局限
may_decline=False；无媒体输入；无多模态假设。

## 5. 参考卡质量
current_action 指明本轮要害；acceptable_actions 给3个可接受方向（允许多答案）；原子2条，逐条回指消息或 initial_kb 原文；protected/forbidden 各给边界。

## 6. 已知风险
同作者撰写，存在作者偏好；程序分覆盖不到语义；
本锚点只测反馈/目标一类机会，不代表全部能力。

## 7. 出口检查
机械验证通过（tools/validate_story_v2.py --stage anchors_20261028 --turns 1 --count 12）；语义审查由 worker 逐位复核。
