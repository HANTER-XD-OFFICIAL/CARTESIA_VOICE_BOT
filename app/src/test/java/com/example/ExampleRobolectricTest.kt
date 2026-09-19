package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ButtonAction
import com.example.data.model.InlineButton
import com.example.data.model.Language
import com.example.viewmodel.TelegramBotViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Cartesia Voice Bot", appName)
  }

  @Test
  fun `bot initializes with greeting and language buttons`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = TelegramBotViewModel(context)
    val state = viewModel.uiState.value

    assertEquals(1, state.messages.size)
    val firstMsg = state.messages.first()
    assertTrue(firstMsg.text?.contains("Cartesia Voice Bot") == true)
    assertTrue(firstMsg.inlineButtons.isNotEmpty())
    assertEquals(Language.ALL.size, firstMsg.inlineButtons.size)
  }

  @Test
  fun `selecting language presents feature buttons`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = TelegramBotViewModel(context)

    viewModel.onInlineButtonClicked(
      InlineButton(
        id = "lang_en",
        text = "🇬🇧 English",
        action = ButtonAction.SELECT_LANGUAGE,
        payload = "en"
      )
    )

    val state = viewModel.uiState.value
    // Should have greeting + user response + bot features response
    assertEquals(3, state.messages.size)
    val featureMsg = state.messages.last()
    assertTrue(featureMsg.text?.contains("Language set to") == true)
    val cloneBtn = featureMsg.inlineButtons.find { it.action == ButtonAction.FEATURE_VOICE_CLONING }
    assertNotNull(cloneBtn)
  }
}

