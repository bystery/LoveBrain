package com.lovebrain.app.data

import com.lovebrain.app.model.KnowledgeBase
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 目录枚举的三条语义（隐藏目录、name 与目录名等值、坏元数据只丢这一条）。
 *
 * 这格从 `KnowledgeRepository` 拆出来时，最该守的不是"代码搬对了"，而是
 * **"两条入口路径以前行为不一样"**：无锁那份被挡下时一声不吭，公开那份才记日志。
 * 所以这里除了结果，还断言"被挡下必须报出原因"——见 [recorder]。
 */
class KnowledgeCatalogStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** 挡下的原因按目录名收着：断言"说了什么"，而不是"说没说过" */
    private val recorder = mutableListOf<Pair<String, String>>()

    private fun store(root: File) = KnowledgeCatalogStore(
        object : CatalogStorage {
            override val catalogRoot: File get() = root
            override fun decodeMeta(text: String): KnowledgeBase? =
                runCatching { json.decodeFromString<KnowledgeBase>(text) }.getOrNull()
            override fun onMetaRejected(dirName: String, reason: String) {
                recorder += dirName to reason
            }
        }
    )

    private fun lib(root: File, dirName: String, metaName: String = dirName, updatedAt: String = "") {
        val dir = File(root, dirName).apply { mkdirs() }
        File(dir, KnowledgeCatalogStore.META_FILE).writeText(
            json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(name = metaName, displayName = dirName, updatedAt = updatedAt)
            ),
            Charsets.UTF_8
        )
    }

    @Test
    fun `hidden directories are not libraries`() {
        val root = folder.newFolder()
        lib(root, "kb-a")
        File(root, ".backup/kb-a_20260101_1200").apply { parentFile.mkdirs(); mkdirs() }
        File(root, ".kb_initialized").writeText("done")
        // 真正的反例：一个**带合法 kb.json 的隐藏目录**。只放"没有 kb.json 的 .backup"的话，
        // 就算把隐藏目录过滤那行删了，这条也照样绿——尺子量不到东西。
        lib(root, ".draft", metaName = ".draft")

        val found = store(root).list()

        assertEquals("只该看到那一个真库：" + found.map { it.name }, listOf("kb-a"), found.map { it.name })
        assertTrue("隐藏目录不该被报成异常（它本来就不是库）：" + recorder, recorder.isEmpty())
    }

    /** 单一扼制点：目录名才是身份，kb.json 里写的 name 只是声明 */
    @Test
    fun `a library whose metadata disagrees with its directory name is dropped and reported`() {
        val root = folder.newFolder()
        lib(root, "kb-real", metaName = "../../escape")
        lib(root, "kb-ok")

        val found = store(root).list()

        assertEquals(
            "字段与目录名不符的那条必须整个丢弃：" + found.map { it.name },
            listOf("kb-ok"), found.map { it.name }
        )
        assertEquals(
            "丢掉一条必须说出是哪条、为什么（以前无锁那条路径什么都不说）",
            listOf("kb-real" to KnowledgeCatalogStore.REASON_NAME_MISMATCH),
            recorder.toList()
        )
    }

    @Test
    fun `one broken library drops itself without taking the listing down`() {
        val root = folder.newFolder()
        lib(root, "kb-good")
        val broken = File(root, "kb-broken").apply { mkdirs() }
        File(broken, KnowledgeCatalogStore.META_FILE).writeText("{ this is not json", Charsets.UTF_8)
        File(root, "kb-no-meta").mkdirs()

        val found = store(root).list()

        assertEquals("坏元数据与缺元数据都只影响自己：" + found.map { it.name }, listOf("kb-good"), found.map { it.name })
        assertEquals(
            "解不开的那条要报 undecodable；连 kb.json 都没有的不算异常",
            listOf("kb-broken" to KnowledgeCatalogStore.REASON_BAD_JSON),
            recorder.toList()
        )
    }

    @Test
    fun `libraries come out newest first`() {
        val root = folder.newFolder()
        lib(root, "kb-old", updatedAt = "2026-01-01T00:00:00")
        lib(root, "kb-new", updatedAt = "2026-09-24T00:00:00")
        lib(root, "kb-mid", updatedAt = "2026-05-05T00:00:00")

        assertEquals(
            "按 updatedAt 倒序（列表页第一条=最近用的库）",
            listOf("kb-new", "kb-mid", "kb-old"),
            store(root).list().map { it.name }
        )
    }

    @Test
    fun `an empty or missing root is an empty list not an exception`() {
        val empty = folder.newFolder()
        assertEquals("空目录=空列表", emptyList<String>(), store(empty).list().map { it.name })
        assertEquals(
            "根目录还不存在时也是空列表",
            emptyList<String>(),
            store(File(empty, "nope")).list().map { it.name }
        )
    }
}
