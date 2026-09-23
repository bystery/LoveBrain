package com.lovebrain.app.testing

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * S1-03/S1-02（审计 §8.1 第 4-5 条）：可控 fake Provider。
 *
 * 为什么用本地 HTTP 服务而不是 MockK / 手写 fake 类：
 * 生产 `DeepSeekRepository`、`GenerationEngine`、`PromptBuilder`、`LoveBrainViewModel`
 * 全是 final Kotlin 类，androidTest 侧既不能子类化，也没有 mockk-android 依赖
 * （app/build.gradle.kts 的 androidTestImplementation 只有 junit / androidx.test /
 * ui-test-junit4）。唯一真实可注入的缝隙就是 Provider 的网络出口。
 * 而 `HttpsTrustGuard` + `res/xml/network_security_config.xml` 明确放行 loopback
 * 明文 HTTP（本地 LLM 场景），所以 127.0.0.1 上的 fake 服务是唯一
 * 「不改生产代码也能注入 Provider」的合法出口。
 *
 * 这样测试跑的是完整生产链：真 ViewModel → 真 GenerationEngine →
 * 真 DeepSeekRepository（真 OkHttp + 真 SSE 解析）→ 本 fake。
 *
 * [requestCount] 同时充当审计要求的「Engine/Provider 调用次数」计数器：
 * 点击空态蓝字必须仍为 0，快速双击必须只为 1。
 */
class FakeProviderServer : AutoCloseable {

    /** 单个请求的应答脚本 */
    sealed class Script {
        /** 200 + SSE：按 [chunks] 逐条下发 delta.content（OpenAI/DashScope wire 格式） */
        data class Stream(val chunks: List<String>, val chunkDelayMs: Long = 0L) : Script()

        /** 直接回一个 HTTP 状态码（401/500/…），body 走 Provider 错误映射 */
        data class HttpStatus(val code: Int, val body: String = "") : Script()

        /** 只下发一部分文本后就关闭连接（Provider 中途断流） */
        data class StreamThenAbort(val chunks: List<String>) : Script()

        /** 接受连接但永不应答（挂起请求，用于停止/覆盖场景） */
        object NoResponse : Script()

        /** 接受连接后立刻关闭（连接层失败） */
        object AbruptClose : Script()
    }

