package com.lovebrain.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *   配置错误分类契约测试（演进表计划 6 条，本文件 7 条）：
 * isConfigError 覆盖前缀 + 五类固定文案；网络/超时类错误不误判；
 * stripConfigPrefix 去前缀透传（无标记原样返回）。
 * 锚定先例：mapApiError/HttpsTrustGuard 的 internal 单测直测口径。
 */
class ConfigErrorClassificationTest {

    // ═══ isConfigError 正向 ═══

    @Test
    fun prefix_marked_message_is_config_error() {
        assertTrue(DeepSeekRepository.isConfigError("CONFIG_ERROR:请先配置一个模型供应商"))
        assertTrue(DeepSeekRepository.isConfigError("CONFIG_ERROR:API Key 缺失，请检查工单配置"))
    }

    @Test
    fun normalizeBaseUrl_messages_are_config_errors() {
        assertTrue(DeepSeekRepository.isConfigError("地址必须以 http:// 或 https:// 开头"))
        assertTrue(DeepSeekRepository.isConfigError("检测到 API Key 误填入地址栏，请检查"))
    }

    @Test
    fun trust_guard_messages_are_config_errors() {
        assertTrue(DeepSeekRepository.isConfigError("地址格式不正确，请检查后重试"))
        assertTrue(DeepSeekRepository.isConfigError(
            "http:// 地址仅限本机（127.0.0.1/::1/localhost）；对外地址请使用 https://，避免 API Key 明文传输"
        ))
    }

    @Test
    fun map_api_error_401_and_no_ticket_are_config_errors() {
        // mapApiError 401 人话（经真实函数产出，防文案漂移）
        assertTrue(DeepSeekRepository.isConfigError(
            DeepSeekRepository.mapApiError("invalid api key", 401)
        ))
        // buildRequest 无工单
        assertTrue(DeepSeekRepository.isConfigError("还没有可用的模型配置，请到设置里检查"))
    }

    // ═══ isConfigError 负向（防误伤真实网络/限流错误——这些仍要走重试或如实提示） ═══

    @Test
    fun network_and_server_errors_are_not_config_errors() {
        assertFalse(DeepSeekRepository.isConfigError("请求超时，请重试"))
        assertFalse(DeepSeekRepository.isConfigError("网络波动，正在重试…"))
        assertFalse(DeepSeekRepository.isConfigError("服务繁忙，稍后再试"))
        assertFalse(DeepSeekRepository.isConfigError("请求太频繁了，稍等几秒再试"))
        assertFalse(DeepSeekRepository.isConfigError(DeepSeekRepository.mapApiError("timeout", 0)))
    }

    // ═══ stripConfigPrefix ═══

    @Test
    fun strip_prefix_passes_original_text_through() {
        assertEquals(
            "请先配置一个模型供应商",
            DeepSeekRepository.stripConfigPrefix("CONFIG_ERROR:请先配置一个模型供应商")
        )
    }

    @Test
    fun strip_prefix_is_identity_without_marker() {
        assertEquals("请求超时，请重试", DeepSeekRepository.stripConfigPrefix("请求超时，请重试"))
        assertEquals("", DeepSeekRepository.stripConfigPrefix(""))
    }
}
