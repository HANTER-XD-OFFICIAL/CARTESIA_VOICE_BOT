package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ButtonAction
import com.example.data.model.InlineButton
import com.example.ui.components.ApiKeyDialog
import com.example.ui.components.ChatInputField
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.LanguageSelectorDialog
import com.example.ui.components.TelegramHeader
import com.example.ui.components.VoiceCloningDialog
import com.example.ui.components.VoiceSelectorDialog
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TelegramCyan
import com.example.ui.theme.TelegramDarkBg
import com.example.ui.theme.TelegramDarkButton
import com.example.ui.theme.TelegramDarkCard
import com.example.ui.theme.TelegramDarkSurface
import com.example.ui.theme.TelegramPurple
import com.example.viewmodel.TelegramBotViewModel

@Composable
fun TelegramChatScreen(
    viewModel: TelegramBotViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.audioPlayer.playbackState.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when messages update
    LaunchedEffect(uiState.messages.size, uiState.isGeneratingAudio) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TelegramHeader(
                botStatus = uiState.botStatusText,
                currentLanguage = uiState.selectedLanguage,
                currentVoice = uiState.selectedVoice,
                onLanguageClick = { viewModel.openLanguageSelector() },
                onVoiceClick = { viewModel.openVoiceSelector() },
                onCloneClick = { viewModel.openVoiceCloning() },
                onResetClick = { viewModel.resetConversation() },
                onSettingsClick = { viewModel.openApiKeyDialog() }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                // Quick Action Bar (Telegram Reply Keyboard shortcuts)
                QuickActionKeyboard(
                    currentVoiceName = uiState.selectedVoice.name,
                    isVoiceCloned = uiState.selectedVoice.isCloned,
                    onActionClick = { action ->
                        when (action) {
                            "clone" -> viewModel.openVoiceCloning()
                            "voice" -> viewModel.openVoiceSelector()
                            "lang" -> viewModel.openLanguageSelector()
                            "sample" -> viewModel.onInlineButtonClicked(
                                InlineButton("quick_sample", "", ButtonAction.ACTION_TRY_SAMPLE_TEXT, "Hello! Testing Cartesia Sonic ultra-realistic voice generation.")
                            )
                        }
                    }
                )

                ChatInputField(
                    inputText = uiState.inputText,
                    onInputTextChanged = { viewModel.onInputTextChanged(it) },
                    onSendMessage = { viewModel.onUserSentText(it) },
                    onOpenVoiceCloning = { viewModel.openVoiceCloning() },
                    onOpenVoiceSelector = { viewModel.openVoiceSelector() },
                    isGenerating = uiState.isGeneratingAudio
                )
            }
        },
        containerColor = TelegramDarkBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(TelegramDarkBg)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("chat_messages_list")
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ChatMessageItem(
                        message = message,
                        playbackState = playbackState,
                        onPlayAudio = { id, path -> viewModel.audioPlayer.play(id, path) },
                        onPauseAudio = { viewModel.audioPlayer.pause() },
                        onSeekAudio = { viewModel.audioPlayer.seekTo(it) },
                        onToggleSpeed = { viewModel.audioPlayer.toggleSpeed() },
                        onInlineButtonClick = { viewModel.onInlineButtonClicked(it) }
                    )
                }

                // Generating indicator bubble if bot is thinking
                if (uiState.isGeneratingAudio) {
                    item {
                        GeneratingVoiceBubble(uiState.selectedVoice.name)
                    }
                }
            }
        }
    }

    // Dialogs
    if (uiState.showVoiceCloningDialog) {
        VoiceCloningDialog(
            recorderManager = viewModel.audioRecorder,
            isCloning = uiState.isCloningVoice,
            onCloneFromFile = { file, name -> viewModel.cloneVoiceFromFile(file, name) },
            onCloneDemo = { name -> viewModel.performDemoVoiceClone(name) },
            onDismiss = { viewModel.dismissVoiceCloningDialog() }
        )
    }

    if (uiState.showVoiceSelectorDialog) {
        VoiceSelectorDialog(
            availableVoices = uiState.availableVoices,
            selectedVoice = uiState.selectedVoice,
            onSelectVoice = { viewModel.selectVoice(it) },
            onOpenCloneDialog = { viewModel.openVoiceCloning() },
            onDismiss = { viewModel.dismissVoiceSelectorDialog() }
        )
    }

    if (uiState.showLanguageSelectorDialog) {
        LanguageSelectorDialog(
            selectedLanguage = uiState.selectedLanguage,
            onSelectLanguage = { viewModel.selectLanguage(it) },
            onDismiss = { viewModel.dismissLanguageSelectorDialog() }
        )
    }

    if (uiState.showApiKeyDialog) {
        ApiKeyDialog(
            currentApiKey = viewModel.getApiKey(),
            onSaveKey = { viewModel.updateApiKey(it) },
            onDismiss = { viewModel.dismissApiKeyDialog() }
        )
    }
}

@Composable
private fun QuickActionKeyboard(
    currentVoiceName: String,
    isVoiceCloned: Boolean,
    onActionClick: (String) -> Unit
) {
    Surface(
        color = TelegramDarkSurface.copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                QuickActionChip(
                    icon = Icons.Default.GraphicEq,
                    label = "🧬 Clone Voice",
                    tint = TelegramPurple,
                    onClick = { onActionClick("clone") }
                )
            }
            item {
                QuickActionChip(
                    icon = Icons.Default.RecordVoiceOver,
                    label = "🎭 Voice: $currentVoiceName",
                    tint = TelegramCyan,
                    onClick = { onActionClick("voice") }
                )
            }
            item {
                QuickActionChip(
                    icon = Icons.Default.Language,
                    label = "🌐 Language",
                    tint = TelegramBlue,
                    onClick = { onActionClick("lang") }
                )
            }
            item {
                QuickActionChip(
                    icon = Icons.Default.SmartToy,
                    label = "⚡ Test Speech",
                    tint = Color(0xFF4FAE4E),
                    onClick = { onActionClick("sample") }
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        color = TelegramDarkButton,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GeneratingVoiceBubble(voiceName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Surface(
            color = TelegramDarkCard,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                CircularProgressIndicator(
                    color = TelegramCyan,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Generating audio with $voiceName...",
                    color = TelegramCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
