package com.lovebrain.app.core.testing

import android.app.Application

/**
 * UI 仪表专用的空壳 Application。
 *
 * 为什么不用真的 [com.lovebrain.app.LoveBrainApp]：它在 `onCreate` 里 `startKoin`，
 * 而 Koin 的 `GlobalContext` 是 JVM 静态的——同一 Robolectric 沙箱里第二个用例
 * 再建一次 Application 就抛 `KoinAppAlreadyStartedException`（实测踩过，四格全红在
 * 装配阶段，跟被测控件毫无关系）。
 *
 * 更根本的一条：`PanelHeader` 这类控件本来就不该需要依赖容器才能组合出来。
 * 仪表用空壳 App 起得来，本身就是"这个控件没有偷偷依赖全局单例"的证据；
 * 哪天它起不来了，说明有人往纯控件里塞了 `get()`——那正是要红的地方。
 */
class UiProbeApplication : Application()
