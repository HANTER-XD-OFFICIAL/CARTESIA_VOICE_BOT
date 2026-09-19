package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TelegramCyan
import com.example.ui.theme.TelegramDarkCard
import com.example.ui.theme.TelegramDarkSurface

@Composable
fun ChatInputField(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onOpenVoiceCloning: () -> Unit,
    onOpenVoiceSelector: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = TelegramDarkSurface,
        tonalElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Voice Clone / Quick Action Button
            IconButton(
                onClick = onOpenVoiceCloning,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("chat_input_clone_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Quick Actions / Clone Voice",
                    tint = TelegramCyan,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Input pill box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(
                        color = TelegramDarkCard,
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "Write text for bot to speak...",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 15.sp
                    )
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(TelegramCyan),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && !isGenerating) {
                                onSendMessage(inputText)
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_text_input")
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Send Button or Voice Record Button
            if (isGenerating) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp)
                ) {
                    CircularProgressIndicator(
                        color = TelegramCyan,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (inputText.isNotBlank()) {
                // Send button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(TelegramBlue)
                        .testTag("chat_send_button")
                ) {
                    IconButton(
                        onClick = { onSendMessage(inputText) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                // Microphone icon to open Voice Clone Recorder
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(TelegramDarkCard)
                        .testTag("chat_mic_record_button")
                ) {
                    IconButton(
                        onClick = onOpenVoiceCloning,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Record & Clone Voice",
                            tint = TelegramCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
