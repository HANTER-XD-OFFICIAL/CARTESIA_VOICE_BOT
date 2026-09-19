package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.TelegramChatScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TelegramDarkBg
import com.example.viewmodel.TelegramBotViewModel

class MainActivity : ComponentActivity() {
    private val botViewModel: TelegramBotViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    color = TelegramDarkBg,
                    modifier = Modifier.fillMaxSize()
                ) {
                    TelegramChatScreen(viewModel = botViewModel)
                }
            }
        }
    }
}

