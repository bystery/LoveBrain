package com.lovebrain.app.data

import com.lovebrain.app.domain.port.PanelRequestPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [PanelRequestPort] 端口视图的契约单测（实现本体是 `EventBus` 单例）。
 *
 * SetupActivity 的面板请求端口化后，ui 只认 [PanelRequestPort]；FloatingService 那一侧
 * 消费的仍是 `EventBus.panelRequest` / `consumePanelRequest()`。这格钉的就是两头的**对得上**：
 * ① 经端口 requestPanel(mode) 投出的请求，`panelRequest` StateFlow 里看得见（replay=1，
 *    服务未启动时投的请求不丢——语义与端口化前逐字相同）；
 * ② `consumePanelRequest()` 取走后置空（服务不重复触发）；
 * ③ 再取一次为 null（"消费后置空"只发生一次）。
 * 注意 EventBus 是全局单例：断言一律以本用例自发值为准（EventBusCaptureTest 同一口径）。
 */
class PanelRequestPortTest {

    @Test
    fun `a request sent through the port lands on the bus the service consumes`() {
        val port: PanelRequestPort = EventBus

        port.requestPanel(1)

        assertEquals(1, EventBus.panelRequest.value?.mode)
    }

    @Test
    fun `consuming takes the request away and clears the slot`() {
        EventBus.requestPanel(0)

        val consumed = EventBus.consumePanelRequest()

        assertEquals(0, consumed?.mode)
        assertNull("消费后必须置空，防服务重启重复触发", EventBus.panelRequest.value)
        assertNull("再取一次不该还有", EventBus.consumePanelRequest())
    }
}
