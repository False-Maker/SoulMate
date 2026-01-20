package com.soulmate.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soulmate.data.memory.IntimacyManager
import com.soulmate.ui.components.FluidBackground
import com.soulmate.ui.components.GlassBubble
import com.soulmate.ui.theme.ChampagneGold
import com.soulmate.ui.theme.RedMei
import com.soulmate.ui.theme.GlassSurface
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.viewmodel.SettingsViewModel

/**
 * SettingsScreen - 设置页面 (Ethereal Redesign)
 * 
 * 显示应用设置和亲密度信息。
 * 包含隐藏的调试功能：长按版本号可以设置亲密度分数到900（恋人等级）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val currentScore by viewModel.currentScore.collectAsState(initial = 0)
    val context = LocalContext.current
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Background
        FluidBackground()
        
        // 2. Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar Area (custom implementation for Ethereal look)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 16.dp), // Adjust for status bar
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(GlassSurface, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )
            }

            // 亲密度卡片
            IntimacyCard(
                score = currentScore,
                levelName = getLevelName(currentScore),
                nextLevelScore = getNextLevelScore(currentScore)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 人设配置部分
            val personaConfig by viewModel.personaConfig.collectAsState(initial = com.soulmate.data.model.PersonaConfig())
            
            SettingsSection(title = "Persona Configuration") {
                // AI Name
                SettingsInputItem(
                    icon = Icons.Default.Person,
                    title = "AI Name",
                    value = personaConfig.aiName,
                    onValueChange = { viewModel.updatePersonaConfig(personaConfig.copy(aiName = it)) }
                )
                
                // AI Nickname
                SettingsInputItem(
                    icon = Icons.Default.Edit,
                    title = "AI Nickname",
                    value = personaConfig.aiNickname,
                    onValueChange = { viewModel.updatePersonaConfig(personaConfig.copy(aiNickname = it)) }
                )
                
                // User Name
                SettingsInputItem(
                    icon = Icons.Default.Person,
                    title = "My Name",
                    value = personaConfig.userName,
                    onValueChange = { viewModel.updatePersonaConfig(personaConfig.copy(userName = it)) }
                )
                
                // User Nickname
                SettingsInputItem(
                    icon = Icons.Default.Edit,
                    title = "My Nickname",
                    value = personaConfig.userNickname,
                    onValueChange = { viewModel.updatePersonaConfig(personaConfig.copy(userNickname = it)) }
                )
                
                // Relationship Type
                SettingsDropdownItem(
                    icon = Icons.Default.Favorite,
                    title = "Relationship",
                    currentValue = personaConfig.relationship.name,
                    options = com.soulmate.data.model.RelationshipType.values().map { it.name },
                    onOptionSelected = { selectedName ->
                        val newType = com.soulmate.data.model.RelationshipType.valueOf(selectedName)
                        viewModel.updatePersonaConfig(personaConfig.copy(relationship = newType))
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 关于部分
            SettingsSection(title = "About") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0",
                    modifier = Modifier.combinedClickable(
                        onClick = { },
                        onLongClick = {
                            // 隐藏的调试功能：长按设置分数到900
                            viewModel.setCheatScore(900)
                            Toast.makeText(
                                context,
                                "Debug: Affinity set to 900 (Lover)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                )
                
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "Developers",
                    subtitle = "Lucian & Eleanor"
                )
            }
        }
    }
}

/**
 * 亲密度展示卡片
 */
@Composable
private fun IntimacyCard(
    score: Int,
    levelName: String,
    nextLevelScore: Int
) {
    val progress = if (nextLevelScore > 0) {
        score.toFloat() / nextLevelScore.toFloat()
    } else {
        1f
    }.coerceIn(0f, 1f)
    
    val levelColor = when (levelName) {
        "Stranger" -> Color(0xFF9E9E9E)
        "Friend" -> Color(0xFF4CAF50)
        "Crush" -> RedMei // Was Pink
        "Soulmate" -> RedMei // Was Pink
        else -> Color(0xFF9E9E9E)
    }
    
    GlassBubble(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 等级图标
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(levelColor, levelColor.copy(alpha = 0.6f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = "Bond Level",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = levelName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = levelColor
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 分数显示
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "/ ${IntimacyManager.MAX_SCORE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 进度条
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = levelColor,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 下一等级提示
            if (nextLevelScore > score) {
                Text(
                    text = "${nextLevelScore - score} points to next level",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            } else {
                Text(
                    text = "Max Level Reached ❤️",
                    style = MaterialTheme.typography.bodySmall,
                    color = levelColor
                )
            }
        }
    }
}

/**
 * 设置区块
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = ChampagneGold,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        )
        
        GlassBubble(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * 设置项
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 获取等级名称
 */
private fun getLevelName(score: Int): String {
    return when {
        score >= IntimacyManager.THRESHOLD_LOVER -> "Soulmate" // Lover
        score >= IntimacyManager.THRESHOLD_CRUSH -> "Crush"
        score >= IntimacyManager.THRESHOLD_FRIEND -> "Friend"
        else -> "Stranger"
    }
}

/**
 * 获取下一等级所需分数
 */
private fun getNextLevelScore(score: Int): Int {
    return when {
        score >= IntimacyManager.THRESHOLD_LOVER -> IntimacyManager.MAX_SCORE
        score >= IntimacyManager.THRESHOLD_CRUSH -> IntimacyManager.THRESHOLD_LOVER
        score >= IntimacyManager.THRESHOLD_FRIEND -> IntimacyManager.THRESHOLD_CRUSH
        else -> IntimacyManager.THRESHOLD_FRIEND
    }
}

/**
 * 设置输入项
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsInputItem(
    icon: ImageVector,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = ChampagneGold,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = ChampagneGold
                )
            )
        }
    }
}

/**
 * 设置下拉项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsDropdownItem(
    icon: ImageVector,
    title: String,
    currentValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { expanded = true })
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentValue,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(GlassSurface)
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = option, color = ChampagneGold) },
                            onClick = {
                                onOptionSelected(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SoulMateTheme {
        // Preview
        Box(modifier = Modifier.background(Color.Black)) {
            IntimacyCard(
                score = 350,
                levelName = "Friend",
                nextLevelScore = 500
            )
        }
    }
}
