package com.lovebrain.app.domain.port

/**
 * 「本机是否已有知识库」读数的窄端口。
 *
 * `SetupViewModel` 的老用户判定（`OnboardingDecision.isExistingUser` 的 hasKnowledgeBase 位）
 * 以前自己拼 `File(context.filesDir, "knowledge")` 再 `listFiles()`——viewmodel 层于是摸到了
 * `java.io.File`，是 PackageDependencyTest 基线里 viewmodel 格的那条债。
 * 还法照 KnowledgeBaseViewModel 归档那颗 `KbArchivePort` 的同一张处方：
 * 目录在哪、怎么算「非空」，都是 data 实现的事，页面只问一句有没有。
 */
interface KnowledgePresencePort {
    /**
     * 本机是否已有知识库：目录存在且其中还有任何条目。
     * 与旧判据逐字同义——不验 kb.json 合法性：这是「老用户」判据不是「合法库」判据，
     * 一个残留的坏目录不该把升级用户拽回引导。
     */
    fun hasAnyKnowledgeBase(): Boolean
}
