package com.lovebrain.app.feature.notice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮窗上那三条"会自己消失的话"的唯一持有者（复核 §5.2 第 6 步：VM 不再持有状态）。
 *
 * 三条通道分别是：知识库后台操作的回执（[Channel.Knowledge]，如"经验提取完成"）、
 * 面板级警告（[Channel.Warning]，如"未配置模型"/"这轮没记入知识库"）、
 * 五维向量重估摘要（[Channel.Vector]）。它们的**变化理由是同一个**——"这条提示该不该还在屏幕上"，
 * 所以放在一起；它们的内容由谁来定是另一件事（那是各业务节点的事，不在这里）。
 *
 * 为什么值得单独一格：搬之前在 VM 里有 10 处直接 `_kbNotice.value = …` 的写点，
 * 而"切库要把这一族清空"只写在其中一处（`refreshKnowledgeBases`）。
 * 漏掉的那几类不会报错，只会让上一轮的提示赖在下一块屏幕上。
 * 现在"整族一起清"是 [dismissVolatileNotices] 一个动作，调用点只说意图。
 */
class NoticeBoard {

    /** 三条通道。用枚举而不是三个字段，是为了让"整族清"这件事有穷尽的定义。 */
    enum class Channel { Knowledge, Warning, Vector }

    private val _knowledge = MutableStateFlow<String?>(null)
    private val _warning = MutableStateFlow<String?>(null)
    private val _vector = MutableStateFlow<String?>(null)

    /** 知识库后台操作回执（悬浮窗内短暂展示） */
    val knowledge: StateFlow<String?> = _knowledge.asStateFlow()

    /** 面板级临时警告（未配置引导 / 未记入提示） */
    val warning: StateFlow<String?> = _warning.asStateFlow()

    /** 五维向量最近一次重估的变化摘要 */
    val vector: StateFlow<String?> = _vector.asStateFlow()

    fun show(channel: Channel, message: String) = write(channel, message)

    fun dismiss(channel: Channel) = write(channel, null)

    /**
     * 换知识库时该清的那三条。
     *
     * 不含 [Channel.Warning]：面板级警告（例如"未配置模型"）说的是**这台设备**的配置状态，
     * 与切到哪块知识库无关，切库把它抹掉反而会让人以为配置好了。
     * 这条区分是搬之前 `refreshKnowledgeBases` 的实际行为，写下来是为了不让下一个人"顺手全清"。
     */
    fun dismissVolatileNotices() {
        _knowledge.value = null
        _vector.value = null
    }

    private fun write(channel: Channel, message: String?) {
        when (channel) {
            Channel.Knowledge -> _knowledge.value = message
            Channel.Warning -> _warning.value = message
            Channel.Vector -> _vector.value = message
        }
    }
}
