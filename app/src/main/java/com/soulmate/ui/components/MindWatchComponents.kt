package com.soulmate.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soulmate.data.service.MindWatchService
import com.soulmate.ui.theme.SoulMateTheme

/**
 * 关怀卡片 (Care Card)
 * 
 * 当 MindWatch 检测到异常状态时，在聊天流中插入的特殊卡片。
 * 提供情感支持和求助入口。
 */
@Composable
fun CareCard(
    status: MindWatchService.WatchStatus,
    message: String,
    onCallHelp: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, title, color) = when (status) {
        MindWatchService.WatchStatus.CRISIS -> Triple(Icons.Default.Phone, "深度守护", Color(0xFFFF4500)) // Red-Orange
        MindWatchService.WatchStatus.WARNING -> Triple(Icons.Default.Warning, "共鸣阴雨", Color(0xFFFF8C00)) // Orange
        else -> Triple(Icons.Default.Favorite, "灵犀提示", SoulMateTheme.colors.accentColor)
    }

    GlassBubble(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = color.copy(alpha = 0.15f),
        borderColor = color.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = SoulMateTheme.colors.textPrimary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row {
                Button(
                    onClick = onCallHelp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = color,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (status == MindWatchService.WatchStatus.CRISIS) "寻求帮助" else "倾诉")
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = SoulMateTheme.colors.textSecondary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SoulMateTheme.colors.cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("我没事")
                }
            }
        }
    }
}
