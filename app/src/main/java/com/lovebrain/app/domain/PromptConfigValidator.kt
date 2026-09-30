package com.lovebrain.app.domain

/**
 * thinkingMode / outputMode 配置校验（自 `domain/PromptBuilder.kt` 的「配置校验」行为块搬出）。
 *
 * 这一块只看两个 int 的范围并给出回退值与警告，不读知识库、不读资产、不读时钟，也不持有状态，
 * 因此是纯函数。搬运前后对同一批输入必须给出逐字相同的 [Result]。
 *
 * 语义约束（搬运时一条都没改）：
 * - thinkingMode 只接受 0/1（两态化：直出/思考）；旧三态值 2 被 SecurePrefs.clampThinkingMode()
 *   钳制为 0，无效值在此回退 0 并给出警告。
 * - outputMode 同口径，只接受 0/1。
 * - [Result.isValid] 当且仅当 warnings 为空。
 */
object PromptConfigValidator {

    /** 校验 thinkingMode/outputMode 范围，无效值回退默认并给出警告 */
    fun validate(thinkingMode: Int, outputMode: Int): Result {
        val warnings = mutableListOf<String>()
        var fixedThinking = thinkingMode
        var fixedOutput = outputMode
        if (thinkingMode !in 0..1) { warnings.add("thinkingMode=$thinkingMode 无效，已回退 0"); fixedThinking = 0 }
        if (outputMode !in 0..1) { warnings.add("outputMode=$outputMode 无效，已回退 0"); fixedOutput = 0 }
        return Result(fixedThinking, fixedOutput, warnings)
    }

    data class Result(
        val thinkingMode: Int,
        val outputMode: Int,
        val warnings: List<String>
    ) { val isValid: Boolean get() = warnings.isEmpty() }
}
