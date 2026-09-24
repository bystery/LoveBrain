package com.lovebrain.app.data

import com.lovebrain.app.model.KnowledgeBase

/**
 * 仓库交给画像格用的**一次元数据写事务**。
 *
 * 只给一件事：在同一个事务里读改写 `kb.json`。不给路径、不给落盘、不给 Mutex——
 * 那三样各有唯一所有者（文档格的守门、仓库的 `writeFileUnlocked`、仓库的 `fileMutex`），
 * 画像格想碰就必须回到那几处。
 */
fun interface ProfileTx {
    /** 读改写 kb.json。false = 库缺失、schema 过新只读、或 JSON 不可解析，一个字节都没写 */
    fun updateMeta(transform: (KnowledgeBase) -> KnowledgeBase): Boolean
}

/**
 * 画像格能向仓库要的能力，六样，一样都不多。
 *
 * `metaOf` 而不是"给我文件内容自己解"：kb.json 的解码口径（坏 JSON 当没有）全仓只有一份，
 * 由仓库的 `readMetaUnlocked` 持有，画像格与 `getTurnCount` 用的是同一个函数。
 * 编码同理，走 [ProfileTx.updateMeta]，画像格里没有第二份 JSON 配置。
 */
interface ProfileStorage {
    fun note(message: String)

    fun timestamp(): String

    /** 过 canonical 守门的读：越界、非法、不存在都得到空串（旧布局有回退） */
    fun read(kbName: String, relativePath: String): String

    /** 唯一写链：仓库的 `writeFileUnlocked`，带着只读 schema 拒绝与备份节流 */
    fun writeUnlocked(kbName: String, relativePath: String, content: String)

    /** kb.json 的当前内容；读不到或解析不了给 null */
    fun metaOf(kbName: String): KnowledgeBase?

    /** 一次元数据写事务；锁与 schema 判定在仓库那一侧 */
    fun writeMetaTransaction(kbName: String, block: ProfileTx.() -> Unit)
}

/**
 * §5.3 拆出的第六格：**画像正文、内容修订、阶段、状态向量、温度文件里的阶段标签**。
 *
 * 这格守的承诺只有一条，但散在五处代码里：**画像的一切读写都以"同一个库的同一份文件"为准**。
 * 具体到本轮还掉的两件事：
 *
 * 1. `readVector` 看得见旧布局（`global/status.md` 回退），`writeVector` 却用裸路径读、
 *    看不见、于是静默不写；阶段标签那条用的又是公开读。三份代码、三种宽严。
 * 2. 阶段白名单（九阶段）的判定与"拒绝时说不说、怎么说"在四处各写一遍
 *    （非事务两处、strict 两处），改一处漏一处。现在只有 [normalizeStage] 一处。
 *
 * 本类不自持锁、不自己拼路径、不自己落盘、不自己配 JSON：这四件事分别由仓库的 `fileMutex`、
 * [KnowledgeDocumentStore.resolve]、`writeFileUnlocked` 那唯一的写链、以及仓库那一份 Json 配置负责。
 * `StorageBoundaryOwnershipTest` 的三条棘轮盯着这几件。
 *
 * ⚠ 唯一没收进来的是 `applyProfileUpdateAtomically`（160+ 行的跨文件事务，含备份与回滚）：
 * 它要的是"多文件一次事务 + 失败回滚"，那是另一格的能力，硬塞进这里等于把回滚语义
 * 也搬进画像格。它留下的四条裸路径由棘轮按条数登记（当前 4），账本里记着"已知未封"。
 */
internal class KnowledgeProfileStore(private val storage: ProfileStorage) {

    /** kb.json 里的当前阶段；读不到（库缺失、只读、JSON 坏、库名越界）给空串 */
    fun stageOf(kbName: String): String = storage.metaOf(kbName)?.stage ?: ""

    /**
     * 阶段白名单归一化（九阶段见 [com.lovebrain.app.domain.StageCatalog]）。
     *
     * 全仓只有这一处判"这个字符串能不能当阶段用"，也决定拒绝时留不留痕。
     * `opLabel` 只用来把日志指回调用方，不参与判定。
     */
    fun normalizeStage(raw: String, opLabel: String): String? {
        val normalized = com.lovebrain.app.domain.StageCatalog.normalize(raw)
        if (normalized == null) {
            storage.note("$opLabel 拒绝非白名单阶段：'$raw'（九阶段见 StageCatalog）")
        }
        return normalized
    }

    /**
     * 写阶段标签。true = 真的落盘。
     *
     * 空串按"没什么可写"直接返回，不记日志（与拆出前一致）；非白名单记一条再拒。
     */
    fun setStage(kbName: String, stage: String): Boolean {
        if (stage.isBlank()) return false
        val normalized = normalizeStage(stage, "updateStage") ?: return false
        var written = false
        storage.writeMetaTransaction(kbName) {
            written = updateMeta { kb -> kb.copy(stage = normalized, updatedAt = storage.timestamp()) }
        }
        return written
    }

    /** 关系画像正文：understand 目录下 me/her/warmth/style 四份正文拼成一段（顺序固定，参与身份比对） */
    fun profileText(kbName: String): String = buildString {
        for (name in PROFILE_SOURCE_FILES) {
            val text = storage.read(kbName, "understand/$name.md").trim()
            if (text.isNotBlank()) append(text).append('\n')
        }
    }

