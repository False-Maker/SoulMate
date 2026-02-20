package com.soulmate.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soulmate.data.memory.IntimacyManager
import com.soulmate.ui.components.ParticleBackground
import com.soulmate.data.model.RelationshipType
import com.soulmate.data.model.UserGender
import com.soulmate.ui.components.GlassBubble
import com.soulmate.ui.components.ParallaxGlassCard
import com.soulmate.ui.theme.ChampagneGold
import com.soulmate.ui.theme.RedMei
import com.soulmate.ui.theme.GlassSurface
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.viewmodel.SettingsViewModel

/**
 * SettingsScreen - 神经网络配置中心 (Neural Link Config)
 *
 * 赛博朋克风格的设置页面，强调科技感与连接感。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToResources: () -> Unit = {},
    onNavigateToReport: () -> Unit = {}
) {
    val currentScore by viewModel.currentScore.collectAsState(initial = 0)
    val affinityScore by viewModel.affinityScore.collectAsState(initial = 60)
    val affinityLevel by viewModel.affinityLevel.collectAsState(initial = com.soulmate.data.repository.AffinityRepository.AffinityLevel.NORMAL)
    val context = LocalContext.current
    
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
        // 1. Background
        Box(modifier = Modifier.fillMaxSize())
        
        // 2. Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // -- Header --
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(SoulMateTheme.colors.cardBg.copy(alpha=0.5f), CircleShape)
                        .border(1.dp, SoulMateTheme.colors.cardBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = SoulMateTheme.colors.textPrimary
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = "SOUL CORE",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = SoulMateTheme.colors.accentColor
                    )
                    Text(
                        text = "灵核配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoulMateTheme.colors.textSecondary
                    )
                }
            }
            
            // -- 0. Intimacy Dashboard (Hero Card) --
            val levelName = getLevelName(currentScore)
            val nextScore = getNextLevelScore(currentScore)
            val nextAnniversary by viewModel.nextAnniversary.collectAsState(initial = null)
            IntimacyDashboard(
                score = currentScore,
                levelName = levelName,
                nextLevelScore = nextScore,
                nextAnniversary = nextAnniversary,
                affinityScore = affinityScore,
                affinityLevel = affinityLevel
            )
            
            // -- 1. Persona Matrix --
            val personaConfig by viewModel.personaConfig.collectAsState(initial = com.soulmate.data.model.PersonaConfig())
            val userGender by viewModel.userGender.collectAsState(initial = UserGender.UNSET)
            val personaWarmth by viewModel.personaWarmth.collectAsState(initial = com.soulmate.data.preferences.UserPreferencesRepository.DEFAULT_PERSONA_WARMTH)
            
            SettingsSection(title = "灵犀化身 (SOUL AVATAR)", icon = Icons.Default.Person) {
                // User & AI Name
                Row(modifier = Modifier.fillMaxWidth()) {
                     Box(modifier = Modifier.weight(1f)) {
                         NeonInputItem(
                            label = "User Name (我的名字)",
                            value = personaConfig.userName,
                            onValueChange = { viewModel.updatePersonaConfig(personaConfig.copy(userName = it)) }
                         )
                     }
                     Spacer(modifier = Modifier.width(16.dp))
                     Box(modifier = Modifier.weight(1f)) {
                         NeonInputItem(
                            label = "AI Name (AI 名字)",
                            value = personaConfig.aiName,
                            onValueChange = { viewModel.updatePersonaConfig(personaConfig.copy(aiName = it)) }
                         )
                     }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Gender Dropdown
                NeonDropdown(
                    label = "Gender (选择性别)",
                    currentValue = getGenderName(userGender),
                    options = listOf("男", "女"),
                    onOptionSelected = { selectedName ->
                        val newGender = when (selectedName) {
                            "男" -> UserGender.MALE
                            "女" -> UserGender.FEMALE
                            else -> UserGender.UNSET
                        }
                        // Explicitly update ViewModel
                        viewModel.updateUserGender(newGender)
                        // Note: Because userGender is collected as State, UI will update automatically
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Warmth Slider uses new Initiative Logic (reusing personaWarmth preference store but labeled Initiative)
                NeonSlider(
                    label = "Initiative Level (主动性)",
                    value = personaWarmth,
                    onValueChange = { viewModel.updatePersonaWarmth(it) }
                )
            }

            // -- 2. Memory Module (Soul Vitality) --
            val memoryRetentionDays by viewModel.memoryRetentionDays.collectAsState(initial = 0L)
            val memoryCount by viewModel.memoryCount.collectAsState(initial = 0L)
            
            SettingsSection(title = "时光琥珀 (TIME AMBER)", icon = Icons.Default.Memory) {
                 // Memory Clarity Meter
                 val load = (memoryCount % 1000).toFloat() / 1000f // Fake progress based on 1000 memories chunk
                 val clarity = ((1f - load) * 100).toInt().coerceIn(0, 100)
                 
                 Column {
                     Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                         Text("Amber Purity (琥珀纯净度)", style=MaterialTheme.typography.labelSmall, color=Color.Gray)
                         Text("$clarity%", style=MaterialTheme.typography.labelSmall, color=SoulMateTheme.colors.accentColor)
                     }
                     
                     Spacer(modifier = Modifier.height(8.dp))
                     
                     // Progress Bar
                     Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha=0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(1f - load) // Clarity is inverse of load
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF00E5FF), Color(0xFFD500F9))
                                    )
                                )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                         // Organize Button
                         Button(
                             onClick = { 
                                 // Trigger cleanup
                                 // viewModel.triggerCleanup() // Need to implement if not auto
                                 Toast.makeText(context, "整理思绪中... (Background Worker Started)", Toast.LENGTH_SHORT).show()
                             },
                             colors = ButtonDefaults.buttonColors(
                                 containerColor = SoulMateTheme.colors.cardBg.copy(alpha=0.5f),
                                 contentColor = SoulMateTheme.colors.accentColor
                             ),
                             border = androidx.compose.foundation.BorderStroke(1.dp, SoulMateTheme.colors.accentColor),
                             contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                             modifier = Modifier.height(36.dp)
                         ) {
                             Text("Crystalize Moments (凝结时光)", fontSize = 12.sp)
                         }
                         
                         Spacer(modifier = Modifier.width(16.dp))
                         
                         // Retention Config (Hidden in deep press or simplified)
                         // Keeping it simple as a text/dropdown for now but minimized?
                         // Or just keeping existing dropdown below?
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    NeonDropdown(
                        label = "Retention Policy (保留策略)",
                        currentValue = getRetentionName(memoryRetentionDays),
                        options = listOf("永久", "90 天", "180 天", "365 天"),
                        onOptionSelected = { selectedName ->
                            val days = retentionNameToDays(selectedName)
                            viewModel.updateMemoryRetentionDays(days)
                        }
                    )
                 }
            }
            
            // -- 3. Safety Protocols --
            SettingsSection(title = "心识守望 (MIND GUARDIAN)", icon = Icons.Default.Shield) {
                NeonActionItem(
                    icon = Icons.Default.Warning,
                    title = "守护热线",
                    subtitle = "Emergency Support",
                    onClick = onNavigateToResources
                )
                Spacer(modifier = Modifier.height(12.dp))
                NeonActionItem(
                    icon = Icons.Default.Psychology,
                    title = "共鸣周报",
                    subtitle = "Mental Health Report",
                    onClick = onNavigateToReport
                )
            }
            
            // -- 4. System --
            SettingsSection(title = "系统 (SYSTEM)", icon = Icons.Default.Info) {
                 NeonActionItem(
                     icon = Icons.Default.Info,
                     title = "Version 1.0.0",
                     subtitle = "Build 2026.01.25",
                     onClick = {},
                     onLongClick = {
                         viewModel.setCheatScore(900)
                         Toast.makeText(context, "Developer Mode: Level Set to SOULMATE", Toast.LENGTH_SHORT).show()
                     }
                 )
                 Spacer(modifier = Modifier.height(12.dp))
                 NeonActionItem(
                     icon = Icons.Default.Favorite,
                     title = "SoulMate Origins",
                     subtitle = "Lucian & Eleanor",
                     onClick = {}
                 )
                 
                 Spacer(modifier = Modifier.height(12.dp))
                 
                 NeonActionItem(
                     icon = Icons.Default.Memory,
                     title = "重置记忆 (Reset Memory)",
                     subtitle = "清除所有并写入初始记忆",
                     onClick = {
                         viewModel.resetMemoryForUser(
                             onSuccess = {
                                 Toast.makeText(context, "记忆重置成功！", Toast.LENGTH_LONG).show()
                             },
                             onError = { msg ->
                                 Toast.makeText(context, "重置失败：$msg", Toast.LENGTH_SHORT).show()
                             }
                         )
                     }
                 )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// -----------------------------------------------------------------------------
// 🔮 Neon Components
// -----------------------------------------------------------------------------

@Composable
fun IntimacyDashboard(
    score: Int, 
    levelName: String, 
    nextLevelScore: Int,
    nextAnniversary: Pair<com.soulmate.data.model.AnniversaryEntity, Int>? = null,
    affinityScore: Int = 60,
    affinityLevel: com.soulmate.data.repository.AffinityRepository.AffinityLevel = com.soulmate.data.repository.AffinityRepository.AffinityLevel.NORMAL
) {
    val progress = if (nextLevelScore > 0) {
        score.toFloat() / nextLevelScore.toFloat()
    } else {
        1f
    }.coerceIn(0f, 1f)
    
     val displayLevelName = when (levelName) {
        "Stranger" -> "STRANGER"
        "Friend" -> "FRIEND"
        "Crush" -> "CRUSH"
        "Soulmate" -> "SOULMATE"
        else -> levelName.uppercase()
    }
    
    val levelColor = when (levelName) {
        "Stranger" -> Color.Gray
        "Friend" -> Color(0xFF4CAF50)
        "Crush" -> Color(0xFFFF4081)
        "Soulmate" -> Color(0xFFD500F9)
        else -> Color.Gray
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha=0.6f))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFD500F9))),
                RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Favorite, 
                    null, 
                    tint = levelColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SYNCHRONICITY",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$score / ${if(nextLevelScore>0) nextLevelScore else "MAX"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = displayLevelName,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Neon Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha=0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(levelColor.copy(alpha=0.5f), levelColor)
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // -- New Affinity (Mood) Display --
            val moodColor = when(affinityLevel) {
                com.soulmate.data.repository.AffinityRepository.AffinityLevel.LOVE -> Color(0xFFFF69B4) // Pink
                com.soulmate.data.repository.AffinityRepository.AffinityLevel.COLD -> Color(0xFF78909C) // Grey
                else -> Color(0xFF00B0FF) // Blue
            }
            val moodName = when(affinityLevel) {
                com.soulmate.data.repository.AffinityRepository.AffinityLevel.LOVE -> "SWEET (甜蜜)"
                com.soulmate.data.repository.AffinityRepository.AffinityLevel.COLD -> "COLD WAR (冷战)"
                else -> "NORMAL (稳定)"
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Psychology, // Brain/Psychology icon for Mood
                    null, 
                    tint = moodColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CURRENT MOOD",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$affinityScore / 100",
                    style = MaterialTheme.typography.labelSmall,
                    color = moodColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = moodName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            // Mood Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha=0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(affinityScore / 100f)
                        .fillMaxHeight()
                        .background(moodColor)
                )
            }
            
            // Next Milestone (Anniversary)
            if (nextAnniversary != null) {
                val (entity, days) = nextAnniversary
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DateRange, 
                        contentDescription = "Milestone",
                        tint = SoulMateTheme.colors.accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEXT MILESTONE: ${entity.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (days == 0) "TODAY" else "IN $days DAYS",
                        style = MaterialTheme.typography.labelSmall,
                        color = SoulMateTheme.colors.accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String, 
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        ) {
            Icon(icon, null, tint = SoulMateTheme.colors.accentColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = SoulMateTheme.colors.accentColor,
                fontWeight = FontWeight.Bold
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SoulMateTheme.colors.cardBg.copy(alpha=0.3f))
                .border(1.dp, SoulMateTheme.colors.cardBorder.copy(alpha=0.3f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun NeonInputItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(label, style=MaterialTheme.typography.labelSmall, color=Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.White.copy(alpha=0.05f), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                singleLine = true,
                cursorBrush = Brush.verticalGradient(listOf(Color(0xFF00E5FF), Color(0xFFD500F9)))
            )
        }
    }
}

@Composable
fun NeonDropdown(
    label: String,
    currentValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Text(label, style=MaterialTheme.typography.labelSmall, color=Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
             modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.White.copy(alpha=0.05f), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                 modifier = Modifier.fillMaxWidth(),
                 horizontalArrangement = Arrangement.SpaceBetween,
                 verticalAlignment = Alignment.CenterVertically
            ) {
                Text(currentValue, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray)
            }
            
            DropdownMenu(
                 expanded = expanded,
                 onDismissRequest = { expanded = false },
                 modifier = Modifier.background(Color(0xFF1A1A2E))
            ) {
                 options.forEach { option ->
                     DropdownMenuItem(
                         text = { Text(option, color = Color.White) },
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

@Composable
fun NeonSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style=MaterialTheme.typography.labelSmall, color=Color.Gray)
            Text("$value%", style=MaterialTheme.typography.labelSmall, color=SoulMateTheme.colors.accentColor)
        }
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00E5FF),
                activeTrackColor = Color(0xFFD500F9),
                inactiveTrackColor = Color.White.copy(alpha=0.1f)
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NeonActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha=0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.Gray)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ArrowBack, null, modifier = Modifier.graphicsLayer { rotationZ = 180f }, tint = Color.Gray.copy(alpha=0.3f))
    }
}

// -----------------------------------------------------------------------------
// 🧠 Helpers
// -----------------------------------------------------------------------------

private fun getRelationshipName(name: String): String {
    return when (name) {
        "ASSISTANT" -> "助手"
        "COMPANION" -> "伙伴"
        "LOVER" -> "恋人"
        else -> name
    }
}

private fun getGenderName(gender: UserGender): String {
    return when (gender) {
        UserGender.MALE -> "男"
        UserGender.FEMALE -> "女"
        UserGender.UNSET -> "未设置"
    }
}

private fun getRetentionName(days: Long): String {
    return when (days) {
        0L -> "永久"
        90L -> "90 天"
        180L -> "180 天"
        365L -> "365 天"
        else -> "$days 天"
    }
}

private fun retentionNameToDays(name: String): Long {
    return when (name) {
        "永久" -> 0L
        "90 天" -> 90L
        "180 天" -> 180L
        "365 天" -> 365L
        else -> 0L
    }
}

private fun getLevelName(score: Int): String {
    return when {
        score >= IntimacyManager.THRESHOLD_LOVER -> "Soulmate"
        score >= IntimacyManager.THRESHOLD_CRUSH -> "Crush"
        score >= IntimacyManager.THRESHOLD_FRIEND -> "Friend"
        else -> "Stranger"
    }
}

private fun getNextLevelScore(score: Int): Int {
    return when {
        score >= IntimacyManager.THRESHOLD_LOVER -> IntimacyManager.MAX_SCORE
        score >= IntimacyManager.THRESHOLD_CRUSH -> IntimacyManager.THRESHOLD_LOVER
        score >= IntimacyManager.THRESHOLD_FRIEND -> IntimacyManager.THRESHOLD_CRUSH
        else -> IntimacyManager.THRESHOLD_FRIEND
    }
}
