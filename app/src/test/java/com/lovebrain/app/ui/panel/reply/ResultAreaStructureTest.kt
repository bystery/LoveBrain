package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结果区纯状态模型测试——验证信息架构不变量。
 *
 * 这里原本还钉着长按录音手势的 reducer 路径与语音两事件 rendezvous 提交决策，
 * 随语音模式一起删除。
 *
 * 这些测试只覆盖 JVM 级别的纯函数/状态模型逻辑：
 * - SchemeIdentity 区分 STYLE/DIRECTION（数据模型）
 * - v1.3.1 theme token 文件存在
 *
 * 不再通过读取源文件字符串来"验证"UI 结构——
 * Compose layout/interaction 覆盖在 androidTest (ResultAreaInteractionTest) 中。
 * 禁止以"函数名已经替换"作为 UI 结构验收。
 */
class ResultAreaStructureTest {

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
        // 这一格只判"文件在不在"。以前它的注释写着"SHA 校验由 CI 层完成"——那是假的：
        // grep .github/workflows 里没有任何一处算 theme 文件的哈希，CI 不算 token 的 SHA。
        // 注释承诺一道不存在的闸，比没有闸更坏（下一个人会以为已经有人看着），所以改口。
        //
        // 第三步-1 把 token 整体迁进 core/designsystem 之后，这里判的东西也变了：
        // 不只是"文件在"，而是"token 在 core、Material 包装留在 ui"——方向反了就是 §5.1 的破口。
        val tokenDir = java.io.File("src/main/java/com/lovebrain/app/core/designsystem")
        assertTrue("designsystem 目录应存在", tokenDir.exists())
        listOf("Color.kt", "Dimens.kt", "Type.kt", "Spacing.kt", "Shapes.kt").forEach { file ->
            assertTrue("token 文件 $file 应在 core/designsystem", java.io.File(tokenDir, file).exists())
        }
        val themeDir = java.io.File("src/main/java/com/lovebrain/app/ui/theme")
        assertTrue("ui/theme 应仍存在（Material 主题包装）", themeDir.exists())
        assertTrue("Theme.kt 留在 ui/theme", java.io.File(themeDir, "Theme.kt").exists())
        // 反向：token 不许又长回 ui 下面（迁移被 revert 一半是最难发现的那种坏法）
        listOf("Color.kt", "Dimens.kt", "Type.kt").forEach { file ->
            assertTrue("token 文件 $file 不许再回到 ui/theme", !java.io.File(themeDir, file).exists())
        }
    }

}
