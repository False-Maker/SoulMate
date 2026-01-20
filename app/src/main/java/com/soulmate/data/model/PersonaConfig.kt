package com.soulmate.data.model

import com.google.gson.annotations.SerializedName

/**
 * PersonaConfig - User definitions for the AI's persona
 * 
 * Allows users to customize:
 * - AI Name & Nickname
 * - User Name & Nickname
 * - Relationship Type (Assistant, Companion, Lover)
 */
data class PersonaConfig(
    @SerializedName("ai_name")
    val aiName: String = "Eleanor",
    
    @SerializedName("ai_nickname")
    val aiNickname: String = "艾诺",
    
    @SerializedName("user_name")
    val userName: String = "Lucian",
    
    @SerializedName("user_nickname")
    val userNickname: String = "路西安",
    
    @SerializedName("relationship")
    val relationship: RelationshipType = RelationshipType.COMPANION
)

enum class RelationshipType {
    @SerializedName("assistant")
    ASSISTANT,  // 助手（礼貌正式）
    
    @SerializedName("companion")
    COMPANION,  // 伙伴（友好亲切）
    
    @SerializedName("lover")
    LOVER       // 恋人（亲密深情）
}

/**
 * UserGender - 用户性别枚举
 * 
 * 用于决定显示哪种数字人：
 * - 男性用户 → 看女性数字人 (Eleanor)
 * - 女性用户 → 看男性数字人
 */
enum class UserGender {
    @SerializedName("male")
    MALE,    // 男性用户
    
    @SerializedName("female")
    FEMALE,  // 女性用户
    
    @SerializedName("unset")
    UNSET    // 未设置（首次启动）
}

