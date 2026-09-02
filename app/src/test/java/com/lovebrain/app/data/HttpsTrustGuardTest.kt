package com.lovebrain.app.data

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * （ 清偿）回归：网络信任闸门——http:// 仅放行 loopback 主机，对外强制 https。
 * 直接测 [HttpsTrustGuard.enforce] 纯判定，不依赖 Android 运行时（PART13 ~T4，恰 4 条）。
 */
class HttpsTrustGuardTest {

    /** ：https 对外地址放行 */
    @Test
    fun t1_https_external_passes() {
        HttpsTrustGuard.enforce("https://api.example.com/v1")
    }

    /** T2：loopback 三主机放行（IPv4 带端口 / localhost 带端口 / [::1] 带方括号与端口） */
    @Test
    fun t2_loopback_hosts_pass() {
        HttpsTrustGuard.enforce("http://127.0.0.1:11434")
        HttpsTrustGuard.enforce("http://localhost:8080")
        HttpsTrustGuard.enforce("http://[::1]:11434")
    }

    /** T3：对外 http 拦截，固定文案引导改用 https */
    @Test
    fun t3_http_external_blocked() {
        try {
            HttpsTrustGuard.enforce("http://api.example.com/v1")
            fail("对外 http 地址应被拦截")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("https://"))
        }
    }

    /** T4：局域网 IP 的 http 同样拦截 */
    @Test
    fun t4_http_lan_ip_blocked() {
        try {
            HttpsTrustGuard.enforce("http://192.168.1.10:11434")
            fail("局域网 http 地址应被拦截")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("https://"))
        }
    }
}
