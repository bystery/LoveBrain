package com.lovebrain.app.data

import com.lovebrain.app.AppConfig
import com.lovebrain.app.model.ProviderFailure
import com.lovebrain.app.model.ProviderFailureException
import com.lovebrain.app.model.ReplyFailureKind
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 一次 HTTP 交换怎么在协程里发出去、怎么在协程被取消时真的断掉。
 *
 * 变化理由只有这一条：传输机制（超时档位、取消接线、状态码到异常的映射）变了才动这里。
 * 请求体的字段形状在 `OpenAiChatWire`，账单与用量在 `ApiUsageTracker`。
 *
 * 取消语义是这一层最要紧的约定：
 * - `cont.invokeOnCancellation { call.cancel() }` —— 协程取消即中断网络连接；
 * - 挂起函数**不接住** CancellationException，取消原样上抛，调用方自己区分
 *   "用户取消"与"真失败"（流式那一侧靠 `call.isCanceled()` 判，非流式靠重抛）。
 */
internal object CancellableHttpTransport {

    /**
     * 一个只有超时档、不设 proxy 的客户端（设备侧真链路证据依赖这个形状：
     * 它走 `ProxySelector.getDefault()`，任何代理改写都会让 androidTest
     * `MainChainHarness.providerDiagnosis` 那套 loopback 证据失真）。
     */
    fun client(readTimeoutSec: Long): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(AppConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
        .writeTimeout(AppConfig.WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    /**
     * 流式客户端按**读超时档位**复用：一个档位一颗，建过就不再建（OkHttp 自己的连接池与
     * 线程池都在 client 里，每轮生成 new 一颗等于把连接复用全扔掉）。
     *
     * 档位从哪来：`ProviderRequestConfig.streamReadTimeoutSec`——也就是这张工单上那一档
     * （[com.lovebrain.app.GenerationTimeoutTier] 的有界白名单）。**这里能放开的只有读**：
     * 连接与写超时仍走上面那两颗固定常量，慢服务不该把"根本联系不上"拖成 300 秒。
     */
    fun streamClient(readTimeoutSec: Long): OkHttpClient =
        streamClients.getOrPut(readTimeoutSec) { client(readTimeoutSec) }

    private val streamClients = java.util.concurrent.ConcurrentHashMap<Long, OkHttpClient>()

    /**
     * 携带 HTTP 状态码的异常，供连通性探测区分 404/405 与其他错误。
     */
    class HttpCodeException(val code: Int, message: String) : Exception(message)

    /** 状态码 + 响应体 */
    data class CodeBody(val code: Int, val body: String)

    /**
     * 返回 HTTP 状态码 + body 的可取消请求执行。
     * 成功（2xx）返回 [CodeBody]；非 2xx 抛 [HttpCodeException]（携带状态码 + body 摘要）。
     * 网络失败抛原始 [IOException]。
     */
    suspend fun executeWithCode(client: OkHttpClient, request: Request): CodeBody =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (resp.isSuccessful) {
                            if (cont.isActive) cont.resume(CodeBody(resp.code, body))
                        } else {
                            if (cont.isActive) cont.resumeWithException(
                                HttpCodeException(resp.code, body.take(200))
                            )
                        }
                    }
                }
            })
        }

    /**
     * 可取消的异步请求执行。协程取消时自动 call.cancel()。
     * 非 2xx 一律翻成 [ProviderFailureException]（typed），不把英文原文甩给下游。
     */
    suspend fun execute(client: OkHttpClient, request: Request): String =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val body = resp.body?.string() ?: ""
                        when {
                            resp.code == 401 ->
                                if (cont.isActive) cont.resumeWithException(
                                    ProviderFailureException(
                                        ProviderFailure(
                                            ReplyFailureKind.Auth,
                                            "API Key 无效，请检查设置"
                                        )
                                    ))
                            resp.code == 429 ->
                                if (cont.isActive) cont.resumeWithException(
                                    ProviderFailureException(ProviderFailure(ReplyFailureKind.RateLimited)))
                            !resp.isSuccessful ->
                                if (cont.isActive) cont.resumeWithException(
                                    ProviderFailureException(OpenAiChatWire.classifyApiError(body.take(200), resp.code)))
                            else ->
                                if (cont.isActive) cont.resume(body)
                        }
                    }
                }
            })
        }
}
