package com.lovebrain.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StageCatalog 八阶段归一化契约单测（；邀约期已废除）。
 * ：阶段值一律经 StageCatalog，禁止裸写阶段字符串比较。
 */
class StageCatalogTest {

    // ═══ 白名单完整性 ═══

    @Test
    fun `ALL contains exactly eight stages all suffixed with 期`() {
        assertEquals(8, StageCatalog.ALL.size)
        StageCatalog.ALL.forEach { stage ->
            assertTrue("stage '$stage' must end with 期", stage.endsWith("期"))
        }
    }

    @Test
    fun `unknown constant is blank sentinel`() {
        assertEquals("待确定", StageCatalog.UNKNOWN)
    }

    @Test
    fun `ALL covers expected stages`() {
        val expected = listOf(
            "初识期", "破冰期", "暧昧期",
            "热恋期", "磨合期", "稳定期", "危机期", "修复期"
        )
        assertEquals(expected, StageCatalog.ALL)
    }

    // ═══ normalize ═══

    @Test
    fun `normalize returns same value for valid stage with suffix`() {
        assertEquals("初识期", StageCatalog.normalize("初识期"))
        assertEquals("修复期", StageCatalog.normalize("修复期"))
    }

    @Test
    fun `normalize trims surrounding whitespace`() {
        assertEquals("破冰期", StageCatalog.normalize("  破冰期  "))
    }

    @Test
    fun `normalize appends missing suffix for valid bare stage`() {
        assertEquals("初识期", StageCatalog.normalize("初识"))
        assertEquals("热恋期", StageCatalog.normalize("热恋"))
        assertEquals("危机期", StageCatalog.normalize("危机"))
        assertEquals("暧昧期", StageCatalog.normalize("暧昧"))
        assertEquals("修复期", StageCatalog.normalize(" 修复 "))
    }

    @Test
    fun `normalize returns null for garbage not close to any stage`() {
        assertNull(StageCatalog.normalize("乱七八糟"))
        assertNull(StageCatalog.normalize(""))
        assertNull(StageCatalog.normalize("热"))
        assertNull(StageCatalog.normalize("蜜月期"))
    }

    @Test
    fun `normalize returns null for bare stage whose plus 期 not in whitelist`() {
        // "磨合" + "期" = "磨合期"（在白名单）→ 这会通过
        // 用一个真正不在白名单的词测试 null 路径
        assertNull(StageCatalog.normalize("路人甲"))
        assertNull(StageCatalog.normalize("已分手期"))
    }

    @Test
    fun `normalize is idempotent on canonical values`() {
        StageCatalog.ALL.forEach { stage ->
            assertEquals(stage, StageCatalog.normalize(stage))
        }
    }

    @Test
    fun `normalize is idempotent on bare forms`() {
        // normalize( normalize(x) ) == normalize(x)
        StageCatalog.ALL.forEach { stage ->
            val bare = stage.removeSuffix("期")
            val once = StageCatalog.normalize(bare)!!
            val twice = StageCatalog.normalize(once)
            assertEquals(once, twice)
        }
    }

    @Test
    fun `normalize returns null for empty after trim`() {
        assertNull(StageCatalog.normalize(""))
        assertNull(StageCatalog.normalize("   "))
    }

    // ═══ normalizeOrUnknown ═══

    @Test
    fun `normalizeOrUnknown returns normalized for valid`() {
        assertEquals("初识期", StageCatalog.normalizeOrUnknown("初识"))
        assertEquals("暧昧期", StageCatalog.normalizeOrUnknown(" 暧昧 "))
    }

    @Test
    fun `normalizeOrUnknown returns unknown sentinel for invalid`() {
        assertEquals(StageCatalog.UNKNOWN, StageCatalog.normalizeOrUnknown("乱七八糟"))
        assertEquals(StageCatalog.UNKNOWN, StageCatalog.normalizeOrUnknown(""))
        assertEquals(StageCatalog.UNKNOWN, StageCatalog.normalizeOrUnknown("乱写的"))
    }

    @Test
    fun `normalizeOrUnknown on already-canonical is identity`() {
        StageCatalog.ALL.forEach { stage ->
            assertEquals(stage, StageCatalog.normalizeOrUnknown(stage))
        }
    }
}
