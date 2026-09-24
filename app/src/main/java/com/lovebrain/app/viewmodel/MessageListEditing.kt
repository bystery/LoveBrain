package com.lovebrain.app.viewmodel

import com.lovebrain.app.model.ChatMessage

/**
 * "第几条正在编辑"这条索引，在一次列表改动之后该落到哪——全仓只这一处判据。
 *
 * 判据是**按身份重算**，不是位移：编辑中的那条消息改完之后在不在列表里、在第几位，
 * 由它自己的 `id` 决定。原先这个规则写在三处，其中两处是算术位移：
 *  - `removeMessageById`：删掉前一位就 -1、删的就是它则清编辑态；
 *  - `reorderMessages`：一套带边界等号的三段推理（那段注释长到要举两个反例才说得清）；
 *  - `commitReplyRound`：按身份重算（三处里唯一不会随删除条数变化的写法）。
 * 三处今天是一致的（穷举矩阵已证，见 `MessageEditingIndexInvariantTest`），
 * 但一致是靠"三个人都算对了"维持的——只要有人改其中一处而没想起另两处，
 * 用户拿到的就是"我在改第 2 条，实际改到第 3 条"，而且不会有任何报错。
 *
 * 现在三处共用这一个函数：改列表之后想知道编辑位去哪，只有这一条路。
 */
internal object MessageListEditing {

    /**
     * @param before 改动前的列表（`editing` 是它在其中的下标）
     * @param after  改动后的列表
     * @return 新的编辑位；原本没有编辑位、或那条消息已被删除时给 -1
     */
    fun reindex(before: List<ChatMessage>, after: List<ChatMessage>, editing: Int): Int {
        if (editing < 0) return -1
        val target = before.getOrNull(editing) ?: return -1
        return after.indexOfFirst { it.id == target.id }
    }
}
