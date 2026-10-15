package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lovebrain.app.BubbleSizeTier
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbRowLabel
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.Spacing

/** 图标大小那一行的行根锚点（量具按它找那一行，见锚点合同） */
internal const val SETTINGS_BUBBLE_SIZE_ROW_TAG = "settings-row-size"

/** 图标大小那一行那条浅底轨道容器的锚点：整条轨道一颗，格子共享它这一条底 */
internal const val SETTINGS_BUBBLE_SIZE_PILL_TAG = "settings-pill-size"

/**
 * 设置页里的**悬浮图标大小**那一格：同一行左边"图标大小"、右边一条浅底轨道里的三档分段（小 / 标准 / 大）。
 *
 * 只做三个档位、不做连续 1dp 调节：三个档位已覆盖日常需求，也容易保证布局和手势正确。
 * 所以这一格**没有滑杆**——滑杆那颗归透明度，两件事不共用控件。
 *
 * 五条纪律：
 * 1. **档位真值不在这页抄一份**。可选项一律从 [BubbleSizeTier.ALLOWED_DP] 数出来（48 / 56 / 64），
 *    选中那一颗按 [BubbleSizeTier.snapDp] 后的读数比——刻度尺长在 `AppConfig`，窗口宽高、主球直径、
 *    角标偏移、面板贴边都从那里推。不许只对界面用 `scale()` 而留着旧的系统窗口点击区。
 * 2. **选中态这一颗是"及时重绘"用的局部值，不是第二份持久真值**：宿主交来的 `sizeDp` 读的是盘上
 *    那一格，而那条读数是**普通取值**、没有可观察通道，所以点完芯片之后光靠"让服务重读一次窗口"
 *    并不会让这一页自己重组——真实尺寸已经改了、屏上选中态还停在旧档，正是"点了没反馈"的成因。
 *    修法只在显示这一侧：`remember(sizeDp)` 起一颗 `mutableIntStateOf`，点一次就按**同一把尺**
 *    （[BubbleSizeTier.snapDp]，也就是写盘那一条链用的那颗）更新它，等宿主那份从盘上读回的新值
 *    进了参数，key 一变就重挂到读回值上。首次进入这一页仍然从盘上的读数起，落盘来源仍然只有一个。
 *    ⚠ 10-11 那一版把这条改对过；这一版换的是**画法**（三颗各自带描边的独立盒子 → 一条浅底轨道 + 透明格），
 *    这颗及时重绘的局部值一寸没退，"点一下当场变蓝"仍是这一格的验收项。
 * 3. **词表住资源，不内联**：三颗标签取 `settings_bubble_size_small/standard/large`，
 *    写进 `LbChip(...)` 调用里会被内联字面量的预算闸数进去。
 * 4. **行不画外框**：整组一张外框由页面那一侧拥有，这一格只渲染组内的那一行
 *    （`Modifier.settingsEntryRow()`：铺满宽度 + 版式下限），不再自带卡壳。
 * 5. **脸照原型 `.segments` 那一族**：底与圆角归**轨道**（[SettingsSegmentedPill] 那颗 `SurfaceInset`
 *    轨道），格子里只有文字与选中那一块实心（[LbChipStyles.segmentedPill] 静息**不画底不画描边**）。
 *    三格各自带描边会读成"三个独立按钮"，正是这一版判"根本不一样"的那件事；轨道读成"一件事的三个选项"。
 *    每格最小可见宽指回族里那颗 [SettingsRowDimens.PILL_CELL_MIN_WIDTH_DP]，可见高与 48dp 热区
 *    由 [LbChipStyles.segmentedPill] 那一条分层外盒管（**缩的是显示，不是命中**），这一格不新造形状。
 *
 * 词条与刻度按 [zip] 对齐：刻度尺哪天多出一档而词条没补，多出来那一档不会画出一颗错标签的胶囊，
 * 而会由"三档恰好三颗"那条形状闸先红。
 */
@Composable
internal fun SettingsBubbleSizeEntry(
    sizeDp: Int,
    onSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tiers = listOf(
        R.string.settings_bubble_size_small,
        R.string.settings_bubble_size_standard,
        R.string.settings_bubble_size_large
    )
    // 盘上读回来的那一档（宿主给的那一份）：局部选中态的起点，也是首次进这一页时的唯一来源。
    val persistedDp = BubbleSizeTier.snapDp(sizeDp)
    var selectedDp by remember(sizeDp) { mutableIntStateOf(persistedDp) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // 行自己**不画外框**、只取族里那颗行骨架；标签贴左、那条轨道贴右。
        // 版式高度由轨道里那格分层外盒的 48dp 下限顶出来，不是这里再叠一层 padding 撑的。
        modifier = modifier.settingsEntryRow().testTag(SETTINGS_BUBBLE_SIZE_ROW_TAG)
    ) {
        // 标签走**自然宽**、中间那颗 Spacer 吃剩下的槽（原型 `.lbl{white-space:nowrap}` +
        // `.action-area{flex:1;justify-content:flex-end}`）：轨道因此贴右缘，而标签那颗节点报回来
        // 的就是自己的自然宽——整页"行标签同一档字"那把尺量的正是这个数，撑满槽的那一颗会被
        // 读成另一种字。三档的命中区都不靠标签让位：轨道按 fit-content 先量到自己那一份宽度。
        LbRowLabel(text = stringResource(R.string.settings_bubble_size_row_label))
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.sm)) // 原型 `.item{gap:5px}` / `.action-area{gap:6px}`，取矮的那一档
        // 一条浅底轨道里三格透明文字，只有选中那一格涂蓝——底与圆角只在轨道画一次。
        SettingsSegmentedPill(modifier = Modifier.testTag(SETTINGS_BUBBLE_SIZE_PILL_TAG)) {
            (BubbleSizeTier.ALLOWED_DP zip tiers).forEach { (dp, labelRes) ->
                LbChip(
                    label = stringResource(labelRes),
                    selected = dp == selectedDp,
                    onClick = {
                        onSizeChange(dp)
                        // 写盘那一条链先过同一把尺再落，所以这里按尺子读回的档画就是盘上那一档；
                        // 宿主下一次交来盘上的新读数时（key 变了）这颗局部值会重挂到读回值上。
                        selectedDp = BubbleSizeTier.snapDp(dp)
                    },
                    interaction = LbChipInteraction.Single,
                        style = LbChipStyles.segmentedPill.copy(
                            // 最小格宽要落在**看得见**那一格上：从 modifier 传 widthIn 只撑外圈热区
                            pillMinWidth = SettingsRowDimens.PILL_CELL_MIN_WIDTH_DP.dp
                        ),
                )
            }
        }
    }
}
