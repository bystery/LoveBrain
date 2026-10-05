package com.lovebrain.app.domain.port

import java.io.InputStream
import java.io.OutputStream

/**
 * 知识库归档（zip）导出 / 导入的能力端口。
 *
 * 页面只管"用户挑了个 Uri、把流交出去"，"库目录在哪、暂存区在哪、zip 怎么解、
 * 越界与同名怎么判"这些**路径归属**全在实现侧——所以这里刻意只交出
 * [OutputStream] / [InputStream] 两个流，不让页面再碰 `java.io.File`：
 * 这与 [KnowledgeRuntimePort] 把"库的存在性"挡在页面之外的用意一脉相承。
 *
 * 它不是第二条写链：实现只是把这两个流转交给仓库之外那唯一的归档实现
 * （`data/KbArchiveTransfer`），落盘边界仍然只有那一处，端口只是把"要导出/导入哪个库"
 * 这件事的调用方从具体类名解耦出来。
 */
interface KbArchivePort {
    /** 把 [kbName] 这本库打包写进 [output]；库目录不存在时抛实现侧的传输异常 */
    fun exportTo(kbName: String, output: OutputStream)

    /** 从 [input] 解出一本库并原子搬入库目录；返回导入成功的库名，结构非法/超限即抛 */
    fun importFrom(input: InputStream): String
}
