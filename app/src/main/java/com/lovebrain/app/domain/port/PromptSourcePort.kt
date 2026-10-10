package com.lovebrain.app.domain.port

/**
 * Prompt 资产读取端口。
 *
 * `PromptBuilder` 组装三流 system/user 时要读 assets 里那批引擎提示词（`AssetRegistry`
 * 登记的路径）。以前它自己抱着 `android.content.Context` 去 `context.assets.open(...)`——
 * domain 因此摸到了 Android，是 PackageDependencyTest 基线里 domain 格的那条债。
 * 还法照 `KbArchivePort`「路径归属收在实现侧」的同一张处方：domain 只问
 * 「这个路径的全文是什么」，资产从哪来、缺失怎么兜底、日志记在哪，都归 data 实现。
 *
 * 契约：读不到（缺失 / IO 失败）交回**空串**，不抛——这与旧 `readAsset` 的 fail-open
 * 语义逐字相同（缺资产只记一条日志、prompt 段留空，不让整条生成链崩）。
 */
fun interface PromptSourcePort {
    /** 按 [path] 读出资产全文；读不到时交回空串（fail-open，见类文档） */
    fun read(path: String): String
}
