package com.lovebrain.app.data

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * "moment/topic.md 这一行"的格式合同：写法、读法、以及**只有一个所有者**这件事。
 *
 * 起因是 §5.3 拆 archive 那一格时量到的：`rotateTopic` 会把 `getCurrentTopic` 的返回值
 * 当成旧话题名写进归档标题，而"正在聊："这个标记当时在四处写、一处读，各写各的字面量。
 * 任何一处改字，读侧就静默读不出话题——`substringAfter` 的默认行为会把**整行**当话题名。
 *
 * 第 4 格是源码形状尺（跟 `SingleOwnerContractTest` 同一族）：它只负责"不许再出现第二个所有者"，
 * 格式本身对不对由前三格钉；扫描自己会断言"扫到了足够多的文件"，否则路径写错时它会永远通过。
 */
class TopicLineFormatTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private lateinit var repo: KnowledgeRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("topic_format").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repo = KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun writeAndReadRoundTripThroughTheTwoOwnersOfTheFormat() {
        val line = KbTextOps.topicLine("2026-09-25 10:00", "周末怎么约")
        assertEquals("一行话题长什么样是要落盘的格式，改动它是一次显式决定",
            "- [2026-09-25 10:00] 正在聊：周末怎么约", line)
        assertEquals("周末怎么约", KbTextOps.topicLabel(line))
    }

    @Test
    fun theKeyValueSuffixIsNotPartOfTheTopicName() {
        assertEquals("吃饭", KbTextOps.topicLabel("- [2026-09-25 10:00] 正在聊：吃饭 | key：meal"))
    }

    /**
     * 钉住一条既存怪癖，不等于认可它：首行没有标记时，`substringAfter` 返回整行。
     *
     * 于是旧格式 `- [2026-09-16 09:00] 旧话题` 会被当成话题名叫"整个那一行"，
     * `rotateTopic` 的归档标题里就会出现时间戳。真要修得先决定"读不到时给空串还是兼容旧格式"，
     * 那是独立一次行为变更（会影响初始态判断与归档标题），不在这一格顺手改。
     */
    @Test
    fun aLineWithoutTheMarkerCurrentlyReadsBackAsTheWholeLine() {
        assertEquals(
            "既存怪癖：没有标记就没有切分点，整行被当话题名（要改请先决定读不到时给什么）",
            "- [2026-09-16 09:00] 旧话题",
            KbTextOps.topicLabel("- [2026-09-16 09:00] 旧话题")
        )
    }

    /** 仓库那两个入口真的共用同一把尺（不只是函数写了，接错线照样量不到） */
    @Test
    fun theRepositoryReadsBackWhatItWrote() = runBlocking {
        val dir = File(root, "kb").apply { mkdirs() }
        File(dir, "kb.json").writeText(
            """{"name":"kb","displayName":"kb","updatedAt":"2026-09-25T09:00:00+08:00","active":true}""",
            Charsets.UTF_8
        )
        File(dir, "moment").mkdirs()

        repo.setCurrentTopic("kb", "第一次约饭")
        val onDisk = File(dir, "moment/topic.md").readText(Charsets.UTF_8)
        assertTrue("落盘的那行该由 topicLine 生成：$onDisk", onDisk.contains(KbTextOps.TOPIC_MARKER))
        assertEquals("第一次约饭", repo.getCurrentTopic("kb"))
    }

    /** 标记字面量只许住在这一个文件里（写侧曾经四处各写一份） */
    @Test
    fun onlyOneFileInTheProductionCodeOwnsTheMarkerLiteral() {
        val appRoot = File("src/main/java/com/lovebrain/app")
            .takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")
        val sources = appRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("扫到 ${sources.size} 个生产源文件——少于 10 个说明路径写错，这条尺会永远通过",
            sources.size >= 10)

        val offenders = sources.map { f ->
            f.name to f.readText(Charsets.UTF_8).lines().filter { line ->
                // 注释里提这件事是应该的，别把说明文字判成违规
                !line.trim().startsWith("*") && !line.trim().startsWith("//") &&
                    !line.trim().startsWith("/*") && line.contains("正在聊")
            }
        }.filter { it.second.isNotEmpty() }

        assertEquals(
            "话题标记的字面量只该出现在 KbTextOps.kt（写侧四处各写一份时，改一处就静默读不出话题）：" +
                offenders.joinToString("; ") { "${it.first} → ${it.second}" },
            listOf("KbTextOps.kt"),
            offenders.map { it.first }
        )
    }
}
