package com.lovebrain.app.domain

import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyDirection
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.SchemeIdentity
import com.lovebrain.app.model.SchemeSource

/**
 * 按方案身份读/改 [LoveBrainResponse] 里的单条正文。
 *
 * 结果的真实存储是两套：四条风格存在 [ReplySchemes] 的四个字段里（A/B/C/D 是**位置**映射），
 * 四条方向存在 `directions` 列表里（按 [ReplyDirection.index] 定位，null = 本轮不适合）。
 * "改写一条就回写整个旧 response"会把期间其他卡的变化抹掉，所以这里坚持
 * **只替换目标那条**，其余候选与 analysis 原样带走。
 *
 * 这段逻辑原先在 ViewModel 里逐字写了两遍（改写成功一处、撤销改写一处），
 * 第三、四处是只差 `.reply` 取法的方案查找。搬成一处后可离线单测。
 */
object ReplyPatch {

    /** 取某条方案卡当前的正文；身份解析不出来或该卡不在结果里时返回 null */
    fun textOf(response: LoveBrainResponse, identity: SchemeIdentity): String? {
        val schemes = when (identity.source) {
            SchemeSource.STYLE -> response.schemes
            SchemeSource.DIRECTION -> response.directionSchemes
        }
        return schemes.find { it.tag == identity.tag }?.reply
    }

    /**
     * 只替换目标卡的正文，返回新的 response。
     *
     * 方向卡标签不认识时**原样返回**而不是抛错——撤销/改写路径上"找不到目标"
     * 属于旧请求迟到的正常情形，把异常抛穿会把一次无害的迟到变成一次可见崩溃。
     */
    fun withText(response: LoveBrainResponse, identity: SchemeIdentity, newReply: String): LoveBrainResponse {
        return when (identity.source) {
            SchemeSource.STYLE -> {
                val updated = response.schemes.map { scheme ->
                    if (scheme.tag == identity.tag && scheme.source == identity.source) {
                        scheme.copy(reply = newReply)
                    } else {
                        scheme
                    }
                }
                response.copy(
                    response = ReplySchemes(
                        recommended = updated.getOrNull(0)?.reply ?: "",
                        badBoy = updated.getOrNull(1)?.reply ?: "",
                        playful = updated.getOrNull(2)?.reply ?: "",
                        warm = updated.getOrNull(3)?.reply ?: ""
                    )
                )
            }
            SchemeSource.DIRECTION -> {
                val dir = ReplyDirection.byTag(identity.tag) ?: return response
                val directions = response.directions.toMutableList()
                // 本轮该方向还没出现过：先补到足够长再写目标位
                while (directions.size <= dir.index) {
                    directions.add(null)
                }
                directions[dir.index] = newReply
                response.copy(directions = directions)
            }
        }
    }
}
