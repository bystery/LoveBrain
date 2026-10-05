package com.lovebrain.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.lovebrain.app.core.designsystem.LbFieldInput

/**
 * 通用表单的单行紧凑输入框——现在是 [LbFieldInput] 的**委托壳**（基线 v1 §3.7；
 * D1 §⑥ 改动点 #8"上提 core/designsystem"落地）。
 *
 * 为什么壳还留着而不是全仓改名：这一颗的调用点在册的不止一处（供应商弹窗、知识库
 * 问卷向导、消息捕获搜索框……逐个回查名单见
 * `evidence/2026-10-05-feedback/impl-M2a-form-controls.md`），名单上的每一处**行为**
 * 必须逐字不变；换名是另一轮的全仓工程，不在这一轮顺手做掉。
 *
 * 原样保留的行为（都在 [LbFieldInput] 里有主人）：
 *  - 36dp 可见框 + 外层 48dp 透明热区，点框内空档获焦、不声明点击语义；
 *  - placeholder 转成可编辑节点的读屏名（`contentDescription = placeholder`）；
 *  - `passwordVisible` 的密码遮蔽；
 *  - `trailingAction` 走具名尾部槽（那颗 40dp 字面量本轮上提为
 *    `AppDimens.INPUT_TRAILING_SLOT_DP`，数值一字未改）；
 *  - `focusRequester` 单焦点入口的转交。
 *
 * 本轮**有意**变化的是外观状态表：五态（静息/聚焦/已填/错误/禁用）由
 * [LbFieldInput] 接管，聚焦终于可见（描边转 `Primary` 1.5dp）。行为清单里没有任何
 * 一条被这一族改动放宽；新页面请直接用 `LbFieldInput` / `LbFormField`，别再长第三层壳。
 */
@Composable
fun CompactInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    passwordVisible: Boolean = true,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    LbFieldInput(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        passwordVisible = passwordVisible,
        trailingAction = trailingAction,
        focusRequester = focusRequester
    )
}
