package com.lovebrain.app.data

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * [AssetPromptSource]（[com.lovebrain.app.domain.port.PromptSourcePort] 的 assets 实现）的契约单测。
 *
 * PromptBuilder 的资产读取端口化后，fail-open 那一半语义（缺失/IO 失败 → 空串 + 日志、不抛）
 * 从 domain 挪进了这颗实现——这格就是它的证人，钉两件事：
 * 1. 资产在 → 交回全文（逐字）；
 * 2. open / 读流任何一步失败 → 交回空串，不抛（旧 `PromptBuilder.readAsset` 同语义，E4：只记日志）。
 */
class AssetPromptSourceTest {

    @Before
    fun setUp() {
        // 失败路径会经 L.w 碰 android.util.Log；JVM 上用宽松桩，与 EventBusCaptureTest 同一口径
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `returns the full text when the asset opens`() {
        val source = AssetPromptSource { path ->
            if (path == "engine/system_prompt/core.md") {
                "第一行\n第二行".byteInputStream()
            } else {
                throw IOException("not found: $path")
            }
        }

        assertEquals("第一行\n第二行", source.read("engine/system_prompt/core.md"))
    }

    @Test
    fun `a missing asset degrades to empty string instead of throwing`() {
        val source = AssetPromptSource { path -> throw IOException("not found: $path") }

        assertEquals("", source.read("engine/system_prompt/missing.md"))
    }

    @Test
    fun `a read failure mid-stream also degrades to empty string`() {
        // open 成功、读到一半断流：fail-open 照样兜住，不让整条生成链崩
        val broken = object : java.io.InputStream() {
            override fun read(): Int = throw IOException("stream broken")
        }
        val source = AssetPromptSource { broken }

        assertEquals("", source.read("engine/system_prompt/broken.md"))
    }
}
