package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Language
import com.example.data.model.Voice
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TelegramCyan
import com.example.ui.theme.TelegramDarkSurface
import com.example.ui.theme.TelegramLightBlue

@Composable
fun TelegramHeader(
    botStatus: String,
    currentLanguage: Language,
    currentVoice: Voice,
    onLanguageClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onCloneClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = TelegramDarkSurface,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Bot Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(TelegramBlue, TelegramCyan)
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Bot Avatar",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Bot Info (Name + Status)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onVoiceClick() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Cartesia Voice Bot",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified Bot",
                        tint = TelegramLightBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = TelegramBlue.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "bot",
                            color = TelegramLightBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Subtitle / Status
                val isBusy = botStatus != "online"
                val transition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by transition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (isBusy) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(TelegramCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = botStatus,
                            color = TelegramCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "${currentLanguage.flagEmoji} ${currentLanguage.code.uppercase()} • 🎙️ ${currentVoice.name}",
                            color = Color(0xFF8DA3B5),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Quick Language Button
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .clickable { onLanguageClick() }
                    .testTag("header_language_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = currentLanguage.flagEmoji,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = currentLanguage.code.uppercase(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Overflow Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.testTag("header_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("🌐 Select Language") },
                        onClick = {
                            showMenu = false
                            onLanguageClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🎭 Change Voice (${currentVoice.name})") },
                        onClick = {
                            showMenu = false
                            onVoiceClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🧬 Clone a New Voice") },
                        onClick = {
                            showMenu = false
                            onCloneClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🔄 Restart Bot") },
                        onClick = {
                            showMenu = false
                            onResetClick()
                        }
                    )
                }
            }
        }
    }
}
