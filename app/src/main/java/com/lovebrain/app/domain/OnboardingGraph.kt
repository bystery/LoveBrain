package com.lovebrain.app.domain

import kotlinx.serialization.Serializable

/**
 * v4.2 动态自适应问卷机制：5 步状态机，每条分支步长恒定 5 题。
 *
 * 设计来源：v4.0 设计文档（timu.md）→ v4.1 口语化改写（R1）+ 自定义输入（R2）
 * → v4.2 多选化重构（R3）。
 *
 * v4.2 增量：
 * R3 每道题自带 selectionMode（SINGLE / MULTIPLE），由题库声明而非 UI 层猜；
 *    答案模型从 Map<Int, Int> 升级为 Map<Int, OnboardingAnswer>（Set<Int> + customText）；
 *    废除 -1 哨兵——自定义补充与固定选项可并存；
 *    红线判定泛化为遍历 selectedIndices 中任意 isRedline==true 即触发；
 *    Q5 红线隐藏项清理只删隐藏 index，保留其余选择与 customText；
 *    点击选项不再自动跳页，统一走「下一步」按钮。
 *
 * 结构：Q1 入口题（5 选项 A-E，SINGLE）→ Q1 答案确定分支 → Q2-Q5 跟随同一分支。
 * 红线：E 分支 Q3/Q4 中任意 selected option 含 isRedline==true → Q5 隐藏 A/B 选项。
 */

// ═══════════ 数据模型 ═══════════

/** 选择模式：单选 / 多选 */
enum class SelectionMode {
    SINGLE,
    MULTIPLE
}

/** 单个选项 */
data class OnboardingOption(
    val text: String,
    val tag: String,
    val isRedline: Boolean = false
)

/** 单道题 */
data class OnboardingQuestion(
    val id: String,
    val step: Int,
    val branch: String,
    val title: String,
    val options: List<OnboardingOption>,
    val selectionMode: SelectionMode = SelectionMode.SINGLE,
    val maxSelections: Int? = null
)

/** 统一答案对象：支持多选 + 自定义补充并存 */
data class OnboardingAnswer(
    val selectedIndices: Set<Int> = emptySet(),
    val customText: String = ""
) {
    /** SINGLE：选了 1 个；MULTIPLE：至少选了 1 个或有 customText */
    fun isAnswered(question: OnboardingQuestion): Boolean {
        return when (question.selectionMode) {
            SelectionMode.SINGLE -> selectedIndices.size == 1
            SelectionMode.MULTIPLE -> selectedIndices.isNotEmpty() || customText.isNotBlank()
        }
    }
}

// ═══════════ JSON Schema（交付引擎） ═══════════

@Serializable
data class OnboardingSchema(
    val stage: String,
    val meta: Meta,
    val names: Names,
    val tags: List<String>,
    val profile: Profile,
    val redline_triggered: Boolean,
    val system_directive: String
) {
    @Serializable
    data class Meta(
        val total_answered: Int,
        val path: List<String>
    )

    // 双方称呼独立上下文字段
    @Serializable
    data class Names(
        val self: String = "",
        val counterpart: String = ""
    )

    @Serializable
    data class Profile(
        val interpersonal_context: String,
        val counterpart_feedback: String,
        val core_dilemma: String,
        val user_intent: String
    )
}

// ═══════════ 状态机 ═══════════

object OnboardingStateMachine {

    /** Q1 答案(选项 index 0-4)确定分支字母 */
    fun branchFromQ1(answerIndex: Int): String = ('A' + answerIndex).toString()

    /** stage 粗分类映射（Q1 选项 → 英文 stage） */
    private val stageMap = mapOf(
        "A" to "new", "B" to "chasing", "C" to "dating",
        "D" to "conflict", "E" to "breakup"
    )
    fun stageFromBranch(branch: String): String = stageMap[branch] ?: "new"

    /**
     * 检查红线是否触发：遍历 E 分支 Q3/Q4 被选中的固定 Option，
     * 只要存在 isRedline==true 即触发（ANY 语义）。
     * answers: step(1-5) → OnboardingAnswer
     */
    fun isRedlineTriggered(answers: Map<Int, OnboardingAnswer>, branch: String): Boolean {
        if (branch != "E") return false
        for (step in listOf(3, 4)) {
            val answer = answers[step] ?: continue
            val question = OnboardingBank.question(step, branch)
            for (idx in answer.selectedIndices) {
                val option = question.options.getOrNull(idx)
                if (option?.isRedline == true) return true
            }
        }
        return false
    }

