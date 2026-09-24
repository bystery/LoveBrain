package com.lovebrain.app.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份需要的最小存储视图——**故意不是** [KbStorageAccess]。
 *
 * 迁移器要读 schema 模板、要改阶段标签；备份只要两件事：看见根目录、把字节写下去。
 * 把两者塞进一个接口，就等于让备份拿到它用不着的写权限（ISP）。
 */
internal interface BackupStorage {
    /** knowledge/ 根目录 */
    val root: File

    /** 唯一落盘出口——真正的写边界仍住在仓库里；false 表示什么都没写 */
    fun guardedWrite(target: File, content: String): Boolean
}

/**
 * 自动备份与备份修剪（§5.3 从 `KnowledgeRepository` 拆出的第一格：archive/backup）。
 *
 * ## 拆的形状照抄 migration 那一次
 * 本类**不持锁、不自己造写路径、也不拿 CoroutineScope**：
 * `SingleOwnerContractTest` 的"领域/数据/VM 不许接收外部 scope，唯一例外是协调器"这条闸
 * 第一版就把我拦下来了（我原本把节流备份的 launch 也搬了进来）。所以"什么时候要备份"
 * 的调度留在仓库——它是这里唯一的启动者；本类只管**给定时刻该备份什么、留几份、删哪些**。
 * 文件动作要么直接对 `File` 做（备份是整目录复制，走不了 kbName+relativePath 那套），
 * 要么经 [BackupStorage] 回到仓库唯一的 `atomicWriteText`。
 *
 * ## 三条语义必须原样保住
 * 1. 两次备份间隔 ≥ 12 小时，靠 `.last_backup` 这个标记文件判，不靠内存；
 * 2. 隐藏目录（`.backup`、`.last_backup`、以 `.` 开头的库）不参与备份；
 * 3. 修剪与删除都按 [backupGroupKey] **精确分组**，绝不用 `startsWith(库名)`——
 *    否则删 `kb-a` 的备份会把 `kb-ab` 的一起删掉。不匹配时间戳后缀的目录名整名自成一组，
 *    也就是永不修剪，宁可多留不可误删。
 */
internal class KnowledgeBackupService(
    private val storage: BackupStorage,
    /** 两次备份的最小间隔 */
    private val intervalMs: Long = BACKUP_INTERVAL_MS,
    /** 每个知识库保留最近几份 */
    private val maxCount: Int = BACKUP_MAX_COUNT,
    /**
     * 墙钟。留成参数是为了能测"12 小时之内不该再备份一次"——
     * 没有它，这条只能靠真等或者把阈值改小，两种都测不到真的东西。
     */
    private val wallClockMs: () -> Long = System::currentTimeMillis
) {

    /**
     * 无锁核心：距上次备份超过 [intervalMs] 时，把所有知识库复制到 `.backup/`。
     *
     * **调用方必须已持有仓库那把锁**（与删除库互斥）。知识库主要是文本，短暂锁住文件更新
     * 的成本可接受。
     */
    fun backupIfNeededUnlocked() {
        val root = storage.root
        val marker = File(root, MARKER_FILE)
        val now = wallClockMs()
        val lastBackup = if (marker.exists()) runCatching { marker.readText().toLong() }.getOrDefault(0L) else 0L
        if (now - lastBackup < intervalMs) return

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val backupRoot = File(root, BACKUP_DIR_NAME)
        backupRoot.mkdirs()

        // 备份每个知识库
        root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.forEach { kbDir ->
                runCatching {
                    val backupDir = File(backupRoot, "${kbDir.name}_$timestamp")
                    if (backupDir.exists()) return@runCatching
                    backupDir.mkdirs()
                    kbDir.walkTopDown().forEach { src ->
                        val rel = src.relativeTo(kbDir).path
                        if (rel == ".") return@forEach
                        val dst = File(backupDir, rel)
                        if (src.isDirectory) dst.mkdirs() else src.copyTo(dst, overwrite = false)
                    }
                }
            }

        pruneBackups()

        // 更新备份时间标记——走仓库那道写门，本类不自建落盘出口
        storage.guardedWrite(marker, now.toString())
    }

    /** 修剪旧备份：每个知识库只保留最近 [maxCount] 份 */
    fun pruneBackups() {
        val backupRoot = File(storage.root, BACKUP_DIR_NAME)
        if (!backupRoot.exists()) return
        val groups = backupRoot.listFiles()?.filter { it.isDirectory }
            ?.groupBy { backupGroupKey(it.name) } ?: return
        groups.forEach { (_, backups) ->
            if (backups.size > maxCount) {
                backups.sortedByDescending { it.name }
                    .drop(maxCount)
                    .forEach { old -> runCatching { old.deleteRecursively() } }
            }
        }
    }

    /**
     * 删除指定知识库的全部备份。按 [backupGroupKey] 精确匹配，不用 `startsWith`——
     * 前缀匹配会让删 `kb-a` 顺手删掉 `kb-ab` 的备份。
     */
    fun deleteBackupsFor(kbName: String) {
        val backupRoot = File(storage.root, BACKUP_DIR_NAME)
        backupRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.filter { backupGroupKey(it.name) == kbName }
            ?.forEach { runCatching { it.deleteRecursively() } }
    }

    companion object {
        const val MARKER_FILE = ".last_backup"
        const val BACKUP_DIR_NAME = ".backup"
        private const val BACKUP_MAX_COUNT = 7                 // 每个知识库保留最近 7 份备份
        private const val BACKUP_INTERVAL_MS = 12 * 3600_000L  // 两次备份间隔 ≥ 12 小时

        // 备份目录名 = <库名>_<yyyyMMdd>_<HHmm>：锚定实际命名去时间戳还原库名作分组键；
        // 不匹配命名 = 整名为键（自成一组永不修剪，保守保留）
        private val BACKUP_TS_SUFFIX = Regex("_\\d{8}_\\d{4}$")

        internal fun backupGroupKey(name: String): String = BACKUP_TS_SUFFIX.replace(name, "")
    }
}
