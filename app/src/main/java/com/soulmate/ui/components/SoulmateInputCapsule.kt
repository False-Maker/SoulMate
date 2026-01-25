package com.soulmate.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.soulmate.R
import java.util.UUID

// ==========================================
// 1. 数据结构定义 (The Payload)
// ==========================================

/**
 * 封装发送给后端的内容
 */
data class InputPayload(
    val text: String?,              // 用户输入的文本 或 语音转写的文本
    val mediaUri: Uri?,             // 图片或视频的本地路径
    val mediaType: MediaType,       // 枚举: NONE, IMAGE, VIDEO
    val inputMode: InputMode        // 枚举: TEXT, VOICE
)

enum class MediaType {
    NONE, IMAGE, VIDEO
}

enum class InputMode {
    TEXT, VOICE
}

/**
 * 回调接口，用于处理发送事件
 */
interface OnSendListener {
    fun onSend(payload: InputPayload)
}

// ==========================================
// 2. 自定义 View 实现 (SoulmateInputCapsule)
// ==========================================

/**
 * SoulmateInputCapsule - 多模态输入组件
 *
 * 角色设定：Android 系统架构师作品
 * 核心功能：在一个极简的 UI 容器内，同时处理文本输入、语音录制、以及图片/视频文件的选择与预览。
 */
