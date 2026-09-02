package com.lovebrain.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  回归（，  修订表；计 1 条用例）：
 * 400+thinking 族人话兜底文案锚定——原"已自动降级"描述失真
 * （降级链用尽才走到该分支），换成如实指引；经 internal 放开直测
 * （先例 = HttpsTrustGuard），companion 成员无需构造 Repository。
 */
class DeepSeekRepositoryMapApiErrorTest {

    @Test
    fun thinking_400_fallback_is_honest_guidance() {
        // raw 含 thinking 族关键词但不命中参数不支持族（无 unknown parameter/unsupported/invalid field）
        assertEquals(
            "模型不支持思考模式参数，请切换直出模式后重试",
            DeepSeekRepository.mapApiError("Invalid value for thinking", code = 400)
        )
    }
}