    private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))

    /** 生产 DeepSeekRepository / SecurePrefs 侧填的 baseUrl（含 /v1，路径由生产端解析） */
    val baseUrl: String = "http://127.0.0.1:${serverSocket.localPort}/v1"

    private val queue = LinkedBlockingQueue<Script>()
    private val counter = AtomicInteger(0)
    private val bodies = CopyOnWriteArrayList<String>()

    @Volatile
    var defaultScript: Script = Script.Stream(listOf(validReplyJson("默认回复")))

    @Volatile
    private var closed = false

    private val acceptThread = Thread({ acceptLoop() }, "FakeProviderServer")

    init {
        acceptThread.isDaemon = true
        acceptThread.start()
    }

    /** 已到达 Provider 的请求数（= Engine 实际发起的网络请求数） */
    val requestCount: Int get() = counter.get()

    /** 第 index 个请求的 JSON body（用于断言「新增的消息真的进了 prompt」） */
    fun requestBodyAt(index: Int): String = bodies.getOrElse(index) { "" }

    fun lastRequestBody(): String = bodies.lastOrNull() ?: ""

    /** 排队一个应答脚本；队列空时使用 [defaultScript] */
    fun enqueue(script: Script) {
        queue.add(script)
    }

    /** 清空计数器 / 队列 / body 记录，供同一实例复用 */
    fun reset(script: Script) {
        counter.set(0)
        bodies.clear()
        queue.clear()
        defaultScript = script
    }

    private fun acceptLoop() {
        while (!closed) {
            val socket = try {
                serverSocket.accept()
            } catch (e: IOException) {
                return // close() 后 accept 抛异常，线程正常退出
            }
            // 每条连接独立线程：NoResponse 挂起时不能阻塞后续请求
            Thread({ handle(socket) }, "FakeProviderConn").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            try {
                val input = s.getInputStream()
                val body = readRequest(input)
                counter.incrementAndGet()
                bodies.add(body)

                val script = queue.poll() ?: defaultScript
                val out = s.getOutputStream()
                when (script) {
                    is Script.Stream -> {
                        writeHead(out, "200 OK", "text/event-stream")
                        script.chunks.forEach { chunk ->
                            out.write(sseEvent(chunk))
                            out.flush()
                            if (script.chunkDelayMs > 0L) Thread.sleep(script.chunkDelayMs)
                        }
                        out.write("data: [DONE]\r\n\r\n".toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    is Script.StreamThenAbort -> {
                        writeHead(out, "200 OK", "text/event-stream")
                        script.chunks.forEach { chunk ->
                            out.write(sseEvent(chunk))
                            out.flush()
                        }
                        // 不发 [DONE]，直接关闭 → 生产侧读到 EOF（无 Complete 事件）
                    }
                    is Script.HttpStatus -> {
                        val reason = if (script.code == 401) "Unauthorized" else "Error"
                        writeHead(out, "${script.code} $reason", "application/json", body = script.body)
                        if (script.body.isNotEmpty()) {
                            out.write(script.body.toByteArray(Charsets.UTF_8))
                        }
                        out.flush()
                    }
                    Script.NoResponse -> {
                        // 故意不写任何字节：连接保持，直到客户端取消或超时
                        waitUntilClosed(s)
                    }
                    Script.AbruptClose -> {
                        s.close()
                    }
                }
            } catch (e: Exception) {
                // fake 内部异常不外抛（取消导致的 socket 关闭属正常路径）
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    private fun waitUntilClosed(socket: Socket) {
        while (!closed && !socket.isClosed) {
            try {
                Thread.sleep(20L)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    /** 读 headers + Content-Length body；返回 body 文本 */
    private fun readRequest(input: java.io.InputStream): String {
        val headerText = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) break
            headerText.append(b.toChar())
            if (prev == '\n'.code && headerText.endsWith("\r\n\r\n")) break
            prev = b
        }
        val length = Regex("(?i)content-length:\\s*(\\d+)")
            .find(headerText.toString())
            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(bytes, read, length - read)
            if (n == -1) break
            read += n
        }
        return String(bytes, 0, read, Charsets.UTF_8)
    }

    private fun writeHead(
        out: java.io.OutputStream,
        status: String,
        contentType: String,
        body: String = ""
    ) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $status\r\n")
        sb.append("Content-Type: $contentType; charset=utf-8\r\n")
        if (body.isNotEmpty()) {
            sb.append("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n")
        }
        sb.append("Connection: close\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun sseEvent(content: String): ByteArray {
        val payload = "{\"choices\":[{\"delta\":{\"content\":\"${escapeJson(content)}\"}}]}"
        return "data: $payload\r\n\r\n".toByteArray(Charsets.UTF_8)
    }

    override fun close() {
        closed = true
        runCatching { serverSocket.close() }
    }

    companion object {
        /** 生产 parseReplyResponse 要求至少一条非空 reply（R10 合同） */
        fun validReplyJson(recommended: String): String =
            "{\"response\":{\"recommended\":\"${escapeJson(recommended)}\"," +
                "\"bad_boy\":\"\",\"playful\":\"\",\"warm\":\"\"}," +
                "\"analysis\":{\"topic_status\":\"same\",\"topic_label\":\"fake\"}}"

        /** 流式增量解析用：把四风格 JSON 拆成多条 SSE 也合法的片段 */
        fun validReplyJsonChunks(recommended: String): List<String> {
            val full = validReplyJson(recommended)
            val mid = full.length / 2
            return listOf(full.substring(0, mid), full.substring(mid))
        }

        /** 不可解析的 Provider 文本（触发 ReplyFailureKind.Parse） */
        const val UNPARSEABLE_TEXT = "模型直接说了一句人话，没有返回任何 JSON 结构。"

        private fun escapeJson(raw: String): String =
            raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}
