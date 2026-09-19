package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioPlaybackState
import com.example.data.model.ChatMessage
import com.example.data.model.InlineButton
import com.example.data.model.MessageSender
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TelegramCyan
import com.example.ui.theme.TelegramDarkBotBubble
import com.example.ui.theme.TelegramDarkButton
import com.example.ui.theme.TelegramDarkButtonHover
import com.example.ui.theme.TelegramDarkUserBubble
import com.example.ui.theme.TelegramLightBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    playbackState: AudioPlaybackState,
    onPlayAudio: (messageId: String, path: String) -> Unit,
    onPauseAudio: () -> Unit,
    onSeekAudio: (Long) -> Unit,
    onToggleSpeed: () -> Unit,
    onInlineButtonClick: (InlineButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val isBot = message.sender == MessageSender.BOT
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }

    Column(
        horizontalAlignment = if (isBot) Alignment.Start else Alignment.End,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Message Bubble
        Surface(
            color = if (isBot) TelegramDarkBotBubble else TelegramDarkUserBubble,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isBot) 4.dp else 16.dp,
                bottomEnd = if (isBot) 16.dp else 4.dp
            ),
            modifier = Modifier
                .widthIn(min = 120.dp, max = 340.dp)
                .testTag("chat_bubble_${message.id}")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Sender label for Bot
                if (isBot) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "Cartesia Voice Bot",
                            color = TelegramLightBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Audio message content
                if (message.audioFilePath != null) {
                    val isCurrentTrack = playbackState.currentMessageId == message.id
                    val isPlaying = isCurrentTrack && playbackState.isPlaying
                    val currentPos = if (isCurrentTrack) playbackState.currentPositionMs else 0L
                    val totalDuration = if (isCurrentTrack && playbackState.durationMs > 0) {
                        playbackState.durationMs
                    } else {
                        message.audioDurationMs.coerceAtLeast(1000L)
                    }

                    AudioMessageContent(
                        messageId = message.id,
                        audioPath = message.audioFilePath,
                        isPlaying = isPlaying,
                        currentPosMs = currentPos,
                        totalDurationMs = totalDuration,
                        playbackSpeed = playbackState.speed,
                        voiceName = message.voiceUsed?.name ?: "Cartesia Voice",
                        langCode = message.languageUsed?.code?.uppercase() ?: "EN",
                        isCloned = message.voiceUsed?.isCloned == true,
                        onPlayPause = {
                            if (isPlaying) {
                                onPauseAudio()
                            } else {
                                onPlayAudio(message.id, message.audioFilePath)
                            }
                        },
                        onSeek = onSeekAudio,
                        onToggleSpeed = onToggleSpeed
                    )
                }

                // Text Content
                if (!message.text.isNullOrBlank()) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                // Timestamp & Status
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = formattedTime,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                    if (!isBot) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Read",
                            tint = TelegramCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Inline Action Buttons (Telegram keyboard)
        if (message.inlineButtons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .padding(start = if (isBot) 0.dp else 40.dp)
            ) {
                message.inlineButtons.forEach { btn ->
                    TelegramInlineButton(
                        button = btn,
                        onClick = { onInlineButtonClick(btn) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioMessageContent(
    messageId: String,
    audioPath: String,
    isPlaying: Boolean,
    currentPosMs: Long,
    totalDurationMs: Long,
    playbackSpeed: Float,
    voiceName: String,
    langCode: String,
    isCloned: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleSpeed: () -> Unit
) {
    val progress = if (totalDurationMs > 0) {
        (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Tag with voice info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = TelegramCyan,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Voice: $voiceName • $langCode",
                color = TelegramLightBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            if (isCloned) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFF9D65E8).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "🧬 Cloned",
                        color = Color(0xFFD0BCFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Voice Player Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Play/Pause circular button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(TelegramBlue)
                    .clickable { onPlayPause() }
                    .testTag("play_pause_button_$messageId")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Waveform & Timers
            Column(modifier = Modifier.weight(1f)) {
                // Visual Waveform bars
                TelegramWaveform(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clickable {
                            // Quick jump along waveform
                        }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Time counters + Speed Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentSec = currentPosMs / 1000
                    val totalSec = totalDurationMs / 1000
                    val timeText = if (isPlaying || currentPosMs > 0) {
                        String.format(Locale.US, "%02d:%02d / %02d:%02d", currentSec / 60, currentSec % 60, totalSec / 60, totalSec % 60)
                    } else {
                        String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
                    }

                    Text(
                        text = timeText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // Speed Chip (1X, 1.5X, 2X)
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onToggleSpeed() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = TelegramCyan,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Telegram-style audio message waveform visualization
 */
@Composable
private fun TelegramWaveform(
    progress: Float,
    modifier: Modifier = Modifier
) {
    // 28 simulated waveform bar heights
    val barHeights = remember {
        listOf(
            0.3f, 0.5f, 0.8f, 0.6f, 0.4f, 0.9f, 0.7f, 0.5f, 0.95f, 0.6f,
            0.4f, 0.7f, 0.85f, 0.5f, 0.9f, 0.75f, 0.45f, 0.8f, 0.65f, 0.35f,
            0.7f, 0.9f, 0.6f, 0.85f, 0.5f, 0.4f, 0.6f, 0.3f
        )
    }

    val activeColor = TelegramCyan
    val inactiveColor = Color.White.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
        val totalBars = barHeights.size
        val barWidth = 3.dp.toPx()
        val spacing = (size.width - (totalBars * barWidth)) / (totalBars - 1)

        barHeights.forEachIndexed { index, heightRatio ->
            val x = index * (barWidth + spacing)
            val barHeight = (size.height * heightRatio).coerceAtLeast(4.dp.toPx())
            val y = (size.height - barHeight) / 2f

            val isPassed = (index.toFloat() / totalBars) <= progress
            val barColor = if (isPassed) activeColor else inactiveColor

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

@Composable
fun TelegramInlineButton(
    button: InlineButton,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = TelegramDarkButton,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag("inline_btn_${button.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = button.text,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
