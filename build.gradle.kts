// Top-level build file
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    // §6.5 :538「screenshot baseline 变更必须人工 review，不允许自动覆盖 baseline 后直接绿」
    // + :610 那张参考表点名的 roborazzi / paparazzi **二选一**（这里选 roborazzi，paparazzi 不引入）。
    // 钉 1.30.0：往上的 1.7x 用 Kotlin 2.0.21 编，本仓库还是 Kotlin 1.9.24。
    id("io.github.takahirom.roborazzi") version "1.30.0" apply false
}
