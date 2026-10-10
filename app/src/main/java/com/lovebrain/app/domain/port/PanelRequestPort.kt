package com.lovebrain.app.domain.port

/**
 * 「请求打开悬浮面板」的窄端口（mode：0=回复、1=谈心）。
 *
 * 为什么单开这一颗：`SetupActivity`（首页功能卡片 → 面板）以前直接 import data 层的
 * `EventBus` 发请求——UI 认数据层具体类型，是 PackageDependencyTest 基线里 ui 格
 * 登记的那条债。还法照 `SecurePrefs` implements `SettingsStorePort` 的同一张处方：
 * data 侧的唯一主人自己 implements 端口，页面只认端口；容器里两个类型解析到
 * **同一个**单例（di/AppModule.kt），事件没有第二条通道。
 *
 * 只开 [requestPanel] 一个成员：订阅与消费（`panelRequest` / `consumePanelRequest`）
 * 是 FloatingService 那一侧的事，页面用不到，就不进这颗端口
 * （ISP 与 SettingsStorePort 那格同一条规矩：成员跟着调用方走，不照实现抄）。
 */
interface PanelRequestPort {
    /** 请求 FloatingService 打开面板；[mode] 取值域与 EventBus.PanelRequest 一致：0=回复、1=谈心 */
    fun requestPanel(mode: Int)
}
