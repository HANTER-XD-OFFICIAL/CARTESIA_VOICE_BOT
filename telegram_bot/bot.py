"""
Cartesia Voice & Cloning Telegram Bot
Features:
- Language selection with interactive inline keyboard
- Voice generation with Cartesia Sonic TTS
- Voice cloning from Telegram audio/voice messages
- Preset & custom voice selection
- Ready for Render 24/7 background deployment
"""

import os
import io
import json
import logging
import asyncio
from typing import Dict, Any

import httpx
from telegram import (
    Update,
    InlineKeyboardButton,
    InlineKeyboardMarkup,
)
from telegram.constants import ChatAction, ParseMode
from telegram.ext import (
    Application,
    CommandHandler,
    CallbackQueryHandler,
    MessageHandler,
    ContextTypes,
    filters,
)

# ---------------- Configuration & Logging ----------------
logging.basicConfig(
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    level=logging.INFO
)
logger = logging.getLogger(__name__)

TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")
CARTESIA_API_KEY = os.getenv("CARTESIA_API_KEY", "sk_car_x62gquQgEdVchAVtPCxcue")

CARTESIA_BASE_URL = "https://api.cartesia.ai"
CARTESIA_VERSION = "2024-06-10"

# Supported Languages
LANGUAGES = {
    "en": {"name": "English 🇬🇧", "default_voice": "a0e99841-438c-4a64-b679-ae501e7d6091"},
    "es": {"name": "Spanish 🇪🇸", "default_voice": "846d35e9-dc05-4526-ba13-34c47fb6f6fe"},
    "fr": {"name": "French 🇫🇷", "default_voice": "a0e99841-438c-4a64-b679-ae501e7d6091"},
    "de": {"name": "German 🇩🇪", "default_voice": "a0e99841-438c-4a64-b679-ae501e7d6091"},
    "ja": {"name": "Japanese 🇯🇵", "default_voice": "2b568345-1d48-4047-b25f-7baccf842eb0"},
    "pt": {"name": "Portuguese 🇧🇷", "default_voice": "a0e99841-438c-4a64-b679-ae501e7d6091"},
    "zh": {"name": "Chinese 🇨🇳", "default_voice": "2b568345-1d48-4047-b25f-7baccf842eb0"},
}

# Standard Preset Voices
PRESET_VOICES = {
    "barbershop": {"name": "Barbershop Man 🎙️", "id": "a0e99841-438c-4a64-b679-ae501e7d6091"},
    "calm_lady": {"name": "Calm Narrator 🌸", "id": "846d35e9-dc05-4526-ba13-34c47fb6f6fe"},
    "storyteller": {"name": "Storyteller 📖", "id": "2b568345-1d48-4047-b25f-7baccf842eb0"},
    "friendly": {"name": "Friendly Assistant ⚡", "id": "69267136-1bdc-4106-96a6-1c024d3f9aa9"},
}

# ---------------- User State Management ----------------
def get_user_state(context: ContextTypes.DEFAULT_TYPE) -> Dict[str, Any]:
    if "settings" not in context.user_data:
        context.user_data["settings"] = {
            "language": "en",
            "voice_id": "a0e99841-438c-4a64-b679-ae501e7d6091",
            "voice_name": "Barbershop Man 🎙️",
            "awaiting_clone": False,
        }
    return context.user_data["settings"]


# ---------------- Keyboards ----------------
def get_language_keyboard() -> InlineKeyboardMarkup:
    keyboard = []
    row = []
    for code, data in LANGUAGES.items():
        row.append(InlineKeyboardButton(data["name"], callback_data=f"lang_{code}"))
        if len(row) == 2:
            keyboard.append(row)
            row = []
    if row:
        keyboard.append(row)
    return InlineKeyboardMarkup(keyboard)


def get_main_menu_keyboard() -> InlineKeyboardMarkup:
    keyboard = [
        [
            InlineKeyboardButton("🎙️ Select Voice", callback_data="menu_voices"),
            InlineKeyboardButton("🌐 Change Language", callback_data="menu_lang"),
        ],
        [
            InlineKeyboardButton("🧬 Clone a Voice", callback_data="menu_clone"),
            InlineKeyboardButton("⚙️ Status & Info", callback_data="menu_status"),
        ],
    ]
    return InlineKeyboardMarkup(keyboard)


def get_voice_selection_keyboard() -> InlineKeyboardMarkup:
    keyboard = []
    for key, voice in PRESET_VOICES.items():
        keyboard.append([InlineKeyboardButton(voice["name"], callback_data=f"voice_{voice['id']}_{key}")])
    keyboard.append([InlineKeyboardButton("🔙 Back to Menu", callback_data="menu_main")])
    return InlineKeyboardMarkup(keyboard)


