package com.lovebrain.app.feature.provider

import com.lovebrain.app.model.ProviderTicket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "现在到底能不能发请求"这件事的唯一持有者（复核 第5节第2条 第 6 步、第2节第2条"面板不再本地计算"那半句）。
 *
 * 两件事必须同帧：**当前激活的工单**与**就绪位**。就绪不是"工单存在"——
 * 判据是三条件（工单在、模型非空、这一家子的 Key 非空），以前写死在悬浮窗里，
 * 于是"配置完回来还是灰的"这类问题有两处答案。现在只有 [refresh] 这一条路能改它。
 *
 * 为什么读配置要靠注入：`feature` 不许 import `data`（第5节第1条 包边界，`PackageDependencyTest` 在看着）。
 * 所以这里只声明"我要三样读数"，谁给的、从加密偏好还是别处取，本类不知道也不需要知道。
 */
class ProviderTicketStore(
    private val readTickets: () -> List<ProviderTicket>,
    private val readActiveTicketId: () -> String?,
    private val readApiKey: (ticketId: String) -> String?
) {

    private val _activeTicket = MutableStateFlow<ProviderTicket?>(null)
    private val _ready = MutableStateFlow(false)

    /** 当前激活的工单；null = 没有激活项，或激活项已被删掉 */
    val activeTicket: StateFlow<ProviderTicket?> = _activeTicket.asStateFlow()

    /**
     * 就绪三条件的合取：工单存在 && 模型非空 && Key 非空。
     * 面板只订阅这一个布尔，不再自己算——**不许**在别处再写一次这三个条件。
     */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** 同步读当前值（生成请求要的是"这一刻"的模型 id，不是订阅） */
    val activeTicketNow: ProviderTicket? get() = _activeTicket.value

    /**
     * 面板重新可见 / 配置改完之后调一次。
     *
     * 这里没有"读到一半失败就保留旧值"的分支：读不到工单与工单被删是同一个用户可见结果
     * （不能发请求），保留旧值反而会给出一个假的就绪位。
     */
    fun refresh() {
        val activeId = readActiveTicketId()
        val ticket = if (activeId.isNullOrEmpty()) null
        else readTickets().find { it.id == activeId }
        if (ticket == null) {
            _activeTicket.value = null
            _ready.value = false
            return
        }
        _activeTicket.value = ticket
        _ready.value = ticket.model.isNotBlank() && !readApiKey(ticket.id).isNullOrBlank()
    }
}
