package com.lovebrain.app.viewmodel

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.FileKbArchiveTransfer
import com.lovebrain.app.domain.port.KnowledgeBaseCatalogPort
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 导入的两段必须分开判：**写入落盘**是导入成功，**把当前库钉回原处**是另一件事。
 *
 * 旧实现把 active 修正和写入裹在同一个 try 里：重名/版本/Keystore 任一处抛错，
 * 已经躺在盘上的那本库就被报成"不是有效的知识库备份文件"，而且 `loadState()` 直接跳过——
 * 列表里看不到刚导入的库，用户只能以为导入失败了。
 *
 * 这一族钉住：写入成功 ⇒ 事件是 [KbEvent.ImportActiveFixFailed]（不是 ImportFailed）、
 * 列表照常刷新、归档文件照常在盘上；只有写入失败才是 ImportFailed。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KnowledgeBaseImportActiveFixTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var filesRoot: File
    private lateinit var kbRoot: File
    private lateinit var cacheRoot: File

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        filesRoot = File(tmp.root, "files").apply { mkdirs() }
        kbRoot = File(filesRoot, "knowledge").apply { mkdirs() }
        cacheRoot = File(tmp.root, "cache").apply { mkdirs() }
    }

    private fun fakeContext(): Context {
        val assets = mockk<AssetManager>()
        every { assets.open(any()) } answers { "ONBOARDING-SYSTEM".byteInputStream() }
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.assets } returns assets
        every { ctx.filesDir } returns filesRoot
        every { ctx.cacheDir } returns cacheRoot
        every { ctx.contentResolver } returns mockk(relaxed = true)
        return ctx
    }

    private fun vm(ctx: Context, repo: KnowledgeBaseCatalogPort) = KnowledgeBaseViewModel(
        ctx, repo, mockk<DeepSeekRepository>(relaxed = true),
        FileKbArchiveTransfer(knowledgeRoot = kbRoot, stagingBase = cacheRoot),
        dispatcher
    )

    private fun newRepo(): KnowledgeBaseCatalogPort {
        val repo = mockk<KnowledgeBaseCatalogPort>(relaxed = true)
        coEvery { repo.listAll() } returns emptyList()
        coEvery { repo.getActive() } returns null
        return repo
    }

    private fun source(ctx: Context, name: String): Uri {
        val source = mockk<Uri>()
        every { ctx.contentResolver.openInputStream(source) } returns
            ByteArrayInputStream(zipWithMeta(name))
        return source
    }

    private fun TestScope.subscribe(vm: KnowledgeBaseViewModel): MutableList<KbEvent> {
        val events = mutableListOf<KbEvent>()
        backgroundScope.launch(dispatcher) { vm.events.collect { events += it } }
        runCurrent()
        return events
    }

    @Test
    fun `an archive that landed is not reported as a broken file when pinning active fails`() =
        runTest(dispatcher) {
            val repo = newRepo()
            val ctx = fakeContext()
            val imported = KnowledgeBase(name = "kb_demo", displayName = "小雅的库")
            coEvery { repo.listAll() } returns listOf(imported)
            coEvery { repo.getActive() } returns imported
            coEvery { repo.setActive(any()) } throws IOException("kb.json is read-only")

            val model = vm(ctx, repo)
            val events = subscribe(model)
            model.import(source(ctx, "kb_demo"))
            advanceUntilIdle()

            assertEquals(
                "写入已经落盘：只许说「已导入但当前库没钉住」，不许报成无效备份",
                listOf(KbEvent.ImportActiveFixFailed), events
            )
            assertTrue("归档必须真的在盘上", File(kbRoot, "kb_demo/kb.json").isFile)
            assertEquals(
                "列表必须刷新出刚导入的库（旧实现连 loadState 都跳过了）",
                listOf(imported), model.state.value.knowledgeBases
            )
            assertEquals("kb_demo", model.state.value.activeName)
        }

    @Test
    fun `a keystore failure while reading active still keeps the landed import`() =
        runTest(dispatcher) {
            val repo = newRepo()
            val ctx = fakeContext()
            val imported = KnowledgeBase(name = "kb_demo", displayName = "小雅的库")
            coEvery { repo.listAll() } returns listOf(imported)
            coEvery { repo.getActive() } throws IllegalStateException("keystore key cannot decrypt")

            val model = vm(ctx, repo)
            val events = subscribe(model)
            model.import(source(ctx, "kb_demo"))
            advanceUntilIdle()

            assertEquals(
                "读 active 抛错只影响「当前库」这一步，导入本身仍然成功",
                listOf(KbEvent.ImportActiveFixFailed), events
            )
            assertFalse(
                "不许把一次已落盘的导入说成无效备份",
                events.contains(KbEvent.ImportFailed)
            )
            assertTrue(File(kbRoot, "kb_demo/kb.json").isFile)
        }

    @Test
    fun `a broken archive is still reported as an import failure`() = runTest(dispatcher) {
        val repo = newRepo()
        val ctx = fakeContext()
        val source = mockk<Uri>()
        val bogus = zipOf("kb_demo/" to null, "kb_demo/notes.md" to "没有 kb.json")
        every { ctx.contentResolver.openInputStream(source) } returns ByteArrayInputStream(bogus)

        val model = vm(ctx, repo)
        val events = subscribe(model)
        model.import(source)
        advanceUntilIdle()

        assertEquals(listOf(KbEvent.ImportFailed), events)
        assertFalse(File(kbRoot, "kb_demo").exists())
    }

    // ═══════════ zip 夹具（与 KnowledgeBaseViewModelTest 同一形状） ═══════════

    private fun zipOf(vararg entries: Pair<String, String?>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                if (content != null) zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipWithMeta(name: String): ByteArray = zipOf(
        "$name/" to null,
        "$name/kb.json" to """{"name":"$name","displayName":"小雅的库"}""",
        "$name/understand/me.md" to "我习惯先讲道理"
    )
}
