package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbRowLabel
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.BorderLight
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint
import kotlin.math.roundToInt

/** 那颗滑杆的稳定锚点：文案会跟着数值变，tag 不会 */
internal const val SETTINGS_OPACITY_SLIDER_TAG = "settings-opacity-slider"

/** 透明度那一行的行根锚点（量具按它找那一行，见锚点合同） */
internal const val SETTINGS_OPACITY_ROW_TAG = "settings-row-opacity"

/**
 * 这一行滑杆**自己**那三个形状数（体例照同目录 [SettingsRowDimens] 与 `SettingsHeaderDimens`：
 * 文件私有、具名、带一句为什么）。
 *
 * 为什么不上提到 [AppDimens]（那是全站那本尺寸账）：
 * · 这三个数只在"`input[type=range]` 画成原型那根细轨"这一件事上成立——全站今天只有这一颗滑杆，
 *   上提就是宣布"以后任何滑杆都必须长这样"，而那颗数并没有任何别的调用位来背书；
 *   `AppDimens` 里每一档矮尺寸都同时被设计系统里的某颗组件读着（见 `UiLayerDependencyContractTest`
 *   的 `the compact tiers this round names are declared once and read somewhere`：core 里没人读的
 *   档当场红），这一族没有第二处读者，放进 core 只会造出一颗死档；
 * · 原型给的数（4 / 15 / 125）与本仓既有 token 不同数，不属于任何现成档位；
 * · 数值的**颜色**一侧仍不新造：轨的已走段用 [Primary]、未走段用 [BorderLight]（原型 `#d9dce5`）、
 *   白圈就是白，全部指回 `core/designsystem` 那颗，没有第四套尺。
 *
 * 原型那一格逐字（`input[type=range]` 与其 thumb 伪元素）：
 * `height:4px` + `border-radius:50px`（⇒ 两端圆头的细轨）、`max-width:125px`、
 * thumb `15px` 实心 `--accent` + `2px solid white` 圈、**没有任何刻度点**
 * （`appearance:none` 之后既没写 `::-webkit-slider-runnable-track` 的刻度也没写 `::-moz-range-progress` 之外的点）。
 * 原型那条 1px `#aab8e5` 外圈描边**不采纳**：那是 `box-shadow` 出来的第三颗色，仓库里没有同名的那颗，
 * 而这一族的规矩是不许为"看起来像"去新造 token。
 */
private object SettingsSliderDimens {
    /** 轨道粗细（dp）：原型的 4px；Material 默认那根粗轨就是这个数没对上 */
    const val TRACK_HEIGHT_DP = 4

    /** 圆球直径（dp）：原型的 15px，含那一圈 2dp 白边 ⇒ 球心到外沿 7.5dp */
    const val THUMB_SIZE_DP = 15

    /** 白圈宽度（dp）：原型 thumb 的 `border:2px solid white` */
    const val THUMB_RING_WIDTH_DP = 2

    /** 整根滑杆的最大宽（dp）：原型 `max-width:125px`（占满整行是10-12 那一版被判"根本不一样"的另一半） */
    const val MAX_WIDTH_DP = 125

    /** 尾部 `%` 读数的最小宽（dp）：原型 `.value{min-width:27px}`，读数换档时那一列不许左右跳 */
    const val READOUT_MIN_WIDTH_DP = 27
}

