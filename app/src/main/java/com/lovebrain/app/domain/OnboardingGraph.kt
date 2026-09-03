package com.lovebrain.app.domain

import kotlinx.serialization.Serializable

/**
 * v4.0 动态自适应问卷机制：5 步状态机，每条分支步长恒定 5 题。
 *
 * 设计来源：v4.0 设计文档（timu.md）→ v4.1 口语化改写（R1）+ 自定义输入（R2）。
 * "推拉""试探""框架"等措辞是项目核心产品语言（format.md/core.md/counseling.md/suggest.md
 * 系统使用），不等于 PUA，按原文使用。
 *
 * 结构：Q1 入口题（5 选项 A-E）→ Q1 答案确定分支 → Q2-Q5 跟随同一分支。
 * 红线：Q3-E 选 D 或 Q4-E 选 A → Q5-E 隐藏 A/B 选项，只显示 C(止损)/D(疗愈)。
 *
 * v4.1 增量：
 * R1 题目文案口语化改写，tag/isRedline/结构与 v4.0 冻结一致。
 * R2 Q2-Q5 支持自定义输入：answers[step] = -1 哨兵值表示「我自己说」，
 *    文本由 customTexts[step] 提供；-1 不参与 tag 收集，也不参与客户端红线
 *    判定（自定义文本的红线信号交给 AI 引擎按 onboarding.md 规则识别）。
 */

// ═══════════ 数据模型 ═══════════

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
    val options: List<OnboardingOption>
)

// ═══════════ JSON Schema（交付引擎） ═══════════

@Serializable
data class OnboardingSchema(
    val stage: String,
    val meta: Meta,
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
     * 检查红线是否触发：Q3 分支 E 选了 D(index=3)，或 Q4 分支 E 选了 A(index=0)。
     * answers: step(1-5) → optionIndex(0-based)
     */
    fun isRedlineTriggered(answers: Map<Int, Int>, branch: String): Boolean {
        if (branch != "E") return false
        return answers[3] == 3 || answers[4] == 0
    }

    /** Stage5 红线触发时，哪些选项被隐藏（A=0 和 B=1） */
    fun hiddenOptionIndices(redline: Boolean): Set<Int> {
        if (!redline) return emptySet()
        return setOf(0, 1)
    }
}

// ═══════════ Schema 构建器 ═══════════

object OnboardingSchemaBuilder {

    fun build(
        answers: Map<Int, Int>,
        myName: String,
        herName: String,
        customTexts: Map<Int, String> = emptyMap()
    ): OnboardingSchema {
        val branch = OnboardingStateMachine.branchFromQ1(answers[1] ?: 0)
        val stage = OnboardingStateMachine.stageFromBranch(branch)
        val redline = OnboardingStateMachine.isRedlineTriggered(answers, branch)
        val directive = if (redline) "SELF_REBUILD_ONLY" else "NORMAL_ASSIST"

        val path = buildPath(answers, branch)
        val tags = collectTags(answers, branch)
        val profile = buildProfile(answers, branch, customTexts)

        return OnboardingSchema(
            stage = stage,
            meta = OnboardingSchema.Meta(total_answered = answers.size, path = path),
            tags = tags,
            profile = profile,
            redline_triggered = redline,
            system_directive = directive
        )
    }

    private fun buildPath(answers: Map<Int, Int>, branch: String): List<String> {
        val path = mutableListOf<String>()
        path.add("Q1")
        for (step in 2..5) {
            val qid = "Q${step}-${branch}"
            path.add(qid)
        }
        return path
    }

    private fun collectTags(answers: Map<Int, Int>, branch: String): List<String> {
        val tags = mutableListOf<String>()
        for (step in 2..5) {
            val idx = answers[step] ?: continue
            val question = OnboardingBank.question(step, branch)
            val option = question.options.getOrNull(idx) ?: continue
            tags.add(option.tag)
        }
        return tags
    }

    private fun buildProfile(
        answers: Map<Int, Int>,
        branch: String,
        customTexts: Map<Int, String> = emptyMap()
    ): OnboardingSchema.Profile {
        // idx == -1 为 R2 自定义输入哨兵：取 customTexts 文本；否则取固定选项文案
        fun field(step: Int): String {
            val idx = answers[step] ?: 0
            if (idx == -1) return customTexts[step]?.trim().orEmpty()
            return OnboardingBank.question(step, branch).options.getOrNull(idx)?.text ?: ""
        }

        return OnboardingSchema.Profile(
            interpersonal_context = field(2),
            counterpart_feedback = field(3),
            core_dilemma = field(4),
            user_intent = field(5)
        )
    }
}