    /** Stage5 红线触发时，哪些选项被隐藏（A=0 和 B=1） */
    fun hiddenOptionIndices(redline: Boolean): Set<Int> {
        if (!redline) return emptySet()
        return setOf(0, 1)
    }

    /**
     * 多选 Toggle 逻辑：
     * SINGLE → 点击新选项替换旧选择；点击已选选项保持不变（单选至少保持一个）。
     * MULTIPLE → 点击未选则添加（未达 max）；点击已选则取消；达 max 时不添加。
     *
     * @return Pair<新答案, 是否因达到上限而被拒绝>
     */
    fun toggleOption(
        question: OnboardingQuestion,
        answer: OnboardingAnswer,
        index: Int
    ): OnboardingAnswer {
        return when (question.selectionMode) {
            SelectionMode.SINGLE -> {
                if (answer.selectedIndices == setOf(index)) {
                    // 点击已选项：单选保持不变（不允许取消到空）
                    answer
                } else {
                    answer.copy(selectedIndices = setOf(index))
                }
            }
            SelectionMode.MULTIPLE -> {
                val current = answer.selectedIndices.toMutableSet()
                if (index in current) {
                    // 取消已选
                    current.remove(index)
                    answer.copy(selectedIndices = current)
                } else {
                    // 添加新选
                    val max = question.maxSelections
                    if (max != null && current.size >= max) {
                        // 达到上限，不添加（调用方负责提示）
                        answer
                    } else {
                        current.add(index)
                        answer.copy(selectedIndices = current)
                    }
                }
            }
        }
    }

    /**
     * Q1 改变后清理 Q2-Q5 全部答案（selectedIndices + customText）。
     */
    fun clearDownstreamAnswers(
        answers: MutableMap<Int, OnboardingAnswer>
    ) {
        answers.keys.filter { it >= 2 }.forEach { answers.remove(it) }
    }

    /**
     * 红线激活后清理 Q5 中被隐藏的选项 index，保留其余选择和 customText。
     */
    fun cleanHiddenFromQ5(
        answers: MutableMap<Int, OnboardingAnswer>,
        hiddenIndices: Set<Int>
    ) {
        val q5 = answers[5] ?: return
        val cleaned = q5.selectedIndices - hiddenIndices
        answers[5] = q5.copy(selectedIndices = cleaned)
    }
}

// ═══════════ Schema 构建器 ═══════════

object OnboardingSchemaBuilder {

    fun build(
        answers: Map<Int, OnboardingAnswer>,
        myName: String,
        herName: String
    ): OnboardingSchema {
        val q1Answer = answers[1]
        val branch = if (q1Answer != null && q1Answer.selectedIndices.isNotEmpty()) {
            OnboardingStateMachine.branchFromQ1(q1Answer.selectedIndices.first())
        } else "A"
        val stage = OnboardingStateMachine.stageFromBranch(branch)
        val redline = OnboardingStateMachine.isRedlineTriggered(answers, branch)
        val directive = if (redline) "SELF_REBUILD_ONLY" else "NORMAL_ASSIST"

        val path = buildPath(branch)
        val tags = collectTags(answers, branch)
        val profile = buildProfile(answers, branch)
        val totalAnswered = answers.count { (k, a) ->
            val q = if (k == 1) OnboardingBank.q1
                    else OnboardingBank.question(k, branch)
            a.isAnswered(q)
        }

        return OnboardingSchema(
            stage = stage,
            meta = OnboardingSchema.Meta(total_answered = totalAnswered, path = path),
            names = OnboardingSchema.Names(
                self = myName.trim(),
                counterpart = herName.trim()
            ),
            tags = tags,
            profile = profile,
            redline_triggered = redline,
            system_directive = directive
        )
    }

    private fun buildPath(branch: String): List<String> {
        val path = mutableListOf<String>()
        path.add("Q1")
        for (step in 2..5) {
            path.add("Q${step}-${branch}")
        }
        return path
    }

