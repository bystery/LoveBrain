package com.lovebrain.app.feature.feedback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.data.FeedbackCaseRepository
import com.lovebrain.app.model.FeedbackCase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 导出那一份文件**是**合法 JSON，而旧的那一份**还读得起来**。
 *
 * 两件事都是"UI 不再展示 ≠ 数据没了"这条判据的证人，所以走真仓库
 * （[FeedbackCaseRepository] 本轮只读不改），不拿假序列化糊一遍：
 *
 * · 反例一：导出那一格退回 Markdown（或者把列表 `toString()` 出去）——
 *   `Json.parseToJsonElement` 当场抛，这一格红；
 * · 反例二：为了"卡片干净"而把 modelId / contextMode / appVersion / buildType / tokens
 *   从数据模型或写盘里删掉——第二条用例读旧文件时那些字段就没了，红；
 * · 反例三：旧文件里出现本版本不认识的新字段就整本读不出来——
 *   夹具里那句 `sentimentHint` 就是专门放的那颗未知键。
 */
@RunWith(RobolectricTestRunner::class)
// 走空壳 App：这一格只 new `FeedbackCaseRepository(context)` + 读写盘，压根不碰 Koin 容器。
// 默认清单 App（LoveBrainApp）在 onCreate 里无条件 startKoin，而 GlobalContext 是 JVM 静态的——
// Robolectric 在同一沙箱里给第二个用例再建一次 Application 就抛 KoinAppAlreadyStartedException
// （实测栈顶在 RobolectricTestRunner.beforeTest→installAndCreateApplication，早于本类的 @Before/@After，
// 所以在用例里 stopKoin / 判 isKoinStarted 都拦不住装配阶段的这一次）。改用 UiProbeApplication 从根上绕开。
@Config(application = UiProbeApplication::class)
class FeedbackExportJsonTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun caseStore(): File = File(File(context.filesDir, "feedback"), "cases.json")

    private fun freshRepo(): FeedbackCaseRepository {
        // 每次用例都从空盘开始，否则上一条用例留下的 cases.json 会让"读旧文件"变成"读自己刚写的"
        caseStore().delete()
        return FeedbackCaseRepository(context)
    }

    private fun case(id: String, reply: String, note: String = "") = FeedbackCase(
        caseId = id,
        schemeIdentityKey = "STYLE:B",
        candidateReply = reply,
        categories = emptyList(),
        reasons = listOf("角色错"),
        userNote = note,
        contextMode = "full",
        modelId = "deepseek-chat",
        promptVersion = "pv-9",
        timestamp = "2026-10-03 12:00:00",
        appVersion = "1.40-re1",
        buildType = "release",
        promptTokens = 1200,
        completionTokens = 340,
        costYuan = 0.02
    )

    @Test
    fun `the exported file parses as a json array and round trips`() = runBlocking {
        val repo = freshRepo()
        val a = case("j-1", "她说她在忙")
        val b = case("j-2", "含\"引号\"、中文与\n换行的一句", note = "用户补的那句")
        repo.save(a); repo.save(b)

        val text = repo.exportJson(repo.getAll())

        val parsed = Json.parseToJsonElement(text).jsonArray
        assertEquals("导出的必须是那两条案例的数组", 2, parsed.size)
        assertEquals(
            "数组里的每一项都得是对象（Markdown 那一档的产物过不了这一句）",
            listOf("j-1", "j-2"),
            parsed.map { it.jsonObject["caseId"]!!.jsonPrimitive.content }
        )
        // 往返一致：读回来必须与写进去的那两条逐字相等（字段被裁掉就会在这里红）
        val decoded = Json.decodeFromString<List<FeedbackCase>>(text)
        assertEquals(listOf(a, b), decoded)
    }

    @Test
    fun `a cases file written by an older build still loads with its diagnostic fields`() {
        val legacy = """
            [
              {
                "caseId": "old-1",
                "schemeIdentityKey": "DIRECTION:F",
                "candidateReply": "旧的那一句候选",
                "categories": ["EXPRESSION_DISLIKE"],
                "reasons": ["太油"],
                "userNote": "旧数据里的补充",
                "contextMode": "full",
                "modelId": "gpt-old",
                "promptVersion": "pv-1",
                "timestamp": "2026-09-01 08:00:00",
                "dialogueSnapshot": [{ "speaker": "PARTNER", "text": "她那句" }],
                "memoryRefs": ["m-7"],
                "appVersion": "1.40-re1",
                "buildType": "release",
                "promptTokens": 900,
                "completionTokens": 120,
                "costYuan": 0.01,
                "status": "PENDING",
                "sentimentHint": "本版本不认识的新字段"
              }
            ]
        """.trimIndent()
        val file = caseStore().also { it.parentFile?.mkdirs() }
        file.writeText(legacy)
        assertTrue("夹具那份旧文件没落到位：$file", file.isFile)

        // 这一格**不**走 freshRepo()：它要的正是"盘上已经有一份旧 JSON"的那个起点
        val loaded = runBlocking { FeedbackCaseRepository(context).getAll() }

        assertEquals("旧文件读不出来——这一格要保的正是「不要展示」不等于「删历史」", 1, loaded.size)
        val c = loaded.single()
        assertEquals("old-1", c.caseId)
        assertEquals("gpt-old", c.modelId)
        assertEquals("full", c.contextMode)
        assertEquals("1.40-re1", c.appVersion)
        assertEquals("release", c.buildType)
        assertEquals(900, c.promptTokens)
        assertEquals(0.01, c.costYuan, 1e-9)
        assertEquals(listOf("太油"), c.reasons)
        assertEquals("旧数据里的补充", c.userNote)
        assertTrue("未知键必须被容忍：" + c.dialogueSnapshot, c.dialogueSnapshot.isNotEmpty())
    }
}
