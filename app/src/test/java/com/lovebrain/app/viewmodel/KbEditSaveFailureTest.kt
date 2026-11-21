package com.lovebrain.app.viewmodel

import com.lovebrain.app.domain.port.KnowledgeDocumentPort
import com.lovebrain.app.domain.port.SettingsStorePort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 编辑页的保存必须**只听写核的回答**，不许自己编一个"保存成功"。
 *
 * 旧写法在"没有版本快照"这一支（整页读失败后仍落草稿）走的是端口那颗不带校验的 `writeFile`：
 * 它返回 `Unit`，写被拒（只读库 / 路径守门 / 落盘失败）与写成在调用方手里长得一模一样，
 * 于是紧接着回传一个新内容哈希——屏幕报"已保存"，盘上什么都没写，草稿还被当成已落盘。
 * 现在这一支先补读当前版本，再走同一条版本化写（写核的 Boolean 折成 null），失败就回 null，
 * 由调用方保留草稿并落红字提示。
 */
class KbEditSaveFailureTest {

    private fun vm(repo: KnowledgeDocumentPort) =
        KbEditViewModel(repo, mockk<SettingsStorePort>(relaxed = true))

    @Test
    fun `a refused write without a version snapshot comes back as null instead of a fresh hash`() =
        runBlocking {
            val repo = mockk<KnowledgeDocumentPort>()
            coEvery { repo.readFileWithVersion("kb_a", "moment/recent.md") } returns ("旧正文" to "v-old")
            coEvery {
                repo.writeFileWithVersion("kb_a", "moment/recent.md", "新正文", "v-old")
            } returns null

            val saved = vm(repo).save("kb_a", "moment/recent.md", "新正文", null)

            assertNull("写被拒必须走失败通道（null），不得回一个新版本号冒充成功", saved)
            coVerify(exactly = 0) { repo.writeFile(any(), any(), any()) }
            coVerify(exactly = 1) {
                repo.writeFileWithVersion("kb_a", "moment/recent.md", "新正文", "v-old")
            }
        }

    @Test
    fun `a blind save still overwrites by deriving the current version from disk`() = runBlocking {
        val repo = mockk<KnowledgeDocumentPort>()
        coEvery { repo.readFileWithVersion("kb_a", "moment/recent.md") } returns ("旧正文" to "v1")
        coEvery { repo.writeFileWithVersion("kb_a", "moment/recent.md", "新正文", "v1") } returns "v2"

        val saved = vm(repo).save("kb_a", "moment/recent.md", "新正文", null)

        assertEquals("写核说落成了才回新版本号", "v2", saved)
    }

    @Test
    fun `the versioned branch keeps handing the caller's snapshot straight through`() = runBlocking {
        val repo = mockk<KnowledgeDocumentPort>()
        coEvery { repo.writeFileWithVersion("kb_a", "understand/me.md", "改", "v9") } returns "v10"

        assertEquals("v10", vm(repo).save("kb_a", "understand/me.md", "改", "v9"))
        coVerify(exactly = 0) { repo.readFileWithVersion(any(), any()) }
    }
}
