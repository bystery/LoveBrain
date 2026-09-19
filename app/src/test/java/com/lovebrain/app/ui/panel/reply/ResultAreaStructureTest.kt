package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-5: 结果区纯状态模型测试——验证信息架构不变量。
 *
 * 这些测试只覆盖 JVM 级别的纯函数/状态模型逻辑：
 * - gesture reducer 的关键交互路径正确（纯函数）
 * - SchemeIdentity 区分 STYLE/DIRECTION（数据模型）
 * - shouldCommitTranscript 两事件 rendezvous（纯函数）
 * - v1.3.1 theme token 文件存在
 *
 * 不再通过读取源文件字符串来"验证"UI 结构——
 * Compose layout/interaction 覆盖在 androidTest (ResultAreaInteractionTest) 中。
 * 禁止以"函数名已经替换"作为 UI 结构验收。
 */
class ResultAreaStructureTest {

    // ═══ gesture reducer 关键交互路径 ═══

    @Test
    fun `drag during PRESSING cancels long press - prevents horizontal swipe triggering Voice`() {
        // 横滑 LazyRow 不触发 Voice
        var phase = GesturePhase.IDLE
        phase = reduceGesturePhase(phase, GestureEvent.DOWN)
        assertEquals(GesturePhase.PRESSING, phase)
        phase = reduceGesturePhase(phase, GestureEvent.DRAG_CANCELLED)
        assertEquals(GesturePhase.IDLE, phase)
        // 即使后续到达 LONG_PRESS_REACHED，也不会进入 RECORDING
        phase = reduceGesturePhase(phase, GestureEvent.LONG_PRESS_REACHED, true)
        assertEquals(GesturePhase.IDLE, phase) // IDLE + LONG_PRESS_REACHED → IDLE（不录音）
    }

    @Test
    fun `click during PRESSING without long press triggers expand - not Voice`() {
        // 点击 copy / like / dislike 不触发卡片展开（需要未达长按阈值）
        var phase = GesturePhase.IDLE
        phase = reduceGesturePhase(phase, GestureEvent.DOWN)
        assertEquals(GesturePhase.PRESSING, phase)
        phase = reduceGesturePhase(phase, GestureEvent.UP_IN_BOUNDS)
        assertEquals(GesturePhase.IDLE, phase)
    }

    @Test
    fun `long press then move out does NOT submit - CANCELLED`() {
        // 长按 + move-out 不提交
        var phase = GesturePhase.IDLE
        phase = reduceGesturePhase(phase, GestureEvent.DOWN)
        assertEquals(GesturePhase.PRESSING, phase)
        phase = reduceGesturePhase(phase, GestureEvent.LONG_PRESS_REACHED, true)
        assertEquals(GesturePhase.RECORDING, phase)
        phase = reduceGesturePhase(phase, GestureEvent.UP_OUT_OF_BOUNDS)
        assertEquals(GesturePhase.CANCELLED, phase)
        // CANCELLED 后不应提交
        assertFalse(shouldCommitTranscript(
            physicalReleased = false,
            finalTranscript = "text",
            cancelled = true,
            submitted = false
        ))
    }

    // ═══ SchemeIdentity 数据模型：STYLE/DIRECTION key 区分 ═══

    @Test
    fun `STYLE and DIRECTION SchemeIdentity keys are different`() {
        val styleA = com.lovebrain.app.model.SchemeIdentity(
            com.lovebrain.app.model.SchemeSource.STYLE, "A"
        )
        val dirF = com.lovebrain.app.model.SchemeIdentity(
            com.lovebrain.app.model.SchemeSource.DIRECTION, "F"
        )
        assertNotEquals("STYLE and DIRECTION must have different keys",
            styleA.key, dirF.key)
    }

    @Test
    fun `STYLE and DIRECTION Schemes have different sources`() {
        val styleScheme = Scheme(
            tag = "A", title = "推荐", reply = "text",
            source = SchemeSource.STYLE
        )
        val dirScheme = Scheme(
            tag = "F", title = "跟进", reply = "text",
            source = SchemeSource.DIRECTION
        )
        assertNotEquals(styleScheme.source, dirScheme.source)
        assertNotEquals(styleScheme.identity.key, dirScheme.identity.key)
    }

    // ═══ v1.3.1 token 不变 ═══

    @Test
    fun `theme token files exist and are stable`() {
        // 验证 theme token 文件存在——SHA 校验由 CI 层完成
        val themeDir = java.io.File("src/main/java/com/lovebrain/app/ui/theme")
        assertTrue("theme directory should exist", themeDir.exists())
        listOf("Color.kt", "Dimens.kt", "Theme.kt", "Type.kt").forEach { file ->
            assertTrue("$file should exist", java.io.File(themeDir, file).exists())
        }
    }

    // ═══ rendezvous: results→release and release→results both submit once ═══

    @Test
    fun `path A - results first then release submits exactly once`() {
        // 路径 A: onResults 先来 → finalTranscript 已缓存
        // release 到达 → physicalReleased=true → tryCommit 提交
        val physicalReleased = false
        val finalTranscript: String? = "hello"
        val cancelled = false
        val submitted = false

        // onResults 到达——尚未 release
        assertFalse(shouldCommitTranscript(physicalReleased, finalTranscript, cancelled, submitted))

        // release 到达——提交
        assertTrue(shouldCommitTranscript(true, finalTranscript, false, false))

        // submitted 后不再提交
        assertFalse(shouldCommitTranscript(true, finalTranscript, false, true))
    }

    @Test
    fun `path B - release first then results submits exactly once`() {
        // 路径 B: release 先来 → physicalReleased=true
        // onResults 到达 → finalTranscript 设置 → tryCommit 提交
        val physicalReleased = true
        val finalTranscript: String? = null  // 尚未到达
        val cancelled = false
        val submitted = false

        // release 到达——onResults 尚未到达
        assertFalse(shouldCommitTranscript(physicalReleased, finalTranscript, cancelled, submitted))

        // onResults 到达——提交
        assertTrue(shouldCommitTranscript(true, "hello", false, false))

        // submitted 后不再提交
        assertFalse(shouldCommitTranscript(true, "hello", false, true))
    }

    @Test
    fun `cancel after release but before results does NOT submit`() {
        // release 已到达，cancel 发生在 onResults 之前
        assertFalse(shouldCommitTranscript(true, null, true, false))
        // 即使 onResults 后到达也不提交
        assertFalse(shouldCommitTranscript(true, "hello", true, false))
    }

    @Test
    fun `release after onError does NOT submit`() {
        // onError → finalTranscript 为 null
        assertFalse(shouldCommitTranscript(true, null, false, false))
    }
}
