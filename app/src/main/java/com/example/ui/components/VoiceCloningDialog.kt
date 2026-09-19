package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.audio.AudioRecorderManager
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TelegramCyan
import com.example.ui.theme.TelegramDarkBg
import com.example.ui.theme.TelegramDarkCard
import com.example.ui.theme.TelegramDarkSurface
import com.example.ui.theme.TelegramPurple
import java.io.File
import java.util.Locale

@Composable
fun VoiceCloningDialog(
    recorderManager: AudioRecorderManager,
    isCloning: Boolean,
    onCloneFromFile: (audioFile: File, voiceName: String) -> Unit,
    onCloneDemo: (voiceName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var voiceName by remember { mutableStateOf("My Cloned Voice") }
    val recordState by recorderManager.recordingState.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            recorderManager.startRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required to record audio", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = TelegramDarkSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("voice_cloning_dialog")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(TelegramPurple, TelegramCyan)))
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Cartesia Voice Cloning",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Instant neural voice replication",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Voice Name Field
                OutlinedTextField(
                    value = voiceName,
                    onValueChange = { voiceName = it },
                    label = { Text("Voice Name") },
                    placeholder = { Text("e.g., Alex's Voice") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TelegramCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = TelegramCyan,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clone_voice_name_input")
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Microphone Recording Centerpiece
                Surface(
                    color = TelegramDarkCard,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = if (recordState.isRecording) "Recording... Speak clearly into mic" else "Record 5-10 seconds of speech",
                            color = if (recordState.isRecording) TelegramCyan else Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Mic Pulse Ring
                        val infiniteTransition = rememberInfiniteTransition(label = "recPulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = if (recordState.isRecording) 1.25f else 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse"
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .scale(if (recordState.isRecording) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(
                                    if (recordState.isRecording) Color(0xFFE53935) else TelegramBlue
                                )
                                .clickable {
                                    if (recordState.isRecording) {
                                        recorderManager.stopRecording()
                                    } else {
                                        if (hasPermission) {
                                            recorderManager.startRecording()
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }
                                .testTag("record_mic_button")
                        ) {
                            Icon(
                                imageVector = if (recordState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (recordState.isRecording) "Stop" else "Record",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Timer text
                        val sec = recordState.durationSeconds
                        val timerString = String.format(Locale.US, "%02d:%02d / 00:10", sec / 60, sec % 60)
                        Text(
                            text = timerString,
                            color = if (recordState.isRecording) TelegramCyan else Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Status of recorded file
                        if (recordState.recordedFile != null && !recordState.isRecording) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "✓ Clip recorded (${sec}s). Ready to clone!",
                                color = Color(0xFF4FAE4E),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Clone from recorded file OR Instant Demo Sample
                if (isCloning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = TelegramCyan,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Cloning with Cartesia neural engine...",
                            color = TelegramCyan,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    // Clone Recorded Audio
                    Button(
                        onClick = {
                            val file = recordState.recordedFile
                            if (file != null && file.exists()) {
                                onCloneFromFile(file, voiceName)
                            } else {
                                Toast.makeText(context, "Please record a clip first, or use the Demo button below", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = recordState.recordedFile != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TelegramBlue,
                            disabledContainerColor = Color.White.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_clone_button")
                    ) {
                        Text(
                            text = "🧬 Clone Recorded Voice",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // One-tap Instant Demo Voice Clone
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TelegramCyan.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onCloneDemo(voiceName.ifBlank { "Sonic Clone" })
                            }
                            .testTag("instant_demo_clone_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = TelegramCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🧪 Instant Clone Demo Sample",
                                color = TelegramCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