# ---------------- Cartesia API Calls ----------------
async def cartesia_tts(text: str, voice_id: str, language: str) -> bytes:
    """Call Cartesia /tts/bytes endpoint and return WAV audio bytes."""
    headers = {
        "X-API-Key": CARTESIA_API_KEY,
        "Cartesia-Version": CARTESIA_VERSION,
        "Content-Type": "application/json",
    }
    payload = {
        "model_id": "sonic",
        "transcript": text,
        "voice": {
            "mode": "id",
            "id": voice_id,
        },
        "output_format": {
            "container": "wav",
            "encoding": "pcm_s16le",
            "sample_rate": 44100,
        },
        "language": language,
    }

    async with httpx.AsyncClient(timeout=60.0) as client:
        response = await client.post(
            f"{CARTESIA_BASE_URL}/tts/bytes",
            headers=headers,
            json=payload,
        )
        if response.status_code != 200:
            logger.error(f"Cartesia TTS failed ({response.status_code}): {response.text}")
            raise RuntimeError(f"Cartesia API Error: {response.text}")
        return response.content


async def cartesia_clone_voice(audio_bytes: bytes, voice_name: str) -> Dict[str, Any]:
    """Clone a voice using Cartesia /voices/clone/clip endpoint."""
    headers = {
        "X-API-Key": CARTESIA_API_KEY,
        "Cartesia-Version": CARTESIA_VERSION,
    }
    files = {
        "clip": ("sample.wav", audio_bytes, "audio/wav")
    }
    data = {
        "name": voice_name,
        "description": "Cloned via Cartesia Telegram Bot",
    }

    async with httpx.AsyncClient(timeout=60.0) as client:
        response = await client.post(
            f"{CARTESIA_BASE_URL}/voices/clone/clip",
            headers=headers,
            data=data,
            files=files,
        )
        if response.status_code not in (200, 201):
            logger.error(f"Cartesia Clone failed ({response.status_code}): {response.text}")
            raise RuntimeError(f"Clone API Error: {response.text}")
        return response.json()


