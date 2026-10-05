package com.lovebrain.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PromptBuilder 阶段契约单测——修 / 的回归门。
 *
 * 验证目标：PromptBuilder.extractStageSection 用正则 `## <stage>` 在 suggest.md / stage.md
 * 中匹配当前阶段小节。若资产标题与 StageCatalog.ALL 的阶段值不一致（如缺"期"后缀），
 * 正则会失配 → 锦囊/阶段策略注入静默失败。
 *
 * 资产来源：app/src/main/assets（由 build.gradle.kts sourceSets.test.resources.srcDir
 * 挂入 test classpath，改坏即红灯，禁止副本/内联）。
 */
class PromptBuilderStageContractTest {

    private fun loadAsset(path: String): String {
        val cls = ClassLoader.getSystemClassLoader()
        val res = cls.getResourceAsStream(path)
            ?: error("资产 $path 未在 test classpath 上——检查 build.gradle.kts sourceSets.test.resources.srcDir")
        return res.bufferedReader().use { it.readText() }
    }

    // ═══ 资产可读性 ═══

    // 2026-10-03：`suggest asset is loadable from test classpath` 这一格已摘除——
    // 它读的是锦囊那颗引擎资产，该资产随「锦囊」功能整删（用户授权），已不在盘上；
    // 这一格测的就是锦囊那段的拼装，主体没了，格子留着只会变成"资产读不到"的恒红。

    @Test
    fun `stage asset is loadable from test classpath`() {
        val content = loadAsset("engine/system_prompt/stage.md")
        assertTrue("stage.md 不应为空", content.isNotBlank())
    }

    // : suggest.md 已重写为轻量日常行动建议引擎，不再按阶段（## XX期）分割。
    // 阶段策略注入只在 stage.md 中检查。

    // : suggest.md 已重写——不再包含阶段小节（## XX期），此契约不再适用。
    // suggest.md 现在是轻量日常行动建议引擎，不按阶段分割。

    // : suggest.md 已重写——不再包含阶段小节，重复检查不再适用。

    // : suggest.md 已重写——不再包含阶段标题，裸标题检查不再适用。

    // ═══ stage.md 阶段标题契约 ═══

    @Test
    fun `stage md has section for every StageCatalog stage`() {
        val stage = loadAsset("engine/system_prompt/stage.md")
        val missing = StageCatalog.ALL.filter { s ->
            val re = Regex(
                "(^|\\n)##\\s*${Regex.escape(s)}\\s*\\n(.*?)(?=\\n##\\s|\\z)",
                RegexOption.DOT_MATCHES_ALL
            )
            re.find(stage) == null
        }
        assertTrue(
            "stage.md 缺少阶段小节：$missing",
            missing.isEmpty()
        )
    }

    @Test
    fun `stage md has no bare stage titles missing the 期 suffix`() {
        val stage = loadAsset("engine/system_prompt/stage.md")
        StageCatalog.ALL.forEach { s ->
            val bare = s.removeSuffix("期")
            val bareRe = Regex("(^|\\n)##\\s*${Regex.escape(bare)}\\s*\\n(?!.{0,3}期)")
            assertTrue(
                "stage.md 出现裸阶段标题 '## $bare'（缺'期'后缀）",
                bareRe.find(stage) == null
            )
        }
    }

    // ═══ 阶段一致性总检 ═══
    // 注：{{LESSONS_SCHEMA}} 与 {{SCHEMA_HEADERS}} 占位符已随主人决策废除（经验引擎不再注入 schema；
    // reflect 输入已含旧画像全文），对应占位符契约测试同步删除。

    @Test
    fun `onboarding asset exists at new path`() {
        //  回归门：onboarding.md 曾在 engine/ 下，搬到 engine/knowledge_prompt/ 后读取点没改
        val onboarding = loadAsset("engine/knowledge_prompt/onboarding.md")
        assertTrue("onboarding.md 不应为空", onboarding.isNotBlank())
    }

    @Test
    fun `all AssetRegistry registered assets exist on test classpath`() {
        AssetRegistry.ALL.forEach { path ->
            val res = javaClass.classLoader!!.getResource(path)
            assertTrue(
                "AssetRegistry 注册的资产不存在：$path（ 类静默断链）",
                res != null
            )
        }
    }

    // : suggest.md 已重写——不再包含阶段标题，此一致性检查只看 stage.md。
}