    private fun collectTags(
        answers: Map<Int, OnboardingAnswer>,
        branch: String
    ): List<String> {
        val tags = mutableListOf<String>()
        for (step in 2..5) {
            val answer = answers[step] ?: continue
            val question = OnboardingBank.question(step, branch)
            for (idx in answer.selectedIndices) {
                val option = question.options.getOrNull(idx) ?: continue
                tags.add(option.tag)
            }
        }
        return tags.distinct()
    }

    private fun buildProfile(
        answers: Map<Int, OnboardingAnswer>,
        branch: String
    ): OnboardingSchema.Profile {
        fun field(step: Int): String {
            val answer = answers[step] ?: return ""
            val question = OnboardingBank.question(step, branch)
            val parts = mutableListOf<String>()
            // 固定选项文案
            for (idx in answer.selectedIndices) {
                val option = question.options.getOrNull(idx) ?: continue
                parts.add(option.text)
            }
            // 自定义补充（与固定选项并存）
            val custom = answer.customText.trim()
            if (custom.isNotEmpty()) {
                parts.add("补充：$custom")
            }
            return parts.joinToString("；")
        }

        return OnboardingSchema.Profile(
            interpersonal_context = field(2),
            counterpart_feedback = field(3),
            core_dilemma = field(4),
            user_intent = field(5)
        )
    }
}

// ═══════════ 题库（v4.2 口语化改写 + 多选配置；tag/isRedline/结构与 v4.0 一致） ═══════════

object OnboardingBank {

    /** Q1 入口题（SINGLE：决定分支） */
    val q1 = OnboardingQuestion(
        id = "Q1",
        step = 1,
        branch = "",
        title = "你们现在啥情况？",
        options = listOf(
            OnboardingOption("刚认识/刚加上好友", "stage_new"),
            OnboardingOption("有点暧昧/在拉扯", "stage_chasing"),
            OnboardingOption("已经在一起了", "stage_dating"),
            OnboardingOption("闹矛盾了/僵住了", "stage_conflict"),
            OnboardingOption("快分了/已经分了", "stage_breakup")
        ),
        selectionMode = SelectionMode.SINGLE
    )

    /** 取当前步的题目（step 2-5 需要 branch） */
    fun question(step: Int, branch: String): OnboardingQuestion {
        return when (step) {
            1 -> q1
            2 -> when (branch) {
                "A" -> q2a; "B" -> q2b; "C" -> q2c; "D" -> q2d; "E" -> q2e
                else -> q2a
            }
            3 -> when (branch) {
                "A" -> q3a; "B" -> q3b; "C" -> q3c; "D" -> q3d; "E" -> q3e
                else -> q3a
            }
            4 -> when (branch) {
                "A" -> q4a; "B" -> q4b; "C" -> q4c; "D" -> q4d; "E" -> q4e
                else -> q4a
            }
            5 -> when (branch) {
                "A" -> q5a; "B" -> q5b; "C" -> q5c; "D" -> q5d; "E" -> q5e
                else -> q5a
            }
            else -> q1
        }
    }

    // ─── Step 2: 基础场景与现状 ───

    private val q2a = OnboardingQuestion("Q2-A", 2, "A", "你们怎么认识的？", listOf(
        OnboardingOption("同事/同学/同圈子", "ctx_circle"),
        OnboardingOption("朋友聚会/活动上认识的", "ctx_social"),
        OnboardingOption("网聊/匹配软件认识的", "ctx_online"),
        OnboardingOption("偶遇/长辈介绍的，基本不熟", "ctx_blind")
    ), selectionMode = SelectionMode.SINGLE)

