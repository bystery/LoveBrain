# 全仓 CancellationException 审计结果

> 本文件由 `python3 scripts/audit_cancellation.py --write docs/CANCELLATION-AUDIT.md`
> 生成，不要手改；CI 里 `--check` 用的是同一套判据。

复核报告 §6 S2-07 点名："没有全仓 CancellationException 审计结果和 lint/static rule 防回归"。
这里既是审计结果，也是那条静态规则本身。

## 判据

扫描范围：`app/src/main` 下所有 `catch (:Exception)` / `catch (:Throwable)` / `runCatching {`。

| 判定 | 含义 |
|---|---|
| PROTECTED | 块内、紧邻上下文或同一条 try 链上显式处理 `CancellationException` |
| WAIVED | 站点旁写了 `cancel-safe:` + 非空理由，人工复核过 |
| SUSPEND-FREE | 受保护代码段内没有挂起点，且所在函数不是 suspend：吞异常不等于吞取消 |
| NEEDS_REVIEW | 可挂起处吞掉取消信号 —— `--check` 直接失败 |

挂起判据由两部分组成：全仓 `suspend fun` 名单（本仓库 135 个）+ 协程/并发库确定的挂起 API（withContext / delay / launch / withLock / collect / await / emit …）。

## 结果

| 判定 | 站点数 |
|---|---:|
| PROTECTED | 53 |
| WAIVED | 2 |
| SUSPEND-FREE | 114 |
| NEEDS_REVIEW | 0 |
| 合计 | 169 |

`--check` 结论：**PASS**（没有未处置的可挂起吞取消站点）

## 逐站点

| 判定 | 位置 | 挂起点 | 豁免理由 |
|---|---|---|---|
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/LoveBrainApp.kt:48` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:275` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:478` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:514` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:543` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:620` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:634` | executeRequest | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:752` | executeRequest | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:829` | executeRequestWithCode | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:1008` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:1140` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/FeedbackCaseRepository.kt:45` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/FeedbackCaseRepository.kt:57` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KbArchiveTransfer.kt:77` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KbArchiveTransfer.kt:135` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeMigrator.kt:75` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeMigrator.kt:139` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeMigrator.kt:161` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:87` | backupIfNeeded | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:108` | backupIfNeeded | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:134` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:145` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:176` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:221` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:224` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:418` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:477` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:515` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:575` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:611` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:622` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:792` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:806` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:853` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:881` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:887` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:904` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1013` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1028` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1070` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1081` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1114` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1377` | updateStageUnlockedStrict | — |
| WAIVED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1406` | delete | 这里只有 java.io.File 读写（delete()/writeText()），协程取消不会从这里抛出 |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1423` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1438` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1447` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1667` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1776` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1785` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1806` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1820` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/SecurePrefs.kt:29` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/SecurePrefs.kt:269` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:131` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:162` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:235` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:303` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:346` | collectStream | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:360` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:424` | collectStream | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:502` | collectStream | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:508` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:568` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:581` | collectStream | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:651` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/IntentPolicy.kt:55` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/IntentPolicy.kt:58` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:133` | getLessonCount | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:165` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:271` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:291` | generateRaw | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:317` | generateRaw | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:369` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:462` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/OngoingContextSelector.kt:313` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/OngoingContextSelector.kt:398` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/PromptBuilder.kt:687` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/PromptBuilder.kt:965` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:192` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:265` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:315` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:394` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:410` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/TopicRecorder.kt:668` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/TopicRecorder.kt:671` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/model/Models.kt:61` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/model/ProfileUpdate.kt:155` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/model/ProfileUpdate.kt:203` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/model/ProfileUpdate.kt:400` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/CopyCaptureService.kt:122` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/CopyCaptureService.kt:158` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/CopyCaptureService.kt:193` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/CopyCaptureService.kt:245` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/CopyCaptureService.kt:253` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/CopyCaptureService.kt:277` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/CopyCaptureService.kt:283` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/CopyCaptureService.kt:342` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/CopyCaptureService.kt:372` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:208` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:302` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:480` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:552` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:567` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:577` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:619` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:674` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:690` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:765` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:799` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:891` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:992` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:1016` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:1049` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:1067` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:218` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:448` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:476` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:522` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/bubble/FloatingBubble.kt:104` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/feedback/FeedbackCasesScreen.kt:129` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/panel/reply/VoiceRewrite.kt:198` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/panel/reply/VoiceRewrite.kt:199` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/panel/reply/VoiceRewrite.kt:324` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/panel/reply/VoiceRewrite.kt:344` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/util/L.kt:21` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/util/L.kt:39` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/util/OpenAiChatEndpointResolver.kt:40` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/util/TimeFmt.kt:20` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/FeedbackCaseController.kt:140` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/FeedbackCaseController.kt:168` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:115` | setActive | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:128` | updateDisplayName | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:142` | delete | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:178` | runOnboardingCreation | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:192` | withContext | — |
| WAIVED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:220` | createKnowledgeBase | 同上——取消经 finishCreation 转成 Cancelled，不伪装成 CreateFailed |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:240` | create | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:273` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:305` | getActive | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:320` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:684` | ensureInitialKnowledgeBase | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:725` | collect | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:857` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:949` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:966` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1016` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1316` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1453` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1588` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1645` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1720` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1751` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1805` | getActive | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1857` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2183` | readIntent | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2198` | saveIntent | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2247` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2310` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2366` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2535` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2626` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2635` | read | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:112` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:134` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:166` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:505` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:511` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:554` | — | — |
