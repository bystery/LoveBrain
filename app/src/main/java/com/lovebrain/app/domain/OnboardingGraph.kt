package com.lovebrain.app.domain

import kotlinx.serialization.Serializable

/**
 * v4.0 动态自适应问卷机制：5 步状态机，每条分支步长恒定 5 题。
 *
 * 设计来源：v4.0 设计文档（timu.md），题目文案逐字搬入不修改。
 * "推拉""试探""框架"等措辞是项目核心产品语言（format.md/core.md/counseling.md/suggest.md
 * 系统使用），不等于 PUA，按原文使用。
 *
 * 结构：Q1 入口题（5 选项 A-E）→ Q1 答案确定分支 → Q2-Q5 跟随同一分支。
 * 红线：Q3-E 选 D 或 Q4-E 选 A → Q5-E 隐藏 A/B 选项，只显示 C(止损)/D(疗愈)。
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

    fun build(answers: Map<Int, Int>, myName: String, herName: String): OnboardingSchema {
        val branch = OnboardingStateMachine.branchFromQ1(answers[1] ?: 0)
        val stage = OnboardingStateMachine.stageFromBranch(branch)
        val redline = OnboardingStateMachine.isRedlineTriggered(answers, branch)
        val directive = if (redline) "SELF_REBUILD_ONLY" else "NORMAL_ASSIST"

        val path = buildPath(answers, branch)
        val tags = collectTags(answers, branch)
        val profile = buildProfile(answers, branch)

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

    private fun buildProfile(answers: Map<Int, Int>, branch: String): OnboardingSchema.Profile {
        val q2 = OnboardingBank.question(2, branch)
        val q3 = OnboardingBank.question(3, branch)
        val q4 = OnboardingBank.question(4, branch)
        val q5 = OnboardingBank.question(5, branch)

        return OnboardingSchema.Profile(
            interpersonal_context = q2.options.getOrNull(answers[2] ?: 0)?.text ?: "",
            counterpart_feedback = q3.options.getOrNull(answers[3] ?: 0)?.text ?: "",
            core_dilemma = q4.options.getOrNull(answers[4] ?: 0)?.text ?: "",
            user_intent = q5.options.getOrNull(answers[5] ?: 0)?.text ?: ""
        )
    }
}

// ═══════════ 题库（v4.0 设计文档逐字搬入） ═══════════

object OnboardingBank {

    /** Q1 入口题 */
    val q1 = OnboardingQuestion(
        id = "Q1",
        step = 1,
        branch = "",
        title = "先把脉，你们俩目前整体走到哪一步了？",
        options = listOf(
            OnboardingOption("刚认识 / 刚加上", "stage_new"),
            OnboardingOption("互相接触 / 暧昧拉扯", "stage_chasing"),
            OnboardingOption("正式恋爱中", "stage_dating"),
            OnboardingOption("闹矛盾 / 僵住了", "stage_conflict"),
            OnboardingOption("处于分手边缘 / 已分开", "stage_breakup")
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

    private val q2a = OnboardingQuestion("Q2-A", 2, "A", "现实交集底子如何？", listOf(
        OnboardingOption("同事/同学/同圈子（抬头不见低头见）", "ctx_circle"),
        OnboardingOption("朋友聚会/活动认识（互相有基本认知）", "ctx_social"),
        OnboardingOption("纯网聊/匹配软件（彼此一张白纸）", "ctx_online"),
        OnboardingOption("偶遇搭讪/长辈牵线（几乎完全陌生）", "ctx_blind")
    ))

    private val q2b = OnboardingQuestion("Q2-B", 2, "B", "目前你们的互动节奏是？", listOf(
        OnboardingOption("刚升温不久（认识 1 个月内，势头挺好）", "pace_early"),
        OnboardingOption("拉扯太久了（超过 1~2 个月，迟迟无法突破）", "pace_stuck"),
        OnboardingOption("忽冷忽热（今天很能聊，过几天又没影）", "pace_wave"),
        OnboardingOption("基本全靠我主动硬撑（对方很少主动找我）", "pace_passive")
    ))

    private val q2c = OnboardingQuestion("Q2-C", 2, "C", "在一起大概多久了？", listOf(
        OnboardingOption("3 个月以内（还在甜蜜热恋期）", "dur_honeymoon"),
        OnboardingOption("3 个月~1 年（开始出现习惯摩擦）", "dur_adjust"),
        OnboardingOption("1 年以上（平稳但也趋于平淡）", "dur_stable"),
        OnboardingOption("长期异地/聚少离多", "dur_distance")
    ))

    private val q2d = OnboardingQuestion("Q2-D", 2, "D", "这次矛盾的起因主要是？", listOf(
        OnboardingOption("鸡毛蒜皮琐事吵架，话赶话上了头", "cause_temper"),
        OnboardingOption("触碰到底线/信任危机（如异性边界、撒谎）", "cause_trust"),
        OnboardingOption("长期积怨总爆发（觉得某一方一直在忍）", "cause_accumulated"),
        OnboardingOption("对方单方面闹情绪，问了也不说", "cause_unclear")
    ))

    private val q2e = OnboardingQuestion("Q2-E", 2, "E", "目前具体走到哪一步了？", listOf(
        OnboardingOption("对方正式提了分手，还没几天", "brk_fresh"),
        OnboardingOption("没挑明分手，但已经形同陌路断联了", "brk_ghost"),
        OnboardingOption("我冲动提了分手，现在后悔想收回", "brk_regret"),
        OnboardingOption("已经分开一段时间，但心里一直放不下", "brk_lingering")
    ))

    // ─── Step 3: 对方近期反应与态度 ───

    private val q3a = OnboardingQuestion("Q3-A", 3, "A", "你发消息过去，她通常怎么接？", listOf(
        OnboardingOption("态度热情，会主动反问和扩句", "att_warm"),
        OnboardingOption("客气礼貌，有问必答，但字数很少", "att_polite"),
        OnboardingOption("回得极慢，隔半天回一两句", "att_slow"),
        OnboardingOption("纯躺列，打过招呼后几乎没说话", "att_silent")
    ))

    private val q3b = OnboardingQuestion("Q3-B", 3, "B", "除了文字打字，你们有过更深的连接吗？", listOf(
        OnboardingOption("会深夜长聊心事，分享过脆弱情绪", "depth_deep"),
        OnboardingOption("经常单独出来吃饭看电影，有肢体接触", "depth_meet"),
        OnboardingOption("停留在日常开玩笑，不敢触及走心话题", "depth_surface"),
        OnboardingOption("一旦约线下就找借口，只愿线上聊", "depth_online")
    ))

    private val q3c = OnboardingQuestion("Q3-C", 3, "C", "日常沟通里最常出现的毛病是？", listOf(
        OnboardingOption("她抛情绪我讲大道理，越聊越火大", "comm_logic"),
        OnboardingOption("报备式打卡，除了\u201c早安晚安吃了没\u201d无话可说", "comm_dry"),
        OnboardingOption("她习惯冷战/闷着，一定要别人猜心思", "comm_cold"),
        OnboardingOption("容易翻旧账，一件小事能扯出一堆历史问题", "comm_history")
    ))

    private val q3d = OnboardingQuestion("Q3-D", 3, "D", "目前的僵局持续多久了？", listOf(
        OnboardingOption("几小时以内，情绪还在风头上", "freeze_hours"),
        OnboardingOption("1~2 天，谁都没主动找谁", "freeze_short"),
        OnboardingOption("超过 3 天甚至一周了", "freeze_long"),
        OnboardingOption("对方虽然回消息，但字字带刺/冷嘲热讽", "freeze_cold")
    ))

    private val q3e = OnboardingQuestion("Q3-E", 3, "E", "目前你和对方还能说得上话吗？", listOf(
        OnboardingOption("还能正常回复，语气客气克制", "reach_ok"),
        OnboardingOption("回得很敷衍，甚至已读不回", "reach_ignore"),
        OnboardingOption("删除了部分联系方式，仅留个别渠道", "reach_part"),
        OnboardingOption("电话微信全部被拉黑", "reach_blocked", isRedline = true)
    ))

    // ─── Step 4: 核心症结与痛点（红线拦截层） ───

    private val q4a = OnboardingQuestion("Q4-A", 4, "A", "现阶段交流，你最大的卡点在？", listOf(
        OnboardingOption("找不到自然的话头，怕被当成查户口", "blk_open"),
        OnboardingOption("不会接梗接话，容易把气氛聊死", "blk_dry"),
        OnboardingOption("怕太主动显得讨好，太被动又变空气", "blk_balance"),
        OnboardingOption("摸不清对方兴趣，找不到共鸣话题", "blk_vibe")
    ))

    private val q4b = OnboardingQuestion("Q4-B", 4, "B", "你觉得阻碍你们跨进下一步的阻力是？", listOf(
        OnboardingOption("猜不透她是真喜欢我，还是单纯享受被追", "blk_heart"),
        OnboardingOption("关系到了天花板，不知道怎么捅破窗户纸", "blk_confirm"),
        OnboardingOption("感觉自己陷进去了，患得患失失去主见", "blk_anxious"),
        OnboardingOption("怕挑明之后如果被拒绝，连朋友都做不成", "blk_fear")
    ))

    private val q4c = OnboardingQuestion("Q4-C", 4, "C", "你在这段关系里最常感到的情绪内耗是？", listOf(
        OnboardingOption("委屈，感觉自己一直在妥协和讨好", "blk_pleasing"),
        OnboardingOption("疲惫，感觉一直在猜对方的心思和脸色", "blk_tired"),
        OnboardingOption("焦虑，总觉得对方没有以前那么在乎自己了", "blk_insecure"),
        OnboardingOption("烦躁，沟通成本太高，宁愿自己待着", "blk_annoyed")
    ))

    private val q4d = OnboardingQuestion("Q4-D", 4, "D", "冷静下来看，你觉得这次矛盾的主要症结是？", listOf(
        OnboardingOption("确实是我有错在先，但不知道怎么挽回面子破冰", "blk_my_fault"),
        OnboardingOption("对方原则性过分，我不想无底线妥协认错", "blk_boundary"),
        OnboardingOption("纯属情绪失控发泄，需要双方给个自然台阶", "blk_temper"),
        OnboardingOption("核心诉求没谈拢，即便这次哄好下次还得吵", "blk_deep")
    ))

    private val q4e = OnboardingQuestion("Q4-E", 4, "E", "对方是否有过极度坚决的表态？", listOf(
        OnboardingOption("明确说了\u201c别再纠缠我/不可能了/放过我\u201d", "blk_explicit_refusal", isRedline = true),
        OnboardingOption("说了\u201c彼此冷静一段时间\u201d，没把话说绝", "blk_cool_down"),
        OnboardingOption("还在数落指责我过去的缺点和不足", "blk_venting"),
        OnboardingOption("平静客气地祝福彼此，保持礼貌距离", "blk_calm_exit")
    ))

    // ─── Step 5: 破局诉求与目标 ───

    private val q5a = OnboardingQuestion("Q5-A", 5, "A", "这次找军师，你最想达成的目的是？", listOf(
        OnboardingOption("帮我想几个自然、不尴尬的破冰开场白", "goal_icebreak"),
        OnboardingOption("帮我润色平时的接话，显得更有趣一些", "goal_polish"),
        OnboardingOption("帮我分析她的话，看她到底愿不愿跟我聊", "goal_judge"),
        OnboardingOption("帮我找合适时机，自然促成第一次线下见面", "goal_invite")
    ))

    private val q5b = OnboardingQuestion("Q5-B", 5, "B", "你最希望军师在哪个环节助攻？", listOf(
        OnboardingOption("帮我适度推拉，找回不迎合、有主见的节奏", "goal_frame"),
        OnboardingOption("制造带点暧昧张力的话术，试探对方心意", "goal_flirt"),
        OnboardingOption("设计一次不刻意的邀约，推进线下实质进展", "goal_escalate"),
        OnboardingOption("教我如何真诚且有底线地确认恋爱关系", "goal_confirm")
    ))

    private val q5c = OnboardingQuestion("Q5-C", 5, "C", "现阶段你最希望军师协助你做好什么？", listOf(
        OnboardingOption("接住对方的情绪爆点，避免误解与争吵", "goal_soothe"),
        OnboardingOption("找回日常新鲜感，设计走心的互动交流", "goal_fresh"),
        OnboardingOption("坚定清晰地表达自己的边界，拒绝单方面迎合", "goal_boundary"),
        OnboardingOption("帮我客观梳理相处中的问题，停止无谓内耗", "goal_evaluate")
    ))

    private val q5d = OnboardingQuestion("Q5-D", 5, "D", "这次局面，你最想怎么收场？", listOf(
        OnboardingOption("真诚且有担当的道歉沟通，先把局破开", "goal_apology"),
        OnboardingOption("给双方一个体面自然的台阶，别再继续冷着", "goal_stepdown"),
        OnboardingOption("就事论事讲清楚问题根源，杜绝下次再犯", "goal_resolve"),
        OnboardingOption("帮我守住尊严底线，不卑不亢地处理对立", "goal_stand")
    ))

    private val q5e = OnboardingQuestion("Q5-E", 5, "E", "听完真话，你现在最想怎么做？", listOf(
        OnboardingOption("还有一丝机会，帮我策划最后一次真诚体面的沟通", "goal_last_shot"),
        OnboardingOption("帮我判断她现在的真实心理，到底是不是彻底死心", "goal_truth"),
        OnboardingOption("帮我稳住心态，阻止我做出卑微纠缠的掉价行为", "goal_stop_chase"),
        OnboardingOption("我很痛苦内耗，需要帮我梳理情绪，体面放下走出泥潭", "goal_heal")
    ))
}
