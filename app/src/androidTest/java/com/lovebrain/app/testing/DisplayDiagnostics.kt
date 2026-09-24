package com.lovebrain.app.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed

/**
 * 「组件找到了但 assertIsDisplayed 说它不在屏幕上」——原生断言在 CI 上只给一句话，
 * 排查时等于没给证据。2026-09-24 真机 19 条失败里有 8 条只有这一句，
 * 所以先把取证能力放进仓库，再谈修（交接文档 §3：先取证再动手）。
 */
fun SemanticsNodeInteraction.assertIsDisplayedDiagnosed(what: String) {
    try {
        assertIsDisplayed()
    } catch (t: Throwable) {
        throw AssertionError(
            "$what 断言「已显示」失败。\n  节点：${describe(what)}\n  原始信息：${t.message}",
            t
        )
    }
}

/**
 * 断言当时能取到的几何量。
 *
 * boundsInRoot 是公开 API；再细一层（positionInRoot / size / clipInfo / isShowing）
 * 挂在 SemanticsNode.layoutInfo 上，而那个属性在不同 Compose 版本里的可见性不一样，
 * 所以用反射取：取不到就少打几行，绝不让"想多要点证据"这件事把测试编译搞挂。
 */
fun SemanticsNodeInteraction.describe(what: String): String {
    val node = runCatching { fetchSemanticsNode(what) }.getOrNull()
        ?: return "节点不存在（fetchSemanticsNode 失败）"
    val texts = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("|") { it.text } ?: "-"
    return buildString {
        append("text=").append(texts)
        append(" boundsInRoot=").append(runCatching { node.boundsInRoot }.getOrNull())
        append(layoutInfoViaReflection(node))
    }
}

private fun layoutInfoViaReflection(node: Any): String {
    val info = runCatching { node.javaClass.getMethod("getLayoutInfo").invoke(node) }.getOrNull()
        ?: return " layoutInfo=<不可得>"
    fun field(name: String): String = runCatching {
        val m = info.javaClass.methods.firstOrNull { it.name.equals("get${name.replaceFirstChar { c -> c.uppercaseChar() }}", true) }
            ?: return@runCatching "<无此字段>"
        m.invoke(info)?.toString() ?: "null"
    }.getOrDefault("<取不到>")
    return buildString {
        append(" position=").append(field("positionInRoot"))
        append(" size=").append(field("size"))
        append(" isShowing=").append(field("isShowing"))
        append(" isAttached=").append(field("isAttached"))
    }
}
