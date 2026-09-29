package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R

/**
 * §6.1 表里的 `LbTopBar`：标题、副标题、**返回（或关于）等单一尾部动作**。
 *
 * ## 它现在也是页头唯一的写法
 *
 * 收口之前全 App 的页头是四式并存（本机实扫，数值见账本 §37）：
 * `LbTopBar`（只有首页）、`ScreenHeader`（`ScreenPage` 那一族 + 知识库编辑）、
 * 手写"箭头字形 + 标题"的 `Row`（关于 / 使用概览 / 供应商）、
 * `SurfaceCard` 底的手写栏（反馈案例）。
 *
 * 四式里最要紧的差别不是好不好看，是**读屏念不念得出来**，而这一点是量出来的：
 * 手写那三颗的 `contentDescription` 实测是**空串**——树里那颗节点的名字就是
 * `Text("←")` 那个箭头字形，TalkBack 对着字形念出什么由不得我们；
 * 而 `ScreenHeader` 那一族挂的是**硬编码中文** `"返回"`，
 * 在英文环境下资源已经翻成 "Back" 了，它还是念中文。
 * ⇒ 名字现在只从 `R.string.common_back` 来，中英各一份。
 *
 * ## 标题字号为什么是一个二选一的枚举
 *
 * §6.1 末句拦的是"只在一个页面看起来不一样的按钮/卡片"。首页那一行是**产品身份**
 * （"LoveBrain" + 一句价值说明），二级页那一行是**页名**，这是两种语义，
 * 所以给两个有名字的档，而不是留一个 `style: TextStyle` 参数让每页自选——
 * 那种参数等于把"另造标题样式"合法化。
 *
 * [showsDivider] 也是同一处判断：带分割线的是 `ScreenPage` 那一族的规格，
 * 首页没有。留着这个开关是为了搬的时候**不改版式**，不是为了鼓励分叉。
 */
enum class LbTopBarLevel {
    /** 首页那种：产品身份 + 副标题，字号走 `headlineLarge` */
    Identity,

    /** 二级页那种：页名，字号走 `titleLarge` */
    Page
}

private object LbTopBarDimens {
    /**
     * 页头行高（§6.5 :531 的 48dp 下限）。
     * 写成下限的别名：这一档**是**因为下限才从 44 抬到 48 的，不是版式自选。
     *
     * 返回那颗的热区边长以前也读这一个数；现在它由 [LbTextAction] 的图标档给，
     * 同一个数、同一个所有者，这一处只管页头那一行本身。
     */
    const val ROW_HEIGHT_DP = AppDimens.TOUCH_TARGET_MIN_DP

    /** 返回箭头与标题之间 */
    const val TITLE_GAP_DP = 2
}

@Composable
fun LbTopBar(
    title: String,
    modifier: Modifier = Modifier,
    level: LbTopBarLevel = LbTopBarLevel.Identity,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showsDivider: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(LbTopBarDimens.ROW_HEIGHT_DP.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (onBack != null) BackControl(onBack = onBack)
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        title,
                        style = when (level) {
                            LbTopBarLevel.Identity -> AppTypography.headlineLarge
                            LbTopBarLevel.Page -> AppTypography.titleLarge
                        },
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = AppTypography.bodySmall,
                            color = TextHint
                        )
                    }
                }
            }
            trailing?.invoke()
        }
        if (showsDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = Border.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 那颗返回钮——**这一格之前它是设计系统里私藏的第二颗图标动作**。
 *
 * 原来这里画的是 `Box(size = 48).clickable(role = Button) { Icon(22dp) }`，热区、角色、
 * 名字三件事都由这一份自己保证，而同一族形状在卡片里、在供应商表单里又各画了一遍，
 * 每一遍都要重新想起来垫一次宽度。现在**形状归 [LbTextAction] 的图标档**，
 * 这一处只留页头才有的两件事：字形尺寸那一档（[LbTextActionGlyph.Header]）
 * 与标题左右那两个间距。
 *
 * 名字仍走资源、不走硬编码中文，也不靠箭头字形当标签——那一条是这一格最早的债，
 * 换了主人它不许跟着换（判据在 `DesignSystemRolesTest` 与 `PageHeaderConsistencyTest`）。
 */
@Composable
private fun BackControl(onBack: () -> Unit) {
    Spacer(Modifier.width(LbTopBarDimens.TITLE_GAP_DP.dp))
    LbTextAction(
        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        description = stringResource(R.string.common_back),
        onClick = onBack,
        tone = LbTextActionTone.RowSecondary,
        glyph = LbTextActionGlyph.Header
    )
    Spacer(Modifier.width(LbTopBarDimens.TITLE_GAP_DP.dp))
}
