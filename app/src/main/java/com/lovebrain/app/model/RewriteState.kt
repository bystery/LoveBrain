package com.lovebrain.app.model

/**
 * 单条改写状态。
 *
 * - [Loading]：改写进行中，原文保留可读，可取消
 * - [Done]：改写成功，新正文已替换，可撤销回到上一版本
 * - [Error]：改写失败/取消，保留原文，可重试
 */
sealed class RewriteState {
    /** 改写中，option 为用户选择的操作文案 */
    data class Loading(val option: String) : RewriteState()

    /** 改写成功，newReply 为新正文 */
    data class Done(val newReply: String) : RewriteState()

    /** 改写失败/取消，message 为错误原因 */
    data class Error(val message: String) : RewriteState()
}
