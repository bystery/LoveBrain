# 全仓 CancellationException 审计结果

> 本文件由 `python3 scripts/audit_cancellation.py --write docs/CANCELLATION-AUDIT.md`
> 生成，不要手改；CI 里 `--check` 用的是同一套判据。

复核报告 §6 S2-07 点名："没有全仓 CancellationException 审计结果和 lint/static rule 防回归"。
这里既是审计结果，也是那条静态规则本身。

## 判据

扫描范围：`app/src/main` 下所有 `catch (:Exception)` / `catch (:Throwable)` / `runCatching {`，**外加每一处 `catch (:CancellationException)`**（后者不看挂起点：站点本身就是取消的落点）。

| 判定 | 含义 |
|---|---|
| PROPAGATED | `catch (:CancellationException)` 块内有 `throw` —— 取消继续传播 |
| CANCEL-SWALLOWED | 接住取消却没重抛：`job.isCancelled` 会变成 false，等它的人以为跑完了 —— `--check` 直接失败 |
| PROTECTED | 块内、紧邻上下文或同一条 try 链上显式处理 `CancellationException` |
| WAIVED | 站点旁写了 `cancel-safe:` + 非空理由，人工复核过 |
| SUSPEND-FREE | 受保护代码段内没有挂起点，且所在函数不是 suspend：吞异常不等于吞取消 |
| NEEDS_REVIEW | 可挂起处吞掉取消信号 —— `--check` 直接失败 |

挂起判据由两部分组成：全仓 `suspend fun` 名单（本仓库 130 个）+ 协程/并发库确定的挂起 API（withContext / delay / launch / withLock / collect / await / emit …）。

## 结果

| 判定 | 站点数 |
|---|---:|
| PROTECTED | 54 |
| WAIVED | 2 |
| SUSPEND-FREE | 99 |
| NEEDS_REVIEW | 0 |
| PROPAGATED | 50 |
| CANCEL-SWALLOWED | 0 |
| 合计 | 205 |

`--check` 结论：**PASS**（没有未处置的可挂起吞取消站点，且每一处 `catch (CancellationException)` 都重抛了）

## 逐站点

| 判定 | 位置 | 挂起点 | 豁免理由 |
|---|---|---|---|
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/LoveBrainApp.kt:48` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/ApiUsageTracker.kt:96` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/ApiUsageTracker.kt:183` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:241` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:272` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:300` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:377` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:388` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:390` | execute | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:491` | execute | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:542` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:553` | executeWithCode | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/FeedbackCaseRepository.kt:45` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/FeedbackCaseRepository.kt:57` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KbArchiveTransfer.kt:77` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KbArchiveTransfer.kt:135` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeBackupService.kt:63` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeBackupService.kt:74` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeBackupService.kt:103` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeBackupService.kt:117` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeCatalogStore.kt:59` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeDocumentStore.kt:51` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeDocumentStore.kt:57` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeDocumentStore.kt:73` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeMigrator.kt:75` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeMigrator.kt:139` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeMigrator.kt:161` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:67` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:136` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:143` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:262` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:265` | backupIfNeeded | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:289` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:291` | backupIfNeeded | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:344` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:345` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:393` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:396` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:523` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:546` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:907` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1191` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1451` | — | — |
| WAIVED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1485` | — | 这里只有写与删（都走守门后的同步 I/O），协程取消不会从这里抛出 |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1505` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1520` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1529` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:1766` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/OpenAiChatWire.kt:193` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/ProviderConfigResolver.kt:118` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/SecurePrefs.kt:29` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/SecurePrefs.kt:269` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:131` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:162` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:233` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:235` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:307` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:350` | collectStream | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:364` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:428` | collectStream | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:506` | collectStream | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:512` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:572` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:585` | collectStream | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:655` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/IntentPolicy.kt:55` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/IntentPolicy.kt:58` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:131` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:133` | getLessonCount | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:163` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:165` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:269` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:271` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:289` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:291` | generateRaw | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:315` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:317` | generateRaw | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:367` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:369` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:460` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/KnowledgeTriggerCoordinator.kt:462` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/OngoingContextSelector.kt:320` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/OngoingContextSelector.kt:405` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/PromptBuilder.kt:693` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/PromptBuilder.kt:971` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:197` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:293` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:353` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:440` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:456` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/SceneChainStore.kt:397` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/SceneChainStore.kt:400` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/feature/profile/ProfileUpdateController.kt:136` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/feature/profile/ProfileUpdateController.kt:139` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/feature/roundcommit/ActualSentRecorder.kt:116` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/feature/roundcommit/ActualSentRecorder.kt:119` | — | — |
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
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/FloatingService.kt:173` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/OverlayWindowHost.kt:110` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/OverlayWindowHost.kt:125` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/OverlayWindowHost.kt:133` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/service/OverlayWindowHost.kt:148` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:250` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:252` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:504` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:506` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:538` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:540` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:575` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:577` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/bubble/FloatingBubble.kt:104` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/feedback/FeedbackCasesScreen.kt:143` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/home/CaptureAppsScreen.kt:127` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/panel/reply/VoiceRewrite.kt:198` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/panel/reply/VoiceRewrite.kt:199` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/panel/reply/VoiceRewrite.kt:324` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/panel/reply/VoiceRewrite.kt:344` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/util/L.kt:21` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/util/L.kt:39` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/util/OpenAiChatEndpointResolver.kt:40` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/util/TimeFmt.kt:20` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/FeedbackCaseController.kt:138` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/FeedbackCaseController.kt:140` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/FeedbackCaseController.kt:166` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/FeedbackCaseController.kt:168` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:124` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:126` | setActive | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:137` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:139` | updateDisplayName | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:151` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:153` | delete | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:170` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:172` | listAll | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:201` | runOnboardingCreation | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:213` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:215` | withContext | — |
| WAIVED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:243` | createKnowledgeBase | 同上——取消经 finishCreation 转成 Cancelled，不伪装成 CreateFailed |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:261` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:263` | create | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:292` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:297` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:325` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:330` | getActive | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:345` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:726` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:728` | ensureInitialKnowledgeBase | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:765` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:767` | collect | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:841` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:914` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:916` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:931` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:933` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:980` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:983` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1242` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1245` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1433` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1436` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1460` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1513` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1515` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1543` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1596` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1598` | getActive | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1658` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1660` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1707` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1793` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1969` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1971` | readIntent | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1984` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1986` | saveIntent | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2036` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2038` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2099` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2101` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2158` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2160` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2344` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2346` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2493` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2500` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2502` | read | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:110` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:112` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:132` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:134` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:166` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:503` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:522` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:535` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:578` | — | — |