    /**
     * 知识内容修订号——对回复链路真正会读到的知识文件取内容指纹。
     *
     * 不能用 turnCount 近似：turnCount 只统计"提交过几轮"，
     * 同一 turnCount 可以对应完全不同的画像/场景/事项内容，手工编辑画像也不会改 turnCount。
     */
    fun contentRevision(kbName: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (path in REVISION_INPUT_PATHS) {
            val text = storage.read(kbName, path)
            // 路径也喂进摘要：这一条是"清单本身变了也不许撞号"的纵深防御。
            // ⚠ 别把它当已验收益：12 个条目各喂一次内容 + 一个 0x00，顺序固定，
            //   在这个形状下我构造不出"删掉路径盐会撞号"的反例（试过两次，见账本），
            //   所以它只进代码不进断言。
            digest.update(path.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(text.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    /** 读取 warmth 文件的五维状态向量（解析不到的维度默认 50） */
    fun vectorOf(kbName: String): Map<String, Int> {
        val warmth = storage.read(kbName, WARMTH_FILE)
        val result = mutableMapOf<String, Int>()
        for ((cn, en) in VECTOR_DIMS) {
            val v = Regex("$cn[^：:]*[：:]\\s*(\\d+)").find(warmth)?.groupValues?.get(1)?.toIntOrNull()
            if (v == null) storage.note("readVector 维度零匹配：$cn（文件长度=${warmth.length}）")
            result[en] = v ?: 50
        }
        return result
    }

    /**
     * 就地更新 warmth 文件的五维数值。true = 文件存在且有内容并走了写链。
     *
     * 读那一侧必须与 [vectorOf] 同一把尺（同一道守门、同一份旧布局回退），
     * 否则就会出现"读得到却静默不写"。`[^/\n]*` 兼容占位值（如"待评估"）并保留 "/100" 后缀。
     */
    fun setVector(kbName: String, values: Map<String, Int>): Boolean {
        var warmth = storage.read(kbName, WARMTH_FILE)
        if (warmth.isBlank()) return false
        for ((cn, en) in VECTOR_DIMS) {
            val v = values[en] ?: continue
            val dimRegex = Regex("($cn[^：:]*[：:]\\s*)[^/\\n]*")
            if (!dimRegex.containsMatchIn(warmth)) {
                storage.note("writeVector 维度零匹配：$cn（文件长度=${warmth.length}）")
            }
            warmth = warmth.replaceFirst(dimRegex, "$1$v")
        }
        storage.writeUnlocked(kbName, WARMTH_FILE, warmth)
        return true
    }

    /**
     * 就地更新 warmth 文件里的阶段标签行，保留旧值作为历史注释。
     *
     * 兼容 "- 阶段标签：" / "- 阶段：" / "阶段标签:" 等变体；找不到就在「当前状态」节首行插入，
     * 连那一节都没有时插到文件头。内容没变化就不写（免得备份节流白排一次）。
     */
    fun setWarmthStageLabel(kbName: String, newStage: String): Boolean {
        if (newStage.isBlank()) return false
        val stage = normalizeStage(newStage, "updateWarmthStageLabel") ?: return false
        val warmth = storage.read(kbName, WARMTH_FILE)
        if (warmth.isBlank()) return false
        val updated = rewriteStageLine(warmth, stage)
        if (updated == warmth) return false
        storage.writeUnlocked(kbName, WARMTH_FILE, updated)
        return true
    }

    /**
     * [setWarmthStageLabel] 与 strict 版共用的那一段文本改写。
     *
     * 抽出来的理由：strict 版（`applyProfileUpdateAtomically` 用）以前把这段插入/历史注释
     * 规则**整段抄了一遍**，改一处就会漏另一处。
     * 命中已有标签行且旧值与目标一致时原样返回（调用方据此决定要不要写）。
     */
    fun rewriteStageLine(warmth: String, stage: String): String {
        val match = STAGE_LINE_REGEX.find(warmth)
        if (match == null) {
            val header = "## 当前状态"
            val idx = warmth.indexOf(header)
            return if (idx >= 0) {
                warmth.substring(0, idx + header.length) + "\n- 阶段标签：$stage" + warmth.substring(idx + header.length)
            } else {
                "- 阶段标签：$stage\n" + warmth
            }
        }
        val oldValue = match.groupValues[2].trim()
        // 提取旧阶段名（去掉已有的历史注释部分）
        val oldStage = oldValue.split("；").firstOrNull()?.trim() ?: oldValue
        val newValue = if (oldStage.isNotBlank() && oldStage != stage) {
            "$stage；过去曾经是$oldStage"
        } else {
            stage
        }
        return warmth.replaceFirst(STAGE_LINE_REGEX, "${match.groupValues[1]}$newValue")
    }

    companion object {
        /** 画像与向量共用的那个文件；读写两侧都取这个常量，不再各写字面量 */
        const val WARMTH_FILE = "understand/warmth.md"

        /** 关系画像正文的四份来源（顺序即拼接顺序） */
        val PROFILE_SOURCE_FILES = listOf("me", "her", "warmth", "style")

        /** 中文维度名 → 英文键。只有这一份，读写两侧共用 */
        val VECTOR_DIMS = listOf(
            "亲密度" to "intimacy", "信任度" to "trust", "承诺度" to "commitment",
            "激情" to "passion", "安全感" to "security"
        )

        /** 阶段标签行的认法（含"阶段："变体），捕获组 1 是前缀、2 是旧值 */
        val STAGE_LINE_REGEX = Regex("(-\\s*阶段(?:标签)?[：:])([^\n]*)")

        /** 回复链路实际读取的知识文件——内容变化即构成一次新的可冻结修订 */
        val REVISION_INPUT_PATHS = listOf(
            "understand/me.md", "understand/her.md", "understand/warmth.md", "understand/style.md",
            "moment/topic.md", "moment/scene.md", "moment/recent.md", "moment/plan.md",
            "memory/lessons.md", "memory/raw_topic.md", "memory/raw_scene.md", "memory/raw_chat.md"
        )
    }
}
