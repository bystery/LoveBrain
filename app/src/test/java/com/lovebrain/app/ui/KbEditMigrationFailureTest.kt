package com.lovebrain.app.ui

import com.lovebrain.app.core.testing.UiProbeApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * 进编辑页前那次补齐迁移：失败必须**落成读数**，不许穿出 `LaunchedEffect`。
 *
 * 旧写法 `LaunchedEffect { ensureMigrated(); loaded = true }` 没有 catch：迁移一抛，
 * 协程当场结束、`loaded` 永远为假——用户看到的是一屏转不完的圈，连"读失败 + 重试"那格都到不了。
 * 取消则是另一件事：它必须原样上抛，不许被记成一次迁移失败（否则退出/切库会静默半程）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = UiProbeApplication::class)
class KbEditMigrationFailureTest {

    @Test
    fun `a throwing migration is reported as a failure instead of escaping`() {
        assertFalse(
            "迁移抛错必须变成 false（调用方据此落错误态），不是把异常扔出去",
            runBlocking {
                runKbEditMigration("kb_a") { throw IOException("keystore cannot decrypt") }
            }
        )
    }

    @Test
    fun `cancellation is rethrown so the caller's scope stays cancelled`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runKbEditMigration("kb_a") { throw CancellationException("page gone") }
            }
        }
    }

    @Test
    fun `a clean migration reports success and hands over the kb name`() {
        var seen = ""
        assertTrue(
            runBlocking {
                runKbEditMigration("kb_a") { seen = it }
            }
        )
        assertEquals("迁移读的就是这一页要打开的那一座库", "kb_a", seen)
    }
}
