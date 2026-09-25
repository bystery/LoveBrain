package com.lovebrain.app.core.designsystem

import androidx.compose.ui.graphics.Color

/** 自动化锚点：点画没画，语义树上要看得见（它自己没有可读名字，靠 tag 定位） */
object LbRowTags {
    const val DOT = "lb_row_dot"
}

/**
 * §6.1 表里 `LbSettingRow` 的"状态"槽：**点用什么颜色只在这里有一份**。
 *
 * 为什么不复用 [LbStatus]：那是"军师"的词汇表（运行中 / 已隐藏 / 未启动 / 未授权 / 窗口未出现），
 * 而设置行要说的是"这一项就绪没有"（模型供应商、消息捕获）。把两件事并一张表，
 * 读屏就会在供应商行念出"运行中"——那是把一枚徽标说成另一件事实。
 *
 * 文字仍然由调用方交进来（`statusText`），因为词是各域自己的：捕获行说"开/关"，
 * 供应商行**只要一颗点**。这张表管的是"就绪 ↔ 什么颜色"这一对，
 * 旧写法是调用方各写一遍 `if (providerReady) Primary else Neutral300`，
 * 于是"Ready 配灰"这种组合谁都能递进来。
 */
enum class LbRowState(val color: Color) {
    Ready(Primary),
    NotReady(Neutral300)
}
