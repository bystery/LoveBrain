package com.lovebrain.app.domain

import java.security.MessageDigest

/**
 * Prompt 资产的内容指纹（纯函数）。
 *
 * 之所以从 `PromptBuilder` 里抽出来，是因为"指纹算的是什么"这件事必须能被测：
 * 它关系到缓存什么时候该失效、`BENCHMARK.md` 里那串 hash 指的是什么。
 *
 * 两条不显然但会咬人的规则：
 *
 * 1. **换行不算内容。** `.gitattributes` 是 `* text=auto`，同一份资产在 Windows 工作树里是
 *    CRLF、在 Linux 检出里是 LF。跟着字节走的话，本机算出的指纹与 CI 算出的永远不同：
 *    资产锁在 CI 上必红，而设备端的 `promptVersion` 会因"这个 APK 是在哪台机器上打出来的"
 *    而不同，缓存说废就废。所以摘要前先归一成 LF。
 * 2. **顺序与路径都是内容的一部分。** 逐项按 `路径 NUL 内容 NUL` 喂进摘要，
 *    换拼装顺序会得到不同指纹——这跟 `buildSystemPrompt()` 里"顺序变了 prompt 就变"一致。
 */
object AssetFingerprints {

    /** 指纹输出长度（16 个十六进制字符）——够短到能写进日志与缓存键，够长到碰撞不是巧合 */
    const val PREFIX_LEN = 16

    /** 归一化：让同一份内容在任何平台上得到同一个指纹 */
    fun canonical(text: String): String = text.replace("\r\n", "\n")

    /**
     * 按给定顺序对「路径 + 内容」取组合指纹。
     *
     * 空列表也有确定值（不是 null、不是异常），因为调用方可能在资产缺失时
     * 仍然要一个可比较的标识；缺失本身由 `readAsset` 那边记日志。
     */
    fun hashOf(entries: List<Pair<String, String>>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for ((path, content) in entries) {
            digest.update(path.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(canonical(content).toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(PREFIX_LEN)
    }
}
