package com.lovebrain.app.core.testing

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.core.view.drawToBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 第6节第5条「颜色对比度」那一栏的**像素**尺：从真的渲染出来的一屏里取像素算 WCAG 比值。
 *
 * 为什么要有这把新的尺，而不是接着改 `ContrastRegressionTest`：那一格读 `Color.kt` 的源码文本，
 * 拿两个 token 的 HSL 三元组自己算——它**结构上看不见**「状态色 15% 透明底」这一档配方。
 * `LbStatusBadge` 的字色 vs 卡片底按 token 算是 4.64:1（绿），而屏幕上真正并排的那两色是
 * 「字 vs 被 15% 状态色染过的底」；把 `0.15f` 改成 `0.5f`，token 数学一个字都不红。
 * 语义树同样读不出底色配比（`LbStatusBadge` 那一族的 KDoc 自己就写着这句话），
 * 所以这一格只能走像素，否则它没有判别力。
 *
 * 一台尺必须先证明自己接上了电，所以这里刻意把「取像素」「算比值」拆成可以单独喂已知颜色的
 * 纯函数：测试可以拿几张已知对比度的纯色图进来核对读数（[contrast]/[compositeOver]），
 * 再拿去量生产组件。
 *
 * 取像素走的是仓库那 42 张截图基线**同一条**通道：`androidx.core.view.drawToBitmap`。
 * 这条不是猜的，是量出来的——`javap -c` 打在 `roborazzi-1.30.0.aar` 的
 * `com.github.takahirom.roborazzi.RoborazziKt` 上，全类只有一处像素入口，就是
 * `Method androidx/core/view/ViewKt.drawToBitmap`；而 `roborazzi-compose` 的
 * `captureRoboImage` 是先把 Compose 的绘制根交给自己造的 activity，再调这个入口。
 * 本仪器把同一条通道用在**测试规则自己那棵树上**（[capture] 收到的 [View] 由调用方从
 * `LocalView.current` 交进来——`androidx.compose.ui.platform.LocalView` 在
 * `ui/panel/ResizeGrip.kt` 已经在用），并且**刻意不落盘**：基线是 `app/src/test/roborazzi/`
 * 里人工签核过的那 42 张，这一格要的是比值，不是又长出一个没人认领的槽位。
 * ⚠ 没用 Compose 自己的 `SemanticsNodeInteraction.captureToImage()`：`roborazzi-compose`
 * 都没走它，仓库里也没有任何一处走过它——按「只用邻居文件里出现过的写法」这条，不在这里开新路。
 *
 * 一个诚实的边界（读数口径）：[inkAgainst] 取的是与底色**反差最大**的那一颗像素，
 * 也就是「字最实的那一点」。这是最宽松的取法——笔画没有实心核时读出来的比值只会更小。
 * 因此它只会**低估**危险、不会高估：它说过的组合，比按整条笔画平均更说得过去。
 * 想要更严的口径（按墨迹面积加权）在这里加一格，别退回 token 数学。
 */
object PixelContrastMeter {

