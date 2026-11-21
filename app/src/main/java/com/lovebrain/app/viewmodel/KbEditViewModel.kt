package com.lovebrain.app.viewmodel

import androidx.lifecycle.ViewModel
import com.lovebrain.app.domain.port.KnowledgeDocumentPort
import com.lovebrain.app.domain.port.SettingsStorePort

/**
 * 知识库编辑页 ViewModel。
 *
 * 分层规则：KbEditActivity 只负责窗口标记、Intent 校验与 Compose 承载；
 * Repository / SecurePrefs 一律经此转发（ 职责拆分：此前 Activity 直接 inject 两者）。
 *
 * 这里注入的是端口而不是具体仓库：编辑页只需要"一篇文档的版本化读写"这一族，
 * 所以它拿不到删库、切库这类能力（见 [KnowledgeDocumentPort] 那份 KDoc）。
 *
 * 保存契约与 [KnowledgeDocumentPort.writeFileWithVersion] 一致：
 * 返回新版本号 = 落盘成功，返回 null = 版本冲突、库已删除、写被拒或落盘失败，
 * 调用方必须保留草稿并报失败，而不是静默覆盖或报"已保存"。
 */
class KbEditViewModel(
    private val repo: KnowledgeDocumentPort,
    private val securePrefs: SettingsStorePort
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
     * @param expectedVersion 读取时拿到的版本快照；为 null（整页那次读没拿到版本）时先补读一次当前版本，
     *                        再走同一条版本化写——端口那口不带校验的写没有失败通道，写完只能自己编一个
     *                        新版本号，写被拒（只读库/路径守门/落盘失败）也会被当成成功
     * @return 新版本号；null = 冲突、库已不在、写被拒或落盘失败（调用方保留草稿并报失败）
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
        // 无版本分支同样要听写核的回答：先取当前版本，再让版本化写这一口回 true/false（null = 没落成）。
        val current = repo.readFileWithVersion(kbName, relativePath).second
        return repo.writeFileWithVersion(kbName, relativePath, content, current)
    }
}