// ═══════════ 题库（v4.1 口语化改写；tag/isRedline/结构与 v4.0 一致） ═══════════

object OnboardingBank {

    /** Q1 入口题 */
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
        )
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
    ))

    private val q2b = OnboardingQuestion("Q2-B", 2, "B", "最近你俩什么节奏？", listOf(
        OnboardingOption("刚升温，认识不到 1 个月", "pace_early"),
        OnboardingOption("拉扯 1~2 个月，一直没突破", "pace_stuck"),
        OnboardingOption("忽冷忽热，时好时坏", "pace_wave"),
        OnboardingOption("基本我主动找她，她很少主动", "pace_passive")
    ))

    private val q2c = OnboardingQuestion("Q2-C", 2, "C", "你俩在一起多久了？", listOf(
        OnboardingOption("不到 3 个月，还热乎着", "dur_honeymoon"),
        OnboardingOption("3 个月到 1 年，开始磨合", "dur_adjust"),
        OnboardingOption("1 年以上，有点平淡了", "dur_stable"),
        OnboardingOption("长期异地，聚少离多", "dur_distance")
    ))

    private val q2d = OnboardingQuestion("Q2-D", 2, "D", "这次为啥吵起来的？", listOf(
        OnboardingOption("小事吵的，话赶话上头", "cause_temper"),
        OnboardingOption("碰了底线：撒谎/异性没边界", "cause_trust"),
        OnboardingOption("积怨太久，这次总爆发", "cause_accumulated"),
        OnboardingOption("她闹情绪，问也问不出来", "cause_unclear")
    ))

    private val q2e = OnboardingQuestion("Q2-E", 2, "E", "现在具体到哪一步了？", listOf(
        OnboardingOption("她正式提了分手，没几天", "brk_fresh"),
        OnboardingOption("没明说，但已经断联了", "brk_ghost"),
        OnboardingOption("我冲动提的分手，后悔了", "brk_regret"),
        OnboardingOption("分了段时间，还是放不下", "brk_lingering")
    ))

    // ─── Step 3: 对方近期反应与态度 ───

    private val q3a = OnboardingQuestion("Q3-A", 3, "A", "你发消息过去，她一般怎么回？", listOf(
        OnboardingOption("挺热情，还会主动找话题", "att_warm"),
        OnboardingOption("客气有回，但就几个字", "att_polite"),
        OnboardingOption("回得超慢，半天蹦一两句", "att_slow"),
        OnboardingOption("纯躺列，基本不说话", "att_silent")
    ))

    private val q3b = OnboardingQuestion("Q3-B", 3, "B", "聊天之外，你们更近一步了吗？", listOf(
        OnboardingOption("深夜聊过心事，交过底", "depth_deep"),
        OnboardingOption("常单独约会，有过肢体接触", "depth_meet"),
        OnboardingOption("只停留在玩笑，没敢走心", "depth_surface"),
        OnboardingOption("一约线下就躲，只想线上聊", "depth_online")
    ))

    private val q3c = OnboardingQuestion("Q3-C", 3, "C", "你俩聊天最容易出啥问题？", listOf(
        OnboardingOption("她发泄我讲理，越聊越炸", "comm_logic"),
        OnboardingOption("只会早安晚安吃了没", "comm_dry"),
        OnboardingOption("她爱冷战憋着，让你猜", "comm_cold"),
        OnboardingOption("爱翻旧账，小事扯出一堆", "comm_history")
    ))

    private val q3d = OnboardingQuestion("Q3-D", 3, "D", "这回僵多久了？", listOf(
        OnboardingOption("几个小时，气还没消", "freeze_hours"),
        OnboardingOption("1~2 天，谁也没找谁", "freeze_short"),
        OnboardingOption("超过 3 天，甚至一周了", "freeze_long"),
        OnboardingOption("她回是回，但句句带刺", "freeze_cold")
    ))

    private val q3e = OnboardingQuestion("Q3-E", 3, "E", "你们现在还说得上话吗？", listOf(
        OnboardingOption("还正常回，语气挺客气", "reach_ok"),
        OnboardingOption("回得敷衍，甚至已读不回", "reach_ignore"),
        OnboardingOption("删了部分联系方式，还留一两个", "reach_part"),
        OnboardingOption("电话微信全被拉黑", "reach_blocked", isRedline = true)
    ))

    // ─── Step 4: 核心症结与痛点（红线拦截层） ───

    private val q4a = OnboardingQuestion("Q4-A", 4, "A", "现在聊天你最卡哪儿？", listOf(
        OnboardingOption("开不了话头，怕像查户口", "blk_open"),
        OnboardingOption("接不住梗，容易把天聊死", "blk_dry"),
        OnboardingOption("主动怕显得讨好，被动没存在感", "blk_balance"),
        OnboardingOption("摸不清她喜欢啥，聊不到一块", "blk_vibe")
    ))

    private val q4b = OnboardingQuestion("Q4-B", 4, "B", "卡在哪儿，没法更进一步？", listOf(
        OnboardingOption("猜不透她是喜欢我，还是享受被追", "blk_heart"),
        OnboardingOption("到天花板了，不知道咋捅破", "blk_confirm"),
        OnboardingOption("陷进去了，患得患失没了主见", "blk_anxious"),
        OnboardingOption("怕表白被拒，朋友都没得做", "blk_fear")
    ))

    private val q4c = OnboardingQuestion("Q4-C", 4, "C", "这段感情里，最磨你的是啥？", listOf(
        OnboardingOption("委屈，一直在妥协讨好", "blk_pleasing"),
        OnboardingOption("累，一直猜她心思脸色", "blk_tired"),
        OnboardingOption("焦虑，觉得她没以前在乎我", "blk_insecure"),
        OnboardingOption("烦，沟通太累，宁愿自己待着", "blk_annoyed")
    ))

    private val q4d = OnboardingQuestion("Q4-D", 4, "D", "冷静想想，这回到底为啥僵？", listOf(
        OnboardingOption("我错在先，但拉不下脸破冰", "blk_my_fault"),
        OnboardingOption("她太过了，我不想无底线认错", "blk_boundary"),
        OnboardingOption("情绪上头，就差个台阶", "blk_temper"),
        OnboardingOption("根子问题没解决，哄好还得吵", "blk_deep")
    ))

    private val q4e = OnboardingQuestion("Q4-E", 4, "E", "她说过特别绝的话吗？", listOf(
        OnboardingOption("明确说过\u201c不可能/放过我\u201d", "blk_explicit_refusal", isRedline = true),
        OnboardingOption("说\u201c先冷静下\u201d，没说死", "blk_cool_down"),
        OnboardingOption("还在数落我以前的毛病", "blk_venting"),
        OnboardingOption("客客气气祝我好，保持距离", "blk_calm_exit")
    ))

    // ─── Step 5: 破局诉求与目标 ───

    private val q5a = OnboardingQuestion("Q5-A", 5, "A", "这回找军师，你最想要啥？", listOf(
        OnboardingOption("给我几个不尬的开场白", "goal_icebreak"),
        OnboardingOption("帮我接话更有趣一点", "goal_polish"),
        OnboardingOption("帮我分析她到底想不想聊", "goal_judge"),
        OnboardingOption("找机会约出第一次见面", "goal_invite")
    ))

    private val q5b = OnboardingQuestion("Q5-B", 5, "B", "最想让军师在哪步搭把手？", listOf(
        OnboardingOption("帮我推拉起来，别一味迎合", "goal_frame"),
        OnboardingOption("来点暧昧张力的话，试探她", "goal_flirt"),
        OnboardingOption("设计个自然的邀约，推进线下", "goal_escalate"),
        OnboardingOption("教我有底线地把关系挑明", "goal_confirm")
    ))

    private val q5c = OnboardingQuestion("Q5-C", 5, "C", "现在最想让军师帮你稳住啥？", listOf(
        OnboardingOption("接住她的情绪雷点，别吵起来", "goal_soothe"),
        OnboardingOption("整点走心互动，找回新鲜感", "goal_fresh"),
        OnboardingOption("帮我立住边界，不再单向迎合", "goal_boundary"),
        OnboardingOption("帮我理清问题，别再内耗", "goal_evaluate")
    ))

    private val q5d = OnboardingQuestion("Q5-D", 5, "D", "这局面你想怎么收场？", listOf(
        OnboardingOption("有担当地道个歉，先破局", "goal_apology"),
        OnboardingOption("给彼此个台阶，别再冷着", "goal_stepdown"),
        OnboardingOption("把根子问题说清，别再犯", "goal_resolve"),
        OnboardingOption("守住尊严，不卑不亢地处理", "goal_stand")
    ))

    private val q5e = OnboardingQuestion("Q5-E", 5, "E", "听完真话，你想怎么走？", listOf(
        OnboardingOption("还有点机会，帮我最后试一次", "goal_last_shot"),
        OnboardingOption("帮我判断她是不是彻底死心", "goal_truth"),
        OnboardingOption("拉住我，别让我卑微纠缠", "goal_stop_chase"),
        OnboardingOption("太痛苦了，帮我体面放下", "goal_heal")
    ))
}
