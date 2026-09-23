package com.lovebrain.app.model

/**
 * S2-06: 统一知识库 schema 版本治理。
 *
 * 使用单一 `.schema_version` 文件替代分散的 `.migrated_v2`、`.migrated_plan_v3` 等 marker 文件。
 * 迁移按 vN -> vN+1 顺序运行；每步有备份、幂等、校验与恢复。
 * PromptBuilder 不再调用 migrateIfNeeded——迁移只能在打开/升级知识库时运行。
 *
 * 向后兼容：如果 `.schema_version` 不存在，回退到 legacy marker 文件检测。
 * 迁移完成后写入 `.schema_version` 并删除所有 legacy marker。
 */
object KnowledgeSchemaVersion {
    /** 当前 App 支持的最新 schema 版本 */
    const val CURRENT: Int = 3

    /** 各版本说明 */
    val versionDescriptions = mapOf(
        1 to "初始版本（v1.3.1 及之前）",
        2 to "plan_v2：事项结构化 + 经验分离",
        3 to "plan_v3：纠正中心 + 持续意图"
    )

    /** 分散 marker 文件名 → 对应版本 */
    val legacyMarkers = mapOf(
        ".migrated_v2" to 2,
        ".migrated_plan_v3" to 3
    )

    /**
     * 检查给定版本是否需要迁移到当前版本。
     */
    fun needsMigration(fromVersion: Int): Boolean = fromVersion < CURRENT

    /**
     * 库的 schema 比本 App 支持的还新——通常是降级安装，或多设备同步带回的新版数据。
     *
     * 这种库只能读不能写：用 v3 的代码去写 v4 结构，会把新字段静默抹掉。
     * 旧实现只判断 needsMigration，遇到更高的版本号返回 false，
     * 于是"太新所以不该动"被当成了"够新所以不用动"，照常读写未来结构。
     */
    fun isBeyondSupported(fromVersion: Int): Boolean = fromVersion > CURRENT

    /**
     * 获取从当前版本到目标版本的迁移步骤列表。
     */
    fun migrationSteps(fromVersion: Int): List<Int> {
        if (fromVersion >= CURRENT) return emptyList()
        return (fromVersion + 1..CURRENT).toList()
    }
}

/**
 * S2-06: KB 名称/路径 value object——验证 canonical boundary。
 * 上层不能传任意路径，必须通过此 value object。
 */
@JvmInline
value class KbName(val value: String) {
    init {
        require(value.isNotBlank()) { "KB name must not be blank" }
        require(!value.contains("/") && !value.contains("\\")) { "KB name must not contain path separators" }
        require(!value.contains("..")) { "KB name must not contain path traversal" }
        require(value.length <= 100) { "KB name too long (max 100)" }
    }
}

/**
 * S2-06: KB 相对路径 value object——验证 canonical boundary。
 */
@JvmInline
value class KbRelativePath(val value: String) {
    init {
        require(value.isNotBlank()) { "Path must not be blank" }
        require(!value.contains("..")) { "Path must not contain path traversal" }
        // POSIX 绝对路径
        require(!value.startsWith("/")) { "Path must be relative" }
        // Windows 绝对路径：盘符（C:\、C:/）与 UNC（\server\share）。
        // 只挡 "/" 是不够的——File(dir, "C:\x") 在 Windows 上会直接跳出库目录。
        require(!(value.length >= 2 && value[1] == ':' && (value[0].isLetter()))) {
            "Path must not be a windows drive-absolute path"
        }
        require(!value.startsWith("\\\\")) { "Path must not be a UNC path" }
        // 反斜杠在 Windows 上就是分隔符，允许它等于允许 ..        require(!value.contains('\')) { "Path must not contain windows separators" }
    }
}
