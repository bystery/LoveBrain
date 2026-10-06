package com.lovebrain.app.ui.panel.settings

import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/**
 * 整窗设置页的两件事：透明度那一颗换算（挂在唯一的换算口 [PanelBackdropOpacity.alphaOf] 上），
 * 以及"这一页不许长成别的东西"。
 *
 * 为什么把两条放在一格 JVM 测试里（不要设备、不要 Robolectric）：
 * · 浓度换算是一条**纯判据**——区间内线性、越界夹紧、非刻度就近对齐；最淡那一档停在合同
 *   划的那条线上（不是"背景完全退掉"，理由写在那一格的 KDoc 里）。
 *   它是"先处理背景层、正文保持可读"这句话唯一能被机器判的那一半，
 *   另一半（真的作用在哪一层）在服务侧，本机量不到。
 * · "不是弹窗/半屏 Sheet、不跳外部 Activity、不新造一个偏好模块"这类否定式合同，
 *   读结构比读语义树可靠：语义树只能证明"现在没画"，证明不了"哪天有人加了一扇浮层"。
 *   同一族先例写在 `UiLayerDependencyContractTest`（浮层只有一个所有者那一格）。
 */
class SettingsPageStructureTest {

    private val settingsDir: File
        get() = File("src/main/java/com/lovebrain/app/ui/panel/settings")
            .takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/settings")

