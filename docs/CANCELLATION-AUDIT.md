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

挂起判据由两部分组成：全仓 `suspend fun` 名单（本仓库 127 个）+ 协程/并发库确定的挂起 API（withContext / delay / launch / withLock / collect / await / emit …）。

## 结果

| 判定 | 站点数 |
|---|---:|
| PROTECTED | 52 |
| WAIVED | 2 |
| SUSPEND-FREE | 113 |
| NEEDS_REVIEW | 0 |
| 合计 | 167 |

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
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:66` | backupIfNeeded | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:87` | backupIfNeeded | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:113` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:124` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:155` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:200` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:203` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:379` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:438` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:476` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:536` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:572` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:583` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:753` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:767` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:814` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:842` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:848` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:865` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:981` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:996` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1038` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1049` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1082` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1345` | updateStageUnlockedStrict | — |
| WAIVED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1374` | delete | 这里只有 java.io.File 读写（delete()/writeText()），协程取消不会从这里抛出 |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1391` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1406` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1415` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1646` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1789` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1798` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1819` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1861` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1953` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1975` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:2246` | — | — |
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
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:120` | getLessonCount | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:143` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:241` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:259` | generateRaw | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:281` | generateRaw | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:358` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:460` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:475` | generateReflectSuggestionSuspend | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/OngoingContextSelector.kt:313` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/OngoingContextSelector.kt:398` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/PromptBuilder.kt:687` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/PromptBuilder.kt:1196` | — | — |
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
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:697` | ensureInitialKnowledgeBase | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:738` | collect | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:870` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:962` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:987` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1036` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1265` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1297` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1437` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1581` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1719` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1778` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1853` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1884` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1938` | getActive | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1983` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2319` | readIntent | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2345` | saveIntent | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2385` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2409` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2751` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2917` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2926` | read | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:71` | withContext | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:93` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:125` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:464` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:470` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:513` | — | — |
