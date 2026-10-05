package com.lovebrain.app.feature.roundstate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 这一次请求要带多大范围的上下文。
 *
 * 两颗取值刚好说出这里唯一的分歧：[FollowSwitch] 跟着住在本 store 的那颗长期开关，
 * [CurrentRoundOnly] 只对**发起来的那一次请求**成立。
 *
 * 为什么不是一个布尔参数：长按入口把值交给 `generate(...)` 之后，调用点要写的是
 * `RoundScope.CurrentRoundOnly` 而不是裸 `true`——裸 `true` 在 `generate(true)` 这种位置上
 * 读不出"这是一次性的"还是"把开关拨上去了"，而这两件事的差别正是这条入口的全部意义。
 */
enum class RoundScope {

    /** 跟随长期开关（普通点击那条路，行为与从前逐字一致） */
    FollowSwitch,

    /** 只读本轮：这一次请求排除旧记忆；**不**改动长期开关 */
    CurrentRoundOnly
}

/**
 * 轮次身份 + 仅看本轮开关的持有者（复核 第5节第2条 第 6 步"VM 不再持有 feature 的内部状态"）。
 *
 * 接管两颗原本住在 [com.lovebrain.app.viewmodel.LoveBrainViewModel] 里的可写流：
 * - `_generationRoundId`：稳定的轮次身份，只在真正完成一次新的整轮 generate 时变化
 *   （单条改写 / undo / feedback 等原地操作不改变它）。ResultArea 的 viewMode 只以它重置。
 * - `_onlyThisRound`：仅看本轮开关。开启后只携带通用生成规则、本轮真实消息和本轮想法，
 *   排除旧画像、关系阶段、历史对话、场景、事项、经验与持续意图。开关属于当前工作轮次；
 *   本轮重生成保留，开启新轮次或切档案后恢复默认。
 *
 * 为什么"切换 onlyThisRound 后旧结果立即 stale"这条跨块协调不在 store 里做：
 * 它要读 VM 的本轮上下文与输入指纹（生成链上的公共上游），store 不知道这些。
 * 所以切换走 [ToggleOnlyThisRound]，store 只负责翻转状态、再同步回调 [onOnlyThisRoundChanged]；
 * 复位（新轮次 / 切库）走 [SetOnlyThisRound]，不触发回调——那些路径各自有自己的 stale 判定。
 *
 * 除了这两条**写**路径，本类还多一条**读**路径 [resolveOnlyThisRound]：长按生成要的是
 * "这一次请求只读本轮"，那个值随请求参数传进来、在这里和长期开关汇合，不进状态。
 * 三条路径给出的仍然是同一个布尔，"排除旧记忆"到底排除什么由生成链自己定义，这里不另发明规则。
 *
 * 界面上那颗可见的"仅看本轮"开关已经撤掉了，于是长期开关在生产里**没有能把它拨开的入口**：
 * 它从复位值 false 起步，[Intent.ToggleOnlyThisRound] 与 [Intent.SetOnlyThisRound] 都留着
 * （开关这条能力不是为长按才存在的，撤 UI 不等于顺手删状态机），但给出 `true` 的唯一活路径
 * 只剩 [RoundScope.CurrentRoundOnly] 那一条——而它是**每次请求各算各的**，不写回这里。
 * 所以"长按一次、往后每次都只读本轮"在这种写法里根本构造不出来：待花费的值没有落脚点，
 * 普通点击每次重新算，算到的都是默认范围。
 */
class RoundStateStore(
    /** 切换 onlyThisRound 后旧结果需立即 stale；这条跨块协调由调用方决定怎么做。同步调用。 */
    private val onOnlyThisRoundChanged: () -> Unit = {}
) {

    private val _generationRoundId = MutableStateFlow(0)
    val generationRoundId: StateFlow<Int> = _generationRoundId.asStateFlow()

    private val _onlyThisRound = MutableStateFlow(false)
    val onlyThisRound: StateFlow<Boolean> = _onlyThisRound.asStateFlow()

    /** VM 内部读取的同步快照（生成链上的指纹 / 输入冻结都要读它） */
    val onlyThisRoundNow: Boolean get() = _onlyThisRound.value

    /**
     * 这一次请求实际该按哪种范围读上下文——**纯读，不写任何状态**。
     *
     * 它是"长按只影响这一次"的唯一通路：值随请求参数走进生成链，在这里和长期开关汇合，
     * 请求结束就消失。所以这里刻意**不**接受 Intent、也刻意**不**留一个"待花费"的字段：
     * 那种写法要靠"谁记得去清它"来保证一次性，而请求是可能被就地拒掉的（没有她的消息、
     * 供应商没配好、前台槽位被别的生成占着）——没发出去的那一次不会去清，
     * 待花费标记就一直躺在 store 里，被**下一次普通点击**领走。
     * 于是"长按一次"变成"往后每次都忽略旧记忆"，正是这条入口禁止的那种走法。
     *
     * 也不触发 [onOnlyThisRoundChanged]：一次性覆盖不是"拨开关"，不该把屏幕上当前的旧结果
     * 标成 stale——那是切换长期开关才有的协调。
     */
    fun resolveOnlyThisRound(roundScope: RoundScope = RoundScope.FollowSwitch): Boolean =
        when (roundScope) {
            RoundScope.FollowSwitch -> onlyThisRoundNow
            RoundScope.CurrentRoundOnly -> true
        }

    sealed interface Intent {
        /** 完成一次新的整轮 generate 时递增（回退那一路也用它，不递减、不用 magic number） */
        data object BumpRoundId : Intent

        /** 复位仅看本轮开关（新轮次 / 切档案）；不触发 stale 判定 */
        data class SetOnlyThisRound(val value: Boolean) : Intent

        /** 切换仅看本轮开关；翻转后同步触发 [onOnlyThisRoundChanged] 让旧结果标 stale */
        data object ToggleOnlyThisRound : Intent
    }

    /** 唯一写入口 */
    fun accept(intent: Intent) {
        when (intent) {
            Intent.BumpRoundId -> _generationRoundId.value++
            is Intent.SetOnlyThisRound -> _onlyThisRound.value = intent.value
            Intent.ToggleOnlyThisRound -> {
                _onlyThisRound.value = !_onlyThisRound.value
                onOnlyThisRoundChanged()
            }
        }
    }
}