/**
 * 设置页里那一颗**透明度滑杆**：label + 细轨滑杆 + 百分比读数，没有别的。
 *
 * 没有说明段落、没有预览条、没有超时档位、没有供应商与捕获范围——那些能力各自住在首页那四个
 * 入口的页面里，这一格只交这一件事。
 *
 * **两把轴分开说，这一格最容易读反的就是这里**：
 * - 屏上画出来的那个数是**真透明度**：`0%` = 完全不透明，`40%` = 这一族允许的最透；
 * - 盘上存的那个数、以及两条回调交回宿主的数，仍是**不透明度**：`100` = 完全不透明、`60` = 最透，
 *   Service 侧与落盘格式一寸没动。
 * 两侧换算只走 [PanelBackdropOpacity] 上已有的那一对镜像口 [PanelBackdropOpacity.transparencyOf]
 * 与 [PanelBackdropOpacity.fromTransparencyPercent]，这一格里**不许**手算 `100 - x`——
 * 手算过的那一版直接把宿主交来的不透明度标成"透明度"印在屏上，于是"100% 透明度"其实是不透明。
 * 区间、步长、默认值、非法值回落全都取自同一把尺 [PanelBackdropOpacity]，这一页一个数都不重抄。
 *
 * 拖动手感跟着**显示**那把轴走：往右拖 = 透明度读数变大 = 面板更透 = 落盘那个不透明度变小。
 *
 * **形状走原型，不走 Material 默认那一档**（这一版的靶心）：这一颗用的是 `Slider` 那颗带自定义
 * `thumb`/`track` 的重载（material3 1.2.1 里实扫存在，标着 `@ExperimentalMaterial3Api`，
 * 所以要下面的 `@OptIn`）。同版本里 `SliderDefaults.Track` / `SliderDefaults.Thumb` **没有**
 * `trackHeight` 那一档参数（也 javap 过那颗 jar），细轨与小圆球拿不到，只能在下面那两个 lambda 里
 * 自己画一次——**只画这一处**，不给这一族再造第二颗滑杆组件。
 * 自定义的只是画：语义仍由 `Slider` 自己那颗 `sliderSemantics` 交（进度区间 + 当前值），
 * 这里不再叠一层 semantics，也不改 `Role`。
 *
 * **刻度吸附改成"拖动连续、松手吸附"**：以前把 `steps` 交给 Slider，等于让 Material 在拖动过程中
 * 就把值量化到格上，屏上的数一路按 5 跳；现在 `steps = 0`（轨道上也就没有刻度点可画），
 * 吸附发生在 [Slider] 的 `onValueChangeFinished` 那一拍，而且只走那一对镜像口——
 * 显示轴上的原始读数 → [PanelBackdropOpacity.fromTransparencyPercent]（`snapPercent` 就在它里面）
 * → 落盘轴上那一档，屏上随后念的那一句正是这一档镜像回来的数，两者同一拍。
 *
 * 生命周期：拖动期间每帧只交预览值（不落盘），松手那一次才交给 commit 写盘。
 * ⚠ commit 之后**不许**动窗口的 `ComposeView.alpha`——服务侧在 present/hide 路径上会把它
 * 复位成 `1f`，那条通道属于显示/隐藏动画，与用户这里的浓度是两件事。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsOpacityEntry(
    opacityPercent: Int,
    onOpacityPreview: (Int) -> Unit,
    onOpacityCommit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 显示轴的两端由那一把尺现算，不抄字面量：透明度 0% 就是完全不透明那一头，
    // 透明度 40% 就是这一族允许的最透那一头（= 不透明度下限 60）。
    val transparentEnd = PanelBackdropOpacity.transparencyOf(PanelBackdropOpacity.MIN_PERCENT)
    val opaqueEnd = PanelBackdropOpacity.transparencyOf(PanelBackdropOpacity.MAX_PERCENT)
    // 拖动过程中的临时读数（这一颗住在**显示轴**上，而且是**没吸附**的那一颗）：调用方那份状态要等
    // onOpacityCommit 才落定，若这里不接管这一段，松手前那半秒读数会停在旧值上——滑杆与数字对不上。
    // null = 现在没人按着，读数一律跟参数走（这一格自己**不**保存透明度）。
    var draggingTransparency by remember { mutableStateOf<Int?>(null) }
    val shownTransparency = (draggingTransparency ?: PanelBackdropOpacity.transparencyOf(opacityPercent))
        .coerceIn(opaqueEnd, transparentEnd)
    val percentReadout = stringResource(R.string.settings_opacity_percent, shownTransparency)
    // 滑杆那颗可点节点自己没有文案（Material 3 给它的是进度语义），读屏就只剩"滑块"两个字。
    val sliderName = stringResource(R.string.settings_opacity_label) + " " + percentReadout
    val displayValueRange = opaqueEnd.toFloat()..transparentEnd.toFloat()

    // 行自己**不画外框**：整组一张卡由页面那一侧拥有，横向内缩也只有一个主人（组外框），
    // 所以这里只取族里那颗行骨架（铺满宽度 + 版式下限），标签左边缘与尾部读数的右边缘就落在
    // 组内那一条线上。那一行的**版式高度**由滑杆自己那条链上的 48dp 触摸下限顶出来（热区轴给的，
    // 不是这里再叠一层 padding 撑的——可见尺寸 / 热区 / 版式三轴分离）。
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.settingsEntryRow().testTag(SETTINGS_OPACITY_ROW_TAG)
    ) {
        // 行标签：整页唯一一种（`labelLarge` + `TextPrimary`，原型 `.lbl{12px/540/var(--text)}`）。
        // 以前这一格是 `labelMedium` 的灰字，与助手那行的 `titleMedium` 粗体并排，一页三种字阶。
        // 标签**不吃权重**：原型 `.lbl{white-space:nowrap}` 是自然宽，剩下那一段归 `.action-area`
        // （`flex:1;justify-content:flex-end`）。这里用同一写法——中间那颗 Spacer 吃权重，
        // 于是滑杆与读数贴右缘，而标签那颗节点报回来的就是它自己的自然宽。
        // ⚠ 别改成 `LbRowLabel(Modifier.weight(1f))`：`fill = true` 会把标签节点撑满剩余槽，
        //   整页"行标签同一档字"那把尺量的就是各标签的自然宽，撑宽的那一颗当场读成另一种字。
        LbRowLabel(text = stringResource(R.string.settings_opacity_label))
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.sm)) // 原型 `.item{gap:5px}` / `.action-area{gap:6px}`，取矮的那一档
        Slider(
            value = shownTransparency.toFloat(),
            onValueChange = { next ->
                // 原型 `input[type=range]{min:0;max:40;step:5}` 里那颗 `step`：**拖动途中就一格一格吸**，
                // 不是等松手才吸（上一版只在松手那一拍吸，手感是连续的、与屏上那句整数对不上）。
                // 吸附不在这格里做算术：原始读数先交给落盘那把尺（`fromTransparencyPercent` 内部走
                // `snapPercent`），再由同一把尺把合法档镜像回显示轴——两趟都只过那对现成的口，
                // 于是"屏上念的数"、"交回宿主的数"、"盘上存下的数"是同一个来源，没有第三份刻度。
                val stepped = PanelBackdropOpacity.fromTransparencyPercent(next.roundToInt())
                draggingTransparency = PanelBackdropOpacity.transparencyOf(stepped)
                // 交出去的那一颗仍是落盘轴上的数（宿主与服务不知道显示层换过一次轴）
                onOpacityPreview(stepped)
            },
            // 松手那一拍才吸附：吸附只走那把尺已有的镜像口，不在这格重抄一遍刻度。
            // commit 交出去的那一档与屏上接下来念的那一句是同一个数——宿主在这一拍把同一颗值写回
            // `backdropPercent`（`LoveBrainPanelScreen` 的 commit 那一段），于是本地读数让位给参数时
            // 不会闪回旧值，也不需要这一格自己留第二份真值。
            onValueChangeFinished = {
                val raw = draggingTransparency
                if (raw != null) {
                    val snappedOpacity = PanelBackdropOpacity.fromTransparencyPercent(raw)
                    draggingTransparency = null
                    onOpacityCommit(snappedOpacity)
                }
            },
            // 滑杆走的是**显示那把轴**：左端 = 完全不透明（0%），右端 = 最透。
            valueRange = displayValueRange,
            // 0 = 拖动过程连续（吸附在上面那一拍做）。这一颗同时让 Material 不在轨道上摆刻度点；
            // 轨道本来也由下面 `track` 自己画，两件事同向。
            steps = 0,
            // 圆球：15dp 实心 [Primary] + 2dp 白圈。半径一律按 dp 现算、不按 `size.minDimension` 推——
            // Slider 会给这一格垫上 Material 自己的最小把手尺寸（比 15dp 大），跟着盒子画就会画出一颗
            // 20dp 的球；画在盒子正中、尺寸自己说，球才真的是原型那一颗。
            thumb = { _ ->
                Canvas(modifier = Modifier.size(SettingsSliderDimens.THUMB_SIZE_DP.dp)) {
                    // 半径在 DrawScope 里现算（那颗 `Dp.toPx()` 要 Density，只有画的那一层有）
                    val ring = SettingsSliderDimens.THUMB_RING_WIDTH_DP.dp.toPx()
                    val outer = SettingsSliderDimens.THUMB_SIZE_DP.dp.toPx() / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(color = Color.White, radius = outer, center = center)
                    drawCircle(color = Primary, radius = outer - ring, center = center)
                }
            },
            // 细轨：4dp、两端圆头、已走段 [Primary]、未走段 [BorderLight]，**不画刻度点**。
            // 已走多长读的是 Slider 自己那份 `value` 与 `valueRange`（都是公开读数），
            // 于是这条填充与 Slider 摆出去的球永远同一拍——不必在这格再算一次 fraction。
            track = { state ->
                val range = state.valueRange
                val span = range.endInclusive - range.start
                val filled = if (span <= 0f) {
                    0f
                } else {
                    ((state.value - range.start) / span).coerceIn(0f, 1f)
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SettingsSliderDimens.TRACK_HEIGHT_DP.dp)
                ) {
                    val centerY = size.height / 2f
                    val stroke = SettingsSliderDimens.TRACK_HEIGHT_DP.dp.toPx()
                    drawLine(
                        color = BorderLight,
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    if (filled > 0f) {
                        drawLine(
                            color = Primary,
                            start = Offset(0f, centerY),
                            end = Offset(size.width * filled, centerY),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                    }
                }
            },
            // 热区下限仍垫在滑杆**自己**那条链上（不是外面套一层大盒子）：带语义的那颗节点如果
            // 还是细轨那 4dp 高，手指信的就是 4dp。
            // ⚠ 宽度那两条的顺序是判据，不是排版口味：`weight(1f)` 会把来向约束钉成 min=max=剩余宽，
            // 再排 `widthIn(max=…)` 就**收不回来**（第一版就是这么写的，量到 221dp，比原型那 125 宽一倍）。
            // 先 `widthIn` 封顶、再 `fillMaxWidth` 吃满这一档上限，才是原型 `input[type=range]{width:100%;max-width:125px}`
            // 那一句；剩下的宽度归上面那颗吃了 weight 的标签，于是滑杆与读数贴右缘。
            modifier = Modifier
                .widthIn(max = SettingsSliderDimens.MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                .semantics { contentDescription = sliderName }
                .testTag(SETTINGS_OPACITY_SLIDER_TAG)
        )
        Spacer(Modifier.width(Spacing.sm)) // 原型 `.item{gap:5px}` / `.action-area{gap:6px}`，取矮的那一档
        // 尾部读数：原型 `.value{11px; color:var(--light); min-width:27px; text-align:right}`
        // ⇒ `labelMedium`（11sp）+ [TextHint] + 最小宽 27dp + 右对齐。
        // 原型那句 `font-variant-numeric:tabular-nums` **不采纳**：Compose 侧要拿它就得给
        // `AppTypography.labelMedium` 加 `fontFeatureSettings`，那是全站 token，这一族的规矩是不许动。
        Text(
            text = percentReadout,
            style = AppTypography.labelMedium,
            color = TextHint,
            textAlign = TextAlign.Right,
            maxLines = 1,
            modifier = Modifier.widthIn(min = SettingsSliderDimens.READOUT_MIN_WIDTH_DP.dp)
        )
    }
}