    /** 一颗像素的 WCAG 相对亮度（忽略 alpha；alpha 混合由 [compositeOver] 负责） */
    fun luminance(argb: Int): Double {
        val r = linearized(((argb shr 16) and 0xFF) / 255.0)
        val g = linearized(((argb shr 8) and 0xFF) / 255.0)
        val b = linearized((argb and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** sRGB 单通道线性化（WCAG 2.1 定义，与 `ContrastRegressionTest` 同一个公式） */
    private fun linearized(channel: Double): Double =
        if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    /** WCAG 对比度 (L1+0.05)/(L2+0.05)，恒 ≥1——与 `ContrastRegressionTest` 同一口径 */
    fun contrast(fgArgb: Int, bgArgb: Int): Double {
        val a = luminance(fgArgb)
        val b = luminance(bgArgb)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /**
     * 按 source-over 把 [fg] 以 [alpha] 叠在 [bg] 上，得到屏幕上的那一色。
     *
     * 存在的理由：token 数学只能算两个**不透明**色，而「15% 状态色底」必须先在这里合成，
     * 才能知道字的对手色到底是什么。测试用它算出**期望底色**，再和 [measure] 从像素里读到的
     * 底色对账——这一步不许由被测组件自己完成。
     */
    fun compositeOver(fgArgb: Int, alpha: Float, bgArgb: Int): Int {
        require(alpha in 0f..1f) { "alpha 必须在 0..1，实到 $alpha" }
        fun mix(shift: Int): Int =
            ((fgArgb shr shift and 0xFF) * alpha + (bgArgb shr shift and 0xFF) * (1 - alpha)).toInt()
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** 一次读数的全部证据：底色、墨色、比值，以及这把尺到底扫了多少颗像素 */
    data class Reading(
        val backgroundArgb: Int,
        val inkArgb: Int,
        val ratio: Double,
        val scannedPixels: Int,
        val opaquePixels: Int,
        val backgroundPixels: Int
    ) {
        fun describe(): String =
            "底=#%06X 墨=#%06X 比值 %.2f:1（扫 %d 颗，不透明 %d 颗，底色铺了 %d 颗）"
                .format(
                    backgroundArgb and 0xFFFFFF, inkArgb and 0xFFFFFF, ratio,
                    scannedPixels, opaquePixels, backgroundPixels
                )
    }

    /**
     * 把语义树上这一颗节点**当前画出来的那一块**取成 Bitmap。
     *
     * [root] 是调用方从 `LocalView.current` 交进来的 Compose 宿主（见类 KDoc 那条通道取证）。
     * 坐标用 `boundsInRoot`——它就是相对这颗 Compose 绘制根的边界，所以裁片不需要再加窗口偏移；
     * 对不上时 [drawRegion] 直接抛，不静默回退去裁整屏。
     *
     * 取像素排在 [ComposeContentTestRule.runOnIdle] 里：`drawToBitmap` 动的是 View 自己的画布，
     * 必须在 UI 线程上；语义边界则在测试线程读（与 `SemanticsProbe` 同一口径）。
     */
    fun capture(rule: ComposeContentTestRule, root: View, node: SemanticsNodeInteraction): Bitmap {
        val rect = node.fetchSemanticsNode("取像素之前这一颗节点不在语义树上").boundsInRoot
        return rule.runOnIdle { drawRegion(drawRootOf(root), rect) }
    }

    private fun drawRegion(root: View, rect: Rect): Bitmap {
        val whole = root.drawToBitmap()
        val left = rect.left.toInt()
        val top = rect.top.toInt()
        val width = rect.width.toInt()
        val height = rect.height.toInt()
        check(width > 0 && height > 0) {
            "这一格量出 ${width}x$height 的边界——节点根本没画出来，不能拿空图去算比值"
        }
        check(left >= 0 && top >= 0 && left + width <= whole.width && top + height <= whole.height) {
            "节点边界 ($left,$top) 起的 ${width}x$height 落在绘制根截图 ${whole.width}x${whole.height} 之外——" +
                "boundsInRoot 与绘制根对不上，这台仪器的坐标尺接错了，不能靠裁别处顶数"
        }
        val shot = Bitmap.createBitmap(whole, left, top, width, height)
        whole.recycle()
        return shot
    }

    /**
     * 从 Compose 宿主往下找**真正在画的那一颗**（`AndroidComposeView`）。
     *
     * 按类名找而不是引用类型：`AndroidComposeView` 是 `@RestrictTo` 的内部类，
     * 引用它的类型会给 lint 预算新增一条 restricted-API 债（这个仓库的 lint 闸只许还债不许欠）。
     * 找不到就抛——拿宿主的父级或兄弟节点的像素顶数，量出来的比值说的是另一件事。
     */
    private fun drawRootOf(view: View): View =
        composeRootOrNull(view)
            ?: error(
                "从 LocalView（${view.javaClass.name}）往下没找到 AndroidComposeView——" +
                    "这台仪器没挂在 Compose 的绘制根上，不能拿别处的像素顶数"
            )

    private fun composeRootOrNull(view: View): View? {
        if (view.javaClass.name.endsWith("AndroidComposeView")) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                composeRootOrNull(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /**
     * 底色 = 不透明像素里的**众数**（面积最大那一色）。
     *
     * 不取左上角那颗像素：胶囊四角被 `LoveBrainShape.full` 裁掉，那里露的是卡片底，
     * 角落单点会读到错的对手色。一个像素都没有 ⇒ 抛，不静默返回「无色」。
     */
    fun backgroundOf(bitmap: Bitmap): Int = scan(bitmap).background

    /** 墨色 = 与底色反差最大的那颗不透明像素（见类 KDoc 那条「最宽松取法」的边界） */
    fun inkAgainst(bitmap: Bitmap, background: Int): Int {
        var ink = background
        var best = -1.0
        var opaque = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val argb = bitmap.getPixel(x, y)
                if ((argb ushr 24) != 0xFF) continue
                opaque++
                val c = contrast(argb, background)
                if (c > best) {
                    best = c
                    ink = argb
                }
            }
        }
        check(opaque > 0) { "整块读不到一颗不透明像素——这台仪器没接上渲染，不能让它空过" }
        return ink
    }

    /** 一次读数：扫一遍拿众数色与计数，再按口径挑墨色（两处共用 [scan]，口径不会漂） */
    fun measure(bitmap: Bitmap): Reading {
        val scan = scan(bitmap)
        val ink = inkAgainst(bitmap, scan.background)
        return Reading(
            backgroundArgb = scan.background,
            inkArgb = ink,
            ratio = contrast(ink, scan.background),
            scannedPixels = bitmap.width * bitmap.height,
            opaquePixels = scan.opaque,
            backgroundPixels = scan.backgroundPixels
        )
    }

    /** 一次扫描的全部读数：众数色 + 它铺了多少颗 + 不透明像素总数 */
    private data class Scan(val background: Int, val backgroundPixels: Int, val opaque: Int)

    private fun scan(bitmap: Bitmap): Scan {
        val counts = HashMap<Int, Int>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val argb = bitmap.getPixel(x, y)
                if ((argb ushr 24) != 0xFF) continue
                counts.merge(argb, 1) { a, b -> a + b }
            }
        }
        check(counts.isNotEmpty()) {
            "截图里一个不透明像素都没有（${bitmap.width}x${bitmap.height}）——底色读不出来，不能让这一格空过"
        }
        val mode = checkNotNull(counts.maxByOrNull { it.value }) {
            "不透明像素非空却挑不出众数色，这把尺自己坏了"
        }
        val total = bitmap.width * bitmap.height
        check(mode.value * 4 >= total) {
            "读到的底色只占 ${mode.value * 100 / total}%（${mode.value}/$total）——这块截图几乎全是空的，" +
                "众数色是从噪声里挑出来的，不能当判据"
        }
        return Scan(mode.key, mode.value, counts.values.sum())
    }
}
