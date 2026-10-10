package com.lovebrain.app.data

import com.lovebrain.app.domain.port.PromptSourcePort
import com.lovebrain.app.util.L
import java.io.InputStream

/**
 * [PromptSourcePort] 的 assets 实现：把「资产从哪来、缺了怎么办」收在 data 这一个适配器里，
 * domain 的 PromptBuilder 只按路径拿全文，不再自己抱 `Context`。
 *
 * fail-open 契约与旧 `PromptBuilder.readAsset` 逐字同语义：open / 读流出任何异常都交回空串，
 * 只记一条带路径的日志（E4：asset 缺失不静默吞掉，定位 prompt 段丢失用）。
 * 这颗实现不开第二条 IO 链——它只是 assets 那一条读口换了主人。
 */
class AssetPromptSource(private val openAsset: (String) -> InputStream) : PromptSourcePort {

    override fun read(path: String): String =
        runCatching { openAsset(path).bufferedReader().use { it.readText() } }
            .onFailure { L.w("readAsset missing/failed: $path") }
            .getOrDefault("")
}