class SoulmateInputCapsule @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    // UI 组件引用
    private lateinit var layoutContainer: LinearLayout
    private lateinit var btnFunction: ImageView // 左侧功能/预览按钮
    private lateinit var btnDeleteMedia: ImageView // 媒体删除小按钮
    private lateinit var inputEditText: EditText // 中间输入框
    private lateinit var btnAction: ImageView // 右侧语音/发送按钮

    // 状态变量
    private var currentMediaUri: Uri? = null
    private var currentMediaType: MediaType = MediaType.NONE
    private var isVoiceRecording = false
    private var isHandsFreeLocked = false // Flow Mode 锁定状态
    
    // 媒体选择器 Launcher
    // 注意：在 View 中注册 ActivityResultLauncher 需要依赖 findViewTreeLifecycleOwner
    private var mediaPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var mediaDocumentLauncher: ActivityResultLauncher<Array<String>>? = null
    
    // 监听器
    var onSendListener: OnSendListener? = null
    var onHandsFreeStateChanged: ((Boolean) -> Unit)? = null // Flow Mode 状态变化回调

    // 录音相关 (占位)
    private var mediaRecorder: MediaRecorder? = null
    private var voiceStartTime: Long = 0

    // 上滑/拖拽发送相关
    private var actionButtonDownY: Float = 0f
    private var isDraggingToSend = false
    private val SLIDE_UP_THRESHOLD_DP = 50f // 上滑触发阈值 (dp)
    private var slideUpThresholdPx = 0f

    init {
        setupUI()
        setupLogic()
    }

    // ==========================================
    // UI 布局构建 (The Capsule Layout)
    // ==========================================
    private fun setupUI() {
        slideUpThresholdPx = dp2px(SLIDE_UP_THRESHOLD_DP)

        // 1. 设置 CardView 基础样式 (圆角悬浮胶囊)
        radius = dp2px(28f) // 圆角半径
        cardElevation = dp2px(8f) // 阴影
        setCardBackgroundColor(Color.parseColor("#F5F5F5")) // 浅灰背景
        setContentPadding(dp2px(4), dp2px(4), dp2px(4), dp2px(4))

        // 2. 内部容器 LinearLayout
        layoutContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM // 底部对齐
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 3. 左侧：功能切换区 (Box 容器用于叠加删除按钮)
        val leftBox = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp2px(48), dp2px(48)).apply {
                gravity = Gravity.BOTTOM
            }
        }

        btnFunction = ImageView(context).apply {
            // 默认显示 "+" 号
            // 假设项目中有 ic_add，如果没有则使用系统自带或 text
            // 这里为了演示，使用 ic_input_add 或 android.R.drawable.ic_input_add
            setImageResource(android.R.drawable.ic_input_add) 
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            padding = dp2px(12)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            
            // 初始状态灰色圆
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE) // 白色背景
            }
        }

        btnDeleteMedia = ImageView(context).apply {
            // 右上角小 "x" 号
            // 假设项目中有 ic_close
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            visibility = View.GONE // 默认隐藏
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(dp2px(16), dp2px(16)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp2px(0)
                rightMargin = dp2px(0)
            }
            // 红色背景圆形
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.RED)
            }
            padding = dp2px(3)
            setColorFilter(Color.WHITE) // 图标白色
        }

        leftBox.addView(btnFunction)
        leftBox.addView(btnDeleteMedia)
        layoutContainer.addView(leftBox)

        // 4. 中间：输入核心区
        inputEditText = EditText(context).apply {
            background = null // 透明背景
            hint = "输入消息..."
            textSize = 16f
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            maxLines = 4
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
                gravity = Gravity.CENTER_VERTICAL
                leftMargin = dp2px(8)
                rightMargin = dp2px(8)
            }
            setPadding(0, dp2px(14), 0, dp2px(14))
        }
        layoutContainer.addView(inputEditText)

        // 5. 右侧：语音/发送 动态按钮
        btnAction = ImageView(context).apply {
            // 默认麦克风
            setImageResource(android.R.drawable.ic_btn_speak_now)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            padding = dp2px(10)
            // 圆形背景
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.LTGRAY) // 默认灰色
            }
            layoutParams = LinearLayout.LayoutParams(dp2px(48), dp2px(48)).apply {
                gravity = Gravity.BOTTOM
            }
        }
        layoutContainer.addView(btnAction)

        addView(layoutContainer)
    }
    
    // 赋值 padding 的辅助属性
    private var View.padding: Int
        get() = paddingLeft
        set(value) = setPadding(value, value, value, value)

    // ==========================================
    // 3. 核心功能逻辑 (The Logic Core)
    // ==========================================
    @SuppressLint("ClickableViewAccessibility")
    private fun setupLogic() {
        // --- 状态监听：EditText 变化 ---
        inputEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateActionButtonState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // --- 功能 A：媒体选择 (Media Picker) ---
        // 左侧按钮功能：无媒体时点击展开菜单，有媒体时点击预览(此处暂做Toast演示)
        btnFunction.setOnClickListener {
            if (currentMediaType == MediaType.NONE) {
                showMediaOptionMenu(it)
            } else {
                Toast.makeText(context, "预览媒体: $currentMediaUri", Toast.LENGTH_SHORT).show()
            }
        }

        btnDeleteMedia.setOnClickListener {
            clearMedia()
        }

        // --- 功能 B & C：语音录制 与 拖拽发送 ---
        btnAction.setOnTouchListener { v, event ->
            val isSendMode = shouldShowSendButton()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    actionButtonDownY = event.rawY
                    if (isSendMode) {
                        // 发送模式：可能是点击，也可能是上滑
                        v.alpha = 0.7f
                    } else {
                        // 语音模式：立即开始录音
                        startVoiceRecording()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = actionButtonDownY - event.rawY
                    
                    if (isSendMode) {
                        // 发送模式：检测上滑
                        if (deltaY > slideUpThresholdPx && !isDraggingToSend) {
                            // 触发上滑视觉反馈 (例如按钮上移或变色)
                            isDraggingToSend = true
                            v.translationY = -dp2px(10f) // 简单位移反馈
                            (v.background as GradientDrawable).setColor(Color.parseColor("#0056b3")) // 深蓝反馈
                            Toast.makeText(context, "松手发送", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                         // 语音模式：上滑锁定免提模式 (Flow Mode)
                         if (deltaY > slideUpThresholdPx) {
                             // 提示锁定免提
                             inputEditText.hint = "松开锁定免提模式"
                             (v.background as GradientDrawable).setColor(Color.parseColor("#00E5FF")) // Cyan for hands-free
                         } else {
                             // 恢复提示
                             inputEditText.hint = "正在听..."
                         }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.alpha = 1.0f
                    v.translationY = 0f
                    val deltaY = actionButtonDownY - event.rawY

                    if (isSendMode) {
                        // 发送模式
                        if (deltaY > slideUpThresholdPx) {
                            // 认为是拖拽发送
                            performSend()
                        } else {
                            // 普通点击发送
                            performSend()
                        }
                        isDraggingToSend = false
                    } else {
                        // 语音模式
                        if (deltaY > slideUpThresholdPx) {
                            // 上滑锁定免提模式 (Flow Mode)
                            lockHandsFreeMode()
                        } else {
                            // 正常结束录音
                            if (isHandsFreeLocked) {
                                // 如果已锁定，点击则解锁
                                unlockHandsFreeMode()
                            } else {
                                stopVoiceRecording()
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f
                    v.translationY = 0f
                    isDraggingToSend = false
                    if (!isSendMode) {
                        if (!isHandsFreeLocked) {
                            cancelVoiceRecording()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 显示媒体选择菜单 (相册、相机)
     */
    private fun showMediaOptionMenu(anchor: View) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 1, 0, "相册 (图片/视频)")
        // popup.menu.add(0, 2, 0, "相机") // 暂未实现
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> launchMediaPicker()
                // 2 -> launchCamera()
            }
            true
        }
        popup.show()
    }

    // --- 状态更新逻辑 ---
    private fun updateActionButtonState() {
        val hasText = !inputEditText.text.isNullOrBlank()
        val hasMedia = currentMediaType != MediaType.NONE

        if (isHandsFreeLocked) {
            // 免提模式锁定：显示波浪图标（使用系统图标作为占位，实际应使用自定义图标）
            btnAction.setImageResource(android.R.drawable.ic_menu_view) // 占位图标，实际应使用 wave icon
            (btnAction.background as GradientDrawable).setColor(Color.parseColor("#00E5FF")) // Cyan for hands-free
        } else if (hasText || hasMedia) {
            // 显示发送图标
            btnAction.setImageResource(android.R.drawable.ic_menu_send)
            (btnAction.background as GradientDrawable).setColor(Color.parseColor("#007AFF")) // 蓝色高亮
        } else {
            // 显示麦克风图标
            btnAction.setImageResource(android.R.drawable.ic_btn_speak_now)
            (btnAction.background as GradientDrawable).setColor(Color.LTGRAY)
        }
    }

    private fun shouldShowSendButton(): Boolean {
        return !inputEditText.text.isNullOrBlank() || currentMediaType != MediaType.NONE
    }

    // --- 功能 A 实现：Media Picker Launcher ---
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 注册 ActivityResultLauncher
        // 必须在 STARTED 之前注册，且需要 LifecycleOwner
        try {
            val lifecycleOwner = findViewTreeLifecycleOwner()
            if (lifecycleOwner != null) {
                val registryOwner = context as? androidx.activity.result.ActivityResultRegistryOwner 
                    ?: (context as? android.content.ContextWrapper)?.baseContext as? androidx.activity.result.ActivityResultRegistryOwner

                if (registryOwner != null) {
                    val uuid = UUID.randomUUID().toString().substring(0, 8)
                    mediaPickerLauncher = registryOwner.activityResultRegistry.register(
                        "media_picker_$uuid",
                        lifecycleOwner,
                        ActivityResultContracts.PickVisualMedia()
                    ) { uri: Uri? ->
                        handleMediaSelection(uri)
                    }
                    
                    mediaDocumentLauncher = registryOwner.activityResultRegistry.register(
                        "media_document_$uuid",
                        lifecycleOwner,
                        ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        handleMediaSelection(uri)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchMediaPicker() {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            if (mediaPickerLauncher != null) {
                mediaPickerLauncher?.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            } else {
                Toast.makeText(context, "无法启动相册，Context 均不支持 ActivityResultRegistry", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (mediaDocumentLauncher != null) {
                mediaDocumentLauncher?.launch(arrayOf("image/*", "video/*"))
            } else {
                Toast.makeText(context, "无法启动相册，Context 均不支持 ActivityResultRegistry", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleMediaSelection(uri: Uri?) {
        if (uri == null) return

        currentMediaUri = uri
        
        // 简单判断类型
        val mimeType = context.contentResolver.getType(uri)
        currentMediaType = if (mimeType?.startsWith("video") == true) {
            MediaType.VIDEO
        } else {
            MediaType.IMAGE
        }

        // 更新 UI：左侧显示缩略图
        btnFunction.setImageURI(uri) // 注意：大图应使用 Glide/Coil
        btnFunction.scaleType = ImageView.ScaleType.CENTER_CROP
        btnFunction.padding = 0 // 去除 padding 以充满
        
        btnDeleteMedia.visibility = View.VISIBLE
        updateActionButtonState()
    }

    private fun clearMedia() {
        currentMediaUri = null
        currentMediaType = MediaType.NONE
        
        // 恢复 "+" 号
        btnFunction.setImageResource(android.R.drawable.ic_input_add)
        btnFunction.scaleType = ImageView.ScaleType.CENTER_INSIDE
        btnFunction.padding = dp2px(12)
        
        btnDeleteMedia.visibility = View.GONE
        updateActionButtonState()
    }

    // --- 功能 B 实现：Voice Handler ---
    
    private fun startVoiceRecording() {
        isVoiceRecording = true
        voiceStartTime = System.currentTimeMillis()
        inputEditText.hint = "正在听..."
        
        // UI 反馈：变红
        (btnAction.background as GradientDrawable).setColor(Color.RED)
        
        // 实际录音逻辑 (MediaRecorder) 占位
        // try {
        //     mediaRecorder = MediaRecorder().apply { ... }
        //     mediaRecorder?.start()
        // } catch (e: Exception) { ... }
    }

    private fun stopVoiceRecording() {
        if (!isVoiceRecording) return
        isVoiceRecording = false
        inputEditText.hint = "输入消息..."
        
        // 恢复 UI
        updateActionButtonState()

        val duration = System.currentTimeMillis() - voiceStartTime
        if (duration < 500) {
            Toast.makeText(context, "说话时间太短", Toast.LENGTH_SHORT).show()
            return
        }

        // 模拟 STT (语音转文字)
        val simulatedText = "这是模拟的语音转写文本"
        inputEditText.setText(inputEditText.text.toString() + simulatedText)
        inputEditText.setSelection(inputEditText.text.length)
    }

    private fun cancelVoiceRecording() {
        isVoiceRecording = false
        inputEditText.hint = "输入消息..."
        updateActionButtonState()
        Toast.makeText(context, "录音取消", Toast.LENGTH_SHORT).show()
    }

    // --- Flow Mode (Hands-free Lock) ---
    
    /**
     * 锁定免提模式
     * 上滑手势触发，保持录音状态，图标变为波浪
     */
    private fun lockHandsFreeMode() {
        if (isHandsFreeLocked) return
        
        isHandsFreeLocked = true
        // 保持录音状态（不停止）
        inputEditText.hint = "免提模式已锁定，点击解锁"
        updateActionButtonState()
        onHandsFreeStateChanged?.invoke(true)
        Toast.makeText(context, "免提模式已锁定", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 解锁免提模式
     * 点击或下滑触发，停止录音
     */
    private fun unlockHandsFreeMode() {
        if (!isHandsFreeLocked) return
        
        isHandsFreeLocked = false
        isVoiceRecording = false
        inputEditText.hint = "输入消息..."
        updateActionButtonState()
        onHandsFreeStateChanged?.invoke(false)
        Toast.makeText(context, "免提模式已解锁", Toast.LENGTH_SHORT).show()
    }

    // --- 功能 C 实现：发送逻辑 (Drag-to-Send) ---
    
    private fun performSend() {
        val text = inputEditText.text.toString().trim()
        val mediaUri = currentMediaUri
        val mediaType = currentMediaType
        
        if (text.isEmpty() && mediaType == MediaType.NONE) return

        // 1. 打包 Payload
        val payload = InputPayload(
            text = if (text.isEmpty()) null else text,
            mediaUri = mediaUri,
            mediaType = mediaType,
            inputMode = InputMode.TEXT // 最终发送都算作一次 Submit
        )

        // 2. 触发回调
        onSendListener?.onSend(payload)

        // 3. 重置状态
        inputEditText.text.clear()
        clearMedia()
    }
    
    // ==========================================
    // 4. 场景模拟 (Context Handling) - 注释说明
    // ==========================================
    /*
     * 后端处理逻辑说明：
     *
     * 1. 仅文本 (text != null, mediaType == NONE)
     *    -> 场景：普通聊天 / 文生图指令
     *    -> 处理：直接将 text 发送给 LLM。
     *
     * 2. 图片 + 文本 (text 可能为空, mediaType == IMAGE)
     *    -> 场景：图片理解 (Vision)
     *    -> 处理：将 text (作为 prompt) 和 mediaUri (转为 base64) 发送给 Vision Model。
     *
     * 3. 视频 + 文本 (text 可能为空, mediaType == VIDEO)
     *    -> 场景：视频理解
     *    -> 处理：客户端抽取视频关键帧 (如 6 帧)，组合成多模态消息发送给 Vision Model。
     */

    // 工具方法：dp 转 px
    private fun dp2px(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }
    
    private fun dp2px(dp: Int): Int {
        return dp2px(dp.toFloat()).toInt()
    }
}
