package com.lovebrain.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  回归（，  修订表；计 3 条用例）：
 * 备份目录分组键锚定——正则锚定实际命名 `${kb}_yyyyMMdd_HHmm`，
 * 替换掉历史 `substringBeforeLast("_2")` 的巧合依赖（年份恒以 2 开头恰可工作的脆弱形态）。
 *
 * 命名不匹配时整名为键（永不修剪该组）——保守保留，宁多留不误删。
 */
class KnowledgeRepositoryBackupKeyTest {

    /** 典型名：库名 + 时间戳 → 剥离时间戳得库名 */
    @Test
    fun typical_backup_name_strips_timestamp() {
        assertEquals("kb1", KnowledgeRepository.backupGroupKey("kb1_20260824_1030"))
    }

    /** 库名自身含 "_2"：只剥末尾时间戳，库名部分原样保留（旧启发式靠巧合才能做对） */
    @Test
    fun kb_name_containing_2_suffix_kept() {
        assertEquals("ab_2cd", KnowledgeRepository.backupGroupKey("ab_2cd_20260824_1030"))
    }

    /** 不匹配实际命名格式：整名为键（该目录自成一组，永不触发修剪） */
    @Test
    fun non_matching_name_returned_whole() {
        assertEquals("kb1", KnowledgeRepository.backupGroupKey("kb1"))
        assertEquals("kb1_backup", KnowledgeRepository.backupGroupKey("kb1_backup"))
    }
}