    private val sources: List<Pair<String, String>> by lazy {
        assertTrue("找不到 $settingsDir——这一格会扫了个空集恒绿", settingsDir.isDirectory)
        settingsDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.name to SourceScan.maskComments(it.readText(Charsets.UTF_8)) }
            .toList()
            .also { assertTrue("settings/ 下没有 .kt：路径接错了", it.isNotEmpty()) }
    }

    /**
     * 浓度换算这条纯判据：区间内线性、越界夹紧、非刻度就近对齐。
     *
     * ⚠ **主体重新挂在哪（2026-10-03 编译修复）**：这一格以前测的是设置页文件里那颗
     * `opacityBackdropAlpha(percent)`。那一颗本来就只是**转发**
     * （ 一个字写着
     * `= PanelBackdropOpacity.alphaOf(percent)`，写它的目的正是"预览条与真面板永远同一格"），
     * 而它连同预览条一起随用户原话"设置里面暂时先弄一个调透明度的，别的都不要弄"退场。
     * 现在这一族判据直接挂在**唯一那一个换算口** [PanelBackdropOpacity.alphaOf] 上——
     * 也就是面板真的画出来那两处读的那一颗：面板根那层
     * `SurfaceBase.copy(alpha = alphaOf(...))`（`ui/panel/LoveBrainPanelScreen.kt:316-321`）
     * 与大面积卡底 `core/designsystem/Color.kt:81-82 panelBackdropCardColor()`。
     * **测的仍是同一件事**："界面上交出的每一个数与画出来的 alpha 必须同一来源"——
     * 而且这一层比转发那颗更近，转发那颗再也不可能在中间偷偷算第二遍。
     * 这一格里**没有**任何"预览条画在屏幕上"的断言：那一半的主体被点名删了，
     * 判据在别处没有对应主体（设置页现在只有 label + 滑杆 + 一个百分比读数）。
     *
     * ⚠ **为什么这一档最低只到 0.6，而不是"完全透明"的 0**：界面合同给整窗背景划的那条线是
     * `0.6..1.0`（"先处理背景层、正文保持可读"那一节写的就是这个区间），滑杆的"最淡"就等于
     * 0.6 而不是 0。滑到底把面板洗到看不见的那一版（下限 40%）越过了这条线，本轮按合同收回 60；
     * 于是"滑到最底 = 背景完全退掉"这句旧预期本身就是错的——它要是还成立，反而说明实现
     * 又越过了规格。这里钉的是**合同那两个端点**，不是实现的当前读数。
     *
     * ⚠ 量纲也照原话钉死：盘上那个整数是**不透明度**，100 = 最不透明、越小越通透，
     * 所以 `alphaOf(100) == 1f`、`alphaOf(60) == 0.6f`。镜像那颗 `transparencyOf` 在 UI 里
     * 没有任何消费者，这一格不许把它读成"100 应该最透"。
     *
     * 各条要挡住的坏实现：
     * · 端点（0/负数 → 0.6、100 与越上界 → 1f）：把越界原样抄出去的实现会交出 0f 或 1.8f，
     *   前者面板看不见、后者颜色溢出；
     * · 区间内线性（65 → 0.65、85 → 0.85）：只把下限钳住却把中段拍平（一律 0.6）的实现，
     *   滑杆就成了装饰；
     * · 就近对齐刻度（62 落回 60，绝不交出一个盘上存不下的数）：界面上的数字与盘上存的数字
     *   必须一模一样，否则重开设置会看到另一个数；
     * · 逐档往返（下面那个循环）：中途偷偷加一层"为了好看"的偏移（例如一律再减 0.05），
     *   端点与中段那几条可能还是绿的，而滑杆上写的 80% 画出来是 75%；
     * · null（这台机器从没滑过）：必须落到合法档，不能是 0f。
     */
    @Test
    fun `opacity maps the background layer linearly and clamps out-of-range values`() {
        // 合同区间的两端：滑到最底就是最底那一档（0.6），滑到顶是不透明
        assertEquals(0.6f, PanelBackdropOpacity.alphaOf(0), 0f)
        assertEquals(1f, PanelBackdropOpacity.alphaOf(100), 0f)
        // 区间内逐档线性（刻度 5%，两端都取到）
        assertEquals(0.65f, PanelBackdropOpacity.alphaOf(65), 0f)
        assertEquals(0.85f, PanelBackdropOpacity.alphaOf(85), 0f)
        // 非刻度的脏值就近对齐到合法刻度（62 落回 60，绝不交出一个盘上存不下的数）
        assertEquals(0.6f, PanelBackdropOpacity.alphaOf(62), 0f)
        // 越界夹紧：滑杆交不出 1f 以上的数，也交不出比合同下限更淡的数
        assertEquals(0.6f, PanelBackdropOpacity.alphaOf(-40), 0f)
        assertEquals(1f, PanelBackdropOpacity.alphaOf(180), 0f)
        // 从没写过这一项（老数据 / 升级）：落到默认那一档 = 完全不透明，不是"面板看不见"
        assertEquals(1f, PanelBackdropOpacity.alphaOf(null), 0f)

        // 逐档往返：这一族交出的每一个数，画出来的 alpha 必须还能原样读回那个数。
        // 刻度表由被测对象自己数（不许在这一格里抄第二份 60..100 的字面量清单）。
        val ticks = PanelBackdropOpacity.steps
        val broken = ticks.filter { (PanelBackdropOpacity.alphaOf(it) * 100).roundToInt() != it }
        assertTrue(
            "画出来的 alpha 与盘上那个整数不是同一个来源，坏掉的档位：$broken（${ticks.size} 档全跑）",
            broken.isEmpty()
        )
        // 循环哨兵：档数为 0 时上面那句会空跑成"一条都没坏 = 绿"，那是假绿
        assertEquals("这一格真的逐档量过（刻度表空了 = 量具瞎）", 9, ticks.size)
        assertEquals("刻度就是 5% 一档、从下限到 100", listOf(60, 65, 70, 75, 80, 85, 90, 95, 100), ticks)
    }

    /**
     * 整窗设置页**不是一扇浮层**。
     *
     * 判的是形状而不是意图：自画的 `Dialog(`、设计系统的 `LbDialog(`、`LbModalSheet`、
     * `BottomSheet`、`Popup(` 任何一颗一出现，这一页就不再是"整个悬浮窗切过去"的那一页了
     * （面板那扇 overlay 窗口也起不了系统对话框——那里缺的是 Activity 的 window token）。
     * 词边界那一支是必要的：`LbDialog(` 里含 `Dialog(` 这个子串，裸 contains 会把
     * "只走共用所有者"误报成"自己开了一扇窗"（同一族坑：按子串认的锚点会误吃前缀）。
     *
     * ⚠ **`LbModalSheet` 在 `SettingsIntentEntry.kt` 里是登记豁免的**：那一格首次打开持续意图
     * 时弹一扇介绍浮层（复用浮层那一份开合动画与版式），它是**叠加层**、不是把整页切过去——
     * "设置页本身仍是一页"这条合同断的是页容器，不是禁这页里一颗合法的弹层入口。
     * 豁免只豁免这一颗文件这一把锚点，别读成"LbModalSheet 在设置页随便用"。
     */
    @Test
    fun `the settings page opens no floating surface and no external window`() {
        val selfDrawnDialog = Regex("(?<![0-9A-Za-z_])Dialog\\s*\\(")
        val needles = listOf(
            "LbDialog(" to "共用浮层的所有者（这一页要画在正文里）",
            "LbModalSheet" to "半屏 Sheet",
            "BottomSheet" to "半屏 Sheet",
            "Popup(" to "弹层",
            "startActivity" to "跳外部 Activity"
        )
        // 登记式豁免（与 UiLayerDependencyContractTest 那一族同形）：路径 -> 为什么这里不是"整页浮层"。
        // 只豁免"这一颗文件用了这一把锚点"这一件事；豁免消失时这格要红（不许留一条已不成立的豁免）。
        val sheetExemptions: Map<String, String> = mapOf(
            "SettingsIntentEntry.kt" to
                "首次打开持续意图时弹的那扇介绍浮层（LbModalSheet）：它是叠加层、不是页容器，" +
                    "整页仍是一页；拨开关才触发，确认后写进 prefs 不再弹"
        )
        val hits = needles.flatMap { (needle, why) ->
            sources.filter { (name, code) ->
                code.contains(needle) &&
                    !(needle == "LbModalSheet" && sheetExemptions.containsKey(name))
            }.map { "${it.first} 里的「$needle」——$why" }
        } + sources.filter { (_, code) -> selfDrawnDialog.containsMatchIn(code) }
            .map { "${it.first} 里自己开了一扇 Dialog(" }
        assertTrue("设置页长出了不该有的容器：\n$hits", hits.isEmpty())
        // 豁免不是空白支票：被豁免的那颗文件必须还在、且确实还画着 LbModalSheet
        // （文件被搬走/改名/删掉介绍浮层 ⇒ 这条豁免就成了幽灵，同样红）。
        sheetExemptions.forEach { (name, why) ->
            val exempt = sources.firstOrNull { it.first == name }
                ?: error("豁免登记指向的文件 $name 不在 settings/ 扫描范围内——豁免要一起删，别留着当已有闸（理由：$why）")
            assertTrue(
                "$name 画了 LbModalSheet 才许它豁免；看不见 LbModalSheet 说明这颗豁免已经没主人认领了：$why",
                exempt.second.contains("LbModalSheet")
            )
        }
    }

    /**
     * 这一页只集中四件事，**不新造模块**。
     *
     * "偏好""工具中心""低频工具"都不是真实功能名，是历史上按使用频率做过的归类；
     * 问卷那几题的措辞也不授权新增一个叫"偏好"的东西。名字先钉住，免得下一轮又被"顺手加一格"。
     */
    @Test
    fun `the settings page does not introduce a preference or tool-hub module`() {
        val bannedWords = listOf("偏好", "工具中心", "低频工具")
        val hits = bannedWords.flatMap { word ->
            sources.filter { (_, code) -> code.contains(word) }.map { "${it.first} 里出现了「$word」" }
        }
        assertTrue("这一页新造了没有的结构：\n$hits", hits.isEmpty())
    }

    /**
     * 反向证人：上面那把尺看得见东西。
     *
     * 否定式判据坏掉的时候最安静——正则写歪、路径接错、mask 把整份文件抹成空格，
     * 三种都表现为"一条都没扫到 = 通过"。所以拿生产里**真有**浮层的那一屏同判据跑一遍：
     * `LbDialog(` 必须被指认到，`BottomSheet` 必须指认不到（它确实不存在，
     * 这条同时证明我没有把名单写成"什么都命中"）。
     */
    @Test
    fun `the floating-surface ruler is not blind on a known dialog host`() {
        val provider = File(
            "src/main/java/com/lovebrain/app/ui/home/ProviderSection.kt"
        ).takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/home/ProviderSection.kt")
        assertTrue("找不到 $provider——这把尺会恒绿", provider.isFile)
        val code = SourceScan.maskComments(provider.readText(Charsets.UTF_8))
        assertTrue("首页供应商那一屏本来就走 LbDialog 那扇浮层，扫不到说明判据坏了", code.contains("LbDialog("))
        assertTrue("名单里那条不存在的形状不该被误报", !code.contains("BottomSheet"))
    }
}
