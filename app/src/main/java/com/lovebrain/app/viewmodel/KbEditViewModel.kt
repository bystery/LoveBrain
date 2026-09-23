package com.lovebrain.app.viewmodel

import androidx.lifecycle.ViewModel
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs

/**
 * 知识库编辑页 ViewModel。
 *
 * 分层规则：KbEditActivity 只负责窗口标记、Intent 校验与 Compose 承载；
 * Repository / SecurePrefs 一律经此转发（ 职责拆分：此前 Activity 直接 inject 两者）。
 *
 * 保存契约与 [KnowledgeRepository.writeFileWithVersion] 一致：
 * 返回新版本号 = 落盘成功，返回 null = 版本冲突或库已删除，调用方必须保留草稿而不是静默覆盖。
 */
class KbEditViewModel(
    private val repo: KnowledgeRepository,
    private val securePrefs: SecurePrefs
) : ViewModel() {

    /** 上次编辑的文件（跨会话记忆）；null = 从未编辑过 */
    var lastFile: String?
        get() = securePrefs.lastKbEditFile
        set(value) {
            securePrefs.lastKbEditFile = value
        }

    /** 进入编辑页时补齐缺失结构（幂等） */
    suspend fun ensureMigrated(kbName: String) = repo.migrateIfNeeded(kbName)

    /** 读取内容 + 当前版本快照（SHA-256） */
    suspend fun read(kbName: String, relativePath: String): Pair<String, String> =
        repo.readFileWithVersion(kbName, relativePath)

    /**
     * 带版本校验的保存。
     *
     * @param expectedVersion 读取时拿到的版本快照；为 null 时退化为无校验覆盖并回传新内容哈希
     * @return 新版本号，null = 冲突（调用方保留草稿）
     */
    suspend fun save(
        kbName: String,
        relativePath: String,
        content: String,
        expectedVersion: String?
    ): String? {
        if (expectedVersion != null) {
            return repo.writeFileWithVersion(kbName, relativePath, content, expectedVersion)
        }
        repo.writeFile(kbName, relativePath, content)
        return repo.hashContent(content)
    }
}