# ---------------- Command & Callback Handlers ----------------
async def start_command(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    user = update.effective_user
    welcome_text = (
        f"👋 **Hello {user.first_name}!**\n\n"
        "Welcome to **Cartesia Voice Bot** — your AI Voice Generation and Voice Cloning studio!\n\n"
        "👉 **Step 1:** Please select your preferred language to begin:"
    )
    await update.message.reply_text(
        welcome_text,
        reply_markup=get_language_keyboard(),
        parse_mode=ParseMode.MARKDOWN
    )


async def button_callback_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()

    data = query.data
    settings = get_user_state(context)

    if data.startswith("lang_"):
        lang_code = data.replace("lang_", "")
        if lang_code in LANGUAGES:
            settings["language"] = lang_code
            lang_info = LANGUAGES[lang_code]
            response_text = (
                f"✅ **Language set to:** {lang_info['name']}\n\n"
                "What would you like to do? Choose an option below, or just type any text to generate audio!"
            )
            await query.edit_message_text(
                response_text,
                reply_markup=get_main_menu_keyboard(),
                parse_mode=ParseMode.MARKDOWN
            )

    elif data == "menu_main":
        await query.edit_message_text(
            "🎛️ **Main Menu:**\nChoose an action below, or type text to synthesize voice:",
            reply_markup=get_main_menu_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )

    elif data == "menu_lang":
        await query.edit_message_text(
            "🌐 **Select your preferred language:**",
            reply_markup=get_language_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )

    elif data == "menu_voices":
        await query.edit_message_text(
            "🎙️ **Choose a voice model:**",
            reply_markup=get_voice_selection_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )

    elif data.startswith("voice_"):
        parts = data.split("_")
        voice_id = parts[1]
        voice_key = parts[2] if len(parts) > 2 else "custom"
        voice_name = PRESET_VOICES.get(voice_key, {}).get("name", "Custom Voice")

        settings["voice_id"] = voice_id
        settings["voice_name"] = voice_name

        await query.edit_message_text(
            f"✅ **Voice set to:** {voice_name}\n\n"
            "Now send me any text message and I will convert it to speech!",
            reply_markup=get_main_menu_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )

    elif data == "menu_status":
        status_text = (
            "⚙️ **Current Configuration:**\n\n"
            f"🌐 **Language:** {LANGUAGES.get(settings['language'], {}).get('name', settings['language'])}\n"
            f"🎙️ **Active Voice:** {settings['voice_name']}\n"
            f"🔑 **Voice ID:** `{settings['voice_id']}`\n\n"
            "💬 *Send any text message to synthesize speech!*"
        )
        await query.edit_message_text(
            status_text,
            reply_markup=get_main_menu_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )

    elif data == "menu_clone":
        settings["awaiting_clone"] = True
        clone_prompt = (
            "🧬 **Voice Cloning Mode**\n\n"
            "Please record a **Voice Note** (5-15 seconds) or upload an audio file containing clean speech.\n\n"
            "Cartesia AI will clone your voice and activate it for text-to-speech!"
        )
        await query.edit_message_text(
            clone_prompt,
            reply_markup=InlineKeyboardMarkup([[
                InlineKeyboardButton("❌ Cancel", callback_data="menu_main")
            ]]),
            parse_mode=ParseMode.MARKDOWN
        )


async def text_message_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    text = update.message.text.strip()
    if not text:
        return

    settings = get_user_state(context)
    voice_id = settings["voice_id"]
    language = settings["language"]

    await update.message.chat.send_action(ChatAction.RECORD_VOICE)
    status_msg = await update.message.reply_text("🔊 *Synthesizing voice with Cartesia Sonic...*", parse_mode=ParseMode.MARKDOWN)

    try:
        audio_bytes = await cartesia_tts(text=text, voice_id=voice_id, language=language)
        await status_msg.delete()

        audio_file = io.BytesIO(audio_bytes)
        audio_file.name = "cartesia_speech.wav"

        caption = f"🎙️ *Voice:* {settings['voice_name']} ({language.upper()})"

        await update.message.reply_voice(
            voice=audio_file,
            caption=caption,
            parse_mode=ParseMode.MARKDOWN,
            reply_markup=get_main_menu_keyboard()
        )
    except Exception as e:
        logger.error(f"Error in TTS generation: {e}")
        await status_msg.edit_text(f"❌ **Failed to generate audio:** `{str(e)}`", parse_mode=ParseMode.MARKDOWN)


async def voice_or_audio_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    settings = get_user_state(context)

    await update.message.chat.send_action(ChatAction.TYPING)
    status_msg = await update.message.reply_text("📥 *Processing audio sample...*", parse_mode=ParseMode.MARKDOWN)

    try:
        if update.message.voice:
            file_obj = await update.message.voice.get_file()
        elif update.message.audio:
            file_obj = await update.message.audio.get_file()
        else:
            await status_msg.edit_text("❌ Please send a valid voice note or audio file.")
            return

        audio_bytes = await file_obj.download_as_bytearray()
        await status_msg.edit_text("🧬 *Cloning voice with Cartesia AI...*", parse_mode=ParseMode.MARKDOWN)

        user_name = update.effective_user.first_name or "User"
        voice_name = f"{user_name}'s Clone"

        clone_result = await cartesia_clone_voice(
            audio_bytes=bytes(audio_bytes),
            voice_name=voice_name
        )

        cloned_voice_id = clone_result.get("id") or clone_result.get("voice_id")
        if not cloned_voice_id:
            raise ValueError(f"No voice ID returned: {clone_result}")

        settings["voice_id"] = cloned_voice_id
        settings["voice_name"] = f"🧬 {voice_name}"
        settings["awaiting_clone"] = False

        success_text = (
            "🎉 **Voice Cloned Successfully!**\n\n"
            f"• **Voice Name:** {voice_name}\n"
            f"• **Voice ID:** `{cloned_voice_id}`\n\n"
            "This voice is now your **active voice**! Send me any text to hear it speak."
        )
        await status_msg.edit_text(
            success_text,
            reply_markup=get_main_menu_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )
    except Exception as e:
        logger.error(f"Cloning error: {e}")
        await status_msg.edit_text(
            f"❌ **Failed to clone voice:** `{str(e)}`\n\nMake sure the audio sample has clear speech (at least 5 seconds).",
            reply_markup=get_main_menu_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )


def main() -> None:
    if not TELEGRAM_BOT_TOKEN:
        logger.error("TELEGRAM_BOT_TOKEN environment variable is missing!")
        print("Please set TELEGRAM_BOT_TOKEN before running.")
        return

    application = Application.builder().token(TELEGRAM_BOT_TOKEN).build()

    application.add_handler(CommandHandler("start", start_command))
    application.add_handler(CommandHandler("menu", start_command))
    application.add_handler(CallbackQueryHandler(button_callback_handler))
    application.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, text_message_handler))
    application.add_handler(MessageHandler(filters.VOICE | filters.AUDIO, voice_or_audio_handler))

    logger.info("Starting Cartesia Telegram Bot...")
    application.run_polling(drop_pending_updates=True)


if __name__ == "__main__":
    main()
