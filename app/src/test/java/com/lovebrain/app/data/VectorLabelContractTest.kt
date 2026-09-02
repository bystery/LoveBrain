package com.lovebrain.app.data

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 向量标签契约单测（ ·  A1，  / ）：
 * schema/warmth.md 模板五维中文标签 ↔ KnowledgeRepository.vectorDims 解析键一致性。
 *
 * 资产来源：app/src/main/assets（由 build.gradle.kts sourceSets.test.resources.srcDir
 * 挂入 test classpath，加载模式照抄 PromptBuilderStageContractTest.loadAsset，禁止副本/内联）。
 * Repository 实例化模式照抄 KnowledgeRepositoryReentrancyTest（临时目录 + mockk）。
 *
 * 注：A1 为零匹配路径新增观测日志（L.w → android.util.Log），单测环境用
 * mockkStatic 屏蔽 Log，使零匹配回落分支可直接断言。
 */
class VectorLabelContractTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope

    private fun loadAsset(path: String): String {
        val cls = ClassLoader.getSystemClassLoader()
        val res = cls.getResourceAsStream(path)
            ?: error("资产 $path 未在 test classpath 上——检查 build.gradle.kts sourceSets.test.resources.srcDir")
        return res.bufferedReader().use { it.readText() }
    }

    private fun newRepo(): KnowledgeRepository {
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kr_vector_label").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // A1 零匹配观测日志触达 android.util.Log，单测环境屏蔽
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        appScope.cancel()
        root.deleteRecursively()
    }

    /** ：schema/warmth.md 五维中文标签在场，且与 vectorDims 中文键逐一对应（英文标签已清零） */
    @Test
    fun `warmth schema template uses chinese vector labels matching vectorDims`() {
        val warmth = loadAsset("schema/warmth.md")
        // 与 KnowledgeRepository.vectorDims 中文键同源（ A1：模板标签中文化后钉死）
        val cnDims = listOf("亲密度", "信任度", "承诺度", "激情", "安全感")
        cnDims.forEach { cn ->
            assertTrue(
                "schema/warmth.md 应含中文五维标签行「- $cn：待评估/100」",
                warmth.contains("- $cn：待评估/100")
            )
        }
        // 英文标签零残留（只扫冒号式英文键，防误伤中文行内英文说明）
        listOf("intimacy:", "trust:", "commitment:", "passion:", "security:").forEach { en ->
            assertTrue("schema/warmth.md 不应残留英文标签「$en」", !warmth.contains(en))
        }
    }

    /** T2：readVector 对中文标签格式解析出正确值（中文键解析 → 英文键返回） */
    @Test
    fun `readVector parses chinese label values correctly`() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                val dir = File(root, "kb").apply { mkdirs() }
                File(dir, "understand").mkdirs()
                File(dir, "understand/warmth.md").writeText(
                    "- 亲密度：78/100\n- 信任度：60/100\n- 承诺度：45/100\n- 激情：90/100\n- 安全感：33/100\n",
                    Charsets.UTF_8
                )

                val vector = newRepo().readVector("kb")

                assertEquals("亲密度应解析为 78", 78, vector["intimacy"])
                assertEquals("信任度应解析为 60", 60, vector["trust"])
                assertEquals("承诺度应解析为 45", 45, vector["commitment"])
                assertEquals("激情应解析为 90", 90, vector["passion"])
                assertEquals("安全感应解析为 33", 33, vector["security"])
            }
        }
    }

    /** T3：readVector 对缺失/乱格式回落 50 且不抛异常（零匹配观测日志已屏蔽） */
    @Test
    fun `readVector falls back to 50 on missing or malformed warmth`() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                // ① warmth.md 缺失（kb 目录无该文件）→ 五维全 50
                File(root, "empty").mkdirs()
                val missingVector = newRepo().readVector("empty")
                assertEquals("缺失时五维各回落 50", listOf(50, 50, 50, 50, 50),
                    listOf(
                        missingVector.getValue("intimacy"), missingVector.getValue("trust"),
                        missingVector.getValue("commitment"), missingVector.getValue("passion"),
                        missingVector.getValue("security")
                    ))

                // ② 乱格式（中文标签在场但冒号后无数字）→ 五维全 50，不抛异常
                val dir = File(root, "kb2").apply { mkdirs() }
                File(dir, "understand").mkdirs()
                File(dir, "understand/warmth.md").writeText(
                    "- 亲密度：破损行无数字也无斜杠结构\n" +
                        "- 信任度：？？？\n" +
                        "- 承诺度：NaN占位\n" +
                        "- 激情：空值占位\n" +
                        "- 安全感：xx/yy\n",
                    Charsets.UTF_8
                )
                val malformedVector = newRepo().readVector("kb2")
                assertEquals("乱格式时五维各回落 50", listOf(50, 50, 50, 50, 50),
                    listOf(
                        malformedVector.getValue("intimacy"), malformedVector.getValue("trust"),
                        malformedVector.getValue("commitment"), malformedVector.getValue("passion"),
                        malformedVector.getValue("security")
                    ))
            }
        }
    }
}