    private val q2b = OnboardingQuestion("Q2-B", 2, "B", "最近你俩什么节奏？", listOf(
        OnboardingOption("刚升温，认识不到 1 个月", "pace_early"),
        OnboardingOption("拉扯 1~2 个月，一直没突破", "pace_stuck"),
        OnboardingOption("忽冷忽热，时好时坏", "pace_wave"),
        OnboardingOption("基本我主动找她，她很少主动", "pace_passive")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q2c = OnboardingQuestion("Q2-C", 2, "C", "你俩在一起多久了？", listOf(
        OnboardingOption("不到 3 个月，还热乎着", "dur_honeymoon"),
        OnboardingOption("3 个月到 1 年，开始磨合", "dur_adjust"),
        OnboardingOption("1 年以上，有点平淡了", "dur_stable"),
        OnboardingOption("长期异地，聚少离多", "dur_distance")
    ), selectionMode = SelectionMode.SINGLE)

    private val q2d = OnboardingQuestion("Q2-D", 2, "D", "这次为啥吵起来的？", listOf(
        OnboardingOption("小事吵的，话赶话上头", "cause_temper"),
        OnboardingOption("碰了底线：撒谎/异性没边界", "cause_trust"),
        OnboardingOption("积怨太久，这次总爆发", "cause_accumulated"),
        OnboardingOption("她闹情绪，问也问不出来", "cause_unclear")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q2e = OnboardingQuestion("Q2-E", 2, "E", "现在具体到哪一步了？", listOf(
        OnboardingOption("她正式提了分手，没几天", "brk_fresh"),
        OnboardingOption("没明说，但已经断联了", "brk_ghost"),
        OnboardingOption("我冲动提的分手，后悔了", "brk_regret"),
        OnboardingOption("分了段时间，还是放不下", "brk_lingering")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    // ─── Step 3: 对方近期反应与态度 ───

    private val q3a = OnboardingQuestion("Q3-A", 3, "A", "你发消息过去，她一般怎么回？", listOf(
        OnboardingOption("挺热情，还会主动找话题", "att_warm"),
        OnboardingOption("客气有回，但就几个字", "att_polite"),
        OnboardingOption("回得超慢，半天蹦一两句", "att_slow"),
        OnboardingOption("纯躺列，基本不说话", "att_silent")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q3b = OnboardingQuestion("Q3-B", 3, "B", "聊天之外，你们更近一步了吗？", listOf(
        OnboardingOption("深夜聊过心事，交过底", "depth_deep"),
        OnboardingOption("常单独约会，有过肢体接触", "depth_meet"),
        OnboardingOption("只停留在玩笑，没敢走心", "depth_surface"),
        OnboardingOption("一约线下就躲，只想线上聊", "depth_online")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q3c = OnboardingQuestion("Q3-C", 3, "C", "你俩聊天最容易出啥问题？", listOf(
        OnboardingOption("她发泄我讲理，越聊越炸", "comm_logic"),
        OnboardingOption("只会早安晚安吃了没", "comm_dry"),
        OnboardingOption("她爱冷战憋着，让你猜", "comm_cold"),
        OnboardingOption("爱翻旧账，小事扯出一堆", "comm_history")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q3d = OnboardingQuestion("Q3-D", 3, "D", "这回僵多久了？", listOf(
        OnboardingOption("几个小时，气还没消", "freeze_hours"),
        OnboardingOption("1~2 天，谁也没找谁", "freeze_short"),
        OnboardingOption("超过 3 天，甚至一周了", "freeze_long"),
        OnboardingOption("她回是回，但句句带刺", "freeze_cold")
    ), selectionMode = SelectionMode.SINGLE)

    private val q3e = OnboardingQuestion("Q3-E", 3, "E", "你们现在还说得上话吗？", listOf(
        OnboardingOption("还正常回，语气挺客气", "reach_ok"),
        OnboardingOption("回得敷衍，甚至已读不回", "reach_ignore"),
        OnboardingOption("删了部分联系方式，还留一两个", "reach_part"),
        OnboardingOption("电话微信全被拉黑", "reach_blocked", isRedline = true)
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    // ─── Step 4: 核心症结与痛点（红线拦截层） ───

    private val q4a = OnboardingQuestion("Q4-A", 4, "A", "现在聊天你最卡哪儿？", listOf(
        OnboardingOption("开不了话头，怕像查户口", "blk_open"),
        OnboardingOption("接不住梗，容易把天聊死", "blk_dry"),
        OnboardingOption("主动怕讨好，被动没存在感", "blk_balance"),
        OnboardingOption("摸不清她喜欢啥，聊不到一块", "blk_vibe")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q4b = OnboardingQuestion("Q4-B", 4, "B", "卡在哪儿，没法更进一步？", listOf(
        OnboardingOption("猜不透她是喜欢我，还是享受被追", "blk_heart"),
        OnboardingOption("到天花板了，不知道咋捅破", "blk_confirm"),
        OnboardingOption("陷进去了，患得患失没了主见", "blk_anxious"),
        OnboardingOption("怕表白被拒，朋友都没得做", "blk_fear")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q4c = OnboardingQuestion("Q4-C", 4, "C", "这段感情里，最磨你的是啥？", listOf(
        OnboardingOption("委屈，一直在妥协讨好", "blk_pleasing"),
        OnboardingOption("累，一直猜她心思脸色", "blk_tired"),
        OnboardingOption("焦虑，觉得她没以前在乎我", "blk_insecure"),
        OnboardingOption("烦，沟通太累，宁愿自己待着", "blk_annoyed")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q4d = OnboardingQuestion("Q4-D", 4, "D", "冷静想想，这回到底为啥僵？", listOf(
        OnboardingOption("我错在先，但拉不下脸破冰", "blk_my_fault"),
        OnboardingOption("她太过了，我不想无底线认错", "blk_boundary"),
        OnboardingOption("情绪上头，就差个台阶", "blk_temper"),
        OnboardingOption("根子问题没解决，哄好还得吵", "blk_deep")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q4e = OnboardingQuestion("Q4-E", 4, "E", "她说过特别绝的话吗？", listOf(
        OnboardingOption("明确说过\u201c不可能/放过我\u201d", "blk_explicit_refusal", isRedline = true),
        OnboardingOption("说\u201c先冷静下\u201d，没说死", "blk_cool_down"),
        OnboardingOption("还在数落我以前的毛病", "blk_venting"),
        OnboardingOption("客客气气祝我好，保持距离", "blk_calm_exit")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    // ─── Step 5: 破局诉求与目标 ───

    private val q5a = OnboardingQuestion("Q5-A", 5, "A", "这回找军师，你最想要啥？", listOf(
        OnboardingOption("给我几个不尬的开场白", "goal_icebreak"),
        OnboardingOption("帮我接话更有趣一点", "goal_polish"),
        OnboardingOption("帮我分析她到底想不想聊", "goal_judge"),
        OnboardingOption("找机会约出第一次见面", "goal_invite")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q5b = OnboardingQuestion("Q5-B", 5, "B", "最想让军师在哪步搭把手？", listOf(
        OnboardingOption("帮我推拉起来，别一味迎合", "goal_frame"),
        OnboardingOption("来点暧昧张力的话，试探她", "goal_flirt"),
        OnboardingOption("设计个自然的邀约，推进线下", "goal_escalate"),
        OnboardingOption("教我有底线地把关系挑明", "goal_confirm")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q5c = OnboardingQuestion("Q5-C", 5, "C", "现在最想让军师帮你稳住啥？", listOf(
        OnboardingOption("接住她的情绪雷点，别吵起来", "goal_soothe"),
        OnboardingOption("整点走心互动，找回新鲜感", "goal_fresh"),
        OnboardingOption("帮我立住边界，不再单向迎合", "goal_boundary"),
        OnboardingOption("帮我理清问题，别再内耗", "goal_evaluate")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q5d = OnboardingQuestion("Q5-D", 5, "D", "这局面你想怎么收场？", listOf(
        OnboardingOption("有担当地道个歉，先破局", "goal_apology"),
        OnboardingOption("给彼此一个台阶，别再冷着", "goal_stepdown"),
        OnboardingOption("把根子问题说清，别再犯", "goal_resolve"),
        OnboardingOption("守住尊严，不卑不亢地处理", "goal_stand")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)

    private val q5e = OnboardingQuestion("Q5-E", 5, "E", "听完真话，你想怎么走？", listOf(
        OnboardingOption("还有点机会，帮我最后试一次", "goal_last_shot"),
        OnboardingOption("帮我判断她是不是彻底死心", "goal_truth"),
        OnboardingOption("拉住我，别让我卑微纠缠", "goal_stop_chase"),
        OnboardingOption("太痛苦了，帮我体面放下", "goal_heal")
    ), selectionMode = SelectionMode.MULTIPLE, maxSelections = 2)
}
