package com.lovebrain.app.domain.port

import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.StreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * AI 网关端口（复核 §5「domain 只依赖 AiGateway / KnowledgeReadPort / KnowledgeWritePort / Clock」）。
 *
 * 签名只引用 model 里的类型。这一步之所以要先做，是因为
 * `ProviderRequestConfig` 与 `RawGenerationResult` 原先声明在 `DeepSeekRepository.kt` 里：
 * 端口签名一旦引用它们，**端口自己**就得 import data —— 抽象反而加深了跨层依赖。
 * 两个 DTO 已挪进 model（原样搬迁，字段与默认值都没动）。
 *
 * 注意 `snapshotProviderConfig` / `configForTicket` 在仓库上原本是 internal：
 * 端口要求实现成员可见，所以这两个的具体实现被放宽到 public。放宽的代价是
 * 少了一道"只有本模块能拿配置快照"的编译期约束；换来的是 Engine 可以只依赖端口，
 * 并且这里把身份校验的语义写进了文档 —— 见下面两个方法的注释。
 */
interface AiGateway {

    /**
     * 当前生效配置的**快照**（含 apiKey，只在发请求前用一次）。
     *
     * 生成链路应当按 [configForTicket] 取冻结身份，而不是反复回读这里；
     * 复核 §2.2 的「Engine 在冻结之后又回读实时配置」就是这条被当成正常路径用了。
     */
    fun snapshotProviderConfig(): ProviderRequestConfig?

    /** 按冻结的 ticketId 取配置；取不到返回 null，调用方必须失败而不是回退到当前 active */
    fun configForTicket(ticketId: String): ProviderRequestConfig?

    /** 流式生成。config 为 null 时实现自行冻结一份请求级快照 */
    fun generateStream(
        systemPrompt: String,
        userPrompt: String,
        thinkingOverride: Int? = null,
        thinkingShapeIndex: Int = 0,
        config: ProviderRequestConfig? = null
    ): Flow<StreamEvent>

    /** 非流式，只要正文（经验提取一类的辅助任务用） */
    suspend fun generateRaw(systemPrompt: String, userPrompt: String): String

    /** 非流式，带 finish_reason 与错误：调用方要能区分"空响应"和"被截断/被过滤" */
    suspend fun generateRawWithMetadata(systemPrompt: String, userPrompt: String): RawGenerationResult

    /** 把正文解析成结构化回复；解析不出来必须抛，不能被吞成"零方案" */
    fun parseReplyResponse(content: String): LoveBrainResponse
}
