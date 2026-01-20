package com.soulmate.data.service

/**
 * AvatarState - 数字人状态封装
 * 
 * 用于表示数字人 SDK 的当前状态，通过 StateFlow 暴露给 ViewModel
 */
sealed class AvatarState {
    /**
     * 空闲状态 - 数字人处于待机状态
     */
    object Idle : AvatarState()
    
    /**
     * 说话状态 - 数字人正在说话
     */
    object Speaking : AvatarState()
    
    /**
     * 聆听状态 - 数字人正在聆听用户输入
     */
    object Listening : AvatarState()
    
    /**
     * 思考状态 - 数字人正在思考（等待 LLM 响应）
     */
    object Thinking : AvatarState()
    
    /**
     * 错误状态 - 包含错误信息
     */
    data class Error(val msg: String) : AvatarState()
}

