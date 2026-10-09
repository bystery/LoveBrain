package com.lovebrain.app.feature.intent

import com.lovebrain.app.model.IntentConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 编辑器库绑定的一对格子：绑定只在"编辑器开着"期间存活。
 *
 * 背景（席6 交回时点名的口子）：`editorKbName` 过去在 [IntentController.dismissEditor] 时不清，
 * 于是"关掉编辑器 → 切库 → 任何漏接线的 save()"会把草稿写进**用户已经离开的那本库**——
 * 写侧身份守卫只挡得住"落屏"，挡不住落盘本身，行为测试也不会红。
 *
 * 红条件：把 dismissEditor 里的 `editorKbName = null` 拿掉（退回旧形状），第一格当场红
 * （writtenTo 停在开编辑器那会儿的库名）；第二格则证明"开着时冻结"的契约没有被顺手改坏。
 */
class IntentEditorBindingTest {

    /** 一台控制器：激活库可变（模拟用户切库），落盘把目标库名交回 [writtenTo] */
    private fun controllerFor(
        activeKb: () -> String?,
        writtenTo: (String) -> Unit
    ): IntentController {
        var stored = IntentConfig()
        return IntentController(
            scope = CoroutineScope(Dispatchers.Unconfined),
            readActiveKbName = activeKb,
            readIntent = { stored },
            writeIntent = { kbName, text, enabled, expiry, expiryDate, status ->
                writtenTo(kbName)
                stored = IntentConfig(
                    text = text,
                    enabled = enabled,
                    revision = stored.revision + 1,
                    expiry = expiry,
                    expiryDate = expiryDate,
                    status = status
                )
                stored
            },
            readNow = { "2026-10-09 09:00" }
        )
    }

    @Test
    fun `a save after dismissing the editor writes to the library active right now`() {
        var activeKb: String? = "小美"
        var writtenTo: String? = null
        val controller = controllerFor({ activeKb }, { writtenTo = it })

        controller.openEditor() // 冻结到"小美"
        activeKb = "小丽" // 用户切了库
        controller.dismissEditor()
        controller.save("周末约她", enabled = true)

        assertEquals(
            "dismiss 后的保存必须落到此刻激活的库（旧形状会把草稿写进已经离开的「小美」）",
            "小丽", writtenTo
        )
    }

    @Test
    fun `while the editor is open the draft stays bound to the library it opened on`() {
        var activeKb: String? = "小美"
        var writtenTo: String? = null
        val controller = controllerFor({ activeKb }, { writtenTo = it })

        controller.openEditor()
        activeKb = "小丽" // 编辑器开着的时候库变了（异常路径）
        controller.save("周末约她", enabled = true)

        assertEquals(
            "开着时冻结在打开那一刻的库，这条契约不许被顺手改坏",
            "小美", writtenTo
        )
    }
}
