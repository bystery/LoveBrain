package com.lovebrain.app.data

import com.lovebrain.app.domain.port.KbArchivePort
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * [KbArchivePort] 的文件系统实现：把"库目录 / 暂存目录在哪"这件事收在这一个适配器里，
 * 页面只交出流，不再自己拼 `File`。
 *
 * 它只是把两个流原样转交给 [KbArchiveTransfer] 那**唯一**的归档实现——
 * 不重打包、不第二处解 zip，路径归属与写边界都还只在 `KbArchiveTransfer` 里，
 * 端口这层没有开出第二条落盘口。
 */
class FileKbArchiveTransfer(
    private val knowledgeRoot: File,
    private val stagingBase: File
) : KbArchivePort {

    override fun exportTo(kbName: String, output: OutputStream) =
        KbArchiveTransfer.export(File(knowledgeRoot, kbName), kbName, output)

    override fun importFrom(input: InputStream): String {
        val staging = File(stagingBase, "kb_import_${System.currentTimeMillis()}")
        return KbArchiveTransfer.import(input, staging, knowledgeRoot)
    }
}
