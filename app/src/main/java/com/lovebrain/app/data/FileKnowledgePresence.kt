package com.lovebrain.app.data

import com.lovebrain.app.domain.port.KnowledgePresencePort
import java.io.File

/**
 * [KnowledgePresencePort] 的文件系统实现：knowledge/ 根的路径归属收在这一颗里
 * （与 FileKbArchiveTransfer 持 knowledgeRoot 同一种接法，di/AppModule.kt 里指向同一个目录），
 * viewmodel 侧从此不再碰 `java.io.File`。
 *
 * 判据与 SetupViewModel 的旧写法逐字同义：目录存在 && `listFiles()` 非空——
 * 任何条目都算，不验 kb.json（理由见端口 KDoc：「老用户」判据不是「合法库」判据）。
 */
class FileKnowledgePresence(private val knowledgeRoot: File) : KnowledgePresencePort {

    override fun hasAnyKnowledgeBase(): Boolean =
        knowledgeRoot.exists() && knowledgeRoot.listFiles()?.isNotEmpty() == true
}
