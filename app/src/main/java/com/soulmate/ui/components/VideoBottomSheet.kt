package com.soulmate.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

/**
 * VideoBottomSheet - 视频播放底部弹窗
 *
 * 当 Avatar SDK 触发 widget_video 事件时显示此弹窗，
 * 展示视频内容。
 *
 * @param videoUrl 视频 URL
 * @param coverUrl 封面图 URL（可选，暂未使用）
 * @param onDismiss 关闭弹窗的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoBottomSheet(
    videoUrl: String,
    coverUrl: String? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 视频标题
            Text(
                text = "视频播放",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 视频播放器
            VideoPlayer(
                videoUrl = videoUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 关闭按钮
            Button(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("关闭")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 底部弹窗拖拽把手
 */
@Composable
private fun BottomSheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(4.dp)
                .fillMaxWidth(0.1f)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
    }
}

/**
 * 视频播放器组件
 *
 * 使用 Android 原生 VideoView 播放视频
 */
@Composable
private fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    AndroidView(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(Uri.parse(videoUrl))
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.isLooping = false
                    start()
                }
                setOnErrorListener { _, _, _ ->
                    // 视频加载失败时的处理
                    false
                }
            }
        },
        update = { videoView ->
            // 当 URL 变化时更新视频
            if (videoView.tag != videoUrl) {
                videoView.tag = videoUrl
                videoView.setVideoURI(Uri.parse(videoUrl))
                videoView.start()
            }
        }
    )
    
    // 清理资源
    DisposableEffect(videoUrl) {
        onDispose {
            // VideoView 会在 AndroidView 销毁时自动清理
        }
    }
}
