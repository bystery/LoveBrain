package com.lovebrain.app.data

import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.ProviderTicket

/**
 * 「一张工单怎样变成一个请求的身份」的唯一所有者。
 *
 * 变化理由只有这一条：凭据与模型来自哪里、什么样的工单算配置完整、
 * 用户填的地址什么形态才允许发出去。请求怎么发、回来怎么算钱都不在这里。
 *
 * 关键约束（原样搬自仓库，逐字未改语义）：开头一次性读取 `securePrefs.activeTicketId`，
 * 后续全部围绕这个固定 ID 取值；ticket 和 apiKey 必须来自同一个 ticketId，
 * 禁止分两次读取 activeTicketId。
 */
internal class ProviderConfigResolver(private val securePrefs: SecurePrefs) {

    /** 所有工单列表 */
    fun allTickets(): List<ProviderTicket> = securePrefs.getWorkerTickets()

    /** 激活工单 */
    fun activeTicket(): ProviderTicket? {
        val ticketId = securePrefs.activeTicketId ?: return null
        return allTickets().find { it.id == ticketId }
    }

    /** 激活工单的 API Key */
    fun activeApiKey(): String? {
        val ticketId = securePrefs.activeTicketId ?: return null
        return securePrefs.getWorkerApiKey(ticketId)
    }

    /** 激活工单绑定的模型名（一工单 = 一模型） */
    fun activeModel(): String? = activeTicket()?.model

    /**
     * 设置激活工单
     * @param ticketId 工单 ID
     */
    fun activateTicket(ticketId: String) {
        securePrefs.activeTicketId = ticketId
    }

    /**
     * 标准化 Base URL（脏数据拦截；多模型批主人原话：不补全任何路径——填什么用什么）
     */
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        // 脏数据拦截
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))
            throw IllegalArgumentException("地址必须以 http:// 或 https:// 开头")
        if (trimmed.contains("sk-") || trimmed.contains(" "))
            throw IllegalArgumentException("检测到 API Key 误填入地址栏，请检查")
        // http:// 仅放行 loopback 主机，对外强制 https（扼制点：生成/测试连接两出口唯一必经）
        HttpsTrustGuard.enforce(trimmed)
        return trimmed
    }

    /**
     * 一次性解析当前激活工单为不可变请求配置快照。
     * 返回 null = 配置不完整（调用方负责发 ProviderMissing）。
     * 返回后整次请求只使用此快照，不再读工单表与 Key。
     * [normalizeBaseUrl]（含 HttpsTrustGuard）在此完成，地址不合法时抛 IllegalArgumentException。
     */
    fun resolveActive(): ProviderRequestConfig? {
        val ticketId = securePrefs.activeTicketId ?: return null
        return resolveFor(ticketId)
    }

    /**
     * 按指定 ticketId 解析请求配置。
     *
     * 生成请求在 GenerationInput 里冻结了 ticketId，Engine 必须按那个 ticketId 取配置，
     * 不能在准备阶段之后再回读 activeTicketId——否则用户中途切工单，
     * 本轮就会用一套从未被冻结、也从未被校验过的配置发出去。
     */
    fun resolveFor(ticketId: String): ProviderRequestConfig? {
        if (ticketId.isBlank()) return null

        val ticket = allTickets().firstOrNull { it.id == ticketId } ?: return null
        if (ticket.id.isBlank() || ticket.baseUrl.isBlank() || ticket.model.isBlank()) return null

        val apiKey = securePrefs.getWorkerApiKey(ticketId)?.takeIf { it.isNotBlank() } ?: return null

        // normalizeBaseUrl 包含脏数据拦截 + HttpsTrustGuard.enforce
        val baseUrl = normalizeBaseUrl(ticket.baseUrl)
        val thinkingMode = ticket.thinkingMode ?: securePrefs.thinkingMode
        return ProviderRequestConfig(
            ticketId = ticketId,
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = ticket.model,
            thinkingMode = thinkingMode
        )
    }
}

/**
 * 网络信任闸门：http:// 仅放行 loopback 主机，其余主机强制 https://，
 * 避免 Bearer Key 与聊天上下文被明文嗅探。
 * 纯判定、不依赖 Android 运行时，由 HttpsTrustGuardTest 直测。
 *
 * 只处理以 http:// 开头的地址；其余形态不在本闸门职责
 * （由 [ProviderConfigResolver.normalizeBaseUrl] 既有脏数据拦截负责）。
 */
internal object HttpsTrustGuard {

    /** loopback 主机白名单（小写化比对）；保本地 LLM（Ollama/LM Studio 默认 http://127.0.0.1）正当场景 */
    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "::1", "localhost")

    /**
     * 违规抛 IllegalArgumentException（固定文案，不拼用户输入）；合规则直接返回。
     * host 用 java.net.URI 解析（IPv6 字面量的方括号显式剥离后比对）；解析失败 = 按违规拦截（宁可拒发不可放行）。
     */
    fun enforce(rawBaseUrl: String) {
        val trimmed = rawBaseUrl.trim().trimEnd('/')
        if (!trimmed.startsWith("http://")) return
        var host = runCatching { java.net.URI(trimmed).host }.getOrNull()?.lowercase()
            ?: throw IllegalArgumentException("地址格式不正确，请检查后重试")
        // JDK URI.getHost() 对 IPv6 字面量保留方括号（[::1]），显式剥离后比对（::1 在豁免名单）
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length - 1)
        }
        if (host !in LOOPBACK_HOSTS) {
            throw IllegalArgumentException(
                "http:// 地址仅限本机（127.0.0.1/::1/localhost）；对外地址请使用 https://，避免 API Key 明文传输"
            )
        }
    }
}
