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

    @Test
    fun `suggest asset is loadable from test classpath`() {
        val content = loadAsset("engine/suggest.md")
        assertTrue("suggest.md 不应为空", content.isNotBlank())
    }

    @Test
    fun `stage asset is loadable from test classpath`() {
        val content = loadAsset("engine/system_prompt/stage.md")
        assertTrue("stage.md 不应为空", content.isNotBlank())
    }

    // ═══ suggest.md 阶段标题契约 ═══

    @Test
    fun `suggest md has section for every StageCatalog stage`() {
        val suggest = loadAsset("engine/suggest.md")
        val missing = StageCatalog.ALL.filter { stage ->
            // 复刻 PromptBuilder.extractStageSection 的正则
            val re = Regex(
                "(^|\\n)##\\s*${Regex.escape(stage)}\\s*\\n(.*?)(?=\\n##\\s|\\z)",
                RegexOption.DOT_MATCHES_ALL
            )
            re.find(suggest) == null
        }
        assertTrue(
            "suggest.md 缺少阶段小节（正则失配，会导致锦囊阶段策略注入失败）：$missing",
            missing.isEmpty()
        )
    }

    @Test
    fun `suggest md has exactly one section per stage no duplicates`() {
        val suggest = loadAsset("engine/suggest.md")
        StageCatalog.ALL.forEach { stage ->
            val re = Regex(
                "(^|\\n)##\\s*${Regex.escape(stage)}\\s*\\n",
                RegexOption.DOT_MATCHES_ALL
            )
            val matches = re.findAll(suggest).toList()
            assertEquals(
                "suggest.md 中 '$stage' 的小节数应为 1（实际 ${matches.size}）",
                1, matches.size
            )
        }
    }

    @Test
    fun `suggest md has no bare stage titles missing the 期 suffix`() {
        //  回归门：suggest.md 历史上漏"期"，正则永远失配
        val suggest = loadAsset("engine/suggest.md")
        StageCatalog.ALL.forEach { stage ->
            val bare = stage.removeSuffix("期")
            // 裸标题形如 "## 初识"（不带"期"）→  旧 Bug
            val bareRe = Regex("(^|\\n)##\\s*${Regex.escape(bare)}\\s*\\n(?!.{0,3}期)")
            assertTrue(
                "suggest.md 出现裸阶段标题 '## $bare'（缺'期'后缀， 回归）",
                bareRe.find(suggest) == null
            )
        }
    }

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

    @Test
    fun `all stages in suggest and stage assets match StageCatalog exactly`() {
        val suggest = loadAsset("engine/suggest.md")
        val stage = loadAsset("engine/system_prompt/stage.md")
        // 资产中所有形如 "## XX期" 的标题必须在 StageCatalog.ALL 内
        val titleRe = Regex("(?m)^##\\s+(.+期)\\s*$")
        val suggestTitles = titleRe.findAll(suggest).map { it.groupValues[1].trim() }.toSet()
        val stageTitles = titleRe.findAll(stage).map { it.groupValues[1].trim() }.toSet()
        val allAssetTitles = suggestTitles + stageTitles

        val orphans = allAssetTitles.filter { it !in StageCatalog.ALL }
        assertTrue(
            "资产中存在不在 StageCatalog 白名单的阶段标题：$orphans",
            orphans.isEmpty()
        )
    }
}
