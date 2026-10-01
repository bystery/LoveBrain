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

挂起判据由两部分组成：全仓 `suspend fun` 名单（本仓库 132 个）+ 协程/并发库确定的挂起 API（withContext / delay / launch / withLock / collect / await / emit …）。

## 结果

| 判定 | 站点数 |
|---|---:|
| PROTECTED | 55 |
| WAIVED | 2 |
| SUSPEND-FREE | 97 |
| NEEDS_REVIEW | 0 |
| PROPAGATED | 51 |
| CANCEL-SWALLOWED | 0 |
| 合计 | 205 |

`--check` 结论：**PASS**（没有未处置的可挂起吞取消站点，且每一处 `catch (CancellationException)` 都重抛了）

## 逐站点

| 判定 | 位置 | 挂起点 | 豁免理由 |
|---|---|---|---|
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/LoveBrainApp.kt:48` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/ApiUsageTracker.kt:96` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/ApiUsageTracker.kt:183` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:252` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:283` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:311` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:388` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:399` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:401` | execute | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:502` | execute | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:553` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt:564` | executeWithCode | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/FeedbackCaseRepository.kt:45` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/FeedbackCaseRepository.kt:57` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KbArchiveTransfer.kt:73` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KbArchiveTransfer.kt:88` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KbArchiveTransfer.kt:146` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeBackupService.kt:70` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeBackupService.kt:81` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeBackupService.kt:110` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeBackupService.kt:124` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeCatalogStore.kt:60` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeDocumentStore.kt:51` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeDocumentStore.kt:57` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeDocumentStore.kt:73` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeDocumentStore.kt:104` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeIntentService.kt:95` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeMigrator.kt:87` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeMigrator.kt:160` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeMigrator.kt:185` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/KnowledgeProfileTransactionService.kt:199` | — | — |
| WAIVED | `app/src/main/java/com/lovebrain/app/data/KnowledgeProfileTransactionService.kt:233` | — | 这里只有写与删（都走守门后的同步 I/O），协程取消不会从这里抛出 |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeProfileTransactionService.kt:253` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeProfileTransactionService.kt:268` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeProfileTransactionService.kt:277` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepoIO.kt:51` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepoIO.kt:52` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepoIO.kt:100` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepoIO.kt:103` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepoIO.kt:298` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepoStorage.kt:25` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepoStorage.kt:105` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepoStorage.kt:112` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepoTx.kt:36` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepoTx.kt:75` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:206` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:209` | backupIfNeeded | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:233` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:235` | backupIfNeeded | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt:747` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/OpenAiChatWire.kt:193` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/ProviderConfigResolver.kt:122` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/SecurePrefs.kt:34` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/data/SecurePrefs.kt:265` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:131` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:162` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:233` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:235` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:309` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:352` | collectStream | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:366` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:432` | collectStream | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:513` | collectStream | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:519` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:581` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:594` | collectStream | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt:669` | — | — |
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
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/PromptBuilder.kt:496` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:197` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:293` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:353` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:440` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/RoundCommitJournal.kt:456` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/SceneChainStore.kt:397` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/SceneChainStore.kt:400` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/domain/prompt/MemoryRefPolicy.kt:112` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/feature/intent/IntentController.kt:90` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/feature/intent/IntentController.kt:92` | readIntent | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/feature/intent/IntentController.kt:102` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/feature/intent/IntentController.kt:104` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/feature/intent/IntentController.kt:150` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/feature/intent/IntentController.kt:152` | — | — |
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
| PROPAGATED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:234` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:236` | readFile | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:275` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:277` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:532` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:534` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:566` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:568` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:604` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/ui/KbEditActivity.kt:606` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/bubble/FloatingBubble.kt:104` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/feedback/FeedbackCasesScreen.kt:142` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/ui/home/CaptureAppsScreen.kt:118` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/util/L.kt:21` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/util/L.kt:39` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/util/OpenAiChatEndpointResolver.kt:40` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/util/TimeFmt.kt:20` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/FeedbackCaseController.kt:138` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/FeedbackCaseController.kt:140` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/FeedbackCaseController.kt:166` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/FeedbackCaseController.kt:168` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:128` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:130` | setActive | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:141` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:143` | updateDisplayName | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:155` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:157` | delete | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:174` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:176` | listAll | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:205` | runOnboardingCreation | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:217` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:219` | withContext | — |
| WAIVED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:247` | createKnowledgeBase | 同上——取消经 finishCreation 转成 Cancelled，不伪装成 CreateFailed |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:265` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:267` | create | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:295` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:300` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:321` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:326` | getActive | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt:341` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:685` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:687` | ensureInitialKnowledgeBase | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:724` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:726` | collect | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:799` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:860` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:862` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:877` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:879` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:926` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:929` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1184` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1187` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1374` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1377` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1401` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1454` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1456` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1479` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1532` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1534` | getActive | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1594` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1596` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1643` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1727` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1927` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1929` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1986` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:1988` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2164` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2166` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2313` | — | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2320` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt:2322` | read | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:111` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:113` | withContext | — |
| PROPAGATED | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:133` | — | — |
| PROTECTED | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:135` | withContext | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:167` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:514` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:533` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:546` | — | — |
| SUSPEND-FREE | `app/src/main/java/com/lovebrain/app/viewmodel/SetupViewModel.kt:589` | — | — |
