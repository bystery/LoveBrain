package com.lovebrain.app.domain

/**
 * 提示词资产注册处：全部 assets 路径的唯一出口。
 * 改名/搬家只改这一处；启动自检（LoveBrainApp debug）防止  类静默断链重演。
 */
object AssetRegistry {

    // ═══ 回复系 system = core + naturalness_check + redline + format（组装顺序即缓存锚点顺序，禁止乱序）═══
    // 谈心/锦囊/润色各以 counseling/suggest/polish 全文为 system；动态内容（知识段/消息/时间戳）一律在 user，
    // 保证 system 前缀稳定以命中上下文缓存
    const val CORE = "engine/system_prompt/core.md"
    const val STAGE = "engine/system_prompt/stage.md"
    const val REDLINE = "engine/system_prompt/redline.md"
    const val NATURALNESS = "engine/system_prompt/naturalness_check.md"
    const val AGGRESSIVE = "engine/system_prompt/aggressive.md"
    const val FORMAT = "engine/system_prompt/format.md"

    // ═══ 引擎附加段 ═══
    const val COUNSELING = "engine/counseling.md"
    const val SUGGEST = "engine/suggest.md"
    const val POLISH = "engine/polish.md"

    // ═══ 知识引擎 ═══
    const val LESSONS = "engine/knowledge_prompt/lessons.md"
    const val ONBOARDING = "engine/knowledge_prompt/onboarding.md"
    const val REFLECT = "engine/knowledge_prompt/reflect.md"
    const val VECTOR = "engine/knowledge_prompt/vector.md"
    const val ENGINE_README = "engine/README.md"

    /** 知识库结构模板（schema 目录文件名清单，与 KnowledgeRepository.loadSchema 消费方一致） */
    val SCHEMA_NAMES = listOf(
        "me", "her", "warmth",
        "topic", "recent", "scene", "plan",
        "lessons", "raw_chat", "raw_topic", "raw_scene", "counseling_log"
    )

    fun schema(name: String): String = "schema/$name.md"

    /** 全部注册资产（启动自检用；新增资产必须登记于此，否则自检覆盖不到） */
    val ALL: List<String> = listOf(
        CORE, STAGE, REDLINE, NATURALNESS, AGGRESSIVE, FORMAT,
        COUNSELING, SUGGEST, POLISH,
        LESSONS, ONBOARDING, REFLECT, VECTOR, ENGINE_README
    ) + SCHEMA_NAMES.map { schema(it) }
}
