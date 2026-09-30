package com.lovebrain.app.data

/**
 * 仓库交给话题文本格用的能力，四样。
 *
 * 与谈心日志格同一纪律：不给 Mutex、不给 File、不给落盘实现。
 * `read` 过 canonical 守门，`writeUnlocked` 回到仓库唯一写链。
 */
internal interface TopicTextStorage {
    fun note(message: String)

    fun kbExists(kbName: String): Boolean

    /** 过 canonical 守门的读：越界、非法、不存在都得到空串 */
    fun read(kbName: String, relativePath: String): String

    /** 锁区内写入核心（调用方已持有 fileMutex）；只读 schema 拒绝在仓库那一侧 */
    fun writeUnlocked(kbName: String, relativePath: String, content: String)
}

/**
 * §5.3 后续拆出的话题文本格：话题标签读写 + 计划事项解析 + 话题年龄。
 *
 * 搬出来的理由是 `readPlanActive` 那 22 行解析逻辑（跨行注释块跟踪、分区判定、
 * 裸说明行防御）以前混在仓库里，只能连临时目录一起测；现在解析规则有一个名字，
 * 话题标签的读法（[KbTextOps.topicLabel]）与写法（[KbTextOps.topicLine]）各有一个所有者。
 *
 * 本类不自持锁、不拼路径、不落盘：这三件事分别由仓库的 fileMutex、
 * [KnowledgeDocumentStore.resolve]、以及 `atomicWriteText` 那唯一的写链负责。
 */
internal class KnowledgeTopicTextService(private val storage: TopicTextStorage) {

    /** 读取当前话题标签。读法在 [KbTextOps.topicLabel]，与四处写侧同一个所有者 */
    fun currentTopic(kbName: String): String =
        KbTextOps.topicLabel(storage.read(kbName, TOPIC_FILE))

    /** 设置当前话题标签。调用方必须已持有 fileMutex；目标 KB 已删除时 no-op */
    fun setCurrentTopic(kbName: String, topicLabel: String) {
        if (!storage.kbExists(kbName)) {
            storage.note("setCurrentTopic skipped: kb no longer exists")
            return
        }
        val time = com.lovebrain.app.util.TimeFmt.now()
        storage.writeUnlocked(kbName, TOPIC_FILE, KbTextOps.topicLine(time, topicLabel))
    }

    /** 读取 plan.md「## 进行中」分区的事项行（注入 prompt；已结束不注入） */
    fun readPlanActive(kbName: String): String {
        val content = storage.read(kbName, "moment/plan.md")
        val sb = StringBuilder()
        var inActive = false
        var inComment = false
        for (line in content.lines()) {
            val t = line.trim()
            // 跨行注释块跟踪（注释里的格式/示例绝不注入）
            if (inComment) {
                if (t.contains("-->")) inComment = false
                continue
            }
            when {
                t.startsWith("<!--") -> if (!t.contains("-->")) inComment = true
                t.startsWith("## 进行中") -> inActive = true
                t.startsWith("##") -> inActive = false
                // 旧数据防御：裸的"格式/示例"说明行不当事项注入
                t.startsWith("格式") || t.startsWith("示例") -> Unit
                inActive && t.contains("|") -> sb.append(t).append("\n")
            }
        }
        return sb.toString().trim()
    }

    /** 获取当前话题的年龄（小时），用于时间衰减判断 */
    fun topicAgeHours(kbName: String): Int {
        val content = storage.read(kbName, TOPIC_FILE)
        val match = Regex("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})]").find(content) ?: return 99
        val updated = com.lovebrain.app.util.TimeFmt.parse(match.groupValues[1])
        if (updated <= 0) return 99
        return ((System.currentTimeMillis() - updated) / 3600_000).toInt()
    }

    companion object {
        private const val TOPIC_FILE = "moment/topic.md"
    }
}
