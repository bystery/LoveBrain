package com.lovebrain.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 供应商这一屏的 Key 遮蔽：连接失败的异常文本上屏之前，长得像 Key 的那一串必须被换掉。
 *
 * `redactSecrets` 是一颗纯函数，不碰组合也不碰颜色——语义树里量不到文字内容，
 * 所以这条隐私判据只能这样留下来被直接判。
 */
class ProviderKeyRedactionTest {

    private val hidden = "(hidden)"

    @Test
    fun `an sk style key inside an exception message never survives`() {
        val raw = "HTTP 401 Unauthorized for sk-ABCDEFGHIJKLMNOP1234 at /v1/models"
        val out = redactSecrets(raw, hidden)
        assertFalse("Key 原文不许出现在提示里：$out", out.contains("sk-ABCDEFGHIJKLMNOP1234"))
        assertTrue("遮蔽词必须落在原位，用户才知道这一格被收掉了：$out", out.contains(hidden))
    }

    @Test
    fun `a bearer header echo and an api_key query param are both redacted`() {
        val bearer = redactSecrets("Authorization: Bearer_xyzTOKEN999999 was rejected", hidden)
        assertFalse("Bearer 后面的 token 本体不许留下：$bearer", bearer.contains("xyzTOKEN999999"))

        val param = redactSecrets("rejected api_key=abcdef123456, retry later", hidden)
        assertFalse("查询串里的 api_key 值不许留下：$param", param.contains("abcdef123456"))
        assertTrue("句子其余部分还要读得出来：$param", param.contains("retry later"))
    }

    /**
     * 反向判据：遮蔽不是"把整句抹掉"。
     * 反例——实现图省事直接返回固定一句"出错了"：这一格会红，
     * 因为用户在连接测试这里就看不见"到底是连不上还是模型名写错了"。
     */
    @Test
    fun `ordinary failure detail is kept verbatim`() {
        val raw = "Unable to resolve host api.example.com: No address associated with hostname"
        assertEquals(raw, redactSecrets(raw, hidden))
    }

    /** 空/空白（异常没有 message）不许变成 "null" 这种字样 */
    @Test
    fun `blank detail stays blank so the caller can substitute its own sentence`() {
        assertEquals("", redactSecrets("", hidden))
        assertEquals("   ", redactSecrets("   ", hidden))
    }

    /** 短到不像 Key 的普通词不许被误伤（反例：正则宽到把 "sk-" 后面的任何字符都吃掉） */
    @Test
    fun `a short sk-like fragment that is not a key is left alone`() {
        val raw = "the sk-ab setting is unrelated"
        assertEquals(raw, redactSecrets(raw, hidden))
    }
}
