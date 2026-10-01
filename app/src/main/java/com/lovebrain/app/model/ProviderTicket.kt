package com.lovebrain.app.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 模型供应商配置
 *
 * @property id 唯一标识（UUID）
 * @property name 供应商名称（用户可读）
 * @property baseUrl 接口地址（用户填什么用什么，不做任何路径补全）
 * @property model 当前生效模型（生成/计费/就绪判定等全部既有消费面读这个字段）
 * @property models 模型列表（一供应商多模型；老数据只有 model 时由存储层读取时迁移 models = [model]）
 * @property thinkingMode 思考模式开关（0=关 1=开；null=升级前老数据未写值，消费时兜底回全局设置）
 * @property generateTimeoutSec 主生成总超时的档位（秒）。**只存 [com.lovebrain.app.GenerationTimeoutTier]
 *   白名单里的四个数之一**；null = 升级前的老数据没写过这一项，消费侧（`ProviderConfigResolver`）
 *   一律回落到默认档 120 秒。慢速非官方兼容服务在 Provider 高级设置里调这一档（Issue #5）。
 *
 * **设计决策**：
 * - **Key 不在结构内**——配置与凭据分离，API Key 通过 SecurePrefs 的加密分条存储
 * - `model` 与 `models` 双字段：`models` 是列表真源，`model` 是"设为当前"的选中值，
 *   全部既有消费面（getActiveModel/providerReady/testConnection）零改动
 * - 超时档位与 `thinkingMode` 同形（可空 Int + 消费侧兜底），**不再另开一份全局状态**：
 *   它就是"每张工单一份配置"这张表里的一个字段
 */
@Serializable
data class ProviderTicket(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val model: String = "",
    val models: List<String> = emptyList(),
    val thinkingMode: Int? = null,
    val generateTimeoutSec: Int? = null,
)
