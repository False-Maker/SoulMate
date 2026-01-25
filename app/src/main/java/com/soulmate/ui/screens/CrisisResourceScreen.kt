package com.soulmate.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soulmate.data.service.CrisisInterventionManager
import com.soulmate.ui.components.GlassBubble
import com.soulmate.ui.components.ParticleBackground
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.viewmodel.SafetyViewModel

/**
 * CrisisResourceScreen - 危机干预资源页面
 *
 * 展示紧急求助热线和资源
 */
@Composable
fun CrisisResourceScreen(
    viewModel: SafetyViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val resources = viewModel.getCrisisResources()

    Box(
        modifier = Modifier.fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SoulMateTheme.colors.bgGradientStart,
                        SoulMateTheme.colors.bgGradientEnd
                    )
                )
            )
    ) {
        // Background
        ParticleBackground(
            modifier = Modifier.fillMaxSize(),
            particleColor = SoulMateTheme.colors.particleColor,
            lineColor = SoulMateTheme.colors.cardBorder
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(SoulMateTheme.colors.cardBg, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = SoulMateTheme.colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "危机干预资源",
                    style = MaterialTheme.typography.displaySmall,
                    color = SoulMateTheme.colors.textPrimary
                )
            }

            // Description
            GlassBubble(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "如果您或您关心的人正在经历心理危机，请不要独自承受。以下专业机构提供 24 小时免费援助。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoulMateTheme.colors.textSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Resource List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(resources) { resource ->
                    ResourceItem(
                        resource = resource,
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${resource.contact}")
                            }
                            context.startActivity(intent)
                        }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ResourceItem(
    resource: CrisisInterventionManager.CrisisResource,
    onClick: () -> Unit
) {
    GlassBubble(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resource.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SoulMateTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = resource.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SoulMateTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = resource.contact,
                    style = MaterialTheme.typography.titleLarge,
                    color = SoulMateTheme.colors.accentColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(SoulMateTheme.colors.accentColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "拨打",
                    tint = SoulMateTheme.colors.accentColor
                )
            }
        }
    }
}
