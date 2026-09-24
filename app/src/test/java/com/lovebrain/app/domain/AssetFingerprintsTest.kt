package com.lovebrain.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 资产内容指纹的规则（`AssetFingerprints`）。
 *
 * 这个指纹同时被两处用：`scripts/asset_hashes.sh` + `docs/prompt-assets.lock` 的离线核对，
 * 和设备端 `promptVersion`（锦囊缓存的命中判据之一）。CI 上那次红就是因为它跟着
 * 平台换行走——所以"换行不算内容"和"顺序算内容"这两条都得有用例钉住，
 * 而不是等下一次跨平台构建再炸一遍。
 */
class AssetFingerprintsTest {

    private val core = "engine/system_prompt/core.md"
    private val format = "engine/system_prompt/format.md"

    @Test
    fun `crlf and lf checkouts of the same asset fingerprint identically`() {
        val lf = AssetFingerprints.hashOf(listOf(core to "# 规则\n- 一条\n- 两条\n"))
        val crlf = AssetFingerprints.hashOf(listOf(core to "# 规则\r\n- 一条\r\n- 两条\r\n"))
        assertEquals("同一份内容不能因为检出平台不同而得到不同指纹", lf, crlf)
    }

    @Test
    fun `a lone carriage return is not mistaken for a line ending`() {
        val withCR = AssetFingerprints.hashOf(listOf(core to "A\rB"))
        val withLF = AssetFingerprints.hashOf(listOf(core to "A\nB"))
        assertNotEquals(withCR, withLF)
    }

    @Test
    fun `assembly order is part of the fingerprint`() {
        val a = "内容A"
        val b = "内容B"
        assertNotEquals(
            "换了拼装顺序 prompt 就变了，指纹必须察觉",
            AssetFingerprints.hashOf(listOf(core to a, format to b)),
            AssetFingerprints.hashOf(listOf(core to b, format to a))
        )
    }

    @Test
    fun `path is part of the fingerprint so swapping filenames is visible`() {
        assertNotEquals(
            AssetFingerprints.hashOf(listOf(core to "同样内容")),
            AssetFingerprints.hashOf(listOf(format to "同样内容"))
        )
    }

    @Test
    fun `one content byte changes the fingerprint and the output stays 16 hex chars`() {
        val base = AssetFingerprints.hashOf(listOf(core to "第一版"))
        assertTrue(base.matches(Regex("[0-9a-f]{16}")))
        assertEquals(16, base.length)
        assertNotEquals(base, AssetFingerprints.hashOf(listOf(core to "第一版。")))
    }

    @Test
    fun `no input is a defined value rather than a crash`() {
        assertEquals(16, AssetFingerprints.hashOf(emptyList()).length)
        assertEquals(AssetFingerprints.hashOf(emptyList()), AssetFingerprints.hashOf(emptyList()))
    }

    @Test
    fun `missing assets hash to the empty-content case instead of inventing a placeholder`() {
        // readAsset 读不到时返回 ""（并记日志），指纹这边就按空内容算，不额外造假字符串
        assertEquals(
            AssetFingerprints.hashOf(listOf(core to "")),
            AssetFingerprints.hashOf(listOf(core to AssetFingerprints.canonical("")))
        )
    }
}
